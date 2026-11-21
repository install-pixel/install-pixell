package com.offlinekeyboard.keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposePathEffect
import android.graphics.CornerPathEffect
import android.graphics.DiscretePathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * The keyboard's own line icons, drawn on a 24×24 grid with one stroke width and round ends, so they
 * look the same on every phone (system emoji differ between vendors and ignore the theme colours).
 */
enum class Icon {
    SHIFT, SHIFT_ON, CAPS, BACKSPACE, ENTER, SEARCH, SEND, DONE, TRANSLATE,
    MIC, STOP, CLIPBOARD, GEAR, KEYBOARD, PHONE, CLOSE, HIDE,
    LEFT, RIGHT, UP, DOWN, BLUETOOTH, EMOJI, UNDO, STICKER, MODES,
    SPEAKER, SPEAKER_OFF, TOUCHPAD, WINDOWS,
    VOLUME_DOWN, VOLUME_UP, PREVIOUS, NEXT, SEEK_BACK, SEEK_FORWARD, PLAY_PAUSE, MUSIC,
}

object Icons {

    private const val GRID = 24f
    private const val STROKE = 2f

    private val paths = HashMap<Icon, Pair<Path, Path?>>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * The theme's line style: plain; glass (1.8 lines with a soft glow of the same line 4.4 wide at 18 %); or
     * sheet (a pencil line that trembles a little). Set when the keyboard's theme changes.
     */
    var style: SurfaceStyle = SurfaceStyle.FLAT

    private val tremble = ComposePathEffect(CornerPathEffect(0.6f), DiscretePathEffect(1.6f, 0.45f))

