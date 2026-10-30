package dev.spotlight.ui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

internal class StyledTextPreview(x: Int, y: Int, width: Int, height: Int, private val font: Font) :
    UiScrollContainer(x, y, width, height, Component.empty()) {
    private fun lines() = font.split(message, (width - 12).coerceAtLeast(1))
    override fun children(): List<GuiEventListener> = emptyList()
    override fun contentHeight() = lines().size * font.lineHeight + 8
    override fun scrollRate() = font.lineHeight.toDouble()

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        setScrollAmount(scrollAmount())
        catalogFrame(graphics, this)
        graphics.enableScissor(x, y, right, bottom)
        try {
            lines().forEachIndexed { index, line ->
                val top = y + 4 + index * font.lineHeight - scrollAmount().toInt()
                if (top + font.lineHeight > y && top < bottom)
                    graphics.drawString(font, line, x + 4, top, -1, SpotlightTheme.textShadow())
            }
        } finally { graphics.disableScissor() }
        renderScrollbar(graphics)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, message)
    }
}
