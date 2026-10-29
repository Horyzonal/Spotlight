package dev.spotlight.platform

import com.google.gson.JsonObject
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.commands.arguments.*
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument
import net.minecraft.commands.arguments.blocks.BlockStateArgument
import net.minecraft.commands.arguments.item.ItemArgument
import net.minecraft.commands.arguments.item.ItemPredicateArgument
import net.minecraft.commands.synchronization.ArgumentTypeInfos
import net.minecraft.commands.synchronization.SuggestionProviders
import net.minecraft.world.Difficulty


object CommandSuggestions {
    private const val LIMIT = 128

    fun apply(text: String, suggestion: CommandSuggestion): String = replaceCommandSuggestion(text, suggestion)

    fun isValidIp(value: String): Boolean = validCommandIp(value)

    
    fun executionText(text: String): String = text.trimEnd(' ')

    
    fun expandAlias(text: String, cursor: Int): Pair<String, Int>? {
        val client = Minecraft.getInstance()
        val connection = client.connection ?: return null
        return expandCommandAlias(connection.commands, connection.suggestionsProvider, text, cursor) { type, selector ->
            when (type) {
                is EntityArgument -> selector
                is GameProfileArgument -> if (selector == "@s") client.player?.gameProfile?.name else null
                else -> null
            }
        }
    }

    
    fun argumentHint(text: String): String? {
        val connection = Minecraft.getInstance().connection ?: return null
        return commandArgumentHint(connection.commands, connection.suggestionsProvider, text)
    }

    
    fun catalog(kind: SelectionKind): List<CommandSuggestion> = CommandCatalog.catalog(kind)

    
    fun completeOnSpace(text: String, cursor: Int, candidates: List<CommandSuggestion> = emptyList()): Pair<String, Int>? {
        expandAlias(text, cursor)?.let { return it }
        val connection = Minecraft.getInstance().connection ?: return null
        return completeCommandOnSpace(connection.commands, connection.suggestionsProvider, text, cursor, candidates) { query ->
            localSuggestions(connection.commands, connection.suggestionsProvider, query).ranked
        }
    }

    private data class LocalSuggestions(
        val ranked: List<CommandSuggestion>,
        val knownRows: Map<String, CommandSuggestion>,
        val inferredKind: SelectionKind?
    )

