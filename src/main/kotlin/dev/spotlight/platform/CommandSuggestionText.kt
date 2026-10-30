package dev.spotlight.platform

enum class SelectionKind {
    ITEMS, ADVANCEMENTS, ATTRIBUTES, ENTITY_TYPES, PLAYERS, DAMAGE_TYPES, DIFFICULTIES,
    EFFECTS, ENCHANTMENTS, BIOMES, GAMEMODES, GAMERULES, STRUCTURES, IP, PARTICLES,
    SOUNDS, RECIPES, TAGS, TIMES, WEATHERS, BLOCKS, ENTITIES, DIMENSIONS, FUNCTIONS,
    FEATURES, LOOT_TABLES, PREDICATES, ITEM_MODIFIERS, REGISTRY
}

data class CommandSuggestion(
    val value: String,
    val title: String,
    val detail: String = "",
    val start: Int,
    val end: Int,
    val kind: SelectionKind? = null,
    val iconId: String? = null
)

internal fun validatedSlash(text: String): String {
    require(validCommandText(text)) { "Command must start with /, contain no control characters, and fit 32767 characters after /" }
    return text.substring(1).trimEnd(' ').also {
        require(it.isNotBlank() && !it.startsWith('/')) { "Enter one slash command" }
    }
}

internal fun validCommandText(text: String): Boolean =
    text.startsWith('/') && text.length in 2..32768 && text.none { it.isISOControl() }

internal fun commandAlias(value: String): String? = when (value.lowercase()) {
    "me", "m" -> "@s"
    "everyone" -> "@a"
    "random" -> "@r"
    "nearest" -> "@p"
    "entities" -> "@e"
    else -> null
}

internal fun replaceCommandSuggestion(text: String, suggestion: CommandSuggestion): String {
    require(text.startsWith('/')) { "Command must begin with /" }
    require(suggestion.start in 1..text.length && suggestion.end in suggestion.start..text.length) {
        "Invalid command replacement span: ${suggestion.start}..${suggestion.end}"
    }
    return text.replaceRange(suggestion.start, suggestion.end, suggestion.value)
}


internal fun commandTokenStart(text: String): Int {
    require(text.startsWith('/')) { "Command must begin with /" }
    var start = 1
    var quote: Char? = null
    var escaped = false
    val brackets = ArrayDeque<Char>()
    for (i in 1 until text.length) {
        val c = text[i]
        if (quote != null) {
            if (escaped) escaped = false
            else if (c == '\\') escaped = true
            else if (c == quote) quote = null
        } else when (c) {
            '"', '\'' -> quote = c
            '[', '{', '(' -> brackets.addLast(c)
            ']', '}', ')' -> if (brackets.lastOrNull() == when (c) { ']' -> '['; '}' -> '{'; else -> '(' }) brackets.removeLast()
            else -> if (c == ' ' && brackets.isEmpty()) start = i + 1
        }
    }
    return start
}

internal fun commandMatch(query: String, value: String): Int {
    val q = query.lowercase()
    val v = value.lowercase()
    if (q.isEmpty() || v == q) return 0
    if (v.startsWith(q) || v.substringAfter(':').startsWith(q)) return 1
    if (q in v) return 2
    var cursor = 0
    for (c in v) if (cursor < q.length && c == q[cursor]) cursor++
    if (cursor == q.length) return 3
    
    val word = v.substringAfter(':')
    if (kotlin.math.abs(q.length - word.length) > 1) return Int.MAX_VALUE
    var i = 0
    var j = 0
    var edits = 0
    while (i < q.length && j < word.length) {
        if (q[i] == word[j]) { i++; j++; continue }
        if (++edits > 1) return Int.MAX_VALUE
        when {
            q.length > word.length -> i++
            q.length < word.length -> j++
            else -> { i++; j++ }
        }
    }
    return if (edits + (q.length - i) + (word.length - j) <= 1) 4 else Int.MAX_VALUE
}


internal fun validCommandIp(value: String): Boolean {
    if (':' in value) {
        if (value.any { it !in "0123456789abcdefABCDEF:." }) return false
        return try { java.net.InetAddress.getByName(value); true } catch (_: java.net.UnknownHostException) { false }
    }
    val parts = value.split('.')
    return parts.size == 4 && parts.all { part ->
        part.length in 1..3 && (part.length == 1 || part.first() != '0') &&
            part.all { it in '0'..'9' } && (part.toIntOrNull() ?: 256) <= 255
    }
}
