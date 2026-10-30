package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.glfw.GLFW

object PaletteShortcutsCheck {
    @JvmStatic fun main(args: Array<String>) {
        check(paletteShortcut(InputConstants.KEY_A, true, false, false, false) == null)
        check(paletteShortcut(InputConstants.KEY_A, true, true, false, false) == PaletteShortcut.ALIAS)
        check(paletteShortcut(InputConstants.KEY_H, true, true, false, false) == PaletteShortcut.HOTKEY)
        check(paletteShortcut(InputConstants.KEY_A, false, true, false, false) == null)
        check(paletteShortcut(InputConstants.KEY_E, true, false, false, false) == PaletteShortcut.SELECTOR)
        check(paletteShortcut(InputConstants.KEY_E, true, false, false, true) == PaletteShortcut.EDIT)
        check(paletteShortcut(InputConstants.KEY_DELETE, false, false, false, false) == null)
        check(paletteShortcut(InputConstants.KEY_DELETE, false, false, false, true) == null)
        check(paletteShortcut(InputConstants.KEY_BACKSPACE, true, false, false, true) == null)
        check(paletteShortcut(InputConstants.KEY_D, true, true, false, true) == PaletteShortcut.DELETE)
        check(paletteShortcut(InputConstants.KEY_D, true, true, false, false) == null)
        check(paletteShortcut(InputConstants.KEY_D, true, false, false, true) == null)
        check(paletteShortcut(InputConstants.KEY_D, false, true, false, true) == null)
        check(paletteShortcut(InputConstants.KEY_D, true, true, true, true) == null)
        check(paletteShortcut(InputConstants.KEY_TAB, false, false, false, true) == null)
        for (reverse in listOf(false, true)) check(paletteShortcut(InputConstants.KEY_TAB, true, reverse, false, false) == PaletteShortcut.CYCLE_TABS)
        check(paletteShortcut(InputConstants.KEY_K, true, false, true, false) == null)
        check(paletteShortcut(InputConstants.KEY_F1, false, false, false, false) == PaletteShortcut.MENU)
        check(paletteShortcut(InputConstants.KEY_3, true, false, false, false) == PaletteShortcut.MACROS)
                for (tab in 0..2) {
                    check(cyclePaletteTab(tab, false) == (tab + 1) % 3)
                    check(cyclePaletteTab(cyclePaletteTab(tab, false), true) == tab)
                }
                check(cyclePaletteTab(2, false) == 0)
                check(cyclePaletteTab(0, true) == 2)
                check(slashCommandName("/give @s stone") == "give")
        check(slashCommandName("/minecraft:give\t@s") == "minecraft:give")
        check(slashCommandName("/") == null)
        check(slashCommandName("give") == null)
        
        check(primaryModifier(KeyEvent(InputConstants.KEY_A, 0, GLFW.GLFW_MOD_SUPER)) == macShortcuts)
        check(primaryModifier(KeyEvent(InputConstants.KEY_A, 0, GLFW.GLFW_MOD_CONTROL)) != macShortcuts)
        for (modifier in listOf(0, GLFW.GLFW_MOD_SHIFT, GLFW.GLFW_MOD_ALT)) {
            check(!primaryModifier(KeyEvent(InputConstants.KEY_A, 0, modifier)))
        }
        val primary = if (macShortcuts) GLFW.GLFW_MOD_SUPER else GLFW.GLFW_MOD_CONTROL
        val selectAll = KeyEvent(InputConstants.KEY_A, 0, primary)
        check(selectAll.key() == InputConstants.KEY_A && selectAll.input() == selectAll.key())
        check(selectAll.isSelectAll)
        check(!KeyEvent(InputConstants.KEY_A, 0, primary or GLFW.GLFW_MOD_SHIFT).isSelectAll)
        check(!KeyEvent(InputConstants.KEY_A, 0, primary or GLFW.GLFW_MOD_ALT).isSelectAll)
        MultilineInputCheck.main(emptyArray())
        checkMacroLocationSelection()
        CommandHistoryCheck.run()
        println("Palette shortcut and history checks passed")
    }
}
