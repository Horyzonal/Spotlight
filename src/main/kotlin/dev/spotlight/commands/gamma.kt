package dev.spotlight.commands

import dev.spotlight.platform.CommandInput
import dev.spotlight.platform.ExtendedOptions

internal fun registerGamma() {
    registerCommand("gamma", "Gamma", "Decimal -2147483648–2147483647; no vanilla limits", CommandInput.NUMBER) { context ->
        ExtendedOptions.setGamma(context.text)
    }
}
