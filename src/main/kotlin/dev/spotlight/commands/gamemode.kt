package dev.spotlight.commands

import dev.spotlight.platform.Spotlight
import dev.spotlight.platform.worldUnavailable

internal fun registerGamemode() {
    for ((mode, aliases) in listOf(
        "survival" to "gms, gm0",
        "creative" to "gmc, gm1",
        "adventure" to "gma, gm2",
        "spectator" to "gmsp, gm3",
    )) {
        registerCommand("gamemode_$mode", "Gamemode: ${mode.replaceFirstChar { it.uppercaseChar() }}",
            "Run /gamemode $mode ($aliases); server permission required", unavailable = ::worldUnavailable) {
            Spotlight.executeSlash("/gamemode $mode")
        }
    }
}
