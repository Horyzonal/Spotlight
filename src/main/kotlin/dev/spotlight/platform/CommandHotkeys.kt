package dev.spotlight.platform

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft

internal object CommandHotkeys {
    private var held = emptySet<String>()
    private val codes = InputConstants::class.java.fields.filter { it.name.startsWith("KEY_") && it.type == Int::class.javaPrimitiveType }
        .associate { it.name.removePrefix("KEY_") to it.getInt(null) }
    private val names = mapOf("ENTER" to "RETURN", "EQUAL" to "EQUALS", "LEFT_BRACKET" to "LBRACKET",
        "RIGHT_BRACKET" to "RBRACKET", "GRAVE_ACCENT" to "GRAVE", "CAPS_LOCK" to "CAPSLOCK",
        "SCROLL_LOCK" to "SCROLLLOCK", "NUM_LOCK" to "NUMLOCK", "PRINT_SCREEN" to "PRINTSCREEN",
        "KP_ENTER" to "NUMPADENTER", "KP_EQUAL" to "NUMPADEQUALS", "KP_ADD" to "ADD", "KP_MULTIPLY" to "MULTIPLY")
    private val specialCodes by lazy { mapOf(
        "KP_DIVIDE" to InputConstants.getKey("key.keyboard.keypad.divide").value,
        "KP_SUBTRACT" to InputConstants.getKey("key.keyboard.keypad.subtract").value,
        "KP_DECIMAL" to InputConstants.getKey("key.keyboard.keypad.period").value,
    ) }
    private val warned = mutableSetOf<String>()
    private val modifiers = mapOf("CTRL" to listOf("LCONTROL", "RCONTROL"), "ALT" to listOf("LALT", "RALT"),
        "SHIFT" to listOf("LSHIFT", "RSHIFT"), "SUPER" to listOf("LGUI", "RGUI"))

    fun tick() {
        val client = Minecraft.getInstance()
        fun down(name: String) = codes[name]?.let(InputConstants::isKeyDown) == true
        val activeModifiers = modifiers.filterValues { keys -> keys.any(::down) }.keys
        val pressed = Spotlight.settings().hotkeys.filterValues { chord ->
            val parts = chord.split('+')
            val key = names[parts.last()] ?: parts.last().replace(Regex("^KP_([0-9])$"), "NUMPAD$1")
            val code = codes[key] ?: specialCodes[parts.last()]
            if (code == null && warned.add(chord)) notifySpotlight("Unsupported platform hotkey: $chord")
            parts.dropLast(1).toSet() == activeModifiers && code?.let(InputConstants::isKeyDown) == true
        }.keys
        val newlyPressed = pressed - held
        held = pressed
        if (!client.isWindowActive || client.gui.screen() != null || client.gui.overlay() != null || client.player == null) return
        for (id in newlyPressed) {
            try {
                val command = Spotlight.allCommands().firstOrNull { it.id == id } ?: continue
                Spotlight.dispatch(command)
            } catch (failure: Exception) { notifySpotlight(failure.message ?: "Command failed") }
            if (client.gui.screen() != null) break
        }
    }
}
