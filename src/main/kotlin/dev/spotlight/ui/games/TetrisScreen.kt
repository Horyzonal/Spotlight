package dev.spotlight.ui.games

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.games.TetrisGame
import dev.spotlight.ui.themeText
import dev.spotlight.ui.SpotlightTheme
import dev.spotlight.ui.catalogText
import dev.spotlight.ui.spotlightButton
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import dev.spotlight.ui.KeyEvent

class TetrisScreen(private val parent: Screen?) : Screen(catalogText("Tetris")) {
    private val game = TetrisGame(System.nanoTime())
    private val restartLabel = "Restart [R]"
    private val pauseLabel = "Pause / Resume [P]"
    private val backLabel = "Back [Esc]"
    private val controls = "← → / A D: move · ↑ / W: rotate · ↓: soft drop · Space: hard drop"
    private val pieceColors = longArrayOf(0xFF81B8FF, 0xFFFFD27D, 0xFFFFC0A0, 0xFF9DADC3, 0xFFFFA49A, 0xFFE0AA85, 0xFFF2F5FC)

    override fun isPauseScreen() = false

    override fun init() {
        val buttonWidth = minOf(140, ((width - 24) / 3).coerceAtLeast(1))
        val left = (width - buttonWidth * 3 - 8) / 2
        addRenderableWidget(spotlightButton(restartLabel, left, height - 28, buttonWidth) { game.restart() })
        addRenderableWidget(spotlightButton(pauseLabel, left + buttonWidth + 4, height - 28, buttonWidth) { game.togglePause() })
        addRenderableWidget(spotlightButton(backLabel, left + (buttonWidth + 4) * 2, height - 28, buttonWidth) { onClose() })
    }

    override fun tick() { super.tick(); game.tick() }
    override fun onClose() { minecraft!!.setScreen(parent) }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        when (event.key()) {
            InputConstants.KEY_ESCAPE -> onClose()
            InputConstants.KEY_R -> game.restart()
            InputConstants.KEY_P -> game.togglePause()
            InputConstants.KEY_LEFT, InputConstants.KEY_A -> game.move(-1)
            InputConstants.KEY_RIGHT, InputConstants.KEY_D -> game.move(1)
            InputConstants.KEY_UP, InputConstants.KEY_W -> game.rotate()
            InputConstants.KEY_DOWN -> game.softDrop()
            InputConstants.KEY_SPACE -> game.hardDrop()
            else -> return super.keyPressed(event.key(), event.scancode(), event.modifiers())
        }
        return true
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.render(graphics, mouseX, mouseY, delta)
        graphics.enableScissor(0, 0, width.coerceAtLeast(1), (height - 32).coerceAtLeast(1))
        val status = when { game.gameOver -> "Game over · $restartLabel"; game.paused -> "Paused · $pauseLabel"; else -> "Playing · $pauseLabel" }
        val header = font.split(catalogText("Tetris · Score: ${game.score} · Lines: ${game.lines} · Level: ${game.level}\nNext: ${game.nextPiece} · $status"), (width - 16).coerceAtLeast(1))
        header.forEachIndexed { i, line -> graphics.themeText(font, line, 8, 8 + i * font.lineHeight, SpotlightTheme.color(0xFFF2F5FC)) }
        val hints = font.split(catalogText(controls), (width - 16).coerceAtLeast(1))
        val bottom = height - 40 - hints.size * font.lineHeight
        hints.forEachIndexed { i, line -> graphics.themeText(font, line, 8, bottom + 4 + i * font.lineHeight, SpotlightTheme.color(0xFF9DADC3)) }
        val top = 16 + header.size * font.lineHeight
        val cell = minOf((width - 16) / TetrisGame.WIDTH, (bottom - top) / TetrisGame.HEIGHT, 22)
        if (cell >= 3) {
            val left = (width - cell * TetrisGame.WIDTH) / 2
            graphics.fill(left - 1, top - 1, left + cell * TetrisGame.WIDTH + 1, top + cell * TetrisGame.HEIGHT + 1, SpotlightTheme.color(0xFF415877))
            val board = game.board
            val active = if (game.gameOver) emptySet() else game.activeCells.toSet()
            for (row in 0 until TetrisGame.HEIGHT) for (column in 0 until TetrisGame.WIDTH) {
                val value = if (column to row in active) game.piece.ordinal + 1 else board[row * TetrisGame.WIDTH + column]
                val x = left + column * cell
                val y = top + row * cell
                graphics.fill(x, y, x + cell, y + cell, SpotlightTheme.color(0xFF19202B))
                if (value != 0) graphics.fill(x, y, x + cell - 1, y + cell - 1, SpotlightTheme.color(pieceColors[value - 1]))
            }
        } else graphics.themeText(font, catalogText("Enlarge window to show board"), 8, top, SpotlightTheme.color(0xFF9DADC3))
        graphics.disableScissor()
    }
}
