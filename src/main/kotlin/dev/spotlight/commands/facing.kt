package dev.spotlight.commands

import dev.spotlight.platform.reportDebug
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import java.util.Locale

internal fun debugNumber(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

internal fun debugAngle(value: Float): String = debugNumber(Mth.wrapDegrees(value).toDouble())

internal fun registerFacing() {
    registerCommand("facing", "Facing direction", "F3 cardinal direction, yaw and pitch angles", unavailable = ::worldUnavailable) {
        val player = requireNotNull(Minecraft.getInstance().player) { "Join a world first" }
        reportDebug("Facing direction", "${player.direction.name.lowercase(Locale.ROOT)}; yaw=${debugAngle(player.yRot)}°; pitch=${debugAngle(player.xRot)}° (positive pitch looks down)")
    }
}
