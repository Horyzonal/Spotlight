package dev.spotlight.core

import kotlin.math.roundToInt


object StyledTextCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        val blackWhite = listOf(0x000000, 0xFFFFFF)
        fun format(text: String, mode: TextMode = TextMode.GRADIENT, colors: List<Int> = blackWhite) =
            TextFormatter.format(text, mode, colors)
        fun rejects(action: () -> Unit) {
            check(runCatching(action).exceptionOrNull() is IllegalArgumentException)
        }

        check(TextFormatter.parseColors(" #aBcD01, 000000\nFFFFFF\t#123456 ") == listOf(0xABCD01, 0, 0xFFFFFF, 0x123456))
        for (value in listOf("", "#FFF", "1234567", "GG0000", "-00001", "0x123456", "123456 nope")) {
            rejects { TextFormatter.parseColors(value) }
        }
        check(TextFormatter.parseColors(List(16) { "123456" }.joinToString(",")).size == 16)
        rejects { TextFormatter.parseColors(List(17) { "123456" }.joinToString(",")) }
        check(format("abc").runs.map { it.color } == listOf(0, 0x808080, 0xFFFFFF))
        check(format("abcde", colors = listOf(0xFF0000, 0x00FF00, 0x0000FF)).runs.map { it.color } ==
            listOf(0xFF0000, 0x808000, 0x00FF00, 0x008080, 0x0000FF))
        check(format("abc", colors = listOf(0xFFFFFF, 0)).runs.map { it.color } == listOf(0xFFFFFF, 0x808080, 0))
        check(format("x").runs == listOf(ColoredRun("x", 0)))
        check(format("abc", colors = listOf(0x123456)).runs == listOf(ColoredRun("abc", 0x123456)))
        check(format("abc", TextMode.SOLID).runs == listOf(ColoredRun("abc", 0)))
        check(format("abc", TextMode.INTERLEAVED).runs.map { it.color } == listOf(0, 0xFFFFFF, 0))
        check(format("abc", TextMode.INTERLEAVED, listOf(42, 42)).runs == listOf(ColoredRun("abc", 42)))
        check(format("abcdef", TextMode.RAINBOW).runs.map { it.color } ==
            listOf(0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF))
        check(format("x", TextMode.RAINBOW).runs == listOf(ColoredRun("x", 0xFF0000)))
        check(format("abcdef", TextMode.RAINBOW, listOf(123)).runs == format("abcdef", TextMode.RAINBOW).runs)
        check(format("😀e\u0301Z").runs.map { it.text } == listOf("😀", "e\u0301", "Z"))
        check(format("👩‍💻X").runs.map { it.text } == listOf("👩‍💻", "X"))
        val multiline = "one\ntwo\tthree"
        check(format(multiline).runs.joinToString("") { it.text } == multiline)
        for (mode in TextMode.entries) {
            val empty = format("", mode)
            check(empty.runs.isEmpty() && empty.toCustom("{text}").isEmpty() && empty.toLegacy().isEmpty())
            check(empty.toJson() == "{\"text\":\"\",\"bold\":false,\"italic\":false,\"underlined\":false,\"strikethrough\":false,\"obfuscated\":false}")
        }

        val styled = TextFormatter.format("ab", TextMode.INTERLEAVED, blackWhite, TextStyle(true, true, true, true, true))
        check(styled.style == TextStyle(true, true, true, true, true))
        check(styled.toJson() == "{\"text\":\"\",\"bold\":true,\"italic\":true,\"underlined\":true,\"strikethrough\":true,\"obfuscated\":true,\"extra\":[{\"text\":\"a\",\"color\":\"#000000\"},{\"text\":\"b\",\"color\":\"#FFFFFF\"}]}")
        check(styled.toLegacy() == "§x§0§0§0§0§0§0§k§l§m§n§oa§x§F§F§F§F§F§F§k§l§m§n§ob§r")
        check(format("a", TextMode.SOLID).toLegacy() == "§x§0§0§0§0§0§0a§r")
        check(styled.toCustom("<#{color}>{text}:{hex};") == "<#000000>a:#000000;<#FFFFFF>b:#FFFFFF;")
        check(styled.toCustom("").isEmpty())
        check(format("{color}{text}{hex}", TextMode.SOLID).toCustom("{text}|{color}|{hex}|{unknown}") ==
            "{color}{text}{hex}|000000|#000000|{unknown}")
        val escaped = format("\"\\\n\t\r\b\u000C\u0001😀\uD800", TextMode.SOLID).toJson()
        check(escaped.contains("\"text\":\"\\\"\\\\\\u000a\\u0009\\u000d\\u0008\\u000c\\u0001\\ud83d\\ude00\\ud800\""))
        check(escaped.none { it < ' ' || it.isSurrogate() })

        check(format("x".repeat(4096), TextMode.SOLID).runs.single().text.length == 4096)
        check(format("😀".repeat(4096), TextMode.SOLID).runs.single().text.length == 8192)
        rejects { format("x".repeat(4097)) }
        rejects { format("😀".repeat(4097)) }
        rejects { format("\u0000") }
        rejects { format("a", colors = emptyList()) }
        rejects { format("", colors = emptyList()) }
        rejects { format("a", colors = List(17) { 0 }) }
        rejects { format("a", colors = listOf(-1)) }
        rejects { format("a", colors = listOf(0x1000000)) }
        rejects { ColoredRun("a", -1) }
        rejects { ColoredRun("\u0000", 0) }
        rejects { StyledText(List(2) { ColoredRun("x".repeat(4096), 0) }) }
        fun positioned(text: String, stops: List<GradientStop>, mode: TextMode = TextMode.GRADIENT) =
            TextFormatter.formatWithStops(text, mode, stops)
        val movable = listOf(GradientStop(0xFFFFFF, 0.75), GradientStop(0, 0.25))
        check(positioned("abcde", movable).runs == listOf(
            ColoredRun("ab", 0), ColoredRun("c", 0x808080), ColoredRun("de", 0xFFFFFF)))
        val unequal = listOf(GradientStop(0, 0.0), GradientStop(0xFF0000, 0.25), GradientStop(0xFFFFFF, 1.0))
        check(positioned("abcde", unequal).runs.map { it.color } ==
            listOf(0, 0xFF0000, 0xFF5555, 0xFFAAAA, 0xFFFFFF))
        val coincident = listOf(GradientStop(0, 0.0), GradientStop(0xFF0000, 0.5),
            GradientStop(0x0000FF, 0.5), GradientStop(0xFFFFFF, 1.0))
        check(positioned("abcde", coincident).runs.map { it.color } ==
            listOf(0, 0x800000, 0x0000FF, 0x8080FF, 0xFFFFFF))
        check(positioned("abc", coincident.map { it.copy(position = 0.5) }).runs ==
            listOf(ColoredRun("a", 0), ColoredRun("bc", 0xFFFFFF)))
        check(positioned("abc", listOf(GradientStop(42, 0.6))).runs == listOf(ColoredRun("abc", 42)))
        check(positioned("x", movable).runs == listOf(ColoredRun("x", 0)))
        val duplicateEnds = listOf(GradientStop(1, 1.0), GradientStop(2, 0.0),
            GradientStop(3, 1.0), GradientStop(4, 0.0))
        check(positioned("ab", duplicateEnds).runs.map { it.color } == listOf(4, 3))
        check(positioned("abcd", duplicateEnds, TextMode.INTERLEAVED).runs.map { it.color } == listOf(2, 4, 1, 3))
        check(positioned("abc", movable, TextMode.SOLID).runs == listOf(ColoredRun("abc", 0)))
        check(positioned("abc", movable, TextMode.RAINBOW) == format("abc", TextMode.RAINBOW))
        for (mode in TextMode.entries) {
            for (stops in listOf(emptyList(), List(17) { GradientStop(0, 0.0) },
                listOf(GradientStop(-1, 0.0)), listOf(GradientStop(0x1000000, 0.0)))) {
                rejects { positioned("", stops, mode) }
            }
            for (position in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.1, 1.1)) {
                rejects { positioned("", listOf(GradientStop(0, position)), mode) }
            }
            rejects { positioned("\u0000", movable, mode) }
            rejects { positioned("x".repeat(4097), movable, mode) }
            for (text in listOf("", "x", "😀e\u0301Z", "👩‍💻X", multiline)) {
                val style = TextStyle(true, true, true, true, true)
                val colors = listOf(0x123456, 0xABCDEF, 0x654321)
                val legacy = TextFormatter.format(text, mode, colors, style)
                val explicit = TextFormatter.formatWithStops(text, mode, ColorPicker.positionedStops(colors), style)
                check(explicit == legacy)
                check(explicit.toJson() == legacy.toJson() && explicit.toLegacy() == legacy.toLegacy())
            }
        }
        check(positioned("😀e\u0301Z", movable).runs.map { it.text } == listOf("😀", "e\u0301", "Z"))
        check(positioned("👩‍💻X", movable).runs.map { it.text } == listOf("👩‍💻", "X"))
        
        for (count in 1..16) for (length in 1..65) {
            val colors = List(count) { (it * 0x173951) and 0xFFFFFF }
            val expected = List(length) { index ->
                if (count == 1) colors.first() else {
                    val scaled = (if (length == 1) 0.0 else index.toDouble() / (length - 1)) * (count - 1)
                    val stop = scaled.toInt().coerceAtMost(count - 2)
                    val fraction = scaled - stop
                    listOf(16, 8, 0).fold(0) { rgb, shift ->
                        val a = colors[stop] shr shift and 255
                        val b = colors[stop + 1] shr shift and 255
                        rgb or ((a + (b - a) * fraction).roundToInt() shl shift)
                    }
                }
            }
            val result = format("x".repeat(length), colors = colors)
            check(result.runs.flatMap { run -> List(run.text.length) { run.color } } == expected)
        }
        println("StyledText checks passed")
    }
}
