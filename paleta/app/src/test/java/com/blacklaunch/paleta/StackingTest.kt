package com.blacklaunch.paleta

import com.blacklaunch.paleta.pack.Limit
import com.blacklaunch.paleta.pack.Num
import com.blacklaunch.paleta.pack.Stacking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackingTest {
    @Test
    fun formExample() {
        // Бланк: 0,47 × 0,40 × 0,40, 2,30 кг, 4 на шар, ліміт 1,80 → 4 шари, 16 кор., 1,75 м, 36,80 кг.
        val s = Stacking.stack(4, 400, 2300, 1800, 700_000, 0)!!
        assertEquals(4, s.layers)
        assertEquals(16, s.boxes)
        assertEquals(1750, s.heightMm)
        assertEquals(36_800, s.weightG)
        assertEquals(Limit.Height, s.limit)
        assertEquals("1,75", Num.metres(s.heightMm))
        assertEquals("36,80", Num.kilos(s.weightG))
    }

    @Test
    fun weightLimitDropsLayers() {
        // 10 на шар по 20 кг, висота дала б 8 шарів (1600 кг) — вага пускає 700 кг → 3 повні шари.
        val s = Stacking.stack(10, 200, 20_000, 1800, 700_000, 0)!!
        assertEquals(Limit.Weight, s.limit)
        assertEquals(8, s.layersByHeight)
        assertEquals(3, s.layers)
        assertEquals(30, s.boxes)
        // Палета 25 кг рахується у вагу.
        val p = Stacking.stack(10, 200, 20_000, 1800, 700_000, 25_000)!!
        assertEquals(3, p.layers)
        assertEquals(625_000, p.weightG)
    }

    @Test
    fun partialLayer() {
        val s = Stacking.stack(10, 300, 100_000, 1800, 700_000, 0)!!
        assertTrue(s.partial)
        assertEquals(7, s.boxes)
        assertEquals(0, s.layers)
    }

    @Test
    fun parsing() {
        assertEquals(470, Num.lengthMm("0,47"))
        assertEquals(470, Num.lengthMm("47"))
        assertEquals(475, Num.lengthMm("47.5"))
        assertEquals(1200, Num.lengthMm("1,2"))
        assertEquals(2300L, Num.grams("2,3"))
        assertEquals(1L, Num.grams("0.001"))
        assertEquals(null, Num.lengthMm(""))
        assertEquals(null, Num.lengthMm("abc"))
        assertEquals("2,5", Num.cm(25))
        assertEquals("0,475", Num.metres(475))
    }
}
