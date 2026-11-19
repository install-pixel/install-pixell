package com.offlinekeyboard.keyboard

import android.graphics.Bitmap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Black paper for the typewriter's strip: charcoal, with a soft grain, a few creases where it was crumpled
 * and the shadow of the platen along its top. Made once per size, always the same.
 */
object PaperTexture {

    private const val BASE = 0x17
    private const val CELL = 28

    fun make(width: Int, height: Int, density: Float): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val random = Random(7)
        // Soft blotches: random values on a coarse grid, blended between the grid points.
        val gw = width / CELL + 2
        val gh = height / CELL + 2
        val grid = FloatArray(gw * gh) { random.nextFloat() * 2f - 1f }
        val pixels = IntArray(width * height)
        val shadow = 7f * density
        for (y in 0 until height) {
            val gy = y / CELL.toFloat()
            val y0 = gy.toInt()
            val fy = gy - y0
            // The platen's shadow at the top, and a slightly lighter lip at the paper's lower edge.
            val top = if (y < shadow) (1f - y / shadow) * -9f else 0f
            val lip = if (y >= height - 1) 6f else 0f
            for (x in 0 until width) {
                val gx = x / CELL.toFloat()
                val x0 = gx.toInt()
                val fx = gx - x0
                val a = grid[y0 * gw + x0] * (1 - fx) + grid[y0 * gw + x0 + 1] * fx
                val b = grid[(y0 + 1) * gw + x0] * (1 - fx) + grid[(y0 + 1) * gw + x0 + 1] * fx
                val blotch = (a * (1 - fy) + b * fy) * 3.5f
                val grain = (random.nextFloat() - 0.5f) * 5f
                val v = (BASE + blotch + grain + top + lip).toInt().coerceIn(0, 255)
                // A touch warmer than grey, like charcoal paper.
                pixels[y * width + x] = (0xFF shl 24) or ((v + 1).coerceAtMost(255) shl 16) or (v shl 8) or (v - 1).coerceAtLeast(0)
            }
        }
        // Creases: long faint folds, a lit edge with its shadow beside it.
        repeat(2 + width / 360) {
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
                shade(pixels, width, height, ix, iy, 9)
                shade(pixels, width, height, ix, iy + 1, -7)
                x += dx
                y += dy + (random.nextFloat() - 0.5f) * 0.3f
                walked += 1f
            }
        }
        return runCatching { Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
    }

    private fun shade(pixels: IntArray, width: Int, height: Int, x: Int, y: Int, by: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        val p = pixels[y * width + x]
        fun ch(shift: Int) = (((p shr shift) and 0xFF) + by).coerceIn(0, 255)
        pixels[y * width + x] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
