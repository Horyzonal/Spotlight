package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

internal data class PaletteMenuAction(
    val label: String,
    val enabled: Boolean = true,
    val shortcut: String = "",
    val group: String = "",
    val run: () -> Unit,
)

internal class CommandMenuScreen(
    private val parent: SpotlightScreen,
    private val actions: List<PaletteMenuAction>,

) : Screen(Component.literal("Spotlight actions")) {
    private val entries = menuRows(actions.map { it.group })
    private var selected = actions.indexOfFirst { it.enabled }.coerceAtLeast(0)
    private var offset = 0
    private var count = 1
    private var left = 0
    private var top = 0
    private var menuWidth = 1
    private var menuHeight = 1

    override fun isPauseScreen() = false

    override fun init() {
        menuWidth = minOf(SpotlightTheme.metric("layout.menuWidth", 360, 160), (width - 8).coerceAtLeast(1))
        count = ((height - 48) / 22).coerceIn(1, entries.size.coerceAtLeast(1))
        menuHeight = count * 22 + 36
        left = ((width - menuWidth) / 2).coerceAtLeast(0)
        top = ((height - menuHeight) / 2).coerceAtLeast(0)
        rebuildMenu()
    }

    private fun rebuildMenu() {
        clearWidgets()
        offset = menuOffset(entries, selected, offset, count)
        entries.drop(offset).take(count).forEachIndexed { index, row ->
            val action = actions.getOrNull(row.actionIndex) ?: return@forEachIndexed
            val narration = listOf(action.group, action.label, action.shortcut).filter { it.isNotEmpty() }.joinToString(". ")
            val button = object : Button(left + 4, top + 17 + index * 22, (menuWidth - 8).coerceAtLeast(1), 20,
                Component.literal(narration), OnPress { choose(row.actionIndex) }, CreateNarration { it.get() }) {
                override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
                    val highlighted = active && (isHovered || isFocused)
                    graphics.fill(x, y, right, bottom, SpotlightTheme.color(when {
                        !active -> 0xFF19202B
                        highlighted -> 0xFF293F5B
                        else -> 0xFF161D29
                    }))
                    graphics.renderOutline(x, y, this.width, this.height,
                        SpotlightTheme.color(if (highlighted) 0xFF81B8FF else 0xFF415877))
                    val shortcut = font.plainSubstrByWidth(action.shortcut, ((this.width - 18) * 2 / 3).coerceAtLeast(0))
                    val shortcutX = right - 6 - font.width(shortcut)
                    val labelWidth = (if (shortcut.isEmpty()) this.width - 12 else shortcutX - x - 14).coerceAtLeast(0)
                    val textY = y + (this.height - font.lineHeight) / 2
                    graphics.themeText(font, font.plainSubstrByWidth(action.label, labelWidth), x + 6, textY,
                        SpotlightTheme.color(if (active) 0xFFF2F5FC else 0xFF91A0B5))
                    graphics.themeText(font, shortcut, shortcutX, textY,
                        SpotlightTheme.color(if (active) 0xFF9DADC3 else 0xFF91A0B5))
                }
            }
            button.active = action.enabled
            addRenderableWidget(button)
            if (row.actionIndex == selected && button.active) setInitialFocus(button)
        }
    }

    private fun choose(index: Int) {
        val action = actions.getOrNull(index)?.takeIf { it.enabled } ?: return
        minecraft!!.setScreen(parent)
        action.run()
    }

    override fun onClose() { minecraft!!.setScreen(parent) }

    private fun moveSelection(direction: Int) {
        val enabled = entries.map { it.actionIndex }.filter { actions.getOrNull(it)?.enabled == true }
        if (enabled.isEmpty()) return
        selected = enabled[Math.floorMod(enabled.indexOf(selected) + direction, enabled.size)]
        rebuildMenu()
        triggerImmediateNarration(true)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (event.isEscape) { onClose(); return true }
        if (parent.handlePaletteShortcut(event)) return true
        if (event.isUp || event.isDown || event.key() == InputConstants.KEY_TAB) {
            moveSelection(if (event.isUp || (event.key() == InputConstants.KEY_TAB && event.hasShiftDown())) -1 else 1)
            return true
        }
        if (event.isConfirmation) { choose(selected); return true }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers())
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, vertical: Double): Boolean {
        if (vertical != 0.0) moveSelection(if (vertical > 0) -1 else 1)
        return true
    }

    override fun renderBackground(graphics: GuiGraphics) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0x99060A12))
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(left, top, left + menuWidth, top + menuHeight, SpotlightTheme.color(0xFA101722))
        graphics.renderOutline(left, top, menuWidth, menuHeight, SpotlightTheme.color(0xFF415877))
        graphics.enableScissor(left + 2, top, left + menuWidth - 2, top + menuHeight)
        graphics.themeText(font, "Actions", left + 5, top + 5, SpotlightTheme.color(0xFFF2F5FC))
        entries.drop(offset).take(count).forEachIndexed { index, row ->
            if (row.actionIndex < 0) graphics.themeText(font, row.heading, left + 10, top + 23 + index * 22,
                SpotlightTheme.color(0xFF9DADC3))
        }
        super.render(graphics, mouseX, mouseY, delta)
        graphics.themeText(font, "↑↓ / Tab Navigate · Enter Run · Esc Back", left + 5, top + menuHeight - 12, SpotlightTheme.color(0xFF9DADC3))
        graphics.disableScissor()
    }
}
