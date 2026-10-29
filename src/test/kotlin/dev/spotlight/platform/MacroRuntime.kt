package dev.spotlight.platform

import dev.spotlight.core.Macro

object MacroRuntimeCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        MacroTargetingCheck.run()
        check(MacroRuntime.variables("/teleport @e[type=dog] {Argument=location}").isEmpty())
        check(MacroRuntime.variables("/tellraw @s {\"text\":\"literal\"}").isEmpty())
        check(MacroRuntime.variables("/say \"\\{Clipboard}\"").isEmpty())
        val macro = Macro("狗", "Teleport dogs", "/teleport @e[type=dog] {Argument=location}")
        val command = MacroRuntime.command(macro)
        check(command.title == "Teleport dogs [Macro]")
        check(dev.spotlight.core.Fuzzy.search("teleport dogs", listOf(dev.spotlight.core.Entry(command.id, command.title))).single().id == command.id)
        check(command.id.matches(Regex("macro:[0-9a-f]{64}")))
        check(command.id == MacroRuntime.command(macro.copy(name = "Renamed")).id)
        check(command.id != MacroRuntime.command(macro.copy(id = "dog")).id)
        check(command.input == CommandInput.NONE && command.description == macro.template)
    }
}
