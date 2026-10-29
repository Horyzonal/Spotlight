package dev.spotlight.commands

import dev.spotlight.ui.CloneScreen
import net.minecraft.client.Minecraft

internal fun registerClone() {
    registerCommand("clone", "clone", "Choose clone mask and behavior, then select source corners and destination",
        unavailable = { regionCommandUnavailable("clone") }) {
        regionCommandUnavailable("clone")?.let { error(it) }
        val client = Minecraft.getInstance()
        client.gui.setScreen(CloneScreen(client.gui.screen()))
    }
}
