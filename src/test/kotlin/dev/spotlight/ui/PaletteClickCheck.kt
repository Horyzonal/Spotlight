package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants

object PaletteClickCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        check(opensPaletteMenu(InputConstants.MOUSE_BUTTON_RIGHT, insidePalette = true, insideInput = false))
        check(!opensPaletteMenu(InputConstants.MOUSE_BUTTON_LEFT, insidePalette = true, insideInput = false))
        check(!opensPaletteMenu(InputConstants.MOUSE_BUTTON_MIDDLE, insidePalette = true, insideInput = false))
        check(!opensPaletteMenu(InputConstants.MOUSE_BUTTON_RIGHT, insidePalette = false, insideInput = false))
        check(!opensPaletteMenu(InputConstants.MOUSE_BUTTON_RIGHT, insidePalette = true, insideInput = true))
        var selectedId = "prior-command"
        val completed = mutableListOf<String>()
        val executed = mutableListOf<String>()
        val complete: (String) -> Unit = { completed += it }
        val activate: (String) -> Unit = { executed += it }
        val clickedId = "server:seed"
        val click = { routePaletteRowClick(clickedId, false, complete, activate) }
        selectedId = "another-command"
        click()
        check(executed == listOf(clickedId))
        check(selectedId !in executed)
        routePaletteRowClick("macro:build", false, complete, activate)
        routePaletteRowClick("slash:1:3:give", true, complete, activate)
        routePaletteRowClick("slash:6:8:@p", true, complete, activate)
        check(executed == listOf(clickedId, "macro:build"))
        check(completed == listOf("slash:1:3:give", "slash:6:8:@p"))
        println("Palette click checks passed")
    }
}
