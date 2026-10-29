package dev.spotlight.core


object IngredientAllocationCheck {
    @JvmStatic fun main(args: Array<String>) {
        check(allocateIngredients(emptyList(), emptyList()).isEmpty())
        check(allocateIngredients(listOf(emptySet()), emptyList()) == listOf(true))
        check(allocateIngredients(listOf(setOf(0), setOf(0)), listOf(1)) == listOf(false, true))
        check(allocateIngredients(listOf(setOf(0), setOf(0)), listOf(2)) == listOf(false, false))
        
        check(allocateIngredients(listOf(setOf(0, 1), setOf(0)), listOf(1, 1)) == listOf(false, false))
        check(allocateIngredients(listOf(setOf(0, 1), setOf(1, 2), setOf(0)), listOf(1, 1, 1)).none { it })
        check(allocateIngredients(List(9) { setOf(0) }, listOf(Int.MAX_VALUE)).none { it })
        check(allocateIngredients(listOf(setOf(0)), listOf(0)) == listOf(true))
        
        val inventory = MutableList(37) { 0 }.also { it[36] = 1 }
        check(allocateIngredients(listOf(setOf(36), setOf(0)), inventory) == listOf(false, true))
        check(allocateIngredients(listOf(setOf(0)), listOf(0, 4)) == listOf(true))
        fun rejected(block: () -> Unit) = check(runCatching(block).exceptionOrNull() is IllegalArgumentException)
        rejected { allocateIngredients(emptyList(), listOf(-1)) }
        rejected { allocateIngredients(listOf(setOf(-1)), listOf(1)) }
        rejected { allocateIngredients(listOf(setOf(1)), listOf(1)) }
        rejected { allocateIngredients(List(10) { emptySet() }, emptyList()) }
        rejected { allocateIngredients(emptyList(), List(50) { 0 }) }

        
        for (graph in 0 until 512) {
            val candidates = List(3) { slot -> (0..2).filterTo(linkedSetOf()) { graph and (1 shl (slot * 3 + it)) != 0 } }
            for (capacity in 0 until 27) {
                val counts = listOf(capacity % 3, capacity / 3 % 3, capacity / 9)
                val remaining = counts.toMutableList()
                fun maximum(slot: Int): Int {
                    if (slot == candidates.size) return 0
                    var best = maximum(slot + 1)
                    for (stack in candidates[slot]) {
                        if (remaining[stack] == 0) continue
                        remaining[stack]--
                        best = maxOf(best, 1 + maximum(slot + 1))
                        remaining[stack]++
                    }
                    return best
                }
                val missing = allocateIngredients(candidates, counts)
                check(missing.count { !it } == maximum(0)) { "Nonmaximum allocation: $candidates / $counts" }
                
                val fulfilled = candidates.filterIndexed { index, _ -> !missing[index] }
                check(allocateIngredients(fulfilled, counts).none { it })
            }
        }
        println("IngredientAllocationCheck passed")
    }
}