    private fun localSuggestions(
        dispatcher: com.mojang.brigadier.CommandDispatcher<ClientSuggestionProvider>,
        source: ClientSuggestionProvider, text: String
    ): LocalSuggestions {
        val start = commandTokenStart(text)
        val query = text.substring(start)
        val children = commandSuggestionChildren(dispatcher, source, text, start)
        val local = mutableListOf<CommandSuggestion>()
        val knownRows = mutableMapOf<String, CommandSuggestion>()
        var inferredKind: SelectionKind? = null
        for (node in children) {
            if (node is LiteralCommandNode) {
                
                val rule = if (dispatcher.getPath(node).toList().dropLast(1).lastOrNull() == "gamerule") {
                    catalog(SelectionKind.GAMERULES).firstOrNull {
                        it.value == node.literal || it.value.removePrefix("minecraft:") == node.literal
                    }
                } else null
                val row = rule?.copy(value = node.literal, start = start, end = text.length)
                    ?: CommandSuggestion(node.literal, node.literal, start = start, end = text.length,
                        kind = literalKind(children.filterIsInstance<LiteralCommandNode<ClientSuggestionProvider>>().map { it.literal }))
                local += row
                knownRows[row.value] = row
                continue
            }
            if (node !is ArgumentCommandNode<ClientSuggestionProvider, *>) continue
            val type = node.type
            val metadata = argumentMetadata(type)
            val registryId = commandRegistryId(metadata)
            val path = dispatcher.getPath(node).toList()
            val kind = if (path.dropLast(1).lastOrNull() in setOf("ban-ip", "pardon-ip") && node.name == "target") SelectionKind.IP
                else kind(type, registryId, node.customSuggestions?.let { SuggestionProviders.getName(it).toString() }, node.name)
            if (kind != null) inferredKind = kind
            val candidates = if (registryId != null) {
                val registry = CommandCatalog.registry(registryId)
                val tagType = type is ResourceOrTagArgument<*> || type is ResourceOrTagKeyArgument<*> || type is ResourceSelectorArgument<*>
                if (registry != null) CommandCatalog.entries(registry, kind ?: SelectionKind.REGISTRY) +
                    (if (tagType) CommandCatalog.entries(registry, SelectionKind.TAGS, tags = true) else emptyList())
                else kind?.let { catalog(it) }.orEmpty()
            } else when (type) {
                is EntityArgument -> catalog(SelectionKind.PLAYERS) + catalog(SelectionKind.ENTITIES).filter {
                    
                    val playersOnly = metadata.get("type")?.asString == "players"
                    val single = metadata.get("amount")?.asString == "single"
                    (!playersOnly || it.value != "@e") && (!single || it.value !in setOf("@a", "@e"))
                }
                is TimeArgument -> emptyList()
                is ItemPredicateArgument, is BlockPredicateArgument -> {
                    val id = if (type is ItemPredicateArgument) "item" else "block"
                    kind?.let { catalog(it) }.orEmpty() + CommandCatalog.registry(id)?.let {
                        CommandCatalog.entries(it, SelectionKind.TAGS, tags = true)
                    }.orEmpty()
                }
                else -> kind?.let { catalog(it) }.orEmpty()
            }
            candidates.forEach { knownRows[it.value] = it }
            
            if (!query.startsWith('@') && !query.startsWith('"') && !query.startsWith('\'') &&
                '[' !in query && '{' !in query) {
                local += candidates.map { it.copy(start = start, end = text.length) }
            }
            if (kind == SelectionKind.IP && validCommandIp(query)) {
                local += CommandSuggestion(query, query, "Manual numeric IP; server decides acceptance; client cannot discover player IPs", start, text.length, kind)
            }
        }
        if (inferredKind == SelectionKind.ENTITIES || inferredKind == SelectionKind.PLAYERS) {
            catalog(SelectionKind.PLAYERS).forEach { knownRows[it.value] = it }
        }
        val ranked = local.mapNotNull { suggestion ->
            val score = minOf(commandMatch(query, suggestion.value), commandMatch(query, suggestion.title),
                if (query.equals("me", true) && suggestion.kind == SelectionKind.PLAYERS && " · me" in suggestion.detail) 0 else Int.MAX_VALUE)
            if (score == Int.MAX_VALUE) null else score to suggestion
        }.sortedWith(compareBy({ it.first }, { it.second.value })).map { it.second }.distinctBy { Triple(it.start, it.end, it.value) }.take(LIMIT)
        return LocalSuggestions(ranked, knownRows, inferredKind)
    }

    
    fun request(text: String, accept: (List<CommandSuggestion>) -> Unit) {
        val client = Minecraft.getInstance()
        if (!client.isSameThread) {
            client.execute { request(text, accept) }
            return
        }
        if (!validCommandText(text) && text != "/") { accept(emptyList()); return }
        val connection = client.connection ?: run { accept(emptyList()); return }
        val dispatcher = connection.commands
        val source = connection.suggestionsProvider
        val reader = StringReader(text).also { it.cursor = 1 }
        val parsed = dispatcher.parse(reader, source)
        val (ranked, knownRows, inferredKind) = localSuggestions(dispatcher, source, text)
        
        
        
        val future = try { boundedCommandCompletion(dispatcher.getCompletionSuggestions(parsed)) }
            catch (_: com.mojang.brigadier.exceptions.CommandSyntaxException) { null }
        accept(ranked)
        future?.whenComplete { suggestions, error ->
            client.execute {
                if (error != null || suggestions == null || client.connection !== connection || connection.commands !== dispatcher) return@execute
                val remote = suggestions.list.asSequence().take(LIMIT).map { s ->
                    val known = knownRows[s.text]
                    CommandSuggestion(s.text, known?.title ?: s.text, s.tooltip?.string ?: known?.detail.orEmpty(),
                        s.range.start, s.range.end, known?.kind ?: inferredKind, known?.iconId)
                }.filter { it.start >= 1 && it.end in it.start..text.length }.toList()
                val merged = (remote + ranked).distinctBy { Triple(it.start, it.end, it.value) }.take(LIMIT)
                if (merged != ranked) accept(merged)
            }
        }
    }

    private fun <A : ArgumentType<*>> argumentMetadata(type: A): JsonObject {
        if (!ArgumentTypeInfos.isClassRecognized(type.javaClass)) return JsonObject()
        val info = ArgumentTypeInfos.byClass(type)
        return serializeMetadata(info, type)
    }

    private fun <A : ArgumentType<*>, T : net.minecraft.commands.synchronization.ArgumentTypeInfo.Template<A>> serializeMetadata(
        info: net.minecraft.commands.synchronization.ArgumentTypeInfo<A, T>, type: A
    ): JsonObject = JsonObject().also { info.serializeToJson(info.unpack(type), it) }

    private fun kind(type: ArgumentType<*>, registry: String?, provider: String?, name: String): SelectionKind? {
        if (registry != null) return selectionRegistries.entries.firstOrNull { "minecraft:${it.value}" == registry }?.key ?: SelectionKind.REGISTRY
        return when (type) {
            is ItemArgument, is ItemPredicateArgument -> SelectionKind.ITEMS
            is BlockStateArgument, is BlockPredicateArgument -> SelectionKind.BLOCKS
            is EntityArgument -> SelectionKind.ENTITIES
            is GameProfileArgument -> SelectionKind.PLAYERS
            is GameModeArgument -> SelectionKind.GAMEMODES
            is DimensionArgument -> SelectionKind.DIMENSIONS
            is ParticleArgument -> SelectionKind.PARTICLES
            is TimeArgument -> SelectionKind.TIMES
            else -> when (provider) {
                "minecraft:available_sounds" -> SelectionKind.SOUNDS
                "minecraft:summonable_entities" -> SelectionKind.ENTITY_TYPES
                
                else -> when (name) {
                    "ip" -> SelectionKind.IP
                    else -> null
                }
            }
        }
    }

    private fun literalKind(literals: List<String>): SelectionKind? = when {
        literals.containsAll(listOf("clear", "rain", "thunder")) -> SelectionKind.WEATHERS
        literals.containsAll(Difficulty.values().map { it.serializedName }) -> SelectionKind.DIFFICULTIES
        literals.containsAll(listOf("day", "noon", "night", "midnight")) -> SelectionKind.TIMES
        else -> null
    }
}
