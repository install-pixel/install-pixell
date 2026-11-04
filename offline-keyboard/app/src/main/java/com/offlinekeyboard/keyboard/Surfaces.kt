package com.offlinekeyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** The theme's font for text views (Exo 2, Caveat), or null for the system font. */
fun KeyboardTheme.typeface(context: Context): Typeface? =
    if (fontRes == 0) null else runCatching { ResourcesCompat.getFont(context, fontRes) }.getOrNull()

/** A surface at rest, under a finger, or chosen (an active modifier, the selected popup letter). */
enum class SurfaceState { NORMAL, PRESSED, SELECTED }

/**
 * Draws keys, buttons and popups in the theme's [SurfaceStyle] (THEMES.md, sections 3 and 4):
 *
 * - FLAT: a rounded rectangle of the given colour.
 * - GLASS: black glass lit from above (#1B1B1B → #0D0D0D → #080808) with a 1 dp rim that is bright on top and
 *   fades down; pressed is the round-button glow (#3A3A3A → #101010); chosen has a brighter rim, not a colour.
 * - SHEET: no fill, a rounded frame drawn twice with a pencil, slightly wavy and different for every [seed];
 *   chosen is a denser stroke, pressed a faint graphite shade.
 *
 * Gradients are made once over a 0..1 square and stretched onto each surface; pencil outlines are made once
 * per size and seed. Drawing allocates nothing in the usual case.
 */
class SurfacePainter(private val density: Float) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val matrix = Matrix()
    private val inner = RectF()

    private val glassFill = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xFF1B1B1B.toInt(), 0xFF0D0D0D.toInt(), 0xFF080808.toInt()),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val glassPressed = RadialGradient(0.5f, 0.35f, 0.8f, 0xFF3A3A3A.toInt(), 0xFF101010.toInt(), Shader.TileMode.CLAMP)
    private val glassRim = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0x80FFFFFF.toInt(), 0x24FFFFFF, 0x12FFFFFF, 0x26FFFFFF),
        floatArrayOf(0f, 0.25f, 0.8f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val glassRimChosen = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xB3FFFFFF.toInt(), 0x40FFFFFF, 0x33FFFFFF),
        floatArrayOf(0f, 0.3f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val sheen = LinearGradient(0f, 0f, 0f, 1f, 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)

    private val outlines = object : LinkedHashMap<Long, Pair<Path, Path>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Pair<Path, Path>>) = size > 400
    }

    /**
     * Draws one surface in [rect]. [color] is used by FLAT; [inkColor] is the pencil colour of SHEET (blue for
     * the Enter key, otherwise the text colour). [sheen] adds the soft top light of large glass surfaces.
     */
    fun draw(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        style: SurfaceStyle,
        color: Int,
        state: SurfaceState = SurfaceState.NORMAL,
        seed: Int = 0,
        inkColor: Int = 0xFFEDEDED.toInt(),
        sheen: Boolean = false,
    ) {
        when (style) {
            SurfaceStyle.FLAT -> {
                fill.shader = null
                fill.color = color
                canvas.drawRoundRect(rect, radius, radius, fill)
            }
            SurfaceStyle.GLASS -> drawGlass(canvas, rect, radius, state, sheen)
            SurfaceStyle.SHEET -> drawSheet(canvas, rect, radius, state, seed, inkColor)
        }
    }

    private fun stretch(shader: Shader, rect: RectF, height: Float = rect.height()) {
        matrix.setScale(rect.width(), height)
        matrix.postTranslate(rect.left, rect.top)
        shader.setLocalMatrix(matrix)
    }

    private fun drawGlass(canvas: Canvas, rect: RectF, radius: Float, state: SurfaceState, withSheen: Boolean) {
        val body = if (state == SurfaceState.PRESSED) glassPressed else glassFill
        stretch(body, rect)
        fill.shader = body
        canvas.drawRoundRect(rect, radius, radius, fill)
        if (withSheen) {
            // Soft light inside the top, gone by half height (at most 120 dp).
            stretch(sheen, rect, minOf(rect.height() / 2, 120 * density))
            fill.shader = sheen
            canvas.drawRoundRect(rect, radius, radius, fill)
        }
        fill.shader = null
        val edge = if (state == SurfaceState.SELECTED) glassRimChosen else glassRim
        stretch(edge, rect)
        rim.shader = edge
        val half = rim.strokeWidth / 2
        inner.set(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half)
        canvas.drawRoundRect(inner, radius - half, radius - half, rim)
    }

    private fun drawSheet(canvas: Canvas, rect: RectF, radius: Float, state: SurfaceState, seed: Int, inkColor: Int) {
        if (state == SurfaceState.PRESSED) {
            fill.shader = null
            fill.color = (inkColor and 0x00FFFFFF) or 0x24000000
            canvas.drawRoundRect(rect, radius, radius, fill)
        }
        val chosen = state == SurfaceState.SELECTED
        val w = rect.width()
        val h = rect.height()
        val key = (w.toInt().toLong() shl 40) or (h.toInt().toLong() shl 24) or ((seed and 0xFFFF).toLong() shl 1) or
            (if (chosen) 1L else 0L)
        val (main, ghost) = outlines.getOrPut(key) {
            val width = (if (chosen) 2.2f else 1.6f) * density
            Pencil.tapered(Pencil.loop(w, h, radius, seed, 1.1f * density), width) to
                Pencil.tapered(Pencil.loop(w, h, radius, seed + 101, 1.4f * density), width * 0.55f)
        }
        canvas.save()
        canvas.translate(rect.left, rect.top)
        ink.color = inkColor
        ink.alpha = if (chosen) 255 else 205
        canvas.drawPath(main, ink)
        ink.alpha = if (chosen) 130 else 80
        canvas.drawPath(ghost, ink)
        canvas.restore()
    }
}

