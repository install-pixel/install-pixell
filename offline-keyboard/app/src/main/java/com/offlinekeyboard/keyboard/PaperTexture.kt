package com.offlinekeyboard.keyboard

import android.graphics.Bitmap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Black paper for the typewriter's strip: charcoal, with a soft grain and a few creases where it was
 * crumpled. Its top edge is plain [EDGE_COLOR], so the keyboard's soft top edge runs into the paper without a
 * line; its lower edge is a slightly lighter lip, the edge of the sheet. Made once per size, always the same.
 */
object PaperTexture {

    private const val BASE = 0x17
    /** The paper's own colour, plain: what the soft edge above the keyboard fades into. */
    const val EDGE_COLOR = 0xFF181716.toInt()
    /** The same for the old parchment of the milky typewriter. */
    const val PARCHMENT_EDGE = 0xFFE3CC9C.toInt()

    fun edgeColor(parchment: Boolean) = if (parchment) PARCHMENT_EDGE else EDGE_COLOR
    private const val CELL = 28

    /**
     * A sheet of the paper: its picture and where its creases run ([folds]: x, y points along each crease,
     * one list per crease), so typed letters can follow the folds.
     */
    class Sheet(
        val bitmap: Bitmap,
        private val folds: List<FloatArray>,
        /**
         * What lies still over everything, the text included: the browned ends of the parchment (null for the
         * black paper). [bitmap] itself has no ends, so the paper that moves with the text never shows one.
         */
        val overlay: Bitmap? = null,
    ) {
        /**
         * How much a letter at [x] (on the sheet), standing [reach] around [y], sits on a fold: 0 off any
         * fold, up to ±1 right on one (the sign tells which way that fold bends).
         */
        fun foldAt(x: Float, y: Float, reach: Float): Float {
            var best = 0f
            val px = ((x % bitmap.width) + bitmap.width) % bitmap.width
            for ((index, points) in folds.withIndex()) {
                var i = 0
                while (i + 1 < points.size) {
                    if (kotlin.math.abs(points[i] - px) < 1f) {
                        val distance = kotlin.math.abs(points[i + 1] - y)
                        if (distance < reach) {
                            val strength = (1f - distance / reach) * if (index % 2 == 0) 1f else -1f
                            if (kotlin.math.abs(strength) > kotlin.math.abs(best)) best = strength
                        }
                        break
                    }
                    i += 2
                }
            }
            return best
        }
    }

    fun make(width: Int, height: Int, density: Float): Bitmap? = sheet(width, height, density)?.bitmap

    /** The graphite paper of the dark sheet style: near black, a touch warm, as a notebook's black page. */
    const val GRAPHITE_EDGE = 0xFF13120F.toInt()

    /** A sketch paper: its picture and the folds pressed into it (so letters on it can bend with them). */
    class Journal(val bitmap: Bitmap, val creases: List<Crease>) {
        /**
         * The fold under ([x], [y]) within [reach] of it, if any: the fold and how far the point lies off it
         * (signed: on the fold's lit side it is positive).
         */
        fun creaseAt(x: Float, y: Float, reach: Float): Pair<Crease, Float>? {
            val px = ((x % bitmap.width) + bitmap.width) % bitmap.width
            val py = ((y % bitmap.height) + bitmap.height) % bitmap.height
            var best: Pair<Crease, Float>? = null
            for (c in creases) {
                if (c.fade(px, py) < 0.35f) continue
                val d = c.offset(px, py)
                if (kotlin.math.abs(d) < reach && (best == null || kotlin.math.abs(d) < kotlin.math.abs(best.second))) best = c to d
            }
            return best
        }
    }

