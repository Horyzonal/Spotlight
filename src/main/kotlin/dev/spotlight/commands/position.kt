package dev.spotlight.commands

import dev.spotlight.platform.reportDebug
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft

internal fun registerPosition() {
    registerCommand("position", "Position details", "F3 precise XYZ, block coordinates and eye position; no clipboard changes", unavailable = ::worldUnavailable) {
        val player = requireNotNull(Minecraft.getInstance().player) { "Join a world first" }
        val pos = player.position()
        val block = player.blockPosition()
        val eye = player.eyePosition
        reportDebug("Position details", "XYZ=${debugNumber(pos.x)}, ${debugNumber(pos.y)}, ${debugNumber(pos.z)}; block=${block.x}, ${block.y}, ${block.z}; eye XYZ=${debugNumber(eye.x)}, ${debugNumber(eye.y)}, ${debugNumber(eye.z)}")
    }
}
