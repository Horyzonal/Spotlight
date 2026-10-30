package dev.spotlight.platform

import com.google.gson.JsonParser
import dev.spotlight.commands.creativePlan
import dev.spotlight.core.ColoredRun
import dev.spotlight.core.StyledText
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BannerItem
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object CreativeBannerCheck {
    @JvmStatic fun main(args: Array<String>) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        CreativeTextNbtCheck.checkAll()
        checkAll()
        println("Legacy text/banner NBT, JSON export, all dyes, layer limits and inventory safety checks passed")
    }

    fun checkAll() {
        val pattern = BuiltInRegistries.BANNER_PATTERN.holders().findFirst().orElseThrow()
        val layers = listOf(BannerLayer(pattern, DyeColor.BLUE))
        val source = CreativeBanner.createBanner(DyeColor.WHITE, emptyList()).copyWithCount(3)
        CreativeText.setName(source, Component.literal("Keep name"))
        CreativeText.setLore(source, listOf(Component.literal("Keep lore")))
        source.orCreateTag.putString("mod:custom", "keep")
        source.getOrCreateTagElement("BlockEntityTag").putString("mod:banner", "keep")
        val before = source.copy()
        val changed = CreativeBanner.buildBanner(source, DyeColor.RED, layers)
        check((changed.item as BannerItem).color == DyeColor.RED && changed.count == 3)
        check(changed.getTagElement("display") == source.getTagElement("display"))
        check(changed.tag?.getString("mod:custom") == "keep")
        check(changed.getTagElement("BlockEntityTag")?.getString("mod:banner") == "keep")
        check(CreativeBanner.layers(changed) == layers)
        val patternTag = changed.getTagElement("BlockEntityTag")!!.getList("Patterns", 10).getCompound(0)
        check(patternTag.getString("Pattern") == pattern.value().hashname)
        check(patternTag.getInt("Color") == DyeColor.BLUE.id)
        check(ItemStack.matches(source, before))
        val shield = ItemStack(Items.SHIELD)
        shield.damageValue = 12
        shield.tag!!.merge(source.tag!!.copy())
        val shieldBefore = shield.copy()
        val editedShield = CreativeBanner.buildBanner(shield, DyeColor.GREEN, layers)
        check(editedShield.item === Items.SHIELD && editedShield.damageValue == 12)
        check(CreativeBanner.baseColor(editedShield) == DyeColor.GREEN)
        check(editedShield.getTagElement("BlockEntityTag")!!.getInt("Base") == DyeColor.GREEN.id)
        check(editedShield.getTagElement("display") == shield.getTagElement("display"))
        check(ItemStack.matches(shield, shieldBefore))
        val cleared = CreativeBanner.buildBanner(editedShield, DyeColor.GREEN, emptyList())
        check(CreativeBanner.layers(cleared).isEmpty() && cleared.damageValue == 12)
        check(cleared.getTagElement("BlockEntityTag")!!.getString("mod:banner") == "keep")
        check(runCatching { CreativeBanner.buildBanner(ItemStack(Items.STONE), DyeColor.RED, layers) }.isFailure)
        check(runCatching { CreativeBanner.buildBanner(ItemStack.EMPTY, DyeColor.RED, layers) }.isFailure)
        check(runCatching { CreativeBanner.buildBanner(source, DyeColor.RED,
            List(CreativeBanner.MAX_LAYERS + 1) { layers[0] }) }.isFailure)
        for (base in DyeColor.entries) {
            val banner = CreativeBanner.createBanner(base, layers)
            check(banner.item === BuiltInRegistries.ITEM.first { it is BannerItem && it.color == base })
            check(banner.count == 1 && CreativeBanner.layers(banner) == layers)
            val createdShield = CreativeBanner.createShield(base, layers)
            check(createdShield.item === Items.SHIELD && createdShield.count == 1)
            check(CreativeBanner.baseColor(createdShield) == base)
            check(CreativeBanner.layers(createdShield) == layers && createdShield.damageValue == 0)
            for (stack in listOf(banner, createdShield)) {
                check(ItemStack.matches(stack, ItemStack.of(stack.save(CompoundTag()))))
            }
        }
        val draftLayers = layers.toMutableList()
        val draft = CreativeBanner.createBanner(DyeColor.RED, draftLayers)
        draftLayers.clear()
        check(CreativeBanner.layers(draft) == layers)
        for (create in listOf(CreativeBanner::createBanner, CreativeBanner::createShield)) {
            check(CreativeBanner.layers(create(DyeColor.WHITE, emptyList())).isEmpty())
            check(CreativeBanner.layers(create(DyeColor.WHITE, List(CreativeBanner.MAX_LAYERS) { layers[0] })).size == CreativeBanner.MAX_LAYERS)
            check(runCatching { create(DyeColor.WHITE, List(CreativeBanner.MAX_LAYERS + 1) { layers[0] }) }.isFailure)
        }
        val emptyInventory = List(36) { ItemStack.EMPTY }
        val emptyPlan = creativePlan(emptyInventory, draft, 1) { 64 }
        check(emptyPlan.size == 1 && emptyPlan[0].first == 0 && emptyPlan[0].second.count == 1)
        check(emptyInventory.all { it.isEmpty })
        val full = List(36) { ItemStack(Items.STONE, 64) }
        val fullBefore = full.map { it.copy() }
        check(runCatching { creativePlan(full, draft, 1) { 64 } }.isFailure)
        check(full.indices.all { ItemStack.matches(full[it], fullBefore[it]) })
        val occupied = full.toMutableList().also { it[12] = ItemStack.EMPTY }
        val occupiedPlan = creativePlan(occupied, draft, 1) { 64 }
        check(occupiedPlan.size == 1 && occupiedPlan[0].first == 12)
        check(occupied[12].isEmpty)
        val matching = full.toMutableList().also { it[4] = draft.copyWithCount(2) }
        val merge = creativePlan(matching, draft, 1) { 64 }
        check(merge.size == 1 && merge[0].first == 4 && merge[0].second.count == 3 && matching[4].count == 2)
        val different = draft.copy().also { it.orCreateTag.putBoolean("mod:different", true) }
        check(runCatching { creativePlan(matching, different, 1) { 64 } }.isFailure)
        val split = creativePlan(emptyInventory, ItemStack(Items.STONE), 65) { 32 }
        check(split.map { it.second.count } == listOf(32, 32, 1))
        check(split.sumOf { it.second.count } == 65)
        check(runCatching { creativePlan(emptyInventory, draft, 0) { 64 } }.isFailure)
        check(runCatching { creativePlan(emptyInventory, draft, 2305) { 64 } }.isFailure)
        check(runCatching { creativePlan(emptyInventory, ItemStack.EMPTY, 1) { 64 } }.isFailure)
        check(ItemStack.matches(source, before) && ItemStack.matches(shield, shieldBefore))
    }
}

