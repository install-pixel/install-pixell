package com.blacklaunch.diary.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.LruCache
import java.io.ByteArrayOutputStream

/** Завантаження фото, зменшення й обробка під тему (у фоновому потоці). */
object Photos {

    /** Найбільша сторона кадру, що обробляється фільтром (досить для екрана телефона). */
    private const val PROCESS_SIDE = 1080

    /** Прочитати вибране фото з урахуванням повороту й зменшити до [maxSide]. */
    fun load(context: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val s = info.size
                val k = maxSide.toFloat() / maxOf(s.width, s.height)
                if (k < 1f) decoder.setTargetSize((s.width * k).toInt().coerceAtLeast(1), (s.height * k).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val rotation = context.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            scaleDown(if (rotation != 0f) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true) else bmp, maxSide)
        }
    }.getOrNull()

    fun scaleDown(bmp: Bitmap, maxSide: Int): Bitmap {
        val k = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        if (k >= 1f) return bmp
        return Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
    }

    fun toJpeg(bmp: Bitmap, quality: Int = 88): ByteArray =
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    fun decode(bytes: ByteArray): Bitmap? = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    /**
     * Фото під тему: [glass] — «Скло», інакше «Аркуш» (олівцевий ескіз). [onPaper] — малюнок олівцем із прозорістю, щоб
     * лягав просто на фактуру паперу (фото дня); обкладинка зберігається у JPEG — там без прозорості.
     */
    fun stylize(src: Bitmap, glass: Boolean, onPaper: Boolean = false): Bitmap {
        val bmp = scaleDown(src, PROCESS_SIDE).let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = if (glass) Stylize.glass(px, w, h) else Stylize.pencil(px, w, h, transparent = onPaper)
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Уже оброблені фото днів — з пам'яті, а не рахуються заново при кожному гортанні (THEMES.md, розділ 11). */
    val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
}
