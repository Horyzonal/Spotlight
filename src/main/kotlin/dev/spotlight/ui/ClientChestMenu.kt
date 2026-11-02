package dev.spotlight.ui

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack


class ClientChestMenu internal constructor(
    val actor: Player,
    private val decode: (CompoundTag) -> ItemStack,
    private val encode: (ItemStack) -> CompoundTag,
    internal var persist: (Map<Int, CompoundTag>, Map<Int, CompoundTag>, CompoundTag?) -> Unit,
) : AbstractContainerMenu(null, 0) {
    private val chest = SimpleContainer(54)
    private var pageRaw = emptyMap<Int, CompoundTag>()
    private var inventoryRaw = emptyMap<Int, CompoundTag>()
    private var cursorRaw: CompoundTag? = null
    val unavailable = mutableSetOf<Int>()
    var readOnly = false
    init {
        repeat(54) { index -> addSlot(localSlot(chest, index, 8 + index % 9 * 18, 18 + index / 9 * 18, index)) }
        ((9..35).toList() + (0..8).toList()).forEachIndexed { index, slot ->
            addSlot(localSlot(actor.inventory, slot, 8 + index % 9 * 18, 139 + index / 9 * 18 + if (index >= 27) 4 else 0, 54 + index))
        }
    }
    private fun localSlot(container: net.minecraft.world.Container, slot: Int, x: Int, y: Int, index: Int) = object : Slot(container, slot, x, y) {
        override fun mayPlace(stack: ItemStack) = !readOnly && index !in unavailable
        override fun mayPickup(player: Player) = !readOnly && index !in unavailable
    }
    fun load(page: Map<Int, CompoundTag>, inventory: Map<Int, CompoundTag>, cursor: CompoundTag?) {
        resetQuickCraft()
        pageRaw = page.mapValues { it.value.copy() }
        inventoryRaw = inventory.mapValues { it.value.copy() }
        cursorRaw = cursor?.copy()
        unavailable.clear()
        slots.forEachIndexed { index, slot ->
            val raw = if (index < 54) page[index] else inventory[slot.containerSlot]
            slot.set(raw?.let { runCatching { decode(it) }.getOrElse { unavailable += index; ItemStack.EMPTY } } ?: ItemStack.EMPTY)
        }
        carried = cursor?.let { runCatching { decode(it) }.getOrElse { unavailable += -1; ItemStack.EMPTY } } ?: ItemStack.EMPTY
    }
    private fun rawSlots(from: Int, to: Int, original: Map<Int, CompoundTag>): Map<Int, CompoundTag> = buildMap {
        for (index in from until to) {
            val slot = slots[index]
            val key = slot.containerSlot
            if (index in unavailable) original[key]?.let { put(key, it.copy()) }
            else if (!slot.item.isEmpty) put(key, encode(slot.item))
        }
    }
    private fun commit() {
        val page = rawSlots(0, 54, pageRaw)
        val inventory = rawSlots(54, 90, inventoryRaw)
        val cursor = if (-1 in unavailable) cursorRaw else carried.takeUnless { it.isEmpty }?.let(encode)
        if (page == pageRaw && inventory == inventoryRaw && cursor == cursorRaw) return
        try { persist(page, inventory, cursor) }
        catch (e: Exception) { load(pageRaw, inventoryRaw, cursorRaw); throw e }
        pageRaw = page; inventoryRaw = inventory; cursorRaw = cursor
    }
    override fun clicked(slotId: Int, button: Int, input: ContainerInput, player: Player) {
        if (readOnly || -1 in unavailable || slotId in unavailable || input == ContainerInput.THROW || input == ContainerInput.CLONE) return
        if (input == ContainerInput.SWAP && (button !in 0..8 || 81 + button in unavailable)) return
        if (slotId !in slots.indices && input != ContainerInput.QUICK_CRAFT) return
        if (input == ContainerInput.QUICK_CRAFT && getQuickcraftType(button) !in 0..1) return
        try { super.clicked(slotId, button, input, actor); commit() }
        catch (e: Exception) { load(pageRaw, inventoryRaw, cursorRaw); throw e }
    }
    fun copySource(stack: ItemStack) {
        check(!readOnly && -1 !in unavailable && carried.isEmpty) { "Clear local cursor and search first" }
        require(!stack.isEmpty) { "Hold an item in real inventory first" }
        carried = stack.copy()
        commit()
    }
    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots[index]
        if (!slot.mayPickup(actor) || !slot.hasItem()) return ItemStack.EMPTY
        val stack = slot.item
        val original = stack.copy()
        if (!moveItemStackTo(stack, if (index < 54) 54 else 0, if (index < 54) 90 else 54, index < 54)) return ItemStack.EMPTY
        if (stack.isEmpty) slot.set(ItemStack.EMPTY) else slot.setChanged()
        return original
    }
    override fun canDragTo(slot: Slot) = slot.mayPlace(carried)
    override fun canTakeItemForPickAll(stack: ItemStack, slot: Slot) = slot.mayPickup(actor)
    override fun stillValid(player: Player) = true
    override fun removed(player: Player) {}
}
