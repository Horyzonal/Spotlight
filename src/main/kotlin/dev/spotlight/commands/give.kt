package dev.spotlight.commands

import dev.spotlight.platform.CommandInput
import net.minecraft.client.Minecraft
import dev.spotlight.platform.Spotlight
import dev.spotlight.platform.worldUnavailable

import dev.spotlight.platform.CommandContext

import net.minecraft.world.item.ItemStack

internal fun registerGive() {

    registerCommand("creative_give", "Give item in Creative", "Requires Creative and enough inventory space", CommandInput.ITEM_QUANTITY, ::creativeUnavailable, ::creativeGive)
}

private fun creativeUnavailable(): String? = worldUnavailable() ?: when {
    Minecraft.getInstance().player?.abilities?.instabuild != true -> "Creative mode required"
    Minecraft.getInstance().gameMode == null -> "Game mode unavailable"
    Minecraft.getInstance().player?.containerMenu !== Minecraft.getInstance().player?.inventoryMenu -> "Close the open container first"
    else -> null
}

internal fun creativePlan(existing: List<ItemStack>, incoming: ItemStack, quantity: Int, slotLimit: (ItemStack) -> Int): List<Pair<Int, ItemStack>> {
    require(quantity in 1..2304) { "Quantity must be between 1 and 2304" }
    require(!incoming.isEmpty) { "Choose a non-air item" }
    var remaining = quantity
    val changes = mutableListOf<Pair<Int, ItemStack>>()

    for (empty in listOf(false, true)) {
        for ((slot, current) in existing.withIndex()) {
            if (remaining == 0) break
            if (current.isEmpty != empty || (!empty && !ItemStack.isSameItemSameTags(current, incoming))) continue
            val count = if (empty) 0 else current.count
            val limit = minOf(incoming.maxStackSize, slotLimit(incoming))
            val added = minOf(remaining, (limit - count).coerceAtLeast(0))
            if (added > 0) {
                changes.add(slot to incoming.copyWithCount(count + added))
                remaining -= added
            }
        }
    }
    check(remaining == 0) { "Not enough inventory space for $quantity items" }
    return changes
}

private fun creativeGive(context: CommandContext) {
    creativeUnavailable()?.let { error(it) }
    val client = Minecraft.getInstance()
    val player = requireNotNull(client.player)
    val gameMode = requireNotNull(client.gameMode)
    val inventory = player.inventory
    val incoming = Spotlight.stack(requireNotNull(context.itemId) { "Choose an item" })
    val changes = creativePlan((0 until 36).map { inventory.getItem(it).copy() }, incoming, context.quantity) { inventory.maxStackSize }
    for ((slot, stack) in changes) {
        inventory.setItem(slot, stack)
        gameMode.handleCreativeModeItemAdd(stack.copy(), if (slot < 9) slot + 36 else slot)
    }
    inventory.setChanged()
}
