package dev.spotlight.commands

import dev.spotlight.ui.games.SnakeScreen
import net.minecraft.client.Minecraft

internal fun registerSnake() {
    registerCommand("snake", "Snake", "Minigame · arrows/WASD · R restart · P pause") {
        val client = Minecraft.getInstance()
        client.setScreen(SnakeScreen(client.screen))
    }
}
