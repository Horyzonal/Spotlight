package dev.spotlight.commands

import dev.spotlight.platform.CreativeText
import dev.spotlight.ui.TextEditorScreen
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

internal fun registerText() {
    registerCommand("text_editor", "Text editor", "Formatted text, gradient, rainbow, interleaved colors, item name and lore") {
        val client = Minecraft.getInstance()
        client.gui.setScreen(TextEditorScreen(client.gui.screen()))
    }
    registerCommand("copy_item_data", "Copy held item JSON", "Copy main-hand item components and count") {
        val client = Minecraft.getInstance()
        client.keyboardHandler.clipboard = CreativeText.copyItemData()
        client.gui.hud.chat.addClientSystemMessage(Component.literal("Held item JSON copied"))
    }
    registerCommand("clear_item_name", "Clear held item name", "Requires Creative; preserve other item data") {
        CreativeText.clearName()
        Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(Component.literal("Item name clear requested"))
    }
    registerCommand("clear_item_lore", "Clear held item lore", "Requires Creative; preserve other item data") {
        CreativeText.clearLore()
        Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(Component.literal("Item lore clear requested"))
    }
}
