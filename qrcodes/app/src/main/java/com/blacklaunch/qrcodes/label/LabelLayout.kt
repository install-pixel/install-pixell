package com.blacklaunch.qrcodes.label

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Розмір наклейки/аркуша. За замовчуванням — наклейка Zebra 150×100 мм (15 см завдовжки, 10 см заввишки).
 * [custom] — розмір задано вручну.
 */
data class LabelSize(
    val widthMm: Float,
    val heightMm: Float,
    val custom: Boolean = false,
    /** «Екран телефону»: пропорції екрана, картинка — точно в його пікселях (відкрити й показати з телефона). */
    val screen: Boolean = false,
) {
    fun rotated() = copy(widthMm = heightMm, heightMm = widthMm)

    companion object {
        val PRESETS = listOf(
            LabelSize(150f, 100f),
            // 4″ × 6,5″ — рулон Zebra (так його показує служба друку: 4,01″ × 6,49″).
            LabelSize(165f, 102f),
            LabelSize(100f, 50f),
            LabelSize(58f, 40f),
            LabelSize(50f, 30f),
            LabelSize(210f, 297f),
        )
        val DEFAULT = PRESETS.first()
        const val MIN_MM = 20f

        /** Ширина аркуша «Екран телефону» в мм (умовна: висота — за пропорціями екрана). */
        const val SCREEN_WIDTH_MM = 70f

        /** Аркуш під екран [widthPx]×[heightPx] (портретно). */
        fun forScreen(widthPx: Int, heightPx: Int): LabelSize {
            val w = minOf(widthPx, heightPx).coerceAtLeast(1)
            val h = maxOf(widthPx, heightPx)
            return LabelSize(SCREEN_WIDTH_MM, (SCREEN_WIDTH_MM * h / w).coerceAtMost(MAX_MM), custom = false, screen = true)
        }
        const val MAX_MM = 400f
    }
}

/** Що друкуємо: розмір наклейки, сторона QR-коду в мм, скільки копій кожного коду, чи підписувати. */
data class LabelSettings(
    val size: LabelSize = LabelSize.DEFAULT,
    val qrMm: Float = 40f,
    val copies: Int = 1,
    val caption: Boolean = true,
)

/** Один код на сторінці: лівий верхній кут клітинки (мм) і який це код зі списку. */
data class Placed(val item: Int, val xMm: Float, val yMm: Float)

/**
 * Розкладка кодів по наклейках: сіткою, з полями й проміжками, кожна сторінка — по центру (один код на наклейці —
 * рівно посередині). Що не вмістилось — на наступні наклейки.
 */
class LabelLayout(val settings: LabelSettings, itemCount: Int) {
    val widthMm = settings.size.widthMm
    val heightMm = settings.size.heightMm

    val qrMm: Float = settings.qrMm.coerceIn(MIN_QR_MM, maxQrMm(settings.size, settings.caption))

    /** Висота літер підпису (мм): пропорційно коду, але не дрібніше 2,2 мм і не більше 6 мм. */
    val captionMm: Float = captionLetters(qrMm)

    /** Місце під підпис: відступ від коду (0,6 літери) + до двох рядків (2,5 літери). */
    val captionAreaMm: Float = captionArea(qrMm, settings.caption)
    val cellWidthMm = qrMm
    val cellHeightMm = qrMm + captionAreaMm
    val gapMm = max(MIN_GAP_MM, qrMm * 0.12f)

    val columns: Int = fit(widthMm, cellWidthMm)
    val rows: Int = fit(heightMm, cellHeightMm)
    val perPage = columns * rows

    /** Усі копії всіх кодів підряд: спершу всі копії першого, потім другого… */
    private val sequence: List<Int> = (0 until itemCount).flatMap { i -> List(settings.copies.coerceAtLeast(1)) { i } }

    val pages: List<List<Placed>> = sequence.chunked(perPage).map(::placePage)

    val pageCount get() = pages.size

    private fun fit(total: Float, cell: Float): Int = max(1, floor((total - 2 * MARGIN_MM + gapMm) / (cell + gapMm)).toInt())

    private fun placePage(items: List<Int>): List<Placed> {
        val usedCols = min(columns, items.size)
        val usedRows = ceil(items.size / columns.toFloat()).toInt()
        val gridW = usedCols * cellWidthMm + (usedCols - 1) * gapMm
        val gridH = usedRows * cellHeightMm + (usedRows - 1) * gapMm
        val left = (widthMm - gridW) / 2f
        val top = (heightMm - gridH) / 2f
        return items.mapIndexed { i, item ->
            val col = i % columns
            val row = i / columns
            Placed(item, left + col * (cellWidthMm + gapMm), top + row * (cellHeightMm + gapMm))
        }
    }

    companion object {
        const val MARGIN_MM = 3f
        const val MIN_GAP_MM = 3f
        const val MIN_QR_MM = 10f

        /** Найбільший код, що вміщається на наклейку (з підписом чи без). */
        fun maxQrMm(size: LabelSize, caption: Boolean): Float {
            val byWidth = size.widthMm - 2 * MARGIN_MM
            val h = size.heightMm - 2 * MARGIN_MM
            // Код + місце під підпис мають уміститись по висоті; місце під підпис залежить від коду — шукаємо поділом навпіл.
            var lo = 0f
            var hi = h
            repeat(40) {
                val mid = (lo + hi) / 2f
                if (mid + captionArea(mid, caption) <= h) lo = mid else hi = mid
            }
            return max(MIN_QR_MM, min(byWidth, lo))
        }

        fun captionLetters(qrMm: Float): Float = (qrMm * 0.11f).coerceIn(2.2f, 6f)

        fun captionArea(qrMm: Float, caption: Boolean): Float = if (caption) captionLetters(qrMm) * 3.1f else 0f
    }
}
