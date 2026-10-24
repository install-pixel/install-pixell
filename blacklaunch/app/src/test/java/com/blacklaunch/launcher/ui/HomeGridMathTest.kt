package com.blacklaunch.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGridMathTest {
    // Екран 400×1000, клітинки 100×100 → 4 стовпці, 10 рядів.
    private fun target(left: Float, top: Float) = HomeGridMath.targetCell(left, top, 100f, 100f, 1000f, 10)

    @Test fun bottomLeftCell() = assertEquals(0, target(0f, 900f))

    @Test fun nearestCellByCentre() {
        assertEquals(1, target(140f, 880f))
        assertEquals(5, target(100f, 800f))
    }

    @Test fun topRightCell() = assertEquals(39, target(300f, 0f))

    @Test fun clampsOutside() {
        assertEquals(3, target(900f, 1500f))
        assertEquals(36, target(-300f, -500f))
    }
}
