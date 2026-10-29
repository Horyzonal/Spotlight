package dev.spotlight.platform

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

internal object MacroTargetingCheck {
    fun run() {
        val from = Vec3(0.5, 0.5, 0.5)
        val to = from.add(MacroTargeting.MAX_DISTANCE, 0.0, 0.0)
        fun box(x: Double) = AABB(x, 0.0, 0.0, x + 1.0, 1.0, 1.0)
        val far = box(100.0)
        val near = box(20.0)
        fun nearest(limit: Double, vararg boxes: AABB) =
            MacroTargeting.nearest(from, to, limit, boxes.asSequence()) { it }
        check(nearest(256.0 * 256.0, far, near) === near)
        check(nearest(256.0 * 256.0, far) === far) 
        check(nearest(10.0 * 10.0, far, near) == null) 
        check(nearest(19.5 * 19.5, near) == null) 
        check(nearest(256.0 * 256.0, box(257.0)) == null)
        check(nearest(256.0 * 256.0, box(0.0)) != null) 
        val offset = AABB(20.0, 0.6, 0.0, 21.0, 1.0, 1.0)
        check(nearest(256.0 * 256.0, offset) == null)
        check(nearest(256.0 * 256.0, offset.inflate(0.2)) != null)
        check(MacroTargeting.eligible(true, true, false, false, true))
        check(!MacroTargeting.eligible(false, true, false, false, true))
        check(!MacroTargeting.eligible(true, false, false, false, true))
        check(!MacroTargeting.eligible(true, true, true, false, true))
        check(!MacroTargeting.eligible(true, true, false, true, true))
        check(!MacroTargeting.eligible(true, true, false, false, false))
        val visited = mutableListOf<Int>()
        val gap = MacroTargeting.trace(from, to, { it.x < 16 }) { pos ->
            visited += pos.x
            null
        }
        check(visited == (0..15).toList()) 
        check(gap.block == null && gap.limitSqr == 15.5 * 15.5)
        check(nearest(gap.limitSqr, near, far) == null)
        val wall = MacroTargeting.trace(from, to, { true }) { pos ->
            if (pos.x == 10) BlockHitResult(box(10.0).clip(from, to).orElseThrow(), Direction.WEST, pos.immutable(), false) else null
        }
        check(wall.block == BlockPos(10, 0, 0) && wall.limitSqr == 9.5 * 9.5)
        check(nearest(wall.limitSqr, near) == null)
        val diagonal = from.add(Vec3(1.0, 1.0, 1.0).normalize().scale(256.0))
        var steps = 0
        MacroTargeting.trace(from, diagonal, { true }) { steps++; null }
        check(steps <= 3 * 257)
        check(runCatching { MacroTargeting.trace(from, from.add(257.0, 0.0, 0.0), { true }) { null } }.isFailure)
        val backwards = MacroTargeting.trace(from, from.add(-256.0, 0.0, 0.0), { it.x >= -16 }) { null }
        check(backwards.limitSqr == 16.5 * 16.5)
        val unloadedEye = MacroTargeting.trace(from, to, { false }) { error("Unloaded block queried") }
        check(unloadedEye.limitSqr == 0.0)
    }
}
