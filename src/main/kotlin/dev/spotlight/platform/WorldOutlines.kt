package dev.spotlight.platform

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.world.phys.AABB

internal fun drawWorldOutline(context: WorldRenderContext, box: AABB, color: Int, width: Float) {
    val poses = context.matrixStack() ?: return
    val camera = context.camera().position
    val lines = RenderType.lines()
    poses.pushPose()
    try {
        poses.translate(-camera.x, -camera.y, -camera.z)
        val buffer = Tesselator.getInstance().builder
                buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL)
        LevelRenderer.renderLineBox(poses, buffer, box,
            (color ushr 16 and 255) / 255f, (color ushr 8 and 255) / 255f,
            (color and 255) / 255f, (color ushr 24) / 255f)
        lines.setupRenderState()
        try {
            // Selection and search markers stay visible through terrain, like always-on-top gizmos.
            RenderSystem.disableDepthTest()
            RenderSystem.depthMask(false)
            RenderSystem.lineWidth(width)
            BufferUploader.drawWithShader(buffer.end())
        } finally {
            RenderSystem.enableDepthTest()
            RenderSystem.depthMask(true)
            lines.clearRenderState()
        }
    } finally {
        poses.popPose()
    }
}
