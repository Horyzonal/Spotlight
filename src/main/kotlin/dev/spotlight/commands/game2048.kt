package dev.spotlight.commands

import dev.spotlight.ui.games.Game2048Screen
import net.minecraft.client.Minecraft

internal fun register2048() {
    registerCommand("2048", "2048", "Tile-merging minigame · arrows/WASD · R restart") {
        val client = Minecraft.getInstance()
        client.gui.setScreen(Game2048Screen(client.gui.screen()))
    }
}
