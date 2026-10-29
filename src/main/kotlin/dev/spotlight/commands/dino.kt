package dev.spotlight.commands

import dev.spotlight.ui.games.DinoScreen
import net.minecraft.client.Minecraft

internal fun registerDino() {
    registerCommand("dino", "Dino", "Offline-style runner minigame · Space jump · Down duck · R restart") {
        val client = Minecraft.getInstance()
        client.gui.setScreen(DinoScreen(client.gui.screen()))
    }
}
