package dev.spotlight.ui

import dev.spotlight.platform.HeadDatabase
import net.minecraft.client.gui.screens.Screen

class HeadDatabaseScreen(parent: Screen? = null) : StorageScreen(parent, "Head database · HeadDB.org + local") {
    private var query = ""
    private var offset = 0
    private var selected: HeadDatabase.Head? = null
    override fun build() {
        row("Search", "Refresh", "Back") { when (it) {
            0 -> prompt("Fuzzy search names, tags, profiles or item IDs", query) { query = it; offset = 0 }
            1 -> rebuild()
            2 -> onClose()
        } }
        row("Refresh online") {
            val connection = minecraft!!.connection
            val future = HeadDatabase.refreshRemote()
            rebuild()
            future.whenComplete { _, error -> minecraft!!.execute {
                if (minecraft!!.screen !== this || minecraft!!.connection !== connection) return@execute
                notice = if (error == null) "HeadDB.org catalog updated" else "HeadDB unavailable; cached/local heads retained"
                rebuild()
            } }
        }
        text("HeadDB.org · Yive / CCBlueX · Refresh online sends request")
        text(HeadDatabase.remoteStatus)
        text("Sources: Vanilla · Online players · Saved · HeadDB.org")
        text("Saved collection: config/spotlight/heads.json · max 512 heads")
        row("Save held head", "Import .nbt", "Player skull") { when (it) {
            0 -> prompt("Saved head name") { name -> prompt("Comma-separated tags") { tags ->
                HeadDatabase.saveHeld(name, tags); notice = "Held head saved, full components retained"; rebuild()
            } }
            1 -> prompt("Compressed stack .nbt filename in config/spotlight") { filename ->
                prompt("Imported head name") { name -> prompt("Comma-separated tags") { tags ->
                    HeadDatabase.importHead(filename, name, tags); notice = "Imported without altering source"; rebuild()
                } }
            }
            2 -> minecraft!!.setScreen(PlayerSkullScreen(this))
        } }
        val entries = HeadDatabase.search(query, HeadDatabase.entries())
        offset = offset.coerceAtMost(((entries.size - 1).coerceAtLeast(0) / 54) * 54)
        text("${entries.size} matches · page ${offset / 54 + 1} · select preview, then Give")
        val visible = entries.drop(offset).take(54)
        grid(visible.mapIndexed { index, head -> index to head.raw }.toMap()) { index ->
            selected = visible.getOrNull(index)
            rebuild()
        }
        val head = selected
        text(head?.let { "${it.title} · ${it.source} · ${it.tags.joinToString(", ")}" } ?: "No head selected")
        row("Previous", "Next", "Give selected", "Export selected") { when (it) {
            0 -> { offset = (offset - 54).coerceAtLeast(0); rebuild() }
            1 -> { if (offset + 54 < entries.size) offset += 54; rebuild() }
            2 -> { HeadDatabase.give(requireNotNull(head) { "Select a head first" }); notice = "Creative copy requested" }
            3 -> { val chosen = requireNotNull(head) { "Select a head first" }; prompt("New .nbt filename in config/spotlight", "head.nbt") { filename ->
                HeadDatabase.exportHead(chosen, filename); notice = "Exported $filename"
            } }
        } }
    }
}
