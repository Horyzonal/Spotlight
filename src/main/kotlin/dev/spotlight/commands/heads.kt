package dev.spotlight.commands

import dev.spotlight.ui.HeadDatabaseScreen
import dev.spotlight.ui.PlayerSkullScreen
import net.minecraft.client.Minecraft

fun registerHeads() {
    registerCommand("head_database", "Head database", "Search Minecraft Heads online catalog by name, tag and category") {
        val client = Minecraft.getInstance()
        client.setScreen(HeadDatabaseScreen(client.screen))
    }
    registerCommand("server_heads", "Server heads", "Search client-visible player profiles, equipment and inventory heads") {
        val client = Minecraft.getInstance()
        client.setScreen(HeadDatabaseScreen(client.screen, online = false))
    }
    registerCommand("player_skull", "Get player skull", "Resolve username or UUID; explicit offline lookup uses Mojang; Creative copy") {
        val client = Minecraft.getInstance()
        client.setScreen(PlayerSkullScreen(client.screen))
    }
}
