package dev.spotlight.ui

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack


internal class VaultInteraction(
    items: Map<Int, CompoundTag>, cursor: CompoundTag?,
    private val decode: (CompoundTag) -> ItemStack,
) {
    val items = items.mapValues { it.value.copy() }.toMutableMap()
    var cursor: CompoundTag? = cursor?.copy()
        private set
    var source: Int? = null
    private fun stack(raw: CompoundTag) = decode(raw).also { require(!it.isEmpty && it.count in 1..it.maxStackSize) { "Invalid stack count; raw preserved" } }
    private fun count(raw: CompoundTag, count: Int) = raw.copy().apply { putByte("Count", count.toByte()) }
    private fun same(a: CompoundTag, b: CompoundTag) = ItemStack.isSameItemSameTags(stack(a), stack(b))
    fun copy(raw: CompoundTag, half: Boolean = false) {
        check(cursor == null) { "Place current local cursor first" }
        val n = stack(raw).count
        cursor = count(raw, if (half) (n + 1) / 2 else n)
    }
    fun click(slot: Int, right: Boolean) {
        require(slot in 0..53)
        val target = items[slot]
        val held = cursor
        if (held == null) {
            if (target == null) return
            val n = stack(target).count
            val take = if (right) (n + 1) / 2 else n
            cursor = count(target, take)
            source = slot
            if (take == n) items.remove(slot) else items[slot] = count(target, n - take)
        } else {
            val n = stack(held).count
            if (target == null || same(held, target)) {
                val old = target?.let { stack(it).count } ?: 0
                val put = minOf(if (right) 1 else n, stack(held).maxStackSize - old)
                if (put <= 0) return
                items[slot] = count(target ?: held, old + put)
                cursor = if (put == n) null else count(held, n - put)
            } else if (!right) {
                stack(target)
                items[slot] = held.copy()
                cursor = target.copy()
                source = slot
            }
        }
    }
    fun spread(slots: Collection<Int>, right: Boolean) {
        val held = cursor ?: return
        val n = stack(held).count
        val targets = slots.distinct().onEach { require(it in 0..53) }.filter { slot ->
            val target = items[slot]
            target == null || (same(held, target) && stack(target).count < stack(target).maxStackSize)
        }
        if (targets.isEmpty()) return
        val each = if (right) 1 else n / targets.size
        var remaining = n
        targets.forEach { slot ->
            val target = items[slot]
            val old = target?.let { stack(it).count } ?: 0
            val put = minOf(each, remaining, stack(held).maxStackSize - old)
            if (put > 0) items[slot] = count(target ?: held, old + put)
            remaining -= put
        }
        cursor = if (remaining == 0) null else count(held, remaining)
    }
    fun insertCopy(raw: CompoundTag) {
        val savedCursor = cursor
        cursor = null
        copy(raw)
        val held = requireNotNull(cursor)
        val matching = items.keys.sorted().filter { runCatching { same(held, items.getValue(it)) }.getOrDefault(false) }
        (matching + (0..53).filter { it !in items }).forEach { if (cursor != null) click(it, false) }
        check(cursor == null) { "Page full; copy not saved" }
        cursor = savedCursor
    }
    fun cancel(preferred: Int?) {
        if (cursor == null) return
        val held = requireNotNull(cursor)
        val candidates = listOfNotNull(preferred) + (0..53).filter { it != preferred }
        candidates.forEach { slot ->
            if (cursor != null && (items[slot] == null || runCatching { same(held, items.getValue(slot)) }.getOrDefault(false))) click(slot, false)
        }
    }
}
