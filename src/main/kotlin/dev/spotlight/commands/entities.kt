package dev.spotlight.commands

import dev.spotlight.platform.CommandInput
import dev.spotlight.platform.FindTargets
import dev.spotlight.platform.worldUnavailable

internal fun registerEntities() {
    registerCommand("find_entities", "Find entities", "Keep searching loaded entities across render distance; highlights nearest 64; stop in Tasks", CommandInput.ENTITY, ::worldUnavailable) {
        FindTargets.entities(it.text)
    }
}
