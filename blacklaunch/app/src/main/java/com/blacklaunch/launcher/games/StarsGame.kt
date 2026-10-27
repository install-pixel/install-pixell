package com.blacklaunch.launcher.games

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * «Зоряний патруль» — як класичні аркадні «космічні рої»: внизу кораблик (пальцем вліво-вправо, стріляє сам),
 * угорі — стрій прибульців, що погойдується; час від часу хтось пікірує на кораблик дугою й скидає бомби.
 * Знищили всіх — новий рівень: більше рядів, частіше й швидше пікірують. Три життя, кожні 5000 очок — ще одне.
 * Поле — умовні одиниці [W]×[H]; екран масштабує.
 */
internal class StarsGame(private val rnd: Random = Random.Default) : ArcadeGame {
    enum class Kind(val hp: Int, val points: Int) { DRONE(1, 50), GUARD(2, 150) }
    enum class State { ENTERING, FORMATION, DIVING }

    class Enemy(val row: Int, val col: Int, val kind: Kind) {
        var hp = kind.hp
        var state = State.ENTERING
        var x = 0f
        var y = 0f
        var t = 0f
        var delay = 0f
        var fromX = 0f
        var fromY = 0f
        var aimX = 0f
        var side = 1f
        var shotCooldown = 0f
        var hitFlash = 0f
    }

    class Shot(var x: Float, var y: Float, val vx: Float = 0f, val vy: Float)
    class Spark(val x: Float, val y: Float, var t: Float = 0f)

    val enemies = ArrayList<Enemy>()
    val shots = ArrayList<Shot>()
    val bombs = ArrayList<Shot>()
    val sparks = ArrayList<Spark>()

    override var score = 0
        private set
    override var level = 1
        private set
    var lives = 3
        private set
    override var over = false
        private set

    var shipX = W / 2
        private set
    /** Куди тягнуть пальцем (кораблик пливе туди). */
    var target: Float? = null

    /** Після удару кораблик блимає й невразливий. */
    var invulnerable = 0f
        private set

    /** «Рівень N» на початку рівня. */
    var banner = BANNER_TIME
        private set
    var time = 0f
        private set
    private var fireCooldown = 0f
    private var diveTimer = 2f
    private var nextLife = LIFE_EVERY

    init {
        startLevel()
    }

    fun reset() {
        score = 0
        level = 1
        lives = 3
        over = false
        shipX = W / 2
        nextLife = LIFE_EVERY
        startLevel()
    }

    private val rows get() = (2 + (level + 1) / 2).coerceAtMost(5)

    private fun startLevel(keep: List<Enemy>? = null) {
        enemies.clear()
        shots.clear()
        bombs.clear()
        banner = BANNER_TIME
        diveTimer = 2.5f
        val list = keep ?: (0 until rows).flatMap { row ->
            (0 until COLS).map { col -> Enemy(row, col, if (row == 0) Kind.GUARD else Kind.DRONE) }
        }
        list.forEach { e ->
            e.state = State.ENTERING
            e.t = 0f
            e.delay = 0.08f * e.col + 0.25f * e.row
            e.fromX = if (e.col < COLS / 2) -10f else W + 10f
            e.fromY = 10f + e.row * 6f
            e.x = e.fromX
            e.y = e.fromY
        }
        enemies.addAll(list)
    }

    /** Місце в строю: стрій повільно гойдається вліво-вправо. */
    fun slotX(e: Enemy) = W / 2 + (e.col - (COLS - 1) / 2f) * 9.5f + sin(time * 0.7f) * 8f
    fun slotY(e: Enemy) = 20f + e.row * 10f

    private val diveSpeed get() = 0.42f + level * 0.05f
    private val diveEvery get() = (3.2f - level * 0.3f).coerceAtLeast(0.7f)

    override fun step(dt: Float) {
        if (over) return
        time += dt
        if (invulnerable > 0f) invulnerable -= dt
        if (banner > 0f) banner -= dt
        sparks.forEach { it.t += dt }
        sparks.removeAll { it.t > 0.6f }
        moveShip(dt)
        fire(dt)
        moveEnemies(dt)
        moveShots(dt)
        collide()
        if (enemies.isEmpty() && !over) {
            level++
            startLevel()
        }
    }

