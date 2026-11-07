package com.blacklaunch.paleta.pack

/*
 * Розкладка однакових коробок на палету (вид зверху). Усе — у міліметрах, цілими числами (без похибок дробів).
 *
 * Авторозкладка шукає найбільшу кількість коробок у шарі трьома способами й бере найкращий:
 *  1. проста сітка — усі коробки однаково (вздовж або впоперек), як у старій програмі;
 *  2. «гільйотинні» розкладки — палета ріжеться прямими розрізами на частини, у кожній — своя сітка
 *     (точний перебір усіх розрізів, динамічне програмування);
 *  3. «млин» (pinwheel) — чотири блоки довкола центру, кожен повернутий по-своєму, і п'ятий у центрі:
 *     так кладуть коробки, коли прямими розрізами не виходить.
 * Різних найкращих розкладок буває кілька — їх можна перебирати («Авто» ще раз).
 */

/** Палета EUR 1200 × 800 мм. */
const val PALLET_L = 1200
const val PALLET_W = 800

/** Розмір коробки в плані: [l] — уздовж палети (вісь X), [w] — упоперек (вісь Y), коли не повернута. */
data class BoxSize(val l: Int, val w: Int) {
    val valid: Boolean get() = l > 0 && w > 0
}

/** Коробка на палеті: лівий нижній кут ([x], [y], мм від кута палети) і чи повернута на 90°. */
data class Placed(val x: Int, val y: Int, val rotated: Boolean)

fun Placed.sx(box: BoxSize): Int = if (rotated) box.w else box.l
fun Placed.sy(box: BoxSize): Int = if (rotated) box.l else box.w

/** Найкращі розкладки для коробки: усі з однаковою (найбільшою) кількістю, перша — найпростіша. */
data class AutoResult(val count: Int, val variants: List<List<Placed>>, val usesOverhang: Boolean)

object Packer {
    /** Межа перебору: для дуже дрібних коробок рахуємо лише прості способи (щоб не думати секундами). */
    private const val DP_BUDGET = 40_000_000L
    private const val PINWHEEL_BUDGET = 25_000_000L

    /**
     * Авторозкладка. [overhang] — дозволений звис з кожного боку (мм). Звис використовується, лише якщо з ним
     * поміщається більше коробок; інакше розкладка — в межах палети.
     */
    fun auto(box: BoxSize, overhang: Int = 0): AutoResult {
        if (!box.valid) return AutoResult(0, emptyList(), false)
        val plain = bestIn(box, PALLET_L, PALLET_W)
        if (overhang > 0) {
            val wide = bestIn(box, PALLET_L + 2 * overhang, PALLET_W + 2 * overhang)
            if (wide.first > plain.first) {
                return AutoResult(wide.first, wide.second.map { centre(it, box) }.distinctLayouts(box), true)
            }
        }
        return AutoResult(plain.first, plain.second.map { centre(it, box) }.distinctLayouts(box), false)
    }

    /** Кількість і варіанти розкладки в прямокутнику [areaL] × [areaW] (координати від його кута). */
    internal fun bestIn(box: BoxSize, areaL: Int, areaW: Int): Pair<Int, List<List<Placed>>> {
        val found = ArrayList<List<Placed>>()
        // 1. Проста сітка (обидва напрямки).
        val g0 = grid(areaL, areaW, box.l, box.w)
        val g1 = grid(areaL, areaW, box.w, box.l)
        found += fillGrid(0, 0, areaL, areaW, box, rotated = g1 > g0)
        // 2. Гільйотина.
        guillotine(box, areaL, areaW)?.let { found += it }
        // 3. Млин.
        pinwheel(box, areaL, areaW)?.let { found += it }
        val best = found.maxOf { it.size }
        // Спершу — найпростіші (сітка), далі складніші; лише з найбільшою кількістю.
        return best to found.filter { it.size == best }
    }

    /** Скільки коробок a×b (без повороту) поміщається сіткою в x×y. */
    private fun grid(x: Int, y: Int, a: Int, b: Int): Int = if (x < a || y < b) 0 else (x / a) * (y / b)

    /** Найкраща сітка (з поворотом чи без) у x×y. */
    private fun bestGrid(x: Int, y: Int, box: BoxSize): Int = maxOf(grid(x, y, box.l, box.w), grid(x, y, box.w, box.l))

    private fun fillGrid(ox: Int, oy: Int, x: Int, y: Int, box: BoxSize, rotated: Boolean): List<Placed> {
        val a = if (rotated) box.w else box.l
        val b = if (rotated) box.l else box.w
        if (x < a || y < b) return emptyList()
        val out = ArrayList<Placed>()
        for (j in 0 until y / b) for (i in 0 until x / a) out += Placed(ox + i * a, oy + j * b, rotated)
        return out
    }

