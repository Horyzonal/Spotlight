package dev.spotlight.commands

import dev.spotlight.ui.SoundManagerScreen
import net.minecraft.client.Minecraft

internal fun registerSounds() {
    registerCommand("sound_manager", "Sounds", "Manage playing sounds stop instance or manually play") {
        val client = Minecraft.getInstance()
        client.setScreen(SoundManagerScreen(client.screen))
    }
}