    private fun moveShip(dt: Float) {
        val goal = target ?: return
        val dx = goal - shipX
        val step = SHIP_SPEED * dt
        shipX = if (abs(dx) <= step) goal else shipX + step * (if (dx > 0) 1 else -1)
        shipX = shipX.coerceIn(6f, W - 6f)
    }

    private fun fire(dt: Float) {
        fireCooldown -= dt
        if (banner > 0.6f || fireCooldown > 0f || shots.size >= 3) return
        shots += Shot(shipX, SHIP_Y - 6f, vy = -SHOT_SPEED)
        fireCooldown = FIRE_EVERY
    }

    private fun moveEnemies(dt: Float) {
        enemies.forEach { e ->
            if (e.hitFlash > 0f) e.hitFlash -= dt
            when (e.state) {
                State.ENTERING -> {
                    if (e.delay > 0f) {
                        e.delay -= dt
                        return@forEach
                    }
                    e.t = (e.t + dt / 1.3f).coerceAtMost(1f)
                    val k = 1f - (1f - e.t) * (1f - e.t)
                    e.x = e.fromX + (slotX(e) - e.fromX) * k
                    e.y = e.fromY + (slotY(e) - e.fromY) * k - sin(k * Math.PI.toFloat()) * 12f
                    if (e.t >= 1f) e.state = State.FORMATION
                }
                State.FORMATION -> {
                    e.x = slotX(e)
                    e.y = slotY(e)
                    // З другого рівня й у строю зрідка стріляють.
                    if (level >= 2) maybeBomb(e, dt, chancePerSecond = 0.012f * level)
                }
                State.DIVING -> {
                    e.t += dt * diveSpeed
                    val (x, y) = divePoint(e, e.t)
                    e.x = x
                    e.y = y
                    maybeBomb(e, dt, chancePerSecond = 0.6f + level * 0.08f)
                    // Пролетів униз — повертається згори на своє місце.
                    if (e.t >= 1f) {
                        e.state = State.ENTERING
                        e.t = 0f
                        e.delay = 0f
                        e.fromX = slotX(e)
                        e.fromY = -12f
                    }
                }
            }
        }
        if (banner > 0f) return
        diveTimer -= dt
        if (diveTimer <= 0f) {
            diveTimer = diveEvery * (0.6f + rnd.nextFloat() * 0.8f)
            // З вищих рівнів — інколи двоє одразу.
            repeat(if (level >= 3 && rnd.nextFloat() < 0.35f) 2 else 1) { startDive() }
        }
    }

    private fun startDive() {
        val ready = enemies.filter { it.state == State.FORMATION }
        if (ready.isEmpty()) return
        val e = ready[rnd.nextInt(ready.size)]
        e.state = State.DIVING
        e.t = 0f
        e.fromX = e.x
        e.fromY = e.y
        e.aimX = shipX
        e.side = if (rnd.nextBoolean()) 1f else -1f
        e.shotCooldown = 0.4f
    }

    /** Дуга пікірування (крива Безьє): убік, донизу на кораблик, повз нього за нижній край. */
    fun divePoint(e: Enemy, t: Float): Pair<Float, Float> {
        val u = t.coerceIn(0f, 1f)
        val p0x = e.fromX
        val p0y = e.fromY
        val p1x = e.fromX + e.side * 28f
        val p1y = e.fromY - 6f
        val p2x = e.aimX - e.side * 20f
        val p2y = SHIP_Y - 10f
        val p3x = e.aimX + e.side * 14f
        val p3y = H + 14f
        val a = (1 - u) * (1 - u) * (1 - u)
        val b = 3 * (1 - u) * (1 - u) * u
        val c = 3 * (1 - u) * u * u
        val d = u * u * u
        return (a * p0x + b * p1x + c * p2x + d * p3x) to (a * p0y + b * p1y + c * p2y + d * p3y)
    }

