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
        // Неповний шар — один шар, на шарі стільки, скільки пускає вага.
        assertEquals(1, s.layers)
        assertEquals(7, s.perLayer)
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

    @Test
    fun alternatingLayers() {
        // Шари 10 і 9 коробок, 8 шарів за висотою: 4×10 + 4×9 = 76.
        val s = Stacking.stackAlternating(10, 9, 200, 1_000, 1800, 700_000, 0)!!
        assertEquals(8, s.layers)
        assertEquals(76, s.boxes)
        assertEquals(9, s.perLayer2)
        // Вага 20 кг: пускає 35 коробок → 3 шари (10 + 9 + 10 = 29), 4 шари — 38 забагато.
        val w = Stacking.stackAlternating(10, 9, 200, 20_000, 1800, 700_000, 0)!!
        assertEquals(3, w.layers)
        assertEquals(29, w.boxes)
        assertEquals(Limit.Weight, w.limit)
    }

    @Test
    fun unitsInPalletBox() {
        // Коробка на всю палету 1,20 × 0,80 × 1,00, штука 0,40 × 0,20 × 0,25 → 3×4×4 = 48.
        assertEquals(48, com.blacklaunch.paleta.pack.Fit.units(Triple(1200, 800, 1000), Triple(400, 200, 250)))
        // Штука більша за коробку — 0.
        assertEquals(0, com.blacklaunch.paleta.pack.Fit.units(Triple(300, 300, 300), Triple(400, 100, 100)))
    }

    @Test
    fun palletBoxHeightFromLimitAndWeight() {
        // Штука 0,40 × 0,20 × 0,25; ліміт 1,80 → місця 1,65: 12 на ряд × 6 рядів (0,25) = 72, висота 1,50…
        val all = com.blacklaunch.paleta.pack.PalletBox.fit(Triple(400, 200, 250), 1800, null)!!
        assertTrue(all.units >= 72)
        assertTrue(all.heightMm <= 1650)
        // Вага пускає лише 20 штук → висота під 20 (стільки рядів, скільки треба).
        val w = com.blacklaunch.paleta.pack.PalletBox.fit(Triple(400, 200, 250), 1800, 20)!!
        assertEquals(20, w.units)
        assertTrue(w.heightMm < all.heightMm)
    }
}
