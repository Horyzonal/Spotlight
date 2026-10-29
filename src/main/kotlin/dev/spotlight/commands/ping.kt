package dev.spotlight.commands

import dev.spotlight.platform.worldUnavailable
import dev.spotlight.ui.PingScreen
import net.minecraft.client.Minecraft

internal fun registerPing() {
    registerCommand("ping", "Ping", "Show ping for all online players", unavailable = ::worldUnavailable) {
        worldUnavailable()?.let { error(it) }
        val client = Minecraft.getInstance()
        client.setScreen(PingScreen(client.screen))
    }
}
