package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.label.LabelLayout
import com.blacklaunch.qrcodes.label.LabelSettings
import com.blacklaunch.qrcodes.label.LabelSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelLayoutTest {
    @Test
    fun singleCodeIsCentredOnZebraLabel() {
        val layout = LabelLayout(LabelSettings(LabelSize(150f, 100f), qrMm = 40f, caption = false), itemCount = 1)
        assertEquals(1, layout.pageCount)
        val p = layout.pages[0].single()
        assertEquals(55f, p.xMm, 0.01f)
        assertEquals(30f, p.yMm, 0.01f)
    }

    @Test
    fun copiesFillGridThenNextLabel() {
        // 40 мм + проміжок 4,8 мм: у 150 мм — 3 стовпчики, у 100 мм — 2 рядки.
        val layout = LabelLayout(LabelSettings(LabelSize(150f, 100f), qrMm = 40f, copies = 7, caption = false), itemCount = 1)
        assertEquals(3, layout.columns)
        assertEquals(2, layout.rows)
        assertEquals(2, layout.pageCount)
        assertEquals(6, layout.pages[0].size)
        assertEquals(1, layout.pages[1].size)
    }

    @Test
    fun everyCodeStaysInsideLabel() {
        for (size in LabelSize.PRESETS) for (qr in listOf(10f, 25f, 40f, 300f)) for (caption in listOf(true, false)) {
            val layout = LabelLayout(LabelSettings(size, qrMm = qr, copies = 5, caption = caption), itemCount = 3)
            layout.pages.flatten().forEach { p ->
                assertTrue("$size $qr $caption", p.xMm >= 0f && p.yMm >= 0f)
                assertTrue("$size $qr $caption", p.xMm + layout.cellWidthMm <= size.widthMm + 0.01f)
                assertTrue("$size $qr $caption", p.yMm + layout.cellHeightMm <= size.heightMm + 0.01f)
            }
            assertEquals(15, layout.pages.sumOf { it.size })
        }
    }

    @Test
    fun tooBigCodeShrinksToFit() {
        val layout = LabelLayout(LabelSettings(LabelSize(58f, 40f), qrMm = 100f, caption = true), itemCount = 1)
        assertTrue(layout.qrMm + layout.captionAreaMm <= 40f - 2 * LabelLayout.MARGIN_MM + 0.01f)
    }

    @Test
    fun copiesOfEachCodeGoTogether() {
        val layout = LabelLayout(LabelSettings(LabelSize(210f, 297f), qrMm = 30f, copies = 2, caption = true), itemCount = 2)
        assertEquals(listOf(0, 0, 1, 1), layout.pages[0].map { it.item })
    }
}
