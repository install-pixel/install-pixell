package com.blacklaunch.paleta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.blacklaunch.paleta.pack.BoxSize
import com.blacklaunch.paleta.pack.PALLET_HEIGHT_MM
import com.blacklaunch.paleta.pack.PALLET_L
import com.blacklaunch.paleta.pack.PALLET_W
import com.blacklaunch.paleta.pack.Placed
import com.blacklaunch.paleta.pack.sx
import com.blacklaunch.paleta.pack.sy
import com.blacklaunch.paleta.ui.theme.Oled
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val DEFAULT_YAW = -35f
private const val DEFAULT_PITCH = 26f

/** Коробка в просторі (мм): від (x0, y0, z0) до (x1, y1, z1); [layer] — номер шару. */
private class Cuboid(val x0: Float, val y0: Float, val z0: Float, val x1: Float, val y1: Float, val z1: Float, val layer: Int)

/** Грані коробки: індекси вершин (проти годинникової, якщо дивитись ззовні) і нормаль. */
private val FACES = listOf(
    intArrayOf(4, 5, 6, 7) to floatArrayOf(0f, 0f, 1f), // верх
    intArrayOf(0, 1, 5, 4) to floatArrayOf(0f, -1f, 0f), // перед (y0)
    intArrayOf(2, 3, 7, 6) to floatArrayOf(0f, 1f, 0f), // зад (y1)
    intArrayOf(0, 3, 7, 4) to floatArrayOf(-1f, 0f, 0f), // ліво (x0)
    intArrayOf(1, 2, 6, 5) to floatArrayOf(1f, 0f, 0f), // право (x1)
    intArrayOf(0, 1, 2, 3) to floatArrayOf(0f, 0f, -1f), // низ
)

/**
 * Уся палета в об'ємі: дерев'яна основа (15 см) і всі шари коробок. Лише перегляд: тягни пальцем — крутити навколо
 * й нахиляти, подвійний дотик — як було. Повних шарів [layers]; якщо [extra] > 0 — ще неповний шар з перших [extra]
 * коробок розкладки. Кольори — з токенів теми.
 */
@Composable
fun Pallet3D(layout: List<Placed>, box: BoxSize?, boxH: Int?, layers: Int, extra: Int, modifier: Modifier = Modifier) {
    val yaw = remember { mutableFloatStateOf(DEFAULT_YAW) }
    val pitch = remember { mutableFloatStateOf(DEFAULT_PITCH) }
    val haptics = LocalHaptics.current
    val cuboids = remember(layout, box, boxH, layers, extra) {
        val out = ArrayList<Cuboid>()
        if (box != null && boxH != null && boxH > 0) {
            fun layer(k: Int, list: List<Placed>) {
                val z0 = (PALLET_HEIGHT_MM + k * boxH).toFloat()
                list.forEach { p -> out += Cuboid(p.x.toFloat(), p.y.toFloat(), z0, (p.x + p.sx(box)).toFloat(), (p.y + p.sy(box)).toFloat(), z0 + boxH, k) }
            }
            for (k in 0 until layers) layer(k, layout)
            if (extra > 0) layer(layers, layout.take(extra))
        }
        out
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(PALLET_ASPECT)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    yaw.floatValue = (yaw.floatValue + drag.x * 0.35f) % 360f
                    pitch.floatValue = (pitch.floatValue - drag.y * 0.3f).coerceIn(4f, 85f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    haptics.tap()
                    yaw.floatValue = DEFAULT_YAW
                    pitch.floatValue = DEFAULT_PITCH
                })
            },
    ) {
        val top = cuboids.maxOfOrNull { it.z1 } ?: PALLET_HEIGHT_MM.toFloat()
        val total = maxOf(top, PALLET_HEIGHT_MM + 400f)
        val cx = PALLET_L / 2f
        val cy = PALLET_W / 2f
        val cz = total / 2f
        val radius = sqrt(cx * cx + cy * cy + cz * cz)
        val scale = minOf(size.width, size.height) / (2f * radius) * 0.98f
        val th = Math.toRadians(yaw.floatValue.toDouble())
        val ph = Math.toRadians(pitch.floatValue.toDouble())
        val ct = cos(th).toFloat()
        val st = sin(th).toFloat()
        val cp = cos(ph).toFloat()
        val sp = sin(ph).toFloat()
        val mid = Offset(size.width / 2f, size.height / 2f)
        val camera = Camera(ct, st, cp, sp, cx, cy, cz, scale, mid)
        val glass = AppStyle.glass

        // Основа палети — завжди під усім, тож першою.
        drawCuboid(camera, Cuboid(0f, 0f, 0f, PALLET_L.toFloat(), PALLET_W.toFloat(), PALLET_HEIGHT_MM.toFloat(), -1), glass, pallet = true)
        // Нижні шари — раніше (згори їх не видно крізь верхні); у шарі — від дальніх до ближчих.
        val sorted = cuboids.sortedWith(compareBy<Cuboid> { it.layer }.thenByDescending { camera.depthXY((it.x0 + it.x1) / 2f, (it.y0 + it.y1) / 2f) })
        val thin = sorted.size > 600
        sorted.forEach { drawCuboid(camera, it, glass, pallet = false, thin = thin) }
    }
}

