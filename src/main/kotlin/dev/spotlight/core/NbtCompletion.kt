package dev.spotlight.core

/** Value-free shape. Choices must come from booleans or native enum serialization, never arbitrary strings. */
data class NbtShape(
    val type: String,
    val keys: Map<String, NbtShape> = emptyMap(),
    val element: NbtShape? = null,
    val choices: List<String> = emptyList()
)

data class NbtCandidate(
    val value: String, val start: Int, val end: Int,
    val type: String, val parentTypes: List<String>, val detail: String
)

object NbtCompletion {
    const val MAX_INPUT = 8192
    const val MAX_DEPTH = 32
    const val MAX_CANDIDATES = 128

    fun complete(text: String, start: Int, shape: NbtShape, cursor: Int = text.length, path: Boolean = false): List<NbtCandidate> {
        if (text.length > MAX_INPUT || text.toByteArray(Charsets.UTF_8).size > MAX_INPUT || start !in 0..cursor || cursor > text.length) return emptyList()
        return Parser(text, cursor).run(start, shape, path)
    }

    private class Parser(val text: String, val cursor: Int) {
        var pos = 0
        var result = emptyList<NbtCandidate>()
        fun space() { while (pos < cursor && text[pos].isWhitespace()) pos++ }
        fun peek(): Char? = text.getOrNull(pos)?.takeIf { pos < cursor }
        fun quoted(): Boolean {
            val quote = text[pos++]
            while (pos < cursor) {
                val c = text[pos++]
                if (c == '\\') { if (pos < cursor) pos++ else return false }
                else if (c == quote) return true
            }
            return false
        }
        fun decode(raw: String): String {
            if (raw.firstOrNull() !in listOf('\'', '"')) return raw
            val quote = raw.first()
            val slashCount = raw.dropLast(1).takeLastWhile { it == '\\' }.length
                        val body = raw.drop(1).let { if (raw.length > 1 && it.endsWith(quote) && slashCount % 2 == 0) it.dropLast(1) else it }
            return body.replace("\\$quote", "$quote").replace("\\\\", "\\")
        }
        fun keyText(key: String, quote: Char?, path: Boolean): String {
            if (quote == null && key.isNotEmpty() && key.all { it.isLetterOrDigit() || it in "_+-" || (!path && it == '.') }) return key
            val q = quote ?: '"'
            return q + key.replace("\\", "\\\\").replace("$q", "\\$q") + q
        }
        fun offer(shape: NbtShape, parents: List<String>, begin: Int, keys: Boolean, path: Boolean = false) {
            val raw = text.substring(begin, cursor)
            val prefix = if (keys) decode(raw) else raw
            val quote = raw.firstOrNull()?.takeIf { it == '\'' || it == '"' }
            var end = cursor
            if (quote != null) {
                while (end < text.length) {
                    val c = text[end++]
                    if (c == '\\' && end < text.length) end++ else if (c == quote) break
                }
            } else {
                while (end < text.length && !text[end].isWhitespace() && text[end] !in if (path) ".[]{}:," else ":,{}[]=") end++
            }
            val after = text.substring(end).trimStart()
            result = (if (keys) shape.keys.asSequence().map { (key, child) ->
                Triple(key, keyText(key, quote, path) + if (!path && !after.startsWith(':')) ":" else "", child.type)
            } else shape.choices.asSequence().map { Triple(it, it, shape.type) })
                .filter { it.first.startsWith(prefix, ignoreCase = true) }
                .take(MAX_CANDIDATES)
                .map { (_, value, type) -> NbtCandidate(value, begin, end, type, parents, "Observed $type · ${parents.joinToString(" / ")}") }.toList()
        }
        fun run(start: Int, shape: NbtShape, path: Boolean): List<NbtCandidate> {
            pos = start
            if (path) path(shape) else value(shape, emptyList(), 0)
            return result
        }
        fun value(shape: NbtShape?, parents: List<String>, depth: Int): Boolean {
            if (depth > MAX_DEPTH) return false
            space()
            if (pos == cursor) { if (shape != null) offer(shape, parents, pos, false); return false }
            when (peek()) {
                '{' -> {
                    pos++
                    space()
                    if (peek() == '}') { pos++; return true }
                    while (pos <= cursor) {
                        space()
                        val begin = pos
                        if (pos == cursor) { if (shape != null) offer(shape, parents + shape.type, begin, true); return false }
                        if (peek() == '\'' || peek() == '"') {
                            if (!quoted()) { if (shape != null) offer(shape, parents + shape.type, begin, true); return false }
                        } else {
                            while (pos < cursor && !text[pos].isWhitespace() && text[pos] !in ":,{}[]") pos++
                        }
                        val key = decode(text.substring(begin, pos))
                        space()
                        if (pos == cursor) { if (shape != null) offer(shape, parents + shape.type, begin, true); return false }
                        if (peek() != ':') return false
                        pos++
                        if (!value(shape?.keys?.get(key), parents + (shape?.type ?: "compound"), depth + 1)) return false
                        space()
                        when (peek()) {
                            '}' -> { pos++; return true }
                            ',' -> pos++
                            else -> return false
                        }
                    }
                }
                '[' -> {
                    pos++
                    space()
                    val typed = peek() in listOf('B', 'I', 'L', 'b', 'i', 'l') && pos + 1 < cursor && text[pos + 1] == ';'
                    if (typed) pos += 2
                    space()
                    if (peek() == ']') { pos++; return true }
                    while (pos <= cursor) {
                        if (!value(if (typed) null else shape?.element, parents + (shape?.type ?: "list"), depth + 1)) return false
                        space()
                        when (peek()) {
                            ']' -> { pos++; return true }
                            ',' -> pos++
                            else -> return false
                        }
                    }
                }
                '\'', '"' -> {
                                    val begin = pos
                                    val closed = quoted()
                                    if (!closed && shape != null) offer(shape, parents, begin, false)
                                    return closed
                                }
                else -> {
                    val begin = pos
                    while (pos < cursor && !text[pos].isWhitespace() && text[pos] !in ",]}{") pos++
                    if (pos == cursor) { if (shape != null) offer(shape, parents, begin, false); return false }
                    return pos > begin
                }
            }
            return false
        }
        fun path(root: NbtShape) {
            var shape: NbtShape? = root
            val parents = mutableListOf<String>()
            while (pos <= cursor && parents.size <= MAX_DEPTH) {
                val current = shape ?: return
                val begin = pos
                if (pos == cursor) { offer(current, parents + current.type, begin, true, true); return }
                if (peek() == '{') { value(current, parents, parents.size); return }
                if (peek() == '\'' || peek() == '"') {
                    if (!quoted()) { offer(current, parents + current.type, begin, true, true); return }
                } else while (pos < cursor && text[pos] !in ".[{" && !text[pos].isWhitespace()) pos++
                if (pos == cursor) { offer(current, parents + current.type, begin, true, true); return }
                if (pos > begin) {
                    parents += current.type
                    shape = current.keys[decode(text.substring(begin, pos))] ?: return
                }
                while (peek() == '[') {
                    pos++
                    shape = shape?.element ?: return
                    if (peek() == '{') { value(shape, parents + "list", parents.size); return }
                    if (peek() == '-') pos++
                    while (peek()?.isDigit() == true) pos++
                    if (peek() != ']') return
                    pos++
                }
                if (peek() == '{') { value(shape, parents, parents.size); return }
                if (peek() != '.') return
                pos++
            }
        }
    }
}
