package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.MusicTrack
import dev.spotlight.platform.CurrentMusic
import dev.spotlight.platform.EndlessMusic
import dev.spotlight.platform.SoundControls
import dev.spotlight.platform.notifySpotlight
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent

internal fun adjacentMusicTrack(ids: List<String>, current: String?, direction: Int): String? {
    if (ids.isEmpty()) return null
    val index = ids.indexOf(current)
    if (index < 0) return if (direction < 0) ids.last() else ids.first()
    return ids[Math.floorMod(index + if (direction < 0) -1 else 1, ids.size)]
}

class MusicPlayerScreen(private val parent: Screen?) : Screen(catalogText("Music player")) {
    private var query = ""
    private var selectedId: String? = null
    private var music = emptyList<MusicTrack>()
    private var current = ""
    private var playingId: String? = null
    private var filteredIds = emptyList<String>()
    private var status = ""
    private var ticks = 0
    private lateinit var search: EditBox
    private lateinit var list: SoundMenuList
    private lateinit var pauseButton: Button
    private lateinit var endlessButton: Button
    private var left = 0
    private var panelWidth = 1
    private var bottom = 0
    private var background = 0
    private var foreground = 0
    private var muted = 0

    override fun isPauseScreen() = false
    override fun init() {
        background = SpotlightTheme.color(0xF0101722)
        foreground = SpotlightTheme.color(0xFFF2F5FC)
        muted = SpotlightTheme.color(0xFF9DADC3)
        panelWidth = minOf(760, (width - 16).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        val top = ((height - 430) / 2).coerceAtLeast(4)
        bottom = minOf(height - 4, top + 430)
        search = addRenderableWidget(SpotlightEditBox(font, left, top + 14, panelWidth, 20, catalogText("Search titles, artists or file IDs")))
        search.value = query
        search.setResponder { query = it; filter() }
        list = addRenderableWidget(SoundMenuList(minecraft, font, panelWidth, (bottom - top - 168).coerceAtLeast(1), top + 38) {
            selectedId = it
        }.also { it.x = left })
        val half = panelWidth / 2
        addRenderableWidget(catalogButton("Previous ($primaryModifierLabel+←)", left, bottom - 126, half) { skip(-1) })
        addRenderableWidget(catalogButton("Next ($primaryModifierLabel+→)", left + half, bottom - 126, panelWidth - half) { skip(1) })
        pauseButton = addRenderableWidget(catalogButton("Pause ($primaryModifierLabel+P)", left, bottom - 102, half) { togglePause() })
        endlessButton = addRenderableWidget(catalogButton("", left + half, bottom - 102, panelWidth - half) { toggleEndless() })
        status = EndlessMusic.error.orEmpty()
        val third = panelWidth / 3
        addRenderableWidget(catalogButton("Play / loop (Enter)", left, bottom - 22, third) { play() })
        addRenderableWidget(catalogButton("Stop current ($primaryModifierLabel+S)", left + third, bottom - 22, third) { stop() })
        addRenderableWidget(catalogButton("Back (Esc)", left + third * 2, bottom - 22, panelWidth - third * 2) { onClose() })
        runCatching { SoundControls.tracks() }.fold({ music = it }, { status = "Music catalog unavailable: ${it.message}" })
        filter()
        refresh()
        setInitialFocus(search)
    }
    private fun filter() {
        val matches = soundMatches(query, music.map { it.id to "${it.label} · ${it.id}" })
        filteredIds = matches.map { it.first }
        if (::list.isInitialized) list.show(matches, selectedId)
    }
    private fun refresh() {
        runCatching { CurrentMusic.snapshot() }.fold({ song ->
            current = song?.let {
                "Now playing: ${it.title}${it.artist?.let { artist -> " · $artist" }.orEmpty()} · ${if (it.paused) "Paused" else if (it.owned) "Looping" else "Ambient"}"
            } ?: "No music playing"
            pauseButton.active = song != null
            pauseButton.message = catalogText("${if (song?.paused == true) "Resume" else "Pause"} ($primaryModifierLabel+P)")
        }, {
            current = "Audio unavailable: ${it.message}"
            pauseButton.active = false
        })
        endlessButton.message = catalogText("Endless: ${if (EndlessMusic.enabled) "On" else "Off"} ($primaryModifierLabel+E)")
    }
    private fun togglePause() {
        runCatching { CurrentMusic.togglePause() }.onFailure { status = "Pause failed: ${it.message}" }
        refresh()
    }
    private fun toggleEndless() {
        val enabled = !EndlessMusic.enabled
        status = if (EndlessMusic.setEnabled(enabled)) {
            "Endless ${if (enabled) "enabled" else "disabled"} · Saved"
        } else EndlessMusic.error ?: "Music preferences could not save"
        refresh()
    }
    private fun play() {
        val id = selectedId ?: return
        val track = music.firstOrNull { it.id == id } ?: return
        runCatching { SoundControls.playTrack(track) }.fold({
                    status = if (it == null) "Music could not start" else "Playing ${track.label}"
                    if (it != null) {
                        playingId = track.id
                        notifySpotlight("Now playing ${track.title}")
                    }
                }, {
            status = "Play failed: ${it.message}"
        })
        refresh()
    }
    private fun skip(direction: Int) {
        val id = adjacentMusicTrack(filteredIds, playingId ?: selectedId, direction) ?: return
        selectedId = id
        filter()
        play()
    }
    private fun stop() {
        runCatching { SoundControls.stopMusic() }.fold({ status = if (it) "Music stopped" else "No player-owned music to stop" }, {
            status = "Stop failed: ${it.message}"
        })
        refresh()
    }
    override fun tick() { if (++ticks % 5 == 0) refresh() }
    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (primaryModifier(event) && !event.hasAltDown() && !event.hasShiftDown()) {
            if (event.key() == InputConstants.KEY_LEFT) { skip(-1); return true }
            if (event.key() == InputConstants.KEY_RIGHT) { skip(1); return true }
        }
        if (primaryModifier(event) && !event.hasAltDown() && event.key() == InputConstants.KEY_S) { stop(); return true }
        if (!search.isFocused && primaryModifier(event) && !event.hasAltDown()) {
            if (event.key() == InputConstants.KEY_E) { toggleEndless(); return true }
            if (event.key() == InputConstants.KEY_P) { togglePause(); return true }
        }
        if ((search.isFocused || list.isFocused) && !primaryModifier(event) && !event.hasAltDown()) {
            if (event.isUp || event.isDown) { list.move(if (event.isDown) 1 else -1); return true }
            if (event.isConfirmation) { play(); return true }
        }
        return super.keyPressed(event)
    }
    override fun onClose() { minecraft.setScreen(parent) }
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, background)
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        fun text(value: String, y: Int, color: Int) = graphics.themeText(font, catalogClip(font, value, panelWidth), left, y, color)
        text(current, search.y - 12, foreground)
        text("Endless skips native music gaps; keeps biome and dimension choices", bottom - 78, muted)
        text("Client-only · Selected track loops · ↑↓ Select", bottom - 64, muted)
        text("Native background music suspended while player owns music", bottom - 50, muted)
        text(status.ifEmpty { "${list.children().size} loaded music matches" }, bottom - 36, foreground)
    }
}
