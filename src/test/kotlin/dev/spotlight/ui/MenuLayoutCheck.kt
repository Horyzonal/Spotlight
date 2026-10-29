package dev.spotlight.ui

object MenuLayoutCheck {
    @JvmStatic fun main(args: Array<String>) {
        check(menuRows(emptyList()).isEmpty())
        check(menuOffset(emptyList(), 0, 0, 1) == 0)
        check(menuRows(listOf("", "")).map { it.actionIndex } == listOf(0, 1))
        val rows = menuRows(listOf("Navigation", "Command", "Locations", "Tools", "Navigation"))
        check(rows.filter { it.actionIndex < 0 }.map { it.heading } == listOf("Navigation", "Command", "Locations", "Tools"))
        check(rows.filter { it.actionIndex >= 0 }.map { it.actionIndex } == listOf(0, 4, 1, 2, 3))
        for (count in 1..rows.size) {
            for (selected in 0..4) {
                for (offset in 0..rows.size) {
                    val start = menuOffset(rows, selected, offset, count)
                    val row = rows.indexOfFirst { it.actionIndex == selected }
                    check(row in start until start + count)
                    check(start in 0..rows.size - count)
                    if (count > 1 && rows[row - 1].actionIndex < 0) check(start < row)
                }
            }
        }
        println("Menu layout checks passed")
    }
}