    private fun fillBestGrid(ox: Int, oy: Int, x: Int, y: Int, box: BoxSize): List<Placed> =
        fillGrid(ox, oy, x, y, box, rotated = grid(x, y, box.w, box.l) > grid(x, y, box.l, box.w))

    /** Усі суми i·a + j·b ≤ max (лише в таких місцях має сенс різати чи ставити межу блоку). */
    internal fun normals(a: Int, b: Int, max: Int): IntArray {
        val seen = BooleanArray(max + 1)
        seen[0] = true
        for (v in 0..max) if (seen[v]) {
            if (v + a <= max) seen[v + a] = true
            if (v + b <= max) seen[v + b] = true
        }
        return (0..max).filter { seen[it] }.toIntArray()
    }

    /**
     * Точний перебір гільйотинних розкладок (розрізи на всю ширину частини). g(X, Y) — найбільше коробок у X×Y;
     * розглядається сітка або розріз уздовж чи впоперек у кожному «нормальному» місці.
     */
    private fun guillotine(box: BoxSize, areaL: Int, areaW: Int): List<Placed>? {
        val nx = normals(box.l, box.w, areaL)
        val ny = normals(box.l, box.w, areaW)
        val work = nx.size.toLong() * ny.size * (nx.size + ny.size)
        if (work > DP_BUDGET) return null
        // Індекс найбільшого нормального значення ≤ v.
        val fx = IntArray(areaL + 1)
        run { var k = 0; for (v in 0..areaL) { while (k + 1 < nx.size && nx[k + 1] <= v) k++; fx[v] = k } }
        val fy = IntArray(areaW + 1)
        run { var k = 0; for (v in 0..areaW) { while (k + 1 < ny.size && ny[k + 1] <= v) k++; fy[v] = k } }
        val cols = ny.size
        val best = IntArray(nx.size * cols)
        // Вибір: 0 — сітка; >0 — розріз по X у точці nx[c]; <0 — розріз по Y у точці ny[-c].
        val choice = IntArray(nx.size * cols)
        for (i in nx.indices) for (j in ny.indices) {
            val x = nx[i]
            val y = ny[j]
            var v = bestGrid(x, y, box)
            var ch = 0
            // Розріз по X: ліва частина nx[c] (до половини — решта симетрична), права — x - nx[c].
            var c = 1
            while (c < nx.size && nx[c] * 2 <= x) {
                val s = best[c * cols + j] + best[fx[x - nx[c]] * cols + j]
                if (s > v) { v = s; ch = c }
                c++
            }
            c = 1
            while (c < ny.size && ny[c] * 2 <= y) {
                val s = best[i * cols + c] + best[i * cols + fy[y - ny[c]]]
                if (s > v) { v = s; ch = -c }
                c++
            }
            best[i * cols + j] = v
            choice[i * cols + j] = ch
        }
        val out = ArrayList<Placed>()
        fun build(i: Int, j: Int, ox: Int, oy: Int) {
            val x = nx[i]
            val y = ny[j]
            val ch = choice[i * cols + j]
            when {
                ch == 0 -> out += fillBestGrid(ox, oy, x, y, box)
                ch > 0 -> {
                    build(ch, j, ox, oy)
                    build(fx[x - nx[ch]], j, ox + nx[ch], oy)
                }
                else -> {
                    build(i, -ch, ox, oy)
                    build(i, fy[y - ny[-ch]], ox, oy + ny[-ch])
                }
            }
        }
        build(nx.size - 1, ny.size - 1, 0, 0)
        return out
    }

    /**
     * «Млин»: A — лівий нижній кут, B — правий нижній, C — правий верхній, D — лівий верхній, E — у центрі.
     *   A = [0, xa] × [0, ya],  B = [xa, L] × [0, yb],  C = [xc, L] × [yb, W],  D = [0, xc] × [ya, W],
     *   E = [xa, xc] × [yb, ya]  (xc ≥ xa, ya ≥ yb). Кожен блок — своя сітка.
     */
    private fun pinwheel(box: BoxSize, areaL: Int, areaW: Int): List<Placed>? {
        val nx = normals(box.l, box.w, areaL)
        val ny = normals(box.l, box.w, areaW)
        val work = nx.size.toLong() * nx.size * ny.size * ny.size / 4
        if (work > PINWHEEL_BUDGET) return null
        var best = -1
        var arg = IntArray(4)
        for (a in nx.indices) {
            val xa = nx[a]
            for (c in a until nx.size) {
                val xc = nx[c]
                for (b in ny.indices) {
                    val yb = ny[b]
                    for (d in b until ny.size) {
                        val ya = ny[d]
                        val s = bestGrid(xa, ya, box) + bestGrid(areaL - xa, yb, box) + bestGrid(areaL - xc, areaW - yb, box) +
                            bestGrid(xc, areaW - ya, box) + bestGrid(xc - xa, ya - yb, box)
                        if (s > best) { best = s; arg = intArrayOf(xa, xc, yb, ya) }
                    }
                }
            }
        }
        if (best <= 0) return null
        val (xa, xc, yb, ya) = arg
        // Блоки щільно до своїх кутів палети: A — до лівого нижнього, B — до правого нижнього, C — до правого верхнього,
        // D — до лівого верхнього. Так вільне місце збирається в центрі, а краї рівні.
        val out = ArrayList<Placed>()
        out += fillBestGrid(0, 0, xa, ya, box)
        out += alignRight(fillBestGrid(xa, 0, areaL - xa, yb, box), box, areaL)
        out += alignTop(alignRight(fillBestGrid(xc, yb, areaL - xc, areaW - yb, box), box, areaL), box, areaW)
        out += alignTop(fillBestGrid(0, ya, xc, areaW - ya, box), box, areaW)
        out += fillBestGrid(xa, yb, xc - xa, ya - yb, box)
        return out
    }

