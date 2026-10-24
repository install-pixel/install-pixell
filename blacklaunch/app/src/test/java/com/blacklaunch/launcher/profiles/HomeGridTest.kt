package com.blacklaunch.launcher.profiles

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGridTest {
    @Test fun newAppsFillBottomRowLeftToRight() =
        assertEquals(mapOf("a" to 0, "b" to 1, "c" to 2), HomeGrid.assign(listOf("a", "b", "c"), emptyMap()))

    @Test fun savedCellsAreKeptAndNewOnesTakeFreeCells() =
        assertEquals(mapOf("a" to 13, "b" to 0, "c" to 1), HomeGrid.assign(listOf("a", "b", "c"), mapOf("a" to 13)))

    @Test fun duplicateSavedCellsAreResolved() =
        assertEquals(mapOf("a" to 5, "b" to 0), HomeGrid.assign(listOf("a", "b"), mapOf("a" to 5, "b" to 5)))

    @Test fun removedAppsAreDropped() =
        assertEquals(mapOf("b" to 1), HomeGrid.assign(listOf("b"), mapOf("a" to 0, "b" to 1)))

    @Test fun moveToFreeCell() =
        assertEquals(mapOf("a" to 10, "b" to 1), HomeGrid.move(mapOf("a" to 0, "b" to 1), "a", 10))

    @Test fun moveOntoOtherSwaps() =
        assertEquals(mapOf("a" to 1, "b" to 0), HomeGrid.move(mapOf("a" to 0, "b" to 1), "a", 1))

    @Test fun cellMath() {
        assertEquals(9, HomeGrid.cell(column = 1, rowFromBottom = 2))
        assertEquals(1, HomeGrid.column(9))
        assertEquals(2, HomeGrid.rowFromBottom(9))
    }

    @Test fun encodeDecode() {
        val data = mapOf("work" to mapOf("a/Main/0" to 3, "b/Main/0" to 9), "home" to mapOf("c/Main/0" to 0))
        assertEquals(data, InMemoryHomeLayoutStore.decode(InMemoryHomeLayoutStore.encode(data)))
    }
}
