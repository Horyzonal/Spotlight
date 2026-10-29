package dev.spotlight.ui

internal object AddLocationCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        check(parseLocationCoordinates(" 1, -2.5, +.3\n") == listOf(1.0, -2.5, 0.3))
        check(parseLocationCoordinates("1\n2 3") == listOf(1.0, 2.0, 3.0))
        check(parseLocationCoordinates("1 2, 3") == listOf(1.0, 2.0, 3.0))
        listOf("", "1 2", "1 2 3 4", "NaN 2 3", "Infinity 2 3", "1e3 2 3", "1,,2,3", "1 2 3,", "1\t2 3", "1\u00002 3", "/tp 1 2 3", "1 2 3\n/say hi", "9".repeat(400) + " 2 3").forEach {
            check(runCatching { parseLocationCoordinates(it) }.isFailure) { "Accepted invalid clipboard: $it" }
        }
        println("Add location coordinate checks passed")
    }
}
