package com.blacklaunch.qrcodes.qr

import com.google.zxing.oned.Code128Writer
import com.google.zxing.oned.EAN13Writer

/** Який код малювати: QR чи лінійний штрихкод. Вибирається раз — на «Створити» — і зберігається з кодом. */
enum class CodeFormat { QR, BARCODE }

/** Готовий до малювання код: QR-матриця або смуги штрихкоду. */
sealed interface Symbol {
    class Qr(val matrix: QrMatrix) : Symbol

    /**
     * Смуги штрихкоду зліва направо (true — темна), без полів. [ean] — EAN-13 (13 цифр), інакше Code128 (будь-який
     * текст латиницею, цифрами й знаками — стандарт складів і сканерів Zebra).
     */
    class Bars(val modules: BooleanArray, val ean: Boolean) : Symbol

    companion object {
        /** Скільки порожніх модулів лишати з боків штрихкоду (сканеру потрібна «тиха зона»). */
        const val BAR_QUIET = 10

        /** Висота смуг відносно ширини штрихкоду. */
        const val BAR_HEIGHT_RATIO = 0.42f

        /** null — порожньо або цей текст таким кодом не закодувати (задовгий QR, кирилиця в штрихкоді…). */
        fun encode(text: String, format: CodeFormat): Symbol? {
            if (text.isEmpty()) return null
            return when (format) {
                CodeFormat.QR -> QrMatrix.encode(text)?.let(::Qr)
                CodeFormat.BARCODE -> bars(text)
            }
        }

        private fun bars(text: String): Bars? = try {
            if (isEan13(text)) {
                Bars(EAN13Writer().encode(text), ean = true)
            } else {
                // Code128 приймає лише ASCII; довгий текст робить штрихкод нечитабельно густим — обмежуємо.
                if (text.length > MAX_CODE128 || text.any { it.code !in 32..126 }) null else Bars(Code128Writer().encode(text), ean = false)
            }
        } catch (_: IllegalArgumentException) {
            null
        }

        /** 13 цифр із правильною контрольною цифрою — це EAN-13 (як на товарах). */
        fun isEan13(text: String): Boolean {
            if (text.length != 13 || text.any { !it.isDigit() }) return false
            val digits = text.map { it - '0' }
            val sum = (0 until 12).sumOf { i -> digits[i] * if (i % 2 == 0) 1 else 3 }
            return (10 - sum % 10) % 10 == digits[12]
        }

        /** Довше — смуги стають надто тонкими для звичайного сканера. */
        const val MAX_CODE128 = 48
    }
}

/** Формат збереженого коду. */
val com.blacklaunch.qrcodes.data.QrCode.codeFormat: CodeFormat
    get() = if (format == CodeFormat.BARCODE.name) CodeFormat.BARCODE else CodeFormat.QR
