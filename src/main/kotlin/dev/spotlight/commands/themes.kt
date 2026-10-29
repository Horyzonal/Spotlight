package dev.spotlight.commands

import dev.spotlight.ui.ThemesScreen
import net.minecraft.client.Minecraft

internal fun registerThemes() {
    registerCommand("themes", "Themes", "Change, edit or create themes; Default and four Catppuccin presets") {
        val client = Minecraft.getInstance()
        client.setScreen(ThemesScreen(client.screen))
    }
}
