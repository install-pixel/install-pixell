package com.blacklaunch.launcher.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/*
 * Малюнок котика без Android: пози й лінії як прості дані (одиниці — dp, котик дивиться праворуч,
 * (0, 0) — під лапами на землі). Екран лише переводить їх у штрихи олівцем — тож малюнок можна перевірити
 * й поза телефоном. Збоку видно одну передню лапу; дальня задня лапа й дальнє вухо — за тілом і головою
 * (тулуб і голова зафарбовані чорним, тож закривають те, що позаду).
 */

/** Що котик робить зараз. */
internal enum class CatAct {
    WALK,

    /** Удар носом: відсахується назад. */
    RECOIL,

    /** Трусить головою. */
    SHAKE,

    /** Сів на хвіст (після удару — з «зірочками»). */
    SIT,

    /** Присів, лапи на голові (удар маківкою знизу). */
    DUCK,

    /** Чухає голову передньою лапою. */
    SCRATCH,

    /** Стоїть, дивиться під ноги. */
    LOOK_DOWN,

    /** Роззирається. */
    LOOK_AROUND,
}

/** Мить анімації: що робить, наскільки просунувся крок, час. */
internal data class CatFrame(
    val act: CatAct,
    val progress: Float,
    val actTime: Float,
    val time: Float,
    val walkPhase: Float,
    val dazed: Boolean,
)

/** Поза на кадр; кути в градусах (0 — донизу, додатні — вперед). */
internal class CatPose {
    var drop = 0f
    var lean = 0f
    var legFar = 0f
    var legNear = 0f
    var legLen = 16f
    var sitting = 0f
    var armNear = 0f
    var armFar = 0f
    var farArmShown = false
    var armLen = 12f
    var headTilt = 0f
    var headDrop = 0f
    var tailWag = 0f
    var bob = 0f
}

private fun ease(t: Float) = (1f - cos(t.coerceIn(0f, 1f) * PI.toFloat())) / 2f

/** Плавно перейти в позу на початку кроку й вийти з неї наприкінці. */
private fun settle(p: Float, inPart: Float = 0.15f, outPart: Float = 0.15f): Float = when {
    p < inPart -> ease(p / inPart)
    p > 1f - outPart -> ease((1f - p) / outPart)
    else -> 1f
}

internal fun catPose(f: CatFrame): CatPose = CatPose().apply {
    val p = f.progress
    val pi = PI.toFloat()
    tailWag = sin(f.time * 2.6f) * 3f
    when (f.act) {
        CatAct.WALK -> {
            val s = sin(f.walkPhase)
            legNear = 22f * s
            legFar = -22f * s
            armNear = -24f * s
            bob = abs(s) * 1.4f
        }
        CatAct.RECOIL -> {
            val k = sin(pi * p)
            lean = -14f * k
            armNear = 55f * k
            legNear = 12f
            legFar = -10f
        }
        CatAct.SHAKE -> {
            headTilt = sin(f.actTime * 26f) * 18f * exp(-f.actTime * 2.2f)
            armNear = 8f
            legNear = 4f
            legFar = -4f
        }
        CatAct.SIT -> {
            val s = settle(p, inPart = if (f.dazed) 0.08f else 0.2f)
            sitting = s
            drop = 12f * s
            legLen = 16f - 4f * s
            legNear = 80f * s
            legFar = 74f * s
            armNear = 18f * s
            lean = -6f * s
            tailWag = sin(f.time * (if (f.dazed) 1.2f else 1.8f)) * 3f
            headTilt = if (f.dazed) sin(f.time * 3f) * 7f * s else 0f
        }
        CatAct.DUCK -> {
            // Присідає, згинаючи лапи, і хапається обома передніми за маківку.
            val s = settle(p, inPart = 0.12f, outPart = 0.2f)
            drop = 5f * s
            headDrop = 2f * s
            legLen = 16f - 4f * s
            legNear = 28f * s
            legFar = -24f * s
            armLen = 12f + 5f * s
            armNear = 170f * s
            armFar = 160f * s
            farArmShown = s > 0.3f
        }
        CatAct.SCRATCH -> {
            val s = settle(p)
            armLen = 12f + 3f * s
            armNear = (150f + sin(f.actTime * 22f) * 12f) * s
            headTilt = 10f * s
            legNear = 4f
            legFar = -4f
        }
        CatAct.LOOK_DOWN -> {
            headTilt = 18f * settle(p, 0.2f, 0.2f)
            armNear = 6f
        }
        CatAct.LOOK_AROUND -> {
            headTilt = sin(p * pi * 2f) * 15f
            armNear = 4f
        }
    }
}

