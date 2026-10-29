package dev.spotlight.commands

import dev.spotlight.platform.reportDebug
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos

internal fun registerChunk() {
    registerCommand("chunk_info", "Chunk info", "Chunk coordinates, section Y, local block and region file coordinates", unavailable = ::worldUnavailable) {
        val player = requireNotNull(Minecraft.getInstance().player) { "Join a world first" }
        reportDebug("Chunk info", chunkInfo(player.blockPosition()))
    }
}

internal fun chunkInfo(pos: BlockPos): String {
    val x = Math.floorDiv(pos.x, 16)
    val z = Math.floorDiv(pos.z, 16)
    return "chunk X/Z=$x, $z; sectionY=${Math.floorDiv(pos.y, 16)}; local block X/Y/Z=${Math.floorMod(pos.x, 16)}, ${Math.floorMod(pos.y, 16)}, ${Math.floorMod(pos.z, 16)}; region file=r.${Math.floorDiv(x, 32)}.${Math.floorDiv(z, 32)}.mca; region-local chunk X/Z=${Math.floorMod(x, 32)}, ${Math.floorMod(z, 32)}"
}
