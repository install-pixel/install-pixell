package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.qr.QrMatrix
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QrMatrixTest {
    /** Закодувати й прочитати назад — те саме, і для кирилиці, і для польських літер. */
    @Test
    fun roundTripUtf8() {
        for (text in listOf("DSV-12345", "Пароль: їжак Ґанок", "Zażółć gęślą jaźń", "WIFI:T:WPA;S:Склад;P:12345678;;")) {
            assertEquals(text, decode(QrMatrix.encode(text)!!))
        }
    }

    @Test
    fun emptyAndTooLongGiveNothing() {
        assertNull(QrMatrix.encode(""))
        assertNull(QrMatrix.encode("x".repeat(5000)))
        assertNotNull(QrMatrix.encode("x".repeat(1500)))
    }

    private fun decode(m: QrMatrix): String {
        val scale = 4
        val side = (m.size + 2 * QrMatrix.QUIET_ZONE) * scale
        val pixels = IntArray(side * side) { i ->
            val x = (i % side) / scale - QrMatrix.QUIET_ZONE
            val y = (i / side) / scale - QrMatrix.QUIET_ZONE
            if (x in 0 until m.size && y in 0 until m.size && m[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap).text
    }
}
