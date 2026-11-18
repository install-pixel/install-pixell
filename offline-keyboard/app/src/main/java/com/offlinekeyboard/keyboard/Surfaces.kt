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
 * - TYPEWRITER: an old typewriter key: round (a pill when much wider than tall, a rounded plate for panels and
 *   popups, [plate]), a worn metal rim around a dark cap, with scratches, a rubbed-bright middle and a few rust
 *   spots, different for every [seed]; pressed sinks the cap, chosen is a brass rim.
 *
 * Gradients are made once over a 0..1 square and stretched onto each surface; pencil outlines are made once
 * per size and seed. Drawing allocates nothing in the usual case. [frosted] glass is partly transparent, for
 * bubbles drawn over the blurred keys (see [Frost]).
 */
class SurfacePainter(private val density: Float) {

    /** 0.4–1: how bright rims, the top light and pencil frames are drawn (the keyboard brightness setting). */
    var brightness = 1f

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
    // The rim of THEMES.md (white at 50/14/7/15 %, chosen 70/25/20 %) flattened onto the black keyboard as opaque
    // greys and at most three stops: the Android 14/15 emulator renderer dies on four-stop gradients.
    private val glassRim = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xFF808080.toInt(), 0xFF242424.toInt(), 0xFF1A1A1A.toInt()),
        floatArrayOf(0f, 0.25f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val glassRimChosen = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xFFB3B3B3.toInt(), 0xFF404040.toInt(), 0xFF333333.toInt()),
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
        frosted: Boolean = false,
        plate: Boolean = false,
    ) {
        when (style) {
            SurfaceStyle.FLAT -> {
                fill.shader = null
                fill.color = color
                canvas.drawRoundRect(rect, radius, radius, fill)
            }
            SurfaceStyle.GLASS -> drawGlass(canvas, rect, radius, state, sheen, frosted)
            SurfaceStyle.SHEET -> drawSheet(canvas, rect, radius, state, seed, inkColor)
            SurfaceStyle.TYPEWRITER -> drawTypewriter(canvas, rect, radius, state, seed, plate || sheen)
        }
    }

    private fun stretch(shader: Shader, rect: RectF, height: Float = rect.height()) {
        matrix.setScale(rect.width(), height)
        matrix.postTranslate(rect.left, rect.top)
        shader.setLocalMatrix(matrix)
    }

    private fun drawGlass(canvas: Canvas, rect: RectF, radius: Float, state: SurfaceState, withSheen: Boolean, frosted: Boolean) {
        val body = if (state == SurfaceState.PRESSED) glassPressed else glassFill
        stretch(body, rect)
        fill.shader = body
        // Frosted (over blurred keys): the dark glass lets a little of what is behind show through.
        if (frosted) fill.alpha = 0xB8
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.alpha = 0xFF
        if (withSheen) {
            // Soft light inside the top, gone by half height (at most 120 dp).
            stretch(sheen, rect, minOf(rect.height() / 2, 120 * density))
            fill.shader = sheen
            fill.alpha = (255 * brightness).toInt()
            canvas.drawRoundRect(rect, radius, radius, fill)
            fill.alpha = 0xFF
        }
        fill.shader = null
        val edge = if (state == SurfaceState.SELECTED) glassRimChosen else glassRim
        stretch(edge, rect)
        rim.shader = edge
        rim.alpha = (255 * brightness).toInt()
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
        ink.alpha = ((if (chosen) 255 else 205) * brightness).toInt()
        canvas.drawPath(main, ink)
        ink.alpha = ((if (chosen) 130 else 80) * brightness).toInt()
        canvas.drawPath(ghost, ink)
        canvas.restore()
    }
    // ---------------------------------------------------------------- typewriter

    // Aged nickel: bright on top, dull and dark below; chosen is brass. Three stops at most (see glassRim).
    private val metalRim = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xFFC9C2B2.toInt(), 0xFF6F685A.toInt(), 0xFF35302A.toInt()),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val brassRim = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xFFEBD9A6.toInt(), 0xFF9E874F.toInt(), 0xFF4E3F22.toInt()),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val capFill = RadialGradient(0.45f, 0.38f, 0.8f, 0xFF2D2924.toInt(), 0xFF0C0A08.toInt(), Shader.TileMode.CLAMP)
    private val capPressed = RadialGradient(0.5f, 0.55f, 0.8f, 0xFF1C1915.toInt(), 0xFF050403.toInt(), Shader.TileMode.CLAMP)
    private val shape = Path()
    private val capShape = Path()
    private val wearPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wear = object : LinkedHashMap<Long, Wear>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Wear>) = size > 400
    }

    /** The marks of age on one key, in 0..1 of its size: rust on the rim, scratches and a rubbed patch on the cap. */
    private class Wear(val rust: FloatArray, val scratches: FloatArray, val rub: FloatArray)

    private fun wearOf(seed: Int, w: Float, h: Float, plate: Boolean): Wear {
        val key = (w.toInt().toLong() shl 40) or (h.toInt().toLong() shl 24) or ((seed and 0xFFFF).toLong() shl 1) or (if (plate) 1L else 0L)
        return wear.getOrPut(key) {
            val random = Random(seed * 7919 + 17)
            // Rust: 0–3 spots on the rim (angle around the key, size, strength); larger panels get a few more.
            val spots = (if (plate) 2 else 0) + when (random.nextInt(10)) { in 0..2 -> 0; in 3..6 -> 1; in 7..8 -> 2; else -> 3 }
            val rust = FloatArray(spots * 3) { i ->
                when (i % 3) {
                    0 -> random.nextFloat() * 6.2832f
                    1 -> 0.6f + random.nextFloat() * 0.9f
                    else -> 0.45f + random.nextFloat() * 0.55f
                }
            }
            // Scratches: 1–4 short strokes across the cap (start x, y, angle, length).
            val lines = 1 + random.nextInt(if (plate) 6 else 4)
            val scratches = FloatArray(lines * 4) { i ->
                when (i % 4) {
                    0, 1 -> 0.22f + random.nextFloat() * 0.56f
                    2 -> random.nextFloat() * 3.1416f
                    else -> 0.12f + random.nextFloat() * 0.22f
                }
            }
            // Where fingers rubbed the cap brighter: centre a little off the middle, size.
            val rub = floatArrayOf(0.42f + random.nextFloat() * 0.16f, 0.4f + random.nextFloat() * 0.16f, 0.28f + random.nextFloat() * 0.12f)
            Wear(rust, scratches, rub)
        }
    }

    private fun drawTypewriter(canvas: Canvas, rect: RectF, radius: Float, state: SurfaceState, seed: Int, plate: Boolean) {
        // The outline: a round key, a long pill (space, Enter) or a rounded plate (panels, popups).
        val round = !plate && rect.width() <= rect.height() * 1.3f
        val outer = RectF(rect)
        if (round) {
            val r = minOf(rect.width(), rect.height()) / 2f - 0.5f * density
            outer.set(rect.centerX() - r, rect.centerY() - r, rect.centerX() + r, rect.centerY() + r)
        }
        val pressed = state == SurfaceState.PRESSED
        // A pressed key sinks a little into the machine.
        if (pressed) outer.inset(0.8f * density, 0.8f * density)
        val corner = when {
            round -> outer.width() / 2f
            plate -> radius
            else -> outer.height() / 2f
        }
        shape.reset()
        shape.addRoundRect(outer, corner, corner, Path.Direction.CW)

        // The metal rim (the whole shape), then the cap inside it.
        val rimShader = if (state == SurfaceState.SELECTED) brassRim else metalRim
        stretch(rimShader, outer)
        fill.shader = rimShader
        fill.alpha = (255 * (0.55f + 0.45f * brightness)).toInt()
        canvas.drawPath(shape, fill)
        fill.alpha = 0xFF
        val rimWidth = (if (plate) 1.6f else 2.4f) * density
        inner.set(outer.left + rimWidth, outer.top + rimWidth, outer.right - rimWidth, outer.bottom - rimWidth)
        val capCorner = maxOf(0f, corner - rimWidth)
        capShape.reset()
        capShape.addRoundRect(inner, capCorner, capCorner, Path.Direction.CW)
        val cap = if (pressed) capPressed else capFill
        stretch(cap, inner)
        fill.shader = cap
        canvas.drawPath(capShape, fill)
        fill.shader = null

        val marks = wearOf(seed, outer.width(), outer.height(), plate)
        val strength = 0.5f + 0.5f * brightness
        canvas.save()
        canvas.clipPath(capShape)
        // The rubbed-bright middle of the cap (a key only: on a large plate it would read as a stain).
        val rub = marks.rub
        wearPaint.style = Paint.Style.FILL
        wearPaint.shader = null
        wearPaint.color = 0xFFFFFFFF.toInt()
        wearPaint.alpha = (14 * strength).toInt()
        if (!plate) {
            val rubR = rub[2] * minOf(inner.width(), inner.height())
            canvas.drawCircle(inner.left + rub[0] * inner.width(), inner.top + rub[1] * inner.height(), rubR, wearPaint)
        }
        // Scratches.
        wearPaint.style = Paint.Style.STROKE
        wearPaint.strokeWidth = 0.6f * density
        wearPaint.strokeCap = Paint.Cap.ROUND
        wearPaint.alpha = ((if (plate) 22 else 40) * strength).toInt()
        val sc = marks.scratches
        // Scratches are a few millimetres long, on a key and on a large plate alike.
        val scale = minOf(inner.width(), inner.height(), 48f * density)
        var i = 0
        while (i + 3 < sc.size) {
            val x = inner.left + sc[i] * inner.width()
            val y = inner.top + sc[i + 1] * inner.height()
            val len = sc[i + 3] * scale
            val dx = cos(sc[i + 2]) * len / 2
            val dy = sin(sc[i + 2]) * len / 2
            canvas.drawLine(x - dx, y - dy, x + dx, y + dy, wearPaint)
            i += 4
        }
        canvas.restore()

        // Rust on the rim: a soft brown stain with a darker core, where the nickel has worn through. It stays
        // on the key: nothing is drawn outside its outline.
        canvas.save()
        canvas.clipPath(shape)
        wearPaint.style = Paint.Style.FILL
        val rust = marks.rust
        val cx = outer.centerX()
        val cy = outer.centerY()
        val ax = outer.width() / 2f - rimWidth / 2f
        val ay = outer.height() / 2f - rimWidth / 2f
        i = 0
        while (i + 2 < rust.size) {
            val a = rust[i]
            // A point on the rim: on the circle, or on the edge of the pill/plate (clamped ellipse).
            val px = (cx + cos(a) * ax * 1.05f).coerceIn(outer.left + rimWidth / 2, outer.right - rimWidth / 2)
            val py = (cy + sin(a) * ay * 1.05f).coerceIn(outer.top + rimWidth / 2, outer.bottom - rimWidth / 2)
            val size = rust[i + 1] * rimWidth * 1.1f
            val alpha = rust[i + 2] * strength
            wearPaint.color = RUST
            wearPaint.alpha = (150 * alpha).toInt()
            canvas.drawCircle(px, py, size, wearPaint)
            wearPaint.color = RUST_DARK
            wearPaint.alpha = (190 * alpha).toInt()
            canvas.drawCircle(px + size * 0.2f, py + size * 0.15f, size * 0.45f, wearPaint)
            i += 3
        }
        canvas.restore()
    }

    private companion object {
        const val RUST = 0xFF8C4A1E.toInt()
        const val RUST_DARK = 0xFF4E2410.toInt()
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
        // About one point per 4 px: a tall frame (a long list) needs many, or its corners turn into chords.
        val steps = (total / 4f).toInt().coerceIn(32, 4000)
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
