package dev.spotlight.platform

import kotlin.math.abs

internal object LookActionsCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        fun angle(x: Double, y: Double, z: Double) = lookRotation(10.25, 65.625, -3.75, 10.25 + x, 65.625 + y, -3.75 + z, 23f)
        val directions = listOf(
            Triple("North", 0.0 to -1.0, 180f), Triple("Northeast", 1.0 to -1.0, -135f),
            Triple("East", 1.0 to 0.0, -90f), Triple("Southeast", 1.0 to 1.0, -45f),
            Triple("South", 0.0 to 1.0, 0f), Triple("Southwest", -1.0 to 1.0, 45f),
            Triple("West", -1.0 to 0.0, 90f), Triple("Northwest", -1.0 to -1.0, 135f),
        )
        directions.forEach { (name, target, yaw) ->
            val actual = angle(target.first, 0.0, target.second)
            check(abs(abs(actual.yaw) - abs(yaw)) < 0.0001f && actual.pitch == 0f) { "$name: $actual" }
            if (abs(yaw) != 180f) check(actual.yaw == yaw)
            check(lookDirection(name, 23f) == LookRotation(yaw, 0f))
        }
        check(angle(0.0, 1.0, 0.0) == LookRotation(23f, -90f))
        check(angle(0.0, -1.0, 0.0) == LookRotation(23f, 90f))
        check(angle(0.0, 1.0, 1.0).pitch == -45f)
        check(lookDirection("up", 23f) == LookRotation(23f, -90f))
        check(lookDirection("down", -721f) == LookRotation(-721f, 90f))
        check(lookDirection(" NE ", 23f) == LookRotation(-135f, 0f))
        check(parseLookCoordinates("10.125 -64.5 .75", 0.0, 0.0, 0.0) == listOf(10.125, -64.5, 0.75))
        check(parseLookCoordinates("~ ~1.625 ~-2.25", 10.25, 64.0, -3.75) == listOf(10.25, 65.625, -6.0))
        check(parseLookCoordinates("1e2 ~ -.5", 1.0, 2.0, 3.0) == listOf(100.0, 2.0, -0.5))
        listOf("", "1 2", "1 2 3 4", "NaN 0 0", "Infinity 0 0", "1e999 0 0", "~NaN 0 0", "^1 0 0", "1,2,3").forEach {
            check(runCatching { parseLookCoordinates(it, 1.0, 2.0, 3.0) }.isFailure) { it }
        }
        check(runCatching { angle(0.0, 0.0, 0.0) }.isFailure)
        check(runCatching { angle(Double.NaN, 0.0, 0.0) }.isFailure)
        check(runCatching { angle(Double.POSITIVE_INFINITY, 0.0, 0.0) }.isFailure)
        check(runCatching { lookRotation(-Double.MAX_VALUE, 0.0, 0.0, Double.MAX_VALUE, 0.0, 0.0, 0f) }.isFailure)
        check(runCatching { lookDirection("invalid", 0f) }.isFailure)
        check(runCatching { lookDirection("north", Float.NaN) }.isFailure)
        println("LookActionsCheck passed")
    }
}
