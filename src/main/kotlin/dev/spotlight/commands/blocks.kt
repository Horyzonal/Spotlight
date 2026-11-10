package dev.spotlight.commands

import dev.spotlight.platform.CommandInput
import dev.spotlight.platform.FindTargets
import dev.spotlight.platform.worldUnavailable

internal fun registerBlocks() {
    registerCommand("find_blocks", "Find blocks", "Keep searching nearest loaded block across render distance and build height; stop in Tasks", CommandInput.BLOCK, ::worldUnavailable) {
        FindTargets.blocks(it.text)
    }
}
