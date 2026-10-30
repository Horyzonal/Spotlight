package dev.spotlight.ui

import dev.spotlight.core.TextFormatter
import dev.spotlight.core.TextMode
import dev.spotlight.core.TextStyle

internal object TextEditorScreenCheck {
    @JvmStatic fun main(args: Array<String>) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        previewChecks()
        check(textEditorColumns(620, 5) == 3)
        check(textEditorColumns(240, 5) == 1)
        check(textEditorColumns(1, 1) == 1)
        check(textEditorScroll(400, 20, 0, 200, 600) == 220)
        check(textEditorScroll(0, 80, 220, 200, 600) == 0)
        check(textEditorScroll(0, 80, 0, 800, 600) == 0)
        GradientTimelineCheck.main(emptyArray())
        val styled = TextFormatter.format("A\nB", TextMode.GRADIENT,
            TextFormatter.parseColors("#FF0000, #0000FF"), TextStyle(bold = true, underlined = true))
        check(styled.toCustom("<#{color}>{text}").startsWith("<#FF0000>A"))
        check(styled.toJson().contains("\"bold\":true"))
        check(styled.toJson().contains("\\u000a"))
        check(styled.toLegacy().endsWith("§r"))
        check(runCatching { TextFormatter.parseColors("#XYZ") }.isFailure)
        println("Text editor live preview rendering, nested wheel, layout and export checks passed")
    }

    private fun previewChecks() {
        val glyph = object : com.mojang.blaze3d.font.GlyphInfo {
            override fun getAdvance() = 6f
            override fun bake(factory: java.util.function.Function<com.mojang.blaze3d.font.SheetGlyphInfo,
                net.minecraft.client.gui.font.glyphs.BakedGlyph>): net.minecraft.client.gui.font.glyphs.BakedGlyph = error("No GPU")
        }
        val source = object : net.minecraft.client.gui.font.FontSet(null, net.minecraft.resources.ResourceLocation("test")) {
            override fun getGlyphInfo(codepoint: Int, filterFishyGlyphs: Boolean) = glyph
        }
        val font = net.minecraft.client.gui.Font({ source }, false)
        val draws = mutableListOf<Triple<String, Int, List<net.minecraft.network.chat.Style>>>()
        val scissors = mutableListOf<List<Int>>()
        val graphics = object : net.minecraft.client.gui.GuiGraphics(null, null) {
            override fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {}
            override fun renderOutline(x: Int, y: Int, width: Int, height: Int, color: Int) {}
            override fun enableScissor(x1: Int, y1: Int, x2: Int, y2: Int) { scissors += listOf(x1, y1, x2, y2) }
            override fun disableScissor() { check(scissors.isNotEmpty()); scissors.removeAt(scissors.lastIndex) }
            override fun drawString(font: net.minecraft.client.gui.Font, text: net.minecraft.util.FormattedCharSequence,
                x: Int, y: Int, color: Int, shadow: Boolean): Int {
                val value = StringBuilder()
                val styles = mutableListOf<net.minecraft.network.chat.Style>()
                text.accept { _, style, codepoint -> value.appendCodePoint(codepoint); styles += style; true }
                check(scissors.size == 2) { "Preview must retain outer clip" }
                check(x == 14 && y + font.lineHeight > 24 && y < 52)
                draws += Triple(value.toString(), y, styles)
                return 0
            }
        }
        val preview = StyledTextPreview(10, 20, 80, 36, font)
        fun render() {
            draws.clear()
            graphics.enableScissor(0, 28, 100, 50)
            preview.render(graphics, -1, -1, 0f)
            check(scissors == listOf(listOf(0, 28, 100, 50)))
            graphics.disableScissor()
        }
        render()
        check(draws.joinToString("") { it.first }.contains("Preview"))
        val formatted = TextFormatter.format("A\nB", TextMode.GRADIENT,
            TextFormatter.parseColors("#FF0000, #0000FF"), TextStyle(bold = true, italic = true,
                underlined = true, strikethrough = true, obfuscated = true))
        preview.message = dev.spotlight.platform.CreativeText.component(formatted)
        render()
        check(draws.map { it.first } == listOf("A", "B"))
        check(draws[0].third.single().color?.value == 0xFF0000)
        check(draws[1].third.single().color?.value == 0x0000FF)
        check(draws.flatMap { it.third }.all { it.isBold && it.isItalic && it.isUnderlined && it.isStrikethrough && it.isObfuscated })
        preview.message = net.minecraft.network.chat.Component.literal((1..12).joinToString("\n") { "line$it" })
        render()
        check(preview.maxScrollAmount() > 0)
        val outer = object : UiScrollContainer(0, 0, 100, 100, net.minecraft.network.chat.Component.empty()) {
            override fun children() = listOf(preview)
            override fun contentHeight() = 400
            override fun scrollRate() = 24.0
            override fun renderWidget(graphics: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {}
            override fun updateWidgetNarration(output: net.minecraft.client.gui.narration.NarrationElementOutput) {}
            override fun mouseScrolled(mouseX: Double, mouseY: Double, vertical: Double): Boolean =
                textEditorScrollChild(children().firstOrNull { it.isMouseOver(mouseX, mouseY) }, mouseX, mouseY, vertical) ||
                    super.mouseScrolled(mouseX, mouseY, vertical)
        }
        check(outer.mouseScrolled(20.0, 30.0, -1.0))
        check(preview.scrollAmount() == 9.0 && outer.scrollAmount() == 0.0)
        render()
        check(draws.first().first == "line2")
        preview.setScrollAmount(10000.0)
        check(outer.mouseScrolled(20.0, 30.0, -1.0) && outer.scrollAmount() == 24.0)
        check(outer.mouseScrolled(20.0, 30.0, 1.0) && outer.scrollAmount() == 24.0)
        check(!outer.mouseScrolled(101.0, 30.0, -1.0))
        preview.message = net.minecraft.network.chat.Component.literal("new")
        render()
        check(preview.scrollAmount() == 0.0 && draws.single().first == "new")
        preview.message = net.minecraft.network.chat.Component.literal("abcdefghij")
        preview.width = 38
        render()
        check(draws.map { it.first } == listOf("abcd", "efgh", "ij"))
        preview.width = 80
        render()
        check(draws.single().first == "abcdefghij")
        val screen = TextEditorScreen(null)
        val formClass = TextEditorScreen::class.java.declaredClasses.single { it.simpleName == "Form" }
        val form = formClass.getDeclaredConstructor(TextEditorScreen::class.java).apply { isAccessible = true }
            .newInstance(screen) as UiScrollContainer
        form.setRectangle(100, 100, 0, 0)
        formClass.getDeclaredField("totalHeight").apply { isAccessible = true }.setInt(form, 400)
        @Suppress("UNCHECKED_CAST")
        val offsets = formClass.getDeclaredField("offsets").apply { isAccessible = true }.get(form) as
            MutableMap<net.minecraft.client.gui.components.AbstractWidget, Int>
        offsets[preview] = 200
        form.setFocused(preview)
        check(form.scrollAmount() == 136.0)
        form.setScrollAmount(0.0)
        form.setFocused(preview)
        check(form.scrollAmount() == 0.0) { "Repeated focus must not undo manual form scrolling" }
        val details = catalogDetails(10, 20, 80, 36, net.minecraft.network.chat.Component.literal("short"), font)
        check(!textEditorScrollChild(details, 20.0, 30.0, -1.0))
        details.message = net.minecraft.network.chat.Component.literal("line\n".repeat(12))
        check(textEditorScrollChild(details, 20.0, 30.0, -1000.0))
        check(!textEditorScrollChild(details, 20.0, 30.0, -1.0))
        check(textEditorScrollChild(details, 20.0, 30.0, 1.0))
        val editor = SpotlightMultiLineEditBox(font, 10, 20, 80, 36, net.minecraft.network.chat.Component.empty())
        check(!textEditorScrollChild(editor, 20.0, 30.0, -1.0))
        editor.value = "line\n".repeat(12)
        check(textEditorScrollChild(editor, 20.0, 30.0, 1000.0))
        check(!textEditorScrollChild(editor, 20.0, 30.0, 1.0))
        check(textEditorScrollChild(editor, 20.0, 30.0, -1.0))
    }
}
