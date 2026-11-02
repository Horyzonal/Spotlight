package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.platform.ClientItemStorage
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

internal fun searchVaultItems(query: String, items: List<Pair<String, CompoundTag>>, name: (CompoundTag) -> String): List<Pair<String, CompoundTag>> {
    if (query.isBlank()) return items
    val indexed = items.toMap()
    val entries = items.map { (id, raw) -> dev.spotlight.core.Entry(id, name(raw), raw.toString()) }
    return dev.spotlight.core.Fuzzy.search(query, entries, limit = entries.size).map { it.id to indexed.getValue(it.id) }
}


abstract class VaultGridScreen(parent: Screen?, title: String) : StorageScreen(parent, title) {
    protected open val transactionPage: Int get() = 1
    protected open val archiveSource: Boolean get() = false
    protected open val copySource: Boolean get() = archiveSource
    protected var searchQuery = ""
    protected fun searchBox(changed: () -> Unit) {
        val search = addRenderableWidget(SpotlightEditBox(font, left, cursorY, 358, 20, catalogText("Search saved items")))
        search.setMaxLength(200)
        search.value = searchQuery
        search.setResponder { value ->
            val cursor = search.cursorPosition
            searchQuery = value
            changed()
            rebuild()
            children().filterIsInstance<SpotlightEditBox>().firstOrNull()?.let {
                setFocused(it)
                it.cursorPosition = cursor.coerceAtMost(value.length)
            }
        }
        cursorY += 24
    }
    protected fun searchItems(items: List<Pair<String, CompoundTag>>) = searchVaultItems(searchQuery, items) {
        runCatching { ClientItemStorage.decode(it).hoverName.string }.getOrDefault("")
    }
    private data class Cell(val widget: CopySlot, val slot: Int, val player: Boolean, val raw: CompoundTag?)
    private val cells = mutableListOf<Cell>()
    private var pressed: Cell? = null
    private var button = -1
    private var placing = false
    private var transferring = false
    private val spread = linkedSetOf<Int>()
    private var pointerX = 0
    private var pointerY = 0
    private var inventorySnapshot = emptyList<ItemStack>()
    override fun tick() {
        super.tick()
        val inventory = minecraft!!.player?.inventory ?: return
        if (pressed == null && (inventorySnapshot.size != 36 || inventorySnapshot.indices.any { !ItemStack.matches(inventorySnapshot[it], inventory.getItem(it)) })) rebuild()
    }
    protected fun chestGrid(stacks: Map<Int, CompoundTag>) {
        cells.clear()
        inventorySnapshot = (0 until 36).map { minecraft!!.player?.inventory?.getItem(it)?.copy() ?: ItemStack.EMPTY }
        ClientItemStorage.cursor()?.let { raw ->
            runCatching { ClientItemStorage.decode(raw) }.onFailure { notice = "Saved cursor unavailable: ${it.message}. Raw preserved; return in compatible world." }
        }
        repeat(54) { slot -> addCell(slot, false, stacks[slot], left + slot % 9 * 20, cursorY + slot / 9 * 20) }
        cursorY += 124
        text("Player inventory · COPY sources · Creative obtain only")
        val top = cursorY
        ((9..35).toList() + (0..8).toList()).forEachIndexed { index, slot ->
            val stack = inventorySnapshot[slot]
            val raw = stack.takeUnless { it.isEmpty }?.let(ClientItemStorage::encode)
            addCell(slot, true, raw, left + index % 9 * 20, top + index / 9 * 20 + if (index >= 27) 4 else 0)
        }
        cursorY += 86
    }
    private fun addCell(slot: Int, player: Boolean, raw: CompoundTag?, x: Int, y: Int) {
        var label = if (player) "Copy player slot ${slot + 1}" else "Local slot ${slot + 1}"
        val stack = raw?.let { try { ClientItemStorage.decode(it) } catch (e: Exception) { label = e.message ?: "Unknown item; raw preserved"; ItemStack.EMPTY } } ?: ItemStack.EMPTY
        lateinit var cell: Cell
        val widget = CopySlot(x, y, stack, label) { attempt { click(cell, false, false) } }
        cell = Cell(widget, slot, player, raw)
        cells += cell
        addRenderableWidget(widget)
    }
    private fun cell(x: Double, y: Double) = if (contentContains(x, y)) cells.firstOrNull { it.widget.isMouseOver(x, y) } else null
    private fun edit(action: (VaultInteraction) -> Unit) {
        val draft = VaultInteraction(ClientItemStorage.items(ClientItemStorage.page(transactionPage)), ClientItemStorage.cursor(), ClientItemStorage::decode)
        val oldSource = ClientItemStorage.cursorSource()
        action(draft)
        ClientItemStorage.atomicReplacePageItems(transactionPage, draft.items, draft.cursor, draft.source?.let { transactionPage to it } ?: oldSource)
        rebuild()
    }
    private fun click(cell: Cell, right: Boolean, shift: Boolean) {
        val rawSource = if (cell.player) minecraft!!.player?.inventory?.getItem(cell.slot)?.takeUnless { it.isEmpty }?.copy()?.let(ClientItemStorage::encode) else cell.raw
        if (shift) {
            val raw = rawSource ?: return
            if (cell.player) {
                check(!copySource) { "Archive originals read-only; copy into Client chest" }
                edit { it.insertCopy(raw) }
                notice = "Inventory copied into local page; real items unchanged"
            } else {
                ClientItemStorage.give(listOf(raw))
                notice = "Creative copy requested; saved original unchanged"
            }
        } else if (cell.player) {
            val held = ClientItemStorage.cursor()
            if (held == null) rawSource?.let { raw -> edit { it.copy(raw, right) } }
            else {
                check(rawSource == null) { "Occupied inventory slot; overwrite blocked" }
                ClientItemStorage.giveToEmptySlot(held, cell.slot)
                notice = "Creative copy requested; local cursor retained"
            }
        } else if (copySource) {
            cell.raw?.let { raw -> edit { it.copy(raw, right) } }
        } else edit { it.click(cell.slot, right) }
    }
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            val event = MouseButtonEvent(mouseX, mouseY, button)
        val hit = cell(event.x(), event.y()) ?: return super.mouseClicked(event.x(), event.y(), event.button())
        if (event.button() !in 0..1) return true
        pressed = hit
        this.button = event.button()
        spread.clear()
        transferring = ClientItemStorage.cursor() == null && !event.hasShiftDown()
        placing = ClientItemStorage.cursor() != null && !hit.player && !copySource && !event.hasShiftDown()
        if (placing) spread += hit.slot
        else attempt { click(hit, button == 1, event.hasShiftDown()) }
        return true
    }
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dx: Double, dy: Double): Boolean {
        val event = MouseButtonEvent(mouseX, mouseY, button)
        if (pressed == null) return super.mouseDragged(event.x(), event.y(), event.button(), dx, dy)
        if (placing) cell(event.x(), event.y())?.takeIf { !it.player }?.let { spread += it.slot }
        return true
    }
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val event = MouseButtonEvent(mouseX, mouseY, button)
        val start = pressed ?: return super.mouseReleased(event.x(), event.y(), event.button())
        val wasPlacing = placing
        val right = button == 1
        val wasTransferring = transferring
        pressed = null; placing = false; transferring = false; this.button = -1
        attempt {
            val hit = cell(event.x(), event.y())
            if (wasPlacing) {
                if (spread.size > 1) edit { it.spread(spread, right) }
                else if (hit != null) click(hit, right, false)
            } else if (wasTransferring && hit != null && (hit.slot != start.slot || hit.player != start.player) && ClientItemStorage.cursor() != null) {
                click(hit, right, false)
            }
        }
        spread.clear()
        return true
    }
    override fun mouseMoved(x: Double, y: Double) { pointerX = x.toInt(); pointerY = y.toInt(); super.mouseMoved(x, y) }
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (focused is net.minecraft.client.gui.components.EditBox) return super.keyPressed(event.key(), event.scancode(), event.modifiers())
        if (event.key() in InputConstants.KEY_1..InputConstants.KEY_9) {
            val hit = cell(pointerX.toDouble(), pointerY.toDouble()) ?: cells.firstOrNull { it.widget.isFocused }
            if (hit != null && !hit.player) attempt {
                val raw = hit.raw ?: return@attempt
                ClientItemStorage.giveToEmptySlot(raw, event.key() - InputConstants.KEY_1)
                notice = "Hotbar copy requested; occupied slots blocked; no swap"
            }
            return hit != null
        }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers())
    }
    override fun onClose() {
        if (!archiveSource) attempt {
            val source = ClientItemStorage.cursorSource()
            edit { it.cancel(source?.takeIf { pair -> pair.first == transactionPage }?.second) }
        }
        super.onClose()
    }
    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(g, mouseX, mouseY, delta)
        val raw = runCatching { ClientItemStorage.cursor() }.getOrNull() ?: return
        val stack = runCatching { ClientItemStorage.decode(raw) }.getOrNull() ?: return
        g.renderFakeItem(stack, mouseX - 8, mouseY - 8)
        g.renderItemDecorations(font, stack, mouseX - 8, mouseY - 8)
    }
}
