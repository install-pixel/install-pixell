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

/** Початковий вигляд — класичний «три чверті»: довгий бік зліва, видно верх. */
private const val DEFAULT_YAW = 38f
private const val DEFAULT_PITCH = 30f

/** Швидкість обертання: градусів на піксель руху пальця (повільно, щоб точно керувати). */
private const val YAW_PER_PX = 0.18f
private const val PITCH_PER_PX = 0.14f

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
fun Pallet3D(
    layout: List<Placed>,
    box: BoxSize?,
    boxH: Int?,
    layers: Int,
    extra: Int,
    modifier: Modifier = Modifier,
    layout2: List<Placed>? = null,
    /** Підсвітити шари: 0 — непарні (шар 1), 1 — парні (шар 2); інші тьмяніші. null — усі однаково. */
    highlight: Int? = null,
    aspect: Float = PALLET_ASPECT,
) {
    val yaw = remember { mutableFloatStateOf(DEFAULT_YAW) }
    val pitch = remember { mutableFloatStateOf(DEFAULT_PITCH) }
    val haptics = LocalHaptics.current
    val cuboids = remember(layout, layout2, box, boxH, layers, extra) {
        val out = ArrayList<Cuboid>()
        if (box != null && boxH != null && boxH > 0) {
            fun layer(k: Int, list: List<Placed>) {
                val z0 = (PALLET_HEIGHT_MM + k * boxH).toFloat()
                list.forEach { p -> out += Cuboid(p.x.toFloat(), p.y.toFloat(), z0, (p.x + p.sx(box)).toFloat(), (p.y + p.sy(box)).toFloat(), z0 + boxH, k) }
            }
            // Шари чергуються: непарні — перший шар, парні — другий (якщо він свій).
            fun of(k: Int) = if (k % 2 == 1 && layout2 != null) layout2 else layout
            for (k in 0 until layers) layer(k, of(k))
            if (extra > 0) layer(layers, of(layers).take(extra))
        }
        out
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .pointerInput(Unit) {
                // «Береш і крутиш»: палець управо — передній бік їде вправо; униз — палета нахиляється до тебе
                // (видно більше верху).
                detectDragGestures { change, drag ->
                    change.consume()
                    yaw.floatValue = (yaw.floatValue + drag.x * YAW_PER_PX) % 360f
                    pitch.floatValue = (pitch.floatValue + drag.y * PITCH_PER_PX).coerceIn(4f, 85f)
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
        // Масштаб — сталий (палета не «дихає», поки крутиш), а положення — за справжніми межами зображення:
        // палета завжди рівно посередині вікна, і зверху, і знизу.
        // Легка перспектива (камера на відстані ~3,2 радіуса): дальнє трохи менше — як бачить око.
        val dist = radius * 3.2f
        val probe = Camera(ct, st, cp, sp, cx, cy, cz, scale, Offset.Zero, dist)
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (k in 0 until 8) {
            val p = probe.project(if (k and 1 == 0) 0f else PALLET_L.toFloat(), if (k and 2 == 0) 0f else PALLET_W.toFloat(), if (k and 4 == 0) 0f else top)
            minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x); minY = minOf(minY, p.y); maxY = maxOf(maxY, p.y)
        }
        val mid = Offset(size.width / 2f - (minX + maxX) / 2f, size.height / 2f - (minY + maxY) / 2f)
        val camera = Camera(ct, st, cp, sp, cx, cy, cz, scale, mid, dist)
        val glass = AppStyle.glass

        // Основа палети — завжди під усім, тож першою.
        drawCuboid(camera, Cuboid(0f, 0f, 0f, PALLET_L.toFloat(), PALLET_W.toFloat(), PALLET_HEIGHT_MM.toFloat(), -1), glass, pallet = true, dim = false)
        // Точний порядок малювання: що за чим стоїть (див. drawOrder), щоб ближні коробки завжди закривали дальні.
        val thin = cuboids.size > 600
        drawOrder(cuboids, camera).forEach {
            val c = cuboids[it]
            drawCuboid(camera, c, glass, pallet = false, thin = thin, dim = highlight != null && c.layer % 2 != highlight)
        }
    }
}

/** Камера: поворот навколо вертикалі ([ct], [st]), нахил ([cp], [sp]) і легка перспектива з відстані [dist]. */
private class Camera(
    val ct: Float, val st: Float, val cp: Float, val sp: Float,
    val cx: Float, val cy: Float, val cz: Float,
    val scale: Float, val mid: Offset, val dist: Float,
) {
    fun project(x: Float, y: Float, z: Float): Offset {
        val dx = x - cx
        val dy = y - cy
        val dz = z - cz
        val x1 = dx * ct - dy * st
        val y1 = dx * st + dy * ct
        val up = dz * cp + y1 * sp
        val depth = y1 * cp - dz * sp
        val f = dist / (dist + depth)
        return Offset(mid.x + x1 * scale * f, mid.y - up * scale * f)
    }

    /** Глибина точки: більше — далі від глядача. */
    fun depth(x: Float, y: Float, z: Float): Float = ((x - cx) * st + (y - cy) * ct) * cp - (z - cz) * sp

    /** Де глядач у світових координатах (мм). */
    val viewer: FloatArray = floatArrayOf(cx - st * cp * dist, cy - ct * cp * dist, cz + sp * dist)

    /** Чи видно грань: глядач з того боку площини грані, куди дивиться нормаль ([axis] 0/1/2, [sign] ±1). */
    fun sees(axis: Int, plane: Float, sign: Float): Boolean = (viewer[axis] - plane) * sign > 0f

    /** Наскільки грань освітлена (світло зверху-зліва-спереду), 0…1. */
    fun light(nx: Float, ny: Float, nz: Float): Float {
        val n1x = nx * ct - ny * st
        return (0.55f + 0.45f * nz + 0.18f * -n1x).coerceIn(0f, 1f)
    }
}

private fun DrawScope.drawCuboid(cam: Camera, c: Cuboid, glass: Boolean, pallet: Boolean, thin: Boolean = false, dim: Boolean = false) {
    val v = arrayOf(
        cam.project(c.x0, c.y0, c.z0), cam.project(c.x1, c.y0, c.z0), cam.project(c.x1, c.y1, c.z0), cam.project(c.x0, c.y1, c.z0),
        cam.project(c.x0, c.y0, c.z1), cam.project(c.x1, c.y0, c.z1), cam.project(c.x1, c.y1, c.z1), cam.project(c.x0, c.y1, c.z1),
    )
    val stroke = Stroke((if (thin) 0.5f else if (pallet) 1f else 0.9f).dp.toPx())
    for ((idx, n) in FACES) {
        // Видимість — за положенням глядача (з перспективою): площина грані й з якого боку він.
        val visible = when {
            n[2] > 0f -> cam.sees(2, c.z1, 1f)
            n[2] < 0f -> cam.sees(2, c.z0, -1f)
            n[0] > 0f -> cam.sees(0, c.x1, 1f)
            n[0] < 0f -> cam.sees(0, c.x0, -1f)
            n[1] > 0f -> cam.sees(1, c.y1, 1f)
            else -> cam.sees(1, c.y0, -1f)
        }
        if (!visible) continue
        val path = Path().apply {
            moveTo(v[idx[0]].x, v[idx[0]].y)
            for (k in 1..3) lineTo(v[idx[k]].x, v[idx[k]].y)
            close()
        }
        val l = cam.light(n[0], n[1], n[2])
        if (glass) {
            // «Скло»: темне скло, верх світліший; кант — тонкий світлий.
            val fill = when {
                pallet -> lerp(Color(0xFF0E0E0E), Color(0xFF1C1C1C), l)
                dim -> lerp(Color(0xFF0A0A0A), Color(0xFF171717), l)
                else -> lerp(Color(0xFF111111), Color(0xFF353535), l)
            }
            drawPath(path, fill)
            drawPath(path, Color(if (pallet) 0x40FFFFFF else if (dim) 0x26FFFFFF else 0x66FFFFFF), style = stroke)
        } else {
            // «Аркуш»: чорний папір, грані обведені білим олівцем (верх трохи яскравіше).
            drawPath(path, Oled.Black)
            drawPath(path, SheetInk.copy(alpha = if (pallet) 0.4f else if (dim) 0.2f else (0.55f + 0.35f * l)), style = stroke)
        }
    }
}

/**
 * Порядок малювання коробок (індекси): спершу ті, що позаду. Для двох коробок, що на екрані перекриваються, беремо
 * вісь, уздовж якої вони розділені (коробки не перетинаються, тож така є): та, що з дальнього від глядача боку
 * площини розділу, закрити іншу не може — її малюємо раніше. Далі — топологічне сортування (за рівності — за глибиною).
 */
private fun drawOrder(c: List<Cuboid>, cam: Camera): IntArray {
    val n = c.size
    if (n == 0) return IntArray(0)
    val view = cam.viewer
    // Рамки на екрані — перевіряти лише пари, що можуть перекриватися.
    val minX = FloatArray(n); val maxX = FloatArray(n); val minY = FloatArray(n); val maxY = FloatArray(n)
    val depth = FloatArray(n)
    for (i in 0 until n) {
        val b = c[i]
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (k in 0 until 8) {
            val p = cam.project(if (k and 1 == 0) b.x0 else b.x1, if (k and 2 == 0) b.y0 else b.y1, if (k and 4 == 0) b.z0 else b.z1)
            if (p.x < x0) x0 = p.x
            if (p.x > x1) x1 = p.x
            if (p.y < y0) y0 = p.y
            if (p.y > y1) y1 = p.y
        }
        minX[i] = x0; maxX[i] = x1; minY[i] = y0; maxY[i] = y1
        depth[i] = cam.depth((b.x0 + b.x1) / 2f, (b.y0 + b.y1) / 2f, (b.z0 + b.z1) / 2f)
    }
    val eps = 0.5f
    val after = Array(n) { ArrayList<Int>(4) }
    val incoming = IntArray(n)
    fun edge(first: Int, second: Int) { after[first] += second; incoming[second]++ }
    for (i in 0 until n) for (j in i + 1 until n) {
        if (maxX[i] <= minX[j] + 0.5f || maxX[j] <= minX[i] + 0.5f || maxY[i] <= minY[j] + 0.5f || maxY[j] <= minY[i] + 0.5f) continue
        val a = c[i]; val b = c[j]
        // Вісь розділу і з якого боку глядач.
        val rel = when {
            // Площина розділу і з якого боку від неї глядач: та коробка, що з дальнього боку, — раніше.
            a.z1 <= b.z0 + eps -> if (view[2] > a.z1) 1 else -1
            b.z1 <= a.z0 + eps -> if (view[2] > b.z1) -1 else 1
            a.x1 <= b.x0 + eps -> if (view[0] > a.x1) 1 else -1
            b.x1 <= a.x0 + eps -> if (view[0] > b.x1) -1 else 1
            a.y1 <= b.y0 + eps -> if (view[1] > a.y1) 1 else -1
            b.y1 <= a.y0 + eps -> if (view[1] > b.y1) -1 else 1
            else -> if (depth[i] >= depth[j]) 1 else -1
        }
        if (rel > 0) edge(i, j) else edge(j, i)
    }
    // Kahn: серед готових — найдальша першою; якщо раптом цикл — беремо найдальшу з решти.
    val done = BooleanArray(n)
    val ready = java.util.PriorityQueue<Int>(compareByDescending { depth[it] })
    for (i in 0 until n) if (incoming[i] == 0) ready += i
    val out = IntArray(n)
    var k = 0
    while (k < n) {
        val next = ready.poll() ?: (0 until n).filter { !done[it] }.maxBy { depth[it] }
        if (done[next]) continue
        done[next] = true
        out[k++] = next
        for (m in after[next]) if (--incoming[m] == 0 && !done[m]) ready += m
    }
    return out
}
