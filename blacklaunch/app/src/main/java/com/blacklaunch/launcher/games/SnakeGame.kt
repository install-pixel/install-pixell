package com.blacklaunch.launcher.games

import kotlin.random.Random

/**
 * «Змійка» — мінімум малюнка: лінія й крапка. Поле [COLS]×[ROWS], стіни — кінець гри.
 * Кожні [FOOD_PER_LEVEL] з'їдені — новий рівень і трохи швидше.
 */
internal class SnakeGame(private val rnd: Random = Random.Default) : ArcadeGame {
    enum class Dir(val dx: Int, val dy: Int) { UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0) }

    /** Голова — перша. Клітинка = y * COLS + x. */
    val body = ArrayDeque<Int>()
    var dir = Dir.RIGHT
        private set
    private val turns = ArrayDeque<Dir>()
    var food = 0
        private set
    override var score = 0
        private set
    override var level = 1
        private set
    var eaten = 0
        private set
    override var over = false
        private set
    private var clock = 0f

    /** Щойно з'їв — для короткого «спалаху» на малюнку. */
    var flash = 0f
        private set

    init {
        reset()
    }

    fun reset() {
        body.clear()
        turns.clear()
        val y = ROWS / 2
        for (x in 6 downTo 3) body.addLast(y * COLS + x)
        dir = Dir.RIGHT
        score = 0
        level = 1
        eaten = 0
        over = false
        clock = 0f
        placeFood()
    }

    /** Поворот (свайп): розворот на 180° ігнорується; до двох поворотів наперед — щоб швидкі свайпи не губились. */
    fun turn(to: Dir) {
        val last = turns.lastOrNull() ?: dir
        if (to == last || (to.dx == -last.dx && to.dy == -last.dy)) return
        if (turns.size < 2) turns.addLast(to)
    }

    val interval: Float get() = (0.2f * Math.pow(0.92, (level - 1).toDouble()).toFloat()).coerceAtLeast(0.07f)

    override fun step(dt: Float) {
        if (over) return
        if (flash > 0f) flash = (flash - dt).coerceAtLeast(0f)
        clock += dt
        while (clock >= interval && !over) {
            clock -= interval
            move()
        }
    }

    private fun move() {
        turns.removeFirstOrNull()?.let { dir = it }
        val head = body.first()
        val x = head % COLS + dir.dx
        val y = head / COLS + dir.dy
        if (x !in 0 until COLS || y !in 0 until ROWS) {
            over = true
            return
        }
        val next = y * COLS + x
        val grows = next == food
        // Хвіст іде геть у той самий крок — у нього можна «наступати».
        val tailFree = !grows && next == body.last()
        if (next in body && !tailFree) {
            over = true
            return
        }
        body.addFirst(next)
        if (grows) {
            eaten++
            score += 10 * level
            flash = 0.25f
            if (eaten % FOOD_PER_LEVEL == 0) level++
            placeFood()
        } else {
            body.removeLast()
        }
    }

    private fun placeFood() {
        val free = (0 until COLS * ROWS).filter { it !in body }
        if (free.isEmpty()) {
            over = true
            return
        }
        food = free[rnd.nextInt(free.size)]
    }

    override fun encode(): String = GameWriter()
        .putList("b", body.toList()).put("d", dir.ordinal).put("f", food)
        .put("s", score).put("l", level).put("e", eaten).put("o", if (over) 1 else 0)
        .toString()

    companion object {
        const val COLS = 16
        const val ROWS = 24
        const val FOOD_PER_LEVEL = 5

        fun decode(text: String?, rnd: Random = Random.Default): SnakeGame? {
            val r = GameReader(text ?: return null)
            val body = r.ints("b")?.takeIf { it.size >= 2 && it.all { c -> c in 0 until COLS * ROWS } } ?: return null
            return SnakeGame(rnd).apply {
                this.body.clear()
                this.body.addAll(body)
                dir = Dir.entries.getOrNull(r.int("d") ?: 0) ?: Dir.RIGHT
                food = r.int("f")?.takeIf { it !in body } ?: food
                score = r.int("s") ?: 0
                level = r.int("l") ?: 1
                eaten = r.int("e") ?: 0
                over = r.int("o") == 1
            }
        }
    }
}
