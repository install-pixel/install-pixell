package com.blacklaunch.qrcodes.qr

import com.google.zxing.oned.Code128Writer
import com.google.zxing.oned.EAN13Writer

/** Який код малювати: квадратний (QR і подібні) чи лінійний штрихкод. Вибирається раз — на «Створити» — і зберігається з кодом. */
enum class CodeFormat { QR, BARCODE }

/**
 * Конкретний вид коду. Типово — [QR] і [AUTO] (штрихкод сам вибирає: EAN-13, EAN-8, UPC-A для цифр із контрольною
 * цифрою, інакше Code 128); решту можна вибрати вручну. [title] — назва (міжнародна, не перекладається).
 */
enum class CodeKind(val format: CodeFormat, val title: String) {
    QR(CodeFormat.QR, "QR"),
    DATA_MATRIX(CodeFormat.QR, "Data Matrix"),
    AZTEC(CodeFormat.QR, "Aztec"),
    AUTO(CodeFormat.BARCODE, ""),
    CODE128(CodeFormat.BARCODE, "Code 128"),
    CODE39(CodeFormat.BARCODE, "Code 39"),
    CODE93(CodeFormat.BARCODE, "Code 93"),
    EAN13(CodeFormat.BARCODE, "EAN-13"),
    EAN8(CodeFormat.BARCODE, "EAN-8"),
    UPCA(CodeFormat.BARCODE, "UPC-A"),
    UPCE(CodeFormat.BARCODE, "UPC-E"),
    ITF(CodeFormat.BARCODE, "ITF"),
    CODABAR(CodeFormat.BARCODE, "Codabar"),
    ;

    /** Як зберігається в базі: «QR», «BARCODE» (авто) або «BARCODE:CODE39», «QR:AZTEC»… (старі записи — як були). */
    val storage: String get() = when (this) {
        QR -> CodeFormat.QR.name
        AUTO -> CodeFormat.BARCODE.name
        else -> "${format.name}:$name"
    }

