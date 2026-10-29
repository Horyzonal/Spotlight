package dev.spotlight.commands

import dev.spotlight.ui.games.TetrisScreen
import net.minecraft.client.Minecraft

internal fun registerTetris() {
    registerCommand("tetris", "Tetris", "Falling-block minigame · arrows move/rotate · Space drop · P pause") {
        val client = Minecraft.getInstance()
        client.gui.setScreen(TetrisScreen(client.gui.screen()))
    }
}
