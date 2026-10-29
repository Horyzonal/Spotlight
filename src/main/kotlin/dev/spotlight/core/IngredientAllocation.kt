package dev.spotlight.core


fun allocateIngredients(candidates: List<Set<Int>>, counts: List<Int>): List<Boolean> {
    require(candidates.size <= 9 && counts.size <= 49) { "Crafting allocation exceeds supported size" }
    require(counts.all { it >= 0 }) { "Inventory counts must be nonnegative" }
    require(candidates.all { choices -> choices.all { it in counts.indices } }) { "Invalid inventory index" }
    
    val units = counts.flatMapIndexed { index, count -> List(minOf(count, candidates.size)) { index } }
    val owner = IntArray(units.size) { -1 }
    fun assign(ingredient: Int, visited: BooleanArray): Boolean {
        for (unit in units.indices) {
            if (visited[unit] || units[unit] !in candidates[ingredient]) continue
            visited[unit] = true
            if (owner[unit] == -1 || assign(owner[unit], visited)) {
                owner[unit] = ingredient
                return true
            }
        }
        return false
    }
    return candidates.indices.map { !assign(it, BooleanArray(units.size)) }
}
