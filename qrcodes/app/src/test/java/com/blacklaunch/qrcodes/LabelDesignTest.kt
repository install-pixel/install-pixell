package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.label.Arrange
import com.blacklaunch.qrcodes.label.Element
import com.blacklaunch.qrcodes.label.LabelDesign
import com.blacklaunch.qrcodes.label.LabelSize
import com.blacklaunch.qrcodes.label.Placement
import com.blacklaunch.qrcodes.label.overlaps
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import com.blacklaunch.qrcodes.label.QrFrame
import com.blacklaunch.qrcodes.label.Snap
import com.blacklaunch.qrcodes.label.bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelDesignTest {
    private val tw = { text: String, letters: Float -> text.length * letters * 0.5f }
    private val zebra = LabelSize(150f, 100f)
    private fun qr(id: Int, cx: Float = 75f, cy: Float = 50f, side: Float = 40f, caption: Boolean = false, frame: QrFrame = QrFrame.None) =
        Element.Qr(id, cx, cy, "c$id", "t$id", side, caption, frame)

    @Test
    fun snapsToCentre() {
        val e = qr(1, cx = 74f, cy = 51.5f)
        val r = Snap.place(74f, 51.5f, e.bounds(tw), e, zebra)
        assertEquals(75f, r.cx, 0.001f)
        assertEquals(50f, r.cy, 0.001f)
        assertEquals(75f, r.vertical!!, 0.001f)
    }

    @Test
    fun staysInsideLabel() {
        val e = qr(1)
        val r = Snap.place(500f, -300f, e.bounds(tw), e, zebra)
        assertEquals(150f - 20f - Placement.EDGE, r.cx, 0.001f)
        assertEquals(20f + Placement.EDGE, r.cy, 0.001f)
    }

    @Test
    fun freeMoveWhenFarFromGuides() {
        val e = qr(1, side = 20f)
        val r = Snap.place(40f, 30f, e.bounds(tw), e, zebra)
        assertEquals(40f, r.cx, 0.001f)
        assertEquals(30f, r.cy, 0.001f)
        assertNull(r.vertical)
        assertNull(r.horizontal)
    }

    @Test
    fun frameAndCaptionGrowBounds() {
        val plain = qr(1).bounds(tw)
        val framed = qr(1, caption = true, frame = QrFrame.Double).bounds(tw)
        assertTrue(framed.width > plain.width)
        assertTrue(framed.height > plain.height + 5f)
    }

    @Test
    fun hitPicksTopmost() {
        val d = LabelDesign(zebra, listOf(qr(1), Element.Text(2, 75f, 50f, "Hello", 6f)))
        assertEquals(2, d.hit(75f, 50f, 0f, tw)!!.id)
        assertEquals(1, d.hit(60f, 35f, 0f, tw)!!.id)
        assertNull(d.hit(5f, 5f, 0f, tw))
    }

    @Test
    fun resizeClamps() {
        assertEquals(Element.MIN_QR, qr(1).resized(1f).size, 0.001f)
        assertEquals(Element.MAX_LETTERS, Element.Text(1, 0f, 0f, "a", 5f).resized(999f).size, 0.001f)
    }

    @Test
    fun fillMakesCopiesInsideLabel() {
        val d = Arrange.fill(LabelDesign(zebra, listOf(qr(1), Element.Text(9, 10f, 10f, "x", 5f))), qr(1), tw)
        val codes = d.elements.filterIsInstance<Element.Qr>()
        assertEquals(6, codes.size)
        assertEquals(codes.size, codes.map { it.id }.toSet().size)
        assertTrue(d.elements.any { it is Element.Text })
        codes.forEach { c ->
            val b = c.bounds(tw)
            assertTrue(b.left >= 0f && b.top >= 0f && b.right <= 150f && b.bottom <= 100f)
        }
    }

    @Test
    fun gridSpreadsCodes() {
        val d = Arrange.grid(LabelDesign(zebra, listOf(qr(1), qr(2), qr(3))), tw)
        val xs = d.elements.map { it.cx }.toSet()
        assertEquals(3, xs.size)
    }

    @Test
    fun resizingLabelKeepsProportions() {
        val d = LabelDesign(zebra, listOf(qr(1, cx = 75f, cy = 50f))).resizedTo(LabelSize(100f, 50f))
        assertEquals(50f, d.elements[0].cx, 0.001f)
        assertEquals(25f, d.elements[0].cy, 0.001f)
    }

    @Test
    fun overlappingDropMovesToNearestFreeSpot() {
        val d = LabelDesign(zebra, listOf(qr(1, cx = 40f), qr(2, cx = 45f)))
        val moving = d.elements[1]
        assertFalse(Placement.fits(d, moving, tw))
        val placed = Placement.nearestFree(d, moving, tw)!!
        val withPlaced = d.replace(placed)
        assertTrue(Placement.fits(withPlaced, placed, tw))
        assertFalse(placed.bounds(tw).overlaps(d.elements[0].bounds(tw)))
    }

    @Test
    fun noRoomGivesNull() {
        val small = LabelSize(50f, 30f)
        val d = LabelDesign(small, listOf(qr(1, cx = 25f, cy = 15f, side = 26f)))
        assertEquals(null, Placement.nearestFree(d, qr(2, cx = 25f, cy = 15f, side = 26f), tw))
    }

    @Test
    fun everyFrameFitsAndFilledCopiesNeverOverlap() {
        for (frame in QrFrame.entries) for (caption in listOf(true, false)) {
            val q = qr(1, side = 30f, caption = caption, frame = frame)
            val d = Arrange.fill(LabelDesign(zebra, listOf(q)), q, tw)
            assertTrue(d.elements.isNotEmpty())
            d.elements.forEach { e -> assertTrue("$frame $caption", Placement.fits(d, e, tw)) }
        }
    }

    @Test
    fun gridShrinksWhenCodesDoNotFit() {
        val many = (1..12).map { qr(it, side = 60f, caption = true) }
        val d = Arrange.grid(LabelDesign(zebra, many), tw)
        d.elements.forEach { e -> assertTrue(Placement.fits(d, e, tw)) }
        assertNotNull(d.elements.first())
    }
}
