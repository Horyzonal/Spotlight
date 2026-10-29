package dev.spotlight.commands

import dev.spotlight.platform.worldUnavailable
import dev.spotlight.ui.FillScreen
import net.minecraft.client.Minecraft

internal fun registerFill() {
    registerCommand("fill", "fill", "fill {block} {type filling}; choose block and mode, then select two corners",
        unavailable = { regionCommandUnavailable("fill") }) {
        regionCommandUnavailable("fill")?.let { error(it) }
        val client = Minecraft.getInstance()
        client.setScreen(FillScreen(client.screen))
    }
}

internal fun regionCommandUnavailable(command: String): String? = worldUnavailable() ?:
    if (Minecraft.getInstance().connection?.commands?.root?.getChild(command) == null) {
        "/$command unavailable: server command not advertised (permission required or command absent)"
    } else null
