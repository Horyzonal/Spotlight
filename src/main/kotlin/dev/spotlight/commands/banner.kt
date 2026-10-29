package dev.spotlight.commands

import dev.spotlight.ui.BannerEditorScreen
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.Minecraft

internal fun registerBanner() {
    registerCommand("banner_editor", "Banner editor", "Create banners and shields from base dye and registry patterns; Creative give", unavailable = ::worldUnavailable) {
        worldUnavailable()?.let { error(it) }
        val client = Minecraft.getInstance()
        client.setScreen(BannerEditorScreen(client.screen))
    }
}
