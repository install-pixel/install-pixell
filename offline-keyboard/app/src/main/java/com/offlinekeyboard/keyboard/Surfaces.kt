package com.offlinekeyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
 * - TYPEWRITER: an old typewriter key: round (a pill when long, a rounded plate for panels and popups,
 *   [plate]): a nickel rim with a line of light, pitted with rust here and there, around a glossy black cap
 *   with fine scratches, all different for every [seed]; it throws a shadow, a pressed key goes down into the
 *   machine, chosen is a brass rim.
 *
 * Gradients are made once over a 0..1 square and stretched onto each surface; pencil outlines are made once
 * per size and seed. Drawing allocates nothing in the usual case. [frosted] glass is partly transparent, for
 * bubbles drawn over the blurred keys (see [Frost]).
 */
class SurfacePainter(private val density: Float) {

    /** 0.4–1: how bright rims, the top light and pencil frames are drawn (the keyboard brightness setting). */
    var brightness = 1f

    /**
     * The milky typewriter: cream caps in bronze rims on a light machine, brown shadows, no rust. Off: the
     * black typewriter (black caps in nickel).
     */
    var milk = false
        set(value) {
            if (field == value) return
            val black = blackShaders
            field = value
            if (value) milkShaders() else restore(black)
        }

    /** The black typewriter's shaders, kept to go back to (taken before the first change to milk). */
    private val blackShaders: Array<Shader> by lazy {
        arrayOf(metalRim, brassRim, capFill, capShadow, keyShadow, gloss, bounce, leverShade, leverLight, rodShade, rodLight, depthShade, rivetShade, collarShade)
    }

    private fun restore(s: Array<Shader>) {
        metalRim = s[0]; brassRim = s[1]; capFill = s[2]; capShadow = s[3]; keyShadow = s[4]; gloss = s[5]; bounce = s[6]
        leverShade = s[7]; leverLight = s[8]; rodShade = s[9]; rodLight = s[10]; depthShade = s[11]; rivetShade = s[12]; collarShade = s[13]
    }

    /** The colours that differ on the milky typewriter (see [milk]). */
    private val edge get() = if (milk) 0x80513A22.toInt() else 0xBF000000.toInt()
    private val capEdge get() = if (milk) 0x996B4A2A.toInt() else 0xD9000000.toInt()
    private val rodEdge get() = if (milk) 0x80513A22.toInt() else 0xD9000000.toInt()

