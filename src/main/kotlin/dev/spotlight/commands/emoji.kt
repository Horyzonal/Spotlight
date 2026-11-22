package dev.spotlight.commands

import dev.spotlight.ui.EmojiScreen
import net.minecraft.client.Minecraft

internal fun registerEmoji() {
    registerCommand("emoji", "Emoji", "Search emoji by name and copy them") {
        val client = Minecraft.getInstance()
        client.gui.setScreen(EmojiScreen(client.gui.screen()))
    }
}
