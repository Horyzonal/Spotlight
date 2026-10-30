package dev.spotlight.ui


internal fun selectorSpan(text: String, cursor: Int): IntRange? {
    var quote: Char? = null
    var escaped = false
    var start = -1
    var depth = 0
    for (i in text.indices) {
        val c = text[i]
        if (quote != null) {
            if (escaped) escaped = false
            else if (c == '\\') escaped = true
            else if (c == quote) quote = null
        } else when {
            c == '"' || c == '\'' -> quote = c
            start < 0 && c == '@' && (i == 0 || text[i - 1].isWhitespace()) && text.getOrNull(i + 1)?.let { it in "seapr" } == true -> start = i
            start >= 0 && c == '[' -> depth++
            start >= 0 && c == ']' -> depth--
            start >= 0 && c.isWhitespace() && depth == 0 -> {
                if (cursor in start..i) return start until i
                start = -1
            }
        }
    }
    return if (start >= 0 && cursor in start..text.length) start until text.length else null
}

internal data class SelectorOption(val key: String, val raw: String)

internal data class SelectorModel(val base: String, val options: List<SelectorOption>) {
    fun text() = base + if (options.isEmpty()) "" else options.joinToString(",", "[", "]") { it.raw }
    fun set(key: String, value: String): SelectorModel {
        require(key.matches(Regex("[a-z_]+"))) { "Invalid selector flag" }
        require(value.isNotBlank()) { "Enter flag value" }

        val parsed = parse("$base[$key=$value]")
        require(parsed.options.size == 1) { "Enter one flag value" }
        val index = options.indexOfFirst { it.key == key }
        return copy(options = if (index < 0) options + parsed.options.single()
            else options.toMutableList().also { it[index] = parsed.options.single() })
    }

    companion object {
        fun parse(text: String): SelectorModel {
            require(text.none { it.isISOControl() }) { "Control characters not allowed" }
            require(text.length >= 2 && text[0] == '@' && text[1] in "seapr") { "Choose vanilla selector" }
            if (text.length == 2) return SelectorModel(text, emptyList())
            require(text[2] == '[' && text.last() == ']') { "Close selector brackets" }
            val parts = mutableListOf<SelectorOption>()
            val stack = ArrayDeque<Char>()
            var quote: Char? = null
            var escaped = false
            var start = 3
            for (i in 3 until text.lastIndex) {
                val c = text[i]
                if (quote != null) {
                    if (escaped) escaped = false
                    else if (c == '\\') escaped = true
                    else if (c == quote) quote = null
                } else when (c) {
                    '"', '\'' -> quote = c
                    '[', '{', '(' -> stack.addLast(c)
                    ']', '}', ')' -> require(stack.removeLastOrNull() == when (c) { ']' -> '['; '}' -> '{'; else -> '(' }) { "Unbalanced selector value" }
                    ',' -> if (stack.isEmpty()) {
                        parts += option(text.substring(start, i))
                        start = i + 1
                    }
                    '\\' -> error("Escape only inside quoted values")
                }
            }
            require(quote == null && !escaped && stack.isEmpty()) { "Unbalanced selector value" }
            if (start < text.lastIndex) parts += option(text.substring(start, text.lastIndex))
            else require(parts.isEmpty()) { "Trailing comma" }
            return SelectorModel(text.take(2), parts)
        }

        private fun option(raw: String): SelectorOption {
            val key = raw.substringBefore('=').trim()
            require('=' in raw && key.matches(Regex("[a-z_]+")) && raw.substringAfter('=').isNotBlank()) { "Expected flag=value" }
            return SelectorOption(key, raw)
        }
    }
}
