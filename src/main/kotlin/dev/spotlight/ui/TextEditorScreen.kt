package dev.spotlight.ui

import dev.spotlight.core.ColorPicker
import dev.spotlight.core.GradientStop
import dev.spotlight.core.StyledText
import dev.spotlight.core.TextFormatter
import dev.spotlight.core.TextMode
import dev.spotlight.core.TextStyle
import dev.spotlight.platform.CreativeText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.FittingMultiLineTextWidget
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType

class TextEditorScreen(private val parent: Screen?) : Screen(catalogText("Text editor")) {
    private var text = ""
    private var colors = "#FF6699, #6699FF"
    private var selectedStop = 0
    private lateinit var colorField: SpotlightEditBox
    private var stops = ColorPicker.positionedStops(TextFormatter.parseColors(colors))
    private var syncingColors = false
    private lateinit var timeline: GradientTimeline
    private lateinit var styleRow: Row
    private var custom = "<#{color}>{text}"
    private var mode = TextMode.GRADIENT
    private var style = TextStyle()
    private var formatted: StyledText? = null
    private var validation: String? = null
    private var notice = "Tab / Shift+Tab: navigate · Enter: activate · Esc: close"
    private lateinit var editor: MultiLineEditBox
    private lateinit var preview: FittingMultiLineTextWidget
    private lateinit var status: FittingMultiLineTextWidget
    private lateinit var form: Form
    private lateinit var close: Button
    private val copies = mutableListOf<Button>()
    private val applies = mutableListOf<Button>()
    private val clears = mutableListOf<Button>()
    private data class Row(val label: String, val widgets: List<AbstractWidget>, val height: Int)
    private val rows = mutableListOf<Row>()

    override fun isPauseScreen() = false
    override fun onClose() { minecraft.gui.setScreen(parent) }

