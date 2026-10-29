package dev.spotlight.commands

import dev.spotlight.platform.CommandContext
import dev.spotlight.platform.CommandInput
import dev.spotlight.platform.Spotlight
import dev.spotlight.platform.SpotlightCommand

internal fun registerCommand(
    id: String,
    title: String,
    description: String = "",
    input: CommandInput = CommandInput.NONE,
    unavailable: () -> String? = { null },
    action: (CommandContext) -> Unit,
) = Spotlight.register(SpotlightCommand("spotlight:$id", title, description, input, unavailable, action))
