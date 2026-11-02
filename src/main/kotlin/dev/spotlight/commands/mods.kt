package dev.spotlight.commands

import dev.spotlight.ui.ModsScreen
import net.minecraft.client.Minecraft

internal fun registerMods() {
    registerCommand("mods", "Installed mods", "Search loaded Fabric mods; native settings through optional Mod Menu public API") {
        val client = Minecraft.getInstance()
        client.gui.setScreen(ModsScreen(client.gui.screen()))
    }
}
