package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.label.Ink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkTest {
    private val n = 101
    private val xs = FloatArray(n) { it * 0.5f }
    private val ys = FloatArray(n)

    @Test
    fun strokeStartsAndEndsThin() {
        val w = Ink.widths(xs, ys, null, 1f)
        assertEquals(Ink.TIP, w.first(), 1e-4f)
        assertEquals(Ink.TIP, w.last(), 1e-4f)
        assertEquals(1f, w[n / 2], 1e-4f)
        // Розходиться плавно, без стрибків.
        for (i in 1 until n) assertTrue(kotlin.math.abs(w[i] - w[i - 1]) < 0.15f)
    }

    @Test
    fun fasterIsThinner() {
        assertTrue(Ink.targetFactor(0f) > Ink.targetFactor(0.3f))
        assertEquals(Ink.FAST_FACTOR, Ink.targetFactor(5f), 1e-4f)
    }

    @Test
    fun shortStrokeIsTaperedButNotEmpty() {
        val w = Ink.widths(floatArrayOf(0f, 0.5f, 1f), floatArrayOf(0f, 0f, 0f), listOf(1f, 1f, 1f), 1f)
        assertTrue(w.all { it > 0f && it <= 1f })
    }
}
