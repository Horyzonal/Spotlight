package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.platform.PlayingSound
import dev.spotlight.platform.SoundControls
import dev.spotlight.platform.commandMatch
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

internal fun soundRow(sound: PlayingSound): String =
    "${sound.name} · ${sound.id} · ${sound.category} · volume ${sound.volume} · pitch ${sound.pitch} · ${if (sound.looping) "loop" else "once"}"

internal fun soundRowClick(button: Int): Boolean = button == InputConstants.MOUSE_BUTTON_LEFT

internal fun soundSelection(ids: List<String>, previous: String?): String? =
    previous?.takeIf { it in ids } ?: ids.firstOrNull()

internal fun soundNumber(text: String, range: ClosedFloatingPointRange<Float>): Float? =
    text.toFloatOrNull()?.takeIf { it.isFinite() && it in range }

internal fun soundMatches(query: String, entries: List<Pair<String, String>>): List<Pair<String, String>> =
    entries.map { it to commandMatch(query, it.second) }.filter { it.second != Int.MAX_VALUE }
        .sortedBy { it.second }.map { it.first }


internal class SoundMenuList(
    client: Minecraft, private val textFont: Font, width: Int, height: Int, top: Int,
    private val changed: (String?) -> Unit,
) : ObjectSelectionList<SoundMenuList.Row>(client, width, height, top, 24) {
    private val foreground = SpotlightTheme.color(0xFFF2F5FC)
    override fun getRowWidth() = (width - 16).coerceAtLeast(1)
    fun show(entries: List<Pair<String, String>>, previous: String?) {
        val scroll = scrollAmount()
        replaceEntries(entries.map { Row(it.first, it.second) })
        val token = soundSelection(entries.map { it.first }, previous)
        setSelected(children().firstOrNull { it.token == token })
        setScrollAmount(scroll)
    }
    fun move(delta: Int) {
        val index = children().indexOf(selected)
        val row = children().getOrNull((index + delta).coerceIn(0, (children().size - 1).coerceAtLeast(0))) ?: return
        setSelected(row)
        scrollToEntry(row)
    }
    override fun setSelected(entry: Row?) {
        super.setSelected(entry)
        changed(entry?.token)
    }
    inner class Row(val token: String, private val label: String) : Entry<Row>() {
        override fun getNarration(): Component = catalogText(label)
        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            if (!soundRowClick(event.button())) return false
            setSelected(this)
            return true
        }
        override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, delta: Float) {
            graphics.themeText(textFont, catalogClip(textFont, label, contentWidth), contentX, contentY + 3, foreground)
            if (hovered) graphics.setTooltipForNextFrame(catalogText(label), mouseX, mouseY)
        }
    }
}

class SoundManagerScreen(private val parent: Screen?) : Screen(catalogText("Sounds")) {
    private var manual = false
    private var query = ""
    private var playingQuery = ""
    private var catalogQuery = ""
    private var selectedToken: String? = null
    private var selectedSound: String? = null
    private var playing = emptyList<PlayingSound>()
    private var catalog = emptyList<String>()
    private var ticks = 0
    private var status = ""
    private var volumeText = "1"
    private var pitchText = "1"
    private lateinit var search: EditBox
    private lateinit var volume: EditBox
    private lateinit var pitch: EditBox
    private lateinit var list: SoundMenuList
    private var left = 0
    private var panelWidth = 1
    private var top = 0
    private var bottom = 0
    private var background = 0
    private var foreground = 0
    private var errorColor = 0