    private fun alignRight(list: List<Placed>, box: BoxSize, right: Int): List<Placed> {
        if (list.isEmpty()) return list
        val dx = right - list.maxOf { it.x + it.sx(box) }
        return list.map { it.copy(x = it.x + dx) }
    }

    private fun alignTop(list: List<Placed>, box: BoxSize, top: Int): List<Placed> {
        if (list.isEmpty()) return list
        val dy = top - list.maxOf { it.y + it.sy(box) }
        return list.map { it.copy(y = it.y + dy) }
    }

    /** Розкладку — по центру палети (вантаж рівномірно, звис — однаковий з обох боків). */
    fun centre(list: List<Placed>, box: BoxSize): List<Placed> {
        if (list.isEmpty()) return list
        val minX = list.minOf { it.x }
        val maxX = list.maxOf { it.x + it.sx(box) }
        val minY = list.minOf { it.y }
        val maxY = list.maxOf { it.y + it.sy(box) }
        val dx = (PALLET_L - (maxX - minX)) / 2 - minX
        val dy = (PALLET_W - (maxY - minY)) / 2 - minY
        return list.map { Placed(it.x + dx, it.y + dy, it.rotated) }
    }

    /** Без однакових (і дзеркальних) варіантів. */
    private fun List<List<Placed>>.distinctLayouts(box: BoxSize): List<List<Placed>> {
        val seen = HashSet<Set<Placed>>()
        val out = ArrayList<List<Placed>>()
        for (l in this) {
            val key = l.toSet()
            val mirrorX = l.map { it.copy(x = PALLET_L - it.x - it.sx(box)) }.toSet()
            val mirrorY = l.map { it.copy(y = PALLET_W - it.y - it.sy(box)) }.toSet()
            val both = l.map { Placed(PALLET_L - it.x - it.sx(box), PALLET_W - it.y - it.sy(box), it.rotated) }.toSet()
            if (key in seen || mirrorX in seen || mirrorY in seen || both in seen) continue
            seen += key
            out += l
        }
        return out
    }
}

/** Що не так з розкладкою (своєю чи авто). */
data class LayoutCheck(
    /** Номери коробок, що накладаються на інші. */
    val overlapping: Set<Int>,
    /** Номери коробок, що виступають більше, ніж дозволено, → на скільки мм понад дозволене. */
    val outside: Map<Int, Int>,
    /** Найбільший звис серед дозволених (мм за край палети), 0 — без звису. */
    val overhangUsed: Int,
) {
    val ok: Boolean get() = overlapping.isEmpty() && outside.isEmpty()
    val bad: Set<Int> get() = overlapping + outside.keys
}

object Layout {
    /** Поріг «прилипання» до країв і сусідніх коробок під час перетягування. */
    const val SNAP_MM = 30

    fun check(boxes: List<Placed>, box: BoxSize, overhang: Int): LayoutCheck {
        val overlapping = HashSet<Int>()
        val outside = HashMap<Int, Int>()
        var used = 0
        for (i in boxes.indices) {
            val p = boxes[i]
            val out = maxOf(0, -p.x, p.x + p.sx(box) - PALLET_L, -p.y, p.y + p.sy(box) - PALLET_W)
            if (out > overhang) outside[i] = out - overhang else used = maxOf(used, out)
            for (j in i + 1 until boxes.size) {
                if (overlap(p, boxes[j], box)) { overlapping += i; overlapping += j }
            }
        }
        return LayoutCheck(overlapping, outside, used)
    }

    private fun overlap(a: Placed, b: Placed, box: BoxSize): Boolean =
        a.x < b.x + b.sx(box) && b.x < a.x + a.sx(box) && a.y < b.y + b.sy(box) && b.y < a.y + a.sy(box)

