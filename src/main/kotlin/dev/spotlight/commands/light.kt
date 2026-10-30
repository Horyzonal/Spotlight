package dev.spotlight.commands

import dev.spotlight.platform.reportDebug
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.chunk.ChunkStatus

internal fun registerLight() {
    registerCommand("light_level", "Light level", "Current block sky, block and raw combined light (not gamma brightness)", unavailable = ::worldUnavailable) {
        val client = Minecraft.getInstance()
        val level = requireNotNull(client.level) { "Join a world first" }
        val pos = requireNotNull(client.player) { "Join a world first" }.blockPosition()
        check(level.getChunk(pos.x shr 4, pos.z shr 4, ChunkStatus.FULL, false) != null) { "Current chunk is not loaded" }
        val sky = level.getBrightness(LightLayer.SKY, pos)
        val block = level.getBrightness(LightLayer.BLOCK, pos)
        val darken = level.skyDarken
        reportDebug("Light level", "at ${pos.x}, ${pos.y}, ${pos.z}; sky=$sky; block=$block; max(sky, block)=${maxOf(sky, block)}; raw combined (skyDarken=$darken)=${level.getRawBrightness(pos, darken)}; range 0–15, not gamma/perceived brightness")
    }
}
