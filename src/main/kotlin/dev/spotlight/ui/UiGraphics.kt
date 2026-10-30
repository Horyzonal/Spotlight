package dev.spotlight.ui

import com.mojang.blaze3d.platform.Lighting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.resources.model.ModelBakery
import net.minecraft.client.renderer.blockentity.BannerRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.chat.Component
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BannerPatternLayers

internal fun GuiGraphics.setTooltipForNextFrame(text: Component, x: Int, y: Int) {
    val client = Minecraft.getInstance()
    client.screen?.setTooltipForNextRenderPass(text) ?: renderTooltip(client.font, text, x, y)
}

internal fun GuiGraphics.setTooltipForNextFrame(font: Font, text: Component, x: Int, y: Int) =
    setTooltipForNextFrame(text, x, y)

internal fun GuiGraphics.setTooltipForNextFrame(font: Font, stack: ItemStack, x: Int, y: Int) =
    renderTooltip(font, stack, x, y)

internal fun GuiGraphics.renderBanner(flag: ModelPart, base: DyeColor, layers: BannerPatternLayers,
    left: Int, top: Int, right: Int, bottom: Int) {
    if (right <= left || bottom <= top) return
    flush()
    Lighting.setupForFlatItems()
    val pose = pose()
    val oldXRot = flag.xRot
    val oldY = flag.y
    pose.pushPose()
    try {
        pose.translate((left + right) / 2f, top.toFloat(), 100f)
        pose.scale((right - left) / 20f * 16f, (bottom - top) / 40f * 16f, -1f)
        flag.xRot = 0f
        flag.y = 0f
        drawSpecial { buffers ->
            BannerRenderer.renderPatterns(pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                flag, ModelBakery.BANNER_BASE, true, base, layers)
        }
        flush()
    } finally {
        flag.xRot = oldXRot
        flag.y = oldY
        pose.popPose()
        Lighting.setupFor3DItems()
    }
}
