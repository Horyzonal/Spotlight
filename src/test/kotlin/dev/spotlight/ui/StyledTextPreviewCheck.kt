package dev.spotlight.ui

import com.mojang.blaze3d.font.GlyphInfo
import com.mojang.blaze3d.font.SheetGlyphInfo
import com.mojang.blaze3d.vertex.PoseStack
import dev.spotlight.core.TextFormatter
import dev.spotlight.core.TextMode
import dev.spotlight.core.TextStyle
import dev.spotlight.platform.CreativeText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.font.FontSet
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FormattedCharSequence
import org.joml.Matrix4f
import sun.misc.Unsafe
import java.util.function.Function

internal object StyledTextPreviewCheck {
    private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let { it.isAccessible = true; it.get(null) as Unsafe }
    private data class Draw(val text: String, val styles: List<Style>, val y: Int, val clip: ScreenRectangle?)
    private val draws = mutableListOf<Draw>()
    private var clip: ScreenRectangle? = null

    // Keep native drawString and Font splitting; replace only GPU calls and constructor side effects.
    private class CaptureGraphics : GuiGraphics(null, null) {
        private val clips = mutableListOf<ScreenRectangle>()
        override fun enableScissor(left: Int, top: Int, right: Int, bottom: Int) {
            val next = ScreenRectangle(left, top, right - left, bottom - top)
            clips += clips.lastOrNull()?.intersection(next) ?: next
            clip = clips.last()
        }
        override fun disableScissor() { clips.removeAt(clips.lastIndex); clip = clips.lastOrNull() }
        override fun containsPointInScissor(x: Int, y: Int) = clip?.containsPoint(x, y) ?: true
        override fun flush() {}
        override fun fill(left: Int, top: Int, right: Int, bottom: Int, color: Int) {}
        override fun renderOutline(x: Int, y: Int, width: Int, height: Int, color: Int) {}
        override fun blitSprite(sprite: ResourceLocation, x: Int, y: Int, width: Int, height: Int) {}
    }

    private fun put(target: Any, owner: Class<*>, name: String, value: Any?) {
        owner.getDeclaredField(name).also { it.isAccessible = true }.set(target, value)
    }

