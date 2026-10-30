package dev.spotlight.commands

import dev.spotlight.platform.reportDebug
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft

internal fun registerDimension() {
    registerCommand("dimension_info", "Dimension info", "Dimension registry ID, build height and sea level", unavailable = ::worldUnavailable) {
        val level = requireNotNull(Minecraft.getInstance().level) { "Join a world first" }
        reportDebug("Dimension info", "${level.dimension().location()}; min Y=${level.minY}; max Y=${level.maxY} (inclusive); height=${level.height}; sea level=${level.seaLevel}")
    }
}
