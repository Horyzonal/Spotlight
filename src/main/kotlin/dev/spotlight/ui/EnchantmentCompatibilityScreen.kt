package dev.spotlight.ui

import dev.spotlight.core.ThemeColor
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.ItemEnchantments
import org.lwjgl.glfw.GLFW

internal enum class EnchantmentSupport(val label: String, val color: ThemeColor) {
    PRIMARY("Primary", ThemeColor.FAVORITE),
    COMPATIBLE("Compatible", ThemeColor.ACCENT),
    UNSUPPORTED("Not supported", ThemeColor.ERROR),
    EMPTY("Hold an item", ThemeColor.TEXT_MUTED);
}

internal fun enchantmentSupport(empty: Boolean, supported: Boolean, primary: Boolean) = when {
    empty -> EnchantmentSupport.EMPTY
    !supported -> EnchantmentSupport.UNSUPPORTED
    primary -> EnchantmentSupport.PRIMARY
    else -> EnchantmentSupport.COMPATIBLE
}

internal data class EnchantmentCard(val holder: Holder.Reference<Enchantment>, val support: EnchantmentSupport, val conflicts: List<Holder<Enchantment>>) {
    val id = holder.key().location().toString()
    val name = holder.value().description().string
    val levels = "Levels ${holder.value().minLevel}–${holder.value().maxLevel}"
    val conflictLabel = if (conflicts.isEmpty()) "" else "Conflicts: ${conflicts.joinToString { it.value().description().string }}"
    val details get() = "$name\n$id\n$levels · Weight ${holder.value().weight}\n${support.label}" +
        (if (conflictLabel.isEmpty()) "" else "\n$conflictLabel")
    fun matches(query: String): Boolean {
        val searchable = "$name $id ${support.label} $conflictLabel" +
            if (support == EnchantmentSupport.PRIMARY) " compatible" else ""
        return query.trim().split(Regex("\\s+")).all { searchable.contains(it, ignoreCase = true) }
    }
}

internal fun enchantmentCard(holder: Holder.Reference<Enchantment>, held: ItemStack): EnchantmentCard {
    val value = holder.value()
    val equipped = held.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).keySet() +
        held.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).keySet()
    return EnchantmentCard(holder, enchantmentSupport(held.isEmpty, value.isSupportedItem(held), value.isPrimaryItem(held)),
        equipped.filter { it != holder && !Enchantment.areCompatible(holder, it) }
            .sortedBy { it.unwrapKey().map { key -> key.location().toString() }.orElse("") })
}

internal fun enchantmentBook(holder: Holder<Enchantment>) = ItemStack(Items.ENCHANTED_BOOK).also { book ->
    book.set(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.Mutable(ItemEnchantments.EMPTY).also {
        it.set(holder, holder.value().maxLevel)
    }.toImmutable())
}

internal data class EnchantmentCardLayout(val width: Int) {
    val columns = ((width - 8).coerceAtLeast(1) / 220).coerceIn(1, 4)
    val cellWidth = ((width - 8).coerceAtLeast(1) / columns).coerceAtLeast(1)
    fun contentHeight(count: Int) = ((count.coerceAtLeast(0) + columns - 1) / columns) * 78
    fun x(index: Int) = index % columns * cellWidth
    fun y(index: Int) = index / columns * 78
}

class EnchantmentCompatibilityScreen(private val parent: Screen) : Screen(catalogText("Enchantment compatibility")) {
    private var held = ItemStack.EMPTY
    private var holders = emptyList<Holder.Reference<Enchantment>>()
    private var cards = emptyList<EnchantmentCard>()
    private var query = ""
    private var failure: String? = null
    private var registryAccess: Any? = null
    private lateinit var grid: CardGrid
    private lateinit var search: SpotlightEditBox
    private fun color(token: ThemeColor) = SpotlightTheme.color(token.defaultArgb)
    override fun isPauseScreen() = false
    override fun onClose() { minecraft!!.setScreen(parent) }

    override fun init() {
        val panelWidth = minOf(920, (width - 16).coerceAtLeast(1))
        val left = (width - panelWidth) / 2
        search = addRenderableWidget(SpotlightEditBox(font, left, 56, panelWidth, 20, catalogText("Search names, IDs or status")))
        search.setMaxLength(256)
        search.setHint(catalogText("Search names, IDs, compatible, primary, conflicts…"))
        search.value = query
        grid = addRenderableWidget(CardGrid(left, 82, panelWidth, (height - 114).coerceAtLeast(1)))
        search.setResponder { query = it; filter() }
        addRenderableWidget(catalogButton("Back (Esc)", left, (height - 26).coerceAtLeast(0), minOf(110, panelWidth)) { onClose() })
        refresh(force = true)
        setInitialFocus(search)
    }

