package dev.spotlight.commands

import dev.spotlight.platform.worldUnavailable
import dev.spotlight.ui.ClientChestScreen
import net.minecraft.client.Minecraft

fun registerClientChest() {
    registerCommand("client_chest", "Client chest", "54-slot sparse pages; local copied items; vanilla chest NBT import/export", unavailable = ::worldUnavailable) {
        val client = Minecraft.getInstance()
        client.gui.setScreen(ClientChestScreen(client.gui.screen()))
    }
}
