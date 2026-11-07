package com.blacklaunch.paleta.pack

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Висота самої палети (дерево), мм. Входить у ліміт висоти. */
const val PALLET_HEIGHT_MM = 150

/** Ліміт висоти для товару ADR (небезпечного) — лише 1,20 м разом з палетою. */
const val ADR_HEIGHT_MM = 1200

/** Що обмежило кількість шарів. */
enum class Limit { Height, Weight }

/**
 * Результат: скільки коробок на палеті, шарів, висота й вага.
 * [partial] — не набирається навіть одного повного шару (вага): «структура орієнтовна».
 */
data class Stack(
    val perLayer: Int,
    val layers: Int,
    val boxes: Int,
    val partial: Boolean,
    val heightMm: Int,
    val weightG: Long,
    val limit: Limit,
    /** Скільки шарів дала б висота (щоб показати «зменшено з N до M»). */
    val layersByHeight: Int,
    /** Коробок у парних шарах (2, 4…), якщо другий шар має свою розкладку; інакше = [perLayer]. */
    val perLayer2: Int = perLayer,
)

object Stacking {
    /**
     * Шари чергуються: непарні — [perA] коробок, парні — [perB] (свій другий шар). Решта — як у [stack]: шарів за
     * висотою, потім, якщо вага не пускає, — менше шарів (лише повні); навіть один не набирається — неповний.
     */
    fun stackAlternating(perA: Int, perB: Int, boxHmm: Int, boxG: Long, heightLimitMm: Int, weightLimitG: Long, palletG: Long): Stack? {
        if (perB == perA) return stack(perA, boxHmm, boxG, heightLimitMm, weightLimitG, palletG)
        if (perA <= 0 || perB <= 0 || boxHmm <= 0 || boxG <= 0) return null
        val byHeight = ((heightLimitMm - PALLET_HEIGHT_MM).coerceAtLeast(0)) / boxHmm
        val byWeight = (weightLimitG - palletG).coerceAtLeast(0) / boxG
        fun boxesIn(k: Int): Long = ((k + 1) / 2).toLong() * perA + (k / 2).toLong() * perB
        var layers = byHeight
        while (layers > 0 && boxesIn(layers) > byWeight) layers--
        val limit = if (layers < byHeight) Limit.Weight else Limit.Height
        if (layers == 0) {
            val n = minOf(byWeight, perA.toLong()).toInt()
            // Неповний шар: на шарі — стільки, скільки пускає вага (не «скільки влазить»), шарів — 1.
            return Stack(n, if (n > 0) 1 else 0, n, n > 0 && byHeight > 0, if (n > 0) boxHmm + PALLET_HEIGHT_MM else PALLET_HEIGHT_MM, n * boxG + palletG, limit, byHeight, n)
        }
        val n = boxesIn(layers)
        return Stack(perA, layers, n.toInt(), false, layers * boxHmm + PALLET_HEIGHT_MM, n * boxG + palletG, limit, byHeight, perB)
    }

    /**
     * [perLayer] коробок у шарі, коробка [boxHmm] заввишки й [boxG] грамів; ліміт висоти [heightLimitMm] (разом з
     * палетою), ліміт ваги [weightLimitG]; [palletG] — вага палети (0 — не враховується).
     */
    fun stack(perLayer: Int, boxHmm: Int, boxG: Long, heightLimitMm: Int, weightLimitG: Long, palletG: Long): Stack? {
        if (perLayer <= 0 || boxHmm <= 0 || boxG <= 0) return null
        val byHeight = ((heightLimitMm - PALLET_HEIGHT_MM).coerceAtLeast(0)) / boxHmm
        val budget = (weightLimitG - palletG).coerceAtLeast(0)
        val byWeight = budget / boxG
        val full = perLayer.toLong() * byHeight
        return if (full <= byWeight) {
            Stack(perLayer, byHeight, full.toInt(), false, byHeight * boxHmm + PALLET_HEIGHT_MM, full * boxG + palletG, Limit.Height, byHeight)
        } else {
            val layers = (byWeight / perLayer).toInt()
            if (layers == 0) {
                // Навіть шар не набирається — скільки коробок дозволяє вага (неповний шар).
                val n = byWeight.toInt()
                // Неповний шар: на шарі — стільки, скільки пускає вага (не «скільки влазить»), шарів — 1.
                Stack(n, if (n > 0) 1 else 0, n, n > 0, if (n > 0) boxHmm + PALLET_HEIGHT_MM else PALLET_HEIGHT_MM, n * boxG + palletG, Limit.Weight, byHeight)
            } else {
                val n = layers * perLayer
                Stack(perLayer, layers, n, false, layers * boxHmm + PALLET_HEIGHT_MM, n.toLong() * boxG + palletG, Limit.Weight, byHeight)
            }
        }
    }
}

