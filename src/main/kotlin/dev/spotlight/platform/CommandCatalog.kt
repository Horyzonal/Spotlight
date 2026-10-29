package dev.spotlight.platform

import net.minecraft.client.Minecraft
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.Difficulty
import net.minecraft.world.level.GameType
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.item.Item
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.gamerules.GameRule

internal val selectionRegistries = mapOf(
    SelectionKind.ITEMS to "item", SelectionKind.ATTRIBUTES to "attribute",
    SelectionKind.ENTITY_TYPES to "entity_type", SelectionKind.DAMAGE_TYPES to "damage_type",
    SelectionKind.EFFECTS to "mob_effect", SelectionKind.ENCHANTMENTS to "enchantment",
    SelectionKind.BIOMES to "worldgen/biome", SelectionKind.GAMERULES to "game_rule",
    SelectionKind.STRUCTURES to "worldgen/structure", SelectionKind.PARTICLES to "particle_type",
    SelectionKind.SOUNDS to "sound_event", SelectionKind.BLOCKS to "block",
    SelectionKind.FEATURES to "worldgen/configured_feature", SelectionKind.FUNCTIONS to "function",
    SelectionKind.ADVANCEMENTS to "advancement", SelectionKind.RECIPES to "recipe",
    SelectionKind.LOOT_TABLES to "loot_table", SelectionKind.PREDICATES to "predicate",
    SelectionKind.ITEM_MODIFIERS to "item_modifier"
)

internal object CommandCatalog {
    private fun registries(): List<Registry<*>> =
        (Minecraft.getInstance().connection?.registryAccess()?.registries()?.map { it.value() }?.toList().orEmpty() +
            BuiltInRegistries.REGISTRY.toList()).distinctBy { it.key().identifier() }

    fun registry(id: String): Registry<*>? = registries().firstOrNull {
        it.key().identifier().toString() == if (':' in id) id else "minecraft:$id"
    }

    fun entries(registry: Registry<*>, kind: SelectionKind, tags: Boolean = false): List<CommandSuggestion> {
        if (tags) return registry.tags.map {
            val id = "#${it.key().location()}"
            entry(id, id, registry.key().identifier().toString(), SelectionKind.TAGS)
        }.toList()
        return registry.entrySet().map { (key, value) ->
            val id = key.identifier().toString()
            val title = when (value) {
                is Item -> Component.translatable(value.descriptionId).string
                is Block -> value.name.string
                is EntityType<*> -> value.description.string
                is MobEffect -> value.displayName.string
                is Attribute -> Component.translatable(value.descriptionId).string
                is Enchantment -> value.description().string
                is GameRule<*> -> Component.translatable(value.descriptionId).string
                else -> id
            }
            val detail = if (value is GameRule<*>) "$id · ${value.valueClass().simpleName}" else id
            entry(id, title, detail, kind, if (kind in setOf(SelectionKind.ITEMS, SelectionKind.EFFECTS, SelectionKind.ENTITY_TYPES)) id else null)
        }
    }

    fun catalog(kind: SelectionKind): List<CommandSuggestion> {
        val client = Minecraft.getInstance()
        check(client.isSameThread) { "CommandSuggestions.catalog must run on client thread" }
        return when (kind) {
            SelectionKind.PLAYERS -> client.connection?.onlinePlayers?.map {
                val profile = it.profile
                entry(profile.name(), profile.name(), "${profile.id()}${if (profile.id() == client.player?.uuid) " · me" else ""}", kind, profile.id().toString())
            }.orEmpty()
            SelectionKind.ENTITIES -> listOf(
                entry("@s", "Me", "Executing entity", kind), entry("@a", "Everyone", "All players", kind),
                entry("@p", "Nearest player", "Nearest player", kind), entry("@r", "Random player", "Random player", kind),
                entry("@e", "All entities", "Entity selector", kind)
            )
            SelectionKind.ADVANCEMENTS -> client.connection?.advancements?.tree()?.nodes()?.map {
                val holder = it.holder()
                entry(holder.id().toString(), net.minecraft.advancements.Advancement.name(holder).string,
                    "Client-known advancement · ${holder.id()}", kind)
            }.orEmpty()
            SelectionKind.SOUNDS -> client.soundManager.availableSounds.map {
                entry(it.toString(), it.toString(), "Loaded sound resource", kind)
            }
            SelectionKind.DIFFICULTIES -> Difficulty.values().map { entry(it.serializedName, it.displayName.string, "", kind) }
            SelectionKind.GAMEMODES -> GameType.values().map { entry(it.serializedName, it.longDisplayName.string, "", kind) }
            SelectionKind.DIMENSIONS -> client.connection?.levels()?.map { entry(it.identifier().toString(), it.identifier().toString(), "", kind) }.orEmpty()
            SelectionKind.TAGS -> registries().flatMap { entries(it, kind, tags = true) }
            SelectionKind.TIMES -> listOf("day", "noon", "night", "midnight").map { entry(it, it, "Time preset", kind) }
            SelectionKind.WEATHERS -> listOf("clear", "rain", "thunder").map { entry(it, it, "Weather", kind) }
            
            SelectionKind.IP -> emptyList()
            
            SelectionKind.RECIPES -> registry("recipe")?.let { entries(it, kind) }.orEmpty()
            else -> selectionRegistries[kind]?.let { registry(it) }?.let { entries(it, kind) }.orEmpty()
        }.sortedBy { it.value }
    }

    private fun entry(value: String, title: String, detail: String, kind: SelectionKind, icon: String? = null) =
        CommandSuggestion(value, title, detail, 0, 0, kind, icon)
}
