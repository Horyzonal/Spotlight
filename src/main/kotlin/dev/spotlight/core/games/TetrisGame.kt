package dev.spotlight.core.games

import kotlin.random.Random

class TetrisGame(private val seed: Long = 0L, initialBoard: IntArray? = null) {
    enum class Piece(val shape: List<Pair<Int, Int>>, val size: Int) {
        I(listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1), 4),
        O(listOf(1 to 0, 2 to 0, 1 to 1, 2 to 1), 4),
        T(listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1), 3),
        S(listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1), 3),
        Z(listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1), 3),
        J(listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1), 3),
        L(listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1), 3);

        fun cells(rotation: Int): List<Pair<Int, Int>> {
            var result = shape
            if (this != O) repeat(Math.floorMod(rotation, 4)) {
                result = result.map { (x, y) -> size - 1 - y to x }
            }
            return result
        }
    }

    private var random = Random(seed)
    private val tiles = IntArray(WIDTH * HEIGHT)
    private val bag = mutableListOf<Piece>()
    val board: IntArray get() = tiles.copyOf()
    var piece = Piece.I
        private set
    var nextPiece = Piece.I
        private set
    var x = 3
        private set
    var y = 0
        private set
    var rotation = 0
        private set
    var score = 0L
        private set
    var lines = 0
        private set
    val level: Int get() = (lines / 10 + 1).coerceAtMost(20)
    val gravityTicks: Int get() = (21 - level).coerceAtLeast(2)
    var gameOver = false
        private set
    var paused = false
        private set
    private var ticks = 0
    val activeCells: List<Pair<Int, Int>> get() = piece.cells(rotation).map { (dx, dy) -> x + dx to y + dy }

    init {
        if (initialBoard != null) require(initialBoard.size == tiles.size && initialBoard.all { it in 0..7 })
        restart()
        if (initialBoard != null) {
            initialBoard.copyInto(tiles)
            gameOver = !canPlace(piece, x, y, rotation)
        }
    }

    fun restart() {
        random = Random(seed)
        tiles.fill(0)
        bag.clear()
        score = 0
        lines = 0
        gameOver = false
        paused = false
        ticks = 0
        nextPiece = draw()
        spawn()
    }

    private fun draw(): Piece {
        if (bag.isEmpty()) bag.addAll(Piece.entries.shuffled(random))
        return bag.removeAt(bag.lastIndex)
    }

    fun canPlace(piece: Piece, x: Int, y: Int, rotation: Int = 0): Boolean =
        piece.cells(rotation).all { (dx, dy) ->
            val column = x.toLong() + dx
            val row = y.toLong() + dy
            column in 0L until WIDTH.toLong() && row in 0L until HEIGHT.toLong() &&
                tiles[row.toInt() * WIDTH + column.toInt()] == 0
        }

    fun move(dx: Int): Boolean {
        if (gameOver || paused || dx !in -1..1 || !canPlace(piece, x + dx, y, rotation)) return false
        x += dx
        return true
    }

    fun rotate(): Boolean {
        if (gameOver || paused) return false
        val next = (rotation + 1) % 4
        
        for ((dx, dy) in listOf(0 to 0, -1 to 0, 1 to 0, -2 to 0, 2 to 0, 0 to -1, 0 to -2)) {
            if (canPlace(piece, x + dx, y + dy, next)) {
                x += dx
                y += dy
                rotation = next
                return true
            }
        }
        return false
    }

    fun softDrop(): Boolean {
        if (gameOver || paused) return false
        val moved = descend()
        if (moved) score++
        ticks = 0
        return moved
    }

    fun hardDrop(): Int {
        if (gameOver || paused) return 0
        var distance = 0
        while (canPlace(piece, x, y + 1, rotation)) { y++; distance++ }
        score += distance * 2L
        lock()
        return distance
    }

    fun togglePause() { if (!gameOver) paused = !paused }

    fun tick() {
        if (gameOver || paused) return
        if (++ticks >= gravityTicks) { ticks = 0; descend() }
    }

    private fun descend(): Boolean {
        if (canPlace(piece, x, y + 1, rotation)) { y++; return true }
        lock()
        return false
    }

    private fun lock() {
        activeCells.forEach { (column, row) -> tiles[row * WIDTH + column] = piece.ordinal + 1 }
        var target = HEIGHT - 1
        var cleared = 0
        for (row in HEIGHT - 1 downTo 0) {
            if ((0 until WIDTH).all { tiles[row * WIDTH + it] != 0 }) cleared++
            else {
                for (column in 0 until WIDTH) tiles[target * WIDTH + column] = tiles[row * WIDTH + column]
                target--
            }
        }
        for (row in 0..target) for (column in 0 until WIDTH) tiles[row * WIDTH + column] = 0
        score += intArrayOf(0, 100, 300, 500, 800)[cleared.coerceAtMost(4)] * level.toLong()
        lines = (lines.toLong() + cleared).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        spawn()
    }

    private fun spawn() {
        piece = nextPiece
        nextPiece = draw()
        x = 3
        y = 0
        rotation = 0
        ticks = 0
        gameOver = !canPlace(piece, x, y, rotation)
    }

    companion object {
        const val WIDTH = 10
        const val HEIGHT = 20
    }
}
