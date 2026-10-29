package dev.spotlight.ui

import dev.spotlight.core.SavedLocation
import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.platform.LocationActions
import dev.spotlight.platform.notifySpotlight
import net.minecraft.client.gui.GuiGraphicsExtractor

import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.client.input.KeyEvent


internal class LocationEditScreen(private val parent: Screen?, private val location: SavedLocation) : Screen(catalogText("Edit location")) {
    private val labels = listOf("Name", "X", "Y", "Z", "Dimension")
    private val values = mutableListOf(location.name, location.x.toString(), location.y.toString(), location.z.toString(), location.dimension)
    private val fields = mutableListOf<EditBox>()
    private var error: String? = null

    override fun isPauseScreen() = false

    override fun init() {
        fields.clear()
        val w = minOf(SpotlightTheme.metric("locationWidth", 360, 160, 4096), (width - 70).coerceAtLeast(1))
        val x = (width - w) / 2
        val step = ((height - 80) / 5).coerceIn(20, 28)
        val y = maxOf(16, (height - (step * 5 + 60)) / 2)
        labels.forEachIndexed { index, label ->
            val field = addRenderableWidget(SpotlightEditBox(font, x, y + index * step, w, 20, catalogText(label)))
            field.setMaxLength(128)
            field.value = values[index]
            field.setResponder { values[index] = it }
            fields += field
        }
        addRenderableWidget(catalogButton("Save", x, y + step * 5, (w - 4) / 2) { save() })
        addRenderableWidget(catalogButton("Cancel", x + (w + 4) / 2, y + step * 5, (w - 4) / 2) { onClose() })
        setInitialFocus(fields.first())
    }

    private fun save() {
        try {
            require(values[4].matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+"))) { "Enter a namespaced dimension ID" }
            fun coordinate(index: Int): Double = requireNotNull(values[index].toDoubleOrNull()?.takeIf { it.isFinite() }) { "${labels[index]} must be finite" }
            val draft = location.copy(name = values[0].trim(), x = coordinate(1), y = coordinate(2),
                z = coordinate(3), dimension = values[4])
            LocationActions.save(draft)
            notifySpotlight("Saved location ${draft.name}")
            minecraft.setScreen(parent)
        } catch (failure: Exception) {
            error = failure.message ?: failure.javaClass.simpleName
            fields.first().message = catalogText("Name. $error")
            setFocused(fields.first())
            triggerImmediateNarration(true)
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isConfirmation && primaryModifier(event)) { save(); return true }
        if (event.isEscape || (primaryModifier(event) && !event.hasAltDown() && !event.hasShiftDown() && event.key() == InputConstants.KEY_W)) { onClose(); return true }
        return super.keyPressed(event)
    }

    override fun onClose() { minecraft.setScreen(parent) }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xEE101722))
        fields.forEachIndexed { index, field -> graphics.themeText(font, catalogText(labels[index]), maxOf(2, field.x - 60), field.y + 6, SpotlightTheme.color(0xFFF2F5FC)) }
        error?.let { graphics.themeText(font, catalogClip(font, it, (width - 20).coerceAtLeast(0)), 10, height - 44, SpotlightTheme.color(0xFFFFB4A9)) }
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        catalogHints(graphics, font, width, height, "${primaryModifierLabel}+Enter Save · Tab Next · Esc / ${primaryModifierLabel}+W Cancel")
    }
}
