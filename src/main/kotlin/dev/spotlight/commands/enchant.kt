package dev.spotlight.commands

import dev.spotlight.platform.worldUnavailable
import dev.spotlight.ui.EnchantEditorScreen
import net.minecraft.client.Minecraft

fun registerCustomEnchantments() {
    fun unavailable(): String? = worldUnavailable()
        ?: if (Minecraft.getInstance().player?.mainHandItem?.isEmpty != false) "Hold an item" else null
    registerCommand("custom_enchant", "Custom enchantments", "Add enchantments to held item at levels 1..255; Creative mode required", unavailable = ::unavailable) {
        unavailable()?.let { error(it) }
        val client = Minecraft.getInstance()
        client.setScreen(EnchantEditorScreen(client.screen))
    }
}
