package com.blacklaunch.launcher.games

import kotlin.random.Random

/**
 * «Блоки» — для тих, хто любить неспішні ігри: класичні фігури з чотирьох квадратів падають у склянку
 * [COLS]×[ROWS]; повний рядок зникає. Очки — лише за рядки (100 / 300 / 500 / 800 за 1–4 одразу, × рівень).
 * Швидкість — за класичною таблицею, але новий рівень лише кожні [LINES_PER_LEVEL] рядків (у класиці — 10): до 29-го
 * дедалі швидше, далі найшвидше й так і лишається (не обнуляється). Пауза будь-коли; фігура не «прилипає» одразу — є мить, щоб її посунути.
 */
internal class BlocksGame(private val rnd: Random = Random.Default) : ArcadeGame {
    /** Склянка: 0 — порожньо, інакше номер фігури + 1. Рядок 0 — верхній. */
    val board = IntArray(COLS * ROWS)
    var piece = 0
        private set
    var rotation = 0
        private set
    var px = 0
        private set
    var py = 0
        private set
    var next = 0
        private set
    private val bag = ArrayDeque<Int>()
    override var score = 0
        private set
    override var level = 1
        private set
    var lines = 0
        private set
    override var over = false
        private set
    private var fall = 0f
    private var lockTimer = 0f
    private val buzz = BuzzQueue()

    /** Рядки поспіль без «порожніх» фігур (серія): кожен наступний — бонус. -1 — серії немає. */
    var combo = -1
        private set

    /** Що показати поверх склянки: «Тетріс!», «Серія ×N», «Чиста склянка!» — і скільки ще. */
    enum class Feat { TETRIS, COMBO, PERFECT }
    var feat: Feat? = null
        private set
    var featValue = 0
        private set
    var featTime = 0f
        private set

    /** Скільки рядків до наступного рівня. */
    val linesToLevel get() = LINES_PER_LEVEL - lines % LINES_PER_LEVEL

    override fun drainBuzz() = buzz.drain()

    /** Рядки, що зникають (коротка анімація), і скільки ще. */
    var clearing: List<Int> = emptyList()
        private set
    var clearTimer = 0f
        private set

    init {
        reset()
    }

    fun reset() {
        board.fill(0)
        bag.clear()
        score = 0
        level = 1
        lines = 0
        over = false
        clearing = emptyList()
        next = draw()
        spawn()
    }

    /** «Мішок із сімох»: кожна фігура раз на сім — без довгих серій однакових. */
    private fun draw(): Int {
        if (bag.isEmpty()) bag.addAll((0 until 7).shuffled(rnd))
        return bag.removeFirst()
    }

    private fun spawn() {
        piece = next
        next = draw()
        rotation = 0
        px = COLS / 2 - 2
        py = -1
        fall = 0f
        lockTimer = 0f
        if (!fits(piece, rotation, px, py) && !fits(piece, rotation, px, py + 1)) over = true
        else if (!fits(piece, rotation, px, py)) py += 1
    }

    /** Клітинки фігури (x, y) у склянці. */
    fun cells(type: Int = piece, rot: Int = rotation, x: Int = px, y: Int = py): List<Pair<Int, Int>> =
        SHAPES[type][rot and 3].map { (cx, cy) -> x + cx to y + cy }

    private fun fits(type: Int, rot: Int, x: Int, y: Int): Boolean = cells(type, rot, x, y).all { (cx, cy) ->
        cx in 0 until COLS && cy < ROWS && (cy < 0 || board[cy * COLS + cx] == 0)
    }

    /** Де фігура впаде («тінь» на малюнку). */
    fun ghostY(): Int {
        var y = py
        while (fits(piece, rotation, px, y + 1)) y++
        return y
    }

    val busy get() = over || clearing.isNotEmpty()

    fun move(dx: Int): Boolean {
        if (busy || !fits(piece, rotation, px + dx, py)) return false
        px += dx
        if (!fits(piece, rotation, px, py + 1)) lockTimer = 0f
        return true
    }

    /** Поворот за годинниковою; біля стінки чи інших блоків — пробуємо трохи зсунути. */
    fun rotate(): Boolean {
        if (busy) return false
        val r = (rotation + 1) and 3
        for ((dx, dy) in KICKS) {
            if (fits(piece, r, px + dx, py + dy)) {
                rotation = r
                px += dx
                py += dy
                lockTimer = 0f
                return true
            }
        }
        return false
    }

    /** Трохи швидше вниз (очок за це немає — лише за рядки). */
    fun softDrop(): Boolean {
        if (busy || !fits(piece, rotation, px, py + 1)) return false
        py++
        fall = 0f
        return true
    }

    /** Одразу вниз. */
    fun hardDrop() {
        if (busy) return
        py = ghostY()
        lock()
    }

    /** Скільки секунд на одну клітинку падіння на цьому рівні (класична таблиця кадрів при 60 кадрах/с). */
    val fallInterval: Float get() = SPEED_FRAMES[(level - 1).coerceIn(0, SPEED_FRAMES.lastIndex)] / 60f

