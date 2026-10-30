package dev.spotlight.ui

internal enum class PaletteStage { ROOT, ITEMS, ACTIONS, QUANTITY, NUMBER, ENTITY, BLOCK, LOCATION, LOCATIONS }

internal data class GiveArguments(val target: String, val item: String, val count: String)


internal fun parseGive(text: String): GiveArguments? {
    if (!text.startsWith("/give") || (text.length > 5 && !text[5].isWhitespace())) return null
    val rest = text.removePrefix("/give").trimStart()
    if (rest.isEmpty()) return GiveArguments("@s", "", "1")
    var depth = 0
    var quote: Char? = null
    var escaped = false
    val tokens = mutableListOf<String>()
    var start = 0
    for ((index, char) in rest.withIndex()) {
        if (escaped) { escaped = false; continue }
        if (char == '\\') { escaped = true; continue }
        if (quote != null) { if (char == quote) quote = null; continue }
        if (char == '"' || char == '\'') quote = char
        else if (char == '[' || char == '{') depth++
        else if (char == ']' || char == '}') depth--
        else if (char.isWhitespace() && depth == 0) {
            if (index > start) tokens += rest.substring(start, index)
            start = index + 1
        }
    }
    if (start < rest.length) tokens += rest.substring(start)

    val explicit = tokens.firstOrNull()?.startsWith('@') == true ||
        (tokens.size >= 2 && !tokens[1].matches(Regex("[+-]?[0-9]+")))
    val target = if (explicit) tokens.first() else "@s"
    val args = if (explicit) tokens.drop(1) else tokens
    if (depth != 0 || quote != null || args.size > 2) return null

    if (args.firstOrNull()?.any { it in "[{" } == true) return null
    return GiveArguments(target, args.getOrNull(0).orEmpty(), args.getOrNull(1) ?: "1")
}

internal data class PaletteState(
    val stage: PaletteStage = PaletteStage.ROOT,
    val commandId: String? = null,
    val itemId: String? = null,
    var query: String = "",
    val give: GiveArguments? = null,
)

internal class PaletteFlow {
    var state = PaletteState()
        private set
    private val history = ArrayDeque<PaletteState>()

    fun chooseItem(id: String, needsQuantity: Boolean) {
        require(id.isNotBlank())
        push(state.copy(
            stage = if (state.commandId == null && state.give == null) PaletteStage.ACTIONS else if (needsQuantity) PaletteStage.QUANTITY else state.stage,
            itemId = id,
            query = if (needsQuantity && (state.commandId != null || state.give != null)) state.give?.count ?: "1" else "",
        ))
    }

    fun chooseCommand(id: String, needsItem: Boolean, needsQuantity: Boolean) {
        require(id.isNotBlank())
        push(state.copy(
            stage = if (needsItem && state.itemId == null) PaletteStage.ITEMS else if (needsQuantity) PaletteStage.QUANTITY else state.stage,
            commandId = id,
            query = if (needsQuantity && state.itemId != null) "1" else "",
        ))
    }

    fun start(stage: PaletteStage, commandId: String? = null, query: String = "") =
        push(PaletteState(stage, commandId, query = query))

    fun startGive(arguments: GiveArguments) = push(PaletteState(PaletteStage.ITEMS, query = arguments.item, give = arguments))

    private fun push(next: PaletteState) {
        history.addLast(state.copy())
        state = next
    }

    fun back(): Boolean {
        if (history.isEmpty()) return false
        state = history.removeLast()
        return true
    }
}
