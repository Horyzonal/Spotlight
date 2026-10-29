package dev.spotlight.platform

import com.mojang.serialization.JsonOps
import dev.spotlight.commands.creativePlan
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.BannerItem
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.BannerPatternLayers

object CreativeBannerCheck {
    @JvmStatic fun main(args: Array<String>) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val registries = net.minecraft.data.registries.VanillaRegistries.createWorldLookup()
                BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach { it.apply() }
        val pattern = registries.lookupOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN).listElements().findFirst().orElseThrow()
        val layers = BannerPatternLayers(listOf(BannerPatternLayers.Layer(pattern, DyeColor.BLUE)))
        val source = ItemStack(BuiltInRegistries.ITEM.first { it is BannerItem && it.color == DyeColor.WHITE }, 3)
        source.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Keep name"))
        source.set(DataComponents.LORE, net.minecraft.world.item.component.ItemLore(listOf(net.minecraft.network.chat.Component.literal("Keep lore"))))
        source.remove(DataComponents.MAX_STACK_SIZE)
        val before = source.copy()
        val changed = CreativeBanner.buildBanner(source, DyeColor.RED, layers)
        check((changed.item as BannerItem).color == DyeColor.RED && changed.count == 3)
        check(changed.get(DataComponents.CUSTOM_NAME) == source.get(DataComponents.CUSTOM_NAME))
        check(changed.get(DataComponents.LORE) == source.get(DataComponents.LORE))
        check(changed.get(DataComponents.MAX_STACK_SIZE) == null)
        check(changed.get(DataComponents.BANNER_PATTERNS) == layers)
        check(ItemStack.matches(source, before))
        val shield = ItemStack(Items.SHIELD)
        shield.damageValue = 12
        shield.set(DataComponents.CUSTOM_NAME, source.get(DataComponents.CUSTOM_NAME))
        val shieldBefore = shield.copy()
        val editedShield = CreativeBanner.buildBanner(shield, DyeColor.GREEN, layers)
        check(editedShield.item === Items.SHIELD && editedShield.damageValue == 12)
        check(editedShield.get(DataComponents.BASE_COLOR) == DyeColor.GREEN)
        check(editedShield.get(DataComponents.CUSTOM_NAME) == shield.get(DataComponents.CUSTOM_NAME))
        check(ItemStack.matches(shield, shieldBefore))
        val cleared = CreativeBanner.buildBanner(editedShield, DyeColor.GREEN, BannerPatternLayers.EMPTY)
        check(cleared.get(DataComponents.BANNER_PATTERNS) == BannerPatternLayers.EMPTY && cleared.damageValue == 12)
        check(runCatching { CreativeBanner.buildBanner(ItemStack(Items.STONE), DyeColor.RED, layers) }.isFailure)
        check(runCatching { CreativeBanner.buildBanner(ItemStack.EMPTY, DyeColor.RED, layers) }.isFailure)
        check(runCatching { CreativeBanner.buildBanner(source, DyeColor.RED,
            BannerPatternLayers(List(CreativeBanner.MAX_LAYERS + 1) { layers.layers()[0] })) }.isFailure)
        for (base in DyeColor.entries) {
            val banner = CreativeBanner.createBanner(base, layers.layers())
            check(banner.item === BuiltInRegistries.ITEM.first { it is BannerItem && it.color == base })
            check(banner.count == 1 && banner.get(DataComponents.BANNER_PATTERNS) == layers)
            val createdShield = CreativeBanner.createShield(base, layers.layers())
            check(createdShield.item === Items.SHIELD && createdShield.count == 1)
            check(createdShield.get(DataComponents.BASE_COLOR) == base)
            check(createdShield.get(DataComponents.BANNER_PATTERNS) == layers && createdShield.damageValue == 0)
            for (stack in listOf(banner, createdShield)) {
                ItemStack.validateStrict(stack).getOrThrow()
                ItemStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow()
            }
        }
        val draftLayers = layers.layers().toMutableList()
        val draft = CreativeBanner.createBanner(DyeColor.RED, draftLayers)
        draftLayers.clear()
        check(draft.get(DataComponents.BANNER_PATTERNS) == layers)
        for (create in listOf(CreativeBanner::createBanner, CreativeBanner::createShield)) {
            check(create(DyeColor.WHITE, emptyList()).get(DataComponents.BANNER_PATTERNS) == BannerPatternLayers.EMPTY)
            check(create(DyeColor.WHITE, List(CreativeBanner.MAX_LAYERS) { layers.layers()[0] })
                .get(DataComponents.BANNER_PATTERNS)?.layers()?.size == CreativeBanner.MAX_LAYERS)
            check(runCatching { create(DyeColor.WHITE, List(CreativeBanner.MAX_LAYERS + 1) { layers.layers()[0] }) }.isFailure)
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
        check(occupied[0].item === Items.STONE && occupied[0].count == 64 && occupied[12].isEmpty)
        val matching = full.toMutableList().also { it[4] = draft.copyWithCount(2) }
        val merge = creativePlan(matching, draft, 1) { 64 }
        check(merge.size == 1 && merge[0].first == 4 && merge[0].second.count == 3 && matching[4].count == 2)
        check(ItemStack.matches(source, before) && ItemStack.matches(shield, shieldBefore))
        println("Creative banner copy, standalone drafts, all dyes, shield components, layer cap and inventory safety checks passed")
    }
}
