package dev.spotlight.core

object MacroCompletion {
    data class Match(val start: Int, val end: Int, val options: List<String>)
    data class Edit(val text: String, val cursor: Int)

    fun find(text: String, cursor: Int, selectionEnd: Int = cursor): Match? {
        if (cursor !in 0..text.length || selectionEnd != cursor) return null
        var start = -1
        var quote: Char? = null
        var escaped = false
        for (index in 0 until cursor) {
            val char = text[index]
            if (escaped) { escaped = false; continue }
            
            if (quote != null && char == '\\') { escaped = true; continue }
            if (char == quote) { quote = null; continue }
            if (quote == null && (char == '"' || char == '\'')) { quote = char; continue }
            if (char == '{') start = index
            if (char == '}') start = -1
        }
        if (start < 0) return null
        val tail = text.substring(cursor).takeWhile { it.isLetterOrDigit() || it in "_=:-" }
        if (':' in tail && !text.substring(start + 1, cursor).startsWith("Argument=", ignoreCase = true)) return null
        if (cursor == start + 1 && (text.getOrNull(cursor) == '"' || text.getOrNull(cursor) == '\'')) return null
        val query = text.substring(start + 1, cursor)
        if (!Regex("[\\p{L}\\p{N}_=:-]*").matches(query)) return null
        val canonical = MacroTemplates.placeholderOptions()
        val options = canonical.toMutableList()
        if (query.startsWith("Argument=", ignoreCase = true)) {
            val name = query.substringAfter('=').substringBefore(':')
            if (Regex("[\\p{L}_][\\p{L}\\p{N}_-]{0,63}").matches(name)) {
                options.addAll(0, MacroArgumentType.entries.map { "{Argument=$name:${it.name.lowercase()}}" })
            }
        } else if (':' in query || '=' in query) return null
        val matches = options.distinct().filter { option ->
            val candidate = option.drop(1).dropLast(1)
            if (':' in query || '=' in query) candidate.startsWith(query, ignoreCase = true)
            else {
                var offset = 0
                query.all { char ->
                    val next = candidate.indexOf(char, offset, ignoreCase = true)
                    if (next < 0) false else { offset = next + 1; true }
                }
            }
        }
        if (matches.isEmpty()) return null
        var end = cursor
        while (end < text.length && (text[end].isLetterOrDigit() || text[end] in "_=:-")) end++
        if (end < text.length && text[end] == '}') end++
        return Match(start, end, matches)
    }

    fun ghostSuffix(text: String, cursor: Int, option: String, selectionEnd: Int = cursor): String? {
        val match = find(text, cursor, selectionEnd) ?: return null
        if (option !in match.options || match.end != cursor) return null
        val prefix = text.substring(match.start, cursor)
        return option.takeIf { it.startsWith(prefix, ignoreCase = true) }
            ?.substring(prefix.length)?.takeIf { it.isNotEmpty() }
    }

    fun accept(text: String, cursor: Int, option: String, selectionEnd: Int = cursor): Edit? {
        val match = find(text, cursor, selectionEnd) ?: return null
        if (option !in match.options) return null
        val prefix = text.substring(match.start, cursor)
        val replacement = if (option.startsWith(prefix, ignoreCase = true)) prefix + option.substring(prefix.length) else option
        val result = text.replaceRange(match.start, match.end, replacement)
        if (result.length > 32768) return null
        return Edit(result, match.start + replacement.length)
    }
}
