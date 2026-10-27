package com.blacklaunch.launcher.games

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * «Зоряний патруль» — як класичні аркадні «космічні рої»: внизу кораблик (пальцем вліво-вправо, стріляє сам),
 * угорі — стрій прибульців, що погойдується; час від часу хтось пікірує на кораблик дугою й скидає бомби.
 * - Знищили всіх — новий рівень: більше рядів, частіше й швидше пікірують (до певної межі — далі рівні тривають).
 * - Кожен 4-й рівень — бонусний: прибульці пролітають візерунками й не стріляють; 100 за кожного, усіх — +10000.
 * - Зі збитих іноді падає капсула: подвійна чи потрійна гармата, швидкострільність, щит (тримає один удар), життя.
 * - Збили кораблик — він розлітається на уламки, мить паузи, потім з'являється знову (блимає й невразливий).
 * Три життя, кожні 5000 очок — ще одне. Поле — умовні одиниці [W]×[H]; екран масштабує.
 */
internal class StarsGame(private val rnd: Random = Random.Default) : ArcadeGame {
    enum class Kind(val hp: Int, val points: Int) { DRONE(1, 50), GUARD(2, 150) }
    enum class State { ENTERING, FORMATION, DIVING, FLYBY }
    enum class Power { DOUBLE, TRIPLE, RAPID, SHIELD, LIFE }

