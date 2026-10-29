package dev.spotlight.platform

import java.util.PriorityQueue


internal class NearestBlockSearch(
    private val startX: Double, private val startY: Double, private val startZ: Double,
    minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int,
    private val sectionMayContain: (Int, Int, Int) -> Boolean,
    private val matches: (Int, Int, Int) -> Boolean,
) {
    data class Hit(val x: Int, val y: Int, val z: Int, val distanceSquared: Double)
    private data class Cell(
        val x: Int, val y: Int, val z: Int, val maxX: Int, val maxY: Int, val maxZ: Int,
        val checked: Boolean, val distance: Double,
    )
    private val pending = PriorityQueue<Cell>(compareBy<Cell> { it.distance }
        .thenBy { it.x }.thenBy { it.y }.thenBy { it.z }
        .thenBy { it.maxX }.thenBy { it.maxY }.thenBy { it.maxZ })
    var done = false
        private set
    var result: Hit? = null
        private set

    init {
        require(minX <= maxX && minY <= maxY && minZ <= maxZ)
        add(minX, minY, minZ, maxX, maxY, maxZ, false)
    }

    private fun add(x: Int, y: Int, z: Int, maxX: Int, maxY: Int, maxZ: Int, checked: Boolean) {
        val dx = startX - startX.coerceIn(x + 0.5, maxX + 0.5)
        val dy = startY - startY.coerceIn(y + 0.5, maxY + 0.5)
        val dz = startZ - startZ.coerceIn(z + 0.5, maxZ + 0.5)
        pending.add(Cell(x, y, z, maxX, maxY, maxZ, checked, dx * dx + dy * dy + dz * dz))
    }

    fun advance(maxWork: Int = 4096, deadline: Long = System.nanoTime() + 2_000_000L) {
        var work = 0
        while (!done && work < maxWork && System.nanoTime() < deadline) {
            val cell = pending.poll()
            if (cell == null) { done = true; break }
            work++
            val sx = (cell.maxX shr 4) - (cell.x shr 4)
            val sy = (cell.maxY shr 4) - (cell.y shr 4)
            val sz = (cell.maxZ shr 4) - (cell.z shr 4)
            val section = sx == 0 && sy == 0 && sz == 0
            if (section && !cell.checked && !sectionMayContain(cell.x shr 4, cell.y shr 4, cell.z shr 4)) continue
            if (cell.x == cell.maxX && cell.y == cell.maxY && cell.z == cell.maxZ) {
                if (matches(cell.x, cell.y, cell.z)) {
                    result = Hit(cell.x, cell.y, cell.z, cell.distance)
                    done = true
                    pending.clear()
                }
                continue
            }
            
            val dx = if (section) cell.maxX - cell.x else sx
            val dy = if (section) cell.maxY - cell.y else sy
            val dz = if (section) cell.maxZ - cell.z else sz
            when {
                dx >= dy && dx >= dz -> {
                    val mid = if (section) cell.x + dx / 2 else (((cell.x shr 4) + sx / 2 + 1) shl 4) - 1
                    add(cell.x, cell.y, cell.z, mid, cell.maxY, cell.maxZ, section)
                    add(mid + 1, cell.y, cell.z, cell.maxX, cell.maxY, cell.maxZ, section)
                }
                dy >= dz -> {
                    val mid = if (section) cell.y + dy / 2 else (((cell.y shr 4) + sy / 2 + 1) shl 4) - 1
                    add(cell.x, cell.y, cell.z, cell.maxX, mid, cell.maxZ, section)
                    add(cell.x, mid + 1, cell.z, cell.maxX, cell.maxY, cell.maxZ, section)
                }
                else -> {
                    val mid = if (section) cell.z + dz / 2 else (((cell.z shr 4) + sz / 2 + 1) shl 4) - 1
                    add(cell.x, cell.y, cell.z, cell.maxX, cell.maxY, mid, section)
                    add(cell.x, cell.y, mid + 1, cell.maxX, cell.maxY, cell.maxZ, section)
                }
            }
        }
    }
}
