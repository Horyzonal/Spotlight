package dev.spotlight.ui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput

internal const val macroFieldStep = 38

internal fun macroFormScroll(index: Int, scroll: Int, viewport: Int, count: Int): Int {
    val top = index * macroFieldStep
    val bottom = top + macroFieldStep
    return (if (top < scroll) top else if (bottom > scroll + viewport) bottom - viewport else scroll)
        .coerceIn(0, (count * macroFieldStep - viewport).coerceAtLeast(0))
}


internal class MacroFormLayout(
    x: Int, y: Int, width: Int, height: Int,
    private val font: Font,
    val fields: List<EditBox>,
    private val labels: List<String>,
    private val onFocus: (Int) -> Unit,
) : UiScrollContainer(x, y, width, height, catalogText("Macro arguments")) {
    override fun children(): List<EditBox> = fields
    override fun contentHeight() = fields.size * macroFieldStep
    override fun scrollRate() = macroFieldStep.toDouble()

    private fun positionFields() {
        fields.forEachIndexed { index, field ->
            field.x = x
            field.y = y + index * macroFieldStep + 14 - scrollAmount().toInt()
            field.width = (width - 10).coerceAtLeast(1)
        }
    }

    override fun setScrollAmount(amount: Double) {
        super.setScrollAmount(amount)
        positionFields()
    }

    override fun setFocused(listener: GuiEventListener?) {
        super.setFocused(listener)
        val index = fields.indexOf(listener)
        if (index >= 0) {
            setScrollAmount(macroFormScroll(index, scrollAmount().toInt(), height, fields.size).toDouble())
            onFocus(index)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val event = MouseButtonEvent(mouseX, mouseY, button)

        if (!isMouseOver(event.x(), event.y())) return false
        positionFields()
        return super.mouseClicked(event.x(), event.y(), event.button())
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dx: Double, dy: Double): Boolean {
        val event = MouseButtonEvent(mouseX, mouseY, button)

        if (!isMouseOver(event.x(), event.y()) && !(event.x() >= scrollBarX() && event.x() < scrollBarX() + 6 && event.y() >= y && event.y() < bottom)) return false
        return super.mouseDragged(event.x(), event.y(), event.button(), dx, dy)
    }

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        positionFields()
        graphics.enableScissor(x, y, right, bottom)
        val inside = isMouseOver(mouseX.toDouble(), mouseY.toDouble())
        fields.forEachIndexed { index, field ->
            if (field.bottom > y && field.y - 14 < bottom) {
                graphics.themeText(font, catalogClip(font, labels[index], field.width), field.x, field.y - 12,
                    SpotlightTheme.color(0xFFF2F5FC))
                field.render(graphics, if (inside) mouseX else -1, if (inside) mouseY else -1, delta)
            }
        }
        graphics.disableScissor()
        renderScrollbar(graphics)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        (focused as? EditBox)?.updateNarration(output)
    }
}

internal fun macroFormSelect(drafts: MutableMap<String, String>, active: String, value: String) {
    drafts[active] = value
}
