package dev.spotlight.commands

import dev.spotlight.platform.reportDebug
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries

internal fun debugEffectDuration(ticks: Int, infinite: Boolean): String =
    if (infinite) "infinite" else "$ticks ticks (${debugNumber(ticks / 20.0)} s at 20 ticks/s)"

internal fun registerEffects() {
    registerCommand("effects", "Active status effects", "Player potion effects: localized names, IDs, amplifier, level and duration ticks/seconds", unavailable = ::worldUnavailable) {
        val player = requireNotNull(Minecraft.getInstance().player) { "Join a world first" }
        val effects = player.activeEffects.sortedBy { BuiltInRegistries.MOB_EFFECT.getKey(it.effect).toString() }
        reportDebug("Active status effects", effects.joinToString("; ") {
            val effect = it.effect
            "${effect.displayName.string} (${BuiltInRegistries.MOB_EFFECT.getKey(effect)}); amplifier=${it.amplifier}, level=${it.amplifier.toLong() + 1}; duration=${debugEffectDuration(it.duration, it.isInfiniteDuration)}"
        }.ifEmpty { "No active status effects" })
    }
}
