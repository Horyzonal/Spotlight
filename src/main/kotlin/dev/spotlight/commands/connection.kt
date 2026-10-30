package dev.spotlight.commands

import dev.spotlight.platform.reportDebug
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft

internal fun registerConnection() {
    registerCommand("connection", "Connection information", "F3 local player server-supplied latency, connection kind and server brand; no IP or network requests", unavailable = ::worldUnavailable) {
        val client = Minecraft.getInstance()
        val player = requireNotNull(client.player) { "Join a world first" }
        val connection = requireNotNull(client.connection) { "No active connection" }
        val info = connection.getPlayerInfo(player.uuid)
        val latency = info?.let { "${it.latency} ms (server-supplied player-list latency, not a live probe)" } ?: "unavailable (local player absent from player list)"
        val kind = if (client.isLocalServer) "integrated server" else "remote server"
        val brand = player.serverBrand?.takeIf { it.isNotBlank() } ?: "unavailable (not supplied)"
        reportDebug("Connection information", "kind=$kind; local player=${info?.profile?.name ?: player.name.string}; latency=$latency; server brand=$brand; server address omitted")
    }
}
