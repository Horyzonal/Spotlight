package dev.spotlight.commands

import dev.spotlight.platform.reportDebug
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap

internal fun registerHeightmap() {
    registerCommand("heightmap", "Heightmap", "Current column WORLD_SURFACE, MOTION_BLOCKING and OCEAN_FLOOR highest occupied Y", unavailable = ::worldUnavailable) {
        val client = Minecraft.getInstance()
        val level = requireNotNull(client.level) { "Join a world first" }
        val pos = requireNotNull(client.player) { "Join a world first" }.blockPosition()
        val chunk = checkNotNull(level.getChunk(pos.x shr 4, pos.z shr 4, ChunkStatus.FULL, false)) { "Current chunk is not loaded" }
        val heights = listOf(Heightmap.Types.WORLD_SURFACE, Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.OCEAN_FLOOR).joinToString("; ") { type ->
            
            if (chunk.hasPrimedHeightmap(type)) "$type=${chunk.getHeight(type, pos.x, pos.z)}"
            else "$type=unavailable on client"
        }
        reportDebug("Heightmap", "column X/Z=${pos.x}, ${pos.z}; highest occupied Y (not first air Y): $heights")
    }
}
