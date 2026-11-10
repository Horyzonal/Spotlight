package dev.spotlight.platform

import dev.spotlight.core.SavedLocation
import net.minecraft.client.Minecraft

import java.util.Locale
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.hypot

internal data class LookRotation(val yaw: Float, val pitch: Float)

internal fun lookRotation(eyeX: Double, eyeY: Double, eyeZ: Double, x: Double, y: Double, z: Double, yaw: Float): LookRotation {
    require(listOf(eyeX, eyeY, eyeZ, x, y, z).all { it.isFinite() } && yaw.isFinite()) { "Coordinates and yaw must be finite" }
    val dx = x - eyeX
    val dy = y - eyeY
    val dz = z - eyeZ
    require(listOf(dx, dy, dz).all { it.isFinite() }) { "Target is too far away" }
    require(dx != 0.0 || dy != 0.0 || dz != 0.0) { "Target coincides with your eye position" }
    val horizontal = hypot(dx, dz)
    return LookRotation(
        if (horizontal == 0.0) yaw else Math.toDegrees(atan2(-dx, dz)).toFloat(),
        -Math.toDegrees(atan2(dy, horizontal)).toFloat(),
    )
}

internal val lookDirections = listOf("North", "Northeast", "East", "Southeast", "South", "Southwest", "West", "Northwest", "Up", "Down")

internal fun lookDirection(name: String, yaw: Float): LookRotation {
    require(yaw.isFinite()) { "Yaw must be finite" }
    return when (name.trim().lowercase(Locale.ROOT)) {
        "n", "north" -> LookRotation(180f, 0f)
        "ne", "northeast", "north-east" -> LookRotation(-135f, 0f)
        "e", "east" -> LookRotation(-90f, 0f)
        "se", "southeast", "south-east" -> LookRotation(-45f, 0f)
        "s", "south" -> LookRotation(0f, 0f)
        "sw", "southwest", "south-west" -> LookRotation(45f, 0f)
        "w", "west" -> LookRotation(90f, 0f)
        "nw", "northwest", "north-west" -> LookRotation(135f, 0f)
        "up" -> LookRotation(yaw, -90f)
        "down" -> LookRotation(yaw, 90f)
        else -> error("Choose North, Northeast, East, Southeast, South, Southwest, West, Northwest, Up or Down")
    }
}

internal fun parseLookCoordinates(text: String, x: Double, y: Double, z: Double): List<Double> {
    require(text.length <= 512) { "Enter three coordinates: X Y Z; decimals and ~ offsets supported" }
    val parts = text.trim().split(Regex("\\s+"))
    require(parts.size == 3) { "Enter three coordinates: X Y Z; decimals and ~ offsets supported" }
    val number = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    return parts.mapIndexed { index, part ->
        val relative = part.startsWith('~')
        val value = if (relative) part.drop(1) else part
        require((relative && value.isEmpty()) || number.matches(value)) { "Invalid coordinate: $part" }
        val offset = if (value.isEmpty()) 0.0 else requireNotNull(value.toDoubleOrNull()) { "Invalid coordinate: $part" }
        val result = offset + if (relative) listOf(x, y, z)[index] else 0.0
        require(offset.isFinite() && result.isFinite()) { "Coordinates must be finite" }
        result
    }
}

internal object LookActions {
    fun at(x: Double, y: Double, z: Double) {
        val player = player()
        val eye = player.eyePosition
        rotate(lookRotation(eye.x, eye.y, eye.z, x, y, z, player.yRot))
    }

    fun coordinates(text: String) {
        val player = player()
        val target = parseLookCoordinates(text, player.x, player.y, player.z)
        at(target[0], target[1], target[2])
    }

    fun direction(name: String) = rotate(lookDirection(name, player().yRot))

    fun location(location: SavedLocation) {
        player()
        require(location.scope == Spotlight.scope()) { "Saved location belongs to another world/server" }
        require(location.dimension == Spotlight.dimension()) { "Saved location belongs to another dimension" }
        at(location.x, location.y, location.z)
    }

    fun head(id: UUID) {
        val player = player()
        val target = requireNotNull(player.level().getPlayerByUUID(id)?.takeIf { it !== player && it.isAlive && !it.isRemoved }) {
            "Player is no longer loaded or alive"
        }
        val eye = target.eyePosition
        at(eye.x, eye.y, eye.z)
    }

    private fun player(): net.minecraft.client.player.LocalPlayer {
        val client = Minecraft.getInstance()
        val player = requireNotNull(client.player) { "Join a world first" }
        require(client.level != null && player.level() === client.level) { "World changed; try again" }
        return player
    }

    private fun rotate(rotation: LookRotation) {
        val player = player()
        player.setYRot(rotation.yaw)
        player.setXRot(rotation.pitch)
        player.setYHeadRot(rotation.yaw)
        player.setYBodyRot(rotation.yaw)
    }
}
