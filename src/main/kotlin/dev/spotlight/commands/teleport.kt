package dev.spotlight.commands

import dev.spotlight.platform.CommandInput
import dev.spotlight.platform.Spotlight
import dev.spotlight.platform.worldUnavailable


internal fun registerTeleport() {
    registerCommand("tp", "tp", "Teleport to preset; server permissions required", CommandInput.LOCATION, ::worldUnavailable) { context ->
        val location = Spotlight.locations().list(Spotlight.scope()).firstOrNull { it.id == context.text }
            ?: error("Choose a location from this world/server")
        require(location.dimension == Spotlight.dimension()) { "Location belongs to ${location.dimension}; change dimension first" }
        require(location.x in -30_000_000.0..30_000_000.0 && location.z in -30_000_000.0..30_000_000.0 &&
            location.y in -20_000_000.0..20_000_000.0) { "Location outside vanilla teleport bounds" }
        Spotlight.executeSlash("/tp @s ${location.x} ${location.y} ${location.z}")
    }
}
