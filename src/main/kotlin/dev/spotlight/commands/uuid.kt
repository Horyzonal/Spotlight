package dev.spotlight.commands

import dev.spotlight.platform.worldUnavailable
import dev.spotlight.ui.UuidScreen
import net.minecraft.client.Minecraft

internal fun registerUuid() {
    registerCommand("uuid", "uuid", "Copy an online player or loaded entity UUID", unavailable = ::worldUnavailable) {
        worldUnavailable()?.let { error(it) }
        val client = Minecraft.getInstance()
        client.setScreen(UuidScreen(client.screen))
    }
}
