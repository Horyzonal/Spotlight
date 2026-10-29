package dev.spotlight.ui

import dev.spotlight.core.Theme
import dev.spotlight.core.ThemeColor
import dev.spotlight.core.ThemeConfig
import dev.spotlight.core.ThemeEntry
import dev.spotlight.core.ThemeMetric
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

internal fun validateThemeDraft(name: String, values: Map<String, String>): ThemeConfig {
    val trimmed = name.trim()
    require(trimmed.isNotEmpty() && trimmed.length <= 64 && trimmed.none { it.isISOControl() || it == '\u2028' || it == '\u2029' }) {
        "Name: expected 1..64 characters without line breaks or control characters"
    }
    val expected = ThemeColor.entries.map { "colors.${it.key}" } + ThemeMetric.entries.map { "layout.${it.key}" } + "font"
    require(values.keys == expected.toSet()) { "Expected all 21 colors, 11 layout fields and optional font" }
    val colors = ThemeColor.entries.associate { token ->
        val value = values.getValue("colors.${token.key}").trim()
        require(value.matches(Regex("#[0-9a-fA-F]{8}"))) { "${token.key}: expected #AARRGGBB" }
        token.defaultArgb to value.drop(1).toLong(16).toInt()
    }
    val metrics = ThemeMetric.entries.associate { token ->
        val value = values.getValue("layout.${token.key}").trim().toIntOrNull()
        require(value != null && value in token.min..token.max) { "${token.key}: expected ${token.min}..${token.max}" }
        token.key to value
    }
    val font = values.getValue("font").trim().ifEmpty { null }
    require(font == null || font.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+"))) { "Font: expected namespace:path or blank" }
    return ThemeConfig(colors, metrics, font)
}

internal fun themePickerArgb(original: Int, rgb: Int): Int = (original and 0xFF000000.toInt()) or (rgb and 0xFFFFFF)

class ThemeEditorScreen(private val parent: Screen, entry: ThemeEntry? = null) : Screen(catalogText("Theme editor")) {
    private var entry = entry
    private var name = entry?.name ?: "New theme"
    private val initial = entry?.config ?: Theme.defaults
    private val values = linkedMapOf<String, String>().apply {
        ThemeColor.entries.forEach { put("colors.${it.key}", argb(initial.color(it.defaultArgb))) }
        ThemeMetric.entries.forEach { put("layout.${it.key}", (initial.metrics[it.key] ?: it.default).toString()) }
        put("font", initial.font.orEmpty())
    }
    private val fields = linkedMapOf<String, SpotlightEditBox>()
    private val pickers = linkedMapOf<ThemeColor, Button>()
    private lateinit var nameField: SpotlightEditBox
    private lateinit var form: Form
    private lateinit var save: Button
    private lateinit var saveApply: Button
    private var layoutTab = false
    private var left = 0
    private var panelWidth = 1
    private var validation: String? = null
    private var notice = "RGB picker preserves alpha; edit #AARRGGBB to change alpha."
    private var failed = false
    private var parsed: ThemeConfig? = null
    private var recreateFields = true

    override fun isPauseScreen() = false
    override fun onClose() { minecraft.gui.setScreen(parent) }
    override fun init() {
        panelWidth = minOf(680, (width - 24).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        
        if (recreateFields) {
            recreateFields = false
            fields.clear()
            pickers.clear()
            nameField = SpotlightEditBox(font, 0, 0, 100, 20, catalogText("Theme name"))
            nameField.setMaxLength(128)
            nameField.value = name
            nameField.setResponder { name = it; changed() }
            values.forEach { (key, value) ->
                val field = SpotlightEditBox(font, 0, 0, 100, 20, catalogText(key))
                field.setMaxLength(256)
                field.value = value
                field.setResponder { values[key] = it; changed() }
                fields[key] = field
            }
            ThemeColor.entries.forEach { token ->
                pickers[token] = macroButton("Pick RGB", 0, 0, 70) { pick(token) }
            }
        }
        nameField.setRectangle(panelWidth, 20, left, 32)
        addRenderableWidget(nameField)
        addRenderableWidget(macroButton("Colors · 21 ARGB tokens", left, 58, panelWidth / 2 - 2) { switchTab(false) })
        addRenderableWidget(macroButton("Layout · 11 fields + font", left + panelWidth / 2 + 2, 58, panelWidth / 2 - 2) { switchTab(true) })
        val scroll = if (::form.isInitialized) form.scrollAmount() else 0.0
        form = addRenderableWidget(Form())
        form.setRectangle(panelWidth, (height - 154).coerceAtLeast(1), left, 86)
        form.layout()
        form.setScrollAmount(scroll)
        val cell = ((panelWidth - 8) / 3).coerceAtLeast(1)
        save = addRenderableWidget(macroButton("Save · $primaryModifierLabel+Enter", left, height - 30, cell) { commit(false) })
        saveApply = addRenderableWidget(macroButton("Save & Apply", left + cell + 4, height - 30, cell) { commit(true) })
        saveApply.setTooltip(Tooltip.create(catalogText("Save and apply immediately · $primaryModifierLabel+Shift+Enter")))
        addRenderableWidget(macroButton("Cancel · Esc", left + (cell + 4) * 2, height - 30, cell) { onClose() })
        validate()
        setInitialFocus(nameField)
    }
    private fun switchTab(layout: Boolean) {
        layoutTab = layout
        form.setFocused(null)
        form.layout()
        form.setScrollAmount(0.0)
    }
    private fun changed() {
        notice = "Unsaved changes"
        failed = false
        validate()
    }
    private fun validate() {
        parsed = try { validateThemeDraft(name, values).also { validation = null } }
        catch (failure: IllegalArgumentException) { validation = failure.message; null }
        if (::save.isInitialized) {
            save.active = parsed != null
            saveApply.active = parsed != null
            save.message = catalogText("${if (entry == null || entry!!.builtin || entry!!.id == "current") "Save copy" else "Save"} · $primaryModifierLabel+Enter")
        }
        pickers.forEach { (token, button) ->
            button.active = values.getValue("colors.${token.key}").trim().matches(Regex("#[0-9a-fA-F]{8}"))
            button.message = catalogText("Pick ${token.key} RGB")
        }
    }
    private fun pick(token: ThemeColor) {
        val key = "colors.${token.key}"
        val original = values.getValue(key).trim().drop(1).toLongOrNull(16)?.toInt() ?: return
        minecraft.gui.setScreen(ColorPickerScreen(this, original and 0xFFFFFF) { rgb ->
            fields.getValue(key).value = argb(themePickerArgb(original, rgb))
        })
    }
    private fun commit(apply: Boolean) {
        validate()
        val config = parsed ?: return
        try {
            val saved = SpotlightTheme.library().save(entry?.id, name, config)
            entry = saved
            name = saved.name
            notice = "Saved ${saved.name}"
            failed = false
            if (apply) {
                val report = SpotlightTheme.apply(saved.id)
                check(report.warnings.isEmpty()) { "Saved, but apply failed: ${report.warnings.joinToString("; ")}" }
                notice = "Saved and applied ${saved.name} · no restart needed"
                
                recreateFields = true
                rebuildWidgets()
            }
            validate()
        } catch (failure: Exception) { failed = true; notice = failure.message ?: failure.javaClass.simpleName }
    }
    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (event.isConfirmation && primaryModifier(event) && !event.hasAltDown()) {
            commit(event.hasShiftDown()); return true
        }
        return super.keyPressed(event)
    }
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.themeText(font, catalogText("Theme editor · Name"), left, 14, SpotlightTheme.color(0xFFF2F5FC))
        graphics.themeText(font, catalogClip(font, validation ?: notice, panelWidth), left, height - 50, SpotlightTheme.color(if (validation != null || failed) 0xFFFFB4A9 else 0xFF9DADC3))
    }

    private inner class Form : AbstractContainerWidget(0, 0, 100, 100, catalogText("Theme fields"), defaultSettings(6)) {
        private val offsets = linkedMapOf<AbstractWidget, Int>()
        private var keys = emptyList<String>()
        override fun children(): List<AbstractWidget> = offsets.keys.toList()
        override fun contentHeight() = keys.size * 46
        override fun scrollRate() = 46.0
        fun layout() {
            offsets.clear()
            keys = if (layoutTab) ThemeMetric.entries.map { "layout.${it.key}" } + "font" else ThemeColor.entries.map { "colors.${it.key}" }
            keys.forEachIndexed { index, key ->
                val field = fields.getValue(key)
                field.x = x
                field.width = if (layoutTab) (width - 12).coerceAtLeast(1) else ((width - 20) / 2).coerceAtLeast(1)
                offsets[field] = index * 46 + 16
                if (!layoutTab) {
                    val token = ThemeColor.entries[index]
                    pickers.getValue(token).also {
                        it.x = x + field.width + 22
                        it.width = (width - field.width - 34).coerceAtLeast(1)
                        offsets[it] = index * 46 + 16
                    }
                }
            }
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
            if (top < scrollAmount()) setScrollAmount(top.toDouble())
            else if (top + widget.height > scrollAmount() + height) setScrollAmount((top + widget.height - height).toDouble())
        }
        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean =
            isMouseOver(event.x(), event.y()) && super.mouseClicked(event, doubleClick)
        override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean =
            isMouseOver(mouseX, mouseY) && super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            graphics.enableScissor(x, y, right, bottom)
            keys.forEachIndexed { index, key ->
                val top = y + index * 46 - scrollAmount().toInt()
                val metric = ThemeMetric.entries.find { "layout.${it.key}" == key }
                val label = if (metric != null) "${metric.key} · ${metric.min}..${metric.max}" else if (key == "font") "Font · namespace:path; blank = Minecraft default" else key.removePrefix("colors.") + " · #AARRGGBB"
                graphics.themeText(font, catalogClip(font, label, width - 12), x, top, SpotlightTheme.color(0xFF9DADC3))
                if (!layoutTab) {
                    val field = fields.getValue(key)
                    val color = values.getValue(key).trim().takeIf { it.matches(Regex("#[0-9a-fA-F]{8}")) }?.drop(1)?.toLong(16)?.toInt()
                    color?.let { graphics.fill(x + field.width + 4, top + 20, x + field.width + 18, top + 32, it) }
                }
            }
            children().forEach { it.extractRenderState(graphics, mouseX, mouseY, delta) }
            graphics.disableScissor()
            extractScrollbar(graphics, mouseX, mouseY)
        }
        override fun updateWidgetNarration(output: NarrationElementOutput) { children().firstOrNull { it.isFocused }?.updateNarration(output) }
    }
    companion object {
        private fun argb(color: Int) = "#" + color.toUInt().toString(16).uppercase().padStart(8, '0')
    }
}
