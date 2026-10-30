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
import net.minecraft.world.level.GameRules

internal val selectionRegistries = mapOf(
    SelectionKind.ITEMS to "item", SelectionKind.ATTRIBUTES to "attribute",
    SelectionKind.ENTITY_TYPES to "entity_type", SelectionKind.DAMAGE_TYPES to "damage_type",
    SelectionKind.EFFECTS to "mob_effect", SelectionKind.ENCHANTMENTS to "enchantment",
    SelectionKind.BIOMES to "worldgen/biome",
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
            BuiltInRegistries.REGISTRY.toList()).distinctBy { it.key().location() }

    fun registry(id: String): Registry<*>? = registries().firstOrNull {
        it.key().location().toString() == if (':' in id) id else "minecraft:$id"
    }

    fun entries(registry: Registry<*>, kind: SelectionKind, tags: Boolean = false): List<CommandSuggestion> {
        if (tags) return registry.tags.map {
            val id = "#${it.first.location()}"
            entry(id, id, registry.key().location().toString(), SelectionKind.TAGS)
        }.toList()
        return registry.entrySet().map { (key, value) ->
            val id = key.location().toString()
            val title = when (value) {
                is Item -> Component.translatable(value.descriptionId).string
                is Block -> value.name.string
                is EntityType<*> -> value.description.string
                is MobEffect -> value.displayName.string
                is Attribute -> Component.translatable(value.descriptionId).string
                is Enchantment -> Component.translatable(value.descriptionId).string
                else -> id
            }
            val detail = id
            entry(id, title, detail, kind, if (kind in setOf(SelectionKind.ITEMS, SelectionKind.EFFECTS, SelectionKind.ENTITY_TYPES)) id else null)
        }
    }

    private var serverSession: net.minecraft.server.MinecraftServer? = null
    private var serverCatalog: Map<SelectionKind, List<CommandSuggestion>> = emptyMap()
    private var serverLoading = false
    private var serverRefresh = 0L

    private fun refreshServer(client: Minecraft) {
        val server = client.singleplayerServer
        if (serverSession !== server) {
            serverSession = server
            serverCatalog = emptyMap()
            serverLoading = false
            serverRefresh = 0L
        }
        if (server == null || serverLoading || System.nanoTime() - serverRefresh < 2_000_000_000L) return
        serverLoading = true
        server.execute {
            val rows = runCatching {
                buildMap {
                    for ((kind, name) in selectionRegistries) {
                        val registry = server.registryAccess().registries().map { it.value() }
                            .filter { it.key().location().toString() == "minecraft:$name" }.findFirst().orElse(null)
                        if (registry != null) put(kind, entries(registry, kind))
                    }
                    put(SelectionKind.FUNCTIONS, server.functions.functionNames.map {
                        entry(it.toString(), it.toString(), "Integrated-server function", SelectionKind.FUNCTIONS)
                    })
                    for (kind in listOf(SelectionKind.LOOT_TABLES, SelectionKind.PREDICATES, SelectionKind.ITEM_MODIFIERS)) {
                        val key = when (kind) {
                            SelectionKind.LOOT_TABLES -> net.minecraft.world.level.storage.loot.LootDataType.TABLE
                            SelectionKind.PREDICATES -> net.minecraft.world.level.storage.loot.LootDataType.PREDICATE
                            else -> net.minecraft.world.level.storage.loot.LootDataType.MODIFIER
                        }
                        put(kind, server.lootData.getKeys(key).map {
                            entry(it.toString(), it.toString(), "Integrated-server data", kind)
                        })
                    }
                }
            }
            client.execute {
                if (serverSession === server && client.singleplayerServer === server) {
                    rows.onSuccess { serverCatalog = it }.onFailure {
                        org.slf4j.LoggerFactory.getLogger("spotlight").warn("Server command catalog snapshot failed", it)
                    }
                    serverLoading = false
                    serverRefresh = System.nanoTime()
                }
            }
        }
    }

    fun catalog(kind: SelectionKind): List<CommandSuggestion> {
        val client = Minecraft.getInstance()
        check(client.isSameThread) { "CommandSuggestions.catalog must run on client thread" }
        refreshServer(client)
        return when (kind) {
            SelectionKind.PLAYERS -> client.connection?.onlinePlayers?.map {
                val profile = it.profile
                entry(profile.name, profile.name, "${profile.id}${if (profile.id == client.player?.uuid) " · me" else ""}", kind, profile.id.toString())
            }.orEmpty()
            SelectionKind.ENTITIES -> listOf(
                entry("@s", "Me", "Executing entity", kind), entry("@a", "Everyone", "All players", kind),
                entry("@p", "Nearest player", "Nearest player", kind), entry("@r", "Random player", "Random player", kind),
                entry("@e", "All entities", "Entity selector", kind)
            )
            SelectionKind.ADVANCEMENTS -> client.connection?.advancements?.advancements?.allAdvancements?.map {
                entry(it.id.toString(), it.chatComponent.string,
                    "Client-known advancement · ${it.id}", kind)
            }.orEmpty()
            SelectionKind.SOUNDS -> client.soundManager.availableSounds.map {
                entry(it.toString(), it.toString(), "Loaded sound resource", kind)
            }
            SelectionKind.DIFFICULTIES -> Difficulty.values().map { entry(it.serializedName, it.displayName.string, "", kind) }
            SelectionKind.GAMEMODES -> GameType.values().map { entry(it.serializedName, it.longDisplayName.string, "", kind) }
            SelectionKind.DIMENSIONS -> client.connection?.levels()?.map { entry(it.location().toString(), it.location().toString(), "", kind) }.orEmpty()
            SelectionKind.GAMERULES -> gameRules()
            SelectionKind.TAGS -> registries().flatMap { entries(it, kind, tags = true) }
            SelectionKind.TIMES -> listOf("day", "noon", "night", "midnight").map { entry(it, it, "Time preset", kind) }
            SelectionKind.WEATHERS -> listOf("clear", "rain", "thunder").map { entry(it, it, "Weather", kind) }

            SelectionKind.IP -> emptyList()

            SelectionKind.RECIPES -> CraftingRecipes.catalog()
            else -> selectionRegistries[kind]?.let { registry(it) }?.let { entries(it, kind) }
                ?: serverCatalog[kind].orEmpty()
        }.sortedBy { it.value }
    }

    internal fun gameRules(): List<CommandSuggestion> = buildList {
        GameRules.visitGameRuleTypes(object : GameRules.GameRuleTypeVisitor {
            override fun <T : GameRules.Value<T>> visit(key: GameRules.Key<T>, type: GameRules.Type<T>) {
                add(entry(key.id, Component.translatable(key.descriptionId).string,
                    "${key.id} · ${type.createRule().javaClass.simpleName}", SelectionKind.GAMERULES))
            }
        })
    }

    private fun entry(value: String, title: String, detail: String, kind: SelectionKind, icon: String? = null) =
        CommandSuggestion(value, title, detail, 0, 0, kind, icon)
}
