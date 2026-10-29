package dev.spotlight.core.games

object RunnerGamesCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        fun step(game: SnakeGame) { repeat(SnakeGame.MOVE_TICKS) { game.tick() } }
        fun cycle(game: SnakeGame) {
            val head = game.snake.first()
            val direction = when {
                head.y == 1 && head.x < 3 -> SnakeGame.Direction.RIGHT
                head.y == 1 -> SnakeGame.Direction.UP
                head.x > 0 -> SnakeGame.Direction.LEFT
                else -> SnakeGame.Direction.DOWN
            }
            game.turn(direction)
            step(game)
        }
        val snake = SnakeGame(42)
        val initialFood = snake.food
        check(initialFood !in snake.snake && snake.snake.size == 3)
        check(!snake.turn(SnakeGame.Direction.LEFT))
        check(snake.turn(SnakeGame.Direction.UP))
        check(!snake.turn(SnakeGame.Direction.LEFT))
        val initialBody = snake.snake
        repeat(SnakeGame.MOVE_TICKS - 1) { snake.tick() }
        check(snake.snake == initialBody)
        snake.tick()
        check(snake.snake.first().y == initialBody.first().y - 1)
        snake.togglePause()
        val frozen = snake.snake
        repeat(100) { snake.tick() }
        check(snake.snake == frozen && !snake.turn(SnakeGame.Direction.RIGHT))
        snake.restart()
        check(snake.food == initialFood && snake.snake == initialBody && snake.score == 0 && !snake.paused)
        repeat(100) { snake.tick() }
        check(snake.gameOver && !snake.won)

        val full = SnakeGame(7, 4, 2)
        repeat(100) {
            if (!full.gameOver) {
                val size = full.snake.size
                val score = full.score
                cycle(full)
                check(full.snake.size - size == full.score - score)
                check(full.food == null || full.food !in full.snake)
            }
        }
        check(full.won && full.gameOver && full.food == null && full.snake.size == 8 && full.score == 5)
        val bodyHit = SnakeGame(7, 4, 2)
        var hit = false
        repeat(100) {
            if (!hit && !bodyHit.gameOver) {
                val head = bodyHit.snake.first()
                val inward = if (head.y == 0) SnakeGame.Direction.DOWN else SnakeGame.Direction.UP
                val target = SnakeGame.Cell(head.x, head.y + inward.dy)
                if (bodyHit.direction in listOf(SnakeGame.Direction.LEFT, SnakeGame.Direction.RIGHT) && target in bodyHit.snake.dropLast(1)) {
                    check(bodyHit.turn(inward))
                    step(bodyHit)
                    check(bodyHit.gameOver && !bodyHit.won)
                    hit = true
                } else cycle(bodyHit)
            }
        }
        check(hit)
        full.restart()
        check(!full.gameOver && !full.won && full.snake.size == 3 && full.score == 0)

        val dino = DinoGame(42)
        val rock = DinoGame.Obstacle(DinoGame.PLAYER_X, DinoGame.Kind.ROCK)
        val glider = DinoGame.Obstacle(DinoGame.PLAYER_X, DinoGame.Kind.GLIDER)
        check(dino.hits(rock) && dino.hits(glider))
        check(!dino.hits(rock.copy(x = 200.0)))
        dino.setDucking(true)
        check(dino.hits(rock) && !dino.hits(glider) && !dino.jump())
        dino.setDucking(false)
        check(dino.jump())
        dino.tick()
        check(!dino.jump())
        repeat(4) { dino.tick() }
        check(dino.altitude > DinoGame.Kind.TALL_ROCK.height && !dino.hits(rock))
        repeat(25) { dino.tick() }
        check(dino.altitude == 0.0)
        dino.togglePause()
        val pausedScore = dino.score
        repeat(100) { dino.tick() }
        check(dino.score == pausedScore && !dino.jump())
        dino.restart()
        check(dino.score == 0L && dino.passed == 0 && !dino.paused && !dino.gameOver && !dino.ducking)
        repeat(35) { dino.tick() }
        check(dino.obstacles.size == 1 && dino.obstacles.first().x == DinoGame.WIDTH.toDouble())
        val firstSpawn = dino.obstacles
        repeat(100) { dino.tick() }
        check(dino.gameOver)
        val deadScore = dino.score
        repeat(100) { dino.tick() }
        check(dino.score == deadScore)
        dino.restart()
        repeat(35) { dino.tick() }
        check(dino.obstacles == firstSpawn)

        
        dino.restart()
        val twin = DinoGame(42)
        val kinds = mutableSetOf<DinoGame.Kind>()
        repeat(6000) {
            for (game in listOf(dino, twin)) {
                val next = game.obstacles.firstOrNull { it.x + it.kind.width > DinoGame.PLAYER_X }
                game.setDucking(next?.kind == DinoGame.Kind.GLIDER)
                if (next != null && next.kind != DinoGame.Kind.GLIDER &&
                    next.x - (DinoGame.PLAYER_X + DinoGame.PLAYER_WIDTH) in 0.0..(game.speed * 6)) game.jump()
                game.tick()
                check(!game.gameOver)
                check(game.speed <= DinoGame.MAX_SPEED)
            }
            kinds += dino.obstacles.map { it.kind }
            check(dino.obstacles == twin.obstacles && dino.score == twin.score && dino.altitude == twin.altitude)
        }
        check(dino.passed > 50 && dino.score > 6000 && dino.speed == DinoGame.MAX_SPEED)
        check(kinds == DinoGame.Kind.entries.toSet())
        println("RunnerGamesCheck: all checks passed")
    }
}
