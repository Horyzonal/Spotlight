package dev.spotlight.commands

import dev.spotlight.platform.CommandInput
import dev.spotlight.platform.FindTargets
import dev.spotlight.platform.worldUnavailable

internal fun registerEntities() {
    registerCommand("find_entities", "Find entities", "Loaded only; radius 32; highlights nearest 64", CommandInput.ENTITY, ::worldUnavailable) {
        FindTargets.entities(it.text)
    }
}
