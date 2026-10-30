package dev.spotlight.ui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.network.chat.Component

// ponytail: 1.21.1 containers lack scrolling; remove when native scrolling containers return.
internal abstract class UiScrollContainer(x: Int, y: Int, width: Int, height: Int, label: Component) :
    AbstractContainerWidget(x, y, width, height, label) {
    private var amount = 0.0
    private var scrolling = false
    protected abstract fun contentHeight(): Int
    protected abstract fun scrollRate(): Double
    fun scrollAmount() = amount
    fun maxScrollAmount() = (contentHeight() - height).coerceAtLeast(0)
    open fun setScrollAmount(amount: Double) { this.amount = amount.coerceIn(0.0, maxScrollAmount().toDouble()) }
    protected fun scrollBarX() = right - 6
    private fun thumbHeight() = (height.toLong() * height / contentHeight().coerceAtLeast(1)).toInt().coerceIn(minOf(32, height), height)

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!active || !visible) return false
        scrolling = button == 0 && maxScrollAmount() > 0 && mouseX >= scrollBarX() && mouseX < right && mouseY >= y && mouseY < bottom
        return scrolling || super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dx: Double, dy: Double): Boolean {
        if (!scrolling || button != 0) return super.mouseDragged(mouseX, mouseY, button, dx, dy)
        setScrollAmount(amount + dy * maxScrollAmount() / (height - thumbHeight()).coerceAtLeast(1))
        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val wasScrolling = scrolling
        scrolling = false
        return super.mouseReleased(mouseX, mouseY, button) || wasScrolling
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (!active || !visible || !isMouseOver(mouseX, mouseY) || maxScrollAmount() == 0) return false
        val before = amount
        setScrollAmount(amount - vertical * scrollRate())
        return before != amount
    }

    protected fun renderScrollbar(graphics: GuiGraphics) {
        if (maxScrollAmount() == 0) return
        val thumb = thumbHeight()
        val top = y + (amount * (height - thumb) / maxScrollAmount()).toInt()
        graphics.fill(scrollBarX(), y, right, bottom, SpotlightTheme.color(0xFF161D29))
        graphics.fill(scrollBarX(), top, right, top + thumb, SpotlightTheme.color(0xFF415877))
    }
}
