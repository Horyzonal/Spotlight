package dev.spotlight.commands

import dev.spotlight.platform.MacroTargeting
import dev.spotlight.platform.reportDebug
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.Vec3

internal fun registerTargetBlock() {
    registerCommand("targeted_block", "Targeted block details", "F3 looked-at block ID, state properties, coordinates and distance; loaded only, 256-block range", unavailable = ::worldUnavailable) {
        val client = Minecraft.getInstance()
        val level = requireNotNull(client.level) { "Join a world first" }
        val player = requireNotNull(client.player) { "Join a world first" }
        val pos = MacroTargeting.capture(client, false).block
        if (pos == null) {
            reportDebug("Targeted block", "No block target in loaded chunks within ${MacroTargeting.MAX_DISTANCE} blocks")
        } else {
            val chunk = requireNotNull(level.chunkSource.getChunk(pos.x shr 4, pos.z shr 4, ChunkStatus.FULL, false)) { "Target chunk is no longer loaded" }
            val state = chunk.getBlockState(pos)
            val properties = state.values.toList().sortedBy { it.first.name }.joinToString(", ") { "${it.first.name}=${it.second.toString()}" }.ifEmpty { "none" }
            val distance = (client.cameraEntity ?: player).eyePosition.distanceTo(Vec3.atCenterOf(pos))
            reportDebug("Targeted block", "${BuiltInRegistries.BLOCK.getKey(state.block)}; block=${pos.x}, ${pos.y}, ${pos.z}; distance to block center=${debugNumber(distance)} blocks; properties=$properties")
        }
    }
}
