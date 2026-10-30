package dev.spotlight.ui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.network.chat.Component

// ponytail: 1.20.1 lacks container widgets; keep native child routing until platform supplies one.
internal abstract class UiScrollContainer(x: Int, y: Int, width: Int, height: Int, label: Component) :
    AbstractWidget(x, y, width, height, label), ContainerEventHandler {
    private var child: GuiEventListener? = null
    private var dragging = false
    override fun getFocused() = child
    override fun setFocused(listener: GuiEventListener?) {
        child?.setFocused(false)
        child = listener
        child?.setFocused(true)
    }
    override fun isDragging() = dragging
    override fun setDragging(dragging: Boolean) { this.dragging = dragging }
    override fun setFocused(focused: Boolean) {
        super<AbstractWidget>.setFocused(focused)
        if (!focused) setFocused(null as GuiEventListener?)
    }
    override fun isFocused() = super<AbstractWidget>.isFocused()
    override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? = super<ContainerEventHandler>.nextFocusPath(event)
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int) = super<ContainerEventHandler>.keyPressed(keyCode, scanCode, modifiers)
    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int) = super<ContainerEventHandler>.keyReleased(keyCode, scanCode, modifiers)
    override fun charTyped(character: Char, modifiers: Int) = super<ContainerEventHandler>.charTyped(character, modifiers)
    fun setRectangle(width: Int, height: Int, x: Int, y: Int) {
        this.width = width
        this.height = height
        this.x = x
        this.y = y
    }
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
        return scrolling || super<ContainerEventHandler>.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dx: Double, dy: Double): Boolean {
        if (!scrolling || button != 0) return super<ContainerEventHandler>.mouseDragged(mouseX, mouseY, button, dx, dy)
        setScrollAmount(amount + dy * maxScrollAmount() / (height - thumbHeight()).coerceAtLeast(1))
        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val wasScrolling = scrolling
        scrolling = false
        return super<ContainerEventHandler>.mouseReleased(mouseX, mouseY, button) || wasScrolling
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, vertical: Double): Boolean {
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