    private fun refresh(force: Boolean = false) {
        val access = minecraft!!.connection?.registryAccess()
        val stack = minecraft!!.player?.mainHandItem ?: ItemStack.EMPTY
        if (!force && access === registryAccess && ItemStack.matches(held, stack)) return
        registryAccess = access
        held = stack.copy()
        failure = null
        holders = try {
            check(minecraft!!.level != null && access != null) { "Join a world to load enchantment compatibility" }
            access.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList()
        } catch (error: Exception) {
            failure = "Registry unavailable: ${error.message ?: error.javaClass.simpleName}"
            emptyList()
        }
        cards = holders.map { enchantmentCard(it, held) }
        filter()
    }

    private fun filter() {
        grid.configure(cards.filter { it.matches(query) }.sortedBy { it.support.ordinal })
    }

    override fun tick() { refresh() }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (event.isEscape) { onClose(); return true }
        if (!primaryModifier(event) && !event.hasAltDown() && (event.isUp || event.isDown ||
                event.key() == GLFW.GLFW_KEY_PAGE_UP || event.key() == GLFW.GLFW_KEY_PAGE_DOWN)) {
            setFocused(grid)
            return grid.keyPressed(event.key(), event.scancode(), event.modifiers())
        }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers())
    }

    override fun renderBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderBackground(g, mouseX, mouseY, delta)
        g.fill(0, 0, width, height, color(ThemeColor.SCREEN_BACKGROUND))
        val left = search.x
        val available = search.width
        g.themeText(font, title, left, 8, color(ThemeColor.TEXT))
        g.fill(left, 23, left + available, 51, color(ThemeColor.PANEL_BACKGROUND))
        if (!held.isEmpty) {
            g.renderItem(held, left + 5, 29)
            g.renderItemDecorations(font, held, left + 5, 29)
        }
        g.themeText(font, catalogClip(font, if (held.isEmpty) "Hold an item" else held.hoverName.string,
            (available - 32).coerceAtLeast(0)), left + 28, 28, color(ThemeColor.TEXT))
        g.themeText(font, catalogClip(font, "Held-item rules · read only", (available - 32).coerceAtLeast(0)), left + 28, 40, color(ThemeColor.TEXT_MUTED))
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(g, mouseX, mouseY, delta)
        val left = search.x
        val available = search.width
        if (failure != null || grid.children().isEmpty()) {
            g.themeText(font, catalogClip(font, failure ?: if (cards.isEmpty()) "No loaded enchantments" else "No matching enchantments", available),
                left, 86, color(if (failure != null) ThemeColor.ERROR else ThemeColor.TEXT_MUTED))
        }
        val count = "${grid.children().size}/${holders.size}"
        g.themeText(font, count, left + available - font.width(count), height - 20, color(ThemeColor.TEXT_MUTED))
        if (!held.isEmpty && mouseX in left + 3..left + 23 && mouseY in 25..49) g.setTooltipForNextFrame(font, held, mouseX, mouseY)
    }

    private inner class CardGrid(x: Int, y: Int, width: Int, height: Int) : AbstractContainerWidget(
        x, y, width, height, catalogText("Enchantment cards; arrows and Page Up / Page Down navigate; Enter selects details")) {
        private val tiles = mutableListOf<Card>()
        private var selected: Holder<Enchantment>? = null
        private val layout get() = EnchantmentCardLayout(width)
        fun configure(values: List<EnchantmentCard>) {
            setFocused(null as GuiEventListener?)
            tiles.clear()
            tiles += values.map { Card(it) }
            setScrollAmount(0.0)
        }
        override fun children(): List<AbstractWidget> = tiles
        override fun contentHeight() = layout.contentHeight(tiles.size)
        override fun scrollRate() = 78.0
        private fun layoutCards() {
            val layout = layout
            tiles.forEachIndexed { index, tile ->
                tile.setRectangle((layout.cellWidth - 4).coerceAtLeast(1), 74, x + layout.x(index), y + layout.y(index) - scrollAmount().toInt())
            }
        }
        override fun setScrollAmount(amount: Double) { super.setScrollAmount(amount); layoutCards() }
        override fun setFocused(listener: GuiEventListener?) {
            super.setFocused(listener)
            val index = tiles.indexOf(listener)
            if (index < 0) return
            selected = tiles[index].card.holder
            val top = layout.y(index)
            if (top < scrollAmount()) setScrollAmount(top.toDouble())
            else if (top + 74 > scrollAmount() + height) setScrollAmount((top + 74 - height).toDouble())
        }
        override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
            val offset = when (event.key()) {
                GLFW.GLFW_KEY_LEFT -> -1
                GLFW.GLFW_KEY_RIGHT -> 1
                GLFW.GLFW_KEY_UP -> -layout.columns
                GLFW.GLFW_KEY_DOWN -> layout.columns
                GLFW.GLFW_KEY_PAGE_UP -> -layout.columns * (height / 78).coerceAtLeast(1)
                GLFW.GLFW_KEY_PAGE_DOWN -> layout.columns * (height / 78).coerceAtLeast(1)
                else -> return super.keyPressed(event.key(), event.scancode(), event.modifiers())
            }
            if (tiles.isEmpty()) return false
            val current = tiles.indexOf(focused)
            setFocused(tiles[if (current < 0) 0 else (current + offset).coerceIn(0, tiles.lastIndex)])
            return true
        }
        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            val event = MouseButtonEvent(mouseX, mouseY, button)
            layoutCards()
            return isMouseOver(event.x(), event.y()) && super.mouseClicked(event.x(), event.y(), event.button())
        }
        override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean =
            isMouseOver(mouseX, mouseY) && super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            layoutCards()
            g.enableScissor(x, y, right, bottom)
            val inside = isMouseOver(mouseX.toDouble(), mouseY.toDouble())
            tiles.filter { it.bottom > y && it.y < bottom }.forEach {
                it.render(g, if (inside) mouseX else -1, if (inside) mouseY else -1, delta)
            }
            g.disableScissor()
            renderScrollbar(g)
        }
        override fun updateWidgetNarration(output: NarrationElementOutput) { (focused as? AbstractWidget)?.updateNarration(output) }

        private inner class Card(val card: EnchantmentCard) : AbstractWidget(0, 0, 100, 74, catalogText(card.details)) {
            private val book = enchantmentBook(card.holder)
            init { setTooltip(Tooltip.create(message)) }
            override fun onClick(mouseX: Double, mouseY: Double) { selected = card.holder }
            override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
                if (event.key() != GLFW.GLFW_KEY_ENTER && event.key() != GLFW.GLFW_KEY_SPACE) return false
                selected = card.holder
                return true
            }
            override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
                g.fill(x, y, right, bottom, color(when {
                    isHoveredOrFocused -> ThemeColor.ROW_HOVER
                    selected == card.holder -> ThemeColor.ROW_SELECTED
                    else -> ThemeColor.BUTTON_BACKGROUND
                }))
                g.renderOutline(x, y, width, height, color(if (selected == card.holder) ThemeColor.ACCENT else ThemeColor.BORDER))
                g.renderItem(book, x + 6, y + 9)
                fun line(text: String, dx: Int, dy: Int, token: ThemeColor) {
                    g.themeText(font, catalogClip(font, text, (width - dx - 6).coerceAtLeast(0)), x + dx, y + dy, color(token))
                }
                line(card.name, 28, 7, ThemeColor.TEXT)
                line(card.levels, 28, 20, ThemeColor.TEXT_MUTED)
                val badgeWidth = minOf(font.width(card.support.label) + 8, (width - 12).coerceAtLeast(1))
                g.fill(x + 5, y + 34, x + 5 + badgeWidth, y + 49, color(ThemeColor.PANEL_BACKGROUND))
                line(card.support.label, 9, 38, card.support.color)
                line(if (card.conflicts.isEmpty()) card.id else card.conflictLabel, 6, 57,
                    if (card.conflicts.isEmpty()) ThemeColor.TEXT_MUTED else ThemeColor.ERROR)
                if (mouseX in x + 4..x + 24 && mouseY in y + 5..y + 29) {
                    g.setTooltipForNextFrame(font, book, mouseX, mouseY)
                }
            }
            override fun updateWidgetNarration(output: NarrationElementOutput) { defaultButtonNarrationText(output) }
        }
    }
}
