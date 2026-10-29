package dev.spotlight.platform

import dev.spotlight.core.LocationStore
import dev.spotlight.core.SavedLocation
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.world.level.storage.LevelData
import java.util.UUID

object LocationActions {
    private var connection: ClientPacketListener? = null
    private var pendingTicks = 0

    fun current(name: String = "Current location"): SavedLocation {
        val player = requireNotNull(Minecraft.getInstance().player) { "Join a world first" }
        return SavedLocation(UUID.randomUUID().toString(), name, player.x, player.y, player.z,
            Spotlight.dimension(), Spotlight.scope())
    }

    fun lookedAt(name: String = "Look location"): SavedLocation {
        val client = Minecraft.getInstance()
        val pos = requireNotNull(MacroTargeting.capture(client, includeEntities = false).block) {
            "Look at a loaded block within 256 blocks"
        }
        return SavedLocation(UUID.randomUUID().toString(), name, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            Spotlight.dimension(), Spotlight.scope())
    }

    fun save(location: SavedLocation) {
        require(location.scope == Spotlight.scope()) { "World/server changed; create the location again" }
        require(location.dimension.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+"))) { "Enter a namespaced dimension ID" }
        Spotlight.locations().put(location)
    }

    
    fun spawn(): SavedLocation? = Spotlight.locations().list(Spotlight.scope()).firstOrNull { it.id == "spawn" }

    fun spawnStatus(): String = when {
        worldUnavailable() != null -> "Join a world first"
        spawn() != null -> "Saved world spawn; server teleport permissions required"
        pendingTicks > 0 -> "Waiting for server world spawn"
        else -> "World spawn unknown; server supplied no spawn location"
    }

    internal fun tick() {
        val client = Minecraft.getInstance()
        if (connection !== client.connection) {
            connection = client.connection
            pendingTicks = if (connection == null) 0 else 100
        }
        if (pendingTicks == 0) return
        pendingTicks--
        try {
            if (client.level != null && worldUnavailable() == null) {
                if (spawn() != null) {
                    pendingTicks = 0
                    return
                }
                
                val data: LevelData.RespawnData? = client.level!!.levelData.respawnData
                if (data != null) {
                    saveSpawn(Spotlight.locations(), spawnDraft(data, Spotlight.scope()))
                    pendingTicks = 0
                    return
                }
            }
            if (pendingTicks == 0) notifySpotlight("World spawn unknown: server supplied no spawn location.")
        } catch (failure: Exception) {
            pendingTicks = 0
            notifySpotlight("Cannot save world spawn: ${failure.message ?: failure.javaClass.simpleName}")
        }
    }

    internal fun spawnDraft(data: LevelData.RespawnData, scope: String): SavedLocation {
        val global = data.globalPos()
        val pos = global.pos()
        return SavedLocation("spawn", "Spawnpoint location", pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            global.dimension().identifier().toString(), scope)
    }

    internal fun saveSpawn(store: LocationStore, draft: SavedLocation) {
        if (store.list(draft.scope).none { it.id == draft.id }) store.put(draft)
    }
}
