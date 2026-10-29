package dev.spotlight.platform

import com.google.gson.JsonObject
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.arguments.*
import net.minecraft.commands.arguments.blocks.BlockStateArgument
import net.minecraft.commands.arguments.item.ItemArgument
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


internal fun <S> commandSuggestionChildren(
    dispatcher: CommandDispatcher<S>, source: S, text: String, start: Int
): List<CommandNode<S>> {
    val reader = StringReader(text.substring(0, start)).also { it.cursor = 1 }
    val parsed = dispatcher.parse(reader, source)
    if (parsed.reader.remaining.any { it != ' ' }) return emptyList()
    val context = try { parsed.context.findSuggestionContext(start) } catch (_: IllegalStateException) { return emptyList() }
    val parent = context.parent
    if (!parent.canUse(source)) return emptyList()
    val target = parent.redirect ?: parent
    if (!target.canUse(source)) return emptyList()
    return target.children.filter { it.canUse(source) }
}

internal fun <S> expandCommandAlias(
    dispatcher: CommandDispatcher<S>, source: S, text: String, cursor: Int,
    replacement: (ArgumentType<*>, String) -> String?
): Pair<String, Int>? {
    if (!validCommandText(text) || cursor !in 2..text.length || text[cursor - 1] != ' ') return null
    if (cursor < text.length && text[cursor] != ' ') return null
    val end = cursor - 1
    val start = commandTokenStart(text.substring(0, end))
    val alias = commandAlias(text.substring(start, end)) ?: return null
    val children = commandSuggestionChildren(dispatcher, source, text, start)
    if (children.any { it is LiteralCommandNode && it.literal == text.substring(start, end) }) return null
    val values = children.filterIsInstance<ArgumentCommandNode<S, *>>().mapNotNull { node ->
        val value = replacement(node.type, alias) ?: return@mapNotNull null
        val reader = StringReader(value)
        try { node.type.parse(reader) } catch (_: com.mojang.brigadier.exceptions.CommandSyntaxException) { return@mapNotNull null }
        value.takeUnless { reader.canRead() }
    }.distinct()
    val value = values.singleOrNull() ?: return null
    val expanded = text.replaceRange(start, end, value)
    if (!validCommandText(expanded) || expanded == text) return null
    return expanded to cursor + value.length - (end - start)
}


internal fun singleTokenCommandArgument(type: ArgumentType<*>): Boolean = when (type) {
    is StringArgumentType -> type.type == StringArgumentType.StringType.SINGLE_WORD
    is BoolArgumentType, is IntegerArgumentType, is LongArgumentType, is FloatArgumentType, is DoubleArgumentType,
    is EntityArgument, is GameProfileArgument, is ResourceArgument<*>, is ResourceKeyArgument<*>,
    is ResourceOrTagArgument<*>, is ResourceOrTagKeyArgument<*>, is ResourceSelectorArgument<*>,
    is ItemArgument, is BlockStateArgument, is GameModeArgument, is DimensionArgument, is TimeArgument -> true
    else -> false
}

private fun plainCommandToken(value: String): Boolean = value.isNotEmpty() &&
    value.none { it.isWhitespace() || it.isISOControl() || it in "@\"'[]{}()\\" }

private fun parsesCommandToken(type: ArgumentType<*>, value: String): Boolean {
    val reader = StringReader(value)
    return try {
        type.parse(reader)
        !reader.canRead()
    } catch (_: com.mojang.brigadier.exceptions.CommandSyntaxException) { false }
}

internal fun <S> completeCommandOnSpace(
    dispatcher: CommandDispatcher<S>, source: S, text: String, cursor: Int,
    candidates: List<CommandSuggestion> = emptyList(),
    localCandidates: (String) -> List<CommandSuggestion> = { emptyList() }
): Pair<String, Int>? {
    if (!validCommandText(text) || cursor !in 2..text.length || text[cursor - 1] != ' ') return null
    val end = cursor - 1
    val queryText = text.substring(0, end)
    val start = commandTokenStart(queryText)
    if (start <= 1) return null 
    val query = queryText.substring(start)
    if (!plainCommandToken(query)) return null
    val children = commandSuggestionChildren(dispatcher, source, queryText, start)
    val literals = children.filterIsInstance<LiteralCommandNode<S>>()
    val arguments = children.filterIsInstance<ArgumentCommandNode<S, *>>().filter { singleTokenCommandArgument(it.type) }
    if (literals.any { it.literal == query }) return null
    if (arguments.any {
        val type = it.type
        (type is IntegerArgumentType || type is LongArgumentType || type is FloatArgumentType ||
            type is DoubleArgumentType || type is TimeArgument) && parsesCommandToken(type, query)
    }) return null
    if (literals.isEmpty() && arguments.isEmpty()) return null
    val rows = candidates + localCandidates(queryText)
    val value = rows.asSequence().filter {
        it.start == start && it.end == end && plainCommandToken(it.value) &&
            commandMatch(query, it.value) != Int.MAX_VALUE &&
            (literals.any { node -> node.literal == it.value } || arguments.any { node -> parsesCommandToken(node.type, it.value) })
    }.minByOrNull { commandMatch(query, it.value) }?.value ?: return null
    if (value == query) return null
    val completed = text.replaceRange(start, end, value)
    return if (validCommandText(completed)) completed to cursor + value.length - query.length else null
}

internal fun <S> commandArgumentHint(dispatcher: CommandDispatcher<S>, source: S, text: String): String? {
    if (!validCommandText(text)) return null
    val children = commandSuggestionChildren(dispatcher, source, text, commandTokenStart(text))
    return if (children.any {
        val type = (it as? ArgumentCommandNode<S, *>)?.type
        type is IntegerArgumentType || type is LongArgumentType || type is FloatArgumentType || type is DoubleArgumentType
    }) "Enter a number" else null
}


internal fun commandRegistryId(metadata: JsonObject): String? = metadata.get("registry")?.let {
    if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString else null
}


internal fun <T> boundedCommandCompletion(future: CompletableFuture<T>, timeoutMillis: Long = 2000): CompletableFuture<T?> =
    future.handle { result, error -> if (error == null) result else null }.completeOnTimeout(null, timeoutMillis, TimeUnit.MILLISECONDS)
