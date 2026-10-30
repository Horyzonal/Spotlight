package dev.spotlight.ui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FormattedCharSequence
import org.joml.Matrix4f

internal object Ui1211Check {
    @JvmStatic fun main(args: Array<String>) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val scroll = object : UiScrollContainer(0, 0, 100, 100, Component.empty()) {
            override fun children(): List<GuiEventListener> = emptyList()
            override fun contentHeight() = 400
            override fun scrollRate() = 20.0
            override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {}
            override fun updateWidgetNarration(output: NarrationElementOutput) {}
        }
        check(!scroll.mouseScrolled(101.0, 10.0, 0.0, -1.0))
        check(scroll.mouseScrolled(10.0, 10.0, 0.0, -1.0))
        check(scroll.scrollAmount() == 20.0)
        scroll.setScrollAmount(1000.0)
        check(scroll.scrollAmount() == 300.0)
        check(!scroll.mouseScrolled(10.0, 10.0, 0.0, -1.0))
        check(scroll.mouseClicked(97.0, 50.0, 0))
        check(scroll.mouseDragged(97.0, 0.0, 0, 0.0, -100.0))
        check(scroll.scrollAmount() == 0.0)
        scroll.mouseReleased(97.0, 0.0, 0)
        check(!scroll.mouseDragged(97.0, 50.0, 0, 0.0, 50.0))

        var drawnShadow: Boolean? = null
        val native = object : Font({ error("Shadow check must not load glyphs") }, false) {
            override fun drawInBatch(text: FormattedCharSequence, x: Float, y: Float, color: Int, shadow: Boolean,
                matrix: Matrix4f, buffers: MultiBufferSource, mode: Font.DisplayMode, background: Int, light: Int): Int {
                drawnShadow = shadow
                return 0
            }
        }
        val themed = catalogFont(native)
        val buffers = MultiBufferSource { error("Shadow check must not render") }
        val configField = SpotlightTheme::class.java.getDeclaredField("shadow").also { it.isAccessible = true }
        val original = SpotlightTheme.textShadow()
        try {
            for (enabled in listOf(false, true)) {
                configField.setBoolean(SpotlightTheme, enabled)
                themed.drawInBatch(FormattedCharSequence.EMPTY, 0f, 0f, -1, true, Matrix4f(), buffers, Font.DisplayMode.NORMAL, 0, 0)
                check(drawnShadow == enabled)
            }
        } finally { configField.setBoolean(SpotlightTheme, original) }
        val model = net.minecraft.client.renderer.blockentity.BannerRenderer.createBodyLayer().bakeRoot()
        check(model.getChild("flag") != null)
        check(net.minecraft.client.model.geom.ModelLayers.BANNER != null)
        check(net.minecraft.client.Minecraft::class.java.getMethod("getToasts").returnType ==
            net.minecraft.client.gui.components.toasts.ToastComponent::class.java)
        net.minecraft.client.renderer.blockentity.BannerRenderer::class.java.getMethod("renderPatterns",
            com.mojang.blaze3d.vertex.PoseStack::class.java, MultiBufferSource::class.java,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            net.minecraft.client.model.geom.ModelPart::class.java,
            net.minecraft.client.resources.model.Material::class.java, Boolean::class.javaPrimitiveType,
            net.minecraft.world.item.DyeColor::class.java,
            net.minecraft.world.level.block.entity.BannerPatternLayers::class.java)
        check(ResourceLocation.withDefaultNamespace("container/loom/pattern").path == "container/loom/pattern")
        MultilineInputCheck.main(emptyArray())
        MenuLayoutCheck.main(emptyArray())
        PaletteClickCheck.main(emptyArray())
        check(primaryModifier(KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0,
            if (macShortcuts) org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER else org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL)))
        println("1.21.1 UI scroll, drag, native font shadow and input checks passed")
    }
}
