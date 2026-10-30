package dev.spotlight.commands

import dev.spotlight.platform.CommandInput
import net.minecraft.client.Minecraft
import dev.spotlight.platform.Spotlight
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component

internal fun registerInventory() {
    registerCommand("find_inventory", "Find item in inventory", "Reports matching slots; selects hotbar match", CommandInput.ITEM, ::worldUnavailable) { context ->
        val client = Minecraft.getInstance()
        val player = requireNotNull(client.player) { "Join a world first" }
        val item = Spotlight.stack(requireNotNull(context.itemId) { "Choose an item" }).item
        val inventory = player.inventory
        val slots = (0 until inventory.containerSize).filter { inventory.getItem(it).item == item && !inventory.getItem(it).isEmpty }
        check(slots.isNotEmpty()) { "Item not found in inventory" }
        slots.firstOrNull { it < 9 }?.let { inventory.selectedSlot = it }
        client.gui.chat.addMessage(Component.literal("${BuiltInRegistries.ITEM.getKey(item)}: " + slots.joinToString { slot ->
            when {
                slot < 9 -> "hotbar ${slot + 1}"
                slot < 36 -> "inventory ${slot - 8}"
                else -> "equipment slot $slot"
            }
        }))
        client.setScreen(InventoryScreen(player))
    }
}
