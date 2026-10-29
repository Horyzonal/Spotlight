package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.Fuzzy
import dev.spotlight.core.Entry
import dev.spotlight.core.Macro
import dev.spotlight.platform.MacroRuntime
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component

class MacroListScreen(private val parent: SpotlightScreen) : Screen(catalogText("Macros")) {
    private var query = ""
    private var macros = emptyList<Macro>()
    private var results = emptyList<Macro>()
    private var selected = 0
    private var offset = 0
    private val rowStep get() = SpotlightTheme.metric("compactRowHeight", 24, 22, 80) + 2
    private var count = 1
    private var left = 0
    private var panelWidth = 1
    private var error: String? = null
    private lateinit var search: EditBox
    private val rows = mutableListOf<Button>()

    override fun isPauseScreen() = false
    override fun init() {
        rows.clear()
        panelWidth = minOf(SpotlightTheme.metric("paletteWidth", 560, 160, 4096), (width - 24).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        count = ((height - 180) / rowStep).coerceIn(1, 12)
        val tabWidth = (panelWidth - 8) / 3
        listOf("All", "Locations", "• Macros").forEachIndexed { index, label ->
            addRenderableWidget(macroButton("$label · ${primaryModifierLabel}+${index + 1}", left + index * (tabWidth + 4), 16, tabWidth) { tab(index) })
        }
        search = addRenderableWidget(SpotlightEditBox(font, left, 44, panelWidth, 20, catalogText("Search macros")))
        search.value = query
        search.setResponder { query = it; selected = 0; offset = 0; refresh() }
        try { macros = MacroRuntime.store().list() }
        catch (failure: Exception) { error = failure.message ?: failure.javaClass.simpleName; macros = emptyList() }
        val cell = (panelWidth - 12) / 4
        listOf("New · ${primaryModifierLabel}+N" to { edit(true) }, "Edit · ${primaryModifierLabel}+E" to { edit(false) }, "Delete… · ${primaryModifierLabel}+Shift+D" to { delete() }, "Back · Esc" to { onClose() }).forEachIndexed { index, (label, action) ->
            addRenderableWidget(macroButton(label, left + index * (cell + 4), height - 94, cell, onPress = action))
        }
        refresh()
        setInitialFocus(search)
    }

    private fun refresh() {
        val ids = Fuzzy.search(query, macros.map { Entry(it.id, it.name, it.template) }, limit = macros.size).map { it.id }
        val byId = macros.associateBy { it.id }
        results = ids.mapNotNull(byId::get)
        selected = selected.coerceIn(0, (results.size - 1).coerceAtLeast(0))
        offset = offset.coerceIn((selected - count + 1).coerceAtLeast(0), selected)
        rows.forEach(::removeWidget)
        rows.clear()
        results.drop(offset).take(count).forEachIndexed { index, macro ->
            rows += addRenderableWidget(macroButton("${if (offset + index == selected) "• " else ""}${macro.name} · Click Edit / Enter Run · ${macro.template}", left, 72 + index * rowStep, panelWidth) {
                selected = offset + index
                minecraft.gui.setScreen(MacroEditorScreen(this, macro))
            })
        }
        search.message = catalogText("Search macros. ${results.getOrNull(selected)?.name ?: "No matches"}. ${selected + 1} of ${results.size}. ${error.orEmpty()}")
    }

    private fun chosen(): Macro? = results.getOrNull(rows.indexOfFirst { it.isFocused }.takeIf { it >= 0 }?.plus(offset) ?: selected)
    private fun run() { chosen()?.let { minecraft.gui.setScreen(MacroRunScreen(this, it)) } }
    private fun edit(new: Boolean) {
        val macro = if (new) null else chosen() ?: return
        minecraft.gui.setScreen(MacroEditorScreen(this, macro))
    }
    private fun delete() {
        val macro = chosen() ?: return
        minecraft.gui.setScreen(ConfirmSpotlightScreen(this, "Delete ${macro.name}?", "This removes the saved macro. This cannot be undone.") {
            try { MacroRuntime.store().remove(macro.id); macros = MacroRuntime.store().list(); refresh() }
            catch (failure: Exception) { error = failure.message ?: failure.javaClass.simpleName }
        })
    }
    private fun tab(index: Int) { if (index != 2) { minecraft.gui.setScreen(parent); parent.openMacroTab(index) } }
    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (primaryModifier(event) && !event.hasAltDown()) {
            when (event.key()) {
                InputConstants.KEY_TAB -> { tab(cyclePaletteTab(2, event.hasShiftDown())); return true }
                InputConstants.KEY_1 -> { tab(0); return true }
                InputConstants.KEY_2 -> { tab(1); return true }
                InputConstants.KEY_3 -> return true
                InputConstants.KEY_N -> if (!event.hasShiftDown()) { edit(true); return true }
                InputConstants.KEY_E -> if (!event.hasShiftDown()) { edit(false); return true }
                InputConstants.KEY_D -> if (event.hasShiftDown()) { delete(); return true }
                InputConstants.KEY_W -> { onClose(); return true }
            }
        }
        if (!primaryModifier(event) && !event.hasAltDown() && (search.isFocused || rows.any { it.isFocused })) {
            if (event.isDown || event.isUp) {
                selected = (rows.indexOfFirst { it.isFocused }.takeIf { it >= 0 }?.plus(offset) ?: selected) + if (event.isDown) 1 else -1
                refresh(); setFocused(search); return true
            }
            if (event.isConfirmation) { run(); return true }
        }
        return super.keyPressed(event)
    }
    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (vertical != 0.0) { selected += if (vertical > 0) -1 else 1; refresh(); return true }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }
    override fun onClose() { minecraft.gui.setScreen(parent) }
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        if (results.isEmpty()) graphics.themeText(font, catalogText("No macros. ${primaryModifierLabel}+N creates one."), left, 76, SpotlightTheme.color(0xFF9DADC3))
        error?.let { graphics.themeText(font, catalogClip(font, it, panelWidth), left, height - 110, SpotlightTheme.color(0xFFFFB4A9)) }

    }
}
