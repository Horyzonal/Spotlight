package dev.spotlight.platform

import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.RecordItem
import java.util.Locale

object ItemFacts {
    fun summary(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        stack.item.foodProperties?.let { return "Hunger ${it.nutrition} · Saturation ${number((it.nutrition * it.saturationModifier * 2).toDouble())}" }
        (stack.item as? RecordItem)?.let {
            val seconds = it.lengthInTicks / 20
            return "Comparator ${it.analogOutput} · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        }
        if (stack.maxDamage > 0) return "Durability ${(stack.maxDamage - stack.damageValue).coerceAtLeast(0)} / ${stack.maxDamage}"
        net.minecraft.world.item.Equipable.get(stack)?.let { return "Equipment: ${it.equipmentSlot.name.lowercase(Locale.ROOT)}" }
        if (stack.item.enchantmentValue > 0) return "Enchantability ${stack.item.enchantmentValue}"
        return "${stack.rarity.name.lowercase(Locale.ROOT)} · Stack ${stack.maxStackSize}"
    }
    fun describe(stack: ItemStack): List<Component> {
        if (stack.isEmpty) return emptyList()
        return buildList {
            fun fact(text: String) { add(Component.literal(text)) }
            fact("Max stack: ${stack.maxStackSize}")
            fact("Rarity: ${stack.rarity.name.lowercase(Locale.ROOT)}")
            if (stack.maxDamage > 0) fact("Durability: ${stack.maxDamage - stack.damageValue} / ${stack.maxDamage}")
            fact("Enchantability: ${stack.item.enchantmentValue}")
            stack.item.foodProperties?.let {
                fact("Nutrition: ${it.nutrition} hunger points")
                fact("Saturation: ${number((it.nutrition * it.saturationModifier * 2).toDouble())} points (before hunger cap)")
                fact("Consume duration: ${number(stack.getUseDuration() / 20.0)} seconds")
                fact("Consume effects: ${it.effects.size}")
            }
            net.minecraft.world.item.Equipable.get(stack)?.let { fact("Equipment slot: ${it.equipmentSlot.name.lowercase(Locale.ROOT)}") }
            (stack.item as? RecordItem)?.let {
                add(Component.translatable(it.descriptionId + ".desc"))
                fact("Track duration: ${number(it.lengthInTicks / 20.0)} seconds")
                fact("Comparator output: ${it.analogOutput}")
            }
            net.minecraft.world.entity.EquipmentSlot.values().forEach { slot ->
                stack.getAttributeModifiers(slot).entries().forEach { entry ->
                    add(Component.literal("Attribute bonus [${slot.name.lowercase(Locale.ROOT)}]: ")
                        .append(Component.translatable(entry.key.descriptionId)).append(" ${number(entry.value.amount)} (${entry.value.operation.name.lowercase(Locale.ROOT)})"))
                }
            }
        }
    }
    private fun number(value: Double) = String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
}
