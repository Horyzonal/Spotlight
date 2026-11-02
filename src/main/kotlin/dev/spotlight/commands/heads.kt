package dev.spotlight.commands

import dev.spotlight.ui.HeadDatabaseScreen
import dev.spotlight.ui.PlayerSkullScreen
import net.minecraft.client.Minecraft

fun registerHeads() {
    registerCommand("head_database", "Head database", "Fuzzy local collection: vanilla skulls, online profiles and saved textured heads") {
        val client = Minecraft.getInstance()
        client.setScreen(HeadDatabaseScreen(client.screen))
    }
    registerCommand("player_skull", "Get player skull", "Resolve username or UUID; explicit offline lookup uses Mojang; Creative copy") {
        val client = Minecraft.getInstance()
        client.setScreen(PlayerSkullScreen(client.screen))
    }
}