    override fun step(dt: Float) {
        if (over) return
        if (featTime > 0f) featTime -= dt
        if (clearing.isNotEmpty()) {
            clearTimer -= dt
            if (clearTimer <= 0f) finishClear()
            return
        }
        if (fits(piece, rotation, px, py + 1)) {
            fall += dt
            if (fall >= fallInterval) {
                fall -= fallInterval
                py++
            }
        } else {
            lockTimer += dt
            if (lockTimer >= LOCK_DELAY) lock()
        }
    }

    private fun lock() {
        cells().forEach { (x, y) ->
            if (y < 0) {
                over = true
                return
            }
            board[y * COLS + x] = piece + 1
        }
        val full = (0 until ROWS).filter { y -> (0 until COLS).all { board[y * COLS + it] != 0 } }
        buzz.add(if (full.size >= 4) GameBuzz.STRONG else if (full.isNotEmpty()) GameBuzz.MEDIUM else GameBuzz.LIGHT)
        if (full.isNotEmpty()) {
            clearing = full
            clearTimer = CLEAR_TIME
        } else {
            combo = -1
            spawn()
        }
    }

    private fun finishClear() {
        val rows = clearing
        clearing = emptyList()
        val keep = (0 until ROWS).filter { it !in rows }
        val copy = board.copyOf()
        board.fill(0)
        // Решта рядків опускається донизу.
        keep.reversed().forEachIndexed { i, y ->
            val target = ROWS - 1 - i
            for (x in 0 until COLS) board[target * COLS + x] = copy[y * COLS + x]
        }
        score += LINE_SCORE[rows.size.coerceAtMost(4)] * level
        // Серія: рядки кілька фігур поспіль — +50 × довжина серії × рівень.
        combo++
        if (combo > 0) {
            score += 50 * combo * level
            showFeat(Feat.COMBO, combo + 1)
        }
        if (rows.size >= 4) showFeat(Feat.TETRIS, LINE_SCORE[4] * level)
        // Склянку очищено повністю — великий бонус.
        if (board.all { it == 0 }) {
            score += PERFECT_BONUS * level
            showFeat(Feat.PERFECT, PERFECT_BONUS * level)
            buzz.add(GameBuzz.STRONG)
        }
        lines += rows.size
        level = 1 + lines / LINES_PER_LEVEL
        spawn()
    }

    private fun showFeat(kind: Feat, value: Int) {
        feat = kind
        featValue = value
        featTime = 1.8f
    }

    override fun encode(): String = GameWriter()
        .putList("g", board.toList()).put("p", piece).put("r", rotation).put("x", px).put("y", py)
        .put("n", next).putList("k", bag.toList()).put("s", score).put("l", level).put("i", lines).put("o", if (over) 1 else 0)
        .toString()

    companion object {
        const val COLS = 10
        const val ROWS = 20
        const val LOCK_DELAY = 0.5f
        const val PERFECT_BONUS = 2000
        const val CLEAR_TIME = 0.3f
        /** Новий рівень кожні 100 рядків — гра довго лишається спокійною. */
        const val LINES_PER_LEVEL = 100
        private val LINE_SCORE = intArrayOf(0, 100, 300, 500, 800)

        /** Кадрів на клітинку для рівнів 1…29+ (як у класичній версії; 29-й і далі — найшвидше). */
        private val SPEED_FRAMES = intArrayOf(
            48, 43, 38, 33, 28, 23, 18, 13, 8, 6,
            5, 5, 5, 4, 4, 4, 3, 3, 3,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 1,
        )
        private val KICKS = listOf(0 to 0, -1 to 0, 1 to 0, -2 to 0, 2 to 0, 0 to -1)

        /** Сім фігур у рамці 4×4; повороти — поворотом координат (I, O, T, S, Z, J, L). */
        private val BASE = listOf(
            listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1),
            listOf(1 to 0, 2 to 0, 1 to 1, 2 to 1),
            listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1),
            listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1),
            listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1),
            listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),
            listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1),
        )

        /** Для кожної фігури — 4 повороти: I і O в рамці 4×4 і 2×2, решта — 3×3. */
        val SHAPES: List<List<List<Pair<Int, Int>>>> = BASE.mapIndexed { type, cells ->
            val size = when (type) { 0 -> 4; 1 -> 0; else -> 3 }
            (0 until 4).map { r ->
                if (size == 0) cells
                else {
                    var c = cells
                    repeat(r) { c = c.map { (x, y) -> size - 1 - y to x } }
                    c
                }
            }
        }

        fun decode(text: String?, rnd: Random = Random.Default): BlocksGame? {
            val r = GameReader(text ?: return null)
            val grid = r.ints("g")?.takeIf { it.size == COLS * ROWS } ?: return null
            return BlocksGame(rnd).apply {
                grid.forEachIndexed { i, v -> board[i] = v.coerceIn(0, 7) }
                piece = (r.int("p") ?: 0).coerceIn(0, 6)
                rotation = (r.int("r") ?: 0) and 3
                px = r.int("x") ?: px
                py = r.int("y") ?: py
                next = (r.int("n") ?: 0).coerceIn(0, 6)
                bag.clear()
                bag.addAll(r.ints("k").orEmpty().filter { it in 0..6 })
                score = r.int("s") ?: 0
                level = r.int("l") ?: 1
                lines = r.int("i") ?: 0
                over = r.int("o") == 1
                if (!over && !fits(piece, rotation, px, py)) over = true
            }
        }
    }
}