    class Capsule(var x: Float, var y: Float, val power: Power)

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
        /** Бонусний рівень: номер візерунка прольоту. */
        var pattern = 0
    }

    class Shot(var x: Float, var y: Float, val vx: Float = 0f, val vy: Float)
    class Spark(val x: Float, val y: Float, var t: Float = 0f, val big: Boolean = false)

    val enemies = ArrayList<Enemy>()
    val shots = ArrayList<Shot>()
    val bombs = ArrayList<Shot>()
    val sparks = ArrayList<Spark>()
    val capsules = ArrayList<Capsule>()

    /** Гармата: звичайна, подвійна чи потрійна (віялом) — і скільки ще. */
    var gun: Power? = null
        private set
    var gunLeft = 0f
        private set
    var rapidLeft = 0f
        private set
    var shield = false
        private set

    /** Кораблик збито: скільки ще до появи нового (уламки розлітаються). */
    var shipDown = 0f
        private set

    /** Бонусний рівень: скільки влучили з усіх і результат наприкінці. */
    val challenge get() = level % CHALLENGE_EVERY == 0
    var challengeHits = 0
        private set
    var challengeTotal = 0
        private set
    /** Показати «Влучань N з M» (і бонус) ще стільки секунд. */
    var challengeResult = 0f
        private set
    private var flybyTimer = 0f
    private var flybyGroups = 0

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
    private val buzz = BuzzQueue()

    override fun drainBuzz() = buzz.drain()
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
        capsules.clear()
        banner = BANNER_TIME
        diveTimer = 3f
        if (challenge && keep == null) {
            challengeHits = 0
            challengeTotal = FLYBY_GROUPS * FLYBY_SIZE
            flybyGroups = 0
            flybyTimer = BANNER_TIME
            return
        }
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

    // Легкий початок і плавне ускладнення до межі — далі рівні тривають, але не стають неможливими.
    private val diveSpeed get() = (0.32f + level * 0.03f).coerceAtMost(0.75f)
    private val diveEvery get() = (4.2f - level * 0.22f).coerceAtLeast(1f)

    override fun step(dt: Float) {
        if (over) return
        time += dt
        if (invulnerable > 0f) invulnerable -= dt
        if (banner > 0f) banner -= dt
        sparks.forEach { it.t += dt }
        sparks.removeAll { it.t > (if (it.big) 1.4f else 0.6f) }
        if (gunLeft > 0f) gunLeft -= dt else gun = null
        if (rapidLeft > 0f) rapidLeft -= dt
        if (challengeResult > 0f) challengeResult -= dt
        if (shipDown > 0f) {
            shipDown -= dt
            if (shipDown <= 0f) invulnerable = 2f
        } else {
            moveShip(dt)
            fire(dt)
        }
        if (challenge) moveFlyby(dt) else moveEnemies(dt)
        moveShots(dt)
        moveCapsules(dt)
        collide()
        val waveDone = if (challenge) flybyGroups >= FLYBY_GROUPS && enemies.isEmpty() else enemies.isEmpty()
        if (waveDone && !over && shipDown <= 0f) {
            if (challenge) {
                if (challengeHits == challengeTotal) addScore(PERFECT_BONUS)
                challengeResult = 3f
            }
            level++
            startLevel()
        }
    }

    /** Бонусний рівень: групи пролітають візерунками (не стріляють), що не збили — відлітають. */
    private fun moveFlyby(dt: Float) {
        if (flybyGroups < FLYBY_GROUPS) {
            flybyTimer -= dt
            if (flybyTimer <= 0f) {
                val pattern = flybyGroups % 3
                for (i in 0 until FLYBY_SIZE) {
                    enemies += Enemy(0, i, if (i % 4 == 0) Kind.GUARD else Kind.DRONE).also {
                        it.state = State.FLYBY
                        it.pattern = pattern
                        it.hp = 1
                        it.delay = i * 0.22f
                        it.t = 0f
                        it.x = -20f
                        it.y = -20f
                    }
                }
                flybyGroups++
                flybyTimer = 3.4f
            }
        }
        enemies.forEach { e ->
            if (e.hitFlash > 0f) e.hitFlash -= dt
            if (e.delay > 0f) {
                e.delay -= dt
                return@forEach
            }
            e.t += dt / 3.2f
            val (x, y) = flybyPoint(e.pattern, e.t)
            e.x = x
            e.y = y
        }
        enemies.removeAll { it.state == State.FLYBY && it.t >= 1f }
    }

    /** Три візерунки прольоту: хвиля зліва направо, петля згори, дуга справа наліво. */
    fun flybyPoint(pattern: Int, t: Float): Pair<Float, Float> {
        val u = t.coerceIn(0f, 1f)
        val pi = Math.PI.toFloat()
        return when (pattern) {
            0 -> (-10f + u * (W + 20f)) to (30f + sin(u * pi * 3f) * 18f)
            1 -> (W / 2 + sin(u * pi * 2f) * 34f) to (-10f + sin(u * pi) * 95f)
            else -> (W + 10f - u * (W + 20f)) to (70f - sin(u * pi) * 45f + sin(u * pi * 4f) * 6f)
        }
    }

    private fun moveCapsules(dt: Float) {
        capsules.forEach { it.y += CAPSULE_SPEED * dt }
        capsules.removeAll { it.y > H + 5f }
        if (shipDown > 0f) return
        val caught = capsules.filter { abs(it.x - shipX) < 6f && abs(it.y - SHIP_Y) < 6f }
        caught.forEach { take(it.power) }
        capsules.removeAll(caught)
    }

    private fun take(power: Power) {
        when (power) {
            Power.DOUBLE, Power.TRIPLE -> {
                gun = power
                gunLeft = if (power == Power.TRIPLE) 12f else 15f
            }
            Power.RAPID -> rapidLeft = 12f
            Power.SHIELD -> shield = true
            Power.LIFE -> lives = (lives + 1).coerceAtMost(6)
        }
        buzz.add(GameBuzz.MEDIUM)
        sparks += Spark(shipX, SHIP_Y - 4f)
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
        val limit = when (gun) { Power.TRIPLE -> 9; Power.DOUBLE -> 6; else -> 3 } * (if (rapidLeft > 0f) 2 else 1)
        if (banner > 0.6f || fireCooldown > 0f || shots.size >= limit) return
        when (gun) {
            Power.DOUBLE -> {
                shots += Shot(shipX - 3f, SHIP_Y - 5f, vy = -SHOT_SPEED)
                shots += Shot(shipX + 3f, SHIP_Y - 5f, vy = -SHOT_SPEED)
            }
            Power.TRIPLE -> {
                shots += Shot(shipX, SHIP_Y - 6f, vy = -SHOT_SPEED)
                shots += Shot(shipX - 2f, SHIP_Y - 5f, vx = -SHOT_SPEED * 0.22f, vy = -SHOT_SPEED * 0.97f)
                shots += Shot(shipX + 2f, SHIP_Y - 5f, vx = SHOT_SPEED * 0.22f, vy = -SHOT_SPEED * 0.97f)
            }
            else -> shots += Shot(shipX, SHIP_Y - 6f, vy = -SHOT_SPEED)
        }
        fireCooldown = if (rapidLeft > 0f) FIRE_EVERY / 2f else FIRE_EVERY
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
                    // З третього рівня й у строю зрідка стріляють.
                    if (level >= 3) maybeBomb(e, dt, chancePerSecond = (0.006f * level).coerceAtMost(0.08f))
                }
                // Проліт буває лише на бонусному рівні (там рухає [moveFlyby]).
                State.FLYBY -> Unit
                State.DIVING -> {
                    e.t += dt * diveSpeed
                    val (x, y) = divePoint(e, e.t)
                    e.x = x
                    e.y = y
                    maybeBomb(e, dt, chancePerSecond = (0.45f + level * 0.05f).coerceAtMost(1.2f))
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
        if (banner > 0f || shipDown > 0f) return
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
            bombs += Shot(e.x, e.y + 3f, vx, (BOMB_SPEED + level * 2f).coerceAtMost(70f))
            e.shotCooldown = 0.8f
        }
    }

    private fun moveShots(dt: Float) {
        shots.forEach { it.x += it.vx * dt; it.y += it.vy * dt }
        shots.removeAll { it.y < -5f || it.x < -5f || it.x > W + 5f }
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
                buzz.add(GameBuzz.LIGHT)
                sparks += Spark(e.x, e.y)
                if (e.state == State.FLYBY) {
                    challengeHits++
                    addScore(100)
                    return@forEach
                }
                // Збитий у піке — подвійні очки.
                addScore(if (e.state == State.DIVING) e.kind.points * 2 else e.kind.points)
                dropCapsule(e)
            }
        }
        shots.removeAll(hitShots)
        enemies.removeAll(dead)
        if (invulnerable > 0f || shipDown > 0f) return
        val bomb = bombs.firstOrNull { abs(it.x - shipX) < 3.5f && abs(it.y - SHIP_Y) < 4f }
        val rammer = enemies.firstOrNull { it.state == State.DIVING && hypot(it.x - shipX, it.y - SHIP_Y) < ENEMY_R + 2.5f }
        if (bomb != null || rammer != null) {
            if (rammer != null) {
                enemies.remove(rammer)
                sparks += Spark(rammer.x, rammer.y)
            }
            bomb?.let { bombs.remove(it) }
            // Щит тримає один удар.
            if (shield) {
                buzz.add(GameBuzz.MEDIUM)
                shield = false
                invulnerable = 1.2f
                sparks += Spark(shipX, SHIP_Y)
                return
            }
            sparks += Spark(shipX, SHIP_Y, big = true)
            buzz.add(GameBuzz.STRONG)
            bombs.clear()
            capsules.clear()
            gun = null
            rapidLeft = 0f
            lives--
            if (lives <= 0) {
                over = true
                return
            }
            // Ті, хто пікірував, вертаються в стрій, поки з'являється новий кораблик.
            enemies.filter { it.state == State.DIVING }.forEach { it.t = 1f }
            shipDown = SHIP_DOWN_TIME
            shipX = W / 2
            target = null
        }
    }

    private fun dropCapsule(e: Enemy) {
        val chance = if (e.kind == Kind.GUARD) 0.25f else 0.09f
        if (rnd.nextFloat() >= chance || capsules.size >= 2) return
        val power = when (rnd.nextFloat()) {
            in 0f..0.3f -> Power.DOUBLE
            in 0.3f..0.5f -> Power.TRIPLE
            in 0.5f..0.72f -> Power.RAPID
            in 0.72f..0.94f -> Power.SHIELD
            else -> Power.LIFE
        }
        capsules += Capsule(e.x, e.y, power)
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
        const val BOMB_SPEED = 40f
        const val FIRE_EVERY = 0.32f
        const val BANNER_TIME = 1.6f
        const val LIFE_EVERY = 5000
        const val CHALLENGE_EVERY = 4
        const val FLYBY_GROUPS = 5
        const val FLYBY_SIZE = 8
        const val PERFECT_BONUS = 10000
        const val CAPSULE_SPEED = 26f
        const val SHIP_DOWN_TIME = 1.6f

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
                // Бонусний рівень починається заново (візерунки прольоту не зберігаємо).
                startLevel(left.takeIf { it.isNotEmpty() && !challenge })
            }
        }
    }
}