    companion object {
        fun of(storage: String?): CodeKind {
            if (storage.isNullOrEmpty()) return QR
            if (storage == CodeFormat.BARCODE.name) return AUTO
            if (storage == CodeFormat.QR.name) return QR
            val name = storage.substringAfter(':', "")
            return entries.firstOrNull { it.name == name } ?: if (storage.startsWith(CodeFormat.BARCODE.name)) AUTO else QR
        }

        /** Типовий для категорії. */
        fun default(format: CodeFormat): CodeKind = if (format == CodeFormat.BARCODE) AUTO else QR
    }
}

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
        fun encode(text: String, format: CodeFormat): Symbol? = encode(text, CodeKind.default(format))

        fun encode(text: String, kind: CodeKind): Symbol? {
            if (text.isEmpty()) return null
            return try {
                when (kind) {
                    CodeKind.QR -> QrMatrix.encode(text)?.let(::Qr)
                    CodeKind.DATA_MATRIX -> square(text, com.google.zxing.BarcodeFormat.DATA_MATRIX)
                    CodeKind.AZTEC -> square(text, com.google.zxing.BarcodeFormat.AZTEC)
                    CodeKind.AUTO -> bars(text)
                    CodeKind.CODE128 -> bars(text, forceCode128 = true)
                    CodeKind.CODE39 -> if (text.length > MAX_1D) null else Bars(com.google.zxing.oned.Code39Writer().encode(text), ean = false)
                    CodeKind.CODE93 -> if (text.length > MAX_1D) null else Bars(com.google.zxing.oned.Code93Writer().encode(text), ean = false)
                    CodeKind.EAN13 -> Bars(EAN13Writer().encode(text), ean = true)
                    CodeKind.EAN8 -> Bars(com.google.zxing.oned.EAN8Writer().encode(text), ean = true)
                    CodeKind.UPCA -> Bars(com.google.zxing.oned.UPCAWriter().encode(text, com.google.zxing.BarcodeFormat.UPC_A, 0, 0, mapOf(com.google.zxing.EncodeHintType.MARGIN to 0)).let(::row), ean = true)
                    CodeKind.UPCE -> Bars(com.google.zxing.oned.UPCEWriter().encode(text), ean = true)
                    CodeKind.ITF -> if (text.length > MAX_1D) null else Bars(com.google.zxing.oned.ITFWriter().encode(text), ean = false)
                    CodeKind.CODABAR -> if (text.length > MAX_1D) null else Bars(com.google.zxing.oned.CodaBarWriter().encode(text), ean = false)
                }
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: com.google.zxing.WriterException) {
                null
            } catch (_: RuntimeException) {
                null
            }
        }

        /** Квадратні 2D-коди (Data Matrix, Aztec) — у ту саму матрицю, що й QR (так само малюються й друкуються). */
        private fun square(text: String, format: com.google.zxing.BarcodeFormat): Qr? {
            // Data Matrix у ZXing — лише латиниця (ISO-8859-1); кирилицю він би зіпсував — краще одразу «не вміщається».
            if (format == com.google.zxing.BarcodeFormat.DATA_MATRIX && text.any { it.code > 255 }) return null
            val hints = mapOf(
                com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
                com.google.zxing.EncodeHintType.MARGIN to 0,
                com.google.zxing.EncodeHintType.DATA_MATRIX_SHAPE to com.google.zxing.datamatrix.encoder.SymbolShapeHint.FORCE_SQUARE,
            )
            val bits = when (format) {
                com.google.zxing.BarcodeFormat.AZTEC -> com.google.zxing.aztec.AztecWriter().encode(text, format, 0, 0, hints)
                else -> com.google.zxing.datamatrix.DataMatrixWriter().encode(text, format, 0, 0, hints)
            }
            // Зрізаємо порожні поля, якщо письменник їх додав.
            val rect = bits.enclosingRectangle ?: return null
            val n = maxOf(rect[2], rect[3])
            return Qr(QrMatrix(n, BooleanArray(n * n) { i -> val x = i % n; val y = i / n; x < rect[2] && y < rect[3] && bits[rect[0] + x, rect[1] + y] }))
        }

        /** Один рядок BitMatrix — у смуги. */
        private fun row(m: com.google.zxing.common.BitMatrix): BooleanArray = BooleanArray(m.width) { m[it, 0] }

        private fun bars(text: String, forceCode128: Boolean = false): Bars? = try {
            if (!forceCode128 && isEan13(text)) {
                Bars(EAN13Writer().encode(text), ean = true)
            } else if (!forceCode128 && text.length == 8 && text.all { it.isDigit() } && checksumOk(text)) {
                Bars(com.google.zxing.oned.EAN8Writer().encode(text), ean = true)
            } else if (!forceCode128 && text.length == 12 && text.all { it.isDigit() } && checksumOk(text)) {
                Bars(com.google.zxing.oned.UPCAWriter().encode(text, com.google.zxing.BarcodeFormat.UPC_A, 0, 0, mapOf(com.google.zxing.EncodeHintType.MARGIN to 0)).let(::row), ean = true)
            } else {
                // Code128 приймає лише ASCII; довгий текст робить штрихкод нечитабельно густим — обмежуємо.
                if (text.length > MAX_CODE128 || text.any { it.code !in 32..126 }) null else Bars(Code128Writer().encode(text), ean = false)
            }
        } catch (_: IllegalArgumentException) {
            null
        }

        /** Контрольна цифра EAN/UPC (остання) правильна — для 8 і 12 цифр. */
        private fun checksumOk(text: String): Boolean {
            val d = text.map { it - '0' }
            val body = d.dropLast(1)
            // Справа наліво: непарні позиції ×3.
            val sum = body.reversed().mapIndexed { i, v -> if (i % 2 == 0) v * 3 else v }.sum()
            return (10 - sum % 10) % 10 == d.last()
        }

        /** Довші для рідших штрихкодів — смуги надто тонкі для звичайного сканера. */
        const val MAX_1D = 40

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

/** Формат збереженого коду (категорія). */
val com.blacklaunch.qrcodes.data.QrCode.codeFormat: CodeFormat
    get() = codeKind.format

/** Конкретний вид збереженого коду. */
val com.blacklaunch.qrcodes.data.QrCode.codeKind: CodeKind
    get() = CodeKind.of(format)