/** Відрізок контуру. */
internal sealed interface CatSeg {
    data class Move(val x: Float, val y: Float) : CatSeg
    data class Line(val x: Float, val y: Float) : CatSeg
    data class Cubic(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : CatSeg
    data object Close : CatSeg
}

/** Чим заливати: нічим, чорним (закриває те, що позаду) чи «олівцем» (носик, зіниця). */
internal enum class CatFill { NONE, BLACK, PENCIL }

/** Одна фігура: контур, заливка, товщина штриха (0 — без штриха) і яскравість. */
internal data class CatShape(val segs: List<CatSeg>, val fill: CatFill, val width: Float, val alpha: Float)

/** Будує фігури з поворотами (нахил тіла, голова) — точки одразу перетворені, екрану лишається намалювати. */
private class CatPen {
    val shapes = ArrayList<CatShape>()

    // Афінне перетворення x' = a x + c y + e, y' = b x + d y + f.
    private var m = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    private val saved = ArrayDeque<FloatArray>()

    fun save() = saved.addLast(m.copyOf())
    fun restore() {
        m = saved.removeLast()
    }

    /** Поворот на [deg] за годинниковою навколо ([px], [py]) — у поточних координатах. */
    fun rotate(deg: Float, px: Float, py: Float) {
        val r = deg * PI.toFloat() / 180f
        val cs = cos(r)
        val sn = sin(r)
        // T(p) · R · T(−p), потім множимо праворуч на поточне.
        val ra = cs
        val rb = sn
        val rc = -sn
        val rd = cs
        val re = px - cs * px + sn * py
        val rf = py - sn * px - cs * py
        val (a, b, c, d, e) = m
        val f = m[5]
        m = floatArrayOf(
            a * ra + c * rb, b * ra + d * rb,
            a * rc + c * rd, b * rc + d * rd,
            a * re + c * rf + e, b * re + d * rf + f,
        )
    }

    private fun tx(x: Float, y: Float) = m[0] * x + m[2] * y + m[4]
    private fun ty(x: Float, y: Float) = m[1] * x + m[3] * y + m[5]

