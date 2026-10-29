package dev.spotlight.ui

import dev.spotlight.platform.CurrentMusic
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component

internal data class NowPlayingLayout(val textWidth: Int, val gap: Int, val widgetWidth: Int)

internal fun nowPlayingLayout(panelWidth: Int): NowPlayingLayout {
    val available = (panelWidth - 24).coerceAtLeast(0)
    val widget = if (available >= 240) minOf(180, panelWidth / 3) else minOf(32, (available - 1).coerceAtLeast(0))
    val gap = minOf(8, (available - widget - 1).coerceAtLeast(0))
    return NowPlayingLayout(available - widget - gap, gap, widget)
}

internal class NowPlayingWidget(font: Font, x: Int, y: Int, width: Int, onPress: () -> Unit) {
    val button = spotlightButton("", x, y, width, onPress = onPress)
    private var ticks = 0

    init {
        button.visible = width > 0
        refresh(font)
    }

    fun tick(font: Font) {
        if (++ticks == 5) {
            ticks = 0
            refresh(font)
        }
    }

    private fun refresh(font: Font) {
        val song = CurrentMusic.snapshot()
        button.visible = song != null && button.width > 0
        button.active = button.visible
        if (song == null) {
            button.setTooltip(null)
            return
        }
        val label = song?.let { "${if (it.paused) "Paused" else "♫"} ${it.title}" } ?: "No music"
        val textWidth = (button.width - 8).coerceAtLeast(0)
        val text = if (button.width <= 32) "♫" else if (font.width(label) <= textWidth) label else
            font.plainSubstrByWidth(label, (textWidth - font.width("…")).coerceAtLeast(0)) + "…"
        button.message = Component.literal(text).withStyle {
            it.withColor(SpotlightTheme.color(if (song == null) 0xFF9DADC3 else 0xFFF2F5FC) and 0xFFFFFF)
        }
        val description = song?.let {
            listOfNotNull(it.title, it.artist?.takeIf(String::isNotBlank), if (it.paused) "Paused" else "Playing").joinToString("\n")
        } ?: "No music"
        
        button.setTooltip(Tooltip.create(Component.literal("$description\nOpen Current song")))
    }
}
