package dev.spotlight.commands

import dev.spotlight.platform.CommandContext
import dev.spotlight.platform.LocationActions
import dev.spotlight.platform.Spotlight
import dev.spotlight.platform.worldUnavailable
import dev.spotlight.ui.AddLocationScreen
import net.minecraft.client.Minecraft

internal fun registerLocation() {
    registerCommand("add_location", "Add location", "Save current, looked-at, or entered coordinates", unavailable = ::worldUnavailable) {
        val client = Minecraft.getInstance()
        client.setScreen(AddLocationScreen(client.screen))
    }
    registerCommand("spawn_location", "Spawn location", "Teleport to saved world spawn; not a bed or respawn anchor", unavailable = {
        worldUnavailable() ?: if (LocationActions.spawn() == null) LocationActions.spawnStatus() else null
    }) {
        val teleport = Spotlight.commands.single { it.id == "spotlight:tp" }
        teleport.unavailable()?.let { error(it) }
        teleport.execute(CommandContext(text = "spawn"))
    }
}