    inner class Builder {
        val segs = ArrayList<CatSeg>()
        fun move(x: Float, y: Float) = apply { segs += CatSeg.Move(tx(x, y), ty(x, y)) }
        fun line(x: Float, y: Float) = apply { segs += CatSeg.Line(tx(x, y), ty(x, y)) }
        fun cubic(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = apply {
            segs += CatSeg.Cubic(tx(x1, y1), ty(x1, y1), tx(x2, y2), ty(x2, y2), tx(x, y), ty(x, y))
        }
        fun close() = apply { segs += CatSeg.Close }
    }

    fun shape(fill: CatFill = CatFill.NONE, width: Float = 1.6f, alpha: Float = 0.9f, build: Builder.() -> Unit) {
        shapes += CatShape(Builder().apply(build).segs, fill, width, alpha)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 1.6f, alpha: Float = 0.9f) =
        shape(width = width, alpha = alpha) { move(x1, y1); line(x2, y2) }

    /** Овал через чотири кубічні дуги. */
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, fill: CatFill = CatFill.NONE, width: Float = 1.6f, alpha: Float = 0.9f) {
        val k = 0.5523f
        shape(fill, width, alpha) {
            move(cx + rx, cy)
            cubic(cx + rx, cy + ry * k, cx + rx * k, cy + ry, cx, cy + ry)
            cubic(cx - rx * k, cy + ry, cx - rx, cy + ry * k, cx - rx, cy)
            cubic(cx - rx, cy - ry * k, cx - rx * k, cy - ry, cx, cy - ry)
            cubic(cx + rx * k, cy - ry, cx + rx, cy - ry * k, cx + rx, cy)
            close()
        }
    }
}

private fun dir(deg: Float): Pair<Float, Float> {
    val r = deg * PI.toFloat() / 180f
    return sin(r) to cos(r)
}

/** Межі котика (для зіткнень): від лап до кінчиків вух, з хвостом позаду. */
internal const val CAT_HALF_WIDTH = 16f
internal const val CAT_HEIGHT = 64f

/** Усі фігури кадру — від дальніх до ближніх (порядок і є «що що закриває»). */
internal fun catShapes(f: CatFrame): List<CatShape> {
    val pose = catPose(f)
    val pen = CatPen()
    pen.rotate(pose.lean, 0f, 0f)
    val hx = 0f
    val hy = -19f - pose.bob + pose.drop
    val sitting = pose.sitting > 0.5f

    // Хвіст: стоячи — вгору плавною дугою, сидячи — лежить на землі й закручується кінчиком.
    pen.shape(width = 2.4f) {
        move(hx - 7f, hy - 3f)
        if (sitting) {
            cubic(hx - 14f, 0f, hx - 21f + pose.tailWag, 0f, hx - 23f + pose.tailWag, -6f)
        } else {
            cubic(hx - 14f, hy - 2f, hx - 20f + pose.tailWag, hy - 11f, hx - 16f + pose.tailWag, hy - 22f)
        }
    }

    // Дальня задня лапа — тьмяніша, наполовину за тулубом.
    fun leg(angle: Float, alpha: Float) {
        val (dx, dy) = dir(angle)
        val kx = hx - 1f
        val ky = hy + 1f
        val fx = kx + dx * pose.legLen
        val fy = ky + dy * pose.legLen
        pen.line(kx, ky, fx, fy, width = 2.2f, alpha = alpha)
        pen.oval(fx + 2.4f, fy - 1.2f, 3.2f, 1.6f, CatFill.BLACK, width = 1.4f, alpha = alpha)
    }
    leg(pose.legFar, 0.5f)

    // Дальня передня лапа — лише коли обидві тягнуться до голови.
    val shoulderX = hx + 3f
    val shoulderY = hy - 16f
    if (pose.farArmShown) {
        val (dx, dy) = dir(pose.armFar)
        pen.line(shoulderX - 1f, shoulderY, shoulderX - 1f + dx * pose.armLen, shoulderY + dy * pose.armLen, width = 2f, alpha = 0.5f)
    }

    // Тулуб — грушею, зафарбований чорним: закриває все, що позаду.
    pen.shape(CatFill.BLACK) {
        move(hx - 1f, hy - 22f)
        cubic(hx + 5f, hy - 23f, hx + 9f, hy - 14f, hx + 8f, hy - 5f)
        cubic(hx + 7.5f, hy + 1f, hx - 6f, hy + 2f, hx - 8f, hy - 4f)
        cubic(hx - 9f, hy - 11f, hx - 6f, hy - 20f, hx - 1f, hy - 22f)
        close()
    }
    // Пухнасті груди й смужки на спині.
    pen.shape(width = 1.1f, alpha = 0.6f) {
        move(hx + 6.2f, hy - 16f); line(hx + 7.6f, hy - 14.6f); line(hx + 6.4f, hy - 13.2f); line(hx + 7.8f, hy - 11.8f)
    }
    pen.line(hx - 7.2f, hy - 15f, hx - 4.6f, hy - 14.2f, width = 1.1f, alpha = 0.55f)
    pen.line(hx - 8f, hy - 11f, hx - 5.2f, hy - 10.4f, width = 1.1f, alpha = 0.55f)

    // Ближня задня лапа й стегно над нею.
    leg(pose.legNear, 0.95f)
    pen.oval(hx - 2f, hy - 3f, if (sitting) 7.5f else 6f, if (sitting) 6f else 5f, CatFill.BLACK)

    // Ближня передня лапа: плече → лікоть → лапка.
    run {
        val (dx, dy) = dir(pose.armNear)
        val (bx, by) = dir(pose.armNear + 18f)
        val ex = shoulderX + dx * pose.armLen * 0.55f
        val ey = shoulderY + dy * pose.armLen * 0.55f
        val px = ex + bx * pose.armLen * 0.45f
        val py = ey + by * pose.armLen * 0.45f
        pen.shape(width = 2.2f, alpha = 0.95f) { move(shoulderX, shoulderY); line(ex, ey); line(px, py) }
        pen.oval(px, py, 2.1f, 1.7f, CatFill.BLACK, width = 1.3f)
    }

    // Голова: нахиляється / трусить навколо шиї.
    val cx = hx + 2.5f
    val cy = hy - 30f + pose.headDrop
    pen.save()
    pen.rotate(pose.headTilt, hx + 1.5f, hy - 22f)
    // Дальнє вухо — позаду голови (голова закриє його основу).
    pen.shape(CatFill.BLACK, alpha = 0.75f) {
        move(cx - 5.5f, cy - 5.5f); line(cx - 4.5f, cy - 14f); line(cx + 0.5f, cy - 8f); close()
    }
    // Голова з мордочкою: лоб, перенісся, носик попереду, підборіддя, щока.
    pen.shape(CatFill.BLACK) {
        move(cx - 1f, cy - 8.5f)
        cubic(cx + 5f, cy - 9f, cx + 8.5f, cy - 5f, cx + 9f, cy - 1.5f)
        cubic(cx + 11f, cy - 0.5f, cx + 11f, cy + 3f, cx + 8.5f, cy + 4f)
        cubic(cx + 6f, cy + 6.5f, cx + 1f, cy + 7f, cx - 3f, cy + 5.5f)
        cubic(cx - 8f, cy + 4f, cx - 8.5f, cy - 5f, cx - 1f, cy - 8.5f)
        close()
    }
    // Ближнє вухо з лінією всередині.
    pen.shape(CatFill.BLACK) {
        move(cx + 0.5f, cy - 8f); line(cx + 3f, cy - 15.5f); line(cx + 6.5f, cy - 6.5f)
    }
    pen.line(cx + 2.9f, cy - 12.5f, cx + 4.4f, cy - 8.2f, width = 1f, alpha = 0.6f)
    // Смужки на лобі.
    pen.line(cx + 0.8f, cy - 6.6f, cx + 1.6f, cy - 4.6f, width = 1f, alpha = 0.55f)
    pen.line(cx - 1.4f, cy - 6.8f, cx - 1f, cy - 4.9f, width = 1f, alpha = 0.55f)
    // Око: мигдалик із зіницею; присів — зажмурився; оглушило — «хрестик» і зірочки.
    when {
        f.dazed && f.act == CatAct.SIT -> {
            pen.line(cx + 3.6f, cy - 3.4f, cx + 6f, cy - 1f, width = 1.2f)
            pen.line(cx + 6f, cy - 3.4f, cx + 3.6f, cy - 1f, width = 1.2f)
            for (i in 0 until 3) {
                val a = f.time * 3f + i * 2.1f
                val sx = cx + 1f + cos(a) * 13f
                val sy = cy - 12f + sin(a) * 3.5f
                pen.line(sx - 1.8f, sy, sx + 1.8f, sy, width = 1f)
                pen.line(sx, sy - 1.8f, sx, sy + 1.8f, width = 1f)
            }
        }
        f.act == CatAct.DUCK -> pen.shape(width = 1.2f) { move(cx + 3.2f, cy - 2.4f); cubic(cx + 4.2f, cy - 1.2f, cx + 5.4f, cy - 1.2f, cx + 6.3f, cy - 2.4f) }
        else -> {
            pen.shape(width = 1.2f) { move(cx + 3f, cy - 2f); cubic(cx + 4f, cy - 3.6f, cx + 5.5f, cy - 3.6f, cx + 6.4f, cy - 2.2f) }
            pen.oval(cx + 5f, cy - 2f, 0.9f, 1.1f, CatFill.PENCIL, width = 0f, alpha = 1f)
        }
    }
    // Носик, ротик і тонкі вуса від щоки вперед.
    pen.shape(CatFill.PENCIL, width = 0f, alpha = 0.95f) { move(cx + 9.3f, cy - 1.3f); line(cx + 10.7f, cy - 1.1f); line(cx + 10.1f, cy); close() }
    pen.shape(width = 1f, alpha = 0.8f) { move(cx + 10f, cy); cubic(cx + 9.9f, cy + 1.4f, cx + 9f, cy + 1.8f, cx + 8.2f, cy + 1.3f) }
    pen.line(cx + 8.4f, cy + 0.6f, cx + 15f, cy - 1.4f, width = 0.7f, alpha = 0.55f)
    pen.line(cx + 8.4f, cy + 1.2f, cx + 15.5f, cy + 1.1f, width = 0.7f, alpha = 0.55f)
    pen.line(cx + 8.2f, cy + 1.8f, cx + 14.5f, cy + 3.5f, width = 0.7f, alpha = 0.55f)
    pen.restore()
    return pen.shapes
}
