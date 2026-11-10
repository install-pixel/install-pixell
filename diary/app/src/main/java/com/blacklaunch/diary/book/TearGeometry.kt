package com.blacklaunch.diary.book

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Геометрія виривання — пласкі обчислення (перевіряються тестами).
 *
 * Сторінку W×H тримає корінець зліва. Палець узяв нижній лівий кут C = (0, H) і тягне його в [finger]. Лінія загину —
 * серединний перпендикуляр відрізка C→finger: усе з боку кута загинається (дзеркально лягає по той бік лінії, видно
 * зворот), решта лежить як лежала — верх сторінки не зсувається. Корінець рветься рівно там, де лінія загину його
 * перетинає: від низу до [tearHeight].
 */
class TearGeometry(val width: Float, val height: Float, val finger: Offset) {
    private val corner = Offset(0f, height)

    /** Середина відрізка — точка на лінії загину. */
    val mid: Offset = (corner + finger) / 2f

    /** Одиничний вектор від лінії загину в бік кута (того, що загинається). */
    val normal: Offset = run {
        val d = corner - finger
        val len = hypot(d.x, d.y)
        if (len < 0.001f) Offset(-0.7071f, 0.7071f) else Offset(d.x / len, d.y / len)
    }

    /** Відстань від лінії загину (додатна — частина, що загинається). */
    fun side(p: Offset): Float = (p.x - mid.x) * normal.x + (p.y - mid.y) * normal.y

    /** Дзеркало точки відносно лінії загину. */
    fun reflect(p: Offset): Offset = p - normal * (2f * side(p))

    /** До якої висоти (0…1) розірвано корінець: де лінія загину перетинає лівий край. */
    val tearHeight: Float = run {
        if (abs(normal.y) < 1e-4f) return@run if (side(Offset(0f, 0f)) > 0f) 1f else 0f
        // (0 - mid.x) * nx + (y - mid.y) * ny = 0
        val y = mid.y + mid.x * normal.x / normal.y
        ((height - y) / height).coerceIn(0f, 1f)
    }

    private val rect = listOf(Offset(0f, 0f), Offset(width, 0f), Offset(width, height), Offset(0f, height))

    /** Що лежить як лежало (з боку, протилежного куту). */
    val stay: List<Offset> = clip(rect, keepCorner = false)

    /** Що піднялося (на своєму місці) — під ним видно наступну сторінку. */
    val lifted: List<Offset> = clip(rect, keepCorner = true)

    /** Загнутий клапоть — уже дзеркально по той бік лінії (його зворот). */
    val flap: List<Offset> = lifted.map { reflect(it) }

    /** Розірвана частина корінця на клапті: (0, y) для y від розриву до низу — дзеркально. */
    fun tornEdge(steps: Int): List<Offset> {
        val top = height * (1f - tearHeight)
        return List(steps + 1) { i -> reflect(Offset(0f, top + (height - top) * i / steps)) }
    }

    private fun clip(poly: List<Offset>, keepCorner: Boolean): List<Offset> {
        val out = ArrayList<Offset>(poly.size + 2)
        fun inside(p: Offset) = if (keepCorner) side(p) > 0f else side(p) <= 0f
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val ia = inside(a)
            val ib = inside(b)
            if (ia) out += a
            if (ia != ib) {
                val da = side(a)
                val db = side(b)
                out += a + (b - a) * (da / (da - db))
            }
        }
        return out
    }
}
