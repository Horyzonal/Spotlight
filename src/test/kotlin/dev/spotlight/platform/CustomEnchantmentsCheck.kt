package dev.spotlight.platform

import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments

object CustomEnchantmentsCheck {
    @JvmStatic fun main(args: Array<String>) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        fun holder(value: net.minecraft.world.item.enchantment.Enchantment) = BuiltInRegistries.ENCHANTMENT.getHolderOrThrow(BuiltInRegistries.ENCHANTMENT.getResourceKey(value).orElseThrow())
        val sharpness = holder(Enchantments.SHARPNESS)
        val smite = holder(Enchantments.SMITE)
        for (text in listOf("1", "255", "001")) check(CustomEnchantments.parseLevel(text) == text.toInt())
        for (text in listOf("", "0", "256", "-1", "+1", " 1", "1.0", "١", "２", "2147483648")) check(CustomEnchantments.parseLevel(text) == null)
        for (item in listOf(Items.STONE, Items.DIAMOND_SWORD, Items.ENCHANTED_BOOK)) {
            val source = ItemStack(item)
            source.setHoverName(net.minecraft.network.chat.Component.literal("Keep name"))
            source.getOrCreateTag().putString("opaque", "Keep NBT")
            val before = source.copy()
            val changed = CustomEnchantments.build(source, mapOf(sharpness to 255, smite to 254))
            check(CustomEnchantments.enchantments(changed)[sharpness.value()] == 255)
            check(CustomEnchantments.enchantments(changed)[smite.value()] == 254)
            check(changed.hoverName.string == "Keep name" && changed.tag!!.getString("opaque") == "Keep NBT")
            check(ItemStack.matches(source, before))
            val lowered = CustomEnchantments.build(changed, mapOf(sharpness to 1))
            check(CustomEnchantments.enchantments(lowered)[sharpness.value()] == 1)
            check(CustomEnchantments.enchantments(lowered)[smite.value()] == 254)
            check(ItemStack.matches(lowered, ItemStack.of(lowered.save(CompoundTag()))))
            for (invalid in listOf(0, -1, 256, Int.MAX_VALUE)) check(runCatching { CustomEnchantments.build(source, mapOf(sharpness to invalid)) }.isFailure)
        }
        check(sharpness.value().maxLevel == 5)
        check(runCatching { CustomEnchantments.build(ItemStack.EMPTY, emptyMap()) }.isFailure)
        println("CustomEnchantmentsCheck passed: 1..255, native NBT, books, unsafe combinations, replacement, copy isolation and validation")
    }
}
