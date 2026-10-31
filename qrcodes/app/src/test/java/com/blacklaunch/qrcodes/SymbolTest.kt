package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.qr.CodeFormat
import com.blacklaunch.qrcodes.qr.Symbol
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolTest {
    @Test
    fun barcodePicksEanOrCode128() {
        assertTrue(Symbol.isEan13("5901234123457"))
        assertTrue(!Symbol.isEan13("5901234123458"))
        assertTrue((Symbol.encode("5901234123457", CodeFormat.BARCODE) as Symbol.Bars).ean)
        assertTrue(!(Symbol.encode("PL-0099812", CodeFormat.BARCODE) as Symbol.Bars).ean)
    }

    @Test
    fun barcodeRejectsCyrillicAndEmpty() {
        assertNull(Symbol.encode("Склад", CodeFormat.BARCODE))
        assertNull(Symbol.encode("", CodeFormat.QR))
        assertTrue(Symbol.encode("Склад", CodeFormat.QR) is Symbol.Qr)
    }
}
