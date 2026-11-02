package dev.spotlight.commands

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmLinkScreen


internal fun registerCatalog() {
    registerCommand("extensions_catalog", "Browse Forge extensions", "Open Modrinth Forge mod catalog in browser; no downloads or code loading") {
        val client = Minecraft.getInstance()
        ConfirmLinkScreen.confirmLinkNow("https://modrinth.com/mods?g=categories:forge", client.screen ?: net.minecraft.client.gui.screens.TitleScreen(), false)
    }
}
