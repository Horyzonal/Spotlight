package dev.spotlight.ui

internal object CommandHistory {
    private const val LIMIT = 100
    private val commands = ArrayDeque<String>()

    fun record(query: String) {
        if (query.isBlank()) return
        commands.addFirst(query)
        if (commands.size > LIMIT) commands.removeLast()
    }

    fun snapshot(): List<String> = commands.toList()
}

internal class HistoryNavigation {
    private var entries: List<String>? = null
    private var cursor = -1
    private var draft = ""
    private var resultsMode = false

    fun edited() {
        entries = null
        cursor = -1
    }

    fun up(query: String, history: () -> List<String>): String? {
        if (resultsMode) return null
        if (entries == null) {
            entries = history()
            draft = query
        }
        val values = entries!!
        if (values.isEmpty()) return query
        cursor = (cursor + 1).coerceAtMost(values.lastIndex)
        return values[cursor]
    }

    fun down(): String? {
        resultsMode = true
        val restore = if (cursor >= 0) draft else null
        edited()
        return restore
    }
}