    /**
     * One fold of a page: almost straight, as a fold is, with a gentle bend and a small unevenness, dying out
     * towards its ends. [ax], [ay] run along it, [nx], [ny] across it (towards its lit side).
     */
    class Crease(
        val cx: Float, val cy: Float, val ax: Float, val ay: Float, private val length: Float,
        private val bend: Float, private val bendLength: Float, private val phase: Float,
        private val jitter: Float, private val jitterLength: Float, private val phase2: Float, val strength: Float,
    ) {
        val nx get() = -ay
        val ny get() = ax

        /** 1 in its middle, 0 at and past its ends. */
        fun fade(x: Float, y: Float): Float {
            val reach = kotlin.math.abs((x - cx) * ax + (y - cy) * ay) / (length / 2)
            return if (reach >= 1f) 0f else 1f - reach * reach * reach * reach
        }

        /** How far ([x], [y]) lies off the fold, across it. */
        fun offset(x: Float, y: Float): Float {
            val along = (x - cx) * ax + (y - cy) * ay
            return (x - cx) * nx + (y - cy) * ny + bend * sin(along / bendLength + phase) +
                jitter * sin(along / jitterLength + phase2) * sin(along / (jitterLength * 2.7f))
        }
    }

    /**
     * The sketch style's paper, as a notebook's: [light] the old parchment (a travel diary's), otherwise the
     * same paper in charcoal; both with mottling, stains, fine fibres and a few folds where the page was once
     * folded. The page under the keys is plain at the very top (where the soft edge above the keyboard runs
     * into it) and has aged edges; [keys] is the lighter paper the keys and cards are cut from (no edges: a
     * piece can come from anywhere in it). The same every time for a size.
     */
    fun journal(width: Int, height: Int, density: Float, light: Boolean, keys: Boolean = false): Journal? {
        if (width <= 0 || height <= 0) return null
        val random = Random((if (light) 31 else 29) + (if (keys) 100 else 0))
        val pixels = IntArray(width * height)
        val plain = if (keys) 0f else 8f * density
        val base = when {
            light && keys -> KEY_PARCHMENT
            light -> PARCHMENT_EDGE
            keys -> KEY_CHARCOAL
            else -> GRAPHITE_EDGE
        }
        val paper = parchment(
            width, height, density,
            seed = if (keys) 17 else 11,
            base = base,
            contrast = if (light) (if (keys) 0.85f else 1f) else 0.4f,
            warm = light,
            plainTop = !keys,
            edges = !keys,
        )?.bitmap ?: return null
        paper.getPixels(pixels, 0, width, 0, 0, width, height)
        val creases = foldPlanes(pixels, width, height, density, light, random, plain)
        val bitmap = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        val canvas = android.graphics.Canvas(bitmap)
        // Fibres: short thin strokes, a little bent, every way (light ones on graphite, brown on parchment).
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = maxOf(1f, 0.4f * density)
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        paint.strokeWidth = maxOf(0.6f, 0.3f * density)
        repeat((width * height / (900 * density * density / 5f)).toInt().coerceAtMost(4500)) {
            val x = random.nextFloat() * width
            val y = plain + random.nextFloat() * (height - plain)
            val length = (3f + random.nextFloat() * 11f) * density
            val a = random.nextFloat() * 3.1416f
            val bend = (random.nextFloat() - 0.5f) * 0.6f
            val mx = x + cos(a) * length / 2
            val my = y + sin(a) * length / 2
            // Faint: fibres in the paper, not scratches on it (warm on the charcoal too).
            paint.color = if (light) (((7 + random.nextInt(12)) shl 24) or 0x5A3C1E) else (((4 + random.nextInt(7)) shl 24) or 0xF2E6D2)
            canvas.drawLine(x, y, mx, my, paint)
            canvas.drawLine(mx, my, mx + cos(a + bend) * length / 2, my + sin(a + bend) * length / 2, paint)
        }
        return Journal(bitmap, creases)
    }

    /** The lighter parchment and charcoal the sketch's keys and cards are cut from. */
    const val KEY_PARCHMENT = 0xFFF0E0BD.toInt()
    const val KEY_CHARCOAL = 0xFF211F1B.toInt()

