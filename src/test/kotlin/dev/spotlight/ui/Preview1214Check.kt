package dev.spotlight.ui

import com.mojang.blaze3d.font.GlyphInfo
import com.mojang.blaze3d.font.SheetGlyphInfo
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.font.FontSet
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FormattedCharSequence
import sun.misc.Unsafe
import java.util.function.Function

internal object Preview1214Check {
    private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let { it.isAccessible = true; it.get(null) as Unsafe }
    private class Capture : GuiGraphics(null, null) {
        lateinit var stack: PoseStack
        lateinit var drawn: MutableList<Pair<Int, Style>>
        override fun pose() = stack
        override fun enableScissor(left: Int, top: Int, right: Int, bottom: Int) {}
        override fun disableScissor() {}
        override fun blitSprite(type: Function<ResourceLocation, RenderType>, sprite: ResourceLocation, x: Int, y: Int, width: Int, height: Int) {}
        override fun drawString(font: Font, text: FormattedCharSequence, x: Int, y: Int, color: Int): Int {
            text.accept { _, style, codepoint -> drawn += codepoint to style; true }
            return 0
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val glyph = object : GlyphInfo {
            override fun getAdvance() = 6f
            override fun bake(factory: Function<SheetGlyphInfo, BakedGlyph>): BakedGlyph = error("No GPU in preview check")
        }
        val source = object : FontSet(null, ResourceLocation.withDefaultNamespace("test")) {
            override fun getGlyphInfo(codepoint: Int, filterFishyGlyphs: Boolean) = glyph
        }
        val font = Font({ source }, false)
        val graphics = (unsafe.allocateInstance(Capture::class.java) as Capture).also {
            it.stack = PoseStack()
            it.drawn = mutableListOf()
        }
        val preview = StyledTextPreview(0, 0, 120, 40, font)
        val style = Style.EMPTY.withColor(0xFF6699).withBold(true).withItalic(true)
            .withUnderlined(true).withStrikethrough(true).withObfuscated(true)
        val themeFont = SpotlightTheme::class.java.getDeclaredField("fontId").also { it.isAccessible = true }
        val originalFont = themeFont.get(SpotlightTheme)
        try {
            themeFont.set(SpotlightTheme, ResourceLocation.withDefaultNamespace("alt"))
            preview.message = Component.literal("Live").withStyle(style)
            preview.renderWidget(graphics, -1, -1, 0f)
            check(graphics.drawn.map { it.first.toChar() }.joinToString("") == "Live")
            check(graphics.drawn.all { it.second == style }) { "Preview changed component styling or theme font" }
            preview.message = Component.literal("Updated")
            graphics.drawn.clear()
            preview.renderWidget(graphics, -1, -1, 0f)
            check(graphics.drawn.map { it.first.toChar() }.joinToString("") == "Updated")
            preview.message = Component.literal((1..30).joinToString("\n") { "line $it" })
            check(preview.maxScrollAmount() > 0)
            check(preview.mouseScrolled(10.0, 10.0, 0.0, -1.0))
            check(preview.scrollAmount() > 0)
            preview.width = 40
            val narrow = preview.maxScrollAmount()
            preview.width = 120
            check(preview.maxScrollAmount() < narrow)
            preview.message = Component.empty()
            graphics.drawn.clear()
            preview.renderWidget(graphics, -1, -1, 0f)
            check(graphics.drawn.isEmpty())
            check(preview.scrollAmount() == 0.0)
        } finally { themeFont.set(SpotlightTheme, originalFont) }

        val details = catalogDetails(0, 0, 120, 40, Component.empty(), font)
        details.message = Component.literal("Status updated")
        graphics.drawn.clear()
        details.renderWidget(graphics, -1, -1, 0f)
        check(graphics.drawn.map { it.first.toChar() }.joinToString("") == "Status updated")
        details.message = Component.literal((1..30).joinToString("\n") { "status $it" })
        check(details.maxScrollAmount() > 0)
        graphics.drawn.clear()
        details.renderWidget(graphics, -1, -1, 0f)
        check(graphics.drawn.isNotEmpty())
        details.setScrollAmount(100.0)
        details.message = Component.literal("Short")
        graphics.drawn.clear()
        details.renderWidget(graphics, -1, -1, 0f)
        check(details.scrollAmount() == 0.0)
        check(graphics.drawn.map { it.first.toChar() }.joinToString("") == "Short")

        val client = unsafe.allocateInstance(Minecraft::class.java) as Minecraft
        Minecraft::class.java.getDeclaredField("instance").also { it.isAccessible = true }.set(null, client)
        val screen = TextEditorScreen(null)
        for ((name, value) in listOf("font" to font, "minecraft" to client)) {
            Screen::class.java.getDeclaredField(name).also { it.isAccessible = true }.set(screen, value)
        }
        screen.width = 640
        screen.height = 480
        TextEditorScreen::class.java.getDeclaredMethod("init").also { it.isAccessible = true }.invoke(screen)
        fun field(name: String): Any = TextEditorScreen::class.java.getDeclaredField(name).let { it.isAccessible = true; it.get(screen) }
        val editor = field("editor") as SpotlightMultiLineEditBox
        val live = field("preview") as StyledTextPreview
        val form = field("form") as AbstractContainerWidget
        check(live.y > editor.bottom && live.bottom <= form.bottom)
        editor.value = "Visible now"
        graphics.drawn.clear()
        live.renderWidget(graphics, -1, -1, 0f)
        check(graphics.drawn.map { it.first.toChar() }.joinToString("") == "Visible now")
        check((form.children()[2] as Button).message.string == "Mode: GRADIENT")
        editor.value = (1..30).joinToString("\n") { "line $it" }
        check(form.mouseScrolled(live.x + 2.0, live.y + 2.0, 0.0, -1.0))
        check(live.scrollAmount() > 0 && form.scrollAmount() == 0.0)
        live.setScrollAmount(live.maxScrollAmount().toDouble())
        check(form.mouseScrolled(live.x + 2.0, live.y + 2.0, 0.0, -1.0))
        check(form.scrollAmount() > 0)
        val before = form.scrollAmount()
        form.setFocused(editor)
        check(form.scrollAmount() == before) { "Same focus reset manual form scroll" }
        val status = field("status") as AbstractScrollArea
        status.message = Component.literal((1..30).joinToString("\n") { "status $it" })
        form.setFocused(status)
        val outerBefore = form.scrollAmount()
        check(form.mouseScrolled(status.x + 2.0, status.y + 2.0, 0.0, -1.0))
        check(status.scrollAmount() > 0 && form.scrollAmount() == outerBefore)
        status.setScrollAmount(0.0)
        check(form.mouseScrolled(status.x + 2.0, status.y + 2.0, 0.0, 1.0))
        check(status.scrollAmount() == 0.0 && form.scrollAmount() < outerBefore)
        form.setFocused(live)
        form.setFocused(editor)
        form.setScrollAmount(0.0)
        editor.setScrollAmount(0.0)
        val inputBefore = editor.scrollAmount()
        check(form.mouseScrolled(editor.x + 2.0, editor.y + 2.0, 0.0, -1.0))
        check(editor.scrollAmount() > inputBefore && form.scrollAmount() == 0.0)
        editor.setScrollAmount(editor.maxScrollAmount().toDouble())
        check(form.mouseScrolled(editor.x + 2.0, editor.y + 2.0, 0.0, -1.0))
        check(form.scrollAmount() > 0)
        editor.value = ""
        graphics.drawn.clear()
        live.renderWidget(graphics, -1, -1, 0f)
        check(graphics.drawn.isEmpty())
        (field("colorField") as SpotlightEditBox).value = "#XYZ"
        check(live.message.string.isEmpty())
        check(status.message.string.contains("Invalid input:"))
        println("1.21.4 live preview draw, styling, status cache, resize, nested wheel and focus checks passed")
    }
}
