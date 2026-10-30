package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.platform.CreativeBanner
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.FittingMultiLineTextWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.entity.BannerPattern
import net.minecraft.world.level.block.entity.BannerPatternLayers

class BannerEditorScreen(private val parent: Screen?) : Screen(catalogText("Banner editor")) {
    private var loaded = false
    private var failure: String? = null
    private var patterns = emptyList<Holder.Reference<BannerPattern>>()
    private val layers = mutableListOf<BannerPatternLayers.Layer>()
    private var base = DyeColor.WHITE
    private var dye = DyeColor.WHITE
    private var query = ""
    private var notice = "Click a pattern to add · first layer bottom, last top. Draft stays here until closed."
    private val baseIcons by lazy { DyeColor.entries.associateWith { CreativeBanner.createBanner(it, emptyList()) } }

    private val dyeIcons by lazy {
        DyeColor.entries.associateWith { ItemStack(net.minecraft.world.item.DyeItem.byColor(it)) }
    }
    private val rows = mutableListOf<List<AbstractWidget>>()
    private lateinit var patternGrid: BannerPatternGrid
    private lateinit var layerList: BannerLayersWidget
    private lateinit var status: FittingMultiLineTextWidget
    private lateinit var giveBanner: Button
    private lateinit var giveShield: Button
    private lateinit var form: Form

    override fun isPauseScreen() = false
    override fun onClose() { minecraft!!.setScreen(parent) }

