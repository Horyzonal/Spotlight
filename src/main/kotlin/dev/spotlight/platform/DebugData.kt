package dev.spotlight.platform

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

internal fun reportDebug(label: String, value: String) {
    Minecraft.getInstance().gui.chat.addMessage(Component.literal("[Spotlight] $label: $value"))

}
