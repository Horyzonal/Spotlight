package dev.spotlight.commands

import dev.spotlight.platform.reportDebug
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft

internal fun registerWorldTime() {
    registerCommand("world_time", "World time", "Day count, dayTime (overworld clock), gameTime and current dimension weather", unavailable = ::worldUnavailable) {
        val level = requireNotNull(Minecraft.getInstance().level) { "Join a world first" }
        val dayTime = level.dayTime
        reportDebug("World time", "day=${Math.floorDiv(dayTime, 24000L)} (zero-based); dayTime=$dayTime (overworld clock ticks); time of day=${Math.floorMod(dayTime, 24000L)}/24000; gameTime=${level.gameTime}; current dimension daytime=${level.isDay}; raining=${level.isRaining}; thundering=${level.isThundering}")
    }
}
