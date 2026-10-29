package dev.spotlight.commands

import dev.spotlight.ui.CurrentSongScreen
import net.minecraft.client.Minecraft

internal fun registerCurrentSong() {
    registerCommand("current_song", "Current song", "Now playing with generated artwork; pause, resume or stop current music") {
        val client = Minecraft.getInstance()
        client.gui.setScreen(CurrentSongScreen(client.gui.screen()))
    }
}