    override fun init() {
        stops = timelineNormalize(stops)
        rows.clear()
        copies.clear()
        applies.clear()
        clears.clear()

        editor = MultiLineEditBox.builder().setX(0).setY(0)
            .setTextShadow(SpotlightTheme.textShadow())
            .setTextColor(catalogNativeColor(-2039584, 0xFFF2F5FC))
            .setCursorColor(catalogNativeColor(-3092272, 0xFFF2F5FC))
            .setShowBackground(!catalogCustomFrame())
            .build(font, 100, 80, catalogText("Text input"))
        editor.setCharacterLimit(32768)
        editor.value = text
        editor.setValueListener { text = it; changed() }
        rows += Row("Text · multiline · up to 4096 code points / 8192 UTF-16 units", listOf(editor), 80)

        val cycle = macroButton("Mode: ${mode.name}", 0, 0, 100) {
            mode = TextMode.entries[(mode.ordinal + 1) % TextMode.entries.size]
            changed()
        }
        cycle.setTooltip(Tooltip.create(catalogText("Cycle SOLID, GRADIENT, RAINBOW, INTERLEAVED · Enter / Space")))
        val paste = macroButton("Paste input", 0, 0, 100) {
            perform("Pasted clipboard into input") {
                val value = minecraft.keyboardHandler.clipboard
                require(value.length <= 32768) { "Clipboard exceeds input draft limit (32768 UTF-16 units)" }
                editor.value = value
            }
        }
        paste.setTooltip(Tooltip.create(catalogText("Replace input with clipboard text; clipboard read only on explicit Paste or native paste shortcut")))
        rows += Row("", listOf(cycle, paste), 20)
        timeline = GradientTimeline(font, stops, selectedStop, ::updateStops) { index ->
            minecraft.gui.setScreen(ColorPickerScreen(this, stops[index].color) { picked ->
                updateStops(stops.mapIndexed { i, stop -> if (i == index) stop.copy(color = picked) else stop }, index)
            })
        }
        rows += Row("Color list · drag to reorder · click to edit · × remove · + add", listOf(timeline), 48)
        colorField = SpotlightEditBox(font, 0, 0, 100, 20, catalogText("RGB color stops"))
        colorField.setMaxLength(2048)
        colorField.value = colors
        colorField.setResponder {
            colors = it
            if (!syncingColors) {
                runCatching { ColorPicker.positionedStops(TextFormatter.parseColors(it)) }.getOrNull()?.let { parsed ->
                    stops = parsed
                    selectedStop = selectedStop.coerceAtMost(stops.lastIndex)
                }
                changed()
            }
        }
        colorField.setTooltip(Tooltip.create(catalogText("1–16 #RRGGBB colors, separated by commas or spaces; always evenly spaced. Gradient needs at least 2. Solid uses first; rainbow ignores values.")))
        rows += Row("Advanced colors · comma / space separated #RRGGBB", listOf(colorField), 20)
        val labels = listOf("Bold", "Italic", "Underline", "Strike", "Obfuscated")
        val toggles = labels.mapIndexed { index, label ->
            macroButton(label, 0, 0, 100) {
                style = when (index) {
                    0 -> style.copy(bold = !style.bold)
                    1 -> style.copy(italic = !style.italic)
                    2 -> style.copy(underlined = !style.underlined)
                    3 -> style.copy(strikethrough = !style.strikethrough)
                    else -> style.copy(obfuscated = !style.obfuscated)
                }
                changed()
            }.also { it.setTooltip(Tooltip.create(catalogText("Toggle $label · Enter / Space; ON means enabled"))) }
        }
        styleRow = Row("Style", toggles, 20)
        rows += styleRow
        val formatField = SpotlightEditBox(font, 0, 0, 100, 20, catalogText("Custom export format"))
        formatField.setMaxLength(2048)
        formatField.value = custom
        formatField.setResponder { custom = it; changed() }
        formatField.setTooltip(Tooltip.create(catalogText("Per colored run: {text}, {color} = RRGGBB, {hex} = #RRGGBB. Custom output does not include style toggles.")))
        rows += Row("Custom format · {color} has no # · styles omitted", listOf(formatField), 20)
        
        preview = FittingMultiLineTextWidget(0, 0, 100, 72, Component.empty(), font)
        rows += Row("Live preview · actual Minecraft colors and styles", listOf(preview), 72)
        status = catalogDetails(0, 0, 100, 60, Component.empty(), font)
        rows += Row("Status / item availability", listOf(status), 60)
        copies += macroButton("Copy JSON", 0, 0, 100) { copy("JSON") }
        copies += macroButton("Copy custom", 0, 0, 100) { copy("custom") }
        copies += macroButton("Copy legacy", 0, 0, 100) { copy("legacy") }
        copies[0].setTooltip(Tooltip.create(catalogText("Copy literal component JSON · ${primaryModifierLabel}+Enter")))
        copies[1].setTooltip(Tooltip.create(catalogText("Copy custom format; colors and text only, no styles")))
        copies[2].setTooltip(Tooltip.create(catalogText("Copy legacy §x RGB text, style codes and final reset")))
        rows += Row("Export to clipboard", copies.toList(), 20)
        applies += macroButton("Apply name", 0, 0, 100) { itemAction("Applied item name") { CreativeText.applyName(requireNotNull(formatted)) } }
        applies += macroButton("Apply lore", 0, 0, 100) { itemAction("Applied item lore") { CreativeText.applyLore(requireNotNull(formatted)) } }
        clears += macroButton("Clear name", 0, 0, 100) { itemAction("Cleared item name", CreativeText::clearName) }
        clears += macroButton("Clear lore", 0, 0, 100) { itemAction("Cleared item lore", CreativeText::clearLore) }
        rows += Row("Held main-hand item · explicit clicks only", applies + clears, 20)
        form = addRenderableWidget(Form())
        close = addRenderableWidget(macroButton("Close · Esc", 0, 0, 100) { onClose() })
        repositionElements()
        refresh()
        setInitialFocus(form)
        form.setFocused(editor)
    }

    override fun repositionElements() {
        if (!::form.isInitialized) return
        val panelWidth = minOf(SpotlightTheme.metric("editorWidth", 620, 160, 4096), (width - 24).coerceAtLeast(1))
        val left = (width - panelWidth) / 2
        form.setRectangle(panelWidth, (height - 62).coerceAtLeast(1), left, 28)
        close.setRectangle(panelWidth, 20, left, (height - 26).coerceAtLeast(0))
        form.layout()
        form.setFocused(form.focused)
    }

