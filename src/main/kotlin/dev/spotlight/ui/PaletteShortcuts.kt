package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants

internal val macShortcuts = net.minecraft.client.Minecraft.ON_OSX
internal val primaryModifierLabel = if (macShortcuts) "Cmd" else "Ctrl"
internal fun primaryModifier(event: KeyEvent): Boolean = event.hasControlDownWithQuirk()

internal enum class PaletteShortcut {
    ALL, LOCATIONS, MACROS, CYCLE_TABS, MENU, ALIAS, HOTKEY, FAVORITE, CATALOGS, SELECTOR, ADD, EDIT, DELETE, SYNC, CLOSE,
}


internal fun paletteShortcut(key: Int, control: Boolean, shift: Boolean, alt: Boolean, locations: Boolean): PaletteShortcut? {
    if (alt) return null
    if (!control) return when {
        key == InputConstants.KEY_F1 -> PaletteShortcut.MENU

        else -> null
    }
    if (key == InputConstants.KEY_TAB) return PaletteShortcut.CYCLE_TABS
    if (shift) return when (key) {
        InputConstants.KEY_A -> PaletteShortcut.ALIAS
        InputConstants.KEY_H -> PaletteShortcut.HOTKEY
        InputConstants.KEY_D -> if (locations) PaletteShortcut.DELETE else null
        else -> null
    }
    return when (key) {
        InputConstants.KEY_1 -> PaletteShortcut.ALL
        InputConstants.KEY_2 -> PaletteShortcut.LOCATIONS
                InputConstants.KEY_3 -> PaletteShortcut.MACROS
        InputConstants.KEY_K -> PaletteShortcut.MENU
        InputConstants.KEY_F -> PaletteShortcut.FAVORITE
        InputConstants.KEY_L -> PaletteShortcut.CATALOGS
        InputConstants.KEY_E -> if (locations) PaletteShortcut.EDIT else PaletteShortcut.SELECTOR
        InputConstants.KEY_N -> if (locations) PaletteShortcut.ADD else null
        InputConstants.KEY_S -> if (locations) PaletteShortcut.SYNC else null
        InputConstants.KEY_W -> PaletteShortcut.CLOSE
        else -> null
    }
}

internal fun cyclePaletteTab(current: Int, reverse: Boolean): Int = Math.floorMod(current + if (reverse) -1 else 1, 3)

internal fun slashCommandName(text: String): String? =
    text.takeIf { it.startsWith('/') }?.drop(1)?.takeWhile { !it.isWhitespace() }?.takeIf { it.isNotEmpty() }
