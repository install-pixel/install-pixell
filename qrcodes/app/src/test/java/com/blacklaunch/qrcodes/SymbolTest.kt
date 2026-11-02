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

    @Test
    fun everyKindEncodesTypicalContent() {
        val k = com.blacklaunch.qrcodes.qr.CodeKind
        assertTrue(Symbol.encode("https://x.pl", k.DATA_MATRIX) is Symbol.Qr)
        assertTrue(Symbol.encode("Квиток 12", k.AZTEC) is Symbol.Qr)
        assertNull(Symbol.encode("Склад", k.DATA_MATRIX))
        assertTrue(Symbol.encode("PL-0099812", k.CODE128) is Symbol.Bars)
        assertTrue(Symbol.encode("ABC-123", k.CODE39) is Symbol.Bars)
        assertTrue(Symbol.encode("ABC-123", k.CODE93) is Symbol.Bars)
        assertTrue(Symbol.encode("5901234123457", k.EAN13) is Symbol.Bars)
        assertTrue(Symbol.encode("96385074", k.EAN8) is Symbol.Bars)
        assertTrue(Symbol.encode("036000291452", k.UPCA) is Symbol.Bars)
        assertTrue(Symbol.encode("12345678", k.ITF) is Symbol.Bars)
        assertTrue(Symbol.encode("A12345B", k.CODABAR) is Symbol.Bars)
        assertNull(Symbol.encode("123", k.EAN13))
        // «Авто» впізнає EAN-8 і UPC-A за контрольною цифрою.
        assertTrue((Symbol.encode("96385074", k.AUTO) as Symbol.Bars).ean)
        assertTrue((Symbol.encode("036000291452", k.AUTO) as Symbol.Bars).ean)
    }

    @Test
    fun kindStorageRoundTripsAndOldValuesStay() {
        val k = com.blacklaunch.qrcodes.qr.CodeKind
        k.entries.forEach { assertTrue(k.of(it.storage) == it) }
        assertTrue(k.of("QR") == k.QR)
        assertTrue(k.of("BARCODE") == k.AUTO)
        assertTrue(k.of(null) == k.QR)
    }
}
