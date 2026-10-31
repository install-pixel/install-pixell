package com.blacklaunch.qrcodes.label

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidPath
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.blacklaunch.qrcodes.R
import com.blacklaunch.qrcodes.qr.CodeFormat
import com.blacklaunch.qrcodes.qr.QrMatrix
import com.blacklaunch.qrcodes.qr.Symbol
import com.blacklaunch.qrcodes.ui.AppStyle
import com.blacklaunch.qrcodes.ui.Glyph
import com.blacklaunch.qrcodes.ui.glyphStrokes
import com.blacklaunch.qrcodes.ui.pencilLoop
import com.blacklaunch.qrcodes.ui.taperedPath
import java.io.File
import java.io.FileOutputStream

/**
 * Кольори наклейки. І на екрані, і на папері — біле тло й чорне чорнило: на чорному екрані аркуш видно цілком, з
 * чіткими межами, і він виглядає точно так, як надрукується (так читає будь-який сканер і друкує Zebra).
 */
data class LabelPalette(val paper: Int, val ink: Int, val qrDark: Int, val qrTile: Int? = null) {
    companion object {
        /**
         * «Екран телефону»: чорне тло, білі тексти й значки, код — на білій картці. На іншому телефоні галерея додасть
         * чорні смуги — вони зіллються з тлом, і картинка всюди виглядає як на весь екран.
         */
        val Phone = LabelPalette(paper = 0xFF000000.toInt(), ink = 0xFFEDEDED.toInt(), qrDark = 0xFF000000.toInt(), qrTile = 0xFFFFFFFF.toInt())

        /** На екрані й у картинці: аркуш «Екран телефону» — чорний, решта — білий папір. */
        fun forView(design: LabelDesign): LabelPalette = if (design.size.screen) Phone else Screen

        val Screen = LabelPalette(paper = 0xFFF4F4F2.toInt(), ink = 0xFF111111.toInt(), qrDark = 0xFF000000.toInt())
        val Print = LabelPalette(paper = 0xFFFFFFFF.toInt(), ink = 0xFF000000.toInt(), qrDark = 0xFF000000.toInt())
    }
}

fun Sticker.glyph(): Glyph = when (this) {
    Sticker.Wifi -> Glyph.Wifi
    Sticker.Phone -> Glyph.Phone
    Sticker.Link -> Glyph.Globe
    Sticker.Download -> Glyph.Download
    Sticker.Email -> Glyph.Mail
    Sticker.Location -> Glyph.Pin
    Sticker.Contact -> Glyph.Person
    Sticker.ScanPhone -> Glyph.ScanPhone
    Sticker.Smile -> Glyph.Smile
    Sticker.Heart -> Glyph.Heart
}

/**
 * Малює наклейку однаково для екрана, друку (PDF — векторно, точний розмір у мм) і картинки (PNG 300 dpi).
 * Текст і підписи — шрифтом за стилем (від руки чи звичайним), рамки й значки — олівцем.
 */
class LabelRenderer(private val hand: Typeface) {
    private val measurePaint = Paint().apply { textSize = 100f; isAntiAlias = true }
    private val plain: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    /** Шрифт наклейки — за стилем програми: «Аркуш» — від руки, «Скло» — звичайний (так і друкується). */
    private fun face(): Typeface = if (AppStyle.glass) plain else hand

    /** Шрифт від руки дрібніший на око — підпис трохи більший; звичайний — як є. */
    private fun scale(): Float = if (AppStyle.glass) 1f else HAND_SCALE

