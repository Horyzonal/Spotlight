package dev.spotlight.commands

import dev.spotlight.platform.CommandInput
import net.minecraft.client.Minecraft
import dev.spotlight.platform.Spotlight
import net.minecraft.core.registries.BuiltInRegistries

internal fun registerCopyItem() {
    registerCommand("copy_item_id", "Copy item ID", input = CommandInput.ITEM) { context ->
        val item = Spotlight.stack(requireNotNull(context.itemId) { "Choose an item" })
        Minecraft.getInstance().keyboardHandler.clipboard = BuiltInRegistries.ITEM.getKey(item.item).toString()
    }
}
