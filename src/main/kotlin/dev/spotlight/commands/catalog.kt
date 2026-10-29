package dev.spotlight.commands

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import java.net.URI

internal fun registerCatalog() {
    registerCommand("extensions_catalog", "Browse Fabric extensions", "Open Modrinth Fabric mod catalog in browser; no downloads or code loading") {
        val client = Minecraft.getInstance()
        ConfirmLinkScreen.confirmLinkNow(client.gui.screen(), URI.create("https://modrinth.com/mods?g=categories:fabric"))
    }
}
