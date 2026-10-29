package dev.spotlight.platform

import kotlin.random.Random


internal object NearestBlockSearchCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        data class Pos(val x: Int, val y: Int, val z: Int)
        val random = Random(813)
        repeat(100) {
            val targets = List(80) { Pos(random.nextInt(-48, 49), random.nextInt(-64, 81), random.nextInt(-48, 49)) }.toSet()
            val sections = targets.map { Pos(it.x shr 4, it.y shr 4, it.z shr 4) }.toSet()
            val x = random.nextDouble(-60.0, 60.0)
            val y = random.nextDouble(-90.0, 100.0)
            val z = random.nextDouble(-60.0, 60.0)
            fun distance(p: Pos): Double = (p.x + 0.5 - x).let { it * it } +
                (p.y + 0.5 - y).let { it * it } + (p.z + 0.5 - z).let { it * it }
            var calls = 0
            val checked = mutableSetOf<Pos>()
            val search = NearestBlockSearch(x, y, z, -48, -64, -48, 48, 80, 48,
                { sx, sy, sz -> calls++; check(checked.add(Pos(sx, sy, sz))); Pos(sx, sy, sz) in sections },
                { bx, by, bz -> calls++; Pos(bx, by, bz) in targets })
            while (!search.done) {
                val before = calls
                search.advance(7, Long.MAX_VALUE)
                check(calls - before <= 7)
            }
            val expected = targets.minOf(::distance)
            check(search.result?.distanceSquared == expected) { "Nearest mismatch: ${search.result}, expected $expected" }
        }
        var probes = 0
        val empty = NearestBlockSearch(0.0, 0.0, 0.0, -512, -64, -512, 527, 319, 527,
            { _, _, _ -> false }, { _, _, _ -> probes++; false })
        empty.advance(deadline = 0)
        check(!empty.done && probes == 0)
        while (!empty.done) empty.advance(4096, Long.MAX_VALUE)
        check(empty.result == null && probes == 0)

        fun single(x: Int, y: Int, z: Int): NearestBlockSearch.Hit {
            val search = NearestBlockSearch(-0.25, 0.0, -0.25, -512, -64, -512, 527, 319, 527,
                { sx, sy, sz -> sx == x shr 4 && sy == y shr 4 && sz == z shr 4 },
                { bx, by, bz -> bx == x && by == y && bz == z })
            while (!search.done) search.advance(13, Long.MAX_VALUE)
            return checkNotNull(search.result)
        }
        check(single(527, 319, 527).y == 319)
        check(single(-512, -64, -512).y == -64)
        var leaves = 0
        fun tie(): NearestBlockSearch.Hit? {
            val search = NearestBlockSearch(0.0, 0.5, 0.5, -16, 0, 0, 15, 15, 15,
                { _, _, _ -> true }, { _, _, _ -> leaves++; true })
            while (!search.done) search.advance(1, Long.MAX_VALUE)
            return search.result
        }
        check(tie() == tie())
        check(leaves == 2) 
        println("NearestBlockSearch checks passed")
    }
}
