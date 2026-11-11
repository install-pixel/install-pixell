package com.blacklaunch.diary.book

import androidx.compose.ui.graphics.drawscope.clipPath

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * «Аркуш»: фактура старого чорного паперу — як щоденник, що пожив: вугільний тон із теплим відтінком, плямистість
 * фарбування, волокна й цятки, кілька м'яких заломів і зморшок, витерті світліші краї. Обкладинка — щільний чорний
 * картон / шкіра. Генерується кодом один раз (без мережі й без картинок у програмі), однакова на всіх сторінках — при
 * перегортанні нічого не «стрибає». Поки рахується (частку секунди у фоні), папір малюється рівним тоном [PAGE_BASE].
 */
object PaperTexture {
    /** Тон паперу без фактури (у кольорах рахунку — без обробки). */
    val PAGE_BASE = androidx.compose.ui.graphics.Color(0xFF15130F)
    val COVER_BASE = androidx.compose.ui.graphics.Color(0xFF0C0B0A)

    var page by mutableStateOf<ImageBitmap?>(null)
        private set
    var cover by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Зерно для олівця: білі лінії й текст ледь «рвуться» на шорсткому папері (накладається множенням). */
    var grain by mutableStateOf<ImageBitmap?>(null)
        private set

    /**
     * Рельєф сторінки — заломи, зморшки й витерті краї — окремо від фарби паперу: тінь (множення) і світло (екран).
     * Накладається ПОВЕРХ усього, що на сторінці (лінії, текст, перо, фото), тож написане лежить на папері й темніє та
     * світлішає разом зі згинами, а не «плаває» зверху.
     */
    var shade by mutableStateOf<ImageBitmap?>(null)
        private set
    var light by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Складка (у частках сторінки) — для обкладинки; лінії сторінок ламаються за самим рельєфом ([reliefAt]). */
    class Crease(val x: Float, val y: Float, val dx: Float, val dy: Float, val len: Float, val width: Float, val amp: Float)

    @Volatile var creases: List<Crease> = emptyList()

    /**
     * Кілька варіантів природного рельєфу (вигнуті заломи з відгалуженнями, сітка дрібних зморшок, витерті краї). Кожна
     * сторінка бере свій варіант і ще віддзеркалює його за власним «зерном» ([Variant.of]) — сусідні сторінки різні, а та
     * сама сторінка завжди однакова.
     */
    private const val VARIANTS = 4
    private const val RW = 270
    private const val RH = 540
    private val reliefs = arrayOfNulls<FloatArray>(VARIANTS)
    private val shades = androidx.compose.runtime.mutableStateListOf<ImageBitmap?>().apply { repeat(VARIANTS) { add(null) } }
    private val lights = androidx.compose.runtime.mutableStateListOf<ImageBitmap?>().apply { repeat(VARIANTS) { add(null) } }
    // Зворот того самого аркуша: рельєф навпаки — де на лиці вм'ятина, там горбок (і навпаки).
    private val backShades = androidx.compose.runtime.mutableStateListOf<ImageBitmap?>().apply { repeat(VARIANTS) { add(null) } }
    private val backLights = androidx.compose.runtime.mutableStateListOf<ImageBitmap?>().apply { repeat(VARIANTS) { add(null) } }

    /** Який варіант і як віддзеркалений — за «зерном» сторінки. */
    class Variant(val index: Int, val flipX: Boolean, val flipY: Boolean) {
        companion object {
            fun of(seed: Int): Variant {
                val h = (seed * 2654435761L).toInt() ushr 3
                return Variant(Math.floorMod(h, VARIANTS), (h shr 4) and 1 == 1, (h shr 5) and 1 == 1)
            }
        }
    }

    fun shadeOf(v: Variant) = shades[v.index]
    fun lightOf(v: Variant) = lights[v.index]
    fun backShadeOf(v: Variant) = backShades[v.index]
    fun backLightOf(v: Variant) = backLights[v.index]