    private fun updateStops(updated: List<GradientStop>, selected: Int) {
        stops = timelineNormalize(updated)
        selectedStop = selected.coerceIn(0, stops.lastIndex)
        colors = stops.joinToString(", ") { ColorPicker.hex(it.color) }
        syncingColors = true
        try { colorField.value = colors } finally { syncingColors = false }
        changed()
    }


    private fun changed() {
        notice = "Tab / Shift+Tab: navigate · Enter: activate · Esc: close"
        refresh()
    }

    private fun refresh() {
        formatted = try {
            TextFormatter.parseColors(colors)
            require(mode != TextMode.GRADIENT || stops.size >= 2) { "Gradient requires at least two color stops" }
            TextFormatter.formatWithStops(text, mode, stops, style).also { validation = null }
        } catch (failure: Exception) {
            validation = failure.message ?: failure.javaClass.simpleName
            null
        }
        preview.message = formatted?.let(CreativeText::component) ?: Component.empty()
        rows[1].widgets[0].message = catalogText("Mode: ${mode.name}")
        val flags = listOf(style.bold, style.italic, style.underlined, style.strikethrough, style.obfuscated)
        styleRow.widgets.forEachIndexed { index, widget ->
            val label = listOf("Bold", "Italic", "Underline", "Strike", "Obfuscated")[index]
            widget.message = catalogText("$label: ${if (flags[index]) "ON" else "OFF"}")
        }
        timeline.stops = stops
        timeline.selected = selectedStop
        timeline.minimum = if (mode == TextMode.GRADIENT) 2 else 1
        timeline.active = runCatching { TextFormatter.parseColors(colors) }.isSuccess
        updateAvailability()
    }

    private fun itemReason(): String? {
        val player = minecraft.player ?: return "Join a world first"
        if (minecraft.level == null || minecraft.connection == null) return "World / connection unavailable"
        if (minecraft.gameMode?.playerMode != GameType.CREATIVE || !player.abilities.instabuild) return "Creative mode required"
        if (player.mainHandItem.isEmpty) return "Hold an item in your main hand"
        if (player.containerMenu !== player.inventoryMenu) return "Close the open container first"
        return null
    }

    private fun updateAvailability() {
        val reason = itemReason()
        copies.forEach { it.active = formatted != null }
        applies.forEach {
            it.active = reason == null && formatted != null
            it.setTooltip(Tooltip.create(catalogText(reason ?: validation ?: "${it.message.string} on held main-hand item; replaces existing value")))
        }
        clears.forEach {
            it.active = reason == null
            it.setTooltip(Tooltip.create(catalogText(reason ?: "${it.message.string} on held main-hand item; removes existing value")))
        }
        val message = listOfNotNull(validation?.let { "Invalid input: $it" }, notice,
            reason?.let { "Item actions disabled: $it" } ?: "Item actions ready · Creative main-hand item").joinToString("\n")
        if (status.message.string != message) status.message = catalogText(message).withStyle {
            it.withColor(catalogNativeColor(-1, if (validation != null) 0xFFFFB4A9 else 0xFFF2F5FC))
        }
    }

    private fun perform(success: String, action: () -> Unit) {
        notice = try { action(); success } catch (failure: Exception) {
            "Failed: ${failure.message ?: failure.javaClass.simpleName}"
        }
        updateAvailability()
    }

    private fun copy(kind: String) {
        val value = formatted ?: return
        perform("Copied $kind to clipboard") {
            minecraft.keyboardHandler.clipboard = when (kind) {
                "JSON" -> value.toJson()
                "custom" -> value.toCustom(custom)
                else -> value.toLegacy()
            }
        }
    }

    private fun itemAction(success: String, action: () -> Unit) {
        perform(success) { check(itemReason() == null) { itemReason().orEmpty() }; action() }
    }

