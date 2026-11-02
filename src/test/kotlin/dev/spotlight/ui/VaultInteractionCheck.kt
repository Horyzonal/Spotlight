package dev.spotlight.ui

import dev.spotlight.core.storage.ItemVault
import dev.spotlight.platform.ClientItemStorage
import net.minecraft.nbt.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.nio.file.Files

object VaultInteractionCheck {
    fun run(encode: (ItemStack) -> CompoundTag, decode: (CompoundTag) -> ItemStack) {
        fun piston(n: Int) = encode(ItemStack(Items.PISTON, n))
        fun draft(items: Map<Int, CompoundTag> = emptyMap(), cursor: CompoundTag? = null) = VaultInteraction(items, cursor, decode)
        val pick = draft(mapOf(0 to piston(9)))
        pick.click(0, true)
        check(pick.cursor!!.getInt("count") == 5 && decode(pick.items.getValue(0)).count == 4)
        pick.click(1, true)
        check(decode(pick.items.getValue(1)).count == 1 && decode(pick.cursor!!).count == 4)
        pick.click(0, false)
        check(pick.cursor == null && decode(pick.items.getValue(0)).count == 8)
        pick.click(0, false)
        check(0 !in pick.items && decode(pick.cursor!!).count == 8)
        pick.cancel(0)
        check(pick.cursor == null && decode(pick.items.getValue(0)).count == 8)
        val merge = draft(mapOf(0 to piston(63)), piston(5))
        merge.click(0, false)
        check(decode(merge.items.getValue(0)).count == 64 && decode(merge.cursor!!).count == 4)
        val drag = draft(cursor = piston(10))
        drag.spread(listOf(0, 1, 2, 2), false)
        check(drag.items.values.all { decode(it).count == 3 } && decode(drag.cursor!!).count == 1)
        drag.spread(listOf(3, 4), true)
        check(decode(drag.items.getValue(3)).count == 1 && 4 !in drag.items && drag.cursor == null)
        val rightDrag = draft(cursor = piston(10))
        rightDrag.spread(listOf(0, 1, 2), true)
        check(rightDrag.items.values.all { decode(it).count == 1 } && decode(rightDrag.cursor!!).count == 7)
        val component = ItemStack(Items.PISTON, 3).apply { set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Named")) }
        val different = draft(mapOf(0 to encode(component)), piston(2))
        different.click(0, true)
        check(decode(different.items.getValue(0)).hoverName.string == "Named" && decode(different.cursor!!).count == 2)
        different.click(0, false)
        check(decode(different.cursor!!).hoverName.string == "Named" && decode(different.items.getValue(0)).count == 2)
        val unknown = CompoundTag().apply { putString("id", "missing_mod:item"); putInt("count", 1) }
        val opaque = draft(mapOf(53 to unknown))
        check(runCatching { opaque.click(53, false) }.isFailure)
        check(opaque.items.getValue(53) == unknown && opaque.cursor == null)
        opaque.copy(piston(2)); opaque.click(0, false)
        check(opaque.items.getValue(53) == unknown)
        val shifted = draft(mapOf(0 to piston(63), 53 to unknown))
        val source = piston(4)
        shifted.insertCopy(source)
        check(decode(shifted.items.getValue(0)).count == 64 && decode(shifted.items.getValue(1)).count == 3)
        check(source == piston(4) && shifted.items.getValue(53) == unknown)
        val full = (0..53).associateWith { piston(64) }
        val stranded = draft(full, piston(7))
        stranded.cancel(0)
        check(stranded.items == full && decode(stranded.cursor!!).count == 7)
        check(runCatching { draft(full).insertCopy(piston(1)) }.isFailure)
        check(runCatching { ClientItemStorage.validatePageReplacement(mapOf(53 to unknown), emptyMap(), decode) }.isFailure)
        ClientItemStorage.validatePageReplacement(mapOf(53 to unknown), mapOf(53 to unknown, 0 to piston(1)), decode)
        check(runCatching { ClientItemStorage.validatePageReplacement(emptyMap(), mapOf(54 to piston(1)), decode) }.isFailure)
        check(runCatching { ClientItemStorage.validatePageReplacement(emptyMap(), mapOf(0 to piston(65)), decode) }.isFailure)
        val root = CompoundTag().apply { put("Archive", CompoundTag().apply { put("legacy", piston(1)) }) }
        ClientItemStorage.appendArchive(root, linkedMapOf("a" to piston(2), "b" to piston(3)))
        check(ClientItemStorage.orderedArchive(root).map { it.first } == listOf("b", "a", "legacy"))
        val unchanged = root.copy()
        ClientItemStorage.appendArchive(root, mapOf("a" to piston(2)))
        check(root == unchanged)
        ClientItemStorage.appendArchive(root, mapOf("c" to piston(4)))
        check(ClientItemStorage.orderedArchive(root).first().first == "c")
        val dir = Files.createTempDirectory("vault-transaction-check")
        try {
            val vault = ItemVault(dir)
            vault.mutatePage(Int.MAX_VALUE) { page, state ->
                page.put("Items", ListTag().apply { drag.items.forEach { (slot, raw) -> add(raw.copy().apply { putByte("Slot", slot.toByte()) }) } })
                state.put("Cursor", piston(7))
                state.put("CursorSource", CompoundTag().apply { putInt("Page", Int.MAX_VALUE); putInt("Slot", 0) })
                ClientItemStorage.appendArchive(state, mapOf("first" to piston(1)))
            }
            check(vault.revision() == 1L && vault.snapshot().getCompound("Pages").size() == 1)
            check(ItemVault(dir).snapshot().getCompound("Cursor") == piston(7))
            vault.mutatePage(Int.MAX_VALUE) { _, _ -> }
            check(vault.revision() == 1L)
            val before = vault.snapshot()
            check(runCatching { vault.mutatePage(1) { _, state -> state.remove("Cursor"); error("reject") } }.isFailure)
            check(vault.snapshot() == before && ItemVault(dir).snapshot() == before)
            val cursorRevision = vault.history().single()
            check(runCatching { vault.restoreRevision(cursorRevision) }.isFailure)
            vault.mutate { state ->
                state.remove("Cursor"); state.remove("CursorSource")
                ClientItemStorage.appendArchive(state, mapOf("second" to piston(2)))
            }
            vault.restoreRevision(cursorRevision)
            val restored = ItemVault(dir).snapshot()
            check(restored.getCompound("Cursor") == piston(7))
            check(restored.getCompound("CursorSource").getInt("Page") == Int.MAX_VALUE)
            check(ClientItemStorage.orderedArchive(restored).map { it.first } == listOf("first"))
            check(restored.getLong("ArchiveSequence") == 1L)
        } finally { Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
        println("VaultInteractionCheck passed: piston pickup/place/split/merge/swap, components, left/right drag, shift copy, full-page cursor recovery, atomic persistence, opaque raw, invalid unchanged, newest unique order")
    }
}
