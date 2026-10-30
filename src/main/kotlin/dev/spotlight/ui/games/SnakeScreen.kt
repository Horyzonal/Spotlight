package dev.spotlight.ui.games

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.games.SnakeGame
import dev.spotlight.ui.themeText
import dev.spotlight.ui.SpotlightTheme
import dev.spotlight.ui.catalogText
import dev.spotlight.ui.spotlightButton
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import dev.spotlight.ui.KeyEvent

class SnakeScreen(private val parent: Screen?) : Screen(catalogText("Snake")) {
    private val game = SnakeGame(System.nanoTime())
    private val controls = "Arrows / WASD: turn · P: pause / resume · R: restart · Esc: back"

    override fun isPauseScreen() = false

    override fun init() {
        val buttonWidth = minOf(140, ((width - 24) / 2).coerceAtLeast(1))
        val left = (width - buttonWidth * 2 - 8) / 2
        addRenderableWidget(spotlightButton("Restart [R]", left, height - 28, buttonWidth) { game.restart() })
        addRenderableWidget(spotlightButton("Back [Esc]", left + buttonWidth + 8, height - 28, buttonWidth) { onClose() })
    }

    override fun tick() { super.tick(); game.tick() }
    override fun onClose() { minecraft!!.setScreen(parent) }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        when (event.key()) {
            InputConstants.KEY_ESCAPE -> onClose()
            InputConstants.KEY_R -> game.restart()
            InputConstants.KEY_P -> game.togglePause()
            InputConstants.KEY_UP, InputConstants.KEY_W -> game.turn(SnakeGame.Direction.UP)
            InputConstants.KEY_DOWN, InputConstants.KEY_S -> game.turn(SnakeGame.Direction.DOWN)
            InputConstants.KEY_LEFT, InputConstants.KEY_A -> game.turn(SnakeGame.Direction.LEFT)
            InputConstants.KEY_RIGHT, InputConstants.KEY_D -> game.turn(SnakeGame.Direction.RIGHT)
            else -> return super.keyPressed(event.key(), event.scancode(), event.modifiers())
        }
        return true
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.render(graphics, mouseX, mouseY, delta)
        graphics.enableScissor(0, 0, width.coerceAtLeast(1), (height - 32).coerceAtLeast(1))
        val status = when {
            game.won -> "Board filled · You win! · R: restart"
            game.gameOver -> "Game over · R: restart"
            game.paused -> "Paused · P: resume"
            else -> "Eat food, avoid walls and yourself"
        }
        val header = font.split(catalogText("Snake · Score: ${game.score}\n$status"), (width - 16).coerceAtLeast(1))
        header.forEachIndexed { i, line -> graphics.themeText(font, line, 8, 8 + i * font.lineHeight, SpotlightTheme.color(0xFFF2F5FC)) }
        val top = 16 + header.size * font.lineHeight
        val hints = font.split(catalogText(controls), (width - 16).coerceAtLeast(1))
        val bottom = height - 40 - hints.size * font.lineHeight
        hints.forEachIndexed { i, line -> graphics.themeText(font, line, 8, bottom + 4 + i * font.lineHeight, SpotlightTheme.color(0xFF9DADC3)) }
        val cell = minOf((width - 16) / game.width, (bottom - top) / game.height, 18)
        if (cell >= 3) {
            val left = (width - cell * game.width) / 2
            graphics.fill(left - 1, top - 1, left + cell * game.width + 1, top + cell * game.height + 1, SpotlightTheme.color(0xFF415877))
            graphics.fill(left, top, left + cell * game.width, top + cell * game.height, SpotlightTheme.color(0xFF19202B))
            game.snake.forEachIndexed { i, part ->
                val x = left + part.x * cell
                val y = top + part.y * cell
                graphics.fill(x, y, x + cell - 1, y + cell - 1, SpotlightTheme.color(if (i == 0) 0xFFF2F5FC else 0xFF81B8FF))
            }
            game.food?.let {
                val x = left + it.x * cell
                val y = top + it.y * cell
                graphics.fill(x, y, x + cell - 1, y + cell - 1, SpotlightTheme.color(0xFFFFD27D))
            }
        } else graphics.themeText(font, catalogText("Enlarge window to show board"), 8, top, SpotlightTheme.color(0xFF9DADC3))
        graphics.disableScissor()
    }
}
