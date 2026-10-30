package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.NativeImage
import dev.spotlight.platform.CurrentMusic
import dev.spotlight.platform.CurrentSong
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import kotlin.math.PI
import kotlin.math.sin

class CurrentSongScreen(private val parent: Screen?) : Screen(catalogText("Current song")) {
    private val textureId = ResourceLocation.fromNamespaceAndPath("spotlight", "current_song_artwork")
    private val rotation = SongRotation()
    private var artworkId: String? = null
    private var hasTexture = false
    private var song: CurrentSong? = null
    private var status = ""
    private var ticks = 0
    private var left = 0
    private var top = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var discSize = 0
    private lateinit var play: Button
    private lateinit var stop: Button

    override fun isPauseScreen() = false

    override fun init() {
        panelWidth = minOf(380, (width - 16).coerceAtLeast(1))
        panelHeight = minOf(408, (height - 12).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        top = (height - panelHeight) / 2
        discSize = minOf(192, panelWidth - 40, panelHeight - 174).coerceAtLeast(16)
        val center = width / 2
        val controlsY = top + panelHeight - 72
        play = addRenderableWidget(object : Button(center - 26, controlsY, 52, 30, catalogText("Pause playback"),
            OnPress { control { CurrentMusic.togglePause() } }, CreateNarration { it.get() }) {
            override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
                val highlighted = active && (isHovered || isFocused)
                graphics.fill(x, y, right, bottom, SpotlightTheme.color(if (highlighted) 0xFF293F5B else 0xFF161D29))
                graphics.renderOutline(x, y, this.width, this.height, SpotlightTheme.color(if (highlighted) 0xFF81B8FF else 0xFF415877))
                val ink = SpotlightTheme.color(if (active) 0xFFF2F5FC else 0xFF91A0B5)
                val cx = x + this.width / 2
                val cy = y + this.height / 2
                if (song?.paused == false) {
                    graphics.fill(cx - 5, cy - 6, cx - 2, cy + 6, ink)
                    graphics.fill(cx + 2, cy - 6, cx + 5, cy + 6, ink)
                } else {
                    for (row in -6..6) graphics.fill(cx - 4, cy + row, cx + 5 - kotlin.math.abs(row), cy + row + 1, ink)
                }
            }
        })
        val half = ((panelWidth - 104) / 2).coerceAtLeast(1)
        stop = addRenderableWidget(catalogButton("Stop", left + 16, controlsY + 5, half) { control { CurrentMusic.stop() } })
        stop.setTooltip(Tooltip.create(catalogText("Stop current song")))
        addRenderableWidget(catalogButton("Back", left + panelWidth - 16 - half, controlsY + 5, half) { onClose() })
            .setTooltip(Tooltip.create(catalogText("Back (Esc); playback continues")))
        addRenderableWidget(catalogButton("Open library", center - minOf(144, panelWidth - 32) / 2, controlsY + 38,
            minOf(144, panelWidth - 32).coerceAtLeast(1)) { minecraft!!.setScreen(MusicPlayerScreen(this)) })
            .setTooltip(Tooltip.create(catalogText("Open music library to choose a song")))
        refresh()
        setInitialFocus(play)
    }

    private fun refresh() {
        try {
            val next = CurrentMusic.snapshot()
            rotation.update(next?.id, next?.paused == false, System.nanoTime())
            song = next
            if (artworkId != next?.id) {
                releaseArtwork()
                artworkId = next?.id
                if (next != null) createArtwork(next)
            }
            play.active = next != null
            stop.active = next != null
            val action = if (next?.paused == true) "Resume playback" else "Pause playback"
                        play.message = catalogText(if (next == null) "No current song" else "$action: ${next.title}, ${next.artist ?: "Unknown artist"}")
            play.setTooltip(Tooltip.create(catalogText("${play.message.string} (Space)")))
            status = ""
        } catch (failure: Exception) {
            status = "Audio or artwork unavailable: ${failure.message ?: failure.javaClass.simpleName}"
            song = null
            releaseArtwork()
            play.active = false
            stop.active = false
        }
    }

    private fun control(action: () -> Unit) {
        try {
            action()
            refresh()
        } catch (failure: Exception) {
            status = "Playback control failed: ${failure.message ?: failure.javaClass.simpleName}"
        }
    }

    private fun createArtwork(track: CurrentSong) {
        val palette = intArrayOf(SpotlightTheme.color(0xFF161D29), SpotlightTheme.color(0xFF415877),
            SpotlightTheme.color(0xFF81B8FF), SpotlightTheme.color(0xFFF2F5FC))
        val pixels = songArtwork(track.id.hashCode(), palette)
        val image = NativeImage(64, 64, false)
        var texture: DynamicTexture? = null
        try {
            for (y in 0 until 64) for (x in 0 until 64) image.setPixelRGBA(x, y, (pixels[y * 64 + x] and 0xFF00FF00.toInt()) or ((pixels[y * 64 + x] ushr 16) and 0xFF) or ((pixels[y * 64 + x] and 0xFF) shl 16))
            texture = DynamicTexture(image)
            minecraft!!.textureManager.register(textureId, texture)
            hasTexture = true
        } catch (failure: Throwable) {
            if (texture != null) texture.close() else image.close()
            throw failure
        }
    }