    override fun isPauseScreen() = false
    override fun init() {
        background = SpotlightTheme.color(0xF0101722)
        foreground = SpotlightTheme.color(0xFFF2F5FC)
        errorColor = SpotlightTheme.color(0xFFFFB4A9)
        panelWidth = minOf(760, (width - 16).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        top = ((height - 430) / 2).coerceAtLeast(4)
        bottom = minOf(height - 4, top + 430)
        val half = panelWidth / 2
        addRenderableWidget(catalogButton(if (manual) "Playing" else "[Playing]", left, top, half) { tab(false) })
        addRenderableWidget(catalogButton(if (manual) "[Play sound]" else "Play sound", left + half, top, panelWidth - half) { tab(true) })
        search = addRenderableWidget(SpotlightEditBox(font, left, top + 24, panelWidth, 20, catalogText("Search sounds")))
        search.value = query
        search.setResponder { query = it; filter() }
        list = addRenderableWidget(SoundMenuList(minecraft, font, panelWidth, (bottom - top - 138).coerceAtLeast(1), top + 48) {
            if (manual) selectedSound = it else selectedToken = it
        }.also { it.x = left })
        val fieldWidth = (panelWidth / 2 - 4).coerceAtLeast(1)
        volume = addRenderableWidget(SpotlightEditBox(font, left, bottom - 62, fieldWidth, 20, catalogText("Volume (0–1)")))
        volume.value = volumeText
        volume.setResponder { volumeText = it }
        pitch = addRenderableWidget(SpotlightEditBox(font, left + half, bottom - 62, fieldWidth, 20, catalogText("Pitch (0.5–2)")))
        pitch.value = pitchText
        pitch.setResponder { pitchText = it }
        volume.visible = manual
        pitch.visible = manual
        val third = panelWidth / 3
        addRenderableWidget(catalogButton(if (manual) "Play (Enter)" else "Stop selected ($primaryModifierLabel+S)", left, bottom - 22, third) { act() })
        addRenderableWidget(catalogButton("Stop all…", left + third, bottom - 22, third) {
            minecraft.setScreen(ConfirmSpotlightScreen(this, "Stop all sounds?", "Stops every vanilla/modded sound and queued playback. Confirm with Delete; this removes no files.") {
                runCatching { SoundControls.stopAll() }.fold({ status = "All sounds stopped" }, { status = "Stop failed: ${it.message}" })
                refresh()
            })
        })
        addRenderableWidget(catalogButton("Back (Esc)", left + third * 2, bottom - 22, panelWidth - third * 2) { onClose() })
        runCatching { SoundControls.catalog() }.fold({ catalog = it }, { status = "Catalog unavailable: ${it.message}" })
        refresh()
        setInitialFocus(search)
    }
    private fun tab(value: Boolean) {
        if (manual == value) return
        if (manual) catalogQuery = query else playingQuery = query
        manual = value
        query = if (manual) catalogQuery else playingQuery
        status = ""
        rebuildWidgets()
    }
    private fun refresh() {
        runCatching { SoundControls.playing() }.fold({ playing = it }, { status = "Audio unavailable: ${it.message}" })
        filter()
    }
    private fun filter() {
        if (!::list.isInitialized) return
        val entries = if (manual) catalog.map { it to it } else playing.map { it.id to soundRow(it) }
        list.show(soundMatches(query, entries), if (manual) selectedSound else selectedToken)
    }
    private fun act() {
        if (manual) {
            val v = soundNumber(volumeText, 0f..1f)
            val p = soundNumber(pitchText, 0.5f..2f)
            if (v == null || p == null) { status = "Volume must be 0–1; pitch must be 0.5–2 (finite numbers)"; return }
            val id = selectedSound ?: return
            runCatching { SoundControls.play(id, v, p) }.fold({ status = if (it == null) "Sound could not start" else "Playing $id" }, { status = "Play failed: ${it.message}" })
        } else {
            val token = selectedToken ?: return
            runCatching { SoundControls.stop(token) }.fold({ status = if (it) "Stopped $token" else "Instance already ended" }, { status = "Stop failed: ${it.message}" })
        }
        refresh()
    }
    override fun tick() { if (++ticks % 5 == 0) refresh() }
    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (!manual && primaryModifier(event) && !event.hasAltDown() && event.key() == InputConstants.KEY_S) { act(); return true }
        if ((search.isFocused || list.isFocused) && !primaryModifier(event) && !event.hasAltDown()) {
            if (event.isUp || event.isDown) { list.move(if (event.isDown) 1 else -1); return true }
            if (event.isConfirmation) { act(); return true }
        }
        return super.keyPressed(event)
    }
    override fun onClose() { minecraft.setScreen(parent) }
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, background)
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        if (manual) {
            graphics.themeText(font, catalogClip(font, "Volume 0–1", panelWidth / 2), left, bottom - 74, foreground)
            graphics.themeText(font, catalogClip(font, "Pitch 0.5–2", panelWidth / 2), left + panelWidth / 2, bottom - 74, foreground)
        } else {
            graphics.themeText(font, catalogClip(font, "${playing.size} playing · ↑↓ Select · Enter Stop instance", panelWidth), left, bottom - 74, foreground)
        }
        val invalid = manual && (soundNumber(volumeText, 0f..1f) == null || soundNumber(pitchText, 0.5f..2f) == null)
        graphics.themeText(font, catalogClip(font, if (invalid) "Volume 0–1; pitch 0.5–2 required" else status.ifEmpty { "${list.children().size} matches" }, panelWidth), left, bottom - 36, if (invalid) errorColor else foreground)
    }
}
