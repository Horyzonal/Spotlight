package dev.spotlight.commands

import dev.spotlight.ui.WikiScreen
import net.minecraft.client.Minecraft

internal fun registerWiki() {
    registerCommand("wiki", "Minecraft Wiki", "Explicit online title search; sends only typed search terms to minecraft.wiki") {
        val client = Minecraft.getInstance()
        client.gui.setScreen(WikiScreen(client.gui.screen()))
    }
}
