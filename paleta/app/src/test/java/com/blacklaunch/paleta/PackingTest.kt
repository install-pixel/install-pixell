package com.blacklaunch.paleta

import com.blacklaunch.paleta.pack.BoxSize
import com.blacklaunch.paleta.pack.Layout
import com.blacklaunch.paleta.pack.Packer
import com.blacklaunch.paleta.pack.Placed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackingTest {
    private fun count(l: Int, w: Int, overhang: Int = 0) = Packer.auto(BoxSize(l, w), overhang).count

    @Test
    fun knownCounts() {
        // Бланк: 47×40 — 4 на шар.
        assertEquals(4, count(470, 400))
        // Тут проста сітка гірша — змішана розкладка дає більше.
        assertEquals(10, count(350, 250))
        assertEquals(11, count(330, 220))
        assertEquals(14, count(290, 210))
        assertEquals(6, count(450, 270))
        assertEquals(8, count(400, 300))
        assertEquals(1, count(1200, 800))
        assertEquals(0, count(1300, 900))
    }

    @Test
    fun everyVariantIsValid() {
        for (l in 150..700 step 37) for (w in 120..l step 41) for (o in listOf(0, 25)) {
            val box = BoxSize(l, w)
            val r = Packer.auto(box, o)
            assertTrue(r.variants.isNotEmpty())
            for (v in r.variants) {
                assertEquals("$l×$w", r.count, v.size)
                val c = Layout.check(v, box, o)
                assertTrue("$l×$w o=$o $c", c.ok)
                if (!r.usesOverhang) assertEquals(0, c.overhangUsed)
            }
        }
    }

    @Test
    fun overhangOnlyWhenItHelps() {
        // 410×270: у межах палети 8 (сітка 2×... ), зі звисом 25 мм на бік (1250×850) — 9.
        val plain = Packer.auto(BoxSize(410, 270), 0)
        val wide = Packer.auto(BoxSize(410, 270), 25)
        assertTrue(wide.count >= plain.count)
        val same = Packer.auto(BoxSize(470, 400), 25)
        assertEquals(4, same.count)
        assertTrue(!same.usesOverhang)
    }

    @Test
    fun checkFindsProblems() {
        val box = BoxSize(400, 300)
        val boxes = listOf(Placed(0, 0, false), Placed(200, 0, false), Placed(1000, 600, false))
        val c = Layout.check(boxes, box, 0)
        assertEquals(setOf(0, 1), c.overlapping)
        assertEquals(mapOf(2 to 200), c.outside)
        val o = Layout.check(listOf(Placed(-20, 0, false)), box, 25)
        assertTrue(o.ok)
        assertEquals(20, o.overhangUsed)
    }

    @Test
    fun snapAndFill() {
        val box = BoxSize(400, 300)
        val s = Layout.snap(listOf(Placed(0, 0, false), Placed(0, 0, false)), 1, 415, 12, false, box, 0)
        assertEquals(Placed(400, 0, false), s)
        val full = Layout.fill(emptyList(), box, 0)
        assertEquals(8, full.size)
        assertTrue(Layout.check(full, box, 0).ok)
        assertNull(Layout.freeSpot(full, box, 0))
        assertEquals(full, Layout.decode(Layout.encode(full)))
    }

    @Test
    fun smallBoxesStayFast() {
        for ((l, w) in listOf(100 to 60, 73 to 41, 50 to 30, 121 to 97)) {
            val t = System.nanoTime()
            val r = Packer.auto(BoxSize(l, w), 25)
            val ms = (System.nanoTime() - t) / 1_000_000
            assertTrue("$l×$w took $ms ms", ms < 3000)
            assertTrue(r.count >= (1200 / l) * (800 / w))
        }
    }
}
