package dev.spotlight.core.games

import kotlin.random.Random

class DinoGame(private val seed: Long) {
    enum class Kind(val width: Double, val bottom: Double, val height: Double) {
        ROCK(14.0, 0.0, 22.0), TALL_ROCK(18.0, 0.0, 30.0), GLIDER(24.0, 23.0, 12.0)
    }
    data class Obstacle(val x: Double, val kind: Kind)
    companion object {
        const val WIDTH = 320
        const val HEIGHT = 100
        const val PLAYER_X = 38.0
        const val PLAYER_WIDTH = 18.0
        const val MAX_SPEED = 8.0
    }

    private var random = Random(seed)
    private val hazards = mutableListOf<Obstacle>()
    val obstacles: List<Obstacle> get() = hazards.toList()
    var altitude = 0.0
        private set
    private var velocity = 0.0
    var ducking = false
        private set
    val playerHeight: Double get() = if (ducking) 18.0 else 34.0
    var score = 0L
        private set
    var passed = 0
        private set
    val speed: Double get() = (4.0 + score / 400.0).coerceAtMost(MAX_SPEED)
    var paused = false
        private set
    var gameOver = false
        private set
    private var spawnTicks = 0

    init { restart() }

    fun restart() {
        random = Random(seed)
        hazards.clear()
        altitude = 0.0
        velocity = 0.0
        ducking = false
        score = 0
        passed = 0
        paused = false
        gameOver = false
        spawnTicks = 35
    }

    fun jump(): Boolean {
        if (paused || gameOver || altitude != 0.0 || ducking) return false
        velocity = 10.0
        return true
    }

    fun setDucking(value: Boolean) {
        if (!gameOver) ducking = value
    }

    fun togglePause() { if (!gameOver) paused = !paused }

    fun tick() {
        if (paused || gameOver) return
        if (velocity != 0.0 || altitude > 0.0) {
            altitude = (altitude + velocity).coerceAtLeast(0.0)
            velocity = if (altitude == 0.0) 0.0 else velocity - 0.9
        }
        val distance = speed
        for (i in hazards.indices) hazards[i] = hazards[i].copy(x = hazards[i].x - distance)
        if (--spawnTicks <= 0) {
            val kind = if (score < 200) Kind.ROCK else Kind.entries[random.nextInt(Kind.entries.size)]
            hazards += Obstacle(WIDTH.toDouble(), kind)
            
            spawnTicks = 35 + random.nextInt(16)
        }
        if (hazards.any { hits(it) }) {
            gameOver = true
            return
        }
        val cleared = hazards.count { it.x + it.kind.width < 0.0 }
        hazards.removeAll { it.x + it.kind.width < 0.0 }
        passed += cleared
        score += 1L + cleared * 25L
    }

    fun hits(obstacle: Obstacle): Boolean =
        PLAYER_X < obstacle.x + obstacle.kind.width && PLAYER_X + PLAYER_WIDTH > obstacle.x &&
            altitude < obstacle.kind.bottom + obstacle.kind.height && altitude + playerHeight > obstacle.kind.bottom
}
