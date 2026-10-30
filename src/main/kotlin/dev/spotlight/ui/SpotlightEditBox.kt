package dev.spotlight.ui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

class SpotlightEditBox(font: Font, x: Int, y: Int, width: Int, height: Int, label: Component) :
    EditBox(catalogFont(font), x, y, width.coerceAtLeast(1), height.coerceAtLeast(1), label) {
    private var bordered = true

    init {
        setFormatter { text, _ -> catalogText(text).visualOrderText }
        if (!SpotlightTheme.isDefault()) {
            setTextColor(SpotlightTheme.color(0xFFF2F5FC))
            setTextColorUneditable(SpotlightTheme.color(0xFF91A0B5))
        }
    }

    override fun setHint(hint: Component) {
        super.setHint(if (SpotlightTheme.isDefault()) hint
            else catalogText(hint).withStyle { it.withColor(SpotlightTheme.color(0xFF9DADC3)) })
    }


    override fun setBordered(bordered: Boolean) { this.bordered = bordered; super.setBordered(bordered) }
    override fun getInnerWidth(): Int = if (bordered) width - 8 else width

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        if (SpotlightTheme.isDefault()) {
            super.renderWidget(graphics, mouseX, mouseY, delta)
            return
        }
        if (!isVisible) return
        graphics.fill(x, y, right, bottom, SpotlightTheme.color(0xFF161D29))
        graphics.renderOutline(x, y, width, height,
            SpotlightTheme.color(if (isFocused && active) 0xFF81B8FF else 0xFF415877))

        val wasBordered = bordered
        super.setBordered(false)
        val oldX = x
        val oldY = y
        if (wasBordered) { x += 4; y += (height - 8) / 2 }
        try {
            super.renderWidget(graphics, mouseX, mouseY, delta)
        } finally {
            x = oldX
            y = oldY

            super.setBordered(wasBordered)
        }
    }
}

fun spotlightEditBox(font: Font, x: Int, y: Int, width: Int, height: Int, label: Component): EditBox =
    SpotlightEditBox(font, x, y, width, height, label)