    private fun releaseArtwork() {
        if (hasTexture) minecraft!!.textureManager.release(textureId)
        hasTexture = false
        artworkId = null
    }

    override fun removed() {
        releaseArtwork()
        rotation.update(null, false, System.nanoTime())
        super.removed()
    }

    override fun tick() { if (++ticks % 5 == 0) refresh() }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (event.key() == InputConstants.KEY_SPACE && !primaryModifier(event) && !event.hasAltDown() && !event.hasControlDown()) {
            if (song != null) control { CurrentMusic.togglePause() }
            return true
        }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers())
    }

    override fun onClose() { minecraft!!.setScreen(parent) }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val foreground = SpotlightTheme.color(0xFFF2F5FC)
        val muted = SpotlightTheme.color(0xFF9DADC3)
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        graphics.fill(left, top, left + panelWidth, top + panelHeight, SpotlightTheme.color(0xFF161D29))
        graphics.renderOutline(left, top, panelWidth, panelHeight, SpotlightTheme.color(0xFF415877))
        graphics.themeCenteredText(font, catalogText("NOW PLAYING"), width / 2, top + 12, muted)
        val cx = width / 2
        val cy = top + 34 + discSize / 2
        val angle = rotation.update(song?.id, song?.paused == false, System.nanoTime())
        if (hasTexture) {
            val pose = graphics.pose()
            pose.pushPose()
            pose.translate(cx.toFloat(), cy.toFloat(), 0f)
            pose.mulPose(com.mojang.math.Axis.ZP.rotation(angle))
            graphics.blit(textureId, -discSize / 2, -discSize / 2, discSize, discSize, 0f, 0f, 64, 64, 64, 64)
            pose.popPose()
            val dx = mouseX - cx
            val dy = mouseY - cy
            if (dx * dx + dy * dy <= discSize * discSize / 4)
                graphics.setTooltipForNextFrame(catalogText("Generated artwork · ${song?.title.orEmpty()}"), mouseX, mouseY)
        } else {
            graphics.themeCenteredText(font, catalogText("Nothing playing"), cx, cy - 4, muted)
        }
        val titleY = top + 44 + discSize
        fun centered(value: String, y: Int, scale: Float, color: Int) {
            val pose = graphics.pose()
            pose.pushPose()
            pose.translate(cx.toFloat(), y.toFloat(), 0f)
            pose.scale(scale, scale, 1f)
            graphics.themeCenteredText(font, catalogClip(font, value, ((panelWidth - 32) / scale).toInt().coerceAtLeast(1)), 0, 0, color)
            pose.popPose()
        }
        centered(song?.title ?: "Choose something to play", titleY, 1.5f, foreground)
        centered(song?.artist?.takeIf { it.isNotBlank() } ?: if (song == null) "Open your music library below" else "Unknown artist", titleY + 20, 1.15f, muted)
        val state = song?.let { if (it.paused) "Paused" else "Playing" } ?: "No current song"
        graphics.themeCenteredText(font, catalogText(state), cx, titleY + 37, muted)
        if (mouseX in left + 12 until left + panelWidth - 12 && mouseY in titleY until titleY + 34 && song != null)
            graphics.setTooltipForNextFrame(catalogText("${song!!.title}\n${song!!.artist ?: "Unknown artist"}"), mouseX, mouseY)
        super.render(graphics, mouseX, mouseY, delta)
        if (status.isNotEmpty()) {
            graphics.themeCenteredText(font, catalogClip(font, status, (panelWidth - 16).coerceAtLeast(1)), cx, top + panelHeight - 12, foreground)
            if (mouseY in top + panelHeight - 14 until top + panelHeight)
                graphics.setTooltipForNextFrame(catalogText(status), mouseX, mouseY)
        }
    }
}

internal class SongRotation {
    private var id: String? = null
    private var playing = false
    private var lastNanos = 0L
    private var angle = 0f

    fun update(nextId: String?, nextPlaying: Boolean, now: Long): Float {
        if (nextId != id) angle = 0f
        else if (nextId != null && playing && nextPlaying) {
            val seconds = ((now - lastNanos).toDouble() / 1_000_000_000).coerceIn(0.0, 0.1)
            angle = ((angle + seconds * PI / 6) % (2 * PI)).toFloat()
        }
        id = nextId
        playing = nextPlaying
        lastNanos = now
        return angle
    }
}


internal fun songArtwork(seed: Int, palette: IntArray): IntArray = IntArray(64 * 64) { index ->
    val x = index % 64
    val y = index / 64
    val dx = x - 31.5
    val dy = y - 31.5
    if (dx * dx + dy * dy > 31 * 31) 0 else {
        val px = x / 2
        val py = y / 2
        val phase = (seed and 255) / 32.0
        val sunX = 9 + ((seed ushr 8) and 15)
        val sunY = 7 + ((seed ushr 16) and 7)
        val ridge = 20 + (5 * sin(px * 0.19 + phase)).toInt()
        val shade = when {
            py > ridge + 4 -> 0
            py > ridge -> 1
            (px - sunX) * (px - sunX) + (py - sunY) * (py - sunY) < 16 -> 3
            else -> 2
        }
        palette[shade] or (0xFF shl 24)
    }
}
