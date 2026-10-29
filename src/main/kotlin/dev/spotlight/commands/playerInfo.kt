package dev.spotlight.commands

import dev.spotlight.platform.reportDebug
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft

internal fun registerPlayerInfo() {
    registerCommand("player_info", "Player information", "Health, max health, armor, absorption, hunger, saturation, XP and gamemode", unavailable = ::worldUnavailable) {
        val client = Minecraft.getInstance()
        val player = requireNotNull(client.player) { "Join a world first" }
        val food = player.foodData
        val mode = client.gameMode?.playerMode?.name ?: "unavailable"
        reportDebug("Player information", "health=${player.health}/${player.maxHealth}; armor=${player.armorValue}; absorption=${player.absorptionAmount}; hunger=${food.foodLevel}/20; saturation=${food.saturationLevel}; XP level=${player.experienceLevel}, total=${player.totalExperience}, progress=${debugNumber(player.experienceProgress * 100.0)}%; gamemode=$mode")
    }
}