object CreativeTextNbtCheck {
    fun checkAll() {
        val text = StyledText(listOf(ColoredRun("name\nlore", 0x123456)))
        val lines = CreativeText.loreLines(text)
        check(lines.map { it.string } == listOf("name", "lore"))
        CreativeText.validateTextSize(lines)
        val stack = ItemStack(Items.STONE, 17)
        stack.orCreateTag.putString("mod:custom", "keep")
        stack.getOrCreateTagElement("display").putInt("color", 123)
        CreativeText.setName(stack, CreativeText.component(text))
        CreativeText.setLore(stack, lines)
        CreativeText.setUnbreakable(stack)
        val saved = stack.save(CompoundTag())
        check(ItemStack.matches(stack, ItemStack.of(saved)))
        check(stack.count == 17 && stack.tag!!.getString("mod:custom") == "keep")
        check(stack.tag!!.getBoolean("Unbreakable"))
        check(stack.tag!!.getByte("Unbreakable").toInt() == 1)
        val display = stack.getTagElement("display")!!
        check(display.getString("Name") == Component.Serializer.toJson(CreativeText.component(text)))
        val lore = display.getList("Lore", 8)
        check(lore.size == 2 && lore.getString(0) == Component.Serializer.toJson(lines[0]))
        val json = JsonParser.parseString(CreativeText.itemDataJson(stack)).asJsonObject
        check(json.get("id").asString == "minecraft:stone" && json.get("Count").asInt == 17)
        val jsonTag = json.getAsJsonObject("tag")
        check(jsonTag.getAsJsonObject("display").getAsJsonArray("Lore").size() == 2)
        check(jsonTag.get("Unbreakable").asInt == 1 && jsonTag.get("mod:custom").asString == "keep")
        stack.orCreateTag.putByteArray("bytes", byteArrayOf(1, 2))
        stack.orCreateTag.putIntArray("ints", intArrayOf(3, 4))
        stack.orCreateTag.putLongArray("longs", longArrayOf(5, 6))
        val arrays = JsonParser.parseString(CreativeText.itemDataJson(stack)).asJsonObject.getAsJsonObject("tag")
        check(arrays.getAsJsonArray("bytes")[1].asInt == 2)
        check(arrays.getAsJsonArray("ints")[1].asInt == 4)
        check(arrays.getAsJsonArray("longs")[1].asLong == 6L)
        CreativeText.clearDisplayKey(stack, "Name")
        check(!display.contains("Name") && display.contains("Lore") && display.getInt("color") == 123)
        CreativeText.clearDisplayKey(stack, "Lore")
        check(!display.contains("Lore") && display.getInt("color") == 123)
        check(stack.count == 17 && stack.tag!!.getBoolean("Unbreakable") && stack.tag!!.getString("mod:custom") == "keep")
        val plain = ItemStack(Items.STONE)
        CreativeText.clearDisplayKey(plain, "Name")
        check(!plain.hasTag())
        CreativeText.setName(plain, Component.literal("name"))
        CreativeText.clearDisplayKey(plain, "Name")
        check(plain.getTagElement("display") == null)
        check(CreativeText.loreLines(StyledText(listOf(ColoredRun("\n".repeat(255), 0)))).size == 256)
        check(runCatching { CreativeText.loreLines(StyledText(listOf(ColoredRun("\n".repeat(256), 0)))) }.isFailure)
        check(runCatching { CreativeText.setLore(plain, List(257) { Component.literal("") }) }.isFailure)
        check(runCatching { CreativeText.setName(plain, Component.literal("é".repeat(17000))) }.isFailure)
        check(runCatching { CreativeText.itemDataJson(ItemStack.EMPTY) }.isFailure)
    }
}
