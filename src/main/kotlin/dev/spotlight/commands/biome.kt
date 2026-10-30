package dev.spotlight.commands

import dev.spotlight.platform.reportDebug
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft
import net.minecraft.world.level.chunk.status.ChunkStatus

internal fun registerBiome() {
    registerCommand("biome", "Biome", "Current biome registry ID at player block position", unavailable = ::worldUnavailable) {
        val client = Minecraft.getInstance()
        val level = requireNotNull(client.level) { "Join a world first" }
        val pos = requireNotNull(client.player) { "Join a world first" }.blockPosition()
        check(level.getChunk(pos.x shr 4, pos.z shr 4, ChunkStatus.FULL, false) != null) { "Current chunk is not loaded" }
        val id = level.getBiome(pos).unwrapKey().map { it.location().toString() }.orElse("Unregistered biome")
        reportDebug("Biome", "$id at ${pos.x}, ${pos.y}, ${pos.z}")
    }
}
