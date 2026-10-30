package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

internal class ConfirmSpotlightScreen(
    private val parent: Screen,
    question: String,
    private val explanation: String,
    private val confirm: () -> Unit,
) : Screen(Component.literal(question)) {
    private var left = 0
    private var top = 0
    private var panelWidth = 1
    private var panelHeight = 1
    private var completed = false
    private var lines = emptyList<net.minecraft.util.FormattedCharSequence>()
    private var hints = emptyList<net.minecraft.util.FormattedCharSequence>()

    override fun isPauseScreen() = false

    override fun init() {
        panelWidth = minOf(SpotlightTheme.metric("layout.confirmWidth", 420, 160), (width - 16).coerceAtLeast(1))
        val textWidth = (panelWidth - 20).coerceAtLeast(1)
        lines = font.split(Component.literal("${title.string}\n\n$explanation"), textWidth)
        hints = font.split(Component.literal("Tab Focus · Enter Press focused button · ${primaryModifierLabel}+Enter Delete · Esc Cancel"), textWidth)
        panelHeight = minOf(height, 60 + (lines.size + hints.size) * font.lineHeight)
        left = (width - panelWidth) / 2
        top = (height - panelHeight) / 2
        val buttonWidth = ((panelWidth - 24) / 2).coerceAtLeast(1)
        val buttonY = top + panelHeight - 30 - hints.size * font.lineHeight
        val cancel = addRenderableWidget(spotlightButton("Cancel", left + 10, buttonY, buttonWidth) { onClose() })
        addRenderableWidget(spotlightButton("Delete", left + 14 + buttonWidth, buttonY, buttonWidth) { finish(true) })
        setInitialFocus(cancel)
    }

    private fun finish(confirmed: Boolean) {
        if (completed) return
        completed = true
        minecraft!!.setScreen(parent)
        if (confirmed) confirm()
    }

    override fun onClose() { finish(false) }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (event.isEscape) { onClose(); return true }
        if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) &&
            primaryModifier(event) && !event.hasAltDown() && !event.hasShiftDown()) {
            finish(true)
            return true
        }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers())
    }

    override fun getNarrationMessage(): Component = Component.literal("${title.string}. $explanation. Cancel selected by default. ${primaryModifierLabel}+Enter confirms deletion. Escape cancels.")

    override fun renderBackground(graphics: GuiGraphics) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0x99060A12))
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(left, top, left + panelWidth, top + panelHeight, SpotlightTheme.color(0xFA101722))
        graphics.renderOutline(left, top, panelWidth, panelHeight, SpotlightTheme.color(0xFF415877))
        val textBottom = top + panelHeight - 36 - hints.size * font.lineHeight
        graphics.enableScissor(left + 4, top + 4, left + panelWidth - 4, textBottom.coerceAtLeast(top + 4))
        lines.forEachIndexed { index, line ->
            graphics.themeText(font, line, left + 10, top + 10 + index * font.lineHeight, SpotlightTheme.color(0xFFF2F5FC))
        }
        graphics.disableScissor()
        super.render(graphics, mouseX, mouseY, delta)
        hints.forEachIndexed { index, line ->
            graphics.themeText(font, line, left + 10, top + panelHeight - 5 - (hints.size - index) * font.lineHeight, SpotlightTheme.color(0xFF9DADC3))
        }
    }
}
