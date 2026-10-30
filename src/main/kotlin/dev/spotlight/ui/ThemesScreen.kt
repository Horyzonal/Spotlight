package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.ThemeColor
import dev.spotlight.core.ThemeEntry
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent

class ThemesScreen(private val parent: Screen?) : Screen(catalogText("Themes")) {
    private var query = ""
    private var entries = emptyList<ThemeEntry>()
    private var results = emptyList<ThemeEntry>()
    private var selectedId = "default"
    private var offset = 0
    private var count = 1
    private var left = 0
    private var panelWidth = 1
    private var notice = "Select a theme; Apply changes appearance immediately."
    private var failed = false
    private lateinit var search: SpotlightEditBox
    private lateinit var apply: Button
    private lateinit var edit: Button
    private val rows = mutableListOf<Button>()
    private val swatches = listOf(ThemeColor.SCREEN_BACKGROUND, ThemeColor.ROW_SELECTED, ThemeColor.ACCENT, ThemeColor.TEXT, ThemeColor.ERROR)

    override fun isPauseScreen() = false
    override fun onClose() { minecraft.setScreen(parent) }
    override fun init() {
        rows.clear()
        panelWidth = minOf(620, (width - 24).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        count = ((height - 192) / 26).coerceIn(1, 14)
        search = addRenderableWidget(SpotlightEditBox(font, left, 30, panelWidth, 20, catalogText("Search themes")))
        search.value = query
        search.setResponder { query = it; offset = 0; refresh() }
        try {
            entries = SpotlightTheme.library().list().sortedBy { if (it.id == "default") 0 else if (it.id == "current") 1 else if (it.builtin) 2 else 3 }
        } catch (failure: Exception) { entries = emptyList(); error(failure) }
        val cell = ((panelWidth - 16) / 5).coerceAtLeast(1)
        apply = addRenderableWidget(macroButton("Apply · Enter", left, height - 48, cell) { applySelected() })
        edit = addRenderableWidget(macroButton("Edit · $primaryModifierLabel+E", left + cell + 4, height - 48, cell) { editSelected() })
        addRenderableWidget(macroButton("New · $primaryModifierLabel+N", left + (cell + 4) * 2, height - 48, cell) { minecraft.setScreen(ThemeEditorScreen(this)) })
        addRenderableWidget(macroButton("Import · $primaryModifierLabel+I", left + (cell + 4) * 3, height - 48, cell) { openImport() })
                addRenderableWidget(macroButton("Back · Esc", left + (cell + 4) * 4, height - 48, cell) { onClose() })
        refresh()
        setInitialFocus(search)
    }

    private fun refresh() {
        results = entries.filter { it.name.contains(query, true) || it.id.contains(query, true) }
        if (results.none { it.id == selectedId }) selectedId = results.firstOrNull()?.id.orEmpty()
        val selected = results.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        offset = offset.coerceIn((selected - count + 1).coerceAtLeast(0), selected)
        rows.forEach(::removeWidget)
        rows.clear()
        results.drop(offset).take(count).forEachIndexed { index, entry ->
            val label = "${if (entry.id == selectedId) "• " else ""}${entry.name}"
            rows += addRenderableWidget(macroButton(font.plainSubstrByWidth(label, (panelWidth - 190).coerceAtLeast(1)), left, 58 + index * 26, panelWidth) {
                selectedId = entry.id
                refresh()
            })
        }
        apply.active = chosen() != null
        edit.active = chosen() != null
        search.message = catalogText("Search themes. Selected: ${chosen()?.name ?: "No matches"}. ${results.size} themes")
    }

    fun showImported(entry: ThemeEntry) {
            query = ""
            selectedId = entry.id
            offset = 0
            failed = false
            notice = "Imported ${entry.name} · not applied; select Apply to use it"
            minecraft.setScreen(this)
        }

        private fun openImport() { minecraft.setScreen(ThemeImportScreen(this)) }
        private fun chosen() = results.find { it.id == selectedId }
    private fun editSelected() { chosen()?.let { minecraft.setScreen(ThemeEditorScreen(this, it)) } }
    private fun error(failure: Exception) { failed = true; notice = failure.message ?: failure.javaClass.simpleName }
    private fun applySelected() {
        val entry = chosen() ?: return
        try {
            val report = SpotlightTheme.apply(entry.id)
            check(report.warnings.isEmpty()) { report.warnings.joinToString("; ") }
            failed = false
            notice = "Applied ${entry.name} · no restart needed"
            rebuildWidgets()
        } catch (failure: Exception) { error(failure) }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (primaryModifier(event) && !event.hasAltDown() && !event.hasShiftDown()) {
            if (event.key() == InputConstants.KEY_N) { minecraft.setScreen(ThemeEditorScreen(this)); return true }
            if (event.key() == InputConstants.KEY_E) { editSelected(); return true }
                        if (event.key() == InputConstants.KEY_I) { openImport(); return true }
        }
        if (!primaryModifier(event) && !event.hasAltDown() && (search.isFocused || rows.any { it.isFocused })) {
            if (event.isConfirmation) { applySelected(); return true }
            if (event.isUp || event.isDown) {
                move(if (event.isDown) 1 else -1)
                setFocused(search)
                return true
            }
        }
        return super.keyPressed(event)
    }
    private fun move(delta: Int) {
        if (results.isEmpty()) return
        selectedId = results[(results.indexOfFirst { it.id == selectedId } + delta).coerceIn(0, results.lastIndex)].id
        refresh()
    }
    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (vertical != 0.0 && mouseX >= left && mouseX <= left + panelWidth && mouseY in 58.0..(58 + count * 26).toDouble()) {
            move(if (vertical > 0) -1 else 1); return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.render(graphics, mouseX, mouseY, delta)
        graphics.themeText(font, title, left, 12, SpotlightTheme.color(0xFFF2F5FC))
        rows.forEachIndexed { index, row ->
            results.getOrNull(offset + index)?.let { entry ->
                swatches.forEachIndexed { i, token ->
                    val x = left + panelWidth - 88 + i * 16
                    graphics.fill(x, row.y + 5, x + 12, row.y + 15, entry.config.color(token.defaultArgb))
                }
            }
        }
        val detailY = 62 + count * 26
        chosen()?.let { entry ->
            graphics.themeText(font, catalogClip(font, "${entry.name} · ${if (entry.builtin || entry.id == "current") "Edit saves a copy" else "Custom theme"}", panelWidth), left, detailY, SpotlightTheme.color(0xFFF2F5FC))
            val previewY = detailY + 16
            graphics.fill(left, previewY, left + panelWidth, previewY + 26, entry.config.color(ThemeColor.PANEL_BACKGROUND.defaultArgb))
            graphics.fill(left + 4, previewY + 4, left + 8, previewY + 22, entry.config.color(ThemeColor.ACCENT.defaultArgb))
            graphics.drawString(font, catalogClip(font, "Selected preview · Text / accent / background", panelWidth - 20, entry.config.textShadow()),
                left + 14, previewY + 9, entry.config.color(ThemeColor.TEXT.defaultArgb), entry.config.textShadow())
        }
        graphics.themeText(font, catalogClip(font, if (results.isEmpty() && !failed) "No matching themes" else notice, panelWidth), left, height - 65, SpotlightTheme.color(if (failed) 0xFFFFB4A9 else 0xFF9DADC3))
    }
}
