package com.blacklaunch.qrcodes.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Модулі QR-коду без поля навколо: [size]×[size], true — темний модуль. */
class QrMatrix(val size: Int, private val cells: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = cells[y * size + x]

    companion object {
        /** Скільки порожніх модулів лишати навколо коду (за стандартом — 4). */
        const val QUIET_ZONE = 4

        /**
         * Кодує [text] (UTF-8 — кирилиця й польські літери читаються правильно). null — порожній текст або задовгий
         * для QR-коду.
         */
        fun encode(text: String): QrMatrix? {
            if (text.isEmpty()) return null
            return try {
                val hints = mapOf(
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to 0,
                )
                val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
                val n = bits.width
                QrMatrix(n, BooleanArray(n * n) { bits[it % n, it / n] })
            } catch (_: WriterException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