    fun sheet(width: Int, height: Int, density: Float, parchment: Boolean = false, base: Int = BASE): Sheet? {
        if (width <= 0 || height <= 0) return null
        if (parchment) return parchment(width, height, density)
        val random = Random(7)
        // Soft blotches: random values on a coarse grid, blended between the grid points.
        val gw = width / CELL + 2
        val gh = height / CELL + 2
        val grid = FloatArray(gw * gh) { random.nextFloat() * 2f - 1f }
        val pixels = IntArray(width * height)
        val plain = 8f * density
        for (y in 0 until height) {
            val gy = y / CELL.toFloat()
            val y0 = gy.toInt()
            val fy = gy - y0
            // Plain at the very top (it meets the soft edge above), the texture growing in below it; the
            // paper's lower edge a slightly lighter lip.
            val texture = (y / plain).coerceAtMost(1f)
            val lip = when (y) {
                height - 1 -> 9f
                height - 2 -> 4f
                else -> 0f
            }
            for (x in 0 until width) {
                val gx = x / CELL.toFloat()
                val x0 = gx.toInt()
                val fx = gx - x0
                val a = grid[y0 * gw + x0] * (1 - fx) + grid[y0 * gw + x0 + 1] * fx
                val b = grid[(y0 + 1) * gw + x0] * (1 - fx) + grid[(y0 + 1) * gw + x0 + 1] * fx
                val blotch = (a * (1 - fy) + b * fy) * 3.5f
                val grain = (random.nextFloat() - 0.5f) * 5f
                val v = (base + (blotch + grain) * texture + lip).toInt().coerceIn(0, 255)
                // A touch warmer than grey, like charcoal paper.
                pixels[y * width + x] = (0xFF shl 24) or ((v + 1).coerceAtMost(255) shl 16) or (v shl 8) or (v - 1).coerceAtLeast(0)
            }
        }
        // Creases: long faint folds, a lit edge with its shadow beside it.
        val folds = ArrayList<FloatArray>()
        repeat(2 + width / 360) {
            val path = ArrayList<Float>()
            val angle = (random.nextFloat() - 0.5f) * 0.9f + if (random.nextBoolean()) 0f else 3.1416f / 2 * 0.35f
            var x = random.nextFloat() * width
            var y = random.nextFloat() * height
            val dx = cos(angle)
            val dy = sin(angle)
            val length = width * (0.25f + random.nextFloat() * 0.4f)
            var walked = 0f
            while (walked < length) {
                val ix = x.toInt()
                val iy = y.toInt()
                // Not in the plain band at the top, where the paper meets the soft edge.
                if (iy >= plain) {
                    shade(pixels, width, height, ix, iy, 9)
                    shade(pixels, width, height, ix, iy + 1, -7)
                }
                path += x
                path += y
                x += dx
                y += dy + (random.nextFloat() - 0.5f) * 0.3f
                walked += 1f
            }
            folds += path.toFloatArray()
        }
        val bitmap = runCatching { Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
            ?: return null
        return Sheet(bitmap, folds)
    }

    /**
     * Old parchment, like an explorer's map or a travel diary: warm tan with darker, uneven stains, edges
     * browned by age (more at the ends of the strip and along its lower edge), a few water rings, fibres and
     * creases. Its top is plain [PARCHMENT_EDGE], so the soft edge above runs into it without a line.
     */
    private fun parchment(
        width: Int,
        height: Int,
        density: Float,
        seed: Int = 11,
        base: Int = PARCHMENT_EDGE,
        contrast: Float = 1f,
        warm: Boolean = true,
        plainTop: Boolean = true,
        edges: Boolean = true,
    ): Sheet? {
        val random = Random(seed)
        val gw = width / CELL + 2
        val gh = height / CELL + 2
        val coarse = FloatArray(gw * gh) { random.nextFloat() * 2f - 1f }
        val fine = 9
        val fw = width / fine + 2
        val fh = height / fine + 2
        val small = FloatArray(fw * fh) { random.nextFloat() * 2f - 1f }
        fun noise(grid: FloatArray, gwid: Int, cell: Int, x: Int, y: Int): Float {
            val gx = x / cell.toFloat()
            val gy = y / cell.toFloat()
            val x0 = gx.toInt()
            val y0 = gy.toInt()
            val fx = gx - x0
            val fy = gy - y0
            val a = grid[y0 * gwid + x0] * (1 - fx) + grid[y0 * gwid + x0 + 1] * fx
            val b = grid[(y0 + 1) * gwid + x0] * (1 - fx) + grid[(y0 + 1) * gwid + x0 + 1] * fx
            return a * (1 - fy) + b * fy
        }
        // Water rings and stains: centre, radius, strength.
        val stains = FloatArray(3 * (2 + width / 300)) { i ->
            when (i % 3) {
                0 -> random.nextFloat() * width
                1 -> random.nextFloat() * height
                else -> 0f
            }
        }
        for (i in stains.indices step 3) stains[i + 2] = height * (0.4f + random.nextFloat() * 0.9f)
        val pixels = IntArray(width * height)
        val plain = 8f * density
        val burn = 34f * density
        val baseR = (base shr 16) and 0xFF
        val baseG = (base shr 8) and 0xFF
        val baseB = base and 0xFF
        // Browns: darkening takes more blue and green than red (charcoal stays neutral).
        val kg = if (warm) 1.15f else 1.05f
        val kb = if (warm) 1.35f else 1.15f
        for (y in 0 until height) {
            val texture = if (plainTop) (y / plain).coerceAtMost(1f) else 1f
            for (x in 0 until width) {
                var dark = noise(coarse, gw, CELL, x, y) * 14f + noise(small, fw, fine, x, y) * 5f
                dark += (random.nextFloat() - 0.5f) * 6f
                // Age along the lower edge (the browned ends are in the overlay, see Sheet.overlay).
                val bottom = (height - 1 - y).toFloat()
                if (edges && bottom < burn * 0.5f) dark -= (1f - bottom / (burn * 0.5f)).let { it * it } * 26f
                for (i in stains.indices step 3) {
                    val d = kotlin.math.hypot(x - stains[i], y - stains[i + 1])
                    val r = stains[i + 2]
                    if (d < r * 1.05f) {
                        // A pale inside and a darker tide line at the edge of the ring.
                        dark -= if (d > r * 0.9f) 10f * (1f - kotlin.math.abs(d - r * 0.97f) / (r * 0.08f)).coerceAtLeast(0f) else 3f
                    }
                }
                val v = dark * texture * contrast
                val r = (baseR + v).toInt().coerceIn(0, 255)
                // Charcoal stays warm both ways: lighter takes less blue than red, as darker takes more.
                val g = (baseG + v * (if (warm || v < 0) kg else 0.93f)).toInt().coerceIn(0, 255)
                val b = (baseB + v * (if (warm || v < 0) kb else 0.8f)).toInt().coerceIn(0, 255)
                pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        // The edge of the sheet: a thin darker line.
        if (edges) for (x in 0 until width) {
            shade(pixels, width, height, x, height - 1, -40)
            shade(pixels, width, height, x, height - 2, -16)
        }
        // Fibres: short faint strokes in the paper.
        repeat(width * height / 900) {
            var fx = random.nextFloat() * width
            var fy = plain + random.nextFloat() * (height - plain)
            val a = random.nextFloat() * 6.2832f
            val by = ((if (random.nextBoolean()) -9 else 7) * contrast).toInt()
            repeat(4 + random.nextInt(8)) {
                shade(pixels, width, height, fx.toInt(), fy.toInt(), by)
                fx += cos(a)
                fy += sin(a)
            }
        }
        val folds = ArrayList<FloatArray>()
        repeat(2 + width / 360) {
            val path = ArrayList<Float>()
            val angle = (random.nextFloat() - 0.5f) * 0.9f
            var x = random.nextFloat() * width
            var y = random.nextFloat() * height
            val length = width * (0.25f + random.nextFloat() * 0.4f)
            var walked = 0f
            while (walked < length) {
                if (y >= plain) {
                    shade(pixels, width, height, x.toInt(), y.toInt(), (14 * contrast).toInt())
                    shade(pixels, width, height, x.toInt(), y.toInt() + 1, (-16 * contrast).toInt())
                }
                path += x
                path += y
                x += cos(angle)
                y += sin(angle) + (random.nextFloat() - 0.5f) * 0.3f
                walked += 1f
            }
            folds += path.toFloatArray()
        }
        val bitmap = runCatching { Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
            ?: return null
        // The browned ends: brown, stronger towards each end of the strip, ragged with the paper's blotches.
        val ends = IntArray(width * height)
        for (y in 0 until height) {
            val texture = (y / plain).coerceAtMost(1f)
            for (x in 0 until width) {
                val side = minOf(x, width - 1 - x).toFloat()
                if (side >= burn) continue
                val k = (1f - side / burn).let { it * it } * (0.85f + noise(coarse, gw, CELL, x, y) * 0.3f)
                val a = (k * 0.62f * 255 * texture).toInt().coerceIn(0, 255)
                ends[y * width + x] = (a shl 24) or BURN
            }
        }
        val overlay = runCatching { Bitmap.createBitmap(ends, width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
        return Sheet(bitmap, folds, overlay)
    }

    /**
     * Folds where the page was once folded and opened again: along a crease the two halves lie at slightly
     * different angles, so one side is a touch lighter and the other a touch darker over a wide area, fading
     * slowly away from it, with a thin crisp line on the crease itself. The crease runs almost straight, as a
     * fold does, with only a gentle bend, and dies out towards its ends.
     */
    private fun foldPlanes(pixels: IntArray, width: Int, height: Int, density: Float, light: Boolean, random: Random, plain: Float): List<Crease> {
        val count = 3
        val creases = ArrayList<Crease>(count)
        for (f in 0 until count) {
            val across = f == count - 1
            val angle = if (across) 1.3f + (random.nextFloat() - 0.5f) * 0.5f else (random.nextFloat() - 0.5f) * 1.2f
            // One main fold; the others fainter.
            val strength = when (f) { 0 -> 1f; count - 1 -> 0.5f; else -> 0.55f }
            val crease = Crease(
                cx = random.nextFloat() * width, cy = random.nextFloat() * height,
                ax = cos(angle), ay = sin(angle),
                length = if (across) height * 1.4f else width * (1.2f + random.nextFloat()),
                bend = (if (across) 6f else 9f) * density, bendLength = (if (across) 150f else 180f) * density,
                phase = random.nextFloat() * 6.2832f,
                jitter = 1.4f * density, jitterLength = 23f * density, phase2 = random.nextFloat() * 6.2832f,
                strength = strength,
            )
            creases += crease
            val lit = (if (light) 7f else 5f) * strength
            val shaded = (if (light) 8f else 4f) * strength
            val line = (if (light) 4f else 5f) * strength
            val wideLit = 55f * density
            val wideShade = 40f * density
            val thin = 0.45f * density
            for (y in 0 until height) {
                val texture = if (plain > 0f) (y / plain).coerceAtMost(1f) else 1f
                if (texture <= 0f) continue
                for (x in 0 until width) {
                    val fade = crease.fade(x.toFloat(), y.toFloat())
                    if (fade <= 0f) continue
                    val d = crease.offset(x.toFloat(), y.toFloat())
                    var v = if (d > 0) lit * kotlin.math.exp(-d / wideLit) else -shaded * kotlin.math.exp(d / wideShade)
                    val q = d / thin
                    if (q > -6f && q < 6f) v += line * kotlin.math.exp(-q * q)
                    v *= fade * texture
                    if (v > -0.3f && v < 0.3f) continue
                    val p = pixels[y * width + x]
                    // On parchment shade is brown (it takes more blue and green than red).
                    val kg = if (light && v < 0) 1.15f else 1f
                    val kb = if (light && v < 0) 1.35f else 1f
                    pixels[y * width + x] = (0xFF shl 24) or
                        ((((p shr 16) and 0xFF) + v).toInt().coerceIn(0, 255) shl 16) or
                        ((((p shr 8) and 0xFF) + v * kg).toInt().coerceIn(0, 255) shl 8) or
                        (((p and 0xFF) + v * kb).toInt().coerceIn(0, 255))
                }
            }
        }
        return creases
    }

    /** The brown of the parchment's aged ends (no alpha). */
    private const val BURN = 0x6B4A22

    private fun shade(pixels: IntArray, width: Int, height: Int, x: Int, y: Int, by: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        val p = pixels[y * width + x]
        fun ch(shift: Int) = (((p shr shift) and 0xFF) + by).coerceIn(0, 255)
        pixels[y * width + x] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
