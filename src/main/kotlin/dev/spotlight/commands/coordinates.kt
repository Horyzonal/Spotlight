package dev.spotlight.commands

import net.minecraft.client.Minecraft
import dev.spotlight.platform.worldUnavailable
import java.util.Locale

internal fun registerCoordinates() {
    registerCommand("copy_coordinates", "Copy coordinates", unavailable = ::worldUnavailable) {
        Minecraft.getInstance().keyboardHandler.clipboard = coordinates()
    }
}

internal fun coordinates(): String {
    val player = requireNotNull(Minecraft.getInstance().player) { "Join a world first" }
    return String.format(Locale.ROOT, "%.2f %.2f %.2f", player.x, player.y, player.z)
}
