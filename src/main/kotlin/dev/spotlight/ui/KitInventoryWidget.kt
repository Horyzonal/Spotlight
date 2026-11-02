package dev.spotlight.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

/** Focusable preview only: no click, drag, or item-creation handlers. */
internal class KitInventoryWidget(x: Int, y: Int, private val stack: ItemStack, label: String, private val emptySprite: String? = null) :
    AbstractWidget(x, y, 20, 20, catalogText(if (stack.isEmpty) label else "$label: ${stack.hoverName.string} ×${stack.count}")) {
    override fun extractWidgetRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val client = Minecraft.getInstance()
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("container/slot"), x, y, 20, 20)
        if (isHoveredOrFocused) g.fill(x + 1, y + 1, right - 1, bottom - 1, 0x40667788)
        if (!stack.isEmpty) {
            g.fakeItem(stack, x + 2, y + 2)
            g.itemDecorations(client.font, stack, x + 2, y + 2)
        } else if (emptySprite != null) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("container/slot/$emptySprite"), x + 2, y + 2, 16, 16)
        }
        if (isHoveredOrFocused) {
            val tooltipX = if (isHovered) mouseX else x + width
            val tooltipY = if (isHovered) mouseY else y
            if (stack.isEmpty) g.setTooltipForNextFrame(message, tooltipX, tooltipY)
            else g.setTooltipForNextFrame(client.font, stack, tooltipX, tooltipY)
        }
    }
    override fun updateWidgetNarration(output: NarrationElementOutput) { defaultButtonNarrationText(output) }
}
