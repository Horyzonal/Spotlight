package dev.spotlight.core

import java.nio.file.Files


object ThemeCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        check(Theme.parse(Theme.template()).config == Theme.defaults)
        check(Theme.defaults.textShadow())
        fun shadow(argb: String) = Theme.parse("colors.panelBackground=#$argb").config.textShadow()
        check(!shadow("FFFFFFFF"))
        check(shadow("FF000000"))
        check(shadow("FF757575") && !shadow("FF767676"))
        check(!shadow("FF00FF00")) 
        check(shadow("FF0000FF"))
        check(shadow("00757575") && !shadow("00767676")) 
        check(ThemeColor.entries.map { it.defaultArgb }.distinct().size == ThemeColor.entries.size)
        ThemeColor.entries.forEach { check(Theme.defaults.color(it.defaultArgb) == it.defaultArgb.toInt()) }
        check(Theme.defaults.color(0x01020304) == 0x01020304)
        check(Theme.defaults.color(0xFFF2F5FC.toInt().toLong()) == 0xFFF2F5FC.toInt())
        val parsed = Theme.parse("""
            colors.text=#80123456
            colors.panelBackground=#00112233
            colors.error=not-a-color
            layout.paletteWidth=720
            layout.rowHeight=0
            layout.menuWidth=999999999999999999
            font=custom:ui/body
            unknown=true
        """.trimIndent())
        check(parsed.config.color(0xFFF2F5FC) == 0x80123456.toInt())
        check(parsed.config.color(0xFA101722) == 0x00112233)
        check(parsed.config.color(0xFFFFB4A9) == 0xFFFFB4A9.toInt())
        check(parsed.config.metric("paletteWidth", 560) == 720)
        check(parsed.config.metric("layout.paletteWidth", 560, max = 600) == 600)
        check(parsed.config.metric("rowHeight", 30) == 30)
        check(parsed.config.metric("unregistered", 12) == 12)
        check(parsed.config.font == "custom:ui/body")
        check(parsed.warnings.size == 4)
        check(Theme.parse("font=INVALID:font").config.font == null)
        val malformed = "colors.text=\\uNOPE\n"
        check(Theme.parse(malformed).config == Theme.defaults)
        check(Theme.parse(malformed).warnings.isNotEmpty())
        val directory = Files.createTempDirectory("spotlight-theme-check-")
        val path = directory.resolve("config/spotlight-theme.properties")
        try {
            val initial = Theme.load(path)
            check(initial.created && initial.warnings.isEmpty() && initial.config == Theme.defaults)
            check(Files.readString(path) == Theme.template())
            check(!Theme.load(path).created)
            Files.writeString(path, malformed)
            check(Theme.load(path).warnings.isNotEmpty())
            check(Files.readString(path) == malformed)
            Files.writeString(path, "colors.text=#FF010203\n")
            check(Theme.load(path).config.color(0xFFF2F5FC) == 0xFF010203.toInt())
            Files.writeString(path, "colors.text=bad\nlayout.paletteWidth=700\n")
            val partial = Theme.load(path)
            check(partial.config.color(0xFFF2F5FC) == 0xFFF2F5FC.toInt())
            check(partial.config.metric("paletteWidth", 560) == 700)
            check(Files.readString(path) == "colors.text=bad\nlayout.paletteWidth=700\n")
            check(Theme.load(directory).warnings.isNotEmpty())
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(path.parent)
            Files.deleteIfExists(directory)
        }
        println("Theme checks passed")
    }
}
