package dev.spotlight.ui

import net.minecraft.SharedConstants
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.server.Bootstrap
import net.minecraft.tags.EnchantmentTags

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.item.enchantment.ItemEnchantments

object EnchantmentCompatibilityCheck {
    @JvmStatic fun main(args: Array<String>) {
        check(enchantmentSupport(true, true, true) == EnchantmentSupport.EMPTY)
        check(enchantmentSupport(false, false, true) == EnchantmentSupport.UNSUPPORTED)
        check(enchantmentSupport(false, true, false) == EnchantmentSupport.COMPATIBLE)
        check(enchantmentSupport(false, true, true) == EnchantmentSupport.PRIMARY)
        check(EnchantmentSupport.entries.map { it.label }.toSet().size == 4)
        for (width in listOf(1, 160, 320, 440, 448, 640, 920, 1600)) {
            val layout = EnchantmentCardLayout(width)
            check(layout.columns in 1..4)
            if (width <= 440) check(layout.columns == 1)
            for (count in listOf(0, 1, 3, 17, 43)) {
                for (index in 0 until count) {
                    check(layout.x(index) >= 0)
                    check(layout.x(index) + layout.cellWidth <= width)
                    check(layout.y(index) + 74 <= layout.contentHeight(count))
                    if (index >= layout.columns) check(layout.y(index) == layout.y(index - layout.columns) + 78)
                }
                if (count == 0) check(layout.contentHeight(count) == 0)
            }
        }
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val registries = VanillaRegistries.createWorldLookup()
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach { it.apply() }
        val registry = registries.lookupOrThrow(Registries.ENCHANTMENT)
        val sharpness = registry.getOrThrow(Enchantments.SHARPNESS)
        val smite = registry.getOrThrow(Enchantments.SMITE)
        val unbreaking = registry.getOrThrow(Enchantments.UNBREAKING)
        // World lookup omits datapack tags. Bind only conflict fixture tags; runtime uses server-bound holders.
        val bindTags = Holder.Reference::class.java.getDeclaredMethod("bindTags", Collection::class.java).also { it.isAccessible = true }
        registry.listElements().forEach { holder ->
            bindTags.invoke(holder, if (holder == sharpness || holder == smite) listOf(EnchantmentTags.DAMAGE_EXCLUSIVE) else emptyList<Any>())
        }
        val sword = ItemStack(Items.DIAMOND_SWORD)
        sword.set(DataComponents.ENCHANTMENTS, ItemEnchantments.Mutable(ItemEnchantments.EMPTY).also {
            it.set(smite, 3)
            it.set(unbreaking, 2)
        }.toImmutable())
        val before = sword.copy()
        val card = enchantmentCard(sharpness, sword)
        check(card.support == enchantmentSupport(false, sharpness.value().isSupportedItem(sword), sharpness.value().isPrimaryItem(sword)))
        check(card.conflicts == listOf(smite))
        check(!Enchantment.areCompatible(sharpness, smite))
        check(Enchantment.areCompatible(sharpness, unbreaking))
        check(card.matches("minecraft:sharpness"))
        check(card.copy(support = EnchantmentSupport.PRIMARY).matches("compatible"))
        check(card.copy(support = EnchantmentSupport.PRIMARY).matches("PRIMARY"))
        check(card.matches("conflicts"))
        check(!card.copy(support = EnchantmentSupport.PRIMARY).matches("not supported"))
        check(card.details.contains("Weight ${sharpness.value().weight}"))
        check(card.details.contains("Levels 1–5"))
        check(enchantmentCard(smite, sword).conflicts.isEmpty()) // Existing enchantment is not its own conflict.
        check(enchantmentCard(sharpness, ItemStack.EMPTY).support == EnchantmentSupport.EMPTY)
        check(enchantmentCard(sharpness, ItemStack(Items.STONE)).support == EnchantmentSupport.UNSUPPORTED)
        check(enchantmentCard(sharpness, ItemStack(Items.STONE)).matches("not supported"))
        val book = enchantmentBook(sharpness)
        check(book.`is`(Items.ENCHANTED_BOOK))
        check(book.get(DataComponents.STORED_ENCHANTMENTS)?.getLevel(sharpness) == sharpness.value().maxLevel)
        check(book.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty)
        check(enchantmentCard(smite, book).conflicts == listOf(sharpness))
        check(ItemStack.matches(sword, before))
        registry.listElements().forEach { holder ->
            val value = holder.value()
            for (stack in listOf(sword, ItemStack(Items.DIAMOND_AXE), ItemStack(Items.BOW), ItemStack.EMPTY)) {
                check(enchantmentCard(holder, stack).support == enchantmentSupport(stack.isEmpty,
                    value.isSupportedItem(stack), value.isPrimaryItem(stack)))
            }
        }
        println("Enchantment compatibility: native support/conflicts, search, book previews, read-only stacks and responsive card layout passed")
    }
}
