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
import net.minecraft.client.input.KeyEvent
import net.minecraft.core.registries.BuiltInRegistries
import java.util.UUID

internal fun uuidEntries(online: List<Pair<UUID, String>>, loaded: List<Entry>): List<Entry> =
    (online.map { (id, name) -> Entry(id.toString(), name, "minecraft:player") } + loaded).distinctBy { it.id }

class UuidScreen(private val parent: Screen?) : Screen(catalogText("uuid")) {
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
    private var error: String? = null
    private val rowStep get() = SpotlightTheme.metric("compactRowHeight", 24, 22, 80) + 14

    override fun isPauseScreen() = false

    private fun currentEntries(): List<Entry> {
        if (worldUnavailable() != null) return emptyList()
        val online = minecraft.connection?.onlinePlayers.orEmpty().map { it.profile.id to it.profile.name }
        val loaded = minecraft.level?.entitiesForRendering()?.toMutableList() ?: mutableListOf()
        minecraft.player?.let { loaded.add(it) }
        return uuidEntries(online, loaded.map {
            Entry(it.uuid.toString(), it.displayName.string, BuiltInRegistries.ENTITY_TYPE.getKey(it.type).toString())
        })
    }

    override fun init() {
        rows.clear()
        panelWidth = minOf(SpotlightTheme.metric("selectorWidth", 520, 160, 4096), (width - 16).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        count = ((height - 100) / rowStep).coerceIn(0, 12)
        search = addRenderableWidget(SpotlightEditBox(font, left, 10, panelWidth, 20, catalogText("Search names, types or UUIDs")))
        search.setMaxLength(32768)
        search.setHint(catalogText("Search names, types or UUIDs"))
        search.value = query
        search.setResponder { query = it; selected = 0; offset = 0; error = null; refresh() }
        entries = currentEntries()
        addRenderableWidget(catalogButton("Back", left, (height - 48).coerceAtLeast(34), panelWidth) { onClose() })
        refresh()
        setInitialFocus(search)
    }

    override fun tick() {
        super.tick()
        if (++ticks % 10 != 0) return
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
                left, 38 + index * rowStep, panelWidth) { copy(entry.id) })
        }
        search.message = catalogText("Search names, types or UUIDs. ${results.getOrNull(selected)?.let { "${it.title}, ${it.detail}, ${it.id}" } ?: "No matches"}. ${error.orEmpty()}")
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
        if (entries.none { it.id == id }) {
            error = "Player no longer online or entity no longer loaded"
            refresh()
            triggerImmediateNarration(true)
            return
        }
        minecraft.keyboardHandler.clipboard = id
        notifySpotlight("Copied UUID to clipboard")
        onClose()
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (event.isConfirmation && !event.hasAltDown()) { copy(); return true }
        if (!primaryModifier(event) && !event.hasAltDown()) {
            if (event.isDown || event.isUp) { move(if (event.isDown) 1 else -1); return true }
            if (event.key() == InputConstants.KEY_TAB) { move(if (event.hasShiftDown()) -1 else 1, cycle = true); return true }
        }
        return super.keyPressed(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (vertical != 0.0) { move(if (vertical > 0) -1 else 1); return true }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun onClose() { minecraft.setScreen(parent) }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.render(graphics, mouseX, mouseY, delta)
        graphics.enableScissor(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
        results.drop(offset).take(count).forEachIndexed { index, entry ->
            graphics.themeText(font, catalogClip(font, entry.id, panelWidth), left, 60 + index * rowStep, SpotlightTheme.color(0xFF9DADC3))
        }
        if (results.isEmpty() && count > 0) graphics.themeText(font, catalogClip(font, "No matching online players or loaded entities", panelWidth), left, 38, SpotlightTheme.color(0xFF9DADC3))
        error?.let { graphics.themeText(font, catalogClip(font, it, panelWidth), left, (height - 62).coerceAtLeast(0), SpotlightTheme.color(0xFFFFB4A9)) }
        graphics.disableScissor()
        catalogHints(graphics, font, width, height, "↑↓ Select · Tab Cycle · Enter Copy UUID · Esc Back")
    }
}
