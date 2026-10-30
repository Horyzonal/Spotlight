package dev.spotlight.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW

// ponytail: 1.20.1 callbacks use primitives; remove these value helpers when native events return.
internal data class KeyEvent(private val key: Int, private val scanCode: Int, private val modifiers: Int) {
    fun key() = key
    fun input() = key
    fun scancode() = scanCode
    fun modifiers() = modifiers
    fun hasShiftDown() = modifiers and GLFW.GLFW_MOD_SHIFT != 0
    fun hasAltDown() = modifiers and GLFW.GLFW_MOD_ALT != 0
    fun hasControlDown() = modifiers and GLFW.GLFW_MOD_CONTROL != 0
    fun hasControlDownWithQuirk() = modifiers and (if (Minecraft.ON_OSX) GLFW.GLFW_MOD_SUPER else GLFW.GLFW_MOD_CONTROL) != 0
    val isEscape get() = key == GLFW.GLFW_KEY_ESCAPE
    val isConfirmation get() = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
    val isUp get() = key == GLFW.GLFW_KEY_UP
    val isDown get() = key == GLFW.GLFW_KEY_DOWN
    val isCopy get() = hasControlDownWithQuirk() && !hasShiftDown() && !hasAltDown() && key == GLFW.GLFW_KEY_C
    val isCut get() = hasControlDownWithQuirk() && !hasShiftDown() && !hasAltDown() && key == GLFW.GLFW_KEY_X
    val isSelectAll get() = hasControlDownWithQuirk() && !hasShiftDown() && !hasAltDown() && key == GLFW.GLFW_KEY_A
}

internal data class MouseButtonEvent(private val x: Double, private val y: Double, private val button: Int) {
    fun x() = x
    fun y() = y
    fun button() = button
    fun hasShiftDown() = Screen.hasShiftDown()
}

internal data class CharacterEvent(private val codepoint: Int, private val modifiers: Int) {
    fun codepoint() = codepoint
    fun modifiers() = modifiers
    fun codepointAsString() = Character.toString(codepoint)
    val isAllowedChatCharacter get() = net.minecraft.SharedConstants.isAllowedChatCharacter(codepoint.toChar())
}
