package dev.spotlight.ui

import dev.spotlight.core.ColorPicker
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.roundToInt

class ColorPickerScreen(private val parent: Screen, initialColor: Int, private val onApply: (Int) -> Unit) :
    Screen(catalogText("Color picker · RGB")) {
    private val original = initialColor.also { require(it in 0..0xFFFFFF) }
    private var color = original
    private var hsv = ColorPicker.hsv(color)
    private var hexDraft = ColorPicker.hex(color)
    private var rgbDraft = listOf(16, 8, 0).map { (color shr it and 255).toString() }
    private lateinit var hex: SpotlightEditBox
    private val channels = mutableListOf<SpotlightEditBox>()
    private lateinit var apply: Button
    private lateinit var sv: Surface
    private lateinit var hue: Surface
    private var syncing = false
    private var dragging: Surface? = null
    private var error: String? = null
    private var left = 0
    private var panelWidth = 0
    private var fieldY = 0

    override fun isPauseScreen() = false
    override fun onClose() { dragging = null; minecraft.setScreen(parent) }

    override fun init() {
        dragging = null
        channels.clear()
        panelWidth = minOf(360, (width - 24).coerceAtLeast(80))
        left = (width - panelWidth) / 2
        val size = minOf(180, panelWidth - 30, (height - 180).coerceAtLeast(20))
        val pickerLeft = left + (panelWidth - size - 30) / 2
        sv = addRenderableWidget(Surface(false, pickerLeft, 36, size, size))
        hue = addRenderableWidget(Surface(true, pickerLeft + size + 12, 36, 18, size))
        fieldY = 36 + size + 16
        hex = addRenderableWidget(SpotlightEditBox(font, left, fieldY, panelWidth / 2 - 4, 20, catalogText("Hex RGB #RRGGBB")))
        hex.setMaxLength(64)
        hex.value = hexDraft
        hex.setResponder {
            if (!syncing) {
                hexDraft = it
                ColorPicker.parseHex(it)?.let { parsed ->
                    color = parsed
                    hsv = ColorPicker.hsv(color)
                    if (ColorPicker.parseRgb(rgbDraft) != null) syncFields(hexToo = false)
                }
                validate()
            }
        }
        listOf("Red", "Green", "Blue").forEachIndexed { index, label ->
            val cell = (panelWidth - 8) / 3
            val field = addRenderableWidget(SpotlightEditBox(font, left + index * (cell + 4), fieldY + 36, cell, 20, catalogText("$label · 0–255")))
            field.setMaxLength(64)
            field.value = rgbDraft[index]
            channels += field
            field.setResponder {
                if (!syncing) {
                    rgbDraft = channels.map { it.value }
                    ColorPicker.parseRgb(rgbDraft)?.let { parsed ->
                        color = parsed
                        hsv = ColorPicker.hsv(color)
                        if (ColorPicker.parseHex(hexDraft) != null) syncFields(rgbToo = false)
                    }
                    validate()
                }
            }
        }
        apply = addRenderableWidget(macroButton("Apply · $primaryModifierLabel+Enter", left, fieldY + 88, panelWidth / 2 - 2) { commit() })
        addRenderableWidget(macroButton("Cancel · Esc", left + panelWidth / 2 + 2, fieldY + 88, panelWidth / 2 - 2) { onClose() })
        validate()
        setInitialFocus(sv)
    }

    private fun syncFields(hexToo: Boolean = true, rgbToo: Boolean = true) {
        syncing = true
        if (hexToo) { hexDraft = ColorPicker.hex(color); hex.value = hexDraft }
        if (rgbToo) {
            rgbDraft = listOf(16, 8, 0).map { (color shr it and 255).toString() }
            channels.forEachIndexed { index, field -> field.value = rgbDraft[index] }
        }
        syncing = false
    }

    private fun validate() {
        error = when {
            ColorPicker.parseHex(hexDraft) == null -> "Hex: expected #RRGGBB"
            ColorPicker.parseRgb(rgbDraft) == null -> "RGB: each value must be 0–255"
            else -> null
        }
        apply.active = error == null
        sv.active = error == null
        hue.active = error == null
        sv.message = catalogText("Saturation ${(hsv.saturation * 100).roundToInt()}%, value ${(hsv.value * 100).roundToInt()}% · arrow keys")
        hue.message = catalogText("Hue ${hsv.hue.roundToInt()} degrees · arrow keys")
    }

    private fun commit() {
        if (!apply.active) return
        onApply(color)
        onClose()
    }

    private fun picked() {
        color = ColorPicker.rgb(hsv.hue, hsv.saturation, hsv.value)
        syncFields()
        validate()
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (event.isConfirmation && primaryModifier(event) && !event.hasAltDown() && !event.hasShiftDown()) {
            commit(); return true
        }
        return super.keyPressed(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val surface = dragging ?: return super.mouseDragged(event, dx, dy)
        surface.pick(event.x(), event.y())
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && dragging != null) { dragging = null; isDragging = false; return true }
        return super.mouseReleased(event)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.render(graphics, mouseX, mouseY, delta)
        val ink = SpotlightTheme.color(0xFFF2F5FC)
        graphics.themeText(font, title, left, 10, ink)
        graphics.themeText(font, catalogText("Hex RGB"), left, fieldY - 11, ink)
        graphics.themeText(font, catalogText("Original / draft"), left + panelWidth / 2 + 4, fieldY - 11, ink)
        graphics.fill(left + panelWidth / 2 + 4, fieldY, left + panelWidth * 3 / 4, fieldY + 20, original or 0xFF000000.toInt())
        graphics.fill(left + panelWidth * 3 / 4, fieldY, left + panelWidth, fieldY + 20, color or 0xFF000000.toInt())
        listOf("Red", "Green", "Blue").forEachIndexed { index, label -> graphics.themeText(font, catalogText(label), channels[index].x, fieldY + 25, ink) }
        graphics.themeText(font, catalogClip(font, error ?: "RGB only · Tab: navigate · Arrows: nudge", panelWidth), left, fieldY + 64,
            SpotlightTheme.color(if (error == null) 0xFF9DADC3 else 0xFFFFB4A9))
    }

    private inner class Surface(private val isHue: Boolean, x: Int, y: Int, width: Int, height: Int) :
        AbstractWidget(x, y, width, height, catalogText(if (isHue) "Hue" else "Saturation / value")) {
        fun pick(mx: Double, my: Double) {
            if (!active) return
            val vertical = ((my - y) / (height - 1).coerceAtLeast(1)).coerceIn(0.0, 1.0)
            hsv = if (isHue) hsv.copy(hue = vertical * 360)
            else hsv.copy(saturation = ((mx - x) / (width - 1).coerceAtLeast(1)).coerceIn(0.0, 1.0), value = 1 - vertical)
            picked()
        }

        override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) { dragging = this; pick(event.x(), event.y()) }

        override fun keyPressed(event: KeyEvent): Boolean {
            if (!active) return false
            val step = if (event.hasShiftDown()) 0.1 else 0.01
            val direction = when (event.key()) {
                InputConstants.KEY_LEFT, InputConstants.KEY_DOWN -> -1
                InputConstants.KEY_RIGHT, InputConstants.KEY_UP -> 1
                else -> return false
            }
            hsv = if (isHue) hsv.copy(hue = (hsv.hue + direction * if (event.hasShiftDown()) 10 else 1).coerceIn(0.0, 360.0))
            else if (event.key() == InputConstants.KEY_LEFT || event.key() == InputConstants.KEY_RIGHT)
                hsv.copy(saturation = (hsv.saturation + direction * step).coerceIn(0.0, 1.0))
            else hsv.copy(value = (hsv.value + direction * step).coerceIn(0.0, 1.0))
            picked()
            return true
        }

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val opaque = 0xFF000000.toInt()
            if (isHue) {
                for (row in 0 until height) graphics.fill(x, y + row, right, y + row + 1,
                    opaque or ColorPicker.rgb(row * 360.0 / (height - 1).coerceAtLeast(1), 1.0, 1.0))
            } else {
                val columns = minOf(width, 160)
                for (column in 0 until columns) graphics.fillGradient(x + column * width / columns, y, x + (column + 1) * width / columns, bottom,
                    opaque or ColorPicker.rgb(hsv.hue, column.toDouble() / (columns - 1).coerceAtLeast(1), 1.0), opaque)
            }
            val ink = SpotlightTheme.color(if (isFocused) 0xFFF2F5FC else 0xFF9DADC3)
            graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, ink)
            val markerX = if (isHue) x + width / 2 else x + (hsv.saturation * (width - 1)).roundToInt()
            val markerY = y + ((if (isHue) hsv.hue / 360 else 1 - hsv.value) * (height - 1)).roundToInt()
            graphics.renderOutline(markerX - 3, markerY - 3, 7, 7, SpotlightTheme.color(0xFF101722))
            graphics.renderOutline(markerX - 2, markerY - 2, 5, 5, ink)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) { defaultButtonNarrationText(output) }
    }
}