    /** Рельєф у точці (частки сторінки 0…1) з урахуванням віддзеркалення; ±кілька одиниць — для зламу ліній. */
    fun reliefAt(v: Variant, fx: Float, fy: Float): Float {
        val r = reliefs[v.index] ?: return 0f
        val x = ((if (v.flipX) 1f - fx else fx) * (RW - 1)).toInt().coerceIn(0, RW - 1)
        val y = ((if (v.flipY) 1f - fy else fy) * (RH - 1)).toInt().coerceIn(0, RH - 1)
        return r[y * RW + x]
    }

    @Volatile private var started = false

    fun ensure() {
        if (started) return
        started = true
        thread(name = "paper-texture", isDaemon = true) {
            // Фарба паперу — одна; рельєф — окремими варіантами (поверх усього, що на сторінці).
            page = sheet(W, H, seed = 11, base = intArrayOf(21, 19, 15), mottle = 7f, grainAmp = 3.5f, fibers = 2200, creases = 0, wrinkles = 0, wear = 0f, pores = 0f, relief = FloatArray(W * H)).asImageBitmap()
            for (i in 0 until VARIANTS) {
                val r = naturalRelief(RW, RH, seed = 101 + i * 37)
                reliefs[i] = r
                shades[i] = reliefMap(r, RW, RH, dark = true).asImageBitmap()
                lights[i] = reliefMap(r, RW, RH, dark = false).asImageBitmap()
                val inv = FloatArray(r.size) { -r[it] }
                backShades[i] = reliefMap(inv, RW, RH, dark = true).asImageBitmap()
                backLights[i] = reliefMap(inv, RW, RH, dark = false).asImageBitmap()
            }
            shade = shades[0]
            light = lights[0]
            cover = sheet(W, H, seed = 29, base = intArrayOf(12, 11, 10), mottle = 5f, grainAmp = 4f, fibers = 600, creases = 0, wrinkles = 0, wear = 18f, pores = 4f).asImageBitmap()
            grain = grainMap(W / 2, H / 2, seed = 5).asImageBitmap()
        }
    }

    private const val W = 540
    private const val H = 1080

