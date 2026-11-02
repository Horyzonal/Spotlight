package dev.spotlight.commands

import dev.spotlight.platform.worldUnavailable
import dev.spotlight.ui.KitScreen
import net.minecraft.client.Minecraft

fun registerKits() {
    registerCommand("kits", "Saved kits", "Save inventory copies, preview, obtain in Creative; bind kit commands in hotkeys", unavailable = ::worldUnavailable) {
        val client = Minecraft.getInstance()
        client.gui.setScreen(KitScreen(client.gui.screen()))
    }
}