    override fun tick() { updateAvailability() }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (event.isConfirmation && primaryModifier(event) && !event.hasAltDown() && !event.hasShiftDown()) {
            copy("JSON")
            return true
        }
        return super.keyPressed(event)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.themeText(font, title, form.x, 10, SpotlightTheme.color(0xFFF2F5FC))
        timeline.extractDragGhost(graphics, mouseX, mouseY)
    }

    private inner class Form : AbstractContainerWidget(0, 28, 100, 100, catalogText("Text editor form"), defaultSettings(6)) {
        private val offsets = mutableMapOf<AbstractWidget, Int>()
        private val labels = mutableListOf<Pair<String, Int>>()
        private var totalHeight = 0
        override fun children(): List<AbstractWidget> = rows.flatMap { it.widgets }
        override fun contentHeight() = totalHeight
        override fun scrollRate() = 24.0

        fun layout() {
            offsets.clear()
            labels.clear()
            val usable = (width - 12).coerceAtLeast(1)
            var top = 0
            rows.forEach { row ->
                if (row.label.isNotEmpty()) { labels += row.label to top; top += 14 }
                if (row.widgets.isEmpty()) return@forEach
                val columns = textEditorColumns(usable, row.widgets.size)
                val cell = ((usable - (columns - 1) * 4) / columns).coerceAtLeast(1)
                row.widgets.forEachIndexed { index, widget ->
                    widget.x = x + (index % columns) * (cell + 4)
                    widget.width = cell
                    widget.height = row.height
                    offsets[widget] = top + (index / columns) * (row.height + 4)
                }
                top += ((row.widgets.size + columns - 1) / columns) * (row.height + 4) + 6
            }
            totalHeight = top
            setScrollAmount(scrollAmount())
        }

        override fun setScrollAmount(amount: Double) {
            super.setScrollAmount(amount)
            offsets.forEach { (widget, offset) -> widget.y = y + offset - scrollAmount().toInt() }
        }

        override fun setFocused(listener: GuiEventListener?) {
            super.setFocused(listener)
            val widget = listener as? AbstractWidget ?: return
            val top = offsets[widget] ?: return
            setScrollAmount(textEditorScroll(top, widget.height, scrollAmount().toInt(), height, totalHeight).toDouble())
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            if (!isMouseOver(event.x(), event.y())) return false
            return super.mouseClicked(event, doubleClick)
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
            if (!isMouseOver(mouseX, mouseY)) return false
            val child = children().firstOrNull { it.isMouseOver(mouseX, mouseY) }
            if (child is net.minecraft.client.gui.components.AbstractScrollArea && child.maxScrollAmount() > 0) {
                val before = child.scrollAmount()
                child.mouseScrolled(mouseX, mouseY, horizontal, vertical)
                if (before != child.scrollAmount()) return true
            }
            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        }

        override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
            
            if (focused === timeline && isDragging) return timeline.mouseDragged(event, dx, dy)
            if (!isMouseOver(event.x(), event.y()) && !isOverScrollbar(event.x(), event.y())) return false
            return super.mouseDragged(event, dx, dy)
        }

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            graphics.enableScissor(x, y, right, bottom)
            labels.forEach { (label, offset) ->
                graphics.themeText(font, catalogClip(font, label, width - 12), x, y + offset - scrollAmount().toInt(), SpotlightTheme.color(0xFF9DADC3))
            }
            val inside = isMouseOver(mouseX.toDouble(), mouseY.toDouble())
            children().filter { it.bottom > y && it.y < bottom }.forEach {
                if (it === editor && catalogCustomFrame()) catalogFrame(graphics, it)
                it.extractRenderState(graphics, if (inside) mouseX else -1, if (inside) mouseY else -1, delta)
            }
            graphics.disableScissor()
            extractScrollbar(graphics, mouseX, mouseY)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            (focused as? AbstractWidget)?.updateNarration(output)
        }
    }
}

internal fun textEditorColumns(width: Int, count: Int) = minOf(count, ((width + 4) / 124).coerceIn(1, 3))

internal fun textEditorScroll(top: Int, widgetHeight: Int, scroll: Int, viewport: Int, content: Int): Int =
    (if (top < scroll) top else if (top + widgetHeight > scroll + viewport) top + widgetHeight - viewport else scroll)
        .coerceIn(0, (content - viewport).coerceAtLeast(0))
