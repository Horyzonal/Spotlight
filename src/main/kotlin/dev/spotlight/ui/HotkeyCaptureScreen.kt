package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent

internal class HotkeyCaptureScreen(
    private val parent: Screen,
    title: String,
    private val current: String,
    private val submit: (String) -> Unit,
) : Screen(catalogText(title)) {
    private var preview = ""
    private var error: String? = null

    override fun isPauseScreen() = false

    override fun init() {
        val w = minOf(SpotlightTheme.metric("promptWidth", 440, 160, 4096), (width - 20).coerceAtLeast(1))
        val x = (width - w) / 2
        val y = height / 2
        addRenderableWidget(catalogButton("Clear binding", x, y + 20, (w - 4) / 2) { save("") })
        addRenderableWidget(catalogButton("Cancel", x + (w + 4) / 2, y + 20, (w - 4) / 2) { onClose() })
    }

    private fun save(chord: String) {
        try {
            submit(chord)
            if (minecraft.screen === this) minecraft.setScreen(parent)
        } catch (failure: Exception) {
            error = failure.message ?: failure.javaClass.simpleName
            triggerImmediateNarration(true)
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape && event.modifiers() == 0) { onClose(); return true }
        if (event.key() in modifierCodes) {
            preview = modifierNames(event.modifiers()).joinToString("+")
            return true
        }
        val name = keyName(event.key()) ?: return true
        val chord = (modifierNames(event.modifiers()) + name).joinToString("+")
        save(chord)
        return true
    }

    override fun onClose() { minecraft.setScreen(parent) }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xEE101722))
        graphics.themeText(font, catalogText(title), maxOf(10, (width - font.width(catalogText(title))) / 2), height / 2 - 48, SpotlightTheme.color(0xFFF2F5FC))
        val prompt = catalogText(if (preview.isEmpty()) "Press a key combination…" else displayChord(preview))
        graphics.themeText(font, prompt, maxOf(10, (width - font.width(prompt)) / 2), height / 2 - 10, SpotlightTheme.color(0xFF81B8FF))
        graphics.themeText(font, catalogClip(font, error ?: "Current: ${if (current.isEmpty()) "none" else displayChord(current)}. ESCAPE cancels capture.", (width - 20).coerceAtLeast(0)),
            10, height / 2 + 54, SpotlightTheme.color(if (error == null) 0xFF91A0B5 else 0xFFFFB4A9))
        super.render(graphics, mouseX, mouseY, delta)
        catalogHints(graphics, font, width, height, "Press keys to bind · Esc Cancel")
    }

    private companion object {
        val codes: Map<String, Int> = InputConstants::class.java.fields
            .filter { it.name.startsWith("KEY_") && it.type == Int::class.javaPrimitiveType }
            .associate { it.name.removePrefix("KEY_") to it.getInt(null) }
        val modifierCodes = listOf("LCONTROL", "RCONTROL", "LSHIFT", "RSHIFT", "LALT", "RALT", "LGUI", "RGUI")
            .mapNotNull { codes[it] }.toSet()
        val canonical = mapOf("RETURN" to "ENTER", "EQUALS" to "EQUAL", "LBRACKET" to "LEFT_BRACKET",
            "RBRACKET" to "RIGHT_BRACKET", "GRAVE" to "GRAVE_ACCENT", "CAPSLOCK" to "CAPS_LOCK",
            "SCROLLLOCK" to "SCROLL_LOCK", "NUMLOCK" to "NUM_LOCK", "PRINTSCREEN" to "PRINT_SCREEN",
            "NUMPADENTER" to "KP_ENTER", "NUMPADEQUALS" to "KP_EQUAL", "ADD" to "KP_ADD", "MULTIPLY" to "KP_MULTIPLY")
        val special by lazy { mapOf(
            InputConstants.getKey("key.keyboard.keypad.divide").value to "KP_DIVIDE",
            InputConstants.getKey("key.keyboard.keypad.subtract").value to "KP_SUBTRACT",
            InputConstants.getKey("key.keyboard.keypad.period").value to "KP_DECIMAL",
        ) }

        fun keyName(code: Int): String? {
            special[code]?.let { return it }
            val raw = codes.entries.firstOrNull { it.value == code }?.key ?: return null
            return canonical[raw] ?: raw.replace(Regex("^NUMPAD([0-9])$"), "KP_$1")
        }

        fun modifierNames(bits: Int): List<String> = listOfNotNull(
            if (bits and 2 != 0 || (macShortcuts && bits and 8 != 0)) "CTRL" else null,
            if (bits and 4 != 0) "ALT" else null,
            if (bits and 1 != 0) "SHIFT" else null,
            if (!macShortcuts && bits and 8 != 0) "SUPER" else null,
        )
    }
}
