package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.label.LabelLayout
import com.blacklaunch.qrcodes.label.LabelSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapacityTest {
    @Test
    fun readableCodesWithCaptionsOnALabel() {
        // 25 мм із підписом (≈33,5 мм заввишки), поля 3 мм, проміжки 3 мм: 100×150 — 3 в ряд, 4 ряди.
        assertEquals(12, LabelLayout.capacity(LabelSize(100f, 150f)))
        assertEquals(1, LabelLayout.capacity(LabelSize(30f, 40f)))
    }

    @Test
    fun biggerLabelNeverHoldsFewer() {
        assertTrue(LabelLayout.capacity(LabelSize(100f, 150f)) >= LabelLayout.capacity(LabelSize(100f, 100f)))
        assertTrue(LabelLayout.capacity(LabelSize(10f, 10f)) >= 1)
    }
}
