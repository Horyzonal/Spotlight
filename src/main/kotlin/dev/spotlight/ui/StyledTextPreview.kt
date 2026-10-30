package dev.spotlight.ui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

internal class StyledTextPreview(x: Int, y: Int, width: Int, height: Int, private val font: Font) :
    UiScrollContainer(x, y, width, height, Component.empty()) {
    override fun children(): List<GuiEventListener> = emptyList()
    private fun lines() = font.split(
        if (message.string.isEmpty()) Component.literal("Preview appears as you type").withStyle { it.withColor(0x9DADC3) }
        else message, (width - 14).coerceAtLeast(1))
    override fun contentHeight() = lines().size * font.lineHeight + 8
    override fun scrollRate() = font.lineHeight.toDouble()

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        setScrollAmount(scrollAmount())
        catalogFrame(graphics, this)
        graphics.enableScissor(x + 4, y + 4, (right - 10).coerceAtLeast(x + 4), (bottom - 4).coerceAtLeast(y + 4))
        try {
            lines().forEachIndexed { index, line ->
                val top = y + 4 + index * font.lineHeight - scrollAmount().toInt()
                if (top + font.lineHeight > y + 4 && top < bottom - 4) {
                    graphics.drawString(font, line, x + 4, top, -1, SpotlightTheme.textShadow())
                }
            }
        } finally {
            graphics.disableScissor()
        }
        renderScrollbar(graphics)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
}

internal fun textEditorScrollChild(child: GuiEventListener?, mouseX: Double, mouseY: Double, vertical: Double): Boolean =
    when (child) {
        is UiScrollContainer -> child.mouseScrolled(mouseX, mouseY, vertical)
        is SpotlightMultiLineEditBox -> {
            val before = child.scrollAmount()
            child.mouseScrolled(mouseX, mouseY, vertical)
            before != child.scrollAmount()
        }
        is net.minecraft.client.gui.components.FittingMultiLineTextWidget -> child.mouseScrolled(mouseX, mouseY, vertical)
        else -> false
    }
