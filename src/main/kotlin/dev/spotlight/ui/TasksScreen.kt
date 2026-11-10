package dev.spotlight.ui

import dev.spotlight.platform.BackgroundTasks
import net.minecraft.client.gui.screens.Screen

class TasksScreen(parent: Screen? = null) : StorageScreen(parent, "Spotlight tasks") {
    private var previous = emptyList<BackgroundTasks.Task>()
    private var age = 0

    override fun build() {
        row("Stop all", "Back") { if (it == 0) { BackgroundTasks.stopAll(); rebuild() } else onClose() }
        previous = BackgroundTasks.snapshot()
        if (previous.isEmpty()) text("No ongoing tasks")
        previous.forEach { task ->
            text(task.title)
            text(task.detail)
            row("Stop · ${task.title}") { BackgroundTasks.stop(task.id); rebuild() }
        }
    }

    override fun tick() {
        super.tick()
        if (++age % 10 == 0 && previous != BackgroundTasks.snapshot()) rebuild()
    }
}
