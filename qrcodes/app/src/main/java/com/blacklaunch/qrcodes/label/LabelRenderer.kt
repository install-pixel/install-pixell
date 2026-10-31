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
 * Кольори наклейки. На екрані — як уся програма: чорний «аркуш», білий олівець, код на світлому папірці.
 * На папері — навпаки: біле тло, чорне чорнило (так читає будь-який сканер і друкує Zebra).
 */
data class LabelPalette(val paper: Int, val ink: Int, val qrTile: Int?, val qrDark: Int) {
    companion object {
        val Screen = LabelPalette(paper = 0xFF000000.toInt(), ink = 0xFFEDEDED.toInt(), qrTile = 0xFFEDEDED.toInt(), qrDark = 0xFF000000.toInt())
        val Print = LabelPalette(paper = 0xFFFFFFFF.toInt(), ink = 0xFF000000.toInt(), qrTile = null, qrDark = 0xFF000000.toInt())
    }
}

fun Sticker.glyph(): Glyph = when (this) {
    Sticker.Camera -> Glyph.Photo
    Sticker.Box -> Glyph.Box
    Sticker.Arrow -> Glyph.Arrow
    Sticker.Star -> Glyph.StarEmpty
    Sticker.Wifi -> Glyph.Wifi
    Sticker.Warning -> Glyph.Warning
    Sticker.Phone -> Glyph.Phone
    Sticker.Scan -> Glyph.ScanQr
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
        val caption = captionArea(e)
        val left = (e.cx - e.side / 2) * k
        val top = (e.cy - (e.side + caption) / 2) * k
        val side = e.side * k
        val m = matrix(e.content)
        palette.qrTile?.let { tile ->
            // На екрані — світлий папірець під кодом (на чорному коду інакше не видно).
            val margin = if (m != null) side / m.size else 0f
            canvas.drawRoundRect(RectF(left - margin, top - margin, left + side + margin, top + side + margin), margin * 1.5f, margin * 1.5f, Paint().apply { color = tile; isAntiAlias = true })
        }
        if (m != null) drawQr(canvas, m, left, top, side, Paint().apply { color = palette.qrDark; style = Paint.Style.FILL })
        if (e.caption) {
            val letters = captionLetters(e.side)
            val text = TextPaint().apply { color = palette.ink; isAntiAlias = true; typeface = hand; textSize = letters * HAND_SCALE * k }
            val width = ((e.side + letters * 4) * k).toInt().coerceAtLeast(1)
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
        val pad = framePad(e) * k
        if (e.frame != QrFrame.None) {
            val w = side + 2 * pad
            val h = side + caption * k + 2 * pad
            pencil(canvas, left - pad, top - pad, w, h, pad * 1.6f, e.id * 7 + 3, k, 0.45f, ink)
            if (e.frame == QrFrame.Double) {
                val d = pad * 0.45f
                pencil(canvas, left - pad + d, top - pad + d, w - 2 * d, h - 2 * d, pad * 1.1f, e.id * 7 + 104, k, 0.3f, ink)
            }
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
