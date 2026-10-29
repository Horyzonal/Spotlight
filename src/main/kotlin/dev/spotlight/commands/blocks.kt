package dev.spotlight.commands

import dev.spotlight.platform.CommandInput
import dev.spotlight.platform.FindTargets
import dev.spotlight.platform.worldUnavailable

internal fun registerBlocks() {
    registerCommand("find_blocks", "Find blocks", "Nearest loaded block; full render distance and build height; bounded scan", CommandInput.BLOCK, ::worldUnavailable) {
        FindTargets.blocks(it.text)
    }
}