    /** Draws [icon] centred at ([cx], [cy]) in a square of [size] pixels. */
    fun draw(canvas: Canvas, icon: Icon, cx: Float, cy: Float, size: Float, color: Int, look: SurfaceStyle = style) {
        val (stroke, fill) = paths.getOrPut(icon) { build(icon) }
        val scale = size / GRID
        canvas.save()
        canvas.translate(cx - size / 2, cy - size / 2)
        canvas.scale(scale, scale)
        paint.color = color
        paint.pathEffect = if (look == SurfaceStyle.SHEET) tremble else null
        val width = when (look) {
            SurfaceStyle.FLAT -> STROKE
            SurfaceStyle.GLASS -> 1.8f
            SurfaceStyle.SHEET -> 2.2f
            SurfaceStyle.TYPEWRITER -> 2f
        }
        if (look == SurfaceStyle.GLASS) {
            // The glow: the same line, wider and faint.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4.4f
            paint.alpha = (Color.alpha(color) * 0.18f).toInt()
            fill?.let { canvas.drawPath(it, paint) }
            canvas.drawPath(stroke, paint)
            paint.color = color
        }
        if (fill != null) {
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = width
            canvas.drawPath(fill, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        canvas.drawPath(stroke, paint)
        paint.pathEffect = null
        canvas.restore()
    }

    /** Returns the outline path and an optional path to fill. */
    private fun build(icon: Icon): Pair<Path, Path?> {
        val p = Path()
        var fill: Path? = null
        when (icon) {
            Icon.SHIFT -> p.shiftArrow()
            Icon.SHIFT_ON -> fill = Path().apply { shiftArrow() }
            Icon.CAPS -> {
                fill = Path().apply { shiftArrow(top = 3f, bottom = 16f) }
                p.line(8.5f, 20.5f, 15.5f, 20.5f)
            }
            Icon.BACKSPACE -> {
                p.moveTo(8.5f, 5f); p.lineTo(20f, 5f); p.lineTo(20f, 19f); p.lineTo(8.5f, 19f); p.lineTo(2.5f, 12f); p.close()
                p.line(11.5f, 9f, 16.5f, 15f); p.line(16.5f, 9f, 11.5f, 15f)
            }
            Icon.ENTER -> {
                p.moveTo(19.5f, 5f); p.lineTo(19.5f, 11f)
                p.quadTo(19.5f, 14f, 16.5f, 14f); p.lineTo(5f, 14f)
                p.moveTo(9f, 10f); p.lineTo(5f, 14f); p.lineTo(9f, 18f)
            }
            Icon.SEARCH -> {
                p.addCircle(10.5f, 10.5f, 6f, Path.Direction.CW)
                p.line(15f, 15f, 20f, 20f)
            }
            Icon.SEND, Icon.RIGHT -> {
                p.line(4.5f, 12f, 19f, 12f)
                p.moveTo(13f, 6f); p.lineTo(19f, 12f); p.lineTo(13f, 18f)
            }
            Icon.LEFT -> {
                p.line(19.5f, 12f, 5f, 12f)
                p.moveTo(11f, 6f); p.lineTo(5f, 12f); p.lineTo(11f, 18f)
            }
            Icon.UP -> {
                p.line(12f, 19.5f, 12f, 5f)
                p.moveTo(6f, 11f); p.lineTo(12f, 5f); p.lineTo(18f, 11f)
            }
            Icon.DOWN -> {
                p.line(12f, 4.5f, 12f, 19f)
                p.moveTo(6f, 13f); p.lineTo(12f, 19f); p.lineTo(18f, 13f)
            }
            Icon.DONE -> { p.moveTo(5f, 12.5f); p.lineTo(10f, 17.5f); p.lineTo(19f, 7f) }
            Icon.TRANSLATE -> {
                p.line(4f, 8f, 19f, 8f); p.moveTo(15.5f, 4.5f); p.lineTo(19f, 8f); p.lineTo(15.5f, 11.5f)
                p.line(20f, 16f, 5f, 16f); p.moveTo(8.5f, 12.5f); p.lineTo(5f, 16f); p.lineTo(8.5f, 19.5f)
            }
            Icon.MIC -> {
                p.addRoundRect(RectF(9f, 3f, 15f, 14f), 3f, 3f, Path.Direction.CW)
                p.moveTo(5.5f, 11f); p.quadTo(5.5f, 17.5f, 12f, 17.5f); p.quadTo(18.5f, 17.5f, 18.5f, 11f)
                p.line(12f, 17.5f, 12f, 21f)
            }
            Icon.STOP -> fill = Path().apply { addRoundRect(RectF(7f, 7f, 17f, 17f), 2f, 2f, Path.Direction.CW) }
            Icon.CLIPBOARD -> {
                p.moveTo(9f, 4.5f); p.lineTo(6.5f, 4.5f); p.quadTo(5f, 4.5f, 5f, 6f); p.lineTo(5f, 19.5f)
                p.quadTo(5f, 21f, 6.5f, 21f); p.lineTo(17.5f, 21f); p.quadTo(19f, 21f, 19f, 19.5f); p.lineTo(19f, 6f)
                p.quadTo(19f, 4.5f, 17.5f, 4.5f); p.lineTo(15f, 4.5f)
                p.addRoundRect(RectF(9f, 2.5f, 15f, 6.5f), 1.5f, 1.5f, Path.Direction.CW)
                p.line(8.5f, 11f, 15.5f, 11f); p.moveTo(13.5f, 9f); p.lineTo(15.5f, 11f); p.lineTo(13.5f, 13f)
                p.line(15.5f, 16.5f, 8.5f, 16.5f); p.moveTo(10.5f, 14.5f); p.lineTo(8.5f, 16.5f); p.lineTo(10.5f, 18.5f)
            }
            Icon.GEAR -> {
                p.addCircle(12f, 12f, 3f, Path.Direction.CW)
                p.addCircle(12f, 12f, 6.5f, Path.Direction.CW)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    p.line(
                        12f + 6.5f * cos(a).toFloat(), 12f + 6.5f * sin(a).toFloat(),
                        12f + 9f * cos(a).toFloat(), 12f + 9f * sin(a).toFloat(),
                    )
                }
            }
            Icon.WINDOWS -> {
                // Two windows, one over the other: switch between windows (Alt+Tab).
                p.addRoundRect(RectF(3f, 7f, 15f, 17f), 1.5f, 1.5f, Path.Direction.CW)
                p.moveTo(8f, 7f); p.lineTo(8f, 5.5f); p.quadTo(8f, 4f, 9.5f, 4f); p.lineTo(19.5f, 4f)
                p.quadTo(21f, 4f, 21f, 5.5f); p.lineTo(21f, 12.5f); p.quadTo(21f, 14f, 19.5f, 14f); p.lineTo(15f, 14f)
                p.line(4f, 10f, 14f, 10f)
            }
            Icon.KEYBOARD -> {
                p.addRoundRect(RectF(2.5f, 6f, 21.5f, 18f), 2f, 2f, Path.Direction.CW)
                for (x in listOf(6.5f, 10f, 13.5f, 17.5f)) p.line(x, 10f, x + 0.01f, 10f)
                p.line(8f, 14.5f, 16f, 14.5f)
            }
            Icon.PHONE -> {
                p.addRoundRect(RectF(7f, 2.5f, 17f, 21.5f), 2.5f, 2.5f, Path.Direction.CW)
                p.line(11f, 18.5f, 13f, 18.5f)
            }
            Icon.CLOSE -> { p.line(6f, 6f, 18f, 18f); p.line(18f, 6f, 6f, 18f) }
            Icon.BLUETOOTH -> {
                p.moveTo(6.5f, 7.5f); p.lineTo(17f, 16.5f); p.lineTo(12f, 21f); p.lineTo(12f, 3f)
                p.lineTo(17f, 7.5f); p.lineTo(6.5f, 16.5f)
            }
            Icon.EMOJI -> {
                p.addCircle(12f, 12f, 8.5f, Path.Direction.CW)
                p.line(9f, 9.5f, 9f, 10.5f)
                p.line(15f, 9.5f, 15f, 10.5f)
                p.moveTo(8f, 14f); p.quadTo(12f, 18f, 16f, 14f)
            }
            Icon.STICKER -> {
                // A sticker with a peeled corner and a smile.
                p.moveTo(14.5f, 20.5f); p.lineTo(6f, 20.5f); p.quadTo(3.5f, 20.5f, 3.5f, 18f); p.lineTo(3.5f, 6f)
                p.quadTo(3.5f, 3.5f, 6f, 3.5f); p.lineTo(18f, 3.5f); p.quadTo(20.5f, 3.5f, 20.5f, 6f); p.lineTo(20.5f, 14.5f)
                p.close()
                p.moveTo(20.5f, 14.5f); p.lineTo(14.5f, 14.5f); p.lineTo(14.5f, 20.5f)
                p.line(9f, 9f, 9f, 10f)
                p.line(15f, 9f, 15f, 10f)
                p.moveTo(8.5f, 13.5f); p.quadTo(10.5f, 15.5f, 12.5f, 15.5f)
            }
            Icon.UNDO -> {
                p.moveTo(9f, 5f); p.lineTo(4.5f, 9.5f); p.lineTo(9f, 14f)
                p.moveTo(4.5f, 9.5f); p.lineTo(14f, 9.5f)
                p.quadTo(19.5f, 9.5f, 19.5f, 14.5f); p.quadTo(19.5f, 19.5f, 14f, 19.5f); p.lineTo(11f, 19.5f)
            }
            Icon.MODES -> {
                // Four small tiles: the input modes.
                p.addRoundRect(RectF(4.5f, 4.5f, 10.5f, 10.5f), 1.5f, 1.5f, Path.Direction.CW)
                p.addRoundRect(RectF(13.5f, 4.5f, 19.5f, 10.5f), 1.5f, 1.5f, Path.Direction.CW)
                p.addRoundRect(RectF(4.5f, 13.5f, 10.5f, 19.5f), 1.5f, 1.5f, Path.Direction.CW)
                p.addRoundRect(RectF(13.5f, 13.5f, 19.5f, 19.5f), 1.5f, 1.5f, Path.Direction.CW)
            }
            Icon.HIDE -> { p.moveTo(6f, 9.5f); p.lineTo(12f, 15.5f); p.lineTo(18f, 9.5f) }
            Icon.SPEAKER, Icon.SPEAKER_OFF -> {
                // A loudspeaker: its box and cone, then sound waves or a cross.
                p.moveTo(4f, 9.5f); p.lineTo(7.5f, 9.5f); p.lineTo(12f, 5.5f); p.lineTo(12f, 18.5f)
                p.lineTo(7.5f, 14.5f); p.lineTo(4f, 14.5f); p.close()
                if (icon == Icon.SPEAKER) {
                    p.addArc(RectF(11f, 8.5f, 17f, 15.5f), -55f, 110f)
                    p.addArc(RectF(11f, 5f, 21f, 19f), -50f, 100f)
                } else {
                    p.line(15.5f, 9.5f, 20.5f, 14.5f)
                    p.line(20.5f, 9.5f, 15.5f, 14.5f)
                }
            }
            Icon.VOLUME_DOWN, Icon.VOLUME_UP -> {
                // A smaller loudspeaker with a minus or a plus beside it.
                p.moveTo(3f, 9.5f); p.lineTo(6.5f, 9.5f); p.lineTo(10.5f, 6f); p.lineTo(10.5f, 18f)
                p.lineTo(6.5f, 14.5f); p.lineTo(3f, 14.5f); p.close()
                p.line(14f, 12f, 21f, 12f)
                if (icon == Icon.VOLUME_UP) p.line(17.5f, 8.5f, 17.5f, 15.5f)
            }
            Icon.SEEK_BACK, Icon.SEEK_FORWARD -> {
                // Two triangles one after the other (drawn for the back way, mirrored for forward).
                val m = icon == Icon.SEEK_FORWARD
                p.triangle(m, 12f, 6.5f, 12f, 17.5f, 4f, 12f)
                p.triangle(m, 20f, 6.5f, 20f, 17.5f, 12f, 12f)
            }
            Icon.PREVIOUS, Icon.NEXT -> {
                // A bar and two triangles running up to it: to the start of the track, or to the next.
                val m = icon == Icon.NEXT
                p.mirrorLine(m, 5f, 6.5f, 5f, 17.5f)
                p.triangle(m, 12.5f, 7f, 12.5f, 17f, 6.5f, 12f)
                p.triangle(m, 19.5f, 7f, 19.5f, 17f, 13.5f, 12f)
            }
            Icon.PLAY_PAUSE -> {
                // Play and pause together: a triangle and two bars.
                p.triangle(false, 3.5f, 6.5f, 3.5f, 17.5f, 12f, 12f)
                p.line(16f, 7f, 16f, 17f)
                p.line(20f, 7f, 20f, 17f)
            }
            Icon.MUSIC -> {
                // A note: its head, the stem and a flag.
                p.addOval(RectF(4.5f, 14.5f, 11.5f, 20f), Path.Direction.CW)
                p.line(11.5f, 17f, 11.5f, 4f)
                p.moveTo(11.5f, 4f); p.cubicTo(13f, 7f, 18.5f, 7f, 18f, 12f)
            }
            Icon.TOUCHPAD -> {
                // A touchpad with its two buttons.
                p.addRoundRect(RectF(3.5f, 4.5f, 20.5f, 19.5f), 3f, 3f, Path.Direction.CW)
                p.line(3.5f, 14.5f, 20.5f, 14.5f)
                p.line(12f, 14.5f, 12f, 19.5f)
            }
        }
        return p to fill
    }

    /** A closed triangle; [mirror] flips it left to right in the 24 grid. */
    private fun Path.triangle(mirror: Boolean, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        fun x(v: Float) = if (mirror) GRID - v else v
        moveTo(x(x1), y1); lineTo(x(x2), y2); lineTo(x(x3), y3); close()
    }

    private fun Path.mirrorLine(mirror: Boolean, x1: Float, y1: Float, x2: Float, y2: Float) {
        fun x(v: Float) = if (mirror) GRID - v else v
        line(x(x1), y1, x(x2), y2)
    }

    private fun Path.line(x1: Float, y1: Float, x2: Float, y2: Float) {
        moveTo(x1, y1)
        lineTo(x2, y2)
    }

    private fun Path.shiftArrow(top: Float = 4f, bottom: Float = 19.5f) {
        val mid = top + (bottom - top) * 0.5f
        moveTo(12f, top); lineTo(20f, mid); lineTo(15.5f, mid); lineTo(15.5f, bottom)
        lineTo(8.5f, bottom); lineTo(8.5f, mid); lineTo(4f, mid); close()
    }
}
