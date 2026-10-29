package dev.spotlight.commands

import dev.spotlight.platform.Spotlight
import dev.spotlight.platform.worldUnavailable

internal fun registerLocationSync() {
    registerCommand("sync_locations", "Sync map locations", "Conflict-safe registered map adapters", unavailable = ::worldUnavailable) {
        Spotlight.syncLocations()
    }
}
