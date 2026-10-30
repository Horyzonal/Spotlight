package dev.spotlight.ui

import net.minecraft.client.gui.GuiGraphics

import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen

import net.minecraft.network.chat.Component

internal class PromptScreen(
    private val parent: Screen,
    title: String,
    private val initial: String = "",
    private val hint: String = "",
    private val submit: (String) -> Unit,
) : Screen(catalogText(title)) {
    private lateinit var input: EditBox
    private var value = initial
    private var error: String? = null

    override fun isPauseScreen() = false

    override fun init() {
        val w = minOf(SpotlightTheme.metric("promptWidth", 440, 160, 4096), (width - 20).coerceAtLeast(1))
        val x = (width - w) / 2
        val y = height / 2
        input = addRenderableWidget(SpotlightEditBox(font, x, y - 10, w, 20, catalogText(title)))
        input.setMaxLength(512)
        input.setHint(catalogText(hint))
        input.value = value
        input.setResponder { value = it }
        addRenderableWidget(catalogButton("Save", x, y + 20, (w - 4) / 2) { save() })
        addRenderableWidget(catalogButton("Cancel", x + (w + 4) / 2, y + 20, (w - 4) / 2) { onClose() })
        setInitialFocus(input)
    }

    private fun save() {
        try {
            submit(input.value)
            if (minecraft!!.screen === this) minecraft!!.setScreen(parent)
        } catch (failure: Exception) {
            error = failure.message ?: failure.javaClass.simpleName
            input.message = catalogText("${title.string}. $error")
            triggerImmediateNarration(true)
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val event = KeyEvent(keyCode, scanCode, modifiers)
        if (event.isConfirmation && (input.isFocused || primaryModifier(event))) { save(); return true }
        if (event.isEscape) { onClose(); return true }
        return super.keyPressed(event.key(), event.scancode(), event.modifiers())
    }

    override fun onClose() { minecraft!!.setScreen(parent) }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xEE101722))
        graphics.themeText(font, catalogText(title), maxOf(10, (width - font.width(catalogText(title))) / 2), height / 2 - 48, SpotlightTheme.color(0xFFF2F5FC))
        graphics.themeText(font, catalogClip(font, error ?: hint, (width - 20).coerceAtLeast(0)), 10, height / 2 + 54, SpotlightTheme.color(0xFFFFB4A9))
        super.render(graphics, mouseX, mouseY, delta)
        catalogHints(graphics, font, width, height, "${primaryModifierLabel}+Enter Save · Tab Next · Esc Back")
    }
}
