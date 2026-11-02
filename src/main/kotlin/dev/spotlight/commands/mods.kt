package dev.spotlight.commands

import dev.spotlight.ui.ModsScreen
import net.minecraft.client.Minecraft

internal fun registerMods() {
    registerCommand("mods", "Installed mods", "Search loaded NeoForge mods; native settings through NeoForge config screen API") {
        val client = Minecraft.getInstance()
        client.setScreen(ModsScreen(client.screen))
    }
}
