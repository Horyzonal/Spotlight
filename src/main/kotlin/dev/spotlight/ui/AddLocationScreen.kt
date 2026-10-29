package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.SavedLocation
import dev.spotlight.platform.LocationActions
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent

internal class AddLocationScreen(private val parent: Screen?) : Screen(catalogText("Add location")) {
    private var error: String? = null

    override fun isPauseScreen() = false

    override fun init() {
        val w = minOf(SpotlightTheme.metric("locationWidth", 360, 160, 4096), (width - 20).coerceAtLeast(1))
        val x = (width - w) / 2
        val y = maxOf(28, (height - 152) / 2)
        val current = addRenderableWidget(catalogButton("Current position · Enter", x, y, w) { choose { LocationActions.current() } })
        addRenderableWidget(catalogButton("Look at block · L · Up to 256 blocks", x, y + 26, w) { choose { LocationActions.lookedAt() } })
        addRenderableWidget(catalogButton("Custom coordinates · C · Starts at current", x, y + 52, w) { custom() })
        addRenderableWidget(catalogButton("Clipboard coordinates · V · X Y Z or X, Y, Z", x, y + 78, w) { clipboard() })
        addRenderableWidget(catalogButton("Cancel · Esc / ${primaryModifierLabel}+W", x, y + 104, w) { onClose() })
        setInitialFocus(current)
    }

    private fun choose(source: () -> SavedLocation) {
        try {
            minecraft.gui.setScreen(LocationEditScreen(parent, source()))
        } catch (failure: Exception) {
            error = failure.message ?: failure.javaClass.simpleName
            focused?.let { if (it is net.minecraft.client.gui.components.AbstractWidget) it.message = catalogText("Add location. $error") }
            triggerImmediateNarration(true)
        }
    }

    private fun custom() = choose { LocationActions.current().copy(name = "New location") }

    private fun clipboard() = choose {
        val coordinates = parseLocationCoordinates(minecraft.keyboardHandler.clipboard)
        LocationActions.current().copy(name = "New location", x = coordinates[0], y = coordinates[1], z = coordinates[2])
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape || (primaryModifier(event) && !event.hasAltDown() && !event.hasShiftDown() && event.key() == InputConstants.KEY_W)) {
            onClose()
            return true
        }
        if (focused !is EditBox && event.modifiers() == 0) {
            when {
                event.isConfirmation -> { choose { LocationActions.current() }; return true }
                event.key() == InputConstants.KEY_L -> { choose { LocationActions.lookedAt() }; return true }
                event.key() == InputConstants.KEY_C -> { custom(); return true }
                event.key() == InputConstants.KEY_V -> { clipboard(); return true }
            }
        }
        return super.keyPressed(event)
    }

    override fun onClose() { minecraft.gui.setScreen(parent) }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xEE101722))
        val y = maxOf(28, (height - 152) / 2)
        graphics.themeText(font, catalogText(title), maxOf(10, (width - font.width(catalogText(title))) / 2), y - 20, SpotlightTheme.color(0xFFF2F5FC))
        error?.let { graphics.themeText(font, catalogClip(font, it, (width - 20).coerceAtLeast(0)), 10, y + 132, SpotlightTheme.color(0xFFFFB4A9)) }
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }
}

internal fun parseLocationCoordinates(text: String): List<Double> {
    val message = "Clipboard must contain exactly three finite coordinates: X Y Z or X, Y, Z"
    require(text.length <= 512 && text.none { it.isISOControl() && it != '\n' && it != '\r' }) { message }
    val number = "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"
    require(text.trim(' ', '\n', '\r').matches(Regex("$number(?:[ \\r\\n]+|[ \\r\\n]*,[ \\r\\n]*)$number(?:[ \\r\\n]+|[ \\r\\n]*,[ \\r\\n]*)$number"))) { message }
    return text.trim(' ', '\n', '\r').split(Regex("[ ,\\r\\n]+")).map {
        requireNotNull(it.toDoubleOrNull()?.takeIf { value -> value.isFinite() }) { message }
    }
}
