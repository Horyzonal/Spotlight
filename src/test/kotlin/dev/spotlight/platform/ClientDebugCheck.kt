package dev.spotlight.platform

import dev.spotlight.commands.debugAngle
import dev.spotlight.commands.debugEffectDuration
import dev.spotlight.commands.debugMemory
import dev.spotlight.commands.debugNumber
import java.util.Locale

internal object ClientDebugCheck {
    @JvmStatic
    fun main(args: Array<String>) = run()

    fun run() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            check(debugNumber(-12.125) == "-12.125")
            check(debugNumber(0.0) == "0.000")
            check(debugMemory(0) == "0.000 MiB")
            check(debugMemory(1_572_864) == "1.500 MiB")
            check(debugMemory(8_589_934_592L) == "8192.000 MiB")
            check(debugAngle(360f) == "0.000")
            check(debugAngle(270f) == "-90.000")
            check(debugAngle(-450f) == "-90.000")
            check(debugAngle(180f) == "-180.000")
            check(debugEffectDuration(21, false) == "21 ticks (1.050 s at 20 ticks/s)")
            check(debugEffectDuration(0, false) == "0 ticks (0.000 s at 20 ticks/s)")
            check(debugEffectDuration(-1, true) == "infinite")
        } finally {
            Locale.setDefault(previous)
        }
    }
}
