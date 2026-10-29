package dev.spotlight.core

import kotlin.math.floor
import kotlin.math.roundToInt

object ColorPicker {
    data class Hsv(val hue: Double, val saturation: Double, val value: Double)

    fun rgb(hue: Double, saturation: Double, value: Double): Int {
        require(hue.isFinite() && saturation in 0.0..1.0 && value in 0.0..1.0) { "Expected finite hue and saturation/value in 0..1" }
        val h = ((hue % 360 + 360) % 360) / 60
        val c = value * saturation
        val x = c * (1 - kotlin.math.abs(h % 2 - 1))
        val channels = when (floor(h).toInt()) {
            0 -> doubleArrayOf(c, x, 0.0)
            1 -> doubleArrayOf(x, c, 0.0)
            2 -> doubleArrayOf(0.0, c, x)
            3 -> doubleArrayOf(0.0, x, c)
            4 -> doubleArrayOf(x, 0.0, c)
            else -> doubleArrayOf(c, 0.0, x)
        }
        return channels.fold(0) { result, channel -> (result shl 8) or ((channel + value - c) * 255).roundToInt().coerceIn(0, 255) }
    }

    fun hsv(color: Int): Hsv {
        require(color in 0..0xFFFFFF) { "Expected RGB color" }
        val r = (color shr 16) / 255.0
        val g = ((color shr 8) and 255) / 255.0
        val b = (color and 255) / 255.0
        val max = maxOf(r, g, b)
        val delta = max - minOf(r, g, b)
        val hue = when {
            delta == 0.0 -> 0.0
            max == r -> 60 * (((g - b) / delta + 6) % 6)
            max == g -> 60 * ((b - r) / delta + 2)
            else -> 60 * ((r - g) / delta + 4)
        }
        return Hsv(hue, if (max == 0.0) 0.0 else delta / max, max)
    }

    fun hex(color: Int): String {
        require(color in 0..0xFFFFFF) { "Expected RGB color" }
        return "#" + color.toString(16).padStart(6, '0').uppercase(java.util.Locale.ROOT)
    }

    fun parseHex(text: String): Int? = text.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }?.drop(1)?.toInt(16)

    fun parseRgb(channels: List<String>): Int? {
        if (channels.size != 3) return null
        var color = 0
        for (text in channels) {
            if (text.isEmpty() || text.any { it !in '0'..'9' }) return null
            val channel = text.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            color = (color shl 8) or channel
        }
        return color
    }

    
    fun positionedStops(colors: List<Int>): List<GradientStop> {
        require(colors.size in 1..16 && colors.all { it in 0..0xFFFFFF }) { "Expected 1..16 RGB stops" }
        return colors.mapIndexed { index, color ->
            GradientStop(color, if (colors.size == 1) 0.0 else index.toDouble() / (colors.size - 1))
        }
    }

    private fun validate(stops: List<Int>, selected: Int) {
        require(stops.size in 1..16 && stops.all { it in 0..0xFFFFFF }) { "Expected 1..16 RGB stops" }
        require(selected in stops.indices) { "Select a color stop" }
    }

    fun add(stops: List<Int>, selected: Int): List<Int> {
        validate(stops, selected)
        require(stops.size < 16) { "Maximum 16 stops" }
        val a = stops[selected]
        val b = stops.getOrElse(selected + 1) { a }
        val middle = listOf(16, 8, 0).fold(0) { result, shift ->
            result or ((((a shr shift and 255) + (b shr shift and 255)) / 2.0).roundToInt() shl shift)
        }
        return stops.toMutableList().also { it.add(selected + 1, middle) }
    }

    fun remove(stops: List<Int>, selected: Int): List<Int> {
        validate(stops, selected)
        require(stops.size > 1) { "Keep at least one stop" }
        return stops.filterIndexed { index, _ -> index != selected }
    }

    fun move(stops: List<Int>, selected: Int, direction: Int): List<Int> {
        validate(stops, selected)
        require(direction == -1 || direction == 1) { "Expected left or right" }
        require(selected + direction in stops.indices) { "Stop already at edge" }
        return stops.toMutableList().also { java.util.Collections.swap(it, selected, selected + direction) }
    }
}
