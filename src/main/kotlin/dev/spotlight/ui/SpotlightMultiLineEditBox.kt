package dev.spotlight.ui

import net.minecraft.Util
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractTextAreaWidget
import net.minecraft.client.gui.components.MultilineTextField
import net.minecraft.client.gui.components.Whence
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.util.StringUtil
import java.util.function.Consumer

// ponytail: 1.21.4 native multiline editor hides styling; use its text model until styling setters return.
internal class SpotlightMultiLineEditBox(
    private val textFont: Font, x: Int, y: Int, width: Int, height: Int, label: Component,
) : AbstractTextAreaWidget(x, y, width, height, label) {
    private class TextState(font: Font, width: Int) : MultilineTextField(font, width) {
        fun lines(): List<IntRange> = iterateLines().map { it.beginIndex() until it.endIndex() }
        fun selection(): IntRange = selected.let { it.beginIndex() until it.endIndex() }
        fun selectWord() {
            val start = previousWord.beginIndex()
            val end = nextWord.endIndex()
            seekCursor(Whence.ABSOLUTE, start)
            setSelecting(true)
            seekCursor(Whence.ABSOLUTE, end)
            setSelecting(false)
        }
    }
    private var state = TextState(textFont, (width - 8).coerceAtLeast(1))
    private var modelWidth = width
    private var limit = Int.MAX_VALUE
    private var listener = Consumer<String> {}
    private var focusedAt = 0L
    private var lastClickAt = 0L
    private var lastClickX = 0.0
    private var lastClickY = 0.0

    init { listen() }

    private fun listen() {
        state.setCharacterLimit(limit)
        state.setValueListener { listener.accept(it) }
        state.setCursorListener { scrollToCursor() }
    }

    private fun updateWidth() {
        if (modelWidth == width) return
        val value = state.value()
        val cursor = state.cursor()
        val selection = state.selection()
        state = TextState(textFont, (width - 8).coerceAtLeast(1))
        state.setValue(value)
        state.seekCursor(Whence.ABSOLUTE, if (cursor == selection.first) selection.last + 1 else selection.first)
        state.setSelecting(true)
        state.seekCursor(Whence.ABSOLUTE, cursor)
        state.setSelecting(false)
        modelWidth = width
        listen()
    }

    var value: String
        get() = state.value()
        set(value) { state.setValue(value) }

    fun setValueListener(listener: Consumer<String>) { this.listener = listener }
    fun setCharacterLimit(limit: Int) { this.limit = limit; state.setCharacterLimit(limit) }
    fun seekCursor(position: Int) = state.seekCursor(Whence.ABSOLUTE, position)
    fun cursor() = state.cursor()
    fun hasSelection() = state.hasSelection()
    fun lineAtCursor() = state.lineAtCursor
    fun lineStart() = state.lines().getOrNull(state.lineAtCursor)?.first ?: 0
    fun atLineEnd() = state.lines().getOrNull(state.lineAtCursor)?.let { state.cursor() == it.last + 1 } == true

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean =
        active && state.keyPressed(keyCode)

    override fun charTyped(character: Char, modifiers: Int): Boolean {
        if (!active || !visible || !isFocused || !StringUtil.isAllowedChatCharacter(character)) return false
        state.insertText(character.toString())
        return true
    }

    override fun onClick(mouseX: Double, mouseY: Double) {
        updateWidth()
        state.setSelecting(Screen.hasShiftDown())
        state.seekCursorToPoint(mouseX - x - 4, mouseY - y - 4 + scrollAmount())
        val now = Util.getMillis()
        if (now - lastClickAt < 250 && kotlin.math.abs(mouseX - lastClickX) < 4 && kotlin.math.abs(mouseY - lastClickY) < 4) state.selectWord()
        lastClickAt = now
        lastClickX = mouseX
        lastClickY = mouseY
    }

    override fun onDrag(mouseX: Double, mouseY: Double, dx: Double, dy: Double) {
        state.setSelecting(true)
        state.seekCursorToPoint(mouseX - x - 4, mouseY - y - 4 + scrollAmount())
        state.setSelecting(Screen.hasShiftDown())
    }

    override fun setFocused(focused: Boolean) {
        super.setFocused(focused)
        if (focused) focusedAt = Util.getMillis()
    }

    override fun getInnerHeight(): Int { updateWidth(); return state.lineCount * textFont.lineHeight }
    override fun scrollRate() = textFont.lineHeight / 2.0

    private fun scrollToCursor() {
        val top = state.lineAtCursor * textFont.lineHeight
        val viewport = (height - 8).coerceAtLeast(1)
        if (top < scrollAmount()) setScrollAmount(top.toDouble())
        else if (top + textFont.lineHeight > scrollAmount() + viewport)
            setScrollAmount((top + textFont.lineHeight - viewport).toDouble())
    }

    override fun renderBackground(graphics: GuiGraphics) {
        if (catalogCustomFrame()) catalogFrame(graphics, this) else super.renderBackground(graphics)
    }

    override fun renderContents(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        updateWidth()
        val value = state.value()
        val cursor = state.cursor()
        val selected = state.selection()
        val cursorVisible = isFocused && (Util.getMillis() - focusedAt) / 300L % 2L == 0L
        state.lines().forEachIndexed { index, line ->
            val top = y + 4 + index * textFont.lineHeight
            if (!withinContentAreaTopBottom(top, top + textFont.lineHeight)) return@forEachIndexed
            val start = line.first
            val end = line.last + 1
            graphics.themeText(textFont, catalogText(value.substring(start, end)), x + 4, top,
                catalogNativeColor(-2039584, 0xFFF2F5FC))
            if (cursorVisible && cursor in start..end && index == state.lineAtCursor) {
                val caretX = x + 4 + textFont.width(value.substring(start, cursor))
                val color = catalogNativeColor(-3092272, 0xFFF2F5FC)
                if (cursor < value.length) graphics.fill(caretX - 1, top - 1, caretX, top + textFont.lineHeight + 1, color)
                else graphics.themeText(textFont, "_", caretX, top, color)
            }
            if (state.hasSelection() && selected.first < end && selected.last + 1 > start) {
                val left = x + 4 + textFont.width(value.substring(start, selected.first.coerceIn(start, end)))
                val right = x + 4 + textFont.width(value.substring(start, (selected.last + 1).coerceIn(start, end)))
                graphics.fill(RenderType.guiTextHighlight(), left, top, right, top + textFont.lineHeight, 0xFF0000FF.toInt())
            }
        }
    }

    override fun renderDecorations(graphics: GuiGraphics) {
        super.renderDecorations(graphics)
        if (state.hasCharacterLimit()) graphics.themeText(textFont, "${value.length}/$limit", right - textFont.width("${value.length}/$limit"), bottom + 4,
            SpotlightTheme.color(0xFF9DADC3))
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox", message, value))
    }
}
