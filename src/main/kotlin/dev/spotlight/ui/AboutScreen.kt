package dev.spotlight.ui

import dev.spotlight.platform.InstalledMods
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import java.net.URI

class AboutScreen(private val parent: Screen?) : Screen(catalogText("About Spotlight")) {
    private val metadata = FabricLoader.getInstance().getModContainer("spotlight").orElseThrow().metadata
    private var left = 0
    private var top = 0
    private var panelWidth = 0

    override fun isPauseScreen() = false
    override fun onClose() { minecraft.gui.setScreen(parent) }

    override fun init() {
        panelWidth = minOf(360, (width - 24).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        top = ((height - 224) / 2).coerceAtLeast(8)
        fun link(label: String, row: Int, uri: URI?) {
            addRenderableWidget(catalogButton(label, left + 12, top + 112 + row * 24, panelWidth - 24) {
                uri?.let { ConfirmLinkScreen.confirmLinkNow(this, it) }
            }).active = uri != null
        }
        link("Personal website", 0, URI("https://hory.zone/"))
        link("Source repository", 1, URI("https://codeberg.org/horyzon/Spotlight/"))
        link("Mod homepage", 2, metadata.contact.get("homepage").orElse(null)?.let(InstalledMods::webLink))
        addRenderableWidget(catalogButton("Back", left + 12, top + 188, panelWidth - 24) { onClose() })
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        g.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        g.fill(left, top, left + panelWidth, top + 220, SpotlightTheme.color(0xFF202630))
        g.blit(RenderPipelines.GUI_TEXTURED, Identifier.parse("spotlight:textures/gui/pfp.png"), left + 12, top + 12,
            0f, 0f, 64, 64, 800, 800, 800, 800)
        g.themeText(font, "Spotlight", left + 88, top + 20, SpotlightTheme.color(0xFFF2F5FC))
        g.themeText(font, "Made by Horyzon", left + 88, top + 38, SpotlightTheme.color(0xFFF2F5FC))
        g.themeText(font, "Version ${metadata.version.friendlyString}", left + 88, top + 56, SpotlightTheme.color(0xFF9DADC3))
        super.extractRenderState(g, mouseX, mouseY, delta)
    }
}
