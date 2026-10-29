package dev.spotlight.ui

import dev.spotlight.core.Entry
import dev.spotlight.core.MacroTemplates

internal fun checkMacroLocationSelection() {
    checkMacroFormLayout()
    check(macroSubmissionAction(0, 0) == "Run")
    check(macroSubmissionAction(0, 1) == "Run")
    check(macroSubmissionAction(0, 2) == "Next")
    check(macroSubmissionAction(1, 2) == "Run")
    val execution = MacroExecutionState()
    check(execution.begin(automatic = true))
    check(!execution.begin(automatic = true))
    check(!execution.begin(automatic = false))
    execution.finish(sent = false)
    repeat(3) { check(!execution.begin(automatic = true)) }
    check(execution.begin(automatic = false))
    execution.finish(sent = true)
    check(!execution.begin(automatic = true))
    check(!execution.begin(automatic = false))
    val home = Entry("location:home", "Home", "Overworld")
    val other = Entry("location:other", "Other", "Nether")
    val results = listOf(home, other)
    check(macroLocationSelection("Home", results, 1) == home)
    check(macroLocationSelection("hom", results, 0) == home)
    check(macroLocationSelection("", results, 1) == other)
    check(macroLocationSelection("12 64 -5", results, 0) == null)
    check(macroLocationSelection("~ ~1 ~", results, 0) == null)
    check(macroLocationSelection("^ ^ ^1", results, 0) == null)
    check(macroLocationSelection("unknown", emptyList(), 0) == null)
    check(macroLocationSelection("12 64", results, 0) == null)
    check(macroLocationSelection("-", results, 0) == null)
    check(macroLocationSelection("~", results, 0) == null)
    check(macroLocationSelection("1e", results, 0) == null)
    check(macroLocationSelection("Home\n/say injected", emptyList(), 0) == null)
    val template = "/say {Argument=target} {Argument=target} {Argument=n:number} {Clipboard}"
    check(MacroTemplates.preview(template, mapOf("target" to "Alex", "n" to "-"), mapOf("Clipboard" to "frozen")) ==
        "/say Alex Alex - frozen")
    check(MacroTemplates.preview("/say {Argument=target}", mapOf("target" to "")) == "/say {Argument=target}")
    check(MacroTemplates.preview("/say {Argument=target}", mapOf("target" to "{Clipboard}"), mapOf("Clipboard" to "frozen")) ==
        "/say {Clipboard}")
}
