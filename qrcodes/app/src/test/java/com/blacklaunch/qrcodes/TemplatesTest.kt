package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.label.Element
import com.blacklaunch.qrcodes.label.LabelSize
import com.blacklaunch.qrcodes.label.Placement
import com.blacklaunch.qrcodes.label.Sticker
import com.blacklaunch.qrcodes.label.Templates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {
    private val tw = { text: String, letters: Float -> text.length * letters * 0.5f }
    private fun code(barcode: Boolean) = Element.Qr(1, 0f, 0f, if (barcode) "PL-0099812" else "WIFI:T:WPA;S:Net;P:x;;", "Полиця A-12", 40f, barcode = barcode)

    @Test
    fun everyTemplateFitsEveryLabel() {
        for (size in LabelSize.PRESETS + LabelSize.PRESETS.map { it.rotated() }) for (kind in Templates.Kind.entries) for (bar in listOf(false, true)) {
            val d = Templates.build(kind, size, code(bar), "Скануй мене", tw)
            assertTrue("$kind $size $bar", d.elements.any { it is Element.Qr })
            d.elements.forEach { assertTrue("$kind $size $bar ${it::class.simpleName}", Placement.fits(d, it, tw)) }
        }
    }

    @Test
    fun scanMeChoosesIconByContent() {
        assertEquals(Sticker.Wifi, Templates.stickerFor("WIFI:T:WPA;S:a;;"))
        assertEquals(Sticker.Contact, Templates.stickerFor("BEGIN:VCARD\nEND:VCARD"))
        assertEquals(Sticker.Link, Templates.stickerFor("https://x.pl"))
        assertEquals(Sticker.ScanPhone, Templates.stickerFor("PL-0099812"))
    }

    @Test
    fun ownTemplateTakesNewCodes() {
        val t = Templates.build(Templates.Kind.Shelf, LabelSize(100f, 50f), code(false), "", tw)
        val applied = Templates.apply(t, listOf(code(true).copy(content = "NEW-1", title = "Нова")), tw)
        val qr = applied.elements.filterIsInstance<Element.Qr>().single()
        assertEquals("NEW-1", qr.content)
        assertTrue(qr.barcode)
        assertEquals(t.size, applied.size)
        applied.elements.forEach { assertTrue(Placement.fits(applied, it, tw)) }
    }
}
