package dev.spotlight.commands

import dev.spotlight.platform.CreativeText
import dev.spotlight.platform.notifySpotlight

internal fun registerUnbreakable() {
    registerCommand("unbreakable", "Make holding item unbreakable", "Requires Creative; preserves other item data") {
        CreativeText.makeUnbreakable()
        notifySpotlight("Unbreakable item update requested")
    }
}
