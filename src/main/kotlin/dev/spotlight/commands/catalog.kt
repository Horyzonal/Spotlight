package dev.spotlight.commands

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmLinkScreen


internal fun registerCatalog() {
    registerCommand("extensions_catalog", "Browse Fabric extensions", "Open Modrinth Fabric mod catalog in browser; no downloads or code loading") {
        val client = Minecraft.getInstance()
        ConfirmLinkScreen.confirmLinkNow("https://modrinth.com/mods?g=categories:fabric", client.screen ?: net.minecraft.client.gui.screens.TitleScreen(), false)
    }
}