/**
 * Pencil outlines of the sheet style, as in the launcher's `Sheet.kt`: a rounded rectangle drawn in one
 * slightly wavy movement that starts somewhere different for every seed and overshoots its start a little,
 * and a stroke that is full in the middle and tapers to a point at both ends.
 */
object Pencil {

    /** Points along a hand-drawn outline of a [w]×[h] rounded rectangle (x, y pairs). */
    fun loop(w: Float, h: Float, radius: Float, seed: Int, wobble: Float): FloatArray {
        if (w <= 0f || h <= 0f) return FloatArray(0)
        val r = radius.coerceAtMost(minOf(w, h) / 2f).coerceAtLeast(0.5f)
        val straightW = w - 2 * r
        val straightH = h - 2 * r
        val arc = (Math.PI / 2 * r).toFloat()
        val perimeter = 2 * straightW + 2 * straightH + 4 * arc
        val out = FloatArray(2)
        fun corner(cx: Float, cy: Float, from: Double, d: Float) {
            val a = from + d / r
            out[0] = cx + r * cos(a).toFloat()
            out[1] = cy + r * sin(a).toFloat()
        }
        fun point(d0: Float) {
            var d = ((d0 % perimeter) + perimeter) % perimeter
            if (d < straightW) { out[0] = r + d; out[1] = 0f; return }
            d -= straightW
            if (d < arc) return corner(w - r, r, -Math.PI / 2, d)
            d -= arc
            if (d < straightH) { out[0] = w; out[1] = r + d; return }
            d -= straightH
            if (d < arc) return corner(w - r, h - r, 0.0, d)
            d -= arc
            if (d < straightW) { out[0] = w - r - d; out[1] = h; return }
            d -= straightW
            if (d < arc) return corner(r, h - r, Math.PI / 2, d)
            d -= arc
            if (d < straightH) { out[0] = 0f; out[1] = h - r - d; return }
            d -= straightH
            corner(r, r, Math.PI, d)
        }
        val random = Random(seed)
        val start = random.nextFloat() * perimeter
        val overlap = perimeter * (0.05f + random.nextFloat() * 0.04f)
        val p1 = random.nextFloat() * 6.28f
        val p2 = random.nextFloat() * 6.28f
        val f1 = 2 * 6.2832f / perimeter
        val f2 = 3 * 6.2832f / perimeter
        val wob = wobble * (0.45f + perimeter / 2500f).coerceAtMost(1f)
        val total = perimeter + overlap
        val steps = (total / 4f).toInt().coerceIn(32, 400)
        val cx = w / 2
        val cy = h / 2
        val points = FloatArray((steps + 1) * 2)
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val d = start + total * t
            point(d)
            val head = ((0.1f - t) / 0.1f).coerceAtLeast(0f)
            val tail = ((t - 0.9f) / 0.1f).coerceAtLeast(0f)
            val drift = wob * (0.65f * sin(d * f1 + p1) + 0.35f * sin(d * f2 + p2)) +
                wob * 1.6f * tail * tail - wob * 1.2f * head * head
            val vx = out[0] - cx
            val vy = out[1] - cy
            val len = hypot(vx, vy).coerceAtLeast(1f)
            points[i * 2] = out[0] + vx / len * drift
            points[i * 2 + 1] = out[1] + vy / len * drift
        }
        return points
    }

    /** A filled stroke [width] wide along [points], tapering to a point at both ends. */
    fun tapered(points: FloatArray, width: Float): Path {
        val path = Path()
        val n = points.size / 2
        if (n < 2) return path
        val left = FloatArray(n * 2)
        val right = FloatArray(n * 2)
        for (i in 0 until n) {
            val a = maxOf(0, i - 1)
            val b = minOf(n - 1, i + 1)
            val dx = points[b * 2] - points[a * 2]
            val dy = points[b * 2 + 1] - points[a * 2 + 1]
            val len = hypot(dx, dy).coerceAtLeast(0.001f)
            val t = i / (n - 1f)
            val taper = minOf(1f, t / 0.1f, (1f - t) / 0.14f).coerceAtLeast(0.04f).pow(0.8f)
            val half = width * taper / 2f
            val nx = -dy / len * half
            val ny = dx / len * half
            left[i * 2] = points[i * 2] + nx
            left[i * 2 + 1] = points[i * 2 + 1] + ny
            right[i * 2] = points[i * 2] - nx
            right[i * 2 + 1] = points[i * 2 + 1] - ny
        }
        path.moveTo(left[0], left[1])
        for (i in 1 until n) path.lineTo(left[i * 2], left[i * 2 + 1])
        for (i in n - 1 downTo 0) path.lineTo(right[i * 2], right[i * 2 + 1])
        path.close()
        return path
    }
}
