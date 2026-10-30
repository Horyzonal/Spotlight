package dev.spotlight.ui

import dev.spotlight.core.TextFormatter
import dev.spotlight.core.TextMode
import dev.spotlight.core.TextStyle

internal object TextEditorScreenCheck {
    @JvmStatic fun main(args: Array<String>) {
        check(textEditorColumns(620, 5) == 3)
        check(textEditorColumns(240, 5) == 1)
        check(textEditorColumns(1, 1) == 1)
        check(textEditorScroll(400, 20, 0, 200, 600) == 220)
        check(textEditorScroll(0, 80, 220, 200, 600) == 0)
        check(textEditorScroll(0, 80, 0, 800, 600) == 0)
        Preview1214Check.main(emptyArray())
        GradientTimelineCheck.main(emptyArray())
        val styled = TextFormatter.format("A\nB", TextMode.GRADIENT,
            TextFormatter.parseColors("#FF0000, #0000FF"), TextStyle(bold = true, underlined = true))
        check(styled.toCustom("<#{color}>{text}").startsWith("<#FF0000>A"))
        check(styled.toJson().contains("\"bold\":true"))
        check(styled.toJson().contains("\\u000a"))
        check(styled.toLegacy().endsWith("§r"))
        check(runCatching { TextFormatter.parseColors("#XYZ") }.isFailure)
        println("Text editor layout and export checks passed")
    }
}
