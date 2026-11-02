package dev.spotlight.ui

import dev.spotlight.platform.InstalledMods
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen

class ModsScreen(private val parent: Screen?) : Screen(catalogText("Installed mods")) {
    private var draft = ""
    private var mods = InstalledMods.search("")
    private var selected: InstalledMods.Mod? = null
    private var status = InstalledMods.configStatus
    private val rows = mutableListOf<Button>()
    private lateinit var settings: Button
    private lateinit var homepage: Button
    private lateinit var issues: Button
    private var page = 0
    private var left = 0
    private var panelWidth = 0
    private var rowCount = 1

    override fun isPauseScreen() = false
    override fun init() {
        rows.clear()
        panelWidth = minOf(620, (width - 24).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        rowCount = ((height - 204) / 24).coerceIn(1, 15)
        val search = addRenderableWidget(SpotlightEditBox(font, left, 32, panelWidth, 20, catalogText("Search mod name, ID, version or description")))
        search.setMaxLength(200)
        search.value = draft
        search.setResponder { draft = it; mods = InstalledMods.search(it); page = 0; rebuildRows() }
        settings = addRenderableWidget(catalogButton("Native settings", left, height - 100, panelWidth / 3 - 2) {
            selected?.let { mod ->
                try {
                    val screen = InstalledMods.configScreen(mod.id, this)
                    if (screen == null) status = "${mod.name}: no native config screen exposed through Mod Menu"
                    else minecraft.setScreen(screen)
                } catch (failure: Exception) { status = "Config screen failed: ${failure.cause?.message ?: failure.message ?: failure.javaClass.simpleName}" }
                catch (failure: LinkageError) { status = "Mod Menu public API unavailable: ${failure.message}" }
            }
        })
        homepage = addRenderableWidget(catalogButton("Homepage…", left + panelWidth / 3, height - 100, panelWidth / 3 - 2) {
            selected?.homepage?.let { ConfirmLinkScreen.confirmLinkNow(this, it) }
        })
        issues = addRenderableWidget(catalogButton("Issues…", left + panelWidth * 2 / 3, height - 100, panelWidth / 3) {
            selected?.issues?.let { ConfirmLinkScreen.confirmLinkNow(this, it) }
        })
        addRenderableWidget(catalogButton("Previous", left, height - 48, panelWidth / 3 - 2) { page = (page - 1).coerceAtLeast(0); rebuildRows() })
        addRenderableWidget(catalogButton("Next", left + panelWidth / 3, height - 48, panelWidth / 3 - 2) { page++; rebuildRows() })
        addRenderableWidget(catalogButton("Back", left + panelWidth * 2 / 3, height - 48, panelWidth / 3) { onClose() })
        rebuildRows()
        setInitialFocus(search)
    }

    private fun rebuildRows() {
        rows.forEach(::removeWidget)
        rows.clear()
        page = page.coerceIn(0, ((mods.size - 1) / rowCount).coerceAtLeast(0))
        mods.drop(page * rowCount).take(rowCount).forEachIndexed { index, mod ->
            val row = addRenderableWidget(catalogButton("${mod.name} · ${mod.id} · ${mod.version}", left, 60 + index * 24, panelWidth) {
                selected = mod
                status = "${mod.name}: ${mod.description}"
                updateActions()
            })
            row.setTooltip(Tooltip.create(catalogText("${mod.name}\nID: ${mod.id}\nVersion: ${mod.version}\n${mod.description}")))
            rows += row
        }
        updateActions()
    }

    private fun updateActions() {
        settings.active = selected != null && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")
        settings.setTooltip(Tooltip.create(catalogText(InstalledMods.configStatus)))
        homepage.active = selected?.homepage != null
        issues.active = selected?.issues != null
        homepage.setTooltip(Tooltip.create(catalogText(selected?.homepage?.toString() ?: "No valid homepage in metadata")))
        issues.setTooltip(Tooltip.create(catalogText(selected?.issues?.toString() ?: "No valid issues link in metadata")))
    }

    override fun onClose() { minecraft.setScreen(parent) }
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        graphics.themeText(font, catalogText("Installed mods · ${mods.size} matches · page ${page + 1}"), left, 12, SpotlightTheme.color(0xFFF2F5FC))
        graphics.themeText(font, catalogClip(font, "Selected: ${selected?.name ?: "none"}", panelWidth), left, height - 122, SpotlightTheme.color(0xFFF2F5FC))
        graphics.themeText(font, catalogClip(font, status, panelWidth), left, height - 72, SpotlightTheme.color(0xFF9DADC3))
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        if (mouseY in height - 74..height - 60) graphics.setTooltipForNextFrame(catalogText(status), mouseX, mouseY)
    }
}
