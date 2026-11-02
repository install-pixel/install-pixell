package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.qr.Batch
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchTest {
    @Test
    fun seriesPadsNumbersAndKeepsOrder() {
        assertEquals(listOf("ПАЛЕТА-001", "ПАЛЕТА-002", "ПАЛЕТА-003"), Batch.series("ПАЛЕТА-", 1, 3, 3))
        assertEquals(listOf("A9", "A10"), Batch.series("A", 9, 2, 0))
        assertEquals(listOf("R-0098/X", "R-0099/X", "R-0100/X"), Batch.series("R-", 98, 3, 4, "/X"))
        assertEquals(Batch.MAX, Batch.series("", 1, 50000, 0).size)
    }

    @Test
    fun listSkipsEmptyAndRepeats() {
        assertEquals(listOf("a", "b", "c"), Batch.lines(" a \n\nb\na\n c "))
    }
}
