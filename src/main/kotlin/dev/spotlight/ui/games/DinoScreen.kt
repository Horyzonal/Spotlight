package dev.spotlight.ui.games

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.games.DinoGame
import dev.spotlight.ui.themeText
import dev.spotlight.ui.SpotlightTheme
import dev.spotlight.ui.catalogText
import dev.spotlight.ui.spotlightButton
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import kotlin.math.roundToInt

class DinoScreen(private val parent: Screen?) : Screen(catalogText("Dino Runner")) {
    private val game = DinoGame(System.nanoTime())
    private val controls = "Space / Up: jump · Hold Down: duck · P: pause / resume · R: restart · Esc: back"

    override fun isPauseScreen() = false

    override fun init() {
        game.setDucking(false)
        val buttonWidth = minOf(140, ((width - 24) / 2).coerceAtLeast(1))
        val left = (width - buttonWidth * 2 - 8) / 2
        addRenderableWidget(spotlightButton("Restart [R]", left, height - 28, buttonWidth) { game.restart() })
        addRenderableWidget(spotlightButton("Back [Esc]", left + buttonWidth + 8, height - 28, buttonWidth) { onClose() })
    }

    override fun tick() { super.tick(); game.tick() }
    override fun onClose() { minecraft.setScreen(parent) }

    override fun keyPressed(event: KeyEvent): Boolean {
        when (event.key()) {
            InputConstants.KEY_ESCAPE -> onClose()
            InputConstants.KEY_R -> game.restart()
            InputConstants.KEY_P -> game.togglePause()
            InputConstants.KEY_SPACE, InputConstants.KEY_UP -> game.jump()
            InputConstants.KEY_DOWN -> game.setDucking(true)
            else -> return super.keyPressed(event)
        }
        return true
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        if (event.key() == InputConstants.KEY_DOWN) { game.setDucking(false); return true }
        return super.keyReleased(event)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.render(graphics, mouseX, mouseY, delta)
        graphics.enableScissor(0, 0, width.coerceAtLeast(1), (height - 32).coerceAtLeast(1))
        val status = when {
            game.gameOver -> "Game over · R: restart"
            game.paused -> "Paused · P: resume"
            else -> "Jump rocks · Duck gliders · Speed: %.1f".format(game.speed)
        }
        val header = font.split(catalogText("Dino Runner · Score: ${game.score}\n$status"), (width - 16).coerceAtLeast(1))
        header.forEachIndexed { i, line -> graphics.themeText(font, line, 8, 8 + i * font.lineHeight, SpotlightTheme.color(0xFFF2F5FC)) }
        val top = 16 + header.size * font.lineHeight
        val hints = font.split(catalogText(controls), (width - 16).coerceAtLeast(1))
        val bottom = height - 40 - hints.size * font.lineHeight
        hints.forEachIndexed { i, line -> graphics.themeText(font, line, 8, bottom + 4 + i * font.lineHeight, SpotlightTheme.color(0xFF9DADC3)) }
        val scale = minOf((width - 16) / DinoGame.WIDTH.toDouble(), (bottom - top) / DinoGame.HEIGHT.toDouble(), 2.0)
        if (scale >= 0.5) {
            val left = (width - DinoGame.WIDTH * scale) / 2
            val ground = top + DinoGame.HEIGHT * scale
            fun rect(x: Double, y: Double, w: Double, h: Double, color: Long) {
                graphics.fill((left + x * scale).roundToInt(), (ground - (y + h) * scale).roundToInt(),
                    (left + (x + w) * scale).roundToInt(), (ground - y * scale).roundToInt(), SpotlightTheme.color(color))
            }
            graphics.enableScissor(left.roundToInt(), top, (left + DinoGame.WIDTH * scale).roundToInt(), ground.roundToInt() + 1)
            rect(0.0, 0.0, DinoGame.WIDTH.toDouble(), DinoGame.HEIGHT.toDouble(), 0xFF19202B)
            rect(0.0, 0.0, DinoGame.WIDTH.toDouble(), 1.0, 0xFF415877)
            for (i in 0..15) rect((i * 23.0 - game.score % 23 + DinoGame.WIDTH) % DinoGame.WIDTH, 3.0, 5.0, 1.0, 0xFF415877)
            val x = DinoGame.PLAYER_X
            val y = game.altitude
            val h = game.playerHeight
            
            rect(x + 3, y + 6, 12.0, h - 12, 0xFF81B8FF)
            rect(x + 8, y + h - 10, 10.0, 8.0, 0xFF81B8FF)
            rect(x + 4, y + h - 2, 8.0, 2.0, 0xFFFFD27D)
            rect(x, y + 8, 5.0, 5.0, 0xFF81B8FF)
            rect(x + 14, y + h - 6, 2.0, 2.0, 0xFF19202B)
            val stride = if (game.altitude > 0 || game.paused || game.gameOver || game.score / 3 % 2 == 0L) 0.0 else 2.0
            rect(x + 3, y + stride, 4.0, 6.0, 0xFFF2F5FC)
            rect(x + 11, y + 2 - stride, 4.0, 6.0, 0xFFF2F5FC)
            game.obstacles.forEach { obstacle ->
                val kind = obstacle.kind
                if (kind == DinoGame.Kind.GLIDER) {
                    rect(obstacle.x, kind.bottom + 4, kind.width, 4.0, 0xFFFFD27D)
                    rect(obstacle.x + 6, kind.bottom, 5.0, kind.height, 0xFFFFD27D)
                    rect(obstacle.x + 16, kind.bottom + 2, 5.0, 8.0, 0xFFFFD27D)
                } else {
                    rect(obstacle.x, 0.0, kind.width, kind.height - 5, 0xFFFFC0A0)
                    rect(obstacle.x + 3, kind.height - 5, kind.width - 6, 5.0, 0xFFFFC0A0)
                    rect(obstacle.x + 4, 5.0, 2.0, kind.height - 12, 0xFF415877)
                }
            }
            graphics.disableScissor()
        } else graphics.themeText(font, catalogText("Enlarge window to show track"), 8, top, SpotlightTheme.color(0xFF9DADC3))
        graphics.disableScissor()
    }
}
