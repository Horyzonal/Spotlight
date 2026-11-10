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
        persistentSearch()
        println("NearestBlockSearch checks passed")
    }

    private fun persistentSearch() {
        data class Pass(val center: Int, var work: Int = 0)
        var starts = 0
        var advances = 0
        var available = false
        fun job() = PersistentTargetSearch<Int, Pass>(
            create = { starts++; Pass(it) },
            advance = { advances++; ++it.work == 2 },
            found = { available },
        )
        val search = job()
        check(search.tick(0) == null)
        check(search.tick(0) == null)
        repeat(20) { check(search.tick(0) == null) }
        check(starts == 1 && advances == 2)
        available = true
        check(search.tick(0) == null)
        check(search.tick(0)?.center == 0)
        check(starts == 2 && advances == 4)
        repeat(30) { check(search.tick(0) == null) }
        check(starts == 2 && advances == 4)

        val canceled = job()
        canceled.tick(0)
        canceled.cancel()
        val before = advances
        repeat(30) { check(canceled.tick(it) == null) }
        check(advances == before)

        val moving = job()
        check(moving.tick(0) == null)
        check(moving.tick(1) == null)
        check(moving.tick(1)?.center == 1)

        available = false
        val refreshing = job()
        refreshing.tick(0)
        refreshing.tick(0)
        val beforeRefresh = starts
        refreshing.tick(1)
        check(starts == beforeRefresh + 1)

        available = true
        val first = job()
        val second = job()
        first.tick(0)
        second.tick(1)
        first.cancel()
        check(second.tick(1)?.center == 1)
        check(first.tick(0) == null)

        val candidates = NearestTargetCandidates<Int>(64)
        for (distance in 1000 downTo 1) candidates.add(distance, distance.toDouble())
        check(candidates.values() == (1..64).toList())

        val center = net.minecraft.core.BlockPos(-1, 100, -1)
        val area = FindTargets.SearchArea(center, 4)
        check(area.contains(net.minecraft.core.BlockPos(63, -64, 63)))
        check(area.contains(net.minecraft.core.BlockPos(-80, 319, -80)))
        check(!area.contains(net.minecraft.core.BlockPos(64, 100, 0)))
        check(!area.contains(net.minecraft.core.BlockPos(-81, 100, 0)))
        val wider = FindTargets.SearchArea(center, 5)
        check(wider != area && wider.contains(net.minecraft.core.BlockPos(64, 100, 0)))
    }
}
