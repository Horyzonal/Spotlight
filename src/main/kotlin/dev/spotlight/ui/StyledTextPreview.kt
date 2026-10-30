package dev.spotlight.ui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractTextAreaWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.network.chat.Component

internal class StyledTextPreview(x: Int, y: Int, width: Int, height: Int, private val font: Font) :
    AbstractTextAreaWidget(x, y, width, height, Component.empty()) {
    private fun lines() = font.split(message, (width - totalInnerPadding()).coerceAtLeast(1))
    override fun getInnerHeight() = lines().size * font.lineHeight
    override fun scrollRate() = font.lineHeight.toDouble()

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        setScrollAmount(scrollAmount())
        super.renderWidget(graphics, mouseX, mouseY, delta)
    }

    override fun renderContents(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        lines().forEachIndexed { index, line ->
            graphics.drawString(font, line, innerLeft, innerTop + index * font.lineHeight, -1)
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, message)
    }
}
