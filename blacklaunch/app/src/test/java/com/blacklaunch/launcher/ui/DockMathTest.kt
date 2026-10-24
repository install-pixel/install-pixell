package com.blacklaunch.launcher.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class DockMathTest {
    private fun target(from: Int, dx: Float, dy: Float, count: Int = 7) =
        DockMath.targetIndex(from, Offset(dx, dy), cellWidth = 100f, cellHeight = 80f, columns = 4, count = count)

    @Test fun smallMoveStaysInPlace() = assertEquals(1, target(1, 30f, 20f))

    @Test fun moveRightAndLeft() {
        assertEquals(2, target(1, 100f, 0f))
        assertEquals(0, target(1, -120f, 0f))
    }

    @Test fun moveDownARow() = assertEquals(5, target(1, 0f, 80f))

    @Test fun clampsToGridAndCount() {
        assertEquals(3, target(1, 900f, 0f))
        assertEquals(6, target(1, 0f, 500f))
        assertEquals(0, target(1, -900f, -500f))
    }
}
