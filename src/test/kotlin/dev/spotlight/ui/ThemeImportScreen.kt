package dev.spotlight.ui

import dev.spotlight.core.Theme
import dev.spotlight.core.ThemeColor
import java.nio.file.Path

object ThemeImportScreenCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        validateImportName(" Example ", false)
        validateImportName("", true)
        listOf("", "x".repeat(65), "bad\nname", "bad\u2028name").forEach {
            check(runCatching { validateImportName(it, false) }.isFailure)
        }
        val report = parseImportText("colors.accent=#FF123456")
        check(report.warnings.isEmpty())
        check(report.config.color(ThemeColor.ACCENT.defaultArgb) == 0xFF123456.toInt())
        listOf("unknown=value", "colors.accent=red", "layout.editorWidth=0", "font=bad font", "font=\\uZZZZ").forEach {
            check(parseImportText(it).warnings.isNotEmpty())
        }
        listOf("", " ", "#".repeat(128 * 1024), "#" + "狗".repeat(45000)).forEach {
            check(runCatching { parseImportText(it) }.isFailure)
        }
        check(importSource(" /tmp/theme.properties ") == Path.of("/tmp/theme.properties"))
        listOf("", "https://example.org/theme.properties", "file:/tmp/theme.properties", "theme.txt").forEach {
            check(runCatching { importSource(it) }.isFailure)
        }
        println("Theme import screen checks passed")
    }
}
