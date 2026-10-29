package dev.spotlight.platform

import dev.spotlight.core.LocationAdapter
import dev.spotlight.core.LocationStore
import dev.spotlight.core.LocationSync
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal object LocationAdapters {
    private val adapters = linkedMapOf<String, LocationAdapter>()
    fun register(adapter: LocationAdapter) {
        require(adapter.id.matches(Regex("[a-z0-9_.-]{1,64}"))) { "Invalid location adapter ID" }
        require(adapter.id !in adapters) { "Duplicate location adapter: ${adapter.id}" }
        adapters[adapter.id] = adapter
    }

    fun status(): String = if (adapters.isEmpty())
        "Map sync unsupported: no verified Xaero/JourneyMap adapter registered"
    else "Map adapters: ${adapters.keys.joinToString()}"

    fun sync(): String {
        if (adapters.isEmpty()) return status().also(::notifySpotlight)
        val scope = Spotlight.scope()
        val store = Spotlight.locations()
        val directory = FabricLoader.getInstance().configDir
        val results = adapters.map { (id, adapter) ->
            try {
                val basePath = directory.resolve("spotlight-sync-$id.dat")
                val base = LocationStore(basePath).list(scope)
                val local = store.list(scope)
                val remote = adapter.snapshot(scope)
                val result = LocationSync.reconcile(scope, base, local, remote)
                val merged = result.merged
                if (merged == null) "$id: ${result.conflicts.size} conflicts; no writes"
                else {
                    check(adapter.compareAndSet(scope, remote, merged)) { "Map changed during sync; retry" }
                    
                    merged.forEach(store::put)
                    local.filter { old -> merged.none { it.id == old.id } }.forEach { store.remove(scope, it.id) }
                    val temporary = Files.createTempFile(directory, ".spotlight-sync-", ".dat")
                    try {
                        Files.copy(basePath, temporary, REPLACE_EXISTING)
                    } catch (_: java.nio.file.NoSuchFileException) { Files.deleteIfExists(temporary) }
                    try {
                        val nextBase = LocationStore(temporary)
                        nextBase.list(scope).forEach { nextBase.remove(scope, it.id) }
                        merged.forEach(nextBase::put)
                        
                        if (Files.exists(temporary)) Files.move(temporary, basePath, ATOMIC_MOVE, REPLACE_EXISTING)
                    } finally { Files.deleteIfExists(temporary) }
                    "$id: synced ${merged.size} locations"
                }
            } catch (failure: Exception) { "$id: sync failed: ${failure.message}" }
        }
        return results.joinToString("; ").also(::notifySpotlight)
    }
}
