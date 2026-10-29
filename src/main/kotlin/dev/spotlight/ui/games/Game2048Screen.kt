package dev.spotlight.ui.games

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.games.Game2048
import dev.spotlight.ui.themeText
import dev.spotlight.ui.SpotlightTheme
import dev.spotlight.ui.spotlightButton
import dev.spotlight.ui.catalogText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent


class Game2048Screen(private val parent: Screen?) : Screen(catalogText("2048")) {
    private val game = Game2048(System.nanoTime())
    private val restartLabel = "Restart [R]"
    private val backLabel = "Back [Esc]"
    private val controls = "Arrows / WASD: slide · $restartLabel · $backLabel"

    override fun isPauseScreen() = false

    override fun init() {
        val buttonWidth = minOf(140, ((width - 24) / 2).coerceAtLeast(1))
        val left = (width - buttonWidth * 2 - 8) / 2
        addRenderableWidget(spotlightButton(restartLabel, left, height - 28, buttonWidth) { game.restart() })
        addRenderableWidget(spotlightButton(backLabel, left + buttonWidth + 8, height - 28, buttonWidth) { onClose() })
    }

    override fun onClose() { minecraft.gui.setScreen(parent) }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (event.key() == InputConstants.KEY_R) { game.restart(); return true }
        val direction = when (event.key()) {
            InputConstants.KEY_LEFT, InputConstants.KEY_A -> Game2048.Direction.LEFT
            InputConstants.KEY_RIGHT, InputConstants.KEY_D -> Game2048.Direction.RIGHT
            InputConstants.KEY_UP, InputConstants.KEY_W -> Game2048.Direction.UP
            InputConstants.KEY_DOWN, InputConstants.KEY_S -> Game2048.Direction.DOWN
            else -> null
        }
        if (direction != null) { game.move(direction); return true }
        return super.keyPressed(event)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.enableScissor(0, 0, width.coerceAtLeast(1), (height - 32).coerceAtLeast(1))
        val status = when {
            game.gameOver -> if (game.won) "2048 reached · Game over" else "Game over"
            game.won -> "2048 reached · Keep playing!"
            else -> "Merge matching tiles to reach 2048"
        }
        val header = font.split(catalogText("2048 · Score: ${game.score}\n$status"), (width - 16).coerceAtLeast(1))
        header.forEachIndexed { i, line -> graphics.themeText(font, line, 8, 8 + i * font.lineHeight, SpotlightTheme.color(0xFFF2F5FC)) }
        val top = 16 + header.size * font.lineHeight
        val hintHeight = font.split(catalogText(controls), (width - 16).coerceAtLeast(1)).size * font.lineHeight
        val bottom = height - 40 - hintHeight
        val cell = minOf((width - 16) / 4, (bottom - top) / 4, 76)
        if (cell >= 28) {
            val left = (width - cell * 4) / 2
            val board = game.board
            board.forEachIndexed { index, value ->
                val x = left + index % 4 * cell
                val y = top + index / 4 * cell
                val background = when {
                    value == 0 -> 0xFF19202B
                    value < 16 -> 0xFF22334B
                    value < 128 -> 0xFF293F5B
                    else -> 0xFF415877
                }
                graphics.fill(x + 1, y + 1, x + cell - 1, y + cell - 1, SpotlightTheme.color(background))
                if (value != 0) {
                    val text = value.toString()
                    graphics.themeText(font, catalogText(text), x + (cell - font.width(catalogText(text))) / 2, y + (cell - font.lineHeight) / 2,
                        SpotlightTheme.color(if (value >= 2048) 0xFFFFD27D else 0xFFF2F5FC))
                }
            }
        } else graphics.themeText(font, catalogText("Enlarge window to show board"), 8, top, SpotlightTheme.color(0xFF9DADC3))
        graphics.disableScissor()
        val hints = font.split(catalogText(controls), (width - 16).coerceAtLeast(1))
                hints.forEachIndexed { i, line ->
                    graphics.themeText(font, line, 8, height - 36 - (hints.size - i) * font.lineHeight, SpotlightTheme.color(0xFF9DADC3))
                }
    }
}
