package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants

internal fun opensPaletteMenu(button: Int, insidePalette: Boolean, insideInput: Boolean): Boolean =
    button == InputConstants.MOUSE_BUTTON_RIGHT && insidePalette && !insideInput

internal fun routePaletteRowClick(id: String, suggestion: Boolean, complete: (String) -> Unit, activate: (String) -> Unit) {
    if (suggestion) complete(id) else activate(id)
}
