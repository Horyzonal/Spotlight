package dev.spotlight.commands

import dev.spotlight.ui.AboutScreen
import net.minecraft.client.Minecraft

fun registerAbout() {
    registerCommand("about", "About Spotlight", "Made by Horyzon · website, source and mod homepage") {
        val client = Minecraft.getInstance()
        client.gui.setScreen(AboutScreen(client.gui.screen()))
    }
}
