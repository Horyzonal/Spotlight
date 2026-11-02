package dev.spotlight.ui

import dev.spotlight.platform.ClientItemStorage
import net.minecraft.client.gui.screens.Screen

class ArchiveScreen(parent: Screen? = null) : VaultGridScreen(parent, "Item archive · newest unique first") {
    override val archiveSource get() = true

    private var offset = 0
    override fun mouseScrolled(x: Double, y: Double, horizontal: Double, vertical: Double): Boolean {
        if (contentContains(x, y) && x >= left && x < left + 180) {
            val count = searchItems(ClientItemStorage.archiveNewest()).size
            if (vertical > 0) offset = (offset - 54).coerceAtLeast(0)
            else if (vertical < 0 && offset + 54 < count) offset += 54
            rebuild(); return true
        }
        return super.mouseScrolled(x, y, horizontal, vertical)
    }
    override fun build() {
        searchBox { offset = 0 }
        row("Resume capture", "Back") {
            if (it == 0) { ClientItemStorage.resumeArchive(); notice = "Capture resumed" } else onClose()
        }
        val entries = searchItems(ClientItemStorage.archiveNewest())
        offset = offset.coerceAtMost(((entries.size - 1).coerceAtLeast(0) / 54) * 54)
        text("${entries.size} observed variants · count and component differences retained")
        text("Archive read-only originals; copied items · page ${offset / 54 + 1}")
        text("Click: cursor copy · Shift: Creative give · 1–9: EMPTY hotbar copy")
        val visible = entries.drop(offset).take(54)
        chestGrid(visible.mapIndexed { index, entry -> index to entry.second }.toMap())
        row("Previous", "Next", "Copy to chest page") { when (it) {
            0 -> { offset = (offset - 54).coerceAtLeast(0); rebuild() }
            1 -> { if (offset + 54 < entries.size) offset += 54; rebuild() }
            2 -> prompt("Copy visible archive entries to empty chest page", "1") { value ->
                ClientItemStorage.copyArchivePage(value.toInt(), visible.map { entry -> entry.second })
            }
        } }
    }
}
