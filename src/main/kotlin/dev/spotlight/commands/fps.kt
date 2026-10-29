package dev.spotlight.commands

import dev.spotlight.platform.reportDebug
import net.minecraft.client.Minecraft

internal fun registerFps() {
    registerCommand("fps", "FPS frame rate", "F3 current client frames per second; available outside worlds") {
        reportDebug("FPS", "${Minecraft.getInstance().fps} frames/s")
    }
}
