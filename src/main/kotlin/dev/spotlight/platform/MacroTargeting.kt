package dev.spotlight.platform

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

internal object MacroTargeting {
    
    const val MAX_DISTANCE = 256.0
    data class Targets(val block: BlockPos?, val entity: String?)
    data class Trace(val block: BlockPos?, val limitSqr: Double)

    fun capture(client: Minecraft, includeEntities: Boolean): Targets {
        val level = requireNotNull(client.level) { "Look requires a world; join a world first" }
        val player = requireNotNull(client.player) { "Look requires a player; join a world first" }
        val camera = client.cameraEntity ?: player
        require(player.level() === level && camera.level() === level) { "Look world changed; try again" }
        val from = camera.eyePosition
        val to = from.add(camera.getViewVector(1.0f).normalize().scale(MAX_DISTANCE))
        fun chunk(pos: BlockPos) = level.chunkSource.getChunk(pos.x shr 4, pos.z shr 4, ChunkStatus.FULL, false)
        fun loaded(pos: BlockPos) = !level.isOutsideBuildHeight(pos) &&
            kotlin.math.abs(pos.x.toLong()) < 30_000_000 && kotlin.math.abs(pos.z.toLong()) < 30_000_000 && chunk(pos) != null
        
        val blocks = object : BlockGetter {
            override fun getHeight() = level.height
            override fun getMinY() = level.minY
            override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
            override fun getBlockState(pos: BlockPos) =
                if (loaded(pos)) chunk(pos)!!.getBlockState(pos) else Blocks.AIR.defaultBlockState()
            override fun getFluidState(pos: BlockPos) = getBlockState(pos).fluidState
        }
        val context = ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camera)
        val trace = trace(from, to, ::loaded) { pos ->
            val state = blocks.getBlockState(pos)
            blocks.clipWithInteractionOverride(from, to, pos, context.getBlockShape(state, blocks, pos), state)
        }
        val entity = if (includeEntities) nearest(
            from, to, trace.limitSqr,
            level.entitiesForRendering().asSequence().filter {
                eligible(it.isAlive, it.isPickable, it.isSpectator, it === camera || it === player, it.level() === level) &&
                    loaded(it.blockPosition())
            },
            { it.boundingBox.inflate(it.pickRadius.toDouble()) },
        )?.uuid?.toString() else null
        require(client.level === level && client.player === player && (client.cameraEntity ?: player) === camera) {
            "Look world changed; try again"
        }
        return Targets(trace.block, entity)
    }

    internal fun eligible(alive: Boolean, pickable: Boolean, spectator: Boolean, self: Boolean, sameWorld: Boolean) =
        alive && pickable && !spectator && !self && sameWorld

    internal fun trace(
        from: Vec3,
        to: Vec3,
        loaded: (BlockPos) -> Boolean,
        hit: (BlockPos) -> BlockHitResult?,
    ): Trace {
        require(from.x.isFinite() && from.y.isFinite() && from.z.isFinite() &&
            to.x.isFinite() && to.y.isFinite() && to.z.isFinite() &&
            from.distanceToSqr(to) > 0.0 && from.distanceToSqr(to) <= MAX_DISTANCE * MAX_DISTANCE + 1e-7) {
            "Invalid macro targeting ray"
        }
        return BlockGetter.traverseBlocks(from, to, Unit, { _, pos ->
            if (!loaded(pos)) {
                val entry = AABB(pos).clip(from, to).orElse(from)
                Trace(null, from.distanceToSqr(entry))
            } else {
                hit(pos)?.let { Trace(it.blockPos.immutable(), from.distanceToSqr(it.location)) }
            }
        }, { Trace(null, from.distanceToSqr(to) + 1e-7) })
    }

    internal fun <T> nearest(
        from: Vec3,
        to: Vec3,
        limitSqr: Double,
        candidates: Sequence<T>,
        bounds: (T) -> AABB,
    ): T? {
        var distance = minOf(limitSqr, from.distanceToSqr(to) + 1e-7)
        var nearest: T? = null
        for (candidate in candidates) {
            val box = bounds(candidate)
            val location = if (box.contains(from)) from else box.clip(from, to).orElse(null) ?: continue
            val next = from.distanceToSqr(location)
            if (next < distance) {
                distance = next
                nearest = candidate
            }
        }
        return nearest
    }
}
