package dev.spotlight.commands

import dev.spotlight.platform.MacroTargeting
import dev.spotlight.platform.reportDebug
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.LivingEntity

internal fun registerTargetEntity() {
    registerCommand("targeted_entity", "Targeted entity details", "F3 looked-at entity name, UUID, type, XYZ and health; loaded only, 256-block range", unavailable = ::worldUnavailable) {
        val client = Minecraft.getInstance()
        val level = requireNotNull(client.level) { "Join a world first" }
        val id = MacroTargeting.capture(client, true).entity
        if (id == null) {
            reportDebug("Targeted entity", "No unobstructed entity target in loaded chunks within ${MacroTargeting.MAX_DISTANCE} blocks")
        } else {
            val entity = requireNotNull(level.entitiesForRendering().firstOrNull { it.isAlive && it.uuid.toString() == id }) { "Target entity is no longer loaded or alive" }
            val pos = entity.position()
            val health = if (entity is LivingEntity) "${entity.health}/${entity.maxHealth}" else "not applicable (non-living entity)"
            reportDebug("Targeted entity", "${entity.name.string}; UUID=$id; type=${BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)}; XYZ=${debugNumber(pos.x)}, ${debugNumber(pos.y)}, ${debugNumber(pos.z)}; health=$health")
        }
    }
}
