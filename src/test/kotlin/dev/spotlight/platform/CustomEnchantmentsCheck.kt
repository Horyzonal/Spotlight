package dev.spotlight.platform

import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.item.enchantment.ItemEnchantments

object CustomEnchantmentsCheck {
    @JvmStatic fun main(args: Array<String>) {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val registries = VanillaRegistries.createLookup()
        val registry = registries.lookupOrThrow(Registries.ENCHANTMENT)
        val sharpness = registry.getOrThrow(Enchantments.SHARPNESS)
        val smite = registry.getOrThrow(Enchantments.SMITE)
        val unbreaking = registry.getOrThrow(Enchantments.UNBREAKING)
        val nativeMax = sharpness.value().maxLevel
        check(nativeMax == 5)
        check(CustomEnchantments.registered(registries).contains(sharpness))
        for (value in listOf("1", "255", "001")) check(CustomEnchantments.parseLevel(value) == value.toInt())
        for (value in listOf("", "0", "256", "-1", "+1", " 1", "1 ", "1.0", "١", "２", "2147483648")) {
            check(CustomEnchantments.parseLevel(value) == null) { value }
        }
        val baseline = ItemStack(Items.STONE, 7)
        check(CustomEnchantments.enchantments(baseline) == ItemEnchantments.EMPTY)
        val baselineBefore = baseline.copy()
        val baselineEdited = CustomEnchantments.build(baseline, mapOf(sharpness to 255))
        check(CustomEnchantments.enchantments(baselineEdited).getLevel(sharpness) == 255)
        check(ItemStack.matches(baseline, baselineBefore))
        val source = ItemStack(Items.DIAMOND_SWORD)
        source.damageValue = 12
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Keep name"))
        source.set(DataComponents.LORE, ItemLore(listOf(Component.literal("Keep lore"))))
        source.set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().also { it.putString("keep", "NBT") }))
        source.remove(DataComponents.MAX_STACK_SIZE)
        source.set(DataComponents.ENCHANTMENTS, ItemEnchantments.Mutable(ItemEnchantments.EMPTY).also { it.set(unbreaking, 3) }.toImmutable())
        val before = source.copy()
        val changed = CustomEnchantments.build(source, mapOf(sharpness to 255, smite to 254))
        check(CustomEnchantments.enchantments(changed).getLevel(unbreaking) == 3)
        check(CustomEnchantments.enchantments(changed).getLevel(sharpness) == 255)
        check(CustomEnchantments.enchantments(changed).getLevel(smite) == 254)
        val unrelatedRestored = changed.copy().also { it.set(DataComponents.ENCHANTMENTS, source.get(DataComponents.ENCHANTMENTS)) }
        check(ItemStack.matches(unrelatedRestored, source))
        check(ItemStack.matches(source, before))
        val lowered = CustomEnchantments.build(changed, mapOf(sharpness to 1))
        check(CustomEnchantments.enchantments(lowered).getLevel(sharpness) == 1)
        check(CustomEnchantments.enchantments(lowered).getLevel(smite) == 254)
        check(CustomEnchantments.enchantments(lowered).getLevel(unbreaking) == 3)
        val unchanged = CustomEnchantments.build(changed, emptyMap())
        check(ItemStack.matches(unchanged, changed))
        check(unchanged !== changed)
        check(sharpness.value().maxLevel == nativeMax)
        val book = ItemStack(Items.ENCHANTED_BOOK)
        book.set(DataComponents.ENCHANTMENTS, CustomEnchantments.enchantments(changed))
        val bookBefore = book.copy()
        val editedBook = CustomEnchantments.build(book, mapOf(sharpness to 255))
        check(editedBook.get(DataComponents.STORED_ENCHANTMENTS)?.getLevel(sharpness) == 255)
        check(editedBook.get(DataComponents.ENCHANTMENTS) == book.get(DataComponents.ENCHANTMENTS))
        check(ItemStack.matches(book, bookBefore))
        val updatedBook = CustomEnchantments.build(editedBook, mapOf(smite to 254))
        check(updatedBook.get(DataComponents.STORED_ENCHANTMENTS)?.getLevel(sharpness) == 255)
        check(updatedBook.get(DataComponents.STORED_ENCHANTMENTS)?.getLevel(smite) == 254)
        check(updatedBook.get(DataComponents.ENCHANTMENTS) == book.get(DataComponents.ENCHANTMENTS))
        for (invalid in listOf(0, -1, 256, Int.MAX_VALUE)) {
            check(runCatching { CustomEnchantments.build(source, mapOf(sharpness to invalid)) }.isFailure)
            check(ItemStack.matches(source, before))
        }
        check(runCatching { CustomEnchantments.build(ItemStack.EMPTY, emptyMap()) }.isFailure)
        val native = ItemEnchantments.Mutable(ItemEnchantments.EMPTY)
        native.set(sharpness, 256)
        check(native.getLevel(sharpness) == 255)
        native.set(sharpness, 0)
        check(native.getLevel(sharpness) == 0)
        val mutableUpdates = mutableMapOf<Holder<Enchantment>, Int>(sharpness to 255)
        val independent = CustomEnchantments.build(baseline, mutableUpdates)
        mutableUpdates.clear()
        check(CustomEnchantments.enchantments(independent).getLevel(sharpness) == 255 && independent.count == 7)
        for (stack in listOf(baselineEdited, changed, lowered, unchanged, editedBook, updatedBook)) {
            ItemStack.validateStrict(stack).getOrThrow()
            val ops = registries.createSerializationContext(JsonOps.INSTANCE)
            val encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()
            check(ItemStack.matches(stack, ItemStack.CODEC.parse(ops, encoded).getOrThrow()))
        }
        println("Custom enchantments: ASCII 1..255, native clamp, registry holders, unsafe combinations, upserts, books, copy/count/components/NBT and codec checks passed")
    }
}
