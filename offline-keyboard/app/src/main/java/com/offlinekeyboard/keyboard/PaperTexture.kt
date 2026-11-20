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
    private const val CELL = 28

    /**
     * A sheet of the paper: its picture and where its creases run ([folds]: x, y points along each crease,
     * one list per crease), so typed letters can follow the folds.
     */
    class Sheet(val bitmap: Bitmap, private val folds: List<FloatArray>) {
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

    fun sheet(width: Int, height: Int, density: Float): Sheet? {
        if (width <= 0 || height <= 0) return null
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
                val v = (BASE + (blotch + grain) * texture + lip).toInt().coerceIn(0, 255)
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

    private fun shade(pixels: IntArray, width: Int, height: Int, x: Int, y: Int, by: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        val p = pixels[y * width + x]
        fun ch(shift: Int) = (((p shr shift) and 0xFF) + by).coerceIn(0, 255)
        pixels[y * width + x] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
