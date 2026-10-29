package dev.spotlight.core

import java.text.BreakIterator
import java.util.Locale
import kotlin.math.roundToInt

enum class TextMode { SOLID, GRADIENT, RAINBOW, INTERLEAVED }

data class GradientStop(val color: Int, val position: Double)

data class TextStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underlined: Boolean = false,
    val strikethrough: Boolean = false,
    val obfuscated: Boolean = false,
)

data class ColoredRun(val text: String, val color: Int) {
    init {
        require(color in 0..0xFFFFFF) { "Color must be RGB (000000..FFFFFF)" }
        validateStyledText(text)
    }
}

data class StyledText(val runs: List<ColoredRun>, val style: TextStyle = TextStyle()) {
    init {
        require(runs.sumOf { it.text.length.toLong() } <= 8192) { "Text exceeds 8192 UTF-16 units" }
        require(runs.sumOf { it.text.codePointCount(0, it.text.length).toLong() } <= 4096) {
            "Text exceeds 4096 code points"
        }
    }

    
    fun toJson(): String = buildString {
        append("{\"text\":\"\",\"bold\":${style.bold},\"italic\":${style.italic},")
        append("\"underlined\":${style.underlined},\"strikethrough\":${style.strikethrough},")
        append("\"obfuscated\":${style.obfuscated}")
        if (runs.isNotEmpty()) {
            append(",\"extra\":[")
            runs.forEachIndexed { index, run ->
                if (index != 0) append(',')
                append("{\"text\":")
                appendJsonString(run.text)
                append(",\"color\":\"#${run.color.rgbHex()}\"}")
            }
            append(']')
        }
        append('}')
    }

    
    fun toCustom(format: String): String = buildString {
        runs.forEach { run ->
            var end = 0
            CUSTOM_TOKEN.findAll(format).forEach { match ->
                append(format, end, match.range.first)
                append(when (match.value) {
                    "{text}" -> run.text
                    "{color}" -> run.color.rgbHex()
                    else -> "#${run.color.rgbHex()}"
                })
                end = match.range.last + 1
            }
            append(format, end, format.length)
        }
    }

    
    fun toLegacy(): String = buildString {
        runs.forEach { run ->
            append("§x")
            run.color.rgbHex().forEach { append('§').append(it) }
            if (style.obfuscated) append("§k")
            if (style.bold) append("§l")
            if (style.strikethrough) append("§m")
            if (style.underlined) append("§n")
            if (style.italic) append("§o")
            append(run.text)
        }
        if (runs.isNotEmpty()) append("§r")
    }
}

object TextFormatter {
    
    fun format(text: String, mode: TextMode, colors: List<Int>, style: TextStyle = TextStyle()): StyledText =
        formatWithStops(text, mode, ColorPicker.positionedStops(colors), style)

    
    fun formatWithStops(text: String, mode: TextMode, stops: List<GradientStop>, style: TextStyle = TextStyle()): StyledText {
        validateStyledText(text)
        require(stops.size in 1..16) { "Expected 1..16 stops" }
        require(stops.all { it.color in 0..0xFFFFFF }) { "Colors must be RGB (000000..FFFFFF)" }
        require(stops.all { it.position.isFinite() && it.position in 0.0..1.0 }) { "Stop positions must be finite and in 0..1" }
        val sorted = stops.sortedBy { it.position }
        val colors = sorted.map { it.color }
        
        val evenlySpaced = sorted.indices.all { sorted[it].position == if (sorted.size == 1) 0.0 else it.toDouble() / (sorted.size - 1) }
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val glyphs = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            glyphs += text.substring(start, end)
            start = end
            end = iterator.next()
        }
        val runs = mutableListOf<ColoredRun>()
        val pending = StringBuilder()
        var previous = -1
        glyphs.forEachIndexed { index, glyph ->
            val color = when (mode) {
                TextMode.SOLID -> colors.first()
                TextMode.INTERLEAVED -> colors[index % colors.size]
                TextMode.GRADIENT -> {
                                    val position = if (glyphs.size == 1) 0.0 else index.toDouble() / (glyphs.size - 1)
                                    if (evenlySpaced) gradient(colors, position) else gradientWithStops(sorted, position)
                                }
                TextMode.RAINBOW -> rainbow(index.toDouble() / glyphs.size)
            }
            if (color != previous && pending.isNotEmpty()) {
                runs += ColoredRun(pending.toString(), previous)
                pending.setLength(0)
            }
            pending.append(glyph)
            previous = color
        }
        if (pending.isNotEmpty()) runs += ColoredRun(pending.toString(), previous)
        return StyledText(runs, style)
    }

    
    fun parseColors(value: String): List<Int> {
        val tokens = value.trim().split(Regex("[,\\s]+"))
        require(tokens.size in 1..16 && tokens.all { it.matches(Regex("#?[0-9a-fA-F]{6}")) }) {
            "Expected 1..16 six-digit RGB colors, separated by commas or whitespace"
        }
        return tokens.map { it.removePrefix("#").toInt(16) }
    }

    private fun gradientWithStops(stops: List<GradientStop>, position: Double): Int {
        val left = stops.indexOfLast { it.position <= position }
        if (left < 0) return stops.first().color
        if (left == stops.lastIndex || stops[left].position == position) return stops[left].color
        val a = stops[left]
        val b = stops[left + 1]
        return interpolate(a.color, b.color, (position - a.position) / (b.position - a.position))
    }

    private fun gradient(colors: List<Int>, position: Double): Int {
        if (colors.size == 1) return colors.first()
        val scaled = position * (colors.size - 1)
        val stop = scaled.toInt().coerceAtMost(colors.size - 2)
        val fraction = scaled - stop
        return interpolate(colors[stop], colors[stop + 1], fraction)
    }

    private fun interpolate(from: Int, to: Int, fraction: Double): Int {
        var result = 0
        for (shift in listOf(16, 8, 0)) {
            val a = (from shr shift) and 255
            val b = (to shr shift) and 255
            result = result or ((a + (b - a) * fraction).roundToInt() shl shift)
        }
        return result
    }

    private fun rainbow(hue: Double): Int {
        val sector = hue * 6
        val rising = ((sector - sector.toInt()) * 255).roundToInt()
        val falling = 255 - rising
        return when (sector.toInt()) {
            0 -> 0xFF0000 or (rising shl 8)
            1 -> (falling shl 16) or 0x00FF00
            2 -> 0x00FF00 or rising
            3 -> (falling shl 8) or 0x0000FF
            4 -> (rising shl 16) or 0x0000FF
            else -> 0xFF0000 or falling
        }
    }
}

private val CUSTOM_TOKEN = Regex("\\{(?:text|color|hex)\\}")

private fun Int.rgbHex(): String = toString(16).padStart(6, '0').uppercase(Locale.ROOT)

private fun validateStyledText(text: String) {
    require(text.length <= 8192) { "Text exceeds 8192 UTF-16 units" }
    require(text.codePointCount(0, text.length) <= 4096) { "Text exceeds 4096 code points" }
    require('\u0000' !in text) { "Text must not contain NUL" }
}

private fun StringBuilder.appendJsonString(text: String) {
    append('"')
    text.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            else -> if (char < ' ' || char.isSurrogate()) {
                append("\\u").append(char.code.toString(16).padStart(4, '0'))
            } else append(char)
        }
    }
    append('"')
}
