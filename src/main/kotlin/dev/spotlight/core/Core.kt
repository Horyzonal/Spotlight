package dev.spotlight.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Locale
import java.util.Properties

data class Entry(val id: String, val title: String, val detail: String = "", val itemId: String? = null)

object Fuzzy {
    fun search(
        query: String,
        entries: List<Entry>,
        favorites: Set<String> = emptySet(),
        recent: List<String> = emptyList(),
        limit: Int = 60,
    ): List<Entry> {
        if (limit <= 0) return emptyList()
        val words = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val history = recent.withIndex().reversed().associate { it.value to it.index }
        return entries.withIndex().mapNotNull { indexed ->
            val entry = indexed.value
            val fields = listOf(entry.title, entry.id, entry.itemId.orEmpty(), entry.detail)
                .filter { it.isNotBlank() }.map(::normalize)
            val scores = words.map { word -> fields.mapNotNull { score(word, it) }.minOrNull() }
            if (scores.any { it == null }) null
            else Ranked(indexed.index, entry, scores.sumOf { it!! })
        }.sortedWith(compareBy<Ranked> { it.score }
            .thenBy { if (it.entry.id in favorites) 0 else 1 }
            .thenBy { history[it.entry.id] ?: Int.MAX_VALUE }
            .thenBy { it.index })
            .take(limit).map { it.entry }
    }

    private data class Ranked(val index: Int, val entry: Entry, val score: Int)
    private fun normalize(text: String) = text.lowercase(Locale.ROOT).trim()

    private fun score(query: String, text: String): Int? {
        if (query == text) return 0
        if (text.startsWith(query)) return 10
        val words = text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (query in words) return 15
        if (words.any { it.startsWith(query) }) return 20
        if (query in text) return 30
        var position = 0
        var first = -1
        var last = -1
        for (character in query) {
            position = text.indexOf(character, position)
            if (position < 0) break
            if (first < 0) first = position
            last = position++
        }
        if (position >= 0) return 40 + (last - first + 1 - query.length).coerceAtMost(15)
        val tolerance = if (query.length >= 6) 2 else if (query.length >= 3) 1 else 0
        if (tolerance == 0) return null
        val distance = (words + text).mapNotNull { word ->
            if (kotlin.math.abs(query.length - word.length) > tolerance) null
            else editDistance(query, word)
        }.minOrNull() ?: return null
        return if (distance <= tolerance) 60 + distance else null
    }

    private fun editDistance(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        var beforePrevious: IntArray? = null
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1)
                if (i > 0 && j > 0 && left[i] == right[j - 1] && left[i - 1] == right[j]) {
                    current[j + 1] = minOf(current[j + 1], beforePrevious!![j - 1] + 1)
                }
            }
            beforePrevious = previous
            previous = current
        }
        return previous[right.length]
    }

}

enum class ItemAction { COPY_ID, PREPARE_GIVE, CREATIVE_GIVE, FIND_INVENTORY }
enum class Stage { ROOT, ITEMS, ITEM_ACTIONS, QUANTITY }

fun parseQuantity(text: String): Int? =
    text.takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }
        ?.toIntOrNull()?.takeIf { it in 1..2304 }

class PaletteModel {
    var stage: Stage = Stage.ROOT
        private set
    var selectedItem: Entry? = null
        private set
    var action: ItemAction? = null
        private set
    private var actionFirst = false

    fun openItems() {
        stage = Stage.ITEMS
        selectedItem = null
        action = null
        actionFirst = false
    }

    fun pickItem(entry: Entry) {
        check(stage == Stage.ROOT || stage == Stage.ITEMS) { "Item picker is not active" }
        require(!entry.itemId.isNullOrBlank() && entry.itemId != "minecraft:air") { "Expected a non-air item" }
        selectedItem = entry
        stage = if (action == ItemAction.PREPARE_GIVE || action == ItemAction.CREATIVE_GIVE)
            Stage.QUANTITY else Stage.ITEM_ACTIONS
    }

    fun startItemAction(action: ItemAction) {
        check(stage != Stage.QUANTITY) { "Leave quantity stage before selecting another action" }
        this.action = action
        if (selectedItem == null) {
            actionFirst = true
            stage = Stage.ITEMS
        } else {
            stage = if (action == ItemAction.PREPARE_GIVE || action == ItemAction.CREATIVE_GIVE)
                Stage.QUANTITY else Stage.ITEM_ACTIONS
        }
    }

    fun back() {
        when (stage) {
            Stage.ROOT -> Unit
            Stage.ITEMS -> {
                stage = Stage.ROOT
                selectedItem = null
                action = null
                actionFirst = false
            }
            Stage.ITEM_ACTIONS -> {
                if (actionFirst) {
                    stage = Stage.ITEMS
                    selectedItem = null
                } else openItems()
            }
            Stage.QUANTITY -> {
                if (actionFirst) {
                    stage = Stage.ITEMS
                    selectedItem = null
                } else {
                    stage = Stage.ITEM_ACTIONS
                    action = null
                }
            }
        }
    }
}

class Preferences(
    private val path: Path,
    private val maxFavorites: Int = 128,
    private val maxRecent: Int = 60,
) {
    private var favoriteIds = linkedSetOf<String>()
    private var recentIds = emptyList<String>()
    val favorites: Set<String> get() = favoriteIds.toSet()
    val recent: List<String> get() = recentIds.toList()

    init {
        require(maxFavorites > 0 && maxRecent > 0)
        val properties = Properties()
        if (Files.exists(path)) Files.newInputStream(path).use { properties.load(it) }
        fun read(prefix: String, limit: Int): List<String> {
            val count = properties.getProperty("$prefix.count", "0").toInt()
            require(count >= 0) { "Invalid $prefix count" }
            return (0 until minOf(count, limit)).map { index ->
                requireNotNull(properties.getProperty("$prefix.$index")) { "Missing $prefix.$index" }
                    .also { require(it.isNotBlank()) { "Blank preference ID" } }
            }.distinct()
        }
        favoriteIds.addAll(read("favorites", maxFavorites))
        recentIds = read("recent", maxRecent)
    }

    fun toggleFavorite(id: String): Boolean {
        require(id.isNotBlank())
        val next = LinkedHashSet(favoriteIds)
        if (!next.remove(id)) {
            next.add(id)
            if (next.size > maxFavorites) next.remove(next.first())
        }
        save(next, recentIds)
        favoriteIds = next
        return id in next
    }

    fun recordRecent(id: String) {
        require(id.isNotBlank())
        val next = (listOf(id) + recentIds.filterNot { it == id }).take(maxRecent)
        save(favoriteIds, next)
        recentIds = next
    }

    private fun save(favorites: Set<String>, recent: List<String>) {
        val properties = Properties()
        fun put(prefix: String, ids: Collection<String>) {
            properties.setProperty("$prefix.count", ids.size.toString())
            ids.forEachIndexed { index, id -> properties.setProperty("$prefix.$index", id) }
        }
        put("favorites", favorites)
        put("recent", recent)
        val target = path.toAbsolutePath()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".spotlight-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { properties.store(it, "Spotlight preferences") }
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (failure: Exception) {
            try { Files.deleteIfExists(temporary) } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }
}
