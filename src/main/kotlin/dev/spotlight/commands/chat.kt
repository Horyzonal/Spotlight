package dev.spotlight.commands

import dev.spotlight.ui.ChatScreen
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft

internal fun registerChat() {
    registerCommand("chat", "Chat", "Apple messages looking ahh", unavailable = ::worldUnavailable) {
        val client = Minecraft.getInstance()
        client.gui.setScreen(ChatScreen(client.gui.screen()))
    }
}
