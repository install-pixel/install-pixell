package com.blacklaunch.diary.book

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Геометрія перегортання — пласкі обчислення (без Compose-малювання), тож перевіряються тестами.
 *
 * Сторінка W×H прикріплена корінцем зліва (x = 0). Палець узяв її в точці [grip] і тягне: точка під пальцем — [corner].
 * Лінія згину — серединний перпендикуляр відрізка grip→corner; усе, що з боку grip, перегортається (дзеркально
 * лягає по той бік згину). Так точка, яку взяв палець, завжди лишається під пальцем — як справжній аркуш.
 */
class FlipGeometry(val width: Float, val height: Float, val grip: Offset, val corner: Offset) {
    /** Середина відрізка — точка на лінії згину. */
    val mid: Offset = (grip + corner) / 2f

    /** Одиничний вектор від згину в бік того, що перегортається. */
    val normal: Offset = run {
        val d = grip - corner
        val len = hypot(d.x, d.y)
        if (len < 0.001f) Offset(1f, 0f) else Offset(d.x / len, d.y / len)
    }

    /** Кут лінії згину (градуси) — для дзеркального відображення. */
    val foldAngle: Float = (Math.toDegrees(atan2(normal.y.toDouble(), normal.x.toDouble())) + 90.0).toFloat()

    /** Наскільки перегорнуто: 0 — лежить, 1 — повністю перекинута на корінець. */
    val progress: Float = if (grip.x <= 0f) 0f else ((grip.x - corner.x) / (2f * grip.x)).coerceIn(0f, 1f)

    /** Відстань від згину (додатна — частина, що перегортається). */
    fun side(p: Offset): Float = (p.x - mid.x) * normal.x + (p.y - mid.y) * normal.y

    /** Дзеркало точки відносно лінії згину. */
    fun reflect(p: Offset): Offset {
        val d = side(p)
        return p - normal * (2f * d)
    }

    private val rect = listOf(Offset(0f, 0f), Offset(width, 0f), Offset(width, height), Offset(0f, height))

    /** Те, що лишається лежати (з боку корінця). */
    val stay: List<Offset> = clip(rect, keepPositive = false)

    /** Те, що піднялося (ще на своєму місці) — під ним видно наступну сторінку. */
    val lifted: List<Offset> = clip(rect, keepPositive = true)

    /** Перегорнутий клапоть — уже дзеркально по той бік згину (його зворот). */
    val flap: List<Offset> = lifted.map { reflect(it) }

    /** Відрізати від багатокутника півплощину (Сазерленд — Ходжмен для однієї прямої). */
    private fun clip(poly: List<Offset>, keepPositive: Boolean): List<Offset> {
        if (poly.isEmpty()) return poly
        val out = ArrayList<Offset>(poly.size + 2)
        fun inside(p: Offset) = if (keepPositive) side(p) > 0f else side(p) <= 0f
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val ia = inside(a)
            val ib = inside(b)
            if (ia) out += a
            if (ia != ib) {
                val da = side(a)
                val db = side(b)
                val t = da / (da - db)
                out += a + (b - a) * t
            }
        }
        return out
    }

    companion object {
        /**
         * Куди тягнеться клапоть за пальцем. [down] — де палець торкнувся, [now] — де він зараз; вертикальний рух
         * пом'якшено (згин лише трохи нахиляється, як у справжньої сторінки).
         */
        fun forwardCorner(grip: Offset, down: Offset, now: Offset, height: Float): Offset {
            val x = (grip.x + (now.x - down.x)).coerceIn(-grip.x, grip.x)
            val y = grip.y + ((now.y - down.y) * 0.38f).coerceIn(-height * 0.16f, height * 0.16f)
            return Offset(x, y)
        }

        /**
         * Назад: попередня сторінка вже лежить перекинута (клапоть за корінцем). Палець ліворуч тягне її назад; коли
         * він доходить до правого краю — сторінка лягає.
         */
        fun backwardCorner(grip: Offset, down: Offset, now: Offset, width: Float, height: Float): Offset {
            val travel = (width - down.x).coerceAtLeast(width * 0.3f)
            val k = ((now.x - down.x) / travel).coerceIn(0f, 1f)
            val x = -grip.x + 2f * grip.x * k
            val y = grip.y + ((now.y - down.y) * 0.38f).coerceIn(-height * 0.16f, height * 0.16f)
            return Offset(x, y)
        }
    }
}
