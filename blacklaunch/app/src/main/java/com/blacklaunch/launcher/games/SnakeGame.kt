package com.blacklaunch.launcher.games

import kotlin.random.Random

/**
 * «Змійка» — мінімум малюнка: лінія й крапка. Поле [COLS]×[ROWS].
 * - Перед стіною чи перешкодою голова на мить зупиняється — є час повернути (крапку біля стіни можна взяти).
 * - Три життя: врізались — змійка з'являється знову посередині, рахунок і рівень лишаються.
 * - Кожні [FOOD_PER_LEVEL] крапок — новий рівень: трохи швидше, а раз на два рівні — інші перешкоди всередині поля.
 * - Іноді з'являється бонус (на кілька секунд): золото — багато очок, равлик — повільніше, ножиці — коротший хвіст,
 *   серце — ще одне життя.
 */
internal class SnakeGame(private val rnd: Random = Random.Default) : ArcadeGame {
    enum class Dir(val dx: Int, val dy: Int) { UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0) }
    enum class Bonus { GOLD, SLOW, SHRINK, LIFE }

    /** Голова — перша. Клітинка = y * COLS + x. */
    val body = ArrayDeque<Int>()
    var dir = Dir.RIGHT
        private set
    private val turns = ArrayDeque<Dir>()
    var food = 0
        private set
    var bonus: Bonus? = null
        private set
    var bonusCell = -1
        private set
    var bonusLeft = 0f
        private set
    /** Скільки ще повільніше (равлик). */
    var slowLeft = 0f
        private set
    var obstacles: Set<Int> = emptySet()
        private set
    override var score = 0
        private set
    override var level = 1
        private set
    var lives = 3
        private set
    var eaten = 0
        private set
    override var over = false
        private set
    private var clock = 0f

    /** Голова вперлась у стіну — один крок «пригальмовує», далі — удар. */
    var braking = false
        private set

    /** Щойно з'їв — короткий «спалах» на малюнку. */
    var flash = 0f
        private set

    /** Після удару — змійка блимає й чекає мить. */
    var respawn = 0f
        private set

    init {
        reset()
    }

    fun reset() {
        score = 0
        level = 1
        lives = 3
        eaten = 0
        over = false
        bonus = null
        slowLeft = 0f
        obstacles = layout(level)
        spawnSnake()
        placeFood()
    }

    private fun spawnSnake() {
        body.clear()
        turns.clear()
        val y = ROWS / 2
        for (x in 6 downTo 3) body.addLast(y * COLS + x)
        dir = Dir.RIGHT
        clock = 0f
        braking = false
    }

    /** Поворот (свайп): розворот на 180° ігнорується; до двох поворотів наперед — щоб швидкі свайпи не губились. */
    fun turn(to: Dir) {
        val last = turns.lastOrNull() ?: dir
        if (to == last || (to.dx == -last.dx && to.dy == -last.dy)) return
        if (turns.size < 2) turns.addLast(to)
    }

    val interval: Float
        get() {
            val base = (0.2f * Math.pow(0.94, (level - 1).toDouble()).toFloat()).coerceAtLeast(0.075f)
            return if (slowLeft > 0f) base * 1.6f else base
        }

    override fun step(dt: Float) {
        if (over) return
        if (flash > 0f) flash = (flash - dt).coerceAtLeast(0f)
        if (slowLeft > 0f) slowLeft = (slowLeft - dt).coerceAtLeast(0f)
        if (bonus != null) {
            bonusLeft -= dt
            if (bonusLeft <= 0f) bonus = null
        }
        if (respawn > 0f) {
            respawn -= dt
            return
        }
        clock += dt
        while (clock >= interval && !over && respawn <= 0f) {
            clock -= interval
            move()
        }
    }

    private fun blockedCell(x: Int, y: Int) = x !in 0 until COLS || y !in 0 until ROWS || (y * COLS + x) in obstacles

    private fun move() {
        turns.removeFirstOrNull()?.let { dir = it }
        val head = body.first()
        val x = head % COLS + dir.dx
        val y = head / COLS + dir.dy
        if (blockedCell(x, y)) {
            // Перший раз — лише пригальмували: ще можна повернути.
            if (!braking) {
                braking = true
                return
            }
            crash()
            return
        }
        braking = false
        val next = y * COLS + x
        val grows = next == food
        // Хвіст іде геть у той самий крок — у нього можна «наступати».
        val tailFree = !grows && next == body.last()
        if (next in body && !tailFree) {
            crash()
            return
        }
        body.addFirst(next)
        if (next == bonusCell && bonus != null) takeBonus()
        if (grows) {
            eaten++
            score += 10 * level
            flash = 0.25f
            if (eaten % FOOD_PER_LEVEL == 0) levelUp()
            placeFood()
            if (bonus == null && rnd.nextFloat() < 0.28f) placeBonus()
        } else {
            body.removeLast()
        }
    }

    private fun crash() {
        lives--
        if (lives <= 0) {
            over = true
            return
        }
        respawn = 1.2f
        spawnSnake()
        // Їжа чи бонус могли опинитись під новою змійкою.
        if (food in body) placeFood()
        if (bonusCell in body) bonus = null
    }

    private fun levelUp() {
        level++
        val next = layout(level)
        // Нові перешкоди не ставимо туди, де зараз змійка.
        obstacles = next - body.toSet()
    }

    private fun takeBonus() {
        when (bonus) {
            Bonus.GOLD -> score += 50 * level
            Bonus.SLOW -> slowLeft = 8f
            Bonus.SHRINK -> repeat(3) { if (body.size > 3) body.removeLast() }
            Bonus.LIFE -> lives = (lives + 1).coerceAtMost(5)
            null -> Unit
        }
        flash = 0.35f
        bonus = null
        bonusCell = -1
    }

    private fun freeCells() = (0 until COLS * ROWS).filter { it !in body && it !in obstacles && it != food && it != bonusCell }

    private fun placeFood() {
        val free = freeCells()
        if (free.isEmpty()) {
            over = true
            return
        }
        food = free[rnd.nextInt(free.size)]
    }

    private fun placeBonus() {
        val free = freeCells()
        if (free.isEmpty()) return
        bonusCell = free[rnd.nextInt(free.size)]
        bonus = when (rnd.nextFloat()) {
            in 0f..0.45f -> Bonus.GOLD
            in 0.45f..0.7f -> Bonus.SLOW
            in 0.7f..0.92f -> Bonus.SHRINK
            else -> Bonus.LIFE
        }
        bonusLeft = 7f
    }

    override fun encode(): String = GameWriter()
        .putList("b", body.toList()).put("d", dir.ordinal).put("f", food)
        .put("s", score).put("l", level).put("e", eaten).put("v", lives).put("o", if (over) 1 else 0)
        .toString()

    companion object {
        const val COLS = 16
        const val ROWS = 26
        const val FOOD_PER_LEVEL = 5

        /** Перешкоди рівня: раз на два рівні — інший візерунок (по колу), середній рядок завжди вільний. */
        fun layout(level: Int): Set<Int> {
            val cells = HashSet<Int>()
            fun bar(x0: Int, y0: Int, x1: Int, y1: Int) {
                for (y in minOf(y0, y1)..maxOf(y0, y1)) for (x in minOf(x0, x1)..maxOf(x0, x1)) cells += y * COLS + x
            }
            when (((level - 1) / 2) % 5) {
                0 -> Unit
                // Чотири кутики.
                1 -> {
                    bar(2, 3, 5, 3); bar(2, 3, 2, 6)
                    bar(10, 3, 13, 3); bar(13, 3, 13, 6)
                    bar(2, 22, 5, 22); bar(2, 19, 2, 22)
                    bar(10, 22, 13, 22); bar(13, 19, 13, 22)
                }
                // Дві горизонтальні смуги.
                2 -> { bar(3, 7, 12, 7); bar(3, 18, 12, 18) }
                // Дві вертикальні смуги з проходами.
                3 -> { bar(4, 3, 4, 10); bar(11, 15, 11, 22); bar(11, 3, 11, 9); bar(4, 16, 4, 22) }
                // Хрест без центру.
                else -> { bar(7, 2, 8, 9); bar(7, 16, 8, 23) }
            }
            // Рядок появи змійки завжди вільний.
            cells.removeAll { it / COLS == ROWS / 2 }
            return cells
        }

        fun decode(text: String?, rnd: Random = Random.Default): SnakeGame? {
            val r = GameReader(text ?: return null)
            val body = r.ints("b")?.takeIf { it.size >= 2 && it.all { c -> c in 0 until COLS * ROWS } } ?: return null
            return SnakeGame(rnd).apply {
                level = r.int("l") ?: 1
                obstacles = layout(level) - body.toSet()
                this.body.clear()
                this.body.addAll(body)
                dir = Dir.entries.getOrNull(r.int("d") ?: 0) ?: Dir.RIGHT
                food = r.int("f")?.takeIf { it !in body && it !in obstacles } ?: food
                score = r.int("s") ?: 0
                eaten = r.int("e") ?: 0
                lives = (r.int("v") ?: 3).coerceAtLeast(1)
                over = r.int("o") == 1
            }
        }
    }
}
