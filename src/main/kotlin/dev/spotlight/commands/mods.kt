package dev.spotlight.commands

import dev.spotlight.ui.ModsScreen
import net.minecraft.client.Minecraft

internal fun registerMods() {
    registerCommand("mods", "Installed mods", "Search loaded mods; settings through native Forge config screens") {
        val client = Minecraft.getInstance()
        client.setScreen(ModsScreen(client.screen))
    }
}
