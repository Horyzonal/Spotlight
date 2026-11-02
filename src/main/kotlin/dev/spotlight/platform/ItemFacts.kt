package dev.spotlight.platform

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.Locale

object ItemFacts {
    fun summary(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        stack.get(DataComponents.FOOD)?.let {
            return "Hunger ${it.nutrition()} · Saturation ${number(it.saturation().toDouble())}"
        }
        song(stack)?.let {
            val seconds = it.lengthInSeconds().toInt().coerceAtLeast(0)
            return "Comparator ${it.comparatorOutput()} · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        }
        if (stack.maxDamage > 0) return "Durability ${(stack.maxDamage - stack.damageValue).coerceAtLeast(0)} / ${stack.maxDamage}"
        stack.get(DataComponents.EQUIPPABLE)?.let { return "Equipment: ${it.slot().serializedName}" }
        stack.get(DataComponents.ENCHANTABLE)?.let { return "Enchantability ${it.value()}" }
        return "${stack.rarity.name.lowercase(Locale.ROOT)} · Stack ${stack.maxStackSize}"
    }

    fun describe(stack: ItemStack): List<Component> {
        if (stack.isEmpty) return emptyList()
        return buildList {
            fun fact(text: String) { add(Component.literal(text)) }
            fact("Max stack: ${stack.maxStackSize}")
            fact("Rarity: ${stack.rarity.name.lowercase(Locale.ROOT)}")
            if (stack.maxDamage > 0) fact("Durability: ${(stack.maxDamage - stack.damageValue).coerceAtLeast(0)} / ${stack.maxDamage}" +
                if (stack.has(DataComponents.UNBREAKABLE)) " (unbreakable)" else "")
            stack.get(DataComponents.ENCHANTABLE)?.let { fact("Enchantability: ${it.value()}") }
            stack.get(DataComponents.FOOD)?.let {
                fact("Nutrition: ${it.nutrition()} hunger points")
                fact("Saturation: ${number(it.saturation().toDouble())} points (before hunger cap)")
            }
            stack.get(DataComponents.CONSUMABLE)?.let {
                fact("Consume duration: ${number(it.consumeSeconds().toDouble())} seconds")
                fact("Consume effects: ${it.onConsumeEffects().size}")
            }
            stack.get(DataComponents.EQUIPPABLE)?.let { fact("Equipment slot: ${it.slot().serializedName}") }
            song(stack)?.let { song ->
                run {
                    add(Component.literal("Track: ").append(song.description()))
                    fact("Track duration: ${number(song.lengthInSeconds().toDouble())} seconds")
                    fact("Comparator output: ${song.comparatorOutput()}")
                }
            }
            stack.get(DataComponents.ATTRIBUTE_MODIFIERS)?.modifiers()?.forEach { entry ->
                val modifier = entry.modifier()
                add(Component.literal("Attribute bonus [${entry.slot().serializedName}]: ")
                    .append(Component.translatable(entry.attribute().value().descriptionId))
                    .append(" ${number(modifier.amount())} (${modifier.operation().serializedName})"))
            }
        }
    }

    private fun song(stack: ItemStack): net.minecraft.world.item.JukeboxSong? {
        val song = stack.get(DataComponents.JUKEBOX_PLAYABLE)?.song() ?: return null
        val lookup = net.minecraft.client.Minecraft.getInstance()?.level?.registryAccess()
        return if (lookup != null) song.unwrap(lookup).orElse(null)?.value() else song.unwrap(net.minecraft.data.registries.VanillaRegistries.createLookup()).orElse(null)?.value()
    }

    private fun number(value: Double) = String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
}
