package dev.spotlight.commands

import dev.spotlight.platform.worldUnavailable
import dev.spotlight.ui.LookScreen
import net.minecraft.client.Minecraft

internal fun registerLook() {
    registerCommand("look", "Look", "Face saved locations, exact coordinates, player heads or exact directions", unavailable = ::worldUnavailable) {
        val client = Minecraft.getInstance()
        client.gui.setScreen(LookScreen(client.gui.screen()))
    }
}
