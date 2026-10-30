package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.MusicTrack
import dev.spotlight.platform.SoundControls
import dev.spotlight.platform.notifySpotlight
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent

class MusicPlayerScreen(private val parent: Screen?) : Screen(catalogText("Music player")) {
    private var query = ""
    private var selectedId: String? = null
    private var music = emptyList<MusicTrack>()
    private var current = ""
    private var status = ""
    private var ticks = 0
    private lateinit var search: EditBox
    private lateinit var list: SoundMenuList
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
        search = addRenderableWidget(SpotlightEditBox(font, left, top, panelWidth, 20, catalogText("Search titles, artists or file IDs")))
        search.value = query
        search.setResponder { query = it; filter() }
        list = addRenderableWidget(SoundMenuList(minecraft, font, panelWidth, (bottom - top - 110).coerceAtLeast(1), top + 24) {
            selectedId = it
        }.also { it.x = left })
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
        if (::list.isInitialized) list.show(soundMatches(query, music.map { it.id to "${it.label} · ${it.id}" }), selectedId)
    }
    private fun refresh() {
        runCatching { SoundControls.currentMusic }.fold({ current = it?.let { sound -> "Looping: ${music.firstOrNull { track -> track.id == sound.name }?.label ?: sound.name}" } ?: "No player-owned music" }, {
            current = "Audio unavailable: ${it.message}"
        })
    }
    private fun play() {
        val id = selectedId ?: return
        val track = music.firstOrNull { it.id == id } ?: return
        runCatching { SoundControls.playTrack(track) }.fold({
                    status = if (it == null) "Music could not start" else "Playing ${track.label}"
                    if (it != null) notifySpotlight("Now playing ${track.title}")
                }, {
            status = "Play failed: ${it.message}"
        })
        refresh()
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
        if (primaryModifier(event) && !event.hasAltDown() && event.key() == InputConstants.KEY_S) { stop(); return true }
        if ((search.isFocused || list.isFocused) && !primaryModifier(event) && !event.hasAltDown()) {
            if (event.isUp || event.isDown) { list.move(if (event.isDown) 1 else -1); return true }
            if (event.isConfirmation) { play(); return true }
        }
        return super.keyPressed(event)
    }
    override fun onClose() { minecraft.setScreen(parent) }
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, background)
        super.render(graphics, mouseX, mouseY, delta)
        fun text(value: String, y: Int, color: Int) = graphics.themeText(font, catalogClip(font, value, panelWidth), left, y, color)
        text(current, bottom - 78, foreground)
        text("Client-only · Selected track loops · ↑↓ Select", bottom - 64, muted)
        text("Native background music suspended while player owns music", bottom - 50, muted)
        text(status.ifEmpty { "${list.children().size} loaded music matches" }, bottom - 36, foreground)
    }
}
