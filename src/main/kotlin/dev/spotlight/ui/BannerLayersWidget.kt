package dev.spotlight.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.model.`object`.banner.BannerFlagModel
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.entity.BannerPatternLayers
import org.lwjgl.sdl.SDLKeycode

internal fun <T> moveBannerLayer(layers: MutableList<T>, index: Int, target: Int): Int {
    if (index !in layers.indices || target !in layers.indices) return index
    layers.add(target, layers.removeAt(index))
    return target
}

internal class BannerLayersWidget(
    private val flag: BannerFlagModel,
    private val layers: MutableList<BannerPatternLayers.Layer>,
    private val changed: () -> Unit,
) : AbstractContainerWidget(0, 0, 100, 180, catalogText("Layers: first bottom, last top. Arrows select; $primaryModifierLabel+arrows reorder; Delete removes. Drag handles reorder."), defaultSettings(6)) {
    private var base = DyeColor.WHITE
    private var snapshot = emptyList<BannerPatternLayers.Layer>()
    private val rows = mutableListOf<Row>()
    private var selected = -1
    private var draggingHandle = false

    fun configure(color: DyeColor) {
        base = color
        if (snapshot == layers) return
        snapshot = layers.toList()
        rows.clear()
        layers.forEachIndexed { index, layer -> rows += Row(index, BannerPatternLayers(layers.take(index + 1)), layer.description().string) }
        selected = selected.coerceAtMost(layers.lastIndex)
        super.setFocused(rows.getOrNull(selected))
        setScrollAmount(scrollAmount())
    }

    override fun children(): List<AbstractWidget> = rows
    override fun contentHeight() = rows.size * 32
    override fun scrollRate() = 32.0
    override fun setScrollAmount(amount: Double) { super.setScrollAmount(amount); layoutRows() }
    private fun layoutRows() {
        rows.forEachIndexed { index, row -> row.setRectangle((width - 8).coerceAtLeast(1), 32, x, y + index * 32 - scrollAmount().toInt()) }
    }
    override fun setFocused(listener: GuiEventListener?) {
        super.setFocused(listener)
        val index = rows.indexOf(listener)
        if (index < 0) return
        selected = index
        val top = index * 32
        if (top < scrollAmount()) setScrollAmount(top.toDouble())
        else if (top + 32 > scrollAmount() + height) setScrollAmount((top + 32 - height).toDouble())
    }
    private fun remove(index: Int) {
        if (index !in layers.indices) return
        layers.removeAt(index)
        selected = index.coerceAtMost(layers.lastIndex)
        changed()
        setFocused(rows.getOrNull(selected))
    }
    private fun move(target: Int) {
        selected = moveBannerLayer(layers, selected, target)
        changed()
        setFocused(rows.getOrNull(selected))
    }
    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == SDLKeycode.SDLK_DELETE || event.key() == SDLKeycode.SDLK_BACKSPACE) { remove(selected); return true }
        val offset = when (event.key()) {
            SDLKeycode.SDLK_UP -> -1
            SDLKeycode.SDLK_DOWN -> 1
            else -> return super.keyPressed(event)
        }
        if (rows.isEmpty()) return true
        val target = (selected.coerceAtLeast(0) + offset).coerceIn(0, rows.lastIndex)
        if (primaryModifier(event)) move(target) else setFocused(rows[target])
        return true
    }
    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        layoutRows()
        if (event.button() != 1 || !isMouseOver(event.x(), event.y())) return false
        val row = rows.firstOrNull { it.isMouseOver(event.x(), event.y()) }
        if (row != null) {
            setFocused(row)
            if (event.x() >= row.right - 20) { remove(row.index); return true }
            draggingHandle = event.x() < row.x + 14
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }
    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (!draggingHandle || rows.isEmpty()) return super.mouseDragged(event, dx, dy)
        val target = ((event.y() - y + scrollAmount()) / 32).toInt().coerceIn(0, rows.lastIndex)
        if (target != selected) move(target)
        return true
    }
    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        draggingHandle = false
        return super.mouseReleased(event)
    }
    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double) =
        isMouseOver(mouseX, mouseY) && super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        layoutRows()
        graphics.enableScissor(x, y, right, bottom)
        if (rows.isEmpty()) graphics.themeText(Minecraft.getInstance().font, "No layers · click a pattern", x + 4, y + 6, SpotlightTheme.color(0xFFF2F5FC))
        val inside = isMouseOver(mouseX.toDouble(), mouseY.toDouble())
        rows.filter { it.bottom > y && it.y < bottom }.forEach { it.extractRenderState(graphics, if (inside) mouseX else -1, if (inside) mouseY else -1, delta) }
        graphics.disableScissor()
        extractScrollbar(graphics, mouseX, mouseY)
    }
    override fun updateWidgetNarration(output: NarrationElementOutput) {
        if (focused is AbstractWidget) (focused as AbstractWidget).updateNarration(output) else defaultButtonNarrationText(output)
    }
    private inner class Row(val index: Int, val cumulative: BannerPatternLayers, label: String) :
        AbstractWidget(0, 0, 100, 32, catalogText("Layer ${index + 1}: $label. $primaryModifierLabel+Up/Down moves; Delete removes.")) {
        init { setTooltip(Tooltip.create(message)) }
        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            val font = Minecraft.getInstance().font
            if (selected == index) graphics.outline(x, y, width, height, SpotlightTheme.color(0xFF81B8FF))
            graphics.themeText(font, "↕", x + 3, y + 11, SpotlightTheme.color(0xFFF2F5FC))
            graphics.bannerPattern(flag, base, cumulative, x + 16, y + 2, x + 30, y + 30)
            graphics.themeText(font, catalogClip(font, "${index + 1}. ${layers[index].description().string}", (width - 58).coerceAtLeast(1)), x + 34, y + 11, SpotlightTheme.color(0xFFF2F5FC))
            graphics.themeText(font, "×", right - 14, y + 11, SpotlightTheme.color(0xFFF2F5FC))
            if (isHovered && mouseX >= right - 20) graphics.setTooltipForNextFrame(catalogText("Remove layer ${index + 1}"), mouseX, mouseY)
        }
        override fun updateWidgetNarration(output: NarrationElementOutput) { defaultButtonNarrationText(output) }
    }
}
