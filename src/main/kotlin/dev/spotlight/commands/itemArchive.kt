package dev.spotlight.commands

import dev.spotlight.ui.ArchiveScreen
import net.minecraft.client.Minecraft

fun registerItemArchive() {
    registerCommand("item_archive", "Item archive", "Observed stacks with modified components; raw registry data preserved") {
        val client = Minecraft.getInstance()
        client.gui.setScreen(ArchiveScreen(client.gui.screen()))
    }
}
