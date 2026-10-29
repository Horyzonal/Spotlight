package dev.spotlight.ui

import dev.spotlight.platform.Spotlight
import dev.spotlight.platform.notifySpotlight
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen

class SpotlightSettingsScreen(private val parent: Screen?) : Screen(catalogText("Spotlight settings")) {
    private var error: String? = Spotlight.spotlightSettingsError

    override fun init() {
        val w = minOf(360, (width - 20).coerceAtLeast(1))
        val x = (width - w) / 2
        val y = maxOf(36, (height - 100) / 2)
        val settings = Spotlight.spotlightSettings
        val toggle = addRenderableWidget(catalogButton(label(), x, y, w) {
            try {
                requireNotNull(settings) { "Spotlight settings unavailable" }
                    .setReplaceCommandKey(!settings.replaceCommandKey)
                error = null
            } catch (failure: Exception) {
                error = "Cannot save Spotlight settings: ${failure.message}"
                notifySpotlight(error!!)
            }
            rebuildWidgets()
        })
        toggle.active = settings != null
        toggle.setTooltip(Tooltip.create(catalogText("Use Minecraft's configured command key (default /) to open Spotlight with / pretyped. OFF keeps vanilla command chat.")))
        addRenderableWidget(catalogButton("Back", x, y + 52, w) { onClose() })
        setInitialFocus(toggle)
    }

    private fun label() = "Replace command key: ${if (Spotlight.spotlightSettings?.replaceCommandKey == true) "ON" else "OFF"}"

    override fun onClose() { minecraft.setScreen(parent) }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xEE101722))
        val y = maxOf(36, (height - 100) / 2)
        graphics.themeText(font, catalogText(title), maxOf(10, (width - font.width(title)) / 2), y - 24, SpotlightTheme.color(0xFFF2F5FC))
        graphics.themeText(font, catalogClip(font, "ON: command key opens Spotlight with /", (width - 20).coerceAtLeast(0)), 10, y + 30, SpotlightTheme.color(0xFFF2F5FC))
        error?.let { graphics.themeText(font, catalogClip(font, it, (width - 20).coerceAtLeast(0)), 10, y + 80, SpotlightTheme.color(0xFFFFB4A9)) }
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }
}
