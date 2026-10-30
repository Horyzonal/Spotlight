package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.Entry
import dev.spotlight.core.Fuzzy
import dev.spotlight.platform.notifySpotlight
import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import java.util.Locale
import java.util.UUID

internal fun pingEntries(online: List<Triple<UUID, String, Int>>): List<Entry> =
    online.map { (id, name, latency) -> Entry(id.toString(), name, if (latency < 0) "Unknown" else "$latency ms") }
        .sortedWith(compareBy<Entry> { it.title.lowercase(Locale.ROOT) }.thenBy { it.title }.thenBy { it.id })

class PingScreen(private val parent: Screen?) : Screen(catalogText("Ping")) {
    private var entries = emptyList<Entry>()
    private var results = emptyList<Entry>()
    private val rows = mutableListOf<Button>()
    private lateinit var search: EditBox
    private var query = ""
    private var selected = 0
    private var offset = 0
    private var count = 0
    private var left = 0
    private var panelWidth = 1
    private var ticks = 0
    private val rowStep get() = SpotlightTheme.metric("compactRowHeight", 24, 22, 80)

    override fun isPauseScreen() = false

    private fun currentEntries(): List<Entry> {
        if (worldUnavailable() != null) return emptyList()
        return pingEntries(minecraft!!.connection?.onlinePlayers.orEmpty().map {
            Triple(it.profile.id, it.profile.name, it.latency)
        })
    }

    override fun init() {
        rows.clear()
        panelWidth = minOf(SpotlightTheme.metric("selectorWidth", 520, 160, 4096), (width - 16).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        count = ((height - 114) / rowStep).coerceAtLeast(0)
        search = addRenderableWidget(SpotlightEditBox(font, left, 10, panelWidth, 20, catalogText("Search player names")))
        search.setMaxLength(32768)
        search.setHint(catalogText("Search player names"))
        search.value = query
        search.setResponder { query = it; selected = 0; offset = 0; refresh() }
        entries = currentEntries()
        addRenderableWidget(catalogButton("Back", left, (height - 48).coerceAtLeast(34), panelWidth) { onClose() })
        refresh()
        setInitialFocus(search)
    }

    override fun tick() {
        super.tick()
        if (++ticks % 20 != 0) return
        val current = currentEntries()
        if (current != entries) {
            val id = results.getOrNull(selected)?.id
            entries = current
            refresh(id)
        }
    }

    private fun refresh(keepId: String? = null) {
        rows.forEach(::removeWidget)
        rows.clear()
        results = Fuzzy.search(query, entries, limit = entries.size)
        if (keepId != null) selected = results.indexOfFirst { it.id == keepId }.takeIf { it >= 0 } ?: selected
        selected = selected.coerceIn(0, (results.size - 1).coerceAtLeast(0))
        offset = offset.coerceIn((selected - count.coerceAtLeast(1) + 1).coerceAtLeast(0), selected)
        results.drop(offset).take(count).forEachIndexed { index, entry ->
            rows += addRenderableWidget(catalogButton("${if (offset + index == selected) "• " else ""}${entry.title} · ${entry.detail}",
                left, 52 + index * rowStep, panelWidth) { copy(entry.id) })
        }
        search.message = catalogText("Search player names. ${results.size} of ${entries.size} online players. ${results.getOrNull(selected)?.let { "${it.title}, ${it.detail}" } ?: "No matches"}")
        setFocused(search)
    }

    private fun move(step: Int, cycle: Boolean = false) {
        if (results.isEmpty()) return
        selected = if (cycle) Math.floorMod(selected + step, results.size) else (selected + step).coerceIn(0, results.lastIndex)
        refresh()
    }

    private fun copy(id: String? = results.getOrNull(selected)?.id) {
        if (id == null) return
        entries = currentEntries()
        val entry = entries.find { it.id == id }
        refresh(id)
        if (entry == null) {
            notifySpotlight("Player no longer online")
            return
        }
        minecraft!!.keyboardHandler.clipboard = "${entry.title}: ${entry.detail}"
        notifySpotlight("Copied player ping to clipboard")
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (event.isEscape) { onClose(); return true }
        if (event.isConfirmation && !event.hasAltDown()) { copy(); return true }
        if (!primaryModifier(event) && !event.hasAltDown()) {
            if (event.isDown || event.isUp) { move(if (event.isDown) 1 else -1); return true }
            if (event.key() == InputConstants.KEY_TAB) { move(if (event.hasShiftDown()) -1 else 1, cycle = true); return true }
        }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers())
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (vertical != 0.0) { move(if (vertical > 0) -1 else 1); return true }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun onClose() { minecraft!!.setScreen(parent) }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.render(graphics, mouseX, mouseY, delta)
        graphics.enableScissor(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
        graphics.themeText(font, catalogClip(font, "Ping · ${results.size}/${entries.size} online players · Player / Latency", panelWidth), left, 38, SpotlightTheme.color(0xFF9DADC3))
        if (results.isEmpty() && count > 0) graphics.themeText(font, catalogClip(font, worldUnavailable() ?: "No matching online players", panelWidth), left, 52, SpotlightTheme.color(0xFF9DADC3))
        graphics.disableScissor()
        catalogHints(graphics, font, width, height, "↑↓ Select · Tab Cycle · Enter Copy · Esc Back")
    }
}
