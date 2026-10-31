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
import com.blacklaunch.qrcodes.qr.QrMatrix
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
data class LabelPalette(val paper: Int, val ink: Int, val qrTile: Int?, val qrDark: Int) {
    companion object {
        val Screen = LabelPalette(paper = 0xFFF4F4F2.toInt(), ink = 0xFF111111.toInt(), qrTile = null, qrDark = 0xFF000000.toInt())
        val Print = LabelPalette(paper = 0xFFFFFFFF.toInt(), ink = 0xFF000000.toInt(), qrTile = null, qrDark = 0xFF000000.toInt())
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
 * Текст і підписи — шрифтом від руки (Caveat), рамки й значки — олівцем.
 */
class LabelRenderer(private val hand: Typeface) {
    private val measurePaint = Paint().apply { typeface = hand; textSize = 100f; isAntiAlias = true }
    private val matrices = HashMap<String, QrMatrix?>()

    /** Ширина тексту в мм при висоті літер [letters] мм. */
    fun textWidth(text: String, letters: Float): Float = synchronized(measurePaint) { measurePaint.measureText(text) } * letters / 100f

    private fun matrix(content: String): QrMatrix? = synchronized(matrices) { matrices.getOrPut(content) { QrMatrix.encode(content) } }

    fun draw(canvas: Canvas, pxPerMm: Float, design: LabelDesign, palette: LabelPalette) {
        canvas.drawColor(palette.paper)
        val ink = Paint().apply { color = palette.ink; style = Paint.Style.FILL; isAntiAlias = true }
        if (design.border) {
            val inset = 1.5f * pxPerMm
            pencil(canvas, inset, inset, design.size.widthMm * pxPerMm - 2 * inset, design.size.heightMm * pxPerMm - 2 * inset, 4f * pxPerMm, 997, pxPerMm, 0.5f, ink)
        }
        design.elements.forEach { e ->
            when (e) {
                is Element.Qr -> drawQrElement(canvas, pxPerMm, e, palette, ink)
                is Element.Text -> drawText(canvas, pxPerMm, e, ink)
                is Element.Icon -> drawIcon(canvas, pxPerMm, e, ink)
            }
        }
    }

    private fun drawQrElement(canvas: Canvas, k: Float, e: Element.Qr, palette: LabelPalette, ink: Paint) {
        val pad = framePad(e)
        val caption = captionArea(e)
        val banner = bannerArea(e)
        val blockW = (e.side + 2 * pad) * k
        val blockH = qrBlockHeight(e) * k
        val blockLeft = e.cx * k - blockW / 2
        val blockTop = e.cy * k - blockH / 2
        val left = (e.cx - e.side / 2) * k
        val top = qrTop(e) * k
        val side = e.side * k
        val m = matrix(e.content)
        palette.qrTile?.let { tile ->
            val margin = if (m != null) side / m.size else 0f
            canvas.drawRoundRect(RectF(left - margin, top - margin, left + side + margin, top + side + margin), margin * 1.5f, margin * 1.5f, Paint().apply { color = tile; isAntiAlias = true })
        }
        if (m != null) drawQr(canvas, m, left, top, side, Paint().apply { color = palette.qrDark; style = Paint.Style.FILL })
        if (e.caption) {
            val letters = captionLetters(e.side)
            val text = TextPaint().apply { color = palette.ink; isAntiAlias = true; typeface = hand; textSize = letters * HAND_SCALE * k }
            val width = ((e.side + letters * 2) * k).toInt().coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(e.title, 0, e.title.length, text, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .setLineSpacing(0f, 0.85f)
                .build()
            canvas.save()
            canvas.translate(left + side / 2 - width / 2f, top + side + letters * 0.45f * k)
            layout.draw(canvas)
            canvas.restore()
        }
        val seed = e.id * 7 + 3
        val radius = pad * 1.6f * k
        when (e.frame) {
            QrFrame.None -> Unit
            QrFrame.Pencil -> pencil(canvas, blockLeft, blockTop, blockW, blockH, radius, seed, k, 0.45f, ink)
            QrFrame.Double -> {
                pencil(canvas, blockLeft, blockTop, blockW, blockH, radius, seed, k, 0.45f, ink)
                val d = pad * 0.45f * k
                pencil(canvas, blockLeft + d, blockTop + d, blockW - 2 * d, blockH - 2 * d, pad * 1.1f * k, seed + 101, k, 0.3f, ink)
            }
            QrFrame.Dashed -> {
                // Пунктир олівцем: той самий контур, порізаний на короткі штрихи (кожен загострений на кінцях).
                val points = pencilLoop(Size(blockW, blockH), radius, seed, 0.3f * k).map { Offset(it.x + blockLeft, it.y + blockTop) }
                val dash = (2.4f * k)
                var run = ArrayList<Offset>()
                var len = 0f
                var draw = true
                for (i in 1 until points.size) {
                    val step = (points[i] - points[i - 1]).getDistance()
                    if (draw) run.add(points[i - 1])
                    len += step
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
                val arm = minOf(blockW, blockH) * 0.22f
                val r = radius * 0.6f
                val corners = listOf(
                    listOf(Offset(blockLeft, blockTop + arm), Offset(blockLeft, blockTop + r), Offset(blockLeft + r, blockTop), Offset(blockLeft + arm, blockTop)),
                    listOf(Offset(blockLeft + blockW - arm, blockTop), Offset(blockLeft + blockW - r, blockTop), Offset(blockLeft + blockW, blockTop + r), Offset(blockLeft + blockW, blockTop + arm)),
                    listOf(Offset(blockLeft + blockW, blockTop + blockH - arm), Offset(blockLeft + blockW, blockTop + blockH - r), Offset(blockLeft + blockW - r, blockTop + blockH), Offset(blockLeft + blockW - arm, blockTop + blockH)),
                    listOf(Offset(blockLeft + arm, blockTop + blockH), Offset(blockLeft + r, blockTop + blockH), Offset(blockLeft, blockTop + blockH - r), Offset(blockLeft, blockTop + blockH - arm)),
                )
                corners.forEach { c -> canvas.drawPath(taperedPath(densify(c), 0.7f * k).asAndroidPath(), ink) }
            }
            QrFrame.ScanMe -> {
                pencil(canvas, blockLeft, blockTop, blockW, blockH, radius, seed, k, 0.45f, ink)
                // Плашка знизу: залита чорнилом, напис — кольором паперу, шрифтом від руки.
                val bh = banner * k
                val bTop = blockTop + blockH - pad * k - bh
                val bRect = RectF(blockLeft + pad * k, bTop, blockLeft + blockW - pad * k, bTop + bh)
                canvas.drawRoundRect(bRect, bh * 0.3f, bh * 0.3f, ink)
                val tp = Paint().apply { color = palette.paper; isAntiAlias = true; typeface = hand; textAlign = Paint.Align.CENTER; textSize = bh * 0.62f }
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
        val paint = Paint(ink).apply { typeface = hand; textSize = e.letters * k; textAlign = Paint.Align.CENTER }
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
    fun writePdf(out: FileOutputStream, design: LabelDesign, copies: Int) {
        val doc = PdfDocument()
        try {
            val w = (design.size.widthMm * PDF_POINTS_PER_MM).toInt()
            val h = (design.size.heightMm * PDF_POINTS_PER_MM).toInt()
            repeat(copies.coerceAtLeast(1)) { p ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, p + 1).create())
                draw(page.canvas, PDF_POINTS_PER_MM, design, LabelPalette.Print)
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
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            try {
                FileOutputStream(destination.fileDescriptor).use { writePdf(it, design, copies) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }

    /** Картинка наклейки (PNG 300 dpi) — для «Поділитися» в будь-яку програму. */
    fun renderPng(context: Context, design: LabelDesign): Uri {
        val dir = File(context.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        val w = (design.size.widthMm * PNG_PX_PER_MM).toInt().coerceAtLeast(1)
        val h = (design.size.heightMm * PNG_PX_PER_MM).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), PNG_PX_PER_MM, design, LabelPalette.Print)
        val file = File(dir, "qr_label.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    companion object {
        private const val PDF_POINTS_PER_MM = 72f / 25.4f
        private const val PNG_PX_PER_MM = 300f / 25.4f

        /** Шрифт від руки дрібніший на око (як у «Аркуші») — підпис трохи більший. */
        private const val HAND_SCALE = 1.25f

        fun create(context: Context): LabelRenderer =
            LabelRenderer(ResourcesCompat.getFont(context, R.font.caveat_semibold) ?: Typeface.DEFAULT)

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
