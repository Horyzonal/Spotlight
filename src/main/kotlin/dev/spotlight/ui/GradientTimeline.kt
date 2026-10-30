package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.ColorPicker
import dev.spotlight.core.GradientStop
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import kotlin.math.abs

internal class GradientTimeline(
    private val font: Font,
    stops: List<GradientStop>,
    var selected: Int,
    private val changed: (List<GradientStop>, Int) -> Unit,
    private val editColor: (Int) -> Unit,
) : AbstractWidget(0, 0, 100, 48, catalogText("Color list")) {
    var stops = timelineNormalize(stops)
    var minimum = 2
    private var captured = -1
    private var pressX = 0.0
    private var pressY = 0.0
    private var moved = false
    private val listLeft get() = x + 2
    private val listRight get() = (right - 26).coerceAtLeast(listLeft + 1)
    private val span get() = listRight - listLeft
    private fun edge(index: Int) = listLeft + index * span / stops.size
    private fun slot(mx: Double) = (((mx - listLeft) * stops.size / span).toInt()).coerceIn(0, stops.lastIndex)
    private fun description(index: Int) = "Color ${index + 1} of ${stops.size}: ${ColorPicker.hex(stops[index].color)}"
    private fun publish() {
        stops = timelineNormalize(stops)
        message = catalogText(description(selected))
        changed(stops, selected)
    }

    private fun hit(mx: Double, my: Double): Int {
        if (my < y + 2 || my >= bottom - 2 || mx < listLeft || mx >= listRight) return -1
        return stops.indices.firstOrNull { mx < edge(it + 1) } ?: -1
    }
    private fun closeHit(my: Double) = my < y + 17
    private fun plusHit(mx: Double, my: Double) = mx >= right - 22 && mx < right - 2 && my >= y + 2 && my < bottom - 2

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val event = MouseButtonEvent(mouseX, mouseY, button)

        if (!active || !visible || event.button() != InputConstants.MOUSE_BUTTON_LEFT || !isMouseOver(event.x(), event.y())) return false
        if (plusHit(event.x(), event.y())) { add(); return true }
        val index = hit(event.x(), event.y())
        if (index < 0) return false
        selected = index
        if (closeHit(event.y())) { remove(); return true }
        captured = index
        pressX = event.x()
        pressY = event.y()
        moved = false
        publish()
        return true
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dx: Double, dy: Double): Boolean {
        val event = MouseButtonEvent(mouseX, mouseY, button)
        if (captured < 0 || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false
        moved = moved || abs(event.x() - pressX) >= 4 || abs(event.y() - pressY) >= 4
        if (moved) {
            val target = slot(event.x())
            if (target != captured) {
                val result = timelineMove(stops, captured, target)
                stops = result.first
                captured = result.second
                selected = captured
                publish()
            }
        }
        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val event = MouseButtonEvent(mouseX, mouseY, button)
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || captured < 0) return false
        val index = captured
        captured = -1
        if (!moved && abs(event.x() - pressX) < 4 && abs(event.y() - pressY) < 4) editColor(index)
        return true
    }

    private fun remove() {
        if (stops.size <= minimum) { publish(); return }
        stops = stops.filterIndexed { index, _ -> index != selected }
        selected = selected.coerceAtMost(stops.lastIndex)
        publish()
    }

    private fun add() {
        if (stops.size >= 16) return
        stops = timelineInsertion(stops, selected)
        selected++
        publish()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (!active) return false
        when (event.key()) {
            InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT -> {
                val direction = if (event.key() == InputConstants.KEY_LEFT) -1 else 1
                if (event.hasShiftDown()) {
                    val result = timelineMove(stops, selected, selected + direction)
                    stops = result.first
                    selected = result.second
                } else selected = (selected + direction).coerceIn(0, stops.lastIndex)
                publish()
            }
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> editColor(selected)
            InputConstants.KEY_DELETE, InputConstants.KEY_BACKSPACE -> remove()
            else -> return false
        }
        return true
    }

    override fun charTyped(character: Char, modifiers: Int): Boolean {
        val event = CharacterEvent(character.code, modifiers)
        if (!active || event.codepoint() != '+'.code) return false
        add()
        return true
    }

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val ink = SpotlightTheme.color(0xFFF2F5FC)
        val muted = SpotlightTheme.color(0xFF9DADC3)
        val background = SpotlightTheme.color(0xFF161D29)
        graphics.fill(x, y, right, bottom, background)
        graphics.renderOutline(x, y, width, height, SpotlightTheme.color(if (isFocused) 0xFFF2F5FC else 0xFF415877))
        stops.forEachIndexed { index, stop ->
            val left = edge(index)
            val end = edge(index + 1)
            val tileWidth = end - left
            if (captured == index && moved) {
                graphics.fill(left + 1, y + 3, end - 1, bottom - 3, SpotlightTheme.color(0xFF22334B))
                graphics.renderOutline(left, y + 2, tileWidth, height - 4, SpotlightTheme.color(0xFF81B8FF))
                return@forEachIndexed
            }
            graphics.fill(left + 1, y + 18, end - 1, bottom - 3, stop.color or 0xFF000000.toInt())
            graphics.renderOutline(left, y + 2, tileWidth, height - 4, if (index == selected) ink else muted)
            if (tileWidth >= font.width(catalogText("×")) + 2) {
                graphics.themeCenteredText(font, catalogText("×"), (left + end) / 2, y + 5, if (stops.size > minimum) ink else muted)
            }
            val hex = catalogText(ColorPicker.hex(stop.color))
            if (tileWidth >= font.width(hex) + 4) {
                graphics.fill(left + 1, bottom - 16, end - 1, bottom - 3, background)
                graphics.themeCenteredText(font, hex, (left + end) / 2, bottom - 14, ink)
            }
        }
        graphics.renderOutline(right - 22, y + 2, 20, height - 4, muted)
        graphics.themeCenteredText(font, catalogText("+"), right - 12, y + (height - font.lineHeight) / 2, if (stops.size < 16) ink else muted)
        val hovered = hit(mouseX.toDouble(), mouseY.toDouble())
        val tip = when {
            plusHit(mouseX.toDouble(), mouseY.toDouble()) -> if (stops.size < 16) "Add interpolated color after selection; duplicate last color at end · +" else "Maximum 16 colors"
            hovered >= 0 && closeHit(mouseY.toDouble()) -> if (stops.size > minimum) "Remove ${description(hovered)} · Delete" else "Keep at least $minimum colors${if (minimum == 2) " in gradient mode" else ""}"
            hovered >= 0 -> "${description(hovered)} · drag to reorder; click to edit color"
            else -> "Color list · Left/Right: select · Shift+Left/Right: move one slot · Enter: edit · Delete: remove · +: add"
        }
        if (captured < 0 && isMouseOver(mouseX.toDouble(), mouseY.toDouble())) graphics.setTooltipForNextFrame(catalogText(tip), mouseX, mouseY)
    }


    fun extractDragGhost(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (captured !in stops.indices || !moved) return
        val color = stops[captured].color
        val ghostWidth = (edge(captured + 1) - edge(captured)).coerceIn(24, 120)
        val ghostHeight = height - 4
        val left = mouseX - ghostWidth / 2
        val top = mouseY - ghostHeight / 2
        graphics.fill(left + 3, top + 3, left + ghostWidth + 3, top + ghostHeight + 3,
            SpotlightTheme.color(0x99060A12))
        graphics.fill(left, top, left + ghostWidth, top + ghostHeight, color or 0xCC000000.toInt())
        graphics.renderOutline(left, top, ghostWidth, ghostHeight, SpotlightTheme.color(0xFF81B8FF))
        val text = catalogText(ColorPicker.hex(color))
        if (ghostWidth >= font.width(text) + 4) {
            graphics.fill(left, top + ghostHeight - 14, left + ghostWidth, top + ghostHeight,
                SpotlightTheme.color(0xFA101722))
            graphics.themeCenteredText(font, text, mouseX, top + ghostHeight - 12, SpotlightTheme.color(0xFFF2F5FC))
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        message = catalogText("${description(selected)}. Left and Right select colors. Shift with Left or Right moves one slot. Enter edits color. Delete removes; keep at least $minimum colors. Plus adds after selection; maximum 16 colors.")
        defaultButtonNarrationText(output)
    }
}

internal fun timelineNormalize(stops: List<GradientStop>): List<GradientStop> =
    ColorPicker.positionedStops(stops.map { it.color })

internal fun timelineMove(stops: List<GradientStop>, index: Int, slot: Int): Pair<List<GradientStop>, Int> {
    val target = slot.coerceIn(0, stops.lastIndex)
    val reordered = stops.toMutableList()
    reordered.add(target, reordered.removeAt(index))
    return timelineNormalize(reordered) to target
}

internal fun timelineInsertion(stops: List<GradientStop>, selected: Int): List<GradientStop> {
    if (stops.size >= 16) return timelineNormalize(stops)
    val color = stops[selected].color
    val next = stops.getOrNull(selected + 1)?.color ?: color
    var midpoint = 0
    for (shift in 0..16 step 8) {
        midpoint = midpoint or (((((color shr shift) and 255) + ((next shr shift) and 255) + 1) / 2) shl shift)
    }
    return timelineNormalize(stops.toMutableList().apply { add(selected + 1, GradientStop(midpoint, 0.0)) })
}