/** Ортогональна камера: поворот навколо вертикалі ([ct], [st]) і нахил ([cp], [sp]). */
private class Camera(
    val ct: Float, val st: Float, val cp: Float, val sp: Float,
    val cx: Float, val cy: Float, val cz: Float,
    val scale: Float, val mid: Offset,
) {
    fun project(x: Float, y: Float, z: Float): Offset {
        val dx = x - cx
        val dy = y - cy
        val dz = z - cz
        val x1 = dx * ct - dy * st
        val y1 = dx * st + dy * ct
        val up = dz * cp + y1 * sp
        return Offset(mid.x + x1 * scale, mid.y - up * scale)
    }

    /** Глибина в площині (без висоти): більше — далі від глядача. */
    fun depthXY(x: Float, y: Float): Float = (x - cx) * st + (y - cy) * ct

    /** Чи грань з нормаллю (nx, ny, nz) дивиться на глядача. */
    fun facing(nx: Float, ny: Float, nz: Float): Float {
        val n1y = nx * st + ny * ct
        return n1y * cp - nz * sp
    }

    /** Наскільки грань освітлена (світло зверху-зліва-спереду), 0…1. */
    fun light(nx: Float, ny: Float, nz: Float): Float {
        val n1x = nx * ct - ny * st
        return (0.55f + 0.45f * nz + 0.18f * -n1x).coerceIn(0f, 1f)
    }
}

private fun DrawScope.drawCuboid(cam: Camera, c: Cuboid, glass: Boolean, pallet: Boolean, thin: Boolean = false) {
    val v = arrayOf(
        cam.project(c.x0, c.y0, c.z0), cam.project(c.x1, c.y0, c.z0), cam.project(c.x1, c.y1, c.z0), cam.project(c.x0, c.y1, c.z0),
        cam.project(c.x0, c.y0, c.z1), cam.project(c.x1, c.y0, c.z1), cam.project(c.x1, c.y1, c.z1), cam.project(c.x0, c.y1, c.z1),
    )
    val stroke = Stroke((if (thin) 0.5f else if (pallet) 1f else 0.9f).dp.toPx())
    for ((idx, n) in FACES) {
        if (cam.facing(n[0], n[1], n[2]) >= -0.0001f) continue
        val path = Path().apply {
            moveTo(v[idx[0]].x, v[idx[0]].y)
            for (k in 1..3) lineTo(v[idx[k]].x, v[idx[k]].y)
            close()
        }
        val l = cam.light(n[0], n[1], n[2])
        if (glass) {
            // «Скло»: темне скло, верх світліший; кант — тонкий світлий.
            val fill = if (pallet) lerp(Color(0xFF0E0E0E), Color(0xFF1C1C1C), l) else lerp(Color(0xFF111111), Color(0xFF353535), l)
            drawPath(path, fill)
            drawPath(path, Color(if (pallet) 0x40FFFFFF else 0x66FFFFFF), style = stroke)
        } else {
            // «Аркуш»: чорний папір, грані обведені білим олівцем (верх трохи яскравіше).
            drawPath(path, Oled.Black)
            drawPath(path, SheetInk.copy(alpha = if (pallet) 0.4f else (0.55f + 0.35f * l)), style = stroke)
        }
    }
}