    private fun maybeBomb(e: Enemy, dt: Float, chancePerSecond: Float) {
        e.shotCooldown -= dt
        if (e.shotCooldown > 0f || e.y > SHIP_Y - 20f || bombs.size >= 6) return
        if (rnd.nextFloat() < chancePerSecond * dt) {
            val vx = ((shipX - e.x) * 0.25f).coerceIn(-14f, 14f)
            bombs += Shot(e.x, e.y + 3f, vx, BOMB_SPEED + level * 3f)
            e.shotCooldown = 0.8f
        }
    }

    private fun moveShots(dt: Float) {
        shots.forEach { it.y += it.vy * dt }
        shots.removeAll { it.y < -5f }
        bombs.forEach { it.x += it.vx * dt; it.y += it.vy * dt }
        bombs.removeAll { it.y > H + 5f }
    }

    private fun collide() {
        val hitShots = HashSet<Shot>()
        val dead = HashSet<Enemy>()
        shots.forEach { s ->
            val e = enemies.firstOrNull { it !in dead && hypot(it.x - s.x, it.y - s.y) < ENEMY_R } ?: return@forEach
            hitShots += s
            e.hp--
            e.hitFlash = 0.15f
            if (e.hp <= 0) {
                dead += e
                // Збитий у піке — подвійні очки.
                addScore(if (e.state == State.DIVING) e.kind.points * 2 else e.kind.points)
                sparks += Spark(e.x, e.y)
            }
        }
        shots.removeAll(hitShots)
        enemies.removeAll(dead)
        if (invulnerable > 0f) return
        val bomb = bombs.firstOrNull { abs(it.x - shipX) < 4.5f && abs(it.y - SHIP_Y) < 5f }
        val rammer = enemies.firstOrNull { it.state == State.DIVING && hypot(it.x - shipX, it.y - SHIP_Y) < ENEMY_R + 3f }
        if (bomb != null || rammer != null) {
            if (rammer != null) {
                enemies.remove(rammer)
                sparks += Spark(rammer.x, rammer.y)
            }
            sparks += Spark(shipX, SHIP_Y)
            bombs.clear()
            lives--
            invulnerable = 2.5f
            if (lives <= 0) over = true
        }
    }

    private fun addScore(points: Int) {
        score += points
        if (score >= nextLife) {
            lives++
            nextLife += LIFE_EVERY
        }
    }

    /** Зберігаємо рахунок, рівень, життя й тих, хто лишився (вони знову влітають у стрій). */
    override fun encode(): String = GameWriter()
        .put("s", score).put("l", level).put("v", lives).put("o", if (over) 1 else 0).put("n", nextLife)
        .putList("e", enemies.flatMap { listOf(it.row, it.col, it.kind.ordinal, it.hp) })
        .toString()

    companion object {
        const val W = 100f
        const val H = 160f
        const val SHIP_Y = 148f
        const val COLS = 8
        const val ENEMY_R = 4.5f
        const val SHIP_SPEED = 120f
        const val SHOT_SPEED = 150f
        const val BOMB_SPEED = 48f
        const val FIRE_EVERY = 0.32f
        const val BANNER_TIME = 1.6f
        const val LIFE_EVERY = 5000

        fun decode(text: String?, rnd: Random = Random.Default): StarsGame? {
            val r = GameReader(text ?: return null)
            val level = r.int("l") ?: return null
            val packed = r.ints("e") ?: return null
            if (packed.size % 4 != 0) return null
            return StarsGame(rnd).apply {
                this.level = level.coerceAtLeast(1)
                score = r.int("s") ?: 0
                lives = (r.int("v") ?: 3).coerceAtLeast(1)
                over = r.int("o") == 1
                nextLife = r.int("n") ?: LIFE_EVERY
                val left = packed.chunked(4).mapNotNull { (row, col, kind, hp) ->
                    Kind.entries.getOrNull(kind)?.let { k -> Enemy(row, col, k).also { it.hp = hp.coerceIn(1, k.hp) } }
                }
                startLevel(left.takeIf { it.isNotEmpty() })
            }
        }
    }
}