    private fun sheet(
        w: Int, h: Int, seed: Int, base: IntArray, mottle: Float, grainAmp: Float,
        fibers: Int, creases: Int, wrinkles: Int, wear: Float, pores: Float,
        relief: FloatArray? = null,
    ): Bitmap {
        // Рельєф (заломи, витерті краї) — або в саму фарбу (обкладинка), або окремо ([relief]) — для накладання поверх.
        val r = relief ?: FloatArray(w * h)
        val found = ArrayList<Crease>()
        val rnd = java.util.Random(seed.toLong())
        val l = FloatArray(w * h)
        val n1 = ValueNoise(rnd, 170f)
        val n2 = ValueNoise(rnd, 60f)
        val n3 = ValueNoise(rnd, 18f)
        val n4 = ValueNoise(rnd, 3.5f)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            // Плямистість фарбування — великі м'які плями, і дрібніші в них.
            var v = (n1.at(x, y) * 0.6f + n2.at(x, y) * 0.3f + n3.at(x, y) * 0.1f - 0.5f) * 2f * mottle
            // Зерно паперу; у шкіри обкладинки — ще й пори.
            v += (rnd.nextFloat() - 0.5f) * 2f * grainAmp
            if (pores > 0f) v += (n4.at(x, y) - 0.5f) * 2f * pores
            // Витерті краї — світліші, нерівно.
            val e = minOf(minOf(x, w - 1 - x), minOf(y, h - 1 - y)).toFloat()
            val band = w * 0.05f
            if (e < band) {
                val k = 1f - e / band
                r[i] += wear * k * k * (0.55f + 0.9f * n3.at(x + 500, y))
            }
            l[i] = v
        }
        // Заломи (через весь аркуш) і зморшки (коротші): світло з одного боку складки, тінь — з другого.
        repeat(creases + wrinkles) { k ->
            val long = k < creases
            val ax = rnd.nextFloat() * w
            val ay = rnd.nextFloat() * h
            val ang = (rnd.nextFloat() - 0.5f) * 2.4f + if (rnd.nextBoolean()) 0f else 1.57f
            val len = if (long) (w + h).toFloat() else w * (0.12f + rnd.nextFloat() * 0.25f)
            val amp = if (long) 7f else 4f
            val width = if (long) 7f else 4f
            val dx = cos(ang)
            val dy = sin(ang)
            found += Crease(ax / w, ay / h, dx, dy, len / w, width / w, amp)
            for (y in 0 until h) for (x in 0 until w) {
                val px = x - ax
                val py = y - ay
                val t = px * dx + py * dy
                if (abs(t) > len / 2) continue
                val d = px * -dy + py * dx
                if (abs(d) > width * 3) continue
                val along = 1f - (abs(t) / (len / 2)).let { it * it }
                val s = d / width
                r[y * w + x] += amp * along * (s * exp(-s * s) * 2.3f) * (0.6f + 0.8f * n2.at(x + 900, y))
            }
        }
        // Волокна: короткі світлі вигнуті нитки.
        repeat(fibers) {
            var fx = rnd.nextFloat() * w
            var fy = rnd.nextFloat() * h
            var a = rnd.nextFloat() * 6.28f
            val len = 6 + rnd.nextInt(18)
            val b = 4f + rnd.nextFloat() * 6f
            for (s in 0 until len) {
                val ix = fx.toInt()
                val iy = fy.toInt()
                if (ix in 0 until w && iy in 0 until h) l[iy * w + ix] += b * (1f - s.toFloat() / len)
                a += (rnd.nextFloat() - 0.5f) * 0.5f
                fx += cos(a)
                fy += sin(a)
            }
        }
        // Цятки.
        repeat(w * h / 1500) {
            val ix = rnd.nextInt(w)
            val iy = rnd.nextInt(h)
            l[iy * w + ix] += 10f + rnd.nextFloat() * 10f
        }
        if (relief == null) for (i in l.indices) l[i] += r[i] else PaperTexture.creases = found
        val px = IntArray(w * h) { i ->
            val v = l[i]
            val rr = (base[0] + v).toInt().coerceIn(0, 255)
            val g = (base[1] + v * 0.94f).toInt().coerceIn(0, 255)
            val bl = (base[2] + v * 0.82f).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (rr shl 16) or (g shl 8) or bl
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Природний рельєф пожмаканого й розправленого паперу: 2–4 довгі вигнуті заломи (з відгалуженнями), сітка дрібних
     * зморшок і витерті краї. Залом — крива, що повільно змінює напрям; уздовж неї папір з одного боку світліший, з
     * другого — у тіні, а до кінців залом тане.
     */
    private fun naturalRelief(w: Int, h: Int, seed: Int): FloatArray {
        val rnd = java.util.Random(seed.toLong())
        val r = FloatArray(w * h)
        fun walk(x0: Float, y0: Float, a0: Float, len: Float, bend: Float): List<FloatArray> {
            val pts = ArrayList<FloatArray>()
            var x = x0
            var y = y0
            var a = a0
            // [bend] — найбільша кривизна: довгі заломи лише ледь вигинаються (не закручуються в кільце), дрібні зморшки — сильніше.
            var c = (rnd.nextFloat() - 0.5f) * bend
            val n = (len / 1.5f).toInt().coerceAtLeast(4)
            repeat(n) {
                pts += floatArrayOf(x, y)
                c += (rnd.nextFloat() - 0.5f) * bend * 0.3f
                c = c.coerceIn(-bend, bend)
                a += c + (rnd.nextFloat() - 0.5f) * bend * 2f
                x += cos(a) * 1.5f
                y += sin(a) * 1.5f
            }
            return pts
        }
        fun crease(x0: Float, y0: Float, a0: Float, len: Float, amp: Float, width: Float, branches: Int, bend: Float) {
            val pts = walk(x0, y0, a0, len, bend)
            stamp(r, w, h, pts, amp, width)
            repeat(branches) {
                val i = (pts.size * (0.2f + rnd.nextFloat() * 0.6f)).toInt().coerceIn(0, pts.size - 2)
                val at = pts[i]
                val nxt = pts[i + 1]
                val base = kotlin.math.atan2(nxt[1] - at[1], nxt[0] - at[0])
                val turn = (0.4f + rnd.nextFloat() * 0.7f) * if (rnd.nextBoolean()) 1f else -1f
                stamp(r, w, h, walk(at[0], at[1], base + turn, len * (0.15f + rnd.nextFloat() * 0.25f), bend * 2f), amp * 0.6f, width * 0.8f)
            }
        }
        // Довгі заломи — найчастіше від краю до краю.
        repeat(2 + rnd.nextInt(3)) {
            val fromLeft = rnd.nextBoolean()
            val y0 = rnd.nextFloat() * h
            val x0 = if (fromLeft) -10f else w + 10f
            val a = (if (fromLeft) 0f else Math.PI.toFloat()) + (rnd.nextFloat() - 0.5f) * 1.6f
            crease(x0, y0, a, (w + h) * (0.5f + rnd.nextFloat() * 0.5f), 6.5f, 3.2f, 1 + rnd.nextInt(3), bend = 0.0025f)
        }
        // Сітка дрібних зморшок.
        repeat(45 + rnd.nextInt(25)) {
            crease(rnd.nextFloat() * w, rnd.nextFloat() * h, rnd.nextFloat() * 6.28f, 8f + rnd.nextFloat() * 40f, 2.2f + rnd.nextFloat() * 1.5f, 1.4f, 0, bend = 0.02f)
        }
        // Витерті краї — світліші, нерівно.
        val band = w * 0.05f
        for (y in 0 until h) for (x in 0 until w) {
            val e = minOf(minOf(x, w - 1 - x), minOf(y, h - 1 - y)).toFloat()
            if (e < band) {
                val k = 1f - e / band
                r[y * w + x] += 13f * k * k * (0.7f + 0.3f * rnd.nextFloat())
            }
        }
        return r
    }

    /** Відбити один залом у рельєф: для кожного пікселя — відстань до найближчого відрізка кривої (зі знаком). */
    private fun stamp(r: FloatArray, w: Int, h: Int, pts: List<FloatArray>, amp: Float, width: Float) {
        if (pts.size < 2) return
        val reach = width * 3f
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        pts.forEach { minX = minOf(minX, it[0]); maxX = maxOf(maxX, it[0]); minY = minOf(minY, it[1]); maxY = maxOf(maxY, it[1]) }
        val bx0 = (minX - reach).toInt().coerceIn(0, w - 1)
        val by0 = (minY - reach).toInt().coerceIn(0, h - 1)
        val bx1 = (maxX + reach).toInt().coerceIn(0, w - 1)
        val by1 = (maxY + reach).toInt().coerceIn(0, h - 1)
        if (bx1 <= bx0 || by1 <= by0) return
        val bw = bx1 - bx0 + 1
        val bh = by1 - by0 + 1
        val dist = FloatArray(bw * bh) { Float.MAX_VALUE }
        val taper = FloatArray(bw * bh)
        val n = pts.size
        for (i in 0 until n - 1) {
            val p = pts[i]
            val q = pts[i + 1]
            val dx = q[0] - p[0]
            val dy = q[1] - p[1]
            val l = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val ux = dx / l
            val uy = dy / l
            val tp = sqrt(sin(Math.PI.toFloat() * i / (n - 1)))
            val x0 = (p[0] - reach).toInt().coerceAtLeast(bx0)
            val x1 = (p[0] + reach).toInt().coerceAtMost(bx1)
            val y0 = (p[1] - reach).toInt().coerceAtLeast(by0)
            val y1 = (p[1] + reach).toInt().coerceAtMost(by1)
            for (y in y0..y1) for (x in x0..x1) {
                val vx = x - p[0]
                val vy = y - p[1]
                val t = vx * ux + vy * uy
                if (t < -0.2f || t > l + 0.2f) continue
                val d = -vx * uy + vy * ux
                val k = (y - by0) * bw + (x - bx0)
                if (abs(d) < abs(dist[k])) { dist[k] = d; taper[k] = tp }
            }
        }
        for (k in dist.indices) {
            val d = dist[k]
            if (abs(d) > reach) continue
            val s = d / width
            val x = bx0 + k % bw
            val y = by0 + k / bw
            r[y * w + x] += amp * taper[k] * s * exp(-s * s) * 2.3f
        }
    }

    /**
     * Карта рельєфу: [dark] — тінь для множення (біле — без змін, темніше — тінь), інакше світло для «екрана» (чорне —
     * без змін). Сила розрахована так, щоб на темному папері дати ті самі ±кілька відтінків, що й раніше.
     */
    private fun reliefMap(relief: FloatArray, w: Int, h: Int, dark: Boolean): Bitmap {
        val px = IntArray(w * h) { i ->
            val v = relief[i]
            val c = if (dark) (255f * (1f + minOf(0f, v) / 20f)).toInt().coerceIn(0, 255)
            else (255f * (maxOf(0f, v) / 255f) * 1.15f).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (c shl 16) or (c shl 8) or c
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Карта зерна: біле з дрібними темнішими цятками (множення робить олівець «шорстким»). */
    private fun grainMap(w: Int, h: Int, seed: Int): Bitmap {
        val rnd = java.util.Random(seed.toLong())
        val n = ValueNoise(rnd, 2.2f)
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val v = (0.8f + 0.2f * sqrt(rnd.nextFloat()) * (0.6f + 0.4f * n.at(x, y))).coerceIn(0f, 1f)
            val c = (v * 255f).toInt()
            (0xFF shl 24) or (c shl 16) or (c shl 8) or c
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Плавний шум на гратці з кроком [cell] px (значення 0…1). */
    private class ValueNoise(rnd: java.util.Random, private val cell: Float) {
        private val size = 64
        private val grid = FloatArray(size * size) { rnd.nextFloat() }
        fun at(x: Int, y: Int): Float {
            val gx = x / cell
            val gy = y / cell
            val x0 = gx.toInt()
            val y0 = gy.toInt()
            val tx = smooth(gx - x0)
            val ty = smooth(gy - y0)
            fun g(i: Int, j: Int) = grid[(Math.floorMod(j, size)) * size + Math.floorMod(i, size)]
            val a = g(x0, y0) + (g(x0 + 1, y0) - g(x0, y0)) * tx
            val b = g(x0, y0 + 1) + (g(x0 + 1, y0 + 1) - g(x0, y0 + 1)) * tx
            return a + (b - a) * ty
        }
        private fun smooth(t: Float) = t * t * (3f - 2f * t)
    }
}

/**
 * «Аркуш»: накласти на вміст сторінки рельєф паперу (заломи, витерті краї) і зерно — усе написане лягає на папір.
 * У «Склі» нічого не робить.
 */
fun androidx.compose.ui.Modifier.paperRelief(glass: Boolean, seed: Int = 0, holes: ReliefHoles? = null): androidx.compose.ui.Modifier =
    if (glass) this else this.drawWithContent {
        drawContent()
        val dst = androidx.compose.ui.unit.IntSize(size.width.toInt() + 1, size.height.toInt() + 1)
        val v = PaperTexture.Variant.of(seed)
        val cut = holes?.rects?.values?.toList().orEmpty()
        val paint: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
            withTransform({ scale(if (v.flipX) -1f else 1f, if (v.flipY) -1f else 1f, pivot = center) }) {
                PaperTexture.shadeOf(v)?.let { drawImage(it, dstSize = dst, blendMode = androidx.compose.ui.graphics.BlendMode.Multiply) }
                PaperTexture.lightOf(v)?.let { drawImage(it, dstSize = dst, blendMode = androidx.compose.ui.graphics.BlendMode.Screen) }
            }
            PaperTexture.grain?.let { drawImage(it, dstSize = dst, alpha = 0.75f, blendMode = androidx.compose.ui.graphics.BlendMode.Multiply) }
        }
        if (cut.isEmpty()) paint() else {
            // Речі, що не з паперу (касета), — без заломів і зерна.
            val path = androidx.compose.ui.graphics.Path().apply {
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                cut.forEach { addRoundRect(it) }
            }
            clipPath(path) { paint() }
        }
    }

/** Місця на сторінці, де рельєфу паперу не треба (касета — вона пластикова). */
class ReliefHoles {
    val rects = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.RoundRect>()
    var page: androidx.compose.ui.layout.LayoutCoordinates? = null
}

val LocalReliefHoles = androidx.compose.runtime.staticCompositionLocalOf<ReliefHoles?> { null }