    /** Останні закодовані QR-коди (не більше [MATRIX_CACHE] — старі витісняються). */
    private val matrices = object : LinkedHashMap<String, Symbol?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Symbol?>?) = size > MATRIX_CACHE
    }

    /** Ширина тексту в мм при висоті літер [letters] мм. */
    fun textWidth(text: String, letters: Float): Float = synchronized(measurePaint) {
        measurePaint.typeface = face()
        measurePaint.measureText(text)
    } * letters / 100f

    private fun symbol(e: Element.Qr): Symbol? = synchronized(matrices) {
        val format = if (e.barcode) CodeFormat.BARCODE else CodeFormat.QR
        matrices.getOrPut("${format.name}|${e.content}") { Symbol.encode(e.content, format) }
    }

    fun draw(canvas: Canvas, pxPerMm: Float, design: LabelDesign, palette: LabelPalette) {
        // Лише сам аркуш: тло й усе намальоване обрізаються по його краю (нічого не вилазить за наклейку).
        val w = design.size.widthMm * pxPerMm
        val h = design.size.heightMm * pxPerMm
        canvas.save()
        canvas.clipRect(0f, 0f, w, h)
        canvas.drawRect(0f, 0f, w, h, Paint().apply { color = palette.paper })
        drawContent(canvas, pxPerMm, design, palette)
        canvas.restore()
    }

    private fun drawContent(canvas: Canvas, pxPerMm: Float, design: LabelDesign, palette: LabelPalette) {
        val ink = Paint().apply { color = palette.ink; style = Paint.Style.FILL; isAntiAlias = true }
        if (design.border != QrFrame.None) {
            val inset = BORDER_INSET * pxPerMm
            drawOutline(
                canvas, design.border, inset, inset,
                design.size.widthMm * pxPerMm - 2 * inset, design.size.heightMm * pxPerMm - 2 * inset,
                4f * pxPerMm, 1.2f * pxPerMm, 997, pxPerMm, ink,
            )
        }
        design.elements.forEach { e -> drawElement(canvas, pxPerMm, e, palette, ink) }
    }

    /** Один елемент (з поворотом навколо його центру). Також для зразків рамок і значків у панелі. */
    fun drawElement(canvas: Canvas, pxPerMm: Float, e: Element, palette: LabelPalette, ink: Paint = Paint().apply { color = palette.ink; style = Paint.Style.FILL; isAntiAlias = true }) {
        canvas.save()
        if (e.rotation != 0f) canvas.rotate(e.rotation, e.cx * pxPerMm, e.cy * pxPerMm)
        when (e) {
            is Element.Qr -> drawQrElement(canvas, pxPerMm, e, palette, ink)
            is Element.Text -> drawText(canvas, pxPerMm, e, ink)
            is Element.Icon -> drawIcon(canvas, pxPerMm, e, ink)
        }
        canvas.restore()
    }

    private fun drawQrElement(canvas: Canvas, k: Float, e: Element.Qr, palette: LabelPalette, ink: Paint) {
        val pad = framePad(e)
        val caption = captionArea(e)
        val blockW = (e.side + 2 * pad) * k
        val blockH = qrBlockHeight(e) * k
        val blockLeft = e.cx * k - blockW / 2
        val blockTop = e.cy * k - blockH / 2
        val left = (e.cx - e.side / 2) * k
        val top = qrTop(e) * k
        val side = e.side * k
        val symbolH = symbolHeight(e) * k
        val dark = Paint().apply { color = palette.qrDark; style = Paint.Style.FILL }
        palette.qrTile?.let { tile ->
            // Біла картка під кодом (на чорному тлі) — з полем навколо, щоб сканер бачив межі коду.
            val m = side * 0.06f
            canvas.drawRoundRect(RectF(left - m, top - m, left + side + m, top + symbolH + m), m * 1.2f, m * 1.2f, Paint().apply { color = tile; isAntiAlias = true })
        }
        when (val symbol = symbol(e)) {
            is Symbol.Qr -> drawQr(canvas, symbol.matrix, left, top, side, dark)
            is Symbol.Bars -> drawBars(canvas, symbol.modules, left, top, side, symbolH, dark)
            null -> Unit
        }
        if (e.caption) {
            val letters = captionLetters(e.side)
            val text = TextPaint().apply { color = palette.ink; isAntiAlias = true; typeface = face(); textSize = letters * scale() * k }
            val width = ((e.side + letters * 2) * k).toInt().coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(e.title, 0, e.title.length, text, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .setLineSpacing(0f, 0.85f)
                .build()
            canvas.save()
            canvas.translate(left + side / 2 - width / 2f, top + symbolH + letters * 0.45f * k)
            layout.draw(canvas)
            canvas.restore()
        }
        val seed = e.id * 7 + 3
        val radius = pad * 1.6f * k
        when (e.frame) {
            QrFrame.None -> Unit
            QrFrame.Pencil, QrFrame.Double, QrFrame.Dashed, QrFrame.Corners ->
                drawOutline(canvas, e.frame, blockLeft, blockTop, blockW, blockH, radius, pad * 0.45f * k, seed, k, ink)
            QrFrame.ScanMe -> {
                pencil(canvas, blockLeft, blockTop, blockW, blockH, radius, seed, k, 0.45f, ink)
                // Плашка знизу: залита чорнилом, напис — кольором паперу, шрифтом від руки.
                val bh = bannerHeight(e) * k
                val bTop = blockTop + blockH - pad * k - bh
                val bRect = RectF(blockLeft + pad * k, bTop, blockLeft + blockW - pad * k, bTop + bh)
                canvas.drawRoundRect(bRect, bh * 0.3f, bh * 0.3f, ink)
                val tp = Paint().apply { color = palette.paper; isAntiAlias = true; typeface = face(); textAlign = Paint.Align.CENTER; textSize = bh * 0.62f }
                var label = e.banner
                // Довгий напис — дрібніше, щоб уліз у плашку.
                val maxW = bRect.width() * 0.92f
                val w = tp.measureText(label)
                if (w > maxW) tp.textSize *= maxW / w
                if (label.isEmpty()) label = " "
                val fm = tp.fontMetrics
                canvas.drawText(label, bRect.centerX(), bRect.centerY() - (fm.ascent + fm.descent) / 2f, tp)
            }
        }
    }

    /**
     * Контур рамки олівцем навколо прямокутника — однаково для рамки коду й рамки всього аркуша: олівцем, подвійна
     * ([gap] — відступ другої лінії), пунктир, кутики.
     */
    private fun drawOutline(canvas: Canvas, style: QrFrame, left: Float, top: Float, w: Float, h: Float, radius: Float, gap: Float, seed: Int, k: Float, ink: Paint) {
        when (style) {
            QrFrame.Pencil, QrFrame.ScanMe -> pencil(canvas, left, top, w, h, radius, seed, k, 0.45f, ink)
            QrFrame.Double -> {
                pencil(canvas, left, top, w, h, radius, seed, k, 0.45f, ink)
                pencil(canvas, left + gap, top + gap, w - 2 * gap, h - 2 * gap, radius * 0.7f, seed + 101, k, 0.3f, ink)
            }
            QrFrame.Dashed -> {
                // Пунктир олівцем: той самий контур, порізаний на короткі штрихи (кожен загострений на кінцях).
                val points = pencilLoop(Size(w, h), radius, seed, 0.3f * k).map { Offset(it.x + left, it.y + top) }
                val dash = 2.4f * k
                var run = ArrayList<Offset>()
                var len = 0f
                var draw = true
                for (i in 1 until points.size) {
                    if (draw) run.add(points[i - 1])
                    len += (points[i] - points[i - 1]).getDistance()
                    if (len >= dash * (if (draw) 1f else 0.7f)) {
                        if (draw && run.size > 1) canvas.drawPath(taperedPath(run + points[i], 0.45f * k).asAndroidPath(), ink)
                        run = ArrayList()
                        len = 0f
                        draw = !draw
                    }
                }
            }
            QrFrame.Corners -> {
                // Лише кутики, як рамка видошукача.
                val arm = minOf(w, h) * 0.22f
                val r = radius * 0.6f
                val corners = listOf(
                    listOf(Offset(left, top + arm), Offset(left, top + r), Offset(left + r, top), Offset(left + arm, top)),
                    listOf(Offset(left + w - arm, top), Offset(left + w - r, top), Offset(left + w, top + r), Offset(left + w, top + arm)),
                    listOf(Offset(left + w, top + h - arm), Offset(left + w, top + h - r), Offset(left + w - r, top + h), Offset(left + w - arm, top + h)),
                    listOf(Offset(left + arm, top + h), Offset(left + r, top + h), Offset(left, top + h - r), Offset(left, top + h - arm)),
                )
                corners.forEach { c -> canvas.drawPath(taperedPath(densify(c), 0.7f * k).asAndroidPath(), ink) }
            }
            QrFrame.None -> Unit
        }
    }

    /** Ламана, густо розбита на точки (щоб штрих рівно тоншав до кінців). */
    private fun densify(corners: List<Offset>): List<Offset> = buildList {
        add(corners.first())
        for (i in 1 until corners.size) {
            val a = corners[i - 1]
            val b = corners[i]
            for (s in 1..10) add(a + (b - a) * (s / 10f))
        }
    }

    private fun drawText(canvas: Canvas, k: Float, e: Element.Text, ink: Paint) {
        val paint = Paint(ink).apply { typeface = face(); textSize = e.letters * k; textAlign = Paint.Align.CENTER }
        val fm = paint.fontMetrics
        canvas.drawText(e.text, e.cx * k, e.cy * k - (fm.ascent + fm.descent) / 2f, paint)
    }

    private fun drawIcon(canvas: Canvas, k: Float, e: Element.Icon, ink: Paint) {
        val unit = e.side * k / 24f
        val ox = (e.cx - e.side / 2) * k
        val oy = (e.cy - e.side / 2) * k
        glyphStrokes(e.sticker.glyph()).forEach { stroke ->
            val path = taperedPath(stroke.map { Offset(ox + it.x * unit, oy + it.y * unit) }, 2.1f * unit).asAndroidPath()
            canvas.drawPath(path, ink)
        }
    }

    /** Рамка олівцем [w]×[h] з лівим верхнім кутом ([x], [y]); товщина — в мм ([widthMm]). */
    private fun pencil(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, radius: Float, seed: Int, k: Float, widthMm: Float, ink: Paint) {
        val points = pencilLoop(Size(w, h), radius, seed, 0.35f * k).map { Offset(it.x + x, it.y + y) }
        canvas.drawPath(taperedPath(points, widthMm * k).asAndroidPath(), ink)
    }

    // ——— друк і «Поділитися» ———

    /** PDF: [copies] однакових наклейок, кожна — окрема сторінка точного розміру. */
    /**
     * PDF: [copies] однакових наклейок, кожна — окрема сторінка. [paperMm] — папір, вибраний у вікні друку (ширина ×
     * висота, мм): сторінка — цього розміру, а наклейка — по центру в справжніх міліметрах, без масштабування (код
     * точно того розміру, що на екрані). Якщо наклейка більша за папір — зменшуємо, щоб умістилась. null — сторінка
     * розміром із саму наклейку.
     */
    fun writePdf(out: FileOutputStream, design: LabelDesign, copies: Int, paperMm: Pair<Float, Float>? = null) {
        val doc = PdfDocument()
        try {
            val landscape = design.size.widthMm > design.size.heightMm
            // Папір повертаємо так само, як наклейку (принтер сам знає, як подавати рулон).
            val (pw, ph) = paperMm?.let { (a, b) -> if ((a > b) == landscape) a to b else b to a }
                ?: (design.size.widthMm to design.size.heightMm)
            val w = (pw * PDF_POINTS_PER_MM).toInt()
            val h = (ph * PDF_POINTS_PER_MM).toInt()
            val fit = minOf(1f, pw / design.size.widthMm, ph / design.size.heightMm)
            val k = PDF_POINTS_PER_MM * fit
            repeat(copies.coerceAtLeast(1)) { p ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, p + 1).create())
                page.canvas.translate((w - design.size.widthMm * k) / 2f, (h - design.size.heightMm * k) / 2f)
                draw(page.canvas, k, design, LabelPalette.Print)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /** Друк через службу друку Android (Zebra чи будь-який принтер): розмір паперу — розмір наклейки. */
    fun print(context: Context, jobName: String, design: LabelDesign, copies: Int) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val mils = { mm: Float -> (mm / 25.4f * 1000f).toInt() }
        val size = design.size
        val media = PrintAttributes.MediaSize(
            "qr_${size.widthMm.toInt()}x${size.heightMm.toInt()}",
            "${fmt(size.widthMm)}×${fmt(size.heightMm)} mm",
            mils(size.widthMm),
            mils(size.heightMm),
        )
        val attributes = PrintAttributes.Builder()
            .setMediaSize(media)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()
        manager.print(jobName, Adapter(jobName, design, copies), attributes)
    }

    private inner class Adapter(val name: String, val design: LabelDesign, val copies: Int) : PrintDocumentAdapter() {
        @Volatile private var paperMm: Pair<Float, Float>? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("$name.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(copies.coerceAtLeast(1))
                .build()
            // Папір, який зараз вибраний у вікні друку (у принтера Zebra — розмір його рулону).
            paperMm = newAttributes.mediaSize?.let { it.widthMils * 25.4f / 1000f to it.heightMils * 25.4f / 1000f }
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            try {
                FileOutputStream(destination.fileDescriptor).use { writePdf(it, design, copies, paperMm) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }

    /** Картинка наклейки (PNG 300 dpi) — для «Поділитися» в будь-яку програму. */
    fun renderPng(context: Context, design: LabelDesign): Uri {
        val dir = File(context.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        val bitmap = renderBitmap(context, design)
        val file = File(dir, "qr_label.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /**
     * Картинка наклейки: звичайна — 300 dpi (для друку); «Екран телефону» — точно в пікселях екрана, щоб на телефоні
     * відкривалась на весь екран без смуг і розмиття.
     */
    fun renderBitmap(context: Context, design: LabelDesign): Bitmap {
        val pxPerMm = if (design.size.screen) {
            val (w, h) = screenPixels(context)
            minOf(w, h).toFloat() / design.size.widthMm
        } else {
            PNG_PX_PER_MM
        }
        val w = (design.size.widthMm * pxPerMm).toInt().coerceAtLeast(1)
        val h = (design.size.heightMm * pxPerMm).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // Картинка — як на екрані: «Екран телефону» — чорна з білою карткою, звичайна наклейка — біла.
        draw(Canvas(bitmap), pxPerMm, design, if (design.size.screen) LabelPalette.Phone else LabelPalette.Print)
        return bitmap
    }

    /**
     * Зберегти картинку наклейки в галерею (Фото → Pictures/QRaft). Android 10+ — без жодних дозволів.
     * true — збережено.
     */
    fun saveToGallery(context: Context, design: LabelDesign, name: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return false
        val bitmap = renderBitmap(context, design)
        return try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/QRaft")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: Exception) {
            false
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        /** Справжні пікселі екрана (з рядком стану й навігацією). */
        fun screenPixels(context: Context): Pair<Int, Int> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val wm = context.getSystemService(android.view.WindowManager::class.java)
                val b = wm.maximumWindowMetrics.bounds
                return b.width() to b.height()
            }
            val m = context.resources.displayMetrics
            return m.widthPixels to m.heightPixels
        }

        private const val PDF_POINTS_PER_MM = 72f / 25.4f
        private const val PNG_PX_PER_MM = 300f / 25.4f

        /** Шрифт від руки дрібніший на око (як у «Аркуші») — підпис трохи більший. */
        private const val HAND_SCALE = 1.25f

        /** Рамка аркуша — за 1 мм від краю (елементи тримаються ще далі, див. Placement.EDGE). */
        private const val BORDER_INSET = 1f

        private const val MATRIX_CACHE = 64

        @Volatile private var shared: LabelRenderer? = null

        /** Один на всю програму: шрифт вантажиться раз, а закодовані QR-коди кешуються для екрана, друку й зразків. */
        fun create(context: Context): LabelRenderer = shared ?: synchronized(this) {
            shared ?: LabelRenderer(ResourcesCompat.getFont(context.applicationContext, R.font.caveat_semibold) ?: Typeface.DEFAULT).also { shared = it }
        }

        /** Модулі коду квадратиками; сусідні в рядку — одним прямокутником (без тонких щілин між ними). */
        fun drawQr(canvas: Canvas, m: QrMatrix, left: Float, top: Float, side: Float, paint: Paint) {
            val cell = side / m.size
            for (y in 0 until m.size) {
                var x = 0
                while (x < m.size) {
                    if (!m[x, y]) { x++; continue }
                    val start = x
                    while (x < m.size && m[x, y]) x++
                    canvas.drawRect(left + start * cell, top + y * cell, left + x * cell, top + (y + 1) * cell + 0.01f, paint)
                }
            }
        }

        /** Смуги штрихкоду в прямокутнику [w]×[h] з тихими зонами по боках. */
        fun drawBars(canvas: Canvas, bars: BooleanArray, left: Float, top: Float, w: Float, h: Float, paint: Paint) {
            val cell = w / (bars.size + 2 * Symbol.BAR_QUIET)
            var x = 0
            while (x < bars.size) {
                if (!bars[x]) { x++; continue }
                val start = x
                while (x < bars.size && bars[x]) x++
                canvas.drawRect(left + (Symbol.BAR_QUIET + start) * cell, top, left + (Symbol.BAR_QUIET + x) * cell, top + h, paint)
            }
        }

        fun shareImage(context: Context, chooserTitle: String, uri: Uri) {
            val intent = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = android.content.ClipData.newRawUri(null, uri)
            context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }

        fun shareText(context: Context, chooserTitle: String, text: String) {
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(intent, chooserTitle))
        }

        fun fmt(mm: Float): String = if (mm % 1f == 0f) mm.toInt().toString() else "%.1f".format(mm)
    }
}