    /**
     * Перетягування: коробка [index] у ([x], [y]) прилипає до країв палети (і меж звису) та до країв сусідніх коробок,
     * якщо близько (до [SNAP_MM]). Далеко за палету витягнути не можна — щонайбільше на пів коробки.
     */
    fun snap(boxes: List<Placed>, index: Int, x: Int, y: Int, rotated: Boolean, box: BoxSize, overhang: Int, snapMm: Int = SNAP_MM): Placed {
        val me = Placed(x, y, rotated)
        val sx = me.sx(box)
        val sy = me.sy(box)
        val cx = ArrayList<Int>()
        val cy = ArrayList<Int>()
        cx += listOf(0, PALLET_L - sx)
        cy += listOf(0, PALLET_W - sy)
        if (overhang > 0) {
            cx += listOf(-overhang, PALLET_L + overhang - sx)
            cy += listOf(-overhang, PALLET_W + overhang - sy)
        }
        boxes.forEachIndexed { i, o ->
            if (i == index) return@forEachIndexed
            val ox = o.sx(box)
            val oy = o.sy(box)
            cx += listOf(o.x + ox, o.x - sx, o.x, o.x + ox - sx)
            cy += listOf(o.y + oy, o.y - sy, o.y, o.y + oy - sy)
        }
        val nx = cx.minByOrNull { kotlin.math.abs(it - x) }?.takeIf { kotlin.math.abs(it - x) <= snapMm } ?: x
        val ny = cy.minByOrNull { kotlin.math.abs(it - y) }?.takeIf { kotlin.math.abs(it - y) <= snapMm } ?: y
        return Placed(
            nx.coerceIn(-sx / 2, PALLET_L - sx / 2),
            ny.coerceIn(-sy / 2, PALLET_W - sy / 2),
            rotated,
        )
    }

    /** Повернути коробку на 90° навколо її центру (і прилипнути, якщо поруч край). */
    fun rotate(boxes: List<Placed>, index: Int, box: BoxSize, overhang: Int): Placed {
        val p = boxes[index]
        val cx = p.x + p.sx(box) / 2
        val cy = p.y + p.sy(box) / 2
        val r = Placed(0, 0, !p.rotated)
        return snap(boxes, index, cx - r.sx(box) / 2, cy - r.sy(box) / 2, !p.rotated, box, overhang)
    }

    /**
     * Перше вільне місце для ще однієї коробки: знизу вгору, зліва направо, біля країв і сусідів; спершу в межах
     * палети, потім (якщо дозволено) зі звисом. [preferRotated] — якою стороною пробувати першою. null — не поміщається.
     */
    fun freeSpot(boxes: List<Placed>, box: BoxSize, overhang: Int, preferRotated: Boolean = false): Placed? {
        for (limit in if (overhang > 0) listOf(0, overhang) else listOf(0)) {
            val xs = (listOf(-limit) + boxes.map { it.x + it.sx(box) } + boxes.map { it.x }).distinct()
            val ys = (listOf(-limit) + boxes.map { it.y + it.sy(box) } + boxes.map { it.y }).distinct()
            val candidates = ArrayList<Placed>()
            for (r in listOf(preferRotated, !preferRotated)) for (x in xs) for (y in ys) candidates += Placed(x, y, r)
            candidates.sortWith(compareBy({ it.y }, { it.x }))
            for (c in candidates) {
                val fits = c.x >= -limit && c.y >= -limit && c.x + c.sx(box) <= PALLET_L + limit && c.y + c.sy(box) <= PALLET_W + limit
                if (fits && boxes.none { overlap(it, c, box) }) return c
            }
        }
        return null
    }

    /** Доповнити вільне місце: додавати коробки, поки поміщаються (пробуємо обидва напрямки, беремо кращий). */
    fun fill(boxes: List<Placed>, box: BoxSize, overhang: Int): List<Placed> =
        listOf(false, true).map { prefer ->
            val out = boxes.toMutableList()
            var guard = 0
            while (guard++ < 400) out += freeSpot(out, box, overhang, prefer) ?: break
            out
        }.maxBy { it.size }

    /** Розкладка в рядок для бази: «x,y,r;x,y,r…» (мм, r — 1 якщо повернута). */
    fun encode(boxes: List<Placed>): String = boxes.joinToString(";") { "${it.x},${it.y},${if (it.rotated) 1 else 0}" }

    fun decode(s: String?): List<Placed> = s.orEmpty().split(';').mapNotNull { part ->
        val f = part.split(',')
        if (f.size != 3) return@mapNotNull null
        val x = f[0].toIntOrNull() ?: return@mapNotNull null
        val y = f[1].toIntOrNull() ?: return@mapNotNull null
        Placed(x, y, f[2] == "1")
    }
}
