package dev.spotlight.core

import java.nio.file.Path


data class SavedLocation(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val dimension: String,
    val scope: String,
)

class LocationStore(private val path: Path) {
    private var locations = linkedMapOf<Pair<String, String>, SavedLocation>()

    init {
        CoreStorage.read(path, "spotlight.locations.1") { input ->
            repeat(CoreStorage.count(input, 4096)) {
                val location = SavedLocation(input.readUTF(), input.readUTF(), input.readDouble(),
                    input.readDouble(), input.readDouble(), input.readUTF(), input.readUTF())
                validateLocation(location)
                val key = location.scope to location.id
                require(key !in locations) { "Duplicate location: $key" }
                locations[key] = location
            }
        }
    }

    fun list(scope: String): List<SavedLocation> {
        CoreStorage.text(scope, "Scope", 256)
        return locations.values.filter { it.scope == scope }.sortedWith(compareBy({ it.name }, { it.id }))
    }

    
    fun put(location: SavedLocation) {
        validateLocation(location)
        val next = LinkedHashMap(locations)
        next[location.scope to location.id] = location
        save(next)
        locations = next
    }

    fun remove(scope: String, id: String) {
        CoreStorage.text(scope, "Scope", 256)
        CoreStorage.text(id, "Location ID", 128)
        if ((scope to id) !in locations) return
        val next = LinkedHashMap(locations)
        next.remove(scope to id)
        save(next)
        locations = next
    }

    private fun save(next: Map<Pair<String, String>, SavedLocation>) {
        require(next.size <= 4096) { "Too many locations (maximum 4096)" }
        CoreStorage.write(path, "spotlight.locations.1") { output ->
            output.writeInt(next.size)
            for (location in next.values) {
                output.writeUTF(location.id)
                output.writeUTF(location.name)
                output.writeDouble(location.x)
                output.writeDouble(location.y)
                output.writeDouble(location.z)
                output.writeUTF(location.dimension)
                output.writeUTF(location.scope)
            }
        }
    }
}

internal fun validateLocation(location: SavedLocation) {
    CoreStorage.text(location.id, "Location ID", 128)
    CoreStorage.text(location.name, "Location name", 128)
    CoreStorage.text(location.dimension, "Dimension", 128)
    CoreStorage.text(location.scope, "Scope", 256)
    require(location.x.isFinite() && location.y.isFinite() && location.z.isFinite()) { "Coordinates must be finite" }
}


interface LocationAdapter {
    val id: String
    fun snapshot(scope: String): List<SavedLocation>

    
    fun compareAndSet(scope: String, expected: List<SavedLocation>, replacement: List<SavedLocation>): Boolean
}

data class LocationConflict(
    val id: String,
    val base: SavedLocation?,
    val local: SavedLocation?,
    val remote: SavedLocation?,
)

data class LocationReconciliation(
    
    val merged: List<SavedLocation>?,
    val conflicts: List<LocationConflict>,
)

object LocationSync {
    
    fun reconcile(
        scope: String,
        base: List<SavedLocation>,
        local: List<SavedLocation>,
        remote: List<SavedLocation>,
    ): LocationReconciliation {
        CoreStorage.text(scope, "Scope", 256)
        fun index(records: List<SavedLocation>): Map<String, SavedLocation> {
            require(records.size <= 4096) { "Too many sync locations (maximum 4096)" }
            val result = linkedMapOf<String, SavedLocation>()
            for (record in records) {
                validateLocation(record)
                require(record.scope == scope) { "Cross-scope sync location" }
                require(result.put(record.id, record) == null) { "Duplicate sync location ID: ${record.id}" }
            }
            return result
        }
        val before = index(base)
        val left = index(local)
        val right = index(remote)
        val merged = mutableListOf<SavedLocation>()
        val conflicts = mutableListOf<LocationConflict>()
        for (id in (before.keys + left.keys + right.keys).sorted()) {
            val b = before[id]
            val l = left[id]
            val r = right[id]
            val chosen = when {
                l == r -> l
                l == b -> r
                r == b -> l
                else -> { conflicts += LocationConflict(id, b, l, r); null }
            }
            if (chosen != null) merged += chosen
        }
        require(merged.size <= 4096) { "Merged locations exceed maximum 4096" }
        return LocationReconciliation(if (conflicts.isEmpty()) merged else null, conflicts)
    }
}
