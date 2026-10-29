package dev.spotlight.core.games

import kotlin.random.Random

class Game2048(private val seed: Long = 0L, initialBoard: IntArray? = null) {
    enum class Direction { LEFT, RIGHT, UP, DOWN }

    private var random = Random(seed)
    private val tiles = IntArray(16)
    val board: IntArray get() = tiles.copyOf()
    var score = 0L
        private set
    val won: Boolean get() = tiles.any { it >= 2048 }
    val gameOver: Boolean get() = tiles.none { it == 0 } && (0..15).none { i ->
        tiles[i] < (1 shl 30) && ((i % 4 < 3 && tiles[i] == tiles[i + 1]) || (i / 4 < 3 && tiles[i] == tiles[i + 4]))
    }

    init {
        if (initialBoard == null) restart() else {
            require(initialBoard.size == 16 && initialBoard.all { it == 0 || (it >= 2 && it <= (1 shl 30) && it and (it - 1) == 0) })
            initialBoard.copyInto(tiles)
        }
    }

    fun restart() {
        random = Random(seed)
        tiles.fill(0)
        score = 0
        spawn()
        spawn()
    }

    fun move(direction: Direction): Boolean {
        val before = tiles.copyOf()
        for (line in 0..3) {
            val indices = IntArray(4) { offset ->
                when (direction) {
                    Direction.LEFT -> line * 4 + offset
                    Direction.RIGHT -> line * 4 + 3 - offset
                    Direction.UP -> offset * 4 + line
                    Direction.DOWN -> (3 - offset) * 4 + line
                }
            }
            val values = indices.map { tiles[it] }.filter { it != 0 }
            val merged = mutableListOf<Int>()
            var i = 0
            while (i < values.size) {
                
                if (i + 1 < values.size && values[i] == values[i + 1] && values[i] < (1 shl 30)) {
                    val value = values[i] * 2
                    merged += value
                    score += value
                    i += 2
                } else merged += values[i++]
            }
            indices.forEachIndexed { index, cell -> tiles[cell] = merged.getOrElse(index) { 0 } }
        }
        if (tiles.contentEquals(before)) return false
        spawn()
        return true
    }

    private fun spawn() {
        val empty = tiles.indices.filter { tiles[it] == 0 }
        if (empty.isNotEmpty()) tiles[empty[random.nextInt(empty.size)]] = if (random.nextInt(10) == 0) 4 else 2
    }
}
