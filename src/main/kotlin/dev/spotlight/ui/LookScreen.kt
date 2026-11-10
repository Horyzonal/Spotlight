package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.platform.LookActions
import dev.spotlight.platform.Spotlight
import dev.spotlight.platform.lookDirections
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent

internal class LookScreen(parent: Screen?) : StorageScreen(parent, "Look · rotate only") {
    override fun build() {
        val player = requireNotNull(minecraft.player) { "Join a world first" }
        val eye = player.eyePosition
        text("Your eye X: ${eye.x}")
        text("Your eye Y: ${eye.y}")
        text("Your eye Z: ${eye.z}")
        row("Coordinates · C · X Y Z / ~ offsets") { coordinates() }
        row("Saved locations · L") { minecraft.setScreen(LookLocationsScreen(this)) }
        row("Loaded player heads · H") { minecraft.setScreen(LookHeadsScreen(this)) }
        lookDirections.chunked(2).forEach { directions ->
            row(*directions.toTypedArray()) { index ->
                LookActions.direction(directions[index])
                minecraft.setScreen(null)
            }
        }
        row("Close · Esc") { onClose() }
        text("Tab / Shift+Tab choose · Enter activate")
    }

    private fun coordinates() = prompt("Look at coordinates", "", "Exact X Y Z; ~ relative to feet; Enter apply") {
        LookActions.coordinates(it)
        minecraft.setScreen(null)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.modifiers() == 0) when (event.key()) {
            InputConstants.KEY_C -> { attempt { coordinates() }; return true }
            InputConstants.KEY_L -> { minecraft.setScreen(LookLocationsScreen(this)); return true }
            InputConstants.KEY_H -> { minecraft.setScreen(LookHeadsScreen(this)); return true }
        }
        return super.keyPressed(event)
    }
}

private class LookLocationsScreen(parent: Screen) : StorageScreen(parent, "Look · saved locations in this dimension") {
    override fun build() {
        val scope = Spotlight.scope()
        val dimension = Spotlight.dimension()
        val locations = Spotlight.locations().list(scope).filter { it.dimension == dimension }
        if (locations.isEmpty()) text("No saved locations in this world and dimension")
        locations.forEach { location ->
            row(location.name) {
                LookActions.location(location)
                minecraft.setScreen(null)
            }
            text("${location.x} ${location.y} ${location.z}")
        }
        row("Back · Esc") { onClose() }
    }
}

private class LookHeadsScreen(parent: Screen) : StorageScreen(parent, "Look · loaded player head / eye coordinates") {
    private val level = net.minecraft.client.Minecraft.getInstance().level

    override fun build() {
        require(level != null && minecraft.level === level) { "World changed; reopen Look" }
        val players = level.players().filter { it !== minecraft.player && it.isAlive && !it.isRemoved }.sortedBy { it.displayName.string }
        if (players.isEmpty()) text("No other loaded players")
        players.forEach { player ->
            val id = player.uuid
            row(player.displayName.string) {
                require(minecraft.level === level) { "World changed; reopen Look" }
                LookActions.head(id)
                minecraft.setScreen(null)
            }
            val eye = player.eyePosition
            text("Eye X: ${eye.x}")
            text("Eye Y: ${eye.y}")
            text("Eye Z: ${eye.z}")
        }
        row("Refresh") { rebuild() }
        row("Back · Esc") { onClose() }
    }
}
