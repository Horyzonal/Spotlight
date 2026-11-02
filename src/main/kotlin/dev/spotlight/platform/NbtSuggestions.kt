package dev.spotlight.platform

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.ArgumentCommandNode
import dev.spotlight.core.NbtCompletion
import dev.spotlight.core.NbtShape
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.commands.arguments.ComponentArgument
import net.minecraft.commands.arguments.CompoundTagArgument
import net.minecraft.commands.arguments.NbtPathArgument
import net.minecraft.commands.arguments.item.ItemArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.*
import net.minecraft.resources.RegistryOps
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.Entity

import net.minecraft.commands.SharedSuggestionProvider

object NbtSuggestions {
    private var cachedSource: Any? = null
    private var cachedLevel: Any? = null
    private var cachedTick = Long.MIN_VALUE
    private var cachedText = ""
    private var cachedRows = emptyList<CommandSuggestion>()

    fun request(dispatcher: CommandDispatcher<SharedSuggestionProvider>, source: ClientSuggestionProvider, text: String): List<CommandSuggestion> {
        val client = Minecraft.getInstance()
        val level = client.level ?: return emptyList()
        if (!client.isSameThread || text.length > NbtCompletion.MAX_INPUT || text.toByteArray(Charsets.UTF_8).size > NbtCompletion.MAX_INPUT || !text.startsWith('/') || '\n' in text || '\r' in text) return emptyList()
        if (cachedSource === source && cachedLevel === level && cachedTick == level.gameTime && cachedText == text) return cachedRows
        val rows = try { collect(dispatcher, source, text) } catch (_: RuntimeException) { emptyList() }
        cachedSource = source
        cachedLevel = level
        cachedTick = level.gameTime
        cachedText = text
        cachedRows = rows
        return rows
    }

    private fun collect(dispatcher: CommandDispatcher<SharedSuggestionProvider>, source: ClientSuggestionProvider, text: String): List<CommandSuggestion> {
        for (start in (text.length downTo 1).asSequence().filter { text[it - 1].isWhitespace() }.take(128)) {
            val types = commandSuggestionChildren(dispatcher, source, text, start)
                .filterIsInstance<ArgumentCommandNode<ClientSuggestionProvider, *>>().map { it.type }
            if (types.any { it is ItemArgument }) return item(text, start)
            if (types.none { it is CompoundTagArgument || it is NbtPathArgument }) continue
            val shape = target(text.substring(0, start).trim().split(Regex("\\s+"))) ?: return emptyList()
            return rows(NbtCompletion.complete(text, start, shape, path = types.any { it is NbtPathArgument }))
        }
        return emptyList()
    }

    private fun rows(candidates: List<dev.spotlight.core.NbtCandidate>) = candidates.map {
        CommandSuggestion(it.value, it.value, it.detail, it.start, it.end)
    }

    private fun item(text: String, start: Int): List<CommandSuggestion> {
        val client = Minecraft.getInstance()
        val level = client.level ?: return emptyList()
        val open = text.indexOf('{', start)
        if (open < 0) return emptyList()
        val id = text.substring(start, open)
        val player = client.player ?: return emptyList()
        val stacks = (0 until player.inventory.containerSize).map { player.inventory.getItem(it) }
        val stack = stacks.firstOrNull { BuiltInRegistries.ITEM.getKey(it.item).toString().let { key -> key == id || key.removePrefix("minecraft:") == id } }
            ?: return emptyList()
        return rows(NbtCompletion.complete(text, open, shape(stack.tag ?: CompoundTag())))
    }

    private fun target(tokens: List<String>): NbtShape? {
        val client = Minecraft.getInstance()
        val level = client.level ?: return null
        val command = tokens.firstOrNull()?.removePrefix("/")?.removePrefix("minecraft:")
        if (command == "summon") {
            val id = tokens.getOrNull(1) ?: return null
            val entity = level.entitiesForRendering().asSequence().firstOrNull {
                BuiltInRegistries.ENTITY_TYPE.getKey(it.type).toString().let { key -> key == id || key.removePrefix("minecraft:") == id }
            } ?: return null
            return entityShape(entity)
        }
        if (command != "data") return null
        val targetIndex = tokens.indexOfLast { it == "entity" || it == "block" }
        if (targetIndex < 0) return null
        if (tokens[targetIndex] == "entity") {
            val selector = tokens.getOrNull(targetIndex + 1) ?: return null
            val entity = if (selector == "@s") client.player else level.entitiesForRendering().asSequence().firstOrNull {
                it.stringUUID == selector || (it is net.minecraft.world.entity.player.Player && it.gameProfile.name == selector)
            }
            return entity?.let { dataShape(entityShape(it), tokens, targetIndex + 2) }
        }
        val player = client.player ?: return null
        val coords = tokens.drop(targetIndex + 1).take(3)
        if (coords.size != 3) return null
        val base = listOf(player.x, player.y, player.z)
        val xyz = coords.mapIndexed { i, raw ->
            val value = if (raw.startsWith('~')) base[i] + (raw.drop(1).ifEmpty { "0" }.toDoubleOrNull() ?: return null)
                else raw.toDoubleOrNull() ?: return null
            if (!value.isFinite() || value < -30000000 || value > 30000000) return null
            kotlin.math.floor(value).toInt()
        }
        val pos = BlockPos(xyz[0], xyz[1], xyz[2])
        if (!level.hasChunk(pos.x shr 4, pos.z shr 4)) return null
        return level.getBlockEntity(pos)?.saveWithFullMetadata()?.let { dataShape(shape(it), tokens, targetIndex + 4) }
    }

    private fun dataShape(root: NbtShape, tokens: List<String>, afterTarget: Int): NbtShape? {
        if (tokens.size <= afterTarget) return root
        if (tokens.getOrNull(1) != "modify" || tokens.lastOrNull() != "value") return null
        val path = tokens[afterTarget]
        if (!path.matches(Regex("[A-Za-z0-9_+-]+(?:\\.[A-Za-z0-9_+-]+)*"))) return null
        var selected = root
        for (key in path.split('.')) selected = selected.keys[key] ?: return null
        return if (tokens.getOrNull(afterTarget + 1) in listOf("append", "prepend", "insert")) selected.element else selected
    }

    private fun entityShape(entity: Entity): NbtShape {
        return shape(entity.saveWithoutId(CompoundTag()))
    }

    private fun shape(tag: Tag): NbtShape {
        var budget = 1024
        fun visit(value: Tag, depth: Int): NbtShape {
            val type = when (value) {
                is CompoundTag -> "compound"
                is ListTag -> "list"
                is ByteArrayTag -> "byte array"
                is IntArrayTag -> "int array"
                is LongArrayTag -> "long array"
                is StringTag -> "string"
                is ByteTag -> "byte"
                is ShortTag -> "short"
                is IntTag -> "int"
                is LongTag -> "long"
                is FloatTag -> "float"
                is DoubleTag -> "double"
                else -> "tag"
            }
            if (depth >= NbtCompletion.MAX_DEPTH || --budget < 0) return NbtShape(type)
            return NbtShape(type,
                keys = if (value is CompoundTag) value.allKeys.asSequence().take(NbtCompletion.MAX_CANDIDATES)
                    .takeWhile { budget > 0 }.associate { it to visit(requireNotNull(value.get(it)), depth + 1) } else emptyMap(),
                element = if (value is ListTag && !value.isEmpty()) visit(value[0], depth + 1) else null)
        }
        return visit(tag, 0)
    }
}
