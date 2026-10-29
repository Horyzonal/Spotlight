package dev.spotlight.ui

internal fun checkMacroFormLayout() {
    check(macroFormScroll(0, 0, 114, 100) == 0)
    check(macroFormScroll(2, 0, 114, 100) == 0)
    check(macroFormScroll(3, 0, 114, 100) == 38)
    check(macroFormScroll(99, 0, 114, 100) == 3686)
    check(macroFormScroll(0, 3686, 114, 100) == 0)
    check(macroFormScroll(1, 0, 200, 2) == 0)
    check(macroFormScroll(99, 3686, 40, 100) == 3760)
    check(macroFormScroll(500, 0, 114, 501) == 18924)
    val drafts = linkedMapOf("player" to "Alex", "item" to "minecraft:stone")
    macroFormSelect(drafts, "item", "minecraft:dirt")
    check(drafts == mapOf("player" to "Alex", "item" to "minecraft:dirt"))
    check(dev.spotlight.core.MacroTemplates.arguments("/say {Argument=x} {Argument=x} {Argument=y}").map { it.name } == listOf("x", "y"))
}
