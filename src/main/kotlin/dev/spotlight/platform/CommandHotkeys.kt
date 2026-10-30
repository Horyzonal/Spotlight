package dev.spotlight.platform

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft

internal fun hotkeyCode(name: String): Int? {
    val key = when (name) {
        "PAGEUP" -> "page.up"
        "PAGEDOWN" -> "page.down"
        else -> name.lowercase().replace("kp_", "keypad.").replace('_', '.')
    }
    return try {
        InputConstants.getKey("key.keyboard.$key").value.takeUnless { it == InputConstants.UNKNOWN.value }
    } catch (_: IllegalArgumentException) { null }
}

internal object CommandHotkeys {
    private var held = emptySet<String>()

    private val warned = mutableSetOf<String>()
    private val modifiers = mapOf(
        "CTRL" to listOf(InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL),
        "ALT" to listOf(InputConstants.KEY_LALT, InputConstants.KEY_RALT),
        "SHIFT" to listOf(InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT),
        "SUPER" to listOf(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SUPER, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SUPER),
    )

    fun tick() {
        val client = Minecraft.getInstance()
        fun down(code: Int) = InputConstants.isKeyDown(client.window.window, code)
        val activeModifiers = modifiers.filterValues { keys -> keys.any(::down) }.keys
        val pressed = Spotlight.settings().hotkeys.filterValues { chord ->
            val parts = chord.split('+')
            val code = hotkeyCode(parts.last())
            if (code == null && warned.add(chord)) notifySpotlight("Unsupported platform hotkey: $chord")
            parts.dropLast(1).toSet() == activeModifiers && code?.let(::down) == true
        }.keys
        val newlyPressed = pressed - held
        held = pressed
        if (!client.isWindowActive || client.screen != null || client.overlay != null || client.player == null) return
        for (id in newlyPressed) {
            try {
                val command = Spotlight.allCommands().firstOrNull { it.id == id } ?: continue
                Spotlight.dispatch(command)
            } catch (failure: Exception) { notifySpotlight(failure.message ?: "Command failed") }
            if (client.screen != null) break
        }
    }
}