    private fun milkShaders() {
        fun linear(c: IntArray, at: FloatArray) = LinearGradient(0f, 0f, 0f, 1f, c, at, Shader.TileMode.CLAMP)
        val mid = floatArrayOf(0f, 0.5f, 1f)
        // Bronze, lit from above.
        metalRim = linear(intArrayOf(0xFFF3DDB2.toInt(), 0xFF6E4C26.toInt(), 0xFFC9A26A.toInt()), mid)
        brassRim = linear(intArrayOf(0xFFF7E3BC.toInt(), 0xFF8A5E2C.toInt(), 0xFFD8B47C.toInt()), mid)
        // Milk-white enamel: bright at the top, a warm cream below.
        capFill = linear(intArrayOf(0xFFFFFBF2.toInt(), 0xFFF0E6D2.toInt(), 0xFFF7EFDF.toInt()), floatArrayOf(0f, 0.55f, 1f))
        capShadow = LinearGradient(0f, 0f, 0f, 1f, 0x594A3420, 0x004A3420, Shader.TileMode.CLAMP)
        keyShadow = RadialGradient(
            0f, 0f, 1f, intArrayOf(0x8C5A4026.toInt(), 0x4D5A4026, 0x005A4026), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP,
        )
        gloss = RadialGradient(
            0f, 0f, 1f, intArrayOf(0xB3FFFFFF.toInt(), 0x40FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        bounce = RadialGradient(0f, 0f, 1f, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        leverShade = linear(intArrayOf(0xFFB08A58.toInt(), 0xFF8A6A42.toInt(), 0xFF5E4428.toInt()), mid)
        leverLight = linear(intArrayOf(0xBFF6E2BC.toInt(), 0x40E0C8A0, 0x00000000), floatArrayOf(0f, 0.6f, 1f))
        rodShade = linear(intArrayOf(0xFF5E4428.toInt(), 0xFF8A6A42.toInt(), 0xFFB89468.toInt()), mid)
        rodLight = linear(intArrayOf(0x00000000, 0x40F2DEB8, 0xB3FAEBCB.toInt()), floatArrayOf(0f, 0.6f, 1f))
        depthShade = linear(intArrayOf(0x995A4026.toInt(), 0x595A4026, 0x005A4026), floatArrayOf(0f, 0.2f, 1f))
        rivetShade = RadialGradient(
            -0.3f, -0.3f, 1.3f, intArrayOf(0xFFF5DFB6.toInt(), 0xFF9A7240.toInt(), 0xFF4A3218.toInt()), mid, Shader.TileMode.CLAMP,
        )
        collarShade = linear(intArrayOf(0xFFF0D6A6.toInt(), 0xFF8A6438.toInt(), 0xFF4A3218.toInt()), mid)
    }

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

    // Aged nickel, lit from above: bright top, dark middle, a lighter reflection at the bottom; chosen is brass.
    // Three stops at most (see glassRim).
    private var metalRim: Shader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xFFF4F1E8.toInt(), 0xFF3A362F.toInt(), 0xFFBDB7A8.toInt()),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    private var brassRim: Shader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xFFEBD9A6.toInt(), 0xFF6E5A30.toInt(), 0xFFB09659.toInt()),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    // A second light across the metal: the upper left catches the window, the lower right turns away.
    private val rimSide = LinearGradient(
        0f, 0f, 1f, 1f,
        intArrayOf(0x40FFFAEB, 0x00000000, 0x59000000),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    // The black cap: a little light at the top edge, darkest below the middle, a faint bounce at the bottom.
    private var capFill: Shader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xFF24211D.toInt(), 0xFF0B0A09.toInt(), 0xFF1A1815.toInt()),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
    )
    // The cap sits under the rim: the rim throws a shadow over its top.
    private var capShadow: Shader = LinearGradient(0f, 0f, 0f, 1f, 0xB3000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
    // Soft round lights and shadows, made over a unit circle and stretched to an ellipse ([softEllipse]).
    private var keyShadow: Shader = RadialGradient(
        0f, 0f, 1f, intArrayOf(0xE6000000.toInt(), 0x99000000.toInt(), 0x00000000), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP,
    )
    private var gloss: Shader = RadialGradient(
        0f, 0f, 1f, intArrayOf(0x33FFF8E6, 0x14FFF8E6, 0x00FFF8E6), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
    )
    private var bounce: Shader = RadialGradient(0f, 0f, 1f, 0x12FFF0D2, 0x00FFF0D2, Shader.TileMode.CLAMP)
    // A steel key lever: lit near the keys, dark deep in the machine; with a thin line of light along it.
    private var leverShade: Shader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xFF4A453C.toInt(), 0xFF2A2722.toInt(), 0xFF0C0B0A.toInt()),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    private var leverLight: Shader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xBFDCD4C3.toInt(), 0x40A0988A, 0x00000000),
        floatArrayOf(0f, 0.6f, 1f),
        Shader.TileMode.CLAMP,
    )
    // A key lever seen from the front: dark deep in the machine (up), lit near its key (down).
    private var rodShade: Shader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xFF0C0B0A.toInt(), 0xFF3A362F.toInt(), 0xFF6A655A.toInt()),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    private var rodLight: Shader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0x00000000, 0x40DCD4C3, 0xB3EBE4D4.toInt()),
        floatArrayOf(0f, 0.6f, 1f),
        Shader.TileMode.CLAMP,
    )
    // The machine's dark depth under the paper: the paper's shadow, black at its edge, fading down.
    private var depthShade: Shader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xFF000000.toInt(), 0xCC000000.toInt(), 0x00000000),
        floatArrayOf(0f, 0.2f, 1f),
        Shader.TileMode.CLAMP,
    )
    // A round rivet head, lit from the upper left.
    private var rivetShade: Shader = RadialGradient(
        -0.3f, -0.3f, 1.3f,
        intArrayOf(0xFFD8D0BF.toInt(), 0xFF6D675B.toInt(), 0xFF151310.toInt()),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val leverPath = Path()
    private val leverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    // The collar under the key.
    private var collarShade: Shader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xFFC9C2B2.toInt(), 0xFF5A554B.toInt(), 0xFF1C1A17.toInt()),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val shape = Path()
    private val capShape = Path()
    private val ring = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val capRect = RectF()
    private val oval = RectF()
    private val wearPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wear = object : LinkedHashMap<Int, Wear>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Wear>) = size > 400
    }

    /**
     * The marks of age on one key, in fractions of its size: rust pitting the rim (angle, depth into the rim,
     * size, colour, strength for every pit) and fine scratches on the cap (x, y, angle, length, strength).
     */
    private class Wear(val rust: FloatArray, val scratches: FloatArray, val fewScratches: Int)

    private fun wearOf(seed: Int, plate: Boolean): Wear = wear.getOrPut(seed * 2 + if (plate) 1 else 0) {
        val random = Random(seed * 7919 + 17)
        // A machine in use: about one key in six has a short corroded stretch of rim.
        val patches = if (random.nextInt(6) == 0) 1 else 0
        val pits = ArrayList<Float>()
        repeat(patches) {
            val from = random.nextFloat() * 6.2832f
            val span = 0.2f + random.nextFloat() * 0.35f
            val n = (8 + span * 22).toInt()
            for (i in 0 until n) {
                pits += from + span * i / n + (random.nextFloat() - 0.5f) * 0.12f
                pits += random.nextFloat().pow(1.8f) // mostly at the outer edge, where the nickel wore through
                pits += 0.18f + random.nextFloat() * 0.42f
                pits += random.nextInt(RUST.size).toFloat()
                pits += 0.35f + random.nextFloat() * 0.45f
            }
        }
        // Up to five fine scratches: a key used often shows them all, a rarely used one at most one.
        val scratches = FloatArray(MAX_SCRATCHES * 5) { i ->
            when (i % 5) {
                0, 1 -> 0.2f + random.nextFloat() * 0.6f
                2 -> random.nextFloat() * 3.1416f
                3 -> 0.15f + random.nextFloat() * 0.25f
                else -> 0.06f + random.nextFloat() * 0.06f
            }
        }
        Wear(pits.toFloatArray(), scratches, random.nextInt(2))
    }

    /** How wide the metal rim of a typewriter key is, for a key [height] tall (labels stay inside it). */
    fun typewriterRim(height: Float, plate: Boolean = false): Float =
        if (plate) 2.4f * density else maxOf(2.6f * density, height / 2f * 0.19f)

    /** How far a pressed typewriter key goes down. */
    val typewriterSink: Float get() = 6f * density

    /** A soft light or shadow: [shader] (made over a unit circle) stretched to the ellipse at [cx], [cy]. */
    private fun softEllipse(canvas: Canvas, shader: Shader, cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float = 1f) {
        if (rx <= 0f || ry <= 0f) return
        matrix.setScale(rx, ry)
        matrix.postTranslate(cx, cy)
        shader.setLocalMatrix(matrix)
        fill.shader = shader
        fill.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
        canvas.drawOval(oval, fill)
        fill.alpha = 0xFF
        fill.shader = null
    }

    /**
     * A key lever of the typewriter: a steel strip [width] wide along [points] (x, y pairs: from the middle of
     * the key, down past its collar, through the gap between the keys below and on into the machine), lit
     * near [top] and dark at [bottom]. [rivet] puts a rivet on the bend where it passes between the keys.
     */
    fun drawTypewriterLever(
        canvas: Canvas, points: FloatArray, width: Float, top: Float, bottom: Float, rivet: Boolean, collar: Boolean = true,
    ) {
        if (points.size < 4) return
        leverPath.reset()
        leverPath.moveTo(points[0], points[1])
        var i = 2
        while (i + 1 < points.size) {
            leverPath.lineTo(points[i], points[i + 1])
            i += 2
        }
        // A dark edge, so a lever stands apart from the one behind it.
        leverPaint.shader = null
        leverPaint.color = rodEdge
        leverPaint.strokeWidth = width + 1.6f * density
        canvas.drawPath(leverPath, leverPaint)
        matrix.setScale(1f, maxOf(1f, bottom - top))
        matrix.postTranslate(0f, top)
        leverShade.setLocalMatrix(matrix)
        leverPaint.shader = leverShade
        leverPaint.strokeWidth = width
        canvas.drawPath(leverPath, leverPaint)
        leverLight.setLocalMatrix(matrix)
        leverPaint.shader = leverLight
        leverPaint.strokeWidth = width * 0.32f
        leverPath.offset(-width * 0.22f, 0f)
        canvas.drawPath(leverPath, leverPaint)
        leverPaint.shader = null
        if (rivet && points.size >= 6) drawRivet(canvas, points[4], points[5], width * 0.62f)
        if (!collar) return
        // The collar just under the key.
        val x = points[2]
        val y = points[3]
        oval.set(x - width * 0.95f, y - width * 0.4f, x + width * 0.95f, y + width * 0.4f)
        stretch(collarShade, oval)
        fill.shader = collarShade
        canvas.drawRoundRect(oval, width * 0.3f, width * 0.3f, fill)
        fill.shader = null
    }

    /**
     * A key lever of the typewriter: a straight steel strip [width] wide from its pivot deep in the machine
     * ([topX], [topY]) down to its key ([keyX], [keyY]); the keys in front hide all but the gaps between rows.
     */
    fun drawTypewriterRod(canvas: Canvas, topX: Float, topY: Float, keyX: Float, keyY: Float, width: Float) {
        if (keyY <= topY) return
        matrix.setScale(1f, keyY - topY)
        matrix.postTranslate(0f, topY)
        leverPaint.strokeCap = Paint.Cap.BUTT
        leverPaint.shader = null
        leverPaint.color = rodEdge
        leverPaint.strokeWidth = width + 1f * density
        canvas.drawLine(topX, topY, keyX, keyY, leverPaint)
        rodShade.setLocalMatrix(matrix)
        leverPaint.shader = rodShade
        leverPaint.strokeWidth = width
        canvas.drawLine(topX, topY, keyX, keyY, leverPaint)
        rodLight.setLocalMatrix(matrix)
        leverPaint.shader = rodLight
        leverPaint.strokeWidth = width * 0.3f
        canvas.drawLine(topX - width * 0.22f, topY, keyX - width * 0.22f, keyY, leverPaint)
        leverPaint.shader = null
    }

    /**
     * The moving parts of a key lever that show: a collar on it just above its key ([x], [y], going down
     * with the key) and, while the key is down ([depth] 0..1), a glint of light along the lever and a short
     * flash at its top where the type strikes the paper ([topX], [topY]).
     */
    fun drawTypewriterLeverParts(
        canvas: Canvas, topX: Float, topY: Float, x: Float, y: Float, width: Float, depth: Float,
    ) {
        if (depth > 0.02f) {
            // A soft glint along the lever just above the key, where it catches the light as it moves; the
            // rest of the lever stays as it is (no flash up at the paper).
            leverPaint.shader = null
            leverPaint.strokeCap = Paint.Cap.ROUND
            leverPaint.color = if (milk) 0xFFFFF4DC.toInt() else 0xFFE8DFCC.toInt()
            leverPaint.alpha = (60 * depth.coerceAtMost(1f) * (0.55f + 0.45f * brightness)).toInt()
            leverPaint.strokeWidth = width * 0.4f
            val reach = (GLINT_DP * density / maxOf(1f, y - topY)).coerceAtMost(1f)
            val fromX = x + (topX - x) * reach
            val fromY = y + (topY - y) * reach
            canvas.drawLine(fromX - width * 0.2f, fromY, x - width * 0.2f, y, leverPaint)
            leverPaint.alpha = 0xFF
            leverPaint.strokeCap = Paint.Cap.BUTT
        }
        oval.set(x - width * 1.05f, y - width * 0.45f, x + width * 1.05f, y + width * 0.45f)
        stretch(collarShade, oval)
        fill.shader = collarShade
        canvas.drawRoundRect(oval, width * 0.3f, width * 0.3f, fill)
        fill.shader = null
        // A screw in the collar.
        fill.color = if (milk) 0xFF3E2A16.toInt() else 0xFF1A1815.toInt()
        canvas.drawCircle(x, y, width * 0.18f, fill)
    }

    /**
     * The paper holder: a polished bar across the top of the keyboard, [height] tall, holding the paper down
     * where it goes into the machine, with a screw at each end (nickel; bronze on the milky machine).
     */
    fun drawPaperHolder(canvas: Canvas, width: Float, height: Float) {
        oval.set(0f, 0f, width, height)
        // Its shadow on the machine below.
        fill.shader = null
        fill.color = if (milk) 0x405A4026 else 0x99000000.toInt()
        canvas.drawRect(0f, height, width, height + 1.5f * density, fill)
        stretch(metalRim, oval)
        fill.shader = metalRim
        canvas.drawRect(oval, fill)
        fill.shader = null
        // A line of light along its upper edge, dimmed with the keyboard brightness.
        fill.color = if (milk) 0xFFFFF4DC.toInt() else 0xFFF4F1E8.toInt()
        fill.alpha = (150 * (0.55f + 0.45f * brightness)).toInt()
        canvas.drawRect(0f, height * 0.18f, width, height * 0.18f + 0.8f * density, fill)
        fill.alpha = 0xFF
        fill.color = edge
        canvas.drawRect(0f, height - 0.8f * density, width, height, fill)
        val r = height * 0.32f
        drawRivet(canvas, height * 1.6f, height / 2, r)
        drawRivet(canvas, width - height * 1.6f, height / 2, r)
    }

    /**
     * A typebar swung up to the paper: a steel (bronze) arm [width] wide from its pivot ([fromX], [fromY]) to
     * its tip ([tipX], [tipY]), with the type slug at the tip, inked dark on its face.
     */
    fun drawTypebar(canvas: Canvas, fromX: Float, fromY: Float, tipX: Float, tipY: Float, width: Float) {
        val dx = tipX - fromX
        val dy = tipY - fromY
        val length = kotlin.math.hypot(dx, dy)
        if (length < width) return
        canvas.save()
        // Drawn upright from the pivot, turned to point at the tip.
        canvas.rotate(Math.toDegrees(kotlin.math.atan2(dx, -dy).toDouble()).toFloat(), fromX, fromY)
        val top = fromY - length
        matrix.setScale(1f, length)
        matrix.postTranslate(0f, top)
        leverPaint.strokeCap = Paint.Cap.BUTT
        leverPaint.shader = null
        leverPaint.color = rodEdge
        leverPaint.strokeWidth = width + 1f * density
        canvas.drawLine(fromX, fromY, fromX, top + width, leverPaint)
        leverShade.setLocalMatrix(matrix)
        leverPaint.shader = leverShade
        leverPaint.strokeWidth = width
        canvas.drawLine(fromX, fromY, fromX, top + width, leverPaint)
        leverPaint.shader = null
        oval.set(fromX - width * 0.95f, top, fromX + width * 0.95f, top + width * 2.6f)
        stretch(collarShade, oval)
        fill.shader = collarShade
        canvas.drawRoundRect(oval, width * 0.35f, width * 0.35f, fill)
        fill.shader = null
        fill.color = if (milk) 0xCC3E2A16.toInt() else 0xE6000000.toInt()
        canvas.drawRect(fromX - width * 0.6f, top, fromX + width * 0.6f, top + width * 0.5f, fill)
        canvas.restore()
    }

    /** The dark depth of the machine under its top edge, where the levers go in ([height] tall). */
    fun drawTypewriterDepth(canvas: Canvas, width: Float, height: Float) {
        oval.set(0f, 0f, width, height)
        stretch(depthShade, oval)
        fill.shader = depthShade
        canvas.drawRect(oval, fill)
        fill.shader = null
    }

    private fun drawRivet(canvas: Canvas, x: Float, y: Float, r: Float) {
        matrix.setScale(r, r)
        matrix.postTranslate(x, y)
        rivetShade.setLocalMatrix(matrix)
        fill.shader = rivetShade
        canvas.drawCircle(x, y, r, fill)
        fill.shader = null
    }

    /**
     * What the round keys of a typewriter popup sit on: a dark backing over [rect] and a steel bar along its
     * middle, with a rivet at each end.
     */
    fun drawTypewriterBar(canvas: Canvas, rect: RectF) {
        fill.shader = null
        fill.color = if (milk) 0xF2EADCC4.toInt() else 0xEB000000.toInt()
        val r = rect.height() / 2
        canvas.drawRoundRect(rect, r, r, fill)
        val w = maxOf(3f * density, rect.height() * 0.1f)
        val y = rect.centerY()
        val from = rect.left + r * 0.6f
        val to = rect.right - r * 0.6f
        drawTypewriterLever(canvas, floatArrayOf(from, y, to, y), w, rect.top, rect.bottom + rect.height(), rivet = false, collar = false)
        drawRivet(canvas, from, y, w * 0.7f)
        drawRivet(canvas, to, y, w * 0.7f)
    }

    /**
     * How far down the next typewriter key is drawn, as a share of [typewriterSink]: set by the keyboard for
     * the spring of a key coming back up (a little below 0 as it overshoots); below -1 follows the state.
     */
    var typewriterDepth = -2f

    /** Whether the next typewriter key is one of the most used letters: more fine scratches on it. */
    var typewriterWorn = false

    /**
     * An old typewriter key, round when [rect] is square and a pill when it is long, or a rounded plate for
     * panels and popups ([plate]). [rect] is the key at rest: a pressed key is drawn [typewriterSink] lower.
     */
    private fun drawTypewriter(canvas: Canvas, rect: RectF, radius: Float, state: SurfaceState, seed: Int, plate: Boolean) {
        val corner = if (plate) minOf(radius, rect.height() / 2) else minOf(rect.width(), rect.height()) / 2f
        val half = rect.height() / 2f
        val depth = when {
            plate -> 0f
            typewriterDepth >= -1f -> typewriterDepth
            state == SurfaceState.PRESSED -> 1f
            else -> 0f
        }
        val pressed = depth > 0.5f
        val sink = depth * typewriterSink
        val lit = 0.55f + 0.45f * brightness
        val cx = rect.centerX()
        val cy = rect.centerY()

        // Its shadow on the machine: a pressed key is closer to it, so the shadow is smaller and sharper.
        if (!plate) {
            softEllipse(
                canvas, keyShadow, cx, cy + half * 0.28f + sink * 0.3f,
                rect.width() / 2 + half * 0.22f - sink * 0.3f, half * 1.12f - sink * 0.4f, 1f - 0.25f * depth.coerceIn(0f, 1f),
            )
        }
        canvas.save()
        canvas.translate(0f, sink)

        // The rim: the whole shape in nickel (brass when chosen), with the side light over it.
        shape.reset()
        shape.addRoundRect(rect, corner, corner, Path.Direction.CW)
        val metal = if (state == SurfaceState.SELECTED) brassRim else metalRim
        stretch(metal, rect)
        fill.shader = metal
        canvas.drawPath(shape, fill)
        stretch(rimSide, rect)
        fill.shader = rimSide
        canvas.drawPath(shape, fill)
        fill.shader = null

        val rimWidth = typewriterRim(rect.height(), plate)
        // Chrome: a dark band round the middle of the rim and a bright lip at its inner edge.
        wearPaint.shader = null
        wearPaint.style = Paint.Style.STROKE
        for ((at, width, colour) in BEVELS) {
            wearPaint.strokeWidth = rimWidth * width
            wearPaint.color = colour
            wearPaint.alpha = (Color.alpha(colour) * lit).toInt()
            val inset = rimWidth * at
            oval.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
            canvas.drawRoundRect(oval, maxOf(0f, corner - inset), maxOf(0f, corner - inset), wearPaint)
        }
        // A bright line of light along the upper left of the rim, soft at its edges.
        wearPaint.shader = null
        wearPaint.style = Paint.Style.STROKE
        wearPaint.strokeCap = Paint.Cap.ROUND
        wearPaint.color = 0xFFFFFCF0.toInt()
        for ((width, alpha) in HIGHLIGHT) {
            wearPaint.strokeWidth = rimWidth * width
            wearPaint.alpha = (255 * alpha * lit).toInt()
            if (rect.width() <= rect.height() * 1.05f) {
                val r = corner - rimWidth / 2
                oval.set(cx - r, cy - r, cx + r, cy + r)
                canvas.drawArc(oval, 194f, 61f, false, wearPaint)
            } else {
                val y = rect.top + rimWidth / 2
                val from = rect.left + corner * 0.9f
                canvas.drawLine(from, y, from + minOf(rect.width() * 0.25f, corner * 1.2f), y, wearPaint)
            }
        }

        capRect.set(rect.left + rimWidth, rect.top + rimWidth, rect.right - rimWidth, rect.bottom - rimWidth)
        val capCorner = maxOf(0f, corner - rimWidth)
        val marks = wearOf(seed, plate)

        // Rust pitting the rim where the nickel wore through: only on the metal, never on the cap or outside.
        ring.reset()
        ring.addRoundRect(rect, corner, corner, Path.Direction.CW)
        ring.addRoundRect(capRect, capCorner, capCorner, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(ring)
        wearPaint.style = Paint.Style.FILL
        val rust = if (milk) FloatArray(0) else marks.rust
        val straight = maxOf(0f, rect.width() / 2 - corner)
        val straightY = maxOf(0f, rect.height() / 2 - corner)
        var i = 0
        while (i + 4 < rust.size) {
            val a = rust[i]
            val ca = cos(a)
            val sa = sin(a)
            // A point on the rim: around the round end of a pill, or along its straight sides.
            val ox = (ca * rect.width() * 0.8f).coerceIn(-straight, straight)
            val oy = (sa * rect.height() * 0.8f).coerceIn(-straightY, straightY)
            val depth = corner - rimWidth * (0.15f + rust[i + 1] * 0.8f)
            wearPaint.color = RUST[rust[i + 3].toInt()]
            wearPaint.alpha = (255 * rust[i + 4] * lit).toInt()
            canvas.drawCircle(cx + ox + ca * depth, cy + oy + sa * depth, rimWidth * rust[i + 2], wearPaint)
            i += 5
        }
        canvas.restore()

        // The outer edge of the rim.
        wearPaint.style = Paint.Style.STROKE
        wearPaint.strokeWidth = 0.9f * density
        wearPaint.color = edge
        val e = 0.45f * density
        oval.set(rect.left + e, rect.top + e, rect.right - e, rect.bottom - e)
        canvas.drawRoundRect(oval, corner - e, corner - e, wearPaint)

        // The black cap inside the rim.
        capShape.reset()
        capShape.addRoundRect(capRect, capCorner, capCorner, Path.Direction.CW)
        stretch(capFill, capRect)
        fill.shader = capFill
        canvas.drawPath(capShape, fill)
        canvas.save()
        canvas.clipPath(capShape)
        stretch(capShadow, capRect, capRect.height() * 0.35f)
        fill.shader = capShadow
        canvas.drawPath(capShape, fill)
        fill.shader = null
        // Gloss: the soft reflection of a window in the upper part (dimmer when the key is down, out of the
        // light), and a faint bounce of light at the bottom.
        val iw = capRect.width()
        val ih = minOf(capRect.height(), if (plate) 60f * density else capRect.height())
        if (pressed) {
            softEllipse(canvas, gloss, cx - iw * 0.1f, capRect.top + ih * 0.34f, iw * 0.36f, ih * 0.16f, 0.45f * lit)
        } else {
            softEllipse(canvas, gloss, cx - iw * 0.12f, capRect.top + ih * 0.3f, iw * 0.42f, ih * 0.2f, lit)
        }
        softEllipse(canvas, bounce, cx, capRect.bottom - capRect.height() * 0.02f, iw * 0.45f, ih * 0.18f, lit)
        // Fine scratches in the celluloid.
        wearPaint.strokeWidth = 0.5f * density
        // Scratches catch the light on black; on milky enamel they hold a little dirt.
        wearPaint.color = if (milk) 0x998A6A48.toInt() else 0xFFFFFFFF.toInt()
        val sc = marks.scratches
        val scale = minOf(iw, capRect.height(), 48f * density)
        val shown = (if (typewriterWorn) MAX_SCRATCHES - marks.fewScratches else marks.fewScratches) * 5
        i = 0
        while (i + 4 < minOf(sc.size, shown)) {
            val x = capRect.left + sc[i] * iw
            val y = capRect.top + sc[i + 1] * capRect.height()
            val len = sc[i + 3] * scale
            val dx = cos(sc[i + 2]) * len / 2
            val dy = sin(sc[i + 2]) * len / 2
            wearPaint.alpha = (255 * sc[i + 4] * lit).toInt()
            canvas.drawLine(x - dx, y - dy, x + dx, y + dy, wearPaint)
            i += 5
        }
        canvas.restore()
        // Where the cap meets the rim.
        wearPaint.strokeWidth = 0.7f * density
        wearPaint.color = capEdge
        canvas.drawPath(capShape, wearPaint)
        canvas.restore()
    }

    private companion object {
        val RUST = intArrayOf(0xFF602E14.toInt(), 0xFF78401E.toInt(), 0xFF3E1E0E.toInt(), 0xFF8A5028.toInt())
        // The rim's line of light: a wide faint stroke under a narrow bright one.
        val HIGHLIGHT = arrayOf(0.7f to 0.22f, 0.3f to 0.7f)
        /** Chrome bevels on the rim: where (a share of the rim from the outside), how wide, what colour. */
        val BEVELS = arrayOf(Triple(0.55f, 0.22f, 0x47000000), Triple(0.9f, 0.16f, 0x38FFFFFF))
        const val MAX_SCRATCHES = 5
        /** How far up its lever a pressed key's glint reaches. */
        const val GLINT_DP = 14f
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
