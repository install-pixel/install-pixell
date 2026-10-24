package com.blacklaunch.launcher.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Іконки вантажаться лише для показаних результатів і кешуються в пам'яті.
 * У сірому режимі: одноколірна версія значка (Android 13+) на об'ємному кружечку,
 * а якщо її немає — знебарвлений звичайний значок.
 */
class AppIconCache(
    private val repository: AppRepository,
    private val density: Int,
    private val sizePx: Int,
    private val monochrome: () -> Boolean,
) {
    private val cache = LruCache<String, ImageBitmap>(MAX_ICONS)

    suspend fun load(app: AppInfo): ImageBitmap? {
        val mono = monochrome()
        val key = "${app.key}#$mono"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val drawable = repository.icon(app, density) ?: return@withContext null
            val bitmap = if (mono) monochromeBitmap(drawable) else render(drawable)
            bitmap.asImageBitmap().also { cache.put(key, it) }
        }
    }

    private fun monochromeBitmap(drawable: Drawable): Bitmap {
        val glyph = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (drawable as? AdaptiveIconDrawable)?.monochrome
        } else {
            null
        }
        // Деякі програми віддають «одноколірний» шар-заглушку (суцільний квадрат) — тоді звичайний сірий значок.
        return if (glyph != null && looksLikeGlyph(glyph)) themedIcon(glyph) else desaturated(drawable)
    }

    /** Справжній силует займає частину кружечка; суцільна пляма чи порожнеча — ні. */
    private fun looksLikeGlyph(glyph: Drawable): Boolean {
        val probe = createBitmap(PROBE, PROBE)
        val inset = -(PROBE * 0.25f).toInt()
        glyph.mutate().apply {
            setBounds(inset, inset, PROBE - inset, PROBE - inset)
            draw(Canvas(probe))
        }
        val radius = PROBE / 2f
        var inside = 0
        var filled = 0
        var corners = 0
        var cornersFilled = 0
        for (y in 0 until PROBE) for (x in 0 until PROBE) {
            val dx = x - radius + 0.5f
            val dy = y - radius + 0.5f
            val opaque = (probe.getPixel(x, y) ushr 24) > 128
            if (dx * dx + dy * dy > radius * radius) {
                // Кути поза кружечком — поза «безпечною зоною»: справжній силует туди не заходить.
                corners++
                if (opaque) cornersFilled++
            } else {
                inside++
                if (opaque) filled++
            }
        }
        probe.recycle()
        val share = filled.toFloat() / inside
        val spill = cornersFilled.toFloat() / corners
        return share in MIN_GLYPH..MAX_GLYPH && spill <= MAX_SPILL
    }

    private fun themedIcon(glyph: Drawable): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val radius = sizePx / 2f
        val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(radius * 0.8f, radius * 0.6f, radius * 1.3f, KNOB_LIGHT, KNOB_DARK, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(radius, radius, radius, knob)
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = sizePx / 36f
            color = RIM
        }
        canvas.drawCircle(radius, radius, radius - rim.strokeWidth, rim)
        // Шар одноколірного значка займає 108dp із «безпечною зоною» 72dp, тому його розширюємо.
        val inset = -(sizePx * 0.25f).toInt()
        // Силует — лише всередині кружечка, щоб нічого не вилазило за край.
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(radius, radius, radius, Path.Direction.CW) })
        glyph.mutate().apply {
            colorFilter = PorterDuffColorFilter(GLYPH, PorterDuff.Mode.SRC_IN)
            setBounds(inset, inset, sizePx - inset, sizePx - inset)
            draw(canvas)
        }
        canvas.restore()
        return bitmap
    }

    private fun desaturated(drawable: Drawable): Bitmap {
        val source = render(drawable)
        val bitmap = createBitmap(sizePx, sizePx)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            alpha = 220
        }
        Canvas(bitmap).drawBitmap(source, 0f, 0f, paint)
        return bitmap
    }

    /**
     * Значок у потрібному розмірі. Деякі «живі» значки виробників (наприклад, Калькулятор OnePlus)
     * ігнорують задані межі й малюють себе у власному розмірі — тоді видно лише кут значка.
     * Тому малюємо у власному розмірі значка й лише потім зменшуємо.
     */
    private fun render(drawable: Drawable): Bitmap {
        if (drawable is AdaptiveIconDrawable) return renderAdaptive(drawable)
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w <= 0 || h <= 0 || (w == sizePx && h == sizePx)) return drawable.toBitmap(sizePx, sizePx)
        val full = drawable.toBitmap(w, h)
        // Не звільняємо `full`: для звичайних значків це може бути спільний бітмап системи.
        return Bitmap.createScaledBitmap(full, sizePx, sizePx, true)
    }

    /**
     * Двошаровий значок складаємо самі, за стандартом Android: шари 108dp, видима частина 72dp
     * (по 18dp запасу з кожного боку), обрізання — заокруглений квадрат. Власне малювання OxygenOS
     * для сторонньої програми масштабує шари неправильно — видно лише збільшений кут значка.
     */
    private fun renderAdaptive(icon: AdaptiveIconDrawable): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val corner = sizePx * CORNER
        canvas.clipPath(Path().apply { addRoundRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), corner, corner, Path.Direction.CW) })
        val extra = sizePx / 4
        listOfNotNull(icon.background, icon.foreground).forEach { layer ->
            layer.setBounds(-extra, -extra, sizePx + extra, sizePx + extra)
            layer.draw(canvas)
        }
        return bitmap
    }

    private companion object {
        /** Заокруглення кутів складеного значка (частка розміру). */
        const val CORNER = 0.24f
        const val MAX_ICONS = 200
        const val KNOB_LIGHT = 0xFF3A3A3A.toInt()
        const val KNOB_DARK = 0xFF141414.toInt()
        const val RIM = 0x33FFFFFF
        const val GLYPH = 0xFFD6D6D6.toInt()
        const val PROBE = 48
        const val MIN_GLYPH = 0.02f
        const val MAX_GLYPH = 0.6f
        const val MAX_SPILL = 0.05f
    }
}
