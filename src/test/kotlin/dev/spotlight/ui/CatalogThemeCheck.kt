package dev.spotlight.ui

internal object CatalogThemeCheck {
    @JvmStatic fun main(args: Array<String>) {
        checkCatalogTheme()
        println("Catalog theme default checks passed")
    }
}

internal fun checkCatalogTheme() {
    val defaults = dev.spotlight.core.Theme.defaults
    listOf("selectorWidth" to 520, "locationWidth" to 360, "promptWidth" to 440,
        "editorWidth" to 620, "paletteWidth" to 560, "compactRowHeight" to 24,
        "widgetHeight" to 20, "borderThickness" to 1).forEach { (key, original) ->
        check(defaults.metric(key, original) == original) { "$key changes original $original" }
    }
    check(defaults.metric("editorWidth", 520) == 620) 
    check(defaults.metric("compactRowHeight", 24) + 2 == 26) 
    check(catalogNativeColor(-1, 0xFFFFB4A9) == -1) 
    check(catalogNativeColor(-2039584, 0xFFF2F5FC) == -2039584)
    check(catalogNativeColor(-3092272, 0xFFF2F5FC) == -3092272)
    check(catalogNativeColor(-1, 0xFFF2F5FC) == -1)
    check(!catalogCustomFrame())
    check(catalogText("preview").string == "preview")
}