    override fun init() {
        if (!loaded) {
            loaded = true
            try {
                val registry = minecraft!!.connection?.registryAccess()?.lookup(Registries.BANNER_PATTERN)?.orElse(null)
                    ?: error("Banner pattern registry unavailable; join a world and reopen editor")
                patterns = registry.listElements().toList().sortedBy { it.key().location().toString() }
                check(patterns.isNotEmpty()) { "Banner pattern registry unavailable or empty" }
            } catch (error: Exception) {
                failure = error.message ?: error.javaClass.simpleName
            }
        }
        rows.clear()
        val flag = minecraft!!.entityModels.bakeLayer(ModelLayers.STANDING_BANNER_FLAG).getChild("flag")
        rows += listOf(ColorPicker("Base color", baseIcons, { base }) { base = it; refresh() })
        rows += listOf(ColorPicker("Next layer color", dyeIcons, { dye }) { dye = it; refresh() })
        val search = SpotlightEditBox(font, 0, 0, 100, 20, catalogText("Search registry patterns"))
        search.setMaxLength(256)
        search.value = query
        search.setHint(catalogText("Search pattern names / registry keys"))
        search.setResponder { query = it; refresh() }
        rows += listOf(search)
        patternGrid = BannerPatternGrid(flag) { pattern ->
            if (failure == null && layers.size < CreativeBanner.MAX_LAYERS) {
                layers += BannerPatternLayers.Layer(pattern, dye)
                notice = ""
                refresh()
            }
        }
        val preview = object : AbstractWidget(0, 0, 100, 180, catalogText("Current banner draft")) {
            override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
                graphics.enableScissor(x, y, right, bottom)
                graphics.themeText(font, catalogClip(font, "Current draft", width), x + 4, y + 4, SpotlightTheme.color(0xFFF2F5FC))
                val bannerWidth = minOf(64, (width - 12).coerceAtLeast(0), ((height - 24) / 2).coerceAtLeast(0))
                if (bannerWidth > 0) graphics.renderBanner(flag, base, BannerPatternLayers(layers.toList()),
                    x + (width - bannerWidth) / 2, y + 20, x + (width + bannerWidth) / 2, y + 20 + bannerWidth * 2)
                graphics.disableScissor()
            }
            override fun updateWidgetNarration(output: NarrationElementOutput) { defaultButtonNarrationText(output) }
        }
        rows += listOf(patternGrid, preview)
        layerList = BannerLayersWidget(flag, layers) { notice = ""; refresh() }
        rows += listOf(layerList)
        status = catalogDetails(0, 0, 100, 42, catalogText(notice), font)
        rows += listOf(status)
        giveBanner = catalogButton("Give banner", 0, 0, 100) { give(false) }
        giveShield = catalogButton("Give shield", 0, 0, 100) { give(true) }
        rows += listOf(giveBanner, giveShield)
        rows += listOf(catalogButton("Close", 0, 0, 100) { onClose() })
        form = addRenderableWidget(Form())
        repositionElements()
        refresh()
        setInitialFocus(form)
        form.setFocused(search)
    }

    override fun repositionElements() {
        if (!::form.isInitialized) return
        val panelWidth = minOf(SpotlightTheme.metric("editorWidth", 620, 160, 4096), (width - 24).coerceAtLeast(1))
        form.setRectangle(panelWidth, (height - 42).coerceAtLeast(1), (width - panelWidth) / 2, 34)
        form.layout()
    }

    private fun unavailableReason(): String? = when {
        failure != null -> failure
        minecraft!!.player == null || minecraft!!.level == null -> "Join a world first"
        minecraft!!.connection?.connection?.isConnected != true -> "Server connection unavailable"
        minecraft!!.gameMode?.playerMode != GameType.CREATIVE || minecraft!!.player?.abilities?.instabuild != true -> "Creative mode required to give"
        minecraft!!.player?.containerMenu !== minecraft!!.player?.inventoryMenu -> "Close the open container first"
        else -> null
    }

    private fun give(shield: Boolean) {
        try {
            check(unavailableReason() == null) { unavailableReason().orEmpty() }
            if (shield) CreativeBanner.giveShield(base, layers.toList()) else CreativeBanner.giveBanner(base, layers.toList())
            notice = if (shield) "Shield give requested" else "Banner give requested"
        } catch (error: Exception) {
            notice = "Give failed: ${error.message ?: error.javaClass.simpleName}"
        }
        refresh()
    }

    private fun filtered() = patterns.filter { pattern ->
        val text = "${pattern.key().location()} ${BannerPatternLayers.Layer(pattern, dye).description().string}".lowercase()
        var index = 0
        query.lowercase().all { character ->
            val found = text.indexOf(character, index)
            if (found < 0) false else { index = found + 1; true }
        }
    }

    private fun refresh() {
        if (!::status.isInitialized) return
        val matches = filtered()
        patternGrid.configure(matches, base, dye, failure == null && layers.size < CreativeBanner.MAX_LAYERS)
        layerList.configure(base)
        val reason = unavailableReason()
        giveBanner.active = reason == null
        giveShield.active = reason == null
        val message = listOfNotNull(reason, "${matches.size}/${patterns.size} patterns · ${layers.size}/${CreativeBanner.MAX_LAYERS} layers", notice.takeIf { it.isNotEmpty() }).joinToString("\n")
        if (status.message.string != message) status.message = catalogText(message)
    }

    override fun tick() { refresh() }
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.render(graphics, mouseX, mouseY, delta)
        graphics.themeText(font, catalogClip(font, failure ?: title.string, (width - 24).coerceAtLeast(1)), 12, 12, SpotlightTheme.color(0xFFF2F5FC))
    }

    private inner class ColorPicker(
        label: String,
        icons: Map<DyeColor, ItemStack>,
        private val current: () -> DyeColor,
        private val choose: (DyeColor) -> Unit,
    ) : AbstractContainerWidget(0, 0, 100, 56, catalogText("$label; arrows choose color")) {
        private val swatches = DyeColor.entries.map { color -> Swatch(color, icons.getValue(color)) }
        private fun columns() = if (width >= 16 * 20) 16 else 8
        private fun layoutSwatches() {
            val columns = columns()
            swatches.forEachIndexed { index, swatch -> swatch.setRectangle(20, 20, x + index % columns * 20, y + 14 + index / columns * 20) }
        }
        override fun children(): List<AbstractWidget> = swatches
        override fun contentHeight() = height
        override fun scrollRate() = 0.0
        override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
            val event = KeyEvent(keyCode, scanCode, modifiers)
            val offset = when (event.key()) {
                InputConstants.KEY_LEFT -> -1
                InputConstants.KEY_RIGHT -> 1
                InputConstants.KEY_UP -> -columns()
                InputConstants.KEY_DOWN -> columns()
                else -> return super.keyPressed(event.key(), event.scancode(), event.modifiers())
            }
            val index = (DyeColor.entries.indexOf(current()) + offset).coerceIn(0, swatches.lastIndex)
            setFocused(swatches[index])
            choose(swatches[index].color)
            return true
        }
        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            val event = MouseButtonEvent(mouseX, mouseY, button)

            layoutSwatches()
            return event.button() == InputConstants.MOUSE_BUTTON_LEFT && isMouseOver(event.x(), event.y()) && super.mouseClicked(event.x(), event.y(), event.button())
        }
        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            layoutSwatches()
            graphics.enableScissor(x, y, right, bottom)
            graphics.themeText(font, message.string.substringBefore(';'), x, y + 2, SpotlightTheme.color(0xFFF2F5FC))
            swatches.forEach { it.render(graphics, mouseX, mouseY, delta) }
            graphics.disableScissor()
        }
        override fun updateWidgetNarration(output: NarrationElementOutput) {
            (focused as? AbstractWidget)?.updateNarration(output) ?: defaultButtonNarrationText(output)
        }
        private inner class Swatch(val color: DyeColor, private val stack: ItemStack) : AbstractWidget(0, 0, 20, 20, stack.hoverName) {
            init { setTooltip(Tooltip.create(message)) }
            override fun onClick(mouseX: Double, mouseY: Double) { choose(color) }
            override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
                val event = KeyEvent(keyCode, scanCode, modifiers)
                if (!event.isConfirmation && event.key() != InputConstants.KEY_SPACE) return false
                choose(color)
                return true
            }
            override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
                graphics.renderItem(stack, x + 2, y + 2)
                if (current() == color || isHoveredOrFocused) graphics.renderOutline(x, y, width, height,
                    SpotlightTheme.color(if (current() == color) 0xFF81B8FF else 0xFFF2F5FC))
            }
            override fun updateWidgetNarration(output: NarrationElementOutput) { defaultButtonNarrationText(output) }
        }
    }

    private inner class Form : AbstractContainerWidget(0, 34, 100, 100, catalogText("Banner editor controls")) {
        private val offsets = mutableMapOf<AbstractWidget, Int>()
        private var totalHeight = 0
        override fun children(): List<AbstractWidget> = rows.flatten()
        override fun contentHeight() = totalHeight
        override fun scrollRate() = 24.0
        fun layout() {
            offsets.clear()
            var top = 0
            val usable = (width - 12).coerceAtLeast(1)
            rows.forEach { row ->
                val columns = if (usable < 200) 1 else row.size
                val cell = ((usable - (columns - 1) * 4) / columns).coerceAtLeast(1)
                row.forEachIndexed { index, widget ->
                    widget.x = x + (index % columns) * (cell + 4)
                    widget.width = cell
                    offsets[widget] = top + (index / columns) * (widget.height + 4)
                }
                top += ((row.size + columns - 1) / columns) * (row.first().height + 4)
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
            if (top < scrollAmount()) setScrollAmount(top.toDouble())
            else if (top + widget.height > scrollAmount() + height) setScrollAmount((top + widget.height - height).toDouble())
        }
        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean =
            isMouseOver(mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button)
        override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
            if (!isMouseOver(mouseX, mouseY)) return false
            if (patternGrid.isMouseOver(mouseX, mouseY) && patternGrid.mouseScrolled(mouseX, mouseY, horizontal, vertical)) return true
            if (layerList.isMouseOver(mouseX, mouseY) && layerList.mouseScrolled(mouseX, mouseY, horizontal, vertical)) return true
            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        }
        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            graphics.enableScissor(x, y, right, bottom)
            val inside = isMouseOver(mouseX.toDouble(), mouseY.toDouble())
            children().filter { it.bottom > y && it.y < bottom }.forEach {
                it.render(graphics, if (inside) mouseX else -1, if (inside) mouseY else -1, delta)
            }
            graphics.disableScissor()
            renderScrollbar(graphics)
        }
        override fun updateWidgetNarration(output: NarrationElementOutput) { (focused as? AbstractWidget)?.updateNarration(output) }
    }
}
