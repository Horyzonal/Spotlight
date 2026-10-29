package dev.spotlight.ui

import dev.spotlight.platform.CreativeBanner
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.model.`object`.banner.BannerFlagModel
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.Holder

import net.minecraft.resources.Identifier
import net.minecraft.world.item.DyeColor

import net.minecraft.world.level.block.entity.BannerPattern
import net.minecraft.world.level.block.entity.BannerPatternLayers
import org.lwjgl.sdl.SDLKeycode

internal fun bannerGridRows(count: Int) = (count.coerceAtLeast(0) + 3) / 4

internal class BannerPatternGrid(
    private val flag: BannerFlagModel,
    private val choose: (Holder.Reference<BannerPattern>) -> Unit,
) : AbstractContainerWidget(0, 0, 100, 180, catalogText("Banner patterns; arrows navigate, click / Enter / Space adds layer"), defaultSettings(6)) {
    private val tiles = mutableListOf<Tile>()
    private var cachedPatterns = emptyList<Holder.Reference<BannerPattern>>()
    private var cachedBase: DyeColor? = null
    private var cachedDye: DyeColor? = null
    var selected: Holder.Reference<BannerPattern>? = null
    private var canAdd = true

    fun configure(patterns: List<Holder.Reference<BannerPattern>>, base: DyeColor, dye: DyeColor, additionsAllowed: Boolean) {
        if (canAdd != additionsAllowed) {
            canAdd = additionsAllowed
            tiles.forEach { it.setTooltip(Tooltip.create(if (canAdd) it.message else catalogText("Cannot add: layer limit ${CreativeBanner.MAX_LAYERS} or registry unavailable"))) }
        }
        if (patterns == cachedPatterns && base == cachedBase && dye == cachedDye) return
        cachedPatterns = patterns
        cachedBase = base
        cachedDye = dye
        setFocused(null as GuiEventListener?)
        tiles.clear()
        
        patterns.forEach { pattern ->
            val layer = BannerPatternLayers.Layer(pattern, dye)
            tiles += Tile(pattern, BannerPatternLayers(listOf(layer)), layer.description().string)
        }
        setScrollAmount(0.0)
    }

    override fun children(): List<AbstractWidget> = tiles
    override fun contentHeight() = bannerGridRows(tiles.size) * 40
    override fun scrollRate() = 40.0
    override fun setScrollAmount(amount: Double) {
        super.setScrollAmount(amount)
        layoutTiles()
    }

    private fun layoutTiles() {
        val cell = ((width - 8).coerceAtLeast(4) / 4).coerceAtLeast(1)
        tiles.forEachIndexed { index, tile ->
            tile.setRectangle(cell, 40, x + index % 4 * cell, y + index / 4 * 40 - scrollAmount().toInt())
        }
    }

    override fun setFocused(listener: GuiEventListener?) {
        super.setFocused(listener)
        val index = tiles.indexOf(listener)
        if (index < 0) return
        val top = index / 4 * 40
        if (top < scrollAmount()) setScrollAmount(top.toDouble())
        else if (top + 40 > scrollAmount() + height) setScrollAmount((top + 40 - height).toDouble())
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val offset = when (event.key()) {
            SDLKeycode.SDLK_LEFT -> -1
            SDLKeycode.SDLK_RIGHT -> 1
            SDLKeycode.SDLK_UP -> -4
            SDLKeycode.SDLK_DOWN -> 4
            else -> return super.keyPressed(event)
        }
        if (tiles.isEmpty()) return false
        val index = (tiles.indexOf(focused).coerceAtLeast(0) + offset).coerceIn(0, tiles.lastIndex)
        setFocused(tiles[index])
        selected = tiles[index].pattern
        return true
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        layoutTiles()
        return event.button() == 1 && isMouseOver(event.x(), event.y()) && super.mouseClicked(event, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean =
        isMouseOver(mouseX, mouseY) && super.mouseScrolled(mouseX, mouseY, horizontal, vertical)

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        layoutTiles()
        graphics.enableScissor(x, y, right, bottom)
        val inside = isMouseOver(mouseX.toDouble(), mouseY.toDouble())
        tiles.filter { it.bottom > y && it.y < bottom }.forEach {
            it.extractRenderState(graphics, if (inside) mouseX else -1, if (inside) mouseY else -1, delta)
        }
        graphics.disableScissor()
        extractScrollbar(graphics, mouseX, mouseY)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        (focused as? AbstractWidget)?.updateNarration(output)
    }

    private inner class Tile(val pattern: Holder.Reference<BannerPattern>, val preview: BannerPatternLayers, label: String) :
        AbstractWidget(0, 0, 24, 40, catalogText("$label · ${pattern.key().identifier()}")) {
        init { setTooltip(Tooltip.create(if (canAdd) message else catalogText("Cannot add: layer limit ${CreativeBanner.MAX_LAYERS} or registry unavailable"))) }
        private fun add() { selected = pattern; if (canAdd) choose(pattern) }
        override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) { add() }
        override fun keyPressed(event: KeyEvent): Boolean {
            if (event.key() != SDLKeycode.SDLK_RETURN && event.key() != SDLKeycode.SDLK_SPACE) return false
            add()
            return true
        }
        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            val state = if (selected == pattern) "pattern_selected" else if (isHoveredOrFocused) "pattern_highlighted" else "pattern"
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("container/loom/$state"), x, y, width, height)
            val bannerWidth = minOf(16, (width - 4).coerceAtLeast(0))
            if (bannerWidth > 0) graphics.bannerPattern(flag, requireNotNull(cachedBase), preview,
                x + (width - bannerWidth) / 2, y + 4, x + (width + bannerWidth) / 2, y + 36)
        }
        override fun updateWidgetNarration(output: NarrationElementOutput) { defaultButtonNarrationText(output) }
    }
}
