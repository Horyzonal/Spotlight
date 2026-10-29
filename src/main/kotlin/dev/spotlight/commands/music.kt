package dev.spotlight.commands

import dev.spotlight.ui.MusicPlayerScreen
import net.minecraft.client.Minecraft

internal fun registerMusic() {
    registerCommand("music_player", "Music player", "Search loaded Minecraft music and loop selected track client-side") {
        val client = Minecraft.getInstance()
        client.setScreen(MusicPlayerScreen(client.screen))
    }
}
