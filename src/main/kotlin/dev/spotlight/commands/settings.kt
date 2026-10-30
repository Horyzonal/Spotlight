package dev.spotlight.commands

import dev.spotlight.ui.SpotlightSettingsScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.options.OptionsScreen

internal fun registerSettings() {
    registerCommand("settings", "Spotlight settings") {
        val client = Minecraft.getInstance()
        client.setScreen(SpotlightSettingsScreen(client.screen))
    }
    registerCommand("minecraft_settings", "Minecraft settings") {
        val client = Minecraft.getInstance()
        val parent = client.screen ?: if (client.level == null) TitleScreen() else PauseScreen(false)
        client.setScreen(OptionsScreen(parent, client.options))
    }
}
