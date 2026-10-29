package dev.spotlight.commands

import dev.spotlight.platform.worldUnavailable
import dev.spotlight.platform.notifySpotlight
import net.minecraft.client.Minecraft

internal fun registerSeed() {
    registerCommand("seed", "seed", "Copy full world seed; integrated server only", unavailable = ::seedUnavailable) {
        seedUnavailable()?.let { error(it) }
        val client = Minecraft.getInstance()
        val server = requireNotNull(client.singleplayerServer) { "Integrated server unavailable" }
        client.keyboardHandler.clipboard = server.overworld().seed.toString()
        notifySpotlight("Copied seed to clipboard")
    }
}

private fun seedUnavailable(): String? = worldUnavailable() ?: if (Minecraft.getInstance().singleplayerServer == null) {
    "Full world seed unavailable in multiplayer; hashed biome seed is not the actual world seed"
} else null
