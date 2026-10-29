package dev.spotlight.core


internal object CatppuccinThemes {
    val entries = listOf(
        palette("latte", "Latte", 0xeff1f5, 0xe6e9ef, 0xdce0e8, 0x4c4f69, 0x6c6f85, 0xccd0da, 0xbcc0cc, 0xacb0be, 0x1e66f5, 0xd20f39, 0xfe640b, 0xdf8e1d),
        palette("frappe", "Frappé", 0x303446, 0x292c3c, 0x232634, 0xc6d0f5, 0xa5adce, 0x414559, 0x51576d, 0x626880, 0x8caaee, 0xe78284, 0xef9f76, 0xe5c890),
        palette("macchiato", "Macchiato", 0x24273a, 0x1e2030, 0x181926, 0xcad3f5, 0xa5adcb, 0x363a4f, 0x494d64, 0x5b6078, 0x8aadf4, 0xed8796, 0xf5a97f, 0xeed49f),
        palette("mocha", "Mocha", 0x1e1e2e, 0x181825, 0x11111b, 0xcdd6f4, 0xa6adc8, 0x313244, 0x45475a, 0x585b70, 0x89b4fa, 0xf38ba8, 0xfab387, 0xf9e2af),
    )

    private fun palette(
        id: String, name: String, base: Int, mantle: Int, crust: Int, text: Int, subtext0: Int,
        surface0: Int, surface1: Int, surface2: Int, blue: Int, red: Int, peach: Int, yellow: Int,
    ): ThemeEntry {
        val colors = ThemeColor.entries.associate { color ->
            val rgb = when (color) {
                ThemeColor.OVERLAY -> crust
                ThemeColor.LOCATION_BACKGROUND, ThemeColor.SCREEN_BACKGROUND, ThemeColor.PANEL_BACKGROUND -> base
                ThemeColor.BUTTON_BACKGROUND -> mantle
                ThemeColor.BUTTON_DISABLED, ThemeColor.ROW_SELECTED -> surface0
                ThemeColor.SEPARATOR, ThemeColor.BUTTON_HOVER, ThemeColor.ROW_HOVER -> surface1
                ThemeColor.BORDER -> surface2
                ThemeColor.ACCENT -> blue
                ThemeColor.BUTTON_TEXT_DISABLED, ThemeColor.ROW_TEXT_DISABLED, ThemeColor.TEXT_MUTED -> subtext0
                ThemeColor.TEXT -> text
                ThemeColor.ERROR, ThemeColor.STATUS_ERROR -> red
                ThemeColor.UNAVAILABLE, ThemeColor.SELECTOR_HINT -> peach
                ThemeColor.FAVORITE -> yellow
            }
            color.defaultArgb to ((color.defaultArgb and 0xFF000000L).toInt() or rgb)
        }
        return ThemeEntry("catppuccin-$id", "Catppuccin $name", true, ThemeConfig(colors = colors))
    }
}