    @JvmStatic fun main(args: Array<String>) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val glyph = object : GlyphInfo {
            override fun getAdvance() = 6f
            override fun bake(factory: Function<SheetGlyphInfo, BakedGlyph>): BakedGlyph = error("No GPU")
        }
        val source = object : FontSet(null, ResourceLocation.withDefaultNamespace("test")) {
            override fun getGlyphInfo(codepoint: Int, filterFishyGlyphs: Boolean) = glyph
        }
        val font = object : Font({ source }, false) {
            override fun drawInBatch(text: FormattedCharSequence, x: Float, y: Float, color: Int, shadow: Boolean,
                matrix: Matrix4f, buffers: MultiBufferSource, mode: DisplayMode, background: Int, light: Int): Int {
                val value = StringBuilder()
                val styles = mutableListOf<Style>()
                text.accept { _, style, codepoint -> value.appendCodePoint(codepoint); styles += style; true }
                draws += Draw(value.toString(), styles, y.toInt(), clip)
                return value.length * 6
            }
        }
        val graphics = unsafe.allocateInstance(CaptureGraphics::class.java) as CaptureGraphics
        put(graphics, CaptureGraphics::class.java, "clips", mutableListOf<ScreenRectangle>())
        put(graphics, GuiGraphics::class.java, "pose", PoseStack())
        put(graphics, GuiGraphics::class.java, "bufferSource", unsafe.allocateInstance(MultiBufferSource.BufferSource::class.java))
        val themeFont = SpotlightTheme::class.java.getDeclaredField("fontId").also { it.isAccessible = true }
        val originalFont = themeFont.get(SpotlightTheme)
        try {
            themeFont.set(SpotlightTheme, ResourceLocation.withDefaultNamespace("uniform"))
            val preview = StyledTextPreview(10, 20, 120, 40, font)
            fun render() { draws.clear(); preview.render(graphics, 0, 0, 0f) }
            render()
            check(draws.all { it.text.isEmpty() })
            val styled = TextFormatter.format("A\nB", TextMode.GRADIENT,
                TextFormatter.parseColors("#FF0000, #0000FF"), TextStyle(bold = true, italic = true,
                    underlined = true, strikethrough = true, obfuscated = true))
            preview.message = CreativeText.component(styled)
            render()
            check(draws.map { it.text } == listOf("A", "B"))
            val styles = draws.flatMap { it.styles }
            check(styles.map { it.color?.value } == listOf(0xFF0000, 0x0000FF))
            check(styles.all { it.isBold && it.isItalic && it.isUnderlined && it.isStrikethrough && it.isObfuscated })
            check(styles.all { it.font == Style.DEFAULT_FONT })
            preview.message = Component.literal("replacement")
            render()
            check(draws.joinToString("") { it.text } == "replacement")
            preview.width = 30
            render()
            check(draws.size > 1)
            preview.message = Component.literal((1..30).joinToString("\n") { "line$it" })
            preview.width = 120
            preview.setScrollAmount(18.0)
            render()
            check(draws.first().text == "line2" && draws.first().y == 15)
            graphics.enableScissor(12, 24, 80, 45)
            render()
            check(draws.all { it.clip == ScreenRectangle(12, 24, 68, 21) })
            check(clip == ScreenRectangle(12, 24, 68, 21))
            graphics.disableScissor()
            check(clip == null)
            preview.setScrollAmount(Double.MAX_VALUE)
            check(!preview.mouseScrolled(20.0, 25.0, 0.0, -1.0))
            check(preview.mouseScrolled(20.0, 25.0, 0.0, 1.0))
            preview.message = Component.literal("short")
            render()
            check(preview.scrollAmount() == 0.0)
            preview.message = Component.empty()
            render()
            check(draws.all { it.text.isEmpty() })

            val details = catalogDetails(0, 0, 120, 60, Component.empty(), font)
            details.message = Component.literal("Current status")
            draws.clear()
            details.render(graphics, 0, 0, 0f)
            check(draws.joinToString("") { it.text } == "Current status")
            details.message = Component.literal("Updated status")
            draws.clear()
            details.render(graphics, 0, 0, 0f)
            check(draws.joinToString("") { it.text } == "Updated status")

            val client = unsafe.allocateInstance(Minecraft::class.java) as Minecraft
            val screen = TextEditorScreen(null)
            put(screen, Screen::class.java, "minecraft", client)
            put(screen, Screen::class.java, "font", font)
            screen.width = 640
            screen.height = 360
            TextEditorScreen::class.java.getDeclaredMethod("init").also { it.isAccessible = true }.invoke(screen)
            fun field(name: String): Any = TextEditorScreen::class.java.getDeclaredField(name).let { it.isAccessible = true; it.get(screen) }
            val form = field("form") as UiScrollContainer
            val input = field("editor") as SpotlightMultiLineEditBox
            val live = field("preview") as StyledTextPreview
            check(live.y == input.bottom + 24 && live.bottom <= form.bottom)
            check(form.children()[1] === live)
            fun wheel(widget: AbstractWidget, direction: Double) = form.mouseScrolled(widget.x + 8.0, widget.y + 8.0, 0.0, direction)
            live.message = Component.literal((1..80).joinToString("\n") { "preview $it" })
            check(wheel(live, -1.0) && live.scrollAmount() > 0 && form.scrollAmount() == 0.0)
            live.setScrollAmount(Double.MAX_VALUE)
            check(wheel(live, -1.0) && form.scrollAmount() == 24.0)
            form.setScrollAmount(0.0)
            input.value = (1..80).joinToString("\n") { "input $it" }
            val inputScroll = net.minecraft.client.gui.components.AbstractScrollWidget::class.java
                .getDeclaredMethod("setScrollAmount", Double::class.javaPrimitiveType).also { it.isAccessible = true }
            inputScroll.invoke(input, 0.0)
            check(wheel(input, -1.0) && input.scrollAmount() > 0 && form.scrollAmount() == 0.0)
            inputScroll.invoke(input, Double.MAX_VALUE)
            check(wheel(input, -1.0) && form.scrollAmount() == 24.0)
            form.setFocused(input)
            check(form.scrollAmount() == 24.0)
            TextEditorScreen::class.java.getDeclaredMethod("repositionElements").also { it.isAccessible = true }.invoke(screen)
            check(form.scrollAmount() == 24.0)
            form.setFocused(live)
            check(form.scrollAmount() == 24.0)
            println("Live preview native draw capture, styles, replacement, wrapping, clipping, nested wheel and focus checks passed")
        } finally { themeFont.set(SpotlightTheme, originalFont) }
    }
}
