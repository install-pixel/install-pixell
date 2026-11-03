package com.blacklaunch.qrcodes.label

import kotlin.math.hypot

/**
 * «Живе перо» для малювання пальцем:
 *  - згладжування — точка лінії йде трохи позаду пальця, тож дрібне тремтіння не видно;
 *  - товщина від швидкості — повільно товще, швидко тонше (як чорнило; натиск більшість екранів не міряє);
 *  - звуження — штрих починається тонким кінчиком, розходиться й наприкінці знову сходить нанівець.
 */
object Ink {
    /** Наскільки сильно згладжувати (частка, на яку точка лишається позаду пальця; 0 — без згладжування). */
    const val SMOOTH = 0.6f

    /** Як швидко товщина встигає за зміною швидкості (менше — плавніше). */
    const val FACTOR_EASE = 0.18f

    /** Множник товщини: найповільніше — [SLOW_FACTOR], найшвидше — [FAST_FACTOR]. */
    const val SLOW_FACTOR = 1.15f
    const val FAST_FACTOR = 0.5f

    /** Швидкість (мм/мс), за якої лінія вже найтонша. */
    const val FAST = 0.8f

    /** Товщина кінчика — частка повної. */
    const val TIP = 0.22f

    /** Довжина звуження — у товщинах лінії (на початку і в кінці). */
    const val START_TAPER = 5f
    const val END_TAPER = 4f

    /** Відстань між збереженими точками, мм (густо — щоб гумка стирала рівно). */
    const val STEP_MM = 0.4f

    fun targetFactor(speedMmPerMs: Float): Float {
        val t = (speedMmPerMs / FAST).coerceIn(0f, 1f)
        return SLOW_FACTOR + (FAST_FACTOR - SLOW_FACTOR) * t
    }

    /**
     * Товщина в кожній точці штриха: [base] × множник швидкості × звуження на кінцях. [xs] / [ys] і [base] — в
     * однакових одиницях; звуження не довше ніж 40 % штриха (короткий штрих — тонка рисочка, а не крапля).
     */
    fun widths(xs: FloatArray, ys: FloatArray, factors: List<Float>?, base: Float): FloatArray {
        val n = xs.size
        val dist = FloatArray(n)
        for (i in 1 until n) dist[i] = dist[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        val total = if (n == 0) 0f else dist[n - 1]
        val start = minOf(base * START_TAPER, total * 0.4f)
        val end = minOf(base * END_TAPER, total * 0.4f)
        return FloatArray(n) { i ->
            val f = factors?.getOrNull(i) ?: 1f
            val a = if (start > 0f) ease(dist[i] / start) else 1f
            val b = if (end > 0f) ease((total - dist[i]) / end) else 1f
            base * f * (TIP + (1f - TIP) * minOf(a, b))
        }
    }

    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}

/** Штрих, поки малюєш: точки в мм наклейки і множник товщини в кожній (порожньо — рівна лінія, старі малюнки). */
data class InkStroke(val points: List<androidx.compose.ui.geometry.Offset>, val factors: List<Float> = emptyList())
