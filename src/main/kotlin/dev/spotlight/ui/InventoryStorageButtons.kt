package dev.spotlight.ui

import net.neoforged.neoforge.client.event.ScreenEvent
import net.minecraft.client.gui.components.Button
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen

object InventoryStorageButtons {

    fun attach(event: ScreenEvent.Init.Post) {
        val screen = event.screen
        if (screen !is InventoryScreen) return
        val buttons = event.listenersList.filterIsInstance<Button>()
        val labels = listOf("Client chest", "Saved kits", "Item archive")
        if (buttons.any { it.message.string == labels.first() }) return
        labels.forEachIndexed { index, label ->
            event.addListener(catalogButton(label, 4, 4 + index * 24, 104) {
                val next = when (index) { 0 -> ClientChestScreen(screen); 1 -> KitScreen(screen); else -> ArchiveScreen(screen) }
                Minecraft.getInstance().setScreen(next)
            })
        }
    }
}

internal class StorageConfirmationScreen(parent: Screen, private val confirm: () -> Unit) : StorageScreen(parent, "Restore saved vault revision?") {
    override fun build() {
        text("Current vault remains in revision history. Real items never change.")
        row("Cancel", "Restore revision") { if (it == 0) onClose() else { onClose(); confirm() } }
    }
}
