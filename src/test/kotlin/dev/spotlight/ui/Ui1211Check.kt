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
        var typed = false
        val child = object : net.minecraft.client.gui.components.AbstractWidget(0, 0, 20, 20, Component.empty()) {
            override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {}
            override fun updateWidgetNarration(output: NarrationElementOutput) {}
            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) = button == 0 && isMouseOver(mouseX, mouseY)
            override fun charTyped(character: Char, modifiers: Int): Boolean { typed = character == 'x'; return typed }
        }
        val scroll = object : UiScrollContainer(0, 0, 100, 100, Component.empty()) {
            override fun children(): List<GuiEventListener> = listOf(child)
            override fun contentHeight() = 400
            override fun scrollRate() = 20.0
            override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {}
            override fun updateWidgetNarration(output: NarrationElementOutput) {}
        }
        check(!scroll.mouseScrolled(101.0, 10.0, -1.0))
        check(scroll.mouseScrolled(10.0, 10.0, -1.0))
        check(scroll.scrollAmount() == 20.0)
        scroll.setScrollAmount(1000.0)
        check(scroll.scrollAmount() == 300.0)
        check(!scroll.mouseScrolled(10.0, 10.0, -1.0))
        check(scroll.mouseClicked(97.0, 50.0, 0))
        check(scroll.mouseDragged(97.0, 0.0, 0, 0.0, -100.0))
        check(scroll.scrollAmount() == 0.0)
        scroll.mouseReleased(97.0, 0.0, 0)
        check(!scroll.mouseDragged(97.0, 50.0, 0, 0.0, 50.0))

        check(scroll.mouseClicked(5.0, 5.0, 0))
        check(scroll.focused === child && child.isFocused)
        check(scroll.charTyped('x', 0) && typed)
        scroll.setFocused(false)
        check(scroll.focused == null && !child.isFocused)
        scroll.setRectangle(120, 80, 2, 3)
        check(scroll.right == 122 && scroll.bottom == 83)
        child.setRectangle(30, 20, 4, 5)
        check(child.right == 34 && child.bottom == 25)
        check(runCatching { child.setRectangle(30, 21, 4, 5) }.isFailure)
        val pattern = net.minecraft.core.registries.BuiltInRegistries.BANNER_PATTERN.getHolderOrThrow(
            net.minecraft.world.level.block.entity.BannerPatterns.STRIPE_BOTTOM)
        val layer = dev.spotlight.platform.BannerLayer(pattern, net.minecraft.world.item.DyeColor.RED)
        check((layer.description().contents as net.minecraft.network.chat.contents.TranslatableContents).key ==
            "block.minecraft.banner.stripe_bottom.red")

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
        themed.drawInBatch(FormattedCharSequence.EMPTY, 0f, 0f, -1, true, Matrix4f(), buffers, Font.DisplayMode.NORMAL, 0, 0)
        check(drawnShadow == SpotlightTheme.textShadow())
        themed.drawInBatch(FormattedCharSequence.EMPTY, 0f, 0f, -1, false, Matrix4f(), buffers, Font.DisplayMode.NORMAL, 0, 0)
        check(drawnShadow == false)
        val model = net.minecraft.client.renderer.blockentity.BannerRenderer.createBodyLayer().bakeRoot()
        check(model.getChild("flag") != null)
        check(net.minecraft.client.model.geom.ModelLayers.BANNER != null)
        check(ResourceLocation("textures/gui/container/loom.png").path == "textures/gui/container/loom.png")
        MultilineInputCheck.main(emptyArray())
        MenuLayoutCheck.main(emptyArray())
        PaletteClickCheck.main(emptyArray())
        check(primaryModifier(KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0,
            if (macShortcuts) org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER else org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL)))
        println("1.20.1 UI scroll, drag, native font shadow and input checks passed")
    }
}
