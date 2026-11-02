package dev.spotlight.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack


internal class KitInventoryWidget(x: Int, y: Int, private val stack: ItemStack, label: String, private val emptySprite: String? = null) :
    AbstractWidget(x, y, 20, 20, catalogText(if (stack.isEmpty) label else "$label: ${stack.hoverName.string} ×${stack.count}")) {
    override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val client = Minecraft.getInstance()
        g.blitSprite(ResourceLocation("minecraft", "container/slot"), x, y, 20, 20)
        if (isHoveredOrFocused) g.fill(x + 1, y + 1, right - 1, bottom - 1, 0x40667788)
        if (!stack.isEmpty) {
            g.renderFakeItem(stack, x + 2, y + 2)
            g.renderItemDecorations(client.font, stack, x + 2, y + 2)
        } else if (emptySprite != null) {
            g.blitSprite(ResourceLocation("minecraft", "container/slot/$emptySprite"), x + 2, y + 2, 16, 16)
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

internal fun net.minecraft.client.gui.screens.Screen.confirmExternalLink(uri: java.net.URI) {
    net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(uri.toString(), this, false)
}

internal fun GuiGraphics.blitSprite(id: ResourceLocation, x: Int, y: Int, width: Int, height: Int) {
    if (id.path == "container/slot") {
        blit(ResourceLocation("minecraft", "textures/gui/container/generic_54.png"), x, y, width, height, 7f, 17f, 18, 18, 256, 256)
    } else {
        val name = id.path.substringAfterLast('/')
        val sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
            .apply(ResourceLocation("minecraft", "item/$name"))
        blit(x, y, 0, width, height, sprite)
    }
}
