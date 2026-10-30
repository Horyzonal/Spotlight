package dev.spotlight.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

fun spotlightButton(label: String, x: Int, y: Int, width: Int, height: Int = 20, onPress: () -> Unit): Button =
    object : Button(x, y, width.coerceAtLeast(1), height.coerceAtLeast(1), Component.literal(label),
        OnPress { onPress() }, CreateNarration { it.get() }) {
        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val highlighted = active && (isHovered || isFocused)
            graphics.fill(this.x, this.y, right, bottom, when {
                !active -> SpotlightTheme.color(0xFF19202B)
                highlighted -> SpotlightTheme.color(0xFF293F5B)
                else -> SpotlightTheme.color(0xFF161D29)
            })
            graphics.renderOutline(this.x, this.y, this.width, this.height,
                if (highlighted) SpotlightTheme.color(0xFF81B8FF) else SpotlightTheme.color(0xFF415877))
            val font = Minecraft.getInstance().font
            graphics.enableScissor(this.x, this.y, right, bottom)
            graphics.themeText(font, message, this.x + (this.width - font.width(message)) / 2,
                this.y + (this.height - font.lineHeight) / 2,
                if (active) SpotlightTheme.color(0xFFF2F5FC) else SpotlightTheme.color(0xFF91A0B5))
            graphics.disableScissor()
        }
    }

internal fun spotlightHints(graphics: GuiGraphics, font: Font, width: Int, height: Int, text: String) {
    val lines = font.split(Component.literal(text), (width - 16).coerceAtLeast(1))
    graphics.enableScissor(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
    lines.forEachIndexed { index, line ->
        graphics.themeText(font, line, 8, height - 4 - (lines.size - index) * font.lineHeight, SpotlightTheme.color(0xFF9DADC3))
    }
    graphics.disableScissor()
}
