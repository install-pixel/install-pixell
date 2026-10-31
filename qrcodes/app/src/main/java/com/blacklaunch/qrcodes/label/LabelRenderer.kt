package com.blacklaunch.qrcodes.label

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
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
import androidx.core.content.FileProvider
import com.blacklaunch.qrcodes.qr.QrMatrix
import java.io.File
import java.io.FileOutputStream

/** Код на наклейці: що закодовано і що підписати. */
data class LabelItem(val content: String, val title: String)

/**
 * Малює наклейки: однаково для попереднього перегляду, друку (PDF — векторно, точний розмір у мм) і картинки
 * «Поділитися» (PNG 300 dpi). Завжди чорне на білому — так читає будь-який сканер.
 */
object LabelRenderer {
    private const val PDF_POINTS_PER_MM = 72f / 25.4f
    private const val PNG_PX_PER_MM = 300f / 25.4f

    fun drawPage(canvas: Canvas, pxPerMm: Float, layout: LabelLayout, page: Int, items: List<LabelItem>, matrices: List<QrMatrix?>) {
        canvas.drawColor(Color.WHITE)
        val ink = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL; isAntiAlias = false }
        val text = TextPaint().apply {
            color = Color.BLACK
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = layout.captionMm * pxPerMm
        }
        layout.pages.getOrNull(page)?.forEach { placed ->
            val x = placed.xMm * pxPerMm
            val y = placed.yMm * pxPerMm
            val side = layout.qrMm * pxPerMm
            matrices.getOrNull(placed.item)?.let { drawQr(canvas, it, x, y, side, ink) }
            if (layout.settings.caption) {
                val title = items.getOrNull(placed.item)?.title.orEmpty()
                // Підпис трохи ширший за код (заходить у проміжок), до двох рядків, по центру.
                val width = ((layout.cellWidthMm + layout.gapMm * 0.8f) * pxPerMm).toInt().coerceAtLeast(1)
                val caption = StaticLayout.Builder.obtain(title, 0, title.length, text, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setIncludePad(false)
                    .build()
                canvas.save()
                canvas.translate(x + side / 2f - width / 2f, y + side + layout.captionMm * 0.6f * pxPerMm)
                caption.draw(canvas)
                canvas.restore()
            }
        }
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

    private fun matrices(items: List<LabelItem>) = items.map { QrMatrix.encode(it.content) }

    /** PDF з наклейками точного розміру. */
    fun writePdf(out: FileOutputStream, layout: LabelLayout, items: List<LabelItem>) {
        val m = matrices(items)
        val doc = PdfDocument()
        try {
            val w = (layout.widthMm * PDF_POINTS_PER_MM).toInt()
            val h = (layout.heightMm * PDF_POINTS_PER_MM).toInt()
            for (p in 0 until layout.pageCount) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, p + 1).create())
                drawPage(page.canvas, PDF_POINTS_PER_MM, layout, p, items, m)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /** Друк через службу друку Android (Zebra, будь-який принтер): розмір паперу — розмір наклейки. */
    fun print(context: Context, jobName: String, layout: LabelLayout, items: List<LabelItem>) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val mils = { mm: Float -> (mm / 25.4f * 1000f).toInt() }
        val media = PrintAttributes.MediaSize(
            "qr_${layout.widthMm.toInt()}x${layout.heightMm.toInt()}",
            "${fmt(layout.widthMm)}×${fmt(layout.heightMm)} mm",
            mils(layout.widthMm),
            mils(layout.heightMm),
        )
        val attributes = PrintAttributes.Builder()
            .setMediaSize(media)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()
        manager.print(jobName, Adapter(jobName, layout, items), attributes)
    }

    private class Adapter(val name: String, val layout: LabelLayout, val items: List<LabelItem>) : PrintDocumentAdapter() {
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
                .setPageCount(layout.pageCount)
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
                FileOutputStream(destination.fileDescriptor).use { writePdf(it, layout, items) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }

    /** Картинки наклейок (PNG 300 dpi) — для «Поділитися» в будь-яку програму. */
    fun renderPngs(context: Context, layout: LabelLayout, items: List<LabelItem>): List<android.net.Uri> {
        val dir = File(context.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        val m = matrices(items)
        val w = (layout.widthMm * PNG_PX_PER_MM).toInt().coerceAtLeast(1)
        val h = (layout.heightMm * PNG_PX_PER_MM).toInt().coerceAtLeast(1)
        val authority = "${context.packageName}.files"
        val uris = (0 until layout.pageCount).map { p ->
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawPage(Canvas(bitmap), PNG_PX_PER_MM, layout, p, items, m)
            val file = File(dir, "qr_${p + 1}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            FileProvider.getUriForFile(context, authority, file)
        }
        return uris
    }

    fun shareImages(context: Context, chooserTitle: String, uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = "image/png"
        intent.clipData = android.content.ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    /** Поділитися самим текстом коду. */
    fun shareText(context: Context, chooserTitle: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun fmt(mm: Float): String = if (mm % 1f == 0f) mm.toInt().toString() else "%.1f".format(mm)
}
