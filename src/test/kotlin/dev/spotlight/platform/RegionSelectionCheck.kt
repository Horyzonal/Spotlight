package dev.spotlight.platform

internal object RegionSelectionCheck {
    @JvmStatic fun main(args: Array<String>) {
        fun fails(block: () -> Unit) = check(runCatching(block).isFailure)
        val fill = RegionSelectionState("minecraft:stone", "outline")
        fails { fill.command(-64, 319) }
        fails { fill.select(false, RegionPoint(0, 0, 0)) }
        fill.select(true, RegionPoint(4, 5, 6))
        fill.select(false, RegionPoint(1, 2, 3))
        check(fill.volume() == 64L)
        check(fill.command(-64, 319) == "/fill 1 2 3 4 5 6 minecraft:stone outline")
        fill.select(true, RegionPoint(1, -65, 3))
        fails { fill.command(-64, 319) }
        fails { RegionSelectionState("minecraft:stone\nkill", "replace") }
        fails { RegionSelectionState("minecraft:stone", "replace dirt") }
        fails { RegionSelectionState(null, "normal", "filtered") }
        fails { RegionSelectionState(null, "normal\nkill") }
        for (filter in listOf(null, "minecraft:stone", "minecraft:air")) {
            val replace = RegionSelectionState("minecraft:glass", "replace", replaceBlockId = filter)
            replace.select(true, RegionPoint(4, 5, 6))
            replace.select(false, RegionPoint(1, 2, 3))
            check(replace.command(-64, 319) == "/fill 1 2 3 4 5 6 minecraft:glass replace" +
                (filter?.let { " $it" } ?: ""))
        }
        for (mode in listOf("destroy", "keep", "hollow", "outline")) {
            fails { RegionSelectionState("minecraft:stone", mode, replaceBlockId = "minecraft:air") }
        }
        for (filter in listOf("stone", "minecraft:stone\nkill", "minecraft:stone destroy",
            "#minecraft:logs", "minecraft:stone[axis=y]", "")) {
            fails { RegionSelectionState("minecraft:stone", "replace", replaceBlockId = filter) }
        }
        fails { RegionSelectionState(null, "normal", "replace", "minecraft:stone") }
        val clone = RegionSelectionState(null, "normal", "masked")
        clone.select(true, RegionPoint(4, 5, 6))
        clone.select(false, RegionPoint(1, 2, 3))
        fails { clone.command(-64, 319) }
        clone.select(true, RegionPoint(10, 20, 30))
        check(clone.destinationBounds() == RegionPoint(10, 20, 30) to RegionPoint(13, 23, 33))
        check(clone.command(-64, 319) == "/clone 1 2 3 4 5 6 10 20 30 masked normal")
        clone.select(true, RegionPoint(10, 318, 30))
        fails { clone.command(-64, 319) }
        clone.select(true, RegionPoint(29_999_998, 20, 30))
        fails { clone.command(-64, 319) }
        clone.select(true, RegionPoint(Int.MAX_VALUE, 20, 30))
        fails { clone.command(-64, 319) }
        clone.select(false, RegionPoint(2, 3, 4))
        check(clone.destination == null)
        fails { clone.command(-64, 319) }
        val large = RegionSelectionState("minecraft:air", "replace")
        large.select(true, RegionPoint(-29_999_999, -64, -29_999_999))
        large.select(false, RegionPoint(29_999_999, 319, 29_999_999))
        check(large.volume() == 59_999_999L * 384 * 59_999_999)
        check(large.command(-64, 319).startsWith("/fill "))
        println("RegionSelectionCheck passed")
    }
}
