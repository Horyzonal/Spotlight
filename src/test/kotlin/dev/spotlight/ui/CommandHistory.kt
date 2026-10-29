package dev.spotlight.ui

internal object CommandHistoryCheck {
    fun run() {
        val navigation = HistoryNavigation()
        val values = listOf("/time set day ", "give stone")
        check(navigation.up("draft") { values } == values[0])
        check(navigation.up(values[0]) { error("Snapshot must stay stable") } == values[1])
        check(navigation.up(values[1]) { values } == values[1])
        check(navigation.down() == "draft")
        navigation.edited()
        check(navigation.up("changed") { values } == null)
        check(HistoryNavigation().up("draft") { emptyList() } == "draft")
        val edited = HistoryNavigation()
        edited.up("old") { values }
        edited.edited()
        check(edited.up("new") { values } == values[0])
        check(edited.down() == "new")
        repeat(105) { CommandHistory.record("/say $it") }
        CommandHistory.record(" ")
        check(CommandHistory.snapshot().size == 100)
        check(CommandHistory.snapshot().first() == "/say 104")
        check(CommandHistory.snapshot().last() == "/say 5")
    }
}
