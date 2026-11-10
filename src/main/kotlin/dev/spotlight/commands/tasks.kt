package dev.spotlight.commands

import dev.spotlight.ui.TasksScreen
import net.minecraft.client.Minecraft

fun registerTasks() {
    registerCommand("tasks", "Tasks", "View and stop ongoing Spotlight searches") {
        val client = Minecraft.getInstance()
        client.gui.setScreen(TasksScreen(client.gui.screen()))
    }
}
