package dev.spotlight.ui

import net.minecraft.SharedConstants
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.server.Bootstrap

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments

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
        val registries = VanillaRegistries.createLookup()
        val registry = registries.lookupOrThrow(Registries.ENCHANTMENT)
        val sharpness = registry.getOrThrow(BuiltInRegistries.ENCHANTMENT.getResourceKey(Enchantments.SHARPNESS).orElseThrow())
        val smite = registry.getOrThrow(BuiltInRegistries.ENCHANTMENT.getResourceKey(Enchantments.SMITE).orElseThrow())
        val unbreaking = registry.getOrThrow(BuiltInRegistries.ENCHANTMENT.getResourceKey(Enchantments.UNBREAKING).orElseThrow())
        val sword = ItemStack(Items.DIAMOND_SWORD)
        net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(mapOf(smite.value() to 3, unbreaking.value() to 2), sword)
        val before = sword.copy()
        val card = enchantmentCard(sharpness, sword)
        check(card.support == enchantmentSupport(false, sharpness.value().canEnchant(sword), sharpness.value().category.canEnchant(sword.item)))
        check(card.conflicts == listOf(smite))
        check(!sharpness.value().isCompatibleWith(smite.value()))
        check(sharpness.value().isCompatibleWith(unbreaking.value()))
        check(card.matches("minecraft:sharpness"))
        check(card.copy(support = EnchantmentSupport.PRIMARY).matches("compatible"))
        check(card.copy(support = EnchantmentSupport.PRIMARY).matches("PRIMARY"))
        check(card.matches("conflicts"))
        check(!card.copy(support = EnchantmentSupport.PRIMARY).matches("not supported"))
        check(card.details.contains("Weight ${sharpness.value().rarity.weight}"))
        check(card.details.contains("Levels 1–5"))
        check(enchantmentCard(smite, sword).conflicts.isEmpty())
        check(enchantmentCard(sharpness, ItemStack.EMPTY).support == EnchantmentSupport.EMPTY)
        check(enchantmentCard(sharpness, ItemStack(Items.STONE)).support == EnchantmentSupport.UNSUPPORTED)
        check(enchantmentCard(sharpness, ItemStack(Items.STONE)).matches("not supported"))
        val book = enchantmentBook(sharpness)
        check(book.`is`(Items.ENCHANTED_BOOK))
        check(net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(book)[sharpness.value()] == sharpness.value().maxLevel)
        check(book.enchantmentTags.isEmpty())
        check(enchantmentCard(smite, book).conflicts == listOf(sharpness))
        check(ItemStack.matches(sword, before))
        registry.listElements().forEach { holder ->
            val value = holder.value()
            for (stack in listOf(sword, ItemStack(Items.DIAMOND_AXE), ItemStack(Items.BOW), ItemStack.EMPTY)) {
                check(enchantmentCard(holder, stack).support == enchantmentSupport(stack.isEmpty,
                    value.canEnchant(stack), value.category.canEnchant(stack.item)))
            }
        }
        println("Enchantment compatibility: native support/conflicts, search, book previews, read-only stacks and responsive card layout passed")
    }
}
