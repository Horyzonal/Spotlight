package dev.spotlight.platform

import com.mojang.serialization.JsonOps
import net.minecraft.client.Minecraft
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.ItemEnchantments
import net.minecraft.world.level.GameType

object CustomEnchantments {

    fun parseLevel(text: String): Int? = text.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }
        ?.toIntOrNull()?.takeIf { it in 1..255 }

    fun registered(registries: HolderLookup.Provider): List<Holder.Reference<Enchantment>> =
        registries.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList()
            .sortedBy { it.key().identifier().toString() }

    fun component(stack: ItemStack) =
        if (stack.`is`(Items.ENCHANTED_BOOK)) DataComponents.STORED_ENCHANTMENTS else DataComponents.ENCHANTMENTS

    fun enchantments(stack: ItemStack): ItemEnchantments = stack.getOrDefault(component(stack), ItemEnchantments.EMPTY)

    fun build(source: ItemStack, updates: Map<Holder<Enchantment>, Int>): ItemStack {
        require(!source.isEmpty) { "Hold an item" }
        require(updates.values.all { it in 1..255 }) { "Level must be an ASCII integer from 1 to 255" }
        val mutable = ItemEnchantments.Mutable(enchantments(source))

        updates.forEach { (holder, level) -> mutable.set(holder, level) }
        return source.copy().also { it.set(component(source), mutable.toImmutable()) }
    }

    fun apply(holder: Holder<Enchantment>, enchantmentLevel: Int): String {
        val client = Minecraft.getInstance()
        val player = requireNotNull(client.player) { "Join a world first" }
        val level = requireNotNull(client.level) { "Join a world first" }
        val connection = requireNotNull(client.connection) { "Server connection unavailable" }
        val gameMode = requireNotNull(client.gameMode) { "Game mode unavailable" }
        val inventory = player.inventory
        val slot = inventory.selectedSlot
        check(client.isSameThread) { "Enchantment editing requires the client thread" }
        val original = player.mainHandItem.copy()
        require(!original.isEmpty) { "Hold an item" }

        fun unavailableReason(): String? = when {
            client.player !== player || client.level !== level || client.connection !== connection ||
                client.gameMode !== gameMode || player.inventory !== inventory || !connection.connection.isConnected ->
                "World or connection changed"
            gameMode.playerMode != GameType.CREATIVE || !player.abilities.instabuild -> "Creative mode required"
            player.containerMenu !== player.inventoryMenu -> "Close the open container first"
            slot !in 0..8 || inventory.selectedSlot != slot || !ItemStack.matches(player.mainHandItem, original) ->
                "Held item changed; try again"
            else -> null
        }

        check(unavailableReason() == null) { unavailableReason().orEmpty() }
        require(holder in registered(connection.registryAccess())) { "Enchantment registry changed; reopen editor" }
        val changed = build(original, mapOf(holder to enchantmentLevel))
        ItemStack.validateStrict(changed).getOrThrow()
        require(changed.count in 1..changed.maxStackSize && changed.isItemEnabled(level.enabledFeatures())) {
            "Held item is not valid for this server"
        }
        ItemStack.CODEC.encodeStart(connection.registryAccess().createSerializationContext(JsonOps.INSTANCE), changed).getOrThrow()
        check(unavailableReason() == null) { unavailableReason().orEmpty() }
        gameMode.handleCreativeModeItemAdd(changed.copy(), slot + 36)
        inventory.setItem(slot, changed)
        inventory.setChanged()
        return changed.hoverName.string
    }
}
