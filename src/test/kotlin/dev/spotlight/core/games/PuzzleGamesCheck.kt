package dev.spotlight.core.games

object PuzzleGamesCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        fun tiles(vararg firstRow: Int) = IntArray(16) { firstRow.getOrElse(it) { 0 } }
        val merge = Game2048(7, tiles(2, 2, 2, 2))
        check(merge.move(Game2048.Direction.LEFT))
        check(merge.board.take(2) == listOf(4, 4) && merge.score == 8L)
        check(merge.board.count { it != 0 } == 3)
        check(merge.board.sum() in listOf(10, 12))
        val once = Game2048(7, tiles(2, 2, 4))
        once.move(Game2048.Direction.LEFT)
        check(once.board.take(2) == listOf(4, 4) && once.score == 4L)
        val unchanged = Game2048(7, tiles(2, 4))
        val before = unchanged.board
        check(!unchanged.move(Game2048.Direction.LEFT))
        check(unchanged.board.contentEquals(before) && unchanged.score == 0L)
        val same = Game2048(7, tiles(2, 4))
        unchanged.move(Game2048.Direction.DOWN)
        same.move(Game2048.Direction.DOWN)
        check(unchanged.board.contentEquals(same.board))
        val dead = intArrayOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2)
        check(Game2048(0, dead).gameOver)
        check(!Game2048(0, dead.copyOf().also { it[0] = 4 }).gameOver)
        val win = Game2048(0, tiles(1024, 1024))
        win.move(Game2048.Direction.LEFT)
        check(win.won && win.score == 2048L)
        check(win.move(Game2048.Direction.DOWN))
        for (direction in Game2048.Direction.entries) {
            val game = Game2048(9, tiles(2, 2))
            game.move(direction)
            check(game.board.all { it == 0 || (it >= 2 && it and (it - 1) == 0) })
        }
        val fresh = Game2048(42)
        val start = fresh.board
        fresh.move(Game2048.Direction.RIGHT)
        fresh.restart()
        check(fresh.board.contentEquals(start) && fresh.score == 0L)
        check(start.count { it != 0 } == 2)
        val snapshot = fresh.board
        snapshot.fill(0)
        check(fresh.board.contentEquals(start))

        val tetris = TetrisGame(42)
        check(tetris.board.size == 200)
        for (piece in TetrisGame.Piece.entries) for (rotation in 0..3) {
            check(piece.cells(rotation).distinct().size == 4)
            check(tetris.canPlace(piece, 3, 0, rotation))
            check(!tetris.canPlace(piece, -10, 0, rotation))
            check(!tetris.canPlace(piece, 3, 20, rotation))
            check(!tetris.canPlace(piece, Int.MAX_VALUE, Int.MAX_VALUE, rotation))
        }
        repeat(4) { check(tetris.rotate()) }
        check(tetris.rotation == 0)
        repeat(20) { tetris.move(-1) }
        check(!tetris.move(-1))
        tetris.rotate()
        check(tetris.activeCells.all { (x, y) -> x in 0..9 && y in 0..19 })
        val pausedBoard = tetris.board
        val pausedCells = tetris.activeCells
        tetris.togglePause()
        repeat(100) { tetris.tick() }
        check(!tetris.move(1) && !tetris.rotate() && !tetris.softDrop() && tetris.hardDrop() == 0)
        check(tetris.board.contentEquals(pausedBoard) && tetris.activeCells == pausedCells)
        tetris.restart()
        val initialPiece = tetris.piece
        val initialNext = tetris.nextPiece
        repeat(tetris.gravityTicks) { tetris.tick() }
        check(tetris.y == 1)
        tetris.restart()
        check(tetris.piece == initialPiece && tetris.nextPiece == initialNext)
        check(tetris.y == 0 && tetris.score == 0L && tetris.lines == 0 && tetris.level == 1)
        check(!tetris.paused && !tetris.gameOver && tetris.board.all { it == 0 })

        
        val stream = mutableListOf<TetrisGame.Piece>()
        val bagGame = TetrisGame(11)
        repeat(14) {
            check(!bagGame.gameOver)
            stream += bagGame.piece
            repeat(10) { bagGame.move(-1) }
            repeat((it % 4) * 2) { bagGame.move(1) }
            bagGame.hardDrop()
            check(bagGame.board.all { value -> value in 0..7 })
            check(bagGame.activeCells.all { (x, y) -> x in 0..9 && y in 0..19 })
        }
        check(stream.take(7).toSet() == TetrisGame.Piece.entries.toSet())
        check(stream.drop(7).toSet() == TetrisGame.Piece.entries.toSet())

        val seed = (0L..100L).first { TetrisGame(it).piece == TetrisGame.Piece.I }
        val well = IntArray(200) { i -> if (i / 10 >= 16 && i % 10 != 5) 1 else 0 }
        val clear = TetrisGame(seed, well)
        check(clear.rotate())
        check(clear.hardDrop() > 0)
        check(clear.lines == 4 && clear.score >= 800 && clear.board.all { it == 0 })
        val blocked = IntArray(200) { if (it / 10 < 4) 1 else 0 }
        val over = TetrisGame(seed, blocked)
        check(over.gameOver && !over.move(1) && !over.rotate())
        over.hardDrop()
        check(over.board.contentEquals(blocked))
        over.restart()
        check(!over.gameOver && over.board.all { it == 0 })
        println("PuzzleGamesCheck passed: 2048 and Tetris")
    }
}
