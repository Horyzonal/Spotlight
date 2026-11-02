package dev.spotlight.commands

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import java.net.URI

internal fun registerCatalog() {
    registerCommand("extensions_catalog", "Browse NeoForge extensions", "Open Modrinth NeoForge mod catalog in browser; no downloads or code loading") {
        val client = Minecraft.getInstance()
        ConfirmLinkScreen.confirmLinkNow(client.screen ?: net.minecraft.client.gui.screens.TitleScreen(), URI.create("https://modrinth.com/mods?g=categories:neoforge"))
    }
}
