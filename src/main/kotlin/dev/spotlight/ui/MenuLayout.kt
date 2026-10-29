package dev.spotlight.ui

internal data class MenuRow(val heading: String = "", val actionIndex: Int = -1)

internal fun menuRows(groups: List<String>): List<MenuRow> = buildList {
    groups.indices.groupBy { groups[it] }.forEach { (group, indices) ->
        if (group.isNotEmpty()) add(MenuRow(heading = group))
        indices.forEach { add(MenuRow(actionIndex = it)) }
    }
}

internal fun menuOffset(rows: List<MenuRow>, selected: Int, offset: Int, count: Int): Int {
    val row = rows.indexOfFirst { it.actionIndex == selected }
    if (row < 0) return 0
    var start = offset.coerceIn((row - count + 1).coerceAtLeast(0), row)
    if (count > 1 && row > 0 && rows[row - 1].actionIndex < 0 && start == row) start--
    return start.coerceIn(0, (rows.size - count).coerceAtLeast(0))
}
