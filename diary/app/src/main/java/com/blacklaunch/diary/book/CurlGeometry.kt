package com.blacklaunch.diary.book

import kotlin.math.PI
import kotlin.math.sin

/**
 * Геометрія загинання сторінки — пласкі обчислення (без Compose-малювання), тож перевіряються тестами.
 *
 * Сторінка шириною [width] прикріплена корінцем зліва (x = 0). Перегортаючи, вона намотується на уявний вертикальний
 * циліндр радіуса [radius], що котиться від правого краю до корінця: [axis] — де циліндр торкається сторінки. Ліворуч
 * від осі аркуш лежить як лежав; далі він іде по валику вгору (лицем до нас, до вершини), перевалює і лягає назад
 * зворотом. Згин завжди вертикальний — сторінку не можна «зламати» навскіс.
 */
class CurlGeometry(val width: Float, val radius: Float, val axis: Float) {
    /** Де на екрані (по горизонталі) опиняється точка аркуша x. */
    fun map(x: Float): Float {
        val s = x - axis
        return when {
            s <= 0f -> x
            s <= PI.toFloat() * radius -> axis + radius * sin(s / radius)
            else -> axis - (s - PI.toFloat() * radius)
        }
    }

    /** Точка аркуша x видна лицем (а не зворотом). */
    fun front(x: Float): Boolean = x - axis <= PI.toFloat() * radius / 2f

    /** Найправіша точка аркуша на екрані — справжній край валика (лінія й тінь — саме тут, а не «де була б вершина»). */
    val rightmost: Float = run {
        val s = width - axis
        when {
            s <= 0f -> width
            s >= PI.toFloat() * radius / 2f -> axis + radius
            else -> axis + radius * sin(s / radius)
        }
    }

    companion object {
        /** Найбільший валик: сторінки тонкий, обкладинки — широкий (тверда обкладинка майже не гнеться). */
        fun radiusFor(width: Float, heavy: Boolean) = width * if (heavy) 0.3f else 0.12f

        /**
         * Стан за часткою перегорту [progress]: 0 — валик ще за правим краєм (сторінка рівна), 1 — валик повністю пішов
         * за корінець (сторінки вже не видно). Між ними рух видно весь — нічого не зникає раптом.
         */
        fun at(progress: Float, width: Float, heavy: Boolean): CurlGeometry {
            val rMax = radiusFor(width, heavy)
            val axis = width - progress.coerceIn(0f, 1f) * (width + rMax)
            // Спершу валик тонкий і росте разом із загнутою частиною: край аркуша одразу перекочується через вершину —
            // видно круглий валик, а не «стінку» під прямим кутом.
            val r = ((width - axis) / ROLL_TURN).coerceIn(0.5f, rMax)
            return CurlGeometry(width, r, axis)
        }

        /** Скільки радіан загнутий край, доки валик росте (≈ 140°: зворот уже видно, валик круглий). */
        private const val ROLL_TURN = 2.4f

        /** Уперед: палець узяв сторінку в [down] і тягне ліворуч, зараз у [now]. */
        fun forwardProgress(down: Float, now: Float, width: Float): Float =
            ((down - now) / (down + width * 0.15f)).coerceIn(0f, 1f)

        /** Назад: попередня сторінка вже за корінцем; палець тягне її праворуч — до правого краю вона лягає. */
        fun backwardProgress(down: Float, now: Float, width: Float): Float {
            val travel = (width - down).coerceAtLeast(width * 0.3f)
            return 1f - ((now - down) / travel).coerceIn(0f, 1f)
        }
    }
}
