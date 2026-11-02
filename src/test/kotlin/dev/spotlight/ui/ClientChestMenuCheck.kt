package dev.spotlight.ui

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object ClientChestMenuCheck {
    fun run(encode: (ItemStack) -> CompoundTag, decode: (CompoundTag) -> ItemStack) {
        val actor = LocalMenuActor.create()
        val outsider = LocalMenuActor.create()
        outsider.inventory.setItem(0, ItemStack(Items.DIAMOND, 17))
        var page = emptyMap<Int, CompoundTag>()
        var inventory = emptyMap<Int, CompoundTag>()
        var cursor: CompoundTag? = null
        var reject = false
        val menu = ClientChestMenu(actor, decode, encode) { p, i, c ->
            check(!reject) { "disk full" }
            page = p; inventory = i; cursor = c
        }
        val vanilla = net.minecraft.world.inventory.ChestMenu.sixRows(0, actor.inventory)
        check(menu.slots.size == vanilla.slots.size)
        menu.slots.zip(vanilla.slots).forEach { (local, native) ->
            check(local.x == native.x && local.y == native.y && local.containerSlot == native.containerSlot)
        }
        for ((width, height) in listOf(320 to 240, 426 to 240, 640 to 360, 854 to 480)) {
            val layout = ClientChestLayout(width, height)
            check(layout.left >= 0 && layout.top - 18 >= 0)
            check(layout.top + 222 <= height)
            check(layout.sidebarLeft >= layout.left + 176 + 8)
            check(layout.sidebarLeft + layout.sidebarWidth + 2 <= width)
        }
        fun piston(n: Int) = encode(ItemStack(Items.PISTON, n))
        fun click(slot: Int, button: Int = 0, input: ClickType = ClickType.PICKUP) = menu.clicked(slot, button, input, outsider)
        menu.load(mapOf(0 to piston(9)), emptyMap(), null)
        click(0, 1)
        check(menu.carried.count == 5 && menu.slots[0].item.count == 4 && decode(cursor!!).count == 5)
        click(1, 1)
        check(menu.slots[1].item.count == 1 && menu.carried.count == 4)
        click(0)
        check(menu.carried.isEmpty && menu.slots[0].item.count == 8)
        click(0, 0, ClickType.QUICK_MOVE)
        check(menu.slots[0].item.isEmpty && inventory.values.sumOf { decode(it).count } == 8)
        click(1, 0, ClickType.SWAP)
        check(actor.inventory.getItem(0).count == 1 && menu.slots[1].item.isEmpty)
        check(outsider.inventory.getItem(0).`is`(Items.DIAMOND) && outsider.inventory.getItem(0).count == 17)
        menu.load(emptyMap(), emptyMap(), piston(10))
        click(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT)
        for (slot in 0..2) click(slot, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT)
        click(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT)
        check((0..2).all { menu.slots[it].item.count == 3 } && menu.carried.count == 1)
        menu.load(emptyMap(), emptyMap(), piston(10))
        click(-999, AbstractContainerMenu.getQuickcraftMask(0, 1), ClickType.QUICK_CRAFT)
        for (slot in 0..2) click(slot, AbstractContainerMenu.getQuickcraftMask(1, 1), ClickType.QUICK_CRAFT)
        click(-999, AbstractContainerMenu.getQuickcraftMask(2, 1), ClickType.QUICK_CRAFT)
        check((0..2).all { menu.slots[it].item.count == 1 } && menu.carried.count == 7)
        val persistedCursor = cursor!!.copy()
        click(-999)
        click(0, 0, ClickType.THROW)
        menu.removed(outsider)
        check(cursor == persistedCursor && menu.carried.count == 7)
        reject = true
        check(runCatching { click(3) }.isFailure)
        check(menu.slots[3].item.isEmpty && menu.carried.count == 7 && cursor == persistedCursor)
        reject = false
        val unknown = CompoundTag().apply { putString("id", "missing_mod:item"); putInt("count", 1) }
        menu.load(mapOf(53 to unknown), emptyMap(), null)
        click(53)
        check(53 in menu.unavailable && menu.carried.isEmpty)
        menu.copySource(ItemStack(Items.PISTON, 2))
        click(0)
        check(page[53] == unknown && decode(page.getValue(0)).count == 2)
        menu.load(page, emptyMap(), unknown)
        click(0)
        check(-1 in menu.unavailable && menu.slots[0].item.count == 2)
        menu.load(mapOf(0 to piston(2)), emptyMap(), null)
        menu.readOnly = true
        click(0); click(0, 0, ClickType.QUICK_MOVE); click(0, 0, ClickType.SWAP)
        check(menu.slots[0].item.count == 2 && menu.carried.isEmpty)
        println("ClientChestMenuCheck passed: vanilla slot coordinates, 320x240+ layout bounds, native pickup/half/drag/shift/SWAP, detached inventory, drop blocking, atomic rollback, cursor recovery, opaque NBT, read-only search")
    }
}
