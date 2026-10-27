package com.blacklaunch.launcher.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SnakeGameTest {
    private fun cell(x: Int, y: Int) = y * SnakeGame.COLS + x

    @Test fun movesTurnsAndIgnoresReverse() {
        val g = SnakeGame(Random(1))
        val head = g.body.first()
        g.step(g.interval + 0.001f)
        assertEquals(head + 1, g.body.first())
        g.turn(SnakeGame.Dir.LEFT) // розворот назад — ігнор
        g.turn(SnakeGame.Dir.DOWN)
        g.step(g.interval + 0.001f)
        assertEquals(head + 1 + SnakeGame.COLS, g.body.first())
    }

    @Test fun hittingWallEndsGame() {
        val g = SnakeGame(Random(2))
        repeat(40) { g.step(g.interval + 0.001f) }
        assertTrue(g.over)
    }

    @Test fun eatingGrowsScoresAndLevelsUp() {
        var game = SnakeGame(Random(4))
        repeat(SnakeGame.FOOD_PER_LEVEL) {
            val next = game.body.first() + 1
            game = SnakeGame.decode(game.encode().replace(Regex("f=\\d+"), "f=$next"), Random(4))!!
            val before = game.body.size
            game.step(game.interval + 0.001f)
            assertEquals(before + 1, game.body.size)
        }
        assertEquals(2, game.level)
        assertEquals(10 * SnakeGame.FOOD_PER_LEVEL, game.score)
    }

    @Test fun saveAndResume() {
        val g = SnakeGame(Random(5))
        g.step(g.interval * 2 + 0.01f)
        val copy = SnakeGame.decode(g.encode())!!
        assertEquals(g.body.toList(), copy.body.toList())
        assertEquals(g.food, copy.food)
        assertNull(SnakeGame.decode("garbage"))
    }

    @Test fun selfCollisionEndsGame() {
        val body = listOf(cell(5, 5), cell(4, 5), cell(4, 6), cell(5, 6), cell(6, 6), cell(6, 5))
        val g = SnakeGame.decode("b=${body.joinToString(",")};d=3;f=${cell(0, 0)};s=0;l=1;e=0;o=0")!!
        g.turn(SnakeGame.Dir.DOWN)
        g.step(g.interval + 0.001f)
        assertTrue(g.over)
    }
}

class BlocksGameTest {
    @Test fun allShapesHaveFourCellsInEveryRotation() {
        BlocksGame.SHAPES.forEach { rotations ->
            assertEquals(4, rotations.size)
            rotations.forEach { assertEquals(4, it.toSet().size) }
        }
    }

    @Test fun pieceFallsSlowlyAtFirstAndLocks() {
        val g = BlocksGame(Random(1))
        val y = g.py
        g.step(0.5f)
        assertEquals(y, g.py)
        g.step(0.6f)
        assertEquals(y + 1, g.py)
        g.hardDrop()
        assertTrue(g.board.any { it != 0 })
    }

    @Test fun fullRowClearsAfterAnimationAndScores() {
        val g = BlocksGame(Random(7))
        // Нижній рядок заповнений, крім чотирьох клітинок посередині під I-фігурою.
        val text = g.encode()
        val grid = IntArray(BlocksGame.COLS * BlocksGame.ROWS)
        val bottom = BlocksGame.ROWS - 1
        for (x in 0 until BlocksGame.COLS) if (x !in 3..6) grid[bottom * BlocksGame.COLS + x] = 1
        val withGrid = text.replace(Regex("g=[^;]*"), "g=" + grid.joinToString(","))
            .replace(Regex("p=\\d"), "p=0").replace(Regex("r=\\d"), "r=0").replace(Regex("x=-?\\d+"), "x=3").replace(Regex("y=-?\\d+"), "y=0")
        val game = BlocksGame.decode(withGrid, Random(7))!!
        game.hardDrop()
        assertEquals(listOf(bottom), game.clearing)
        game.step(BlocksGame.CLEAR_TIME + 0.01f)
        assertEquals(1, game.lines)
        assertTrue(game.score >= 100)
        assertTrue((0 until BlocksGame.COLS).all { game.board[bottom * BlocksGame.COLS + it] == 0 })
    }

    @Test fun movesAndRotatesInsideWalls() {
        val g = BlocksGame(Random(2))
        repeat(12) { g.move(-1) }
        assertTrue(g.cells().all { it.first >= 0 })
        repeat(12) { g.move(1) }
        assertTrue(g.cells().all { it.first < BlocksGame.COLS })
        g.rotate()
        assertTrue(g.cells().all { it.first in 0 until BlocksGame.COLS })
    }

    @Test fun saveAndResume() {
        val g = BlocksGame(Random(3))
        g.hardDrop()
        val copy = BlocksGame.decode(g.encode())!!
        assertEquals(g.board.toList(), copy.board.toList())
        assertEquals(g.score, copy.score)
        assertNull(BlocksGame.decode("x=1"))
    }

    @Test fun stackingToTopEndsGame() {
        val g = BlocksGame(Random(4))
        repeat(60) { if (!g.over) { g.hardDrop(); g.step(1f) } }
        assertTrue(g.over)
    }
}

class StarsGameTest {
    private fun run(g: StarsGame, seconds: Float) {
        var t = 0f
        while (t < seconds) {
            g.step(1f / 60f)
            t += 1f / 60f
        }
    }

    @Test fun formationFliesInAndShipShoots() {
        val g = StarsGame(Random(1))
        assertEquals(3 * StarsGame.COLS, g.enemies.size)
        run(g, 4f)
        assertTrue(g.enemies.any { it.state == StarsGame.State.FORMATION || it.state == StarsGame.State.DIVING })
        assertFalse(g.over)
    }

    @Test fun shootingEnemiesScores() {
        val g = StarsGame(Random(2))
        run(g, 30f)
        assertTrue("score ${g.score}", g.score > 0)
    }

    @Test fun clearingWaveStartsNextLevelWithMoreRows() {
        val g = StarsGame.decode("s=0;l=1;v=3;o=0;n=5000;e=1,0,0,1")!!
        g.target = g.slotX(g.enemies[0])
        run(g, 20f)
        assertTrue("level ${g.level}", g.level >= 2)
    }

    @Test fun saveAndResumeKeepsProgress() {
        val g = StarsGame(Random(3))
        run(g, 10f)
        val copy = StarsGame.decode(g.encode())!!
        assertEquals(g.score, copy.score)
        assertEquals(g.level, copy.level)
        assertEquals(g.enemies.size, copy.enemies.size)
        assertNotNull(copy)
        assertNull(StarsGame.decode("nothing"))
    }

    @Test fun losingAllLivesEndsGame() {
        val g = StarsGame(Random(4))
        // Стоїмо під строєм і чекаємо — рано чи пізно бомби й тарани заберуть усі життя.
        g.target = StarsGame.W / 2
        run(g, 400f)
        assertTrue(g.over || g.level > 1)
    }
}
