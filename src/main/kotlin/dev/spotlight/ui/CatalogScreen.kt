package dev.spotlight.ui

import dev.spotlight.platform.CommandSuggestion
import dev.spotlight.platform.CommandSuggestions
import dev.spotlight.platform.SelectionKind
import dev.spotlight.platform.commandMatch
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.FittingMultiLineTextWidget



internal fun catalogButton(label: String, x: Int, y: Int, width: Int, height: Int = 20, onPress: () -> Unit): Button =
    spotlightButton(label, x, y, width, height, onPress).also { it.message = catalogText(it.message) }

internal fun catalogHints(graphics: GuiGraphics, font: Font, width: Int, height: Int, text: String) {
    val lines = font.split(catalogText(text), (width - 16).coerceAtLeast(1))
    graphics.enableScissor(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
    lines.forEachIndexed { index, line ->
        graphics.themeText(font, line, 8, height - 4 - (lines.size - index) * font.lineHeight, SpotlightTheme.color(0xFF9DADC3))
    }
    graphics.disableScissor()
}

internal fun catalogClip(font: Font, text: String, width: Int, shadow: Boolean = SpotlightTheme.textShadow()): net.minecraft.util.FormattedCharSequence =
    font.split(font.substrByWidth(catalogText(Component.literal(text), shadow), width.coerceAtLeast(0)), Int.MAX_VALUE).firstOrNull()
        ?: net.minecraft.util.FormattedCharSequence.EMPTY

internal fun catalogText(text: String): net.minecraft.network.chat.MutableComponent = catalogText(Component.literal(text))

internal fun catalogText(text: Component, shadow: Boolean = SpotlightTheme.textShadow()): net.minecraft.network.chat.MutableComponent = text.copy().withStyle { style ->
    val fontStyle = SpotlightTheme.font()?.let { style.withFont(it) } ?: style
    if (shadow) fontStyle else fontStyle.withShadowColor(0)
}


internal fun catalogNativeColor(native: Int, generic: Long): Int =
    SpotlightTheme.color(generic).let { if (it == generic.toInt()) native else it }

internal fun catalogCustomFrame(): Boolean =
    SpotlightTheme.color(0xFF161D29) != 0xFF161D29.toInt() ||
        SpotlightTheme.color(0xFF415877) != 0xFF415877.toInt() ||
        SpotlightTheme.color(0xFF81B8FF) != 0xFF81B8FF.toInt() ||
        SpotlightTheme.metric("borderThickness", 1, 1, 4) != 1

internal fun catalogFrame(graphics: GuiGraphics, widget: AbstractWidget) {
    graphics.fill(widget.x, widget.y, widget.right, widget.bottom, SpotlightTheme.color(0xFF161D29))
    repeat(SpotlightTheme.metric("borderThickness", 1, 1, 4).coerceAtMost(minOf(widget.width, widget.height) / 2)) { inset ->
        graphics.renderOutline(widget.x + inset, widget.y + inset, widget.width - inset * 2, widget.height - inset * 2,
            SpotlightTheme.color(if (widget.isFocused) 0xFF81B8FF else 0xFF415877))
    }
}

internal fun catalogDetails(x: Int, y: Int, width: Int, height: Int, message: Component, font: Font): FittingMultiLineTextWidget =
    object : FittingMultiLineTextWidget(x, y, width, height, message, font) {
        override fun renderBackground(graphics: GuiGraphics) {
            if (catalogCustomFrame()) catalogFrame(graphics, this) else super.renderBackground(graphics)
        }

        override fun getInnerHeight() = font.split(this.message, (this.width - totalInnerPadding()).coerceAtLeast(1)).size * font.lineHeight

        override fun renderContents(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            font.split(this.message, (this.width - totalInnerPadding()).coerceAtLeast(1)).forEachIndexed { index, line ->
                graphics.drawString(font, line, innerLeft, innerTop + index * font.lineHeight, -1)
            }
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            setScrollAmount(scrollAmount())
            if (showingScrollBar()) {
                super.renderWidget(graphics, mouseX, mouseY, delta)
            } else if (visible) {
                renderBackground(graphics)
                renderContents(graphics, mouseX, mouseY, delta)
            }
        }

        override fun setMessage(message: Component) {
            super.setMessage(catalogText(message).withStyle {
                if (it.color == null) it.withColor(catalogNativeColor(-1, 0xFFF2F5FC)) else it
            })
        }
    }.also { it.message = message }


class CatalogScreen(private val parent: Screen) : Screen(catalogText("Selection data lists")) {
    private var kind = SelectionKind.ITEMS
    private var entries = emptyList<CommandSuggestion>()
    private val rows = mutableListOf<Button>()
    private lateinit var search: EditBox
    private var query = ""
    private var page = 0
    private val rowStep get() = SpotlightTheme.metric("compactRowHeight", 24, 22, 80)
    private var count = 1
    private var total = 0
    private var left = 0
    private var panelWidth = 0

    override fun isPauseScreen() = false
    override fun init() {
        rows.clear()
        panelWidth = minOf(SpotlightTheme.metric("selectorWidth", 520, 160, 4096), (width - 16).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        count = ((height - 130) / rowStep).coerceIn(1, 12)
        lateinit var listButton: Button
        listButton = addRenderableWidget(catalogButton("List: ${kind.name.lowercase()}", left, 12, panelWidth) {
            kind = SelectionKind.entries[(kind.ordinal + 1) % SelectionKind.entries.size]
            listButton.message = catalogText("List: ${kind.name.lowercase()}")
            entries = CommandSuggestions.catalog(kind)
            page = 0
            refresh()
        })
        search = addRenderableWidget(SpotlightEditBox(font, left, 38, panelWidth, 20, catalogText("Fuzzy search data list")))
        search.value = query
        search.setResponder { query = it; page = 0; refresh() }
        entries = CommandSuggestions.catalog(kind)
        val controlsY = maxOf(88, height - 64)
        addRenderableWidget(catalogButton("Previous", left, controlsY, panelWidth / 3 - 2) {
            page = (page - 1).coerceAtLeast(0); refresh()
        })
        addRenderableWidget(catalogButton("Next", left + panelWidth / 3, controlsY, panelWidth / 3 - 2) {
            page++; refresh()
        })
        addRenderableWidget(catalogButton("Back", left + panelWidth * 2 / 3, controlsY, panelWidth / 3) { onClose() })
        refresh()
        setInitialFocus(search)
    }

    private fun refresh() {
        val hadRowFocus = rows.any { it.isFocused }
        rows.forEach(::removeWidget)
        rows.clear()
        val filtered = entries.map { minOf(commandMatch(search.value, it.value), commandMatch(search.value, it.title)) to it }
            .filter { it.first != Int.MAX_VALUE }.sortedBy { it.first }.map { it.second }
        total = filtered.size
        page = page.coerceIn(0, ((filtered.size - 1) / count).coerceAtLeast(0))
        filtered.drop(page * count).take(count).forEachIndexed { index, row ->
            rows += addRenderableWidget(catalogButton("${row.title} · ${row.value}", left, 64 + index * rowStep, panelWidth) {
                minecraft!!.keyboardHandler.clipboard = row.value
            })
        }
        if (hadRowFocus) setFocused(search)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (event.isEscape) { onClose(); return true }
        val selected = rows.indexOfFirst { it.isFocused }
        if ((search.isFocused || selected >= 0) && !primaryModifier(event) && !event.hasAltDown()) {
            if (event.isDown || event.isUp) {
                val step = if (event.isDown) 1 else -1
                if (rows.isNotEmpty()) {
                    val target = if (selected < 0) {
                        page * count + if (step > 0) 0 else rows.lastIndex
                    } else (page * count + selected + step).coerceIn(0, total - 1)
                    page = target / count
                    refresh()
                    setFocused(rows[target % count])
                }
                return true
            }
            if (event.isConfirmation && search.isFocused) {
                rows.firstOrNull()?.onPress()
                return true
            }
        }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers())
    }

    override fun onClose() { minecraft!!.setScreen(parent) }
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.render(graphics, mouseX, mouseY, delta)
        catalogHints(graphics, font, width, height, "↑↓ Select · Enter Copy ID · Tab Next · Esc Back")
    }
}