/** Числа з поля: кома чи крапка; довжина — метри або сантиметри. */
object Num {
    /** Звичайне число (кома чи крапка), null — порожньо чи не число. */
    fun parse(text: String): Double? {
        val t = text.trim().replace(',', '.').replace(" ", "")
        if (t.isEmpty()) return null
        return t.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    }

    /**
     * Довжина в мм. Пишуть як у бланку — у метрах («0,47»); якщо число від 3 і більше — це сантиметри («47»):
     * коробок по 3 метри на палеті не буває.
     */
    fun lengthMm(text: String): Int? {
        val v = parse(text) ?: return null
        val metres = if (v >= 3.0) v / 100.0 else v
        val mm = (metres * 1000).roundToInt()
        return mm.takeIf { it > 0 }
    }

    /** Чи число в полі зрозуміли як сантиметри (для підказки «= 0,47 м»). */
    fun isCentimetres(text: String): Boolean = (parse(text) ?: 0.0) >= 3.0

    /** Вага в грамах (поле — у кілограмах, точність до 1 г). */
    fun grams(text: String): Long? {
        val v = parse(text) ?: return null
        val g = (v * 1000).roundToLong()
        return g.takeIf { it > 0 }
    }

    /** Ціле число > 0. */
    fun count(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it > 0 }

    /** мм → «0,47» (метри, два знаки, як у бланку; третій — лише якщо є). */
    fun metres(mm: Int, comma: Boolean = true): String {
        val s = if (mm % 10 == 0) String.format(java.util.Locale.ROOT, "%.2f", mm / 1000.0) else String.format(java.util.Locale.ROOT, "%.3f", mm / 1000.0)
        return if (comma) s.replace('.', ',') else s
    }

    /** г → «2,30» (кг, два знаки; до грама — три, якщо є). */
    fun kilos(g: Long, comma: Boolean = true): String {
        val s = if (g % 10 == 0L) String.format(java.util.Locale.ROOT, "%.2f", g / 1000.0) else String.format(java.util.Locale.ROOT, "%.3f", g / 1000.0)
        return if (comma) s.replace('.', ',') else s
    }

    /** мм → «2,5» (сантиметри, без зайвих нулів). */
    fun cm(mm: Int, comma: Boolean = true): String {
        val s = if (mm % 10 == 0) (mm / 10).toString() else String.format(java.util.Locale.ROOT, "%.1f", mm / 10.0)
        return if (comma) s.replace('.', ',') else s
    }
}

/** Скільки штук влазить у коробку (рядами, будь-яким боком — береться найкращий з 6 способів). */
object Fit {
    fun units(box: Triple<Int, Int, Int>, unit: Triple<Int, Int, Int>): Int {
        val (a, b, c) = unit
        if (a <= 0 || b <= 0 || c <= 0) return 0
        val perms = listOf(Triple(a, b, c), Triple(a, c, b), Triple(b, a, c), Triple(b, c, a), Triple(c, a, b), Triple(c, b, a))
        return perms.maxOf { (x, y, z) -> (box.first / x) * (box.second / y) * (box.third / z) }
    }
}

/**
 * Коробка на всю палету (1200 × 800), висоти якої немає: висота — від ліміту висоти (мінус палета) і ваги.
 * Перебираються 6 способів покласти штуку; береться найбільше штук, які влазять по висоті й не перевищують [maxUnits]
 * (вага); за рівності — нижча коробка. Висота — стільки рядів штук, скільки треба для цієї кількості.
 */
data class PalletBoxFit(val units: Int, val heightMm: Int)

object PalletBox {
    fun fit(unit: Triple<Int, Int, Int>, heightLimitMm: Int, maxUnits: Long?): PalletBoxFit? {
        val (a, b, c) = unit
        if (a <= 0 || b <= 0 || c <= 0) return null
        val room = heightLimitMm - PALLET_HEIGHT_MM
        if (room <= 0) return null
        val perms = listOf(Triple(a, b, c), Triple(a, c, b), Triple(b, a, c), Triple(b, c, a), Triple(c, a, b), Triple(c, b, a))
        var best: PalletBoxFit? = null
        for ((x, y, z) in perms) {
            val perLayer = (PALLET_L / x) * (PALLET_W / y)
            val layers = room / z
            if (perLayer <= 0 || layers <= 0) continue
            val cap = perLayer.toLong() * layers
            val n = minOf(cap, maxUnits ?: cap)
            if (n <= 0) continue
            val needLayers = ((n + perLayer - 1) / perLayer).toInt()
            val f = PalletBoxFit(n.toInt(), needLayers * z)
            val cur = best
            if (cur == null || f.units > cur.units || (f.units == cur.units && f.heightMm < cur.heightMm)) best = f
        }
        return best
    }
}
