package dev.spotlight.core.games

import kotlin.random.Random

class SnakeGame(private val seed: Long, val width: Int = 20, val height: Int = 16) {
    data class Cell(val x: Int, val y: Int)
    enum class Direction(val dx: Int, val dy: Int) { UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0) }

    companion object { const val MOVE_TICKS = 4 }

    private var random = Random(seed)
    private val body = mutableListOf<Cell>()
    val snake: List<Cell> get() = body.toList()
    var food: Cell? = null
        private set
    var direction = Direction.RIGHT
        private set
    private var pending = direction
    private var ticks = 0
    var score = 0
        private set
    var paused = false
        private set
    var gameOver = false
        private set
    var won = false
        private set

    init {
        require(width in 4..100 && height in 2..100)
        restart()
    }

    fun restart() {
        random = Random(seed)
        body.clear()
        val x = width / 2
        val y = height / 2
        body.addAll(listOf(Cell(x, y), Cell(x - 1, y), Cell(x - 2, y)))
        direction = Direction.RIGHT
        pending = direction
        ticks = 0
        score = 0
        paused = false
        gameOver = false
        won = false
        spawnFood()
    }

    fun turn(next: Direction): Boolean {
        if (paused || gameOver || pending != direction || next == direction ||
            next.dx == -direction.dx && next.dy == -direction.dy) return false
        pending = next
        return true
    }

    fun togglePause() { if (!gameOver) paused = !paused }

    fun tick() {
        if (paused || gameOver) return
        if (++ticks < MOVE_TICKS) return
        ticks = 0
        direction = pending
        val head = body.first()
        val next = Cell(head.x + direction.dx, head.y + direction.dy)
        val grows = next == food
        
        if (next.x !in 0 until width || next.y !in 0 until height ||
            body.take(if (grows) body.size else body.size - 1).contains(next)) {
            gameOver = true
            return
        }
        body.add(0, next)
        if (grows) {
            score++
            spawnFood()
        } else body.removeAt(body.lastIndex)
    }

    private fun spawnFood() {
        val free = (0 until width * height).map { Cell(it % width, it / width) }.filter { it !in body }
        food = if (free.isEmpty()) null else free[random.nextInt(free.size)]
        won = free.isEmpty()
        gameOver = won
    }
}
