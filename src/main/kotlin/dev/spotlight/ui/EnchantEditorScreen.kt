package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.Entry
import dev.spotlight.core.Fuzzy
import dev.spotlight.platform.CustomEnchantments
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.FittingMultiLineTextWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.Holder
import net.minecraft.world.item.enchantment.Enchantment

class EnchantEditorScreen(private val parent: Screen?) : Screen(catalogText("Custom enchantments")) {
    private var query = ""
    private var levelText = "1"
    private var selected: Holder<Enchantment>? = null
    private var page = 0
    private var notice = ""
    private val controls = mutableListOf<AbstractWidget>()
    private val rows = mutableListOf<Button>()
    private var results = emptyList<Holder.Reference<Enchantment>>()
    private lateinit var search: SpotlightEditBox
    private lateinit var level: SpotlightEditBox
    private lateinit var selection: FittingMultiLineTextWidget
    private lateinit var status: FittingMultiLineTextWidget
    private lateinit var add: Button
    private lateinit var previous: Button
    private lateinit var next: Button
    private lateinit var form: Form

    override fun isPauseScreen() = false
    override fun onClose() { minecraft!!.setScreen(parent) }

    override fun init() {
        controls.clear()
        rows.clear()
        search = SpotlightEditBox(font, 0, 0, 100, 20, catalogText("Search enchantment names / registry IDs"))
        search.setMaxLength(256)
        search.value = query
        search.setHint(catalogText("Search enchantment names / registry IDs"))
        search.setResponder { query = it; page = 0; refresh() }
        controls += search
        repeat(5) { index ->
            rows += catalogButton("", 0, 0, 100) {
                chooseEnchantment(page * 5 + index)
            }
        }
        controls += rows
        previous = catalogButton("Previous enchantments", 0, 0, 100) { page--; refresh() }
        next = catalogButton("Next enchantments", 0, 0, 100) { page++; refresh() }
        controls += previous
        controls += next
        selection = catalogDetails(0, 0, 100, 20, catalogText("Select an enchantment"), font)
        controls += selection
        level = SpotlightEditBox(font, 0, 0, 100, 20, catalogText("Level: ASCII integer 1..255"))
        level.setMaxLength(32)
        level.value = levelText
        level.setResponder { levelText = it; refresh() }
        controls += level
        add = catalogButton("Add to held item", 0, 0, 100) { addToHeldItem() }
        controls += add
        controls += catalogButton("Back", 0, 0, 100) { onClose() }
        status = catalogDetails(0, 0, 100, 30, catalogText(notice), font)
        controls += status
        form = addRenderableWidget(Form())
        repositionElements()
        refresh()
        setInitialFocus(form)
        form.setFocused(search)
    }

    private fun chooseEnchantment(index: Int) {
        selected = results.getOrNull(index) ?: return
        refresh()
        form.setFocused(level)
        level.setHighlightPos(0)
        level.moveCursorToEnd(true)
    }

    private fun addToHeldItem() {
        val holder = selected ?: return
        val value = CustomEnchantments.parseLevel(levelText) ?: return
        notice = try {
            val item = CustomEnchantments.apply(holder, value)
            "Added ${holder.value().description().string} $value to $item (requested; server may reject)"
        } catch (error: Exception) {
            error.message ?: error.javaClass.simpleName
        }
        refresh()
    }

    private fun refresh() {
        if (!::status.isInitialized) return
        val holders = minecraft!!.connection?.let { CustomEnchantments.registered(it.registryAccess()) }.orEmpty()
        val byId = holders.associateBy { it.key().location().toString() }
        results = Fuzzy.search(query, holders.map { Entry(it.key().location().toString(), it.value().description().string) }, limit = holders.size)
            .map { byId.getValue(it.id) }
        page = page.coerceIn(0, ((results.size - 1).coerceAtLeast(0) / 5))
        rows.forEachIndexed { index, button ->
            val holder = results.getOrNull(page * 5 + index)
            button.active = holder != null
            button.message = catalogText(holder?.let { "${if (it == selected) "> " else ""}${it.value().description().string} · ${it.key().location()}" } ?: "—")
            button.setTooltip(holder?.let { Tooltip.create(catalogText("${it.value().description().string} · ${it.key().location()} · native max ${it.value().maxLevel}")) })
        }
        previous.active = page > 0
        next.active = (page + 1) * 5 < results.size
        selection.message = catalogText(selected?.value()?.description()?.string ?: "Select an enchantment")
        val validLevel = CustomEnchantments.parseLevel(levelText) != null
        add.active = selected != null && validLevel
        status.message = catalogText(if (!validLevel) "Level must be an ASCII integer from 1 to 255" else notice)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (event.isEscape) { onClose(); return true }
        if (event.isConfirmation && primaryModifier(event) && !event.hasAltDown()) {
            addToHeldItem()
            return true
        }
        val row = rows.indexOfFirst { it.isFocused }
        if (!primaryModifier(event) && !event.hasAltDown()) {
            if ((search.isFocused || row >= 0) && (event.isDown || event.isUp)) {
                if (results.isNotEmpty()) {
                    val target = if (row < 0) page * 5 + if (event.isDown) 0 else minOf(4, results.lastIndex - page * 5)
                        else (page * 5 + row + if (event.isDown) 1 else -1).coerceIn(0, results.lastIndex)
                    selected = results[target]
                    page = target / 5
                    refresh()
                    form.setFocused(rows[target % 5])
                }
                return true
            }
            if (event.key() == InputConstants.KEY_TAB && !event.hasShiftDown() && (search.isFocused || row >= 0)) {
                form.setFocused(level)
                return true
            }
            if (event.isConfirmation && (search.isFocused || row >= 0)) {
                chooseEnchantment(page * 5 + row.coerceAtLeast(0))
                return true
            }
            if (event.isConfirmation && level.isFocused) {
                addToHeldItem()
                return true
            }
        }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers())
    }

    override fun repositionElements() {
        if (!::form.isInitialized) return
        val panelWidth = minOf(620, (width - 24).coerceAtLeast(1))
        form.setRectangle(panelWidth, (height - 42).coerceAtLeast(1), (width - panelWidth) / 2, 34)
        form.layout()
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(g, mouseX, mouseY, delta)
        g.themeText(font, title.string, 12, 12, -1)
    }

    private inner class Form : AbstractContainerWidget(0, 34, 100, 100, catalogText("Custom enchantment controls")) {
        private val offsets = mutableMapOf<AbstractWidget, Int>()
        private var total = 0
        override fun children(): List<AbstractWidget> = controls
        override fun contentHeight() = total
        override fun scrollRate() = 24.0
        fun layout() {
            offsets.clear()
            total = 0
            controls.forEach { widget ->
                widget.x = x
                widget.width = (width - 12).coerceAtLeast(1)
                offsets[widget] = total
                total += widget.height + 4
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
        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean =
            isMouseOver(mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button)
        override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            g.enableScissor(x, y, right, bottom)
            val inside = isMouseOver(mouseX.toDouble(), mouseY.toDouble())
            controls.filter { it.bottom > y && it.y < bottom }.forEach {
                it.render(g, if (inside) mouseX else -1, if (inside) mouseY else -1, delta)
            }
            g.disableScissor()
            renderScrollbar(g)
        }
        override fun updateWidgetNarration(output: NarrationElementOutput) {
            (focused as? AbstractWidget)?.updateNarration(output) ?: defaultButtonNarrationText(output)
        }
    }
}
