package com.blacklaunch.qrcodes.label

import kotlin.math.abs

/**
 * Рамка навколо QR-коду, намальована олівцем. Як на постерах із QR: проста, подвійна, пунктир, лише кутики
 * (видошукач) і «Скануй мене» — рамка з плашкою-написом знизу.
 */
enum class QrFrame { None, Pencil, Double, Dashed, Corners, Bold, Ticket, Dots, ScanMe }

/**
 * Значки — що закодовано і що з ним робити (як біля QR на сайтах і листівках): Wi‑Fi, дзвінок, сайт, завантаження,
 * пошта, адреса, контакт, «наведи телефон»; і просто для настрою — смайлик, серце.
 */
enum class Sticker { Wifi, Phone, Link, Download, Email, Location, Contact, ScanPhone, Smile, Heart }

/**
 * Елемент наклейки. Координати — центр елемента в мм від лівого верхнього кута наклейки, тож розмір змінюється
 * навколо центру, а «по центру наклейки» — це просто x = ширина/2.
 */
sealed interface Element {
    val id: Int
    val cx: Float
    val cy: Float

    /** Поворот навколо центру, градуси (0…360, за годинниковою стрілкою). */
    val rotation: Float

    fun moved(cx: Float, cy: Float): Element

    /** Новий «основний розмір» (сторона коду, висота літер, розмір значка). */
    fun resized(size: Float): Element

    fun rotated(degrees: Float): Element

    val size: Float

    /** Код: сторона [side] мм, підпис під ним (своя назва або зміст), рамка. */
    data class Qr(
        override val id: Int,
        override val cx: Float,
        override val cy: Float,
        val content: String,
        val title: String,
        val side: Float,
        val caption: Boolean = true,
        val frame: QrFrame = QrFrame.None,
        /** Напис на плашці рамки «Скануй мене». */
        val banner: String = "",
        /** Лінійний штрихкод замість QR: [side] — ширина, висота смуг — [BAR_HEIGHT_RATIO] від неї. */
        val barcode: Boolean = false,
        override val rotation: Float = 0f,
        /** Конкретний вид коду (як у базі: «QR», «BARCODE», «BARCODE:CODE39»…); порожньо — за [barcode]. */
        val kind: String = "",
    ) : Element {
        override val size get() = side
        override fun rotated(degrees: Float) = copy(rotation = normalizeAngle(degrees))
        override fun moved(cx: Float, cy: Float) = copy(cx = cx, cy = cy)
        override fun resized(size: Float) = copy(side = size.coerceIn(MIN_QR, MAX_QR))
    }

    /** Свій текст шрифтом від руки; [letters] — висота літер у мм. */
    data class Text(
        override val id: Int,
        override val cx: Float,
        override val cy: Float,
        val text: String,
        val letters: Float,
        override val rotation: Float = 0f,
    ) : Element {
        override val size get() = letters
        override fun rotated(degrees: Float) = copy(rotation = normalizeAngle(degrees))
        override fun moved(cx: Float, cy: Float) = copy(cx = cx, cy = cy)
        override fun resized(size: Float) = copy(letters = size.coerceIn(MIN_LETTERS, MAX_LETTERS))
    }

    /** Значок олівцем, сторона [side] мм. */
    data class Icon(
        override val id: Int,
        override val cx: Float,
        override val cy: Float,
        val sticker: Sticker,
        val side: Float,
        override val rotation: Float = 0f,
    ) : Element {
        override val size get() = side
        override fun rotated(degrees: Float) = copy(rotation = normalizeAngle(degrees))
        override fun moved(cx: Float, cy: Float) = copy(cx = cx, cy = cy)
        override fun resized(size: Float) = copy(side = size.coerceIn(MIN_ICON, MAX_ICON))
    }

    /**
     * Малюнок пальцем: штрихи — точки (x, y по черзі) у частках прямокутника [width]×[width]·[aspect], тож малюнок
     * масштабується разом з елементом. [thick] — товща лінія.
     */
    data class Drawing(
        override val id: Int,
        override val cx: Float,
        override val cy: Float,
        val strokes: List<List<Float>>,
        val aspect: Float,
        val width: Float,
        val thick: Boolean = false,
        override val rotation: Float = 0f,
        /** Товщина лінії — частка ширини (малюнок на наклейці); 0 — за [thick]. */
        val line: Float = 0f,
    ) : Element {
        override val size get() = width
        override fun rotated(degrees: Float) = copy(rotation = normalizeAngle(degrees))
        override fun moved(cx: Float, cy: Float) = copy(cx = cx, cy = cy)
        override fun resized(size: Float) = copy(width = size.coerceIn(MIN_DRAWING, MAX_DRAWING))

        /** Товщина лінії, мм — пропорційно розміру малюнка. */
        val strokeMm: Float get() = if (line > 0f) (line * width).coerceIn(0.2f, 6f) else (width * if (thick) 0.03f else 0.016f).coerceIn(0.25f, 4f)
    }

    companion object {
        const val MIN_DRAWING = 6f
        const val MAX_DRAWING = 280f
        const val MIN_QR = 8f
        const val MAX_QR = 280f
        const val MIN_LETTERS = 2f
        const val MAX_LETTERS = 40f
        const val MIN_ICON = 4f
        const val MAX_ICON = 120f
    }
}

/** Кут у межах 0…360; біля прямого кута (±[SNAP_DEG]) — рівно прямий, щоб легко поставити рівно. */
fun normalizeAngle(degrees: Float, snap: Boolean = false): Float {
    var a = degrees % 360f
    if (a < 0f) a += 360f
    if (snap) {
        val right = kotlin.math.round(a / 90f) * 90f
        if (abs(a - right) <= SNAP_DEG) a = right % 360f
    }
    return a
}

const val SNAP_DEG = 6f

/**
 * Зображення на тлі наклейки: [source] — файл у пам'яті програми або «asset:…» (вбудована сцена); [aspect] — висота /
 * ширина картинки; центр ([cx], [cy]), ширина [width] у мм і поворот — як у елемента (рухається, масштабується).
 */
data class BgImage(
    val source: String,
    val aspect: Float,
    val cx: Float,
    val cy: Float,
    val width: Float,
    val rotation: Float = 0f,
    /** Затерте гумкою: штрихи (fx, fy по черзі) у частках картинки — рухаються й масштабуються разом з нею. */
    val erased: List<List<Float>> = emptyList(),
) {
    val height: Float get() = width * aspect

    /** Товщина гумки — частка ширини картинки. */
    val eraseWidth: Float get() = ERASE_FRACTION * width

    /** Точка наклейки (мм) — у частках картинки (зворотне до [pointMm]). */
    fun fractionOf(x: Float, y: Float): Pair<Float, Float> {
        val a = Math.toRadians(-rotation.toDouble())
        val c = kotlin.math.cos(a).toFloat()
        val s = kotlin.math.sin(a).toFloat()
        val dx = x - cx
        val dy = y - cy
        return ((dx * c - dy * s) / width + 0.5f) to ((dx * s + dy * c) / height + 0.5f)
    }

    companion object {
        const val ERASE_FRACTION = 0.035f
    }

    /** Повернута на 90° (або 270°) — видимі ширина й висота міняються місцями. */
    private val sideways: Boolean get() = (kotlin.math.round(rotation / 90f).toInt() % 2) != 0

    /** Уся картинка в межах наклейки, по центру. */
    fun fitted(size: LabelSize): BgImage {
        val w = if (sideways) minOf(size.heightMm, size.widthMm / aspect) else minOf(size.widthMm, size.heightMm / aspect)
        return copy(cx = size.widthMm / 2, cy = size.heightMm / 2, width = w)
    }

    /** Картинка на всю наклейку (краї обрізаються), по центру. */
    fun filled(size: LabelSize): BgImage {
        val w = if (sideways) maxOf(size.heightMm, size.widthMm / aspect) else maxOf(size.widthMm, size.heightMm / aspect)
        return copy(cx = size.widthMm / 2, cy = size.heightMm / 2, width = w)
    }

    /** Точка картинки (частки 0…1 її ширини й висоти) — у мм наклейки, з урахуванням повороту. */
    fun pointMm(fx: Float, fy: Float): Pair<Float, Float> {
        val dx = (fx - 0.5f) * width
        val dy = (fy - 0.5f) * height
        val a = Math.toRadians(rotation.toDouble())
        val c = kotlin.math.cos(a).toFloat()
        val s = kotlin.math.sin(a).toFloat()
        return (cx + dx * c - dy * s) to (cy + dx * s + dy * c)
    }
}

/** Прямокутник у мм. */
data class MmRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun contains(x: Float, y: Float, pad: Float = 0f) = x >= left - pad && x <= right + pad && y >= top - pad && y <= bottom + pad
}

/** Висота літер підпису під кодом: пропорційно коду, від 2,2 до 6 мм. */
fun captionLetters(side: Float): Float = (side * 0.11f).coerceIn(2.2f, 6f)

/** Підпис: відступ від коду + до двох рядків. */
fun captionArea(qr: Element.Qr): Float = if (qr.caption) captionLetters(qr.side) * 3.1f else 0f

/** Поле між кодом і рамкою олівцем. */
fun framePad(qr: Element.Qr): Float = when (qr.frame) {
    QrFrame.None -> 0f
    QrFrame.Pencil, QrFrame.Dashed, QrFrame.ScanMe -> (qr.side * 0.06f).coerceIn(1.5f, 5f)
    QrFrame.Double -> (qr.side * 0.09f).coerceIn(2.2f, 7f)
    QrFrame.Corners -> (qr.side * 0.07f).coerceIn(1.8f, 6f)
    QrFrame.Bold -> (qr.side * 0.08f).coerceIn(2f, 6.5f)
    QrFrame.Ticket -> (qr.side * 0.1f).coerceIn(2.4f, 7.5f)
    QrFrame.Dots -> (qr.side * 0.07f).coerceIn(1.8f, 6f)
}

/**
 * Місце під плашку «Скануй мене»: сама плашка й відступ від коду. З підписом відступ дає підпис; без нього —
 * окремий проміжок, щоб плашка не прилипала до коду.
 */
fun bannerArea(qr: Element.Qr): Float {
    if (qr.frame != QrFrame.ScanMe) return 0f
    val gap = if (qr.caption) 0f else (qr.side * 0.07f).coerceIn(1.5f, 5f)
    return bannerHeight(qr) + gap
}

/** Висота самої плашки «Скануй мене». */
fun bannerHeight(qr: Element.Qr): Float = (qr.side * 0.24f).coerceIn(5f, 18f)

/** Повна висота блоку коду: поле, код, підпис, плашка, поле. */
fun qrBlockHeight(qr: Element.Qr): Float = symbolHeight(qr) + captionArea(qr) + bannerArea(qr) + 2 * framePad(qr)

/** Висота самого коду: QR — квадрат, штрихкод — нижчий за ширину. */
fun symbolHeight(qr: Element.Qr): Float = if (qr.barcode) qr.side * BAR_HEIGHT_RATIO else qr.side

/** Висота смуг штрихкоду відносно його ширини (з тихими зонами по боках). */
const val BAR_HEIGHT_RATIO = 0.42f

/** Верх самого коду (мм). */
fun qrTop(qr: Element.Qr): Float = qr.cy - qrBlockHeight(qr) / 2 + framePad(qr)

/**
 * Межі елемента в мм. Ширину тексту дає [textWidth] (міряє програма тим самим шрифтом, яким малює:
 * текст, висота літер → ширина в мм).
 */
fun Element.bounds(textWidth: (String, Float) -> Float): MmRect {
    val b = baseBounds(textWidth)
    if (rotation == 0f) return b
    // Повернутий елемент займає більший прямокутник (центр той самий).
    val rad = Math.toRadians(rotation.toDouble())
    val c = abs(kotlin.math.cos(rad)).toFloat()
    val s = abs(kotlin.math.sin(rad)).toFloat()
    val w = b.width * c + b.height * s
    val h = b.width * s + b.height * c
    val mx = (b.left + b.right) / 2
    val my = (b.top + b.bottom) / 2
    return MmRect(mx - w / 2, my - h / 2, mx + w / 2, my + h / 2)
}

/** Межі без повороту. */
fun Element.baseBounds(textWidth: (String, Float) -> Float): MmRect = when (this) {
    is Element.Qr -> {
        val halfW = side / 2 + framePad(this)
        val halfH = qrBlockHeight(this) / 2
        MmRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
    }
    is Element.Text -> {
        val w = textWidth(text, letters).coerceAtLeast(letters)
        val h = letters * 1.35f
        MmRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }
    is Element.Icon -> MmRect(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
    is Element.Drawing -> {
        val pad = strokeMm / 2
        val h = width * aspect
        MmRect(cx - width / 2 - pad, cy - h / 2 - pad, cx + width / 2 + pad, cy + h / 2 + pad)
    }
}

/** Точки штрихів малюнка в мм наклейки (з поворотом), зміщені на ([dx], [dy]). */
fun Element.Drawing.pointsMm(dx: Float = 0f, dy: Float = 0f): List<Pair<Float, Float>> {
    val w = width
    val h = width * aspect
    val a = Math.toRadians(rotation.toDouble())
    val c = kotlin.math.cos(a).toFloat()
    val s = kotlin.math.sin(a).toFloat()
    return strokes.flatMap { st ->
        st.chunked(2).filter { it.size == 2 }.map { (u, v) ->
            val x = (u - 0.5f) * w
            val y = (v - 0.5f) * h
            (cx + dx + x * c - y * s) to (cy + dy + x * s + y * c)
        }
    }
}

/** Чи лінії малюнка (а не весь його прямокутник) заходять у [r] — тож обвести код від руки можна. */
fun Element.Drawing.strokesHit(r: MmRect, gap: Float, dx: Float = 0f, dy: Float = 0f): Boolean {
    val pad = strokeMm / 2 + gap
    return pointsMm(dx, dy).any { (x, y) -> x > r.left - pad && x < r.right + pad && y > r.top - pad && y < r.bottom + pad }
}

/**
 * Чи [a], зміщений на ([dx], [dy]), налазить на [b]. Малюнок рахуємо по лініях (рамка від руки навколо коду — можна),
 * два малюнки один одному не заважають; решта — за прямокутниками.
 */
fun collides(a: Element, b: Element, tw: (String, Float) -> Float, gap: Float, dx: Float = 0f, dy: Float = 0f): Boolean {
    val ra = a.bounds(tw).let { MmRect(it.left + dx, it.top + dy, it.right + dx, it.bottom + dy) }
    val rb = b.bounds(tw)
    if (!ra.overlaps(rb, gap)) return false
    return when {
        a is Element.Drawing && b is Element.Drawing -> false
        a is Element.Drawing -> a.strokesHit(rb, gap, dx, dy)
        b is Element.Drawing -> b.strokesHit(ra, gap)
        else -> true
    }
}

fun MmRect.overlaps(o: MmRect, gap: Float = 0f): Boolean =
    left < o.right + gap && right > o.left - gap && top < o.bottom + gap && bottom > o.top - gap

/** Наклейка: розмір, елементи (останній — зверху), рамка олівцем по краю. */
data class LabelDesign(
    val size: LabelSize = LabelSize.DEFAULT,
    val elements: List<Element> = emptyList(),
    /** Рамка по краю всього аркуша (ті самі стилі, що й у коду, крім «Скануй мене»). */
    val border: QrFrame = QrFrame.None,
    /** Зображення на тлі або null. Коди лежать на суцільній підкладці поверх нього. */
    val background: BgImage? = null,
) {
    val nextId: Int get() = (elements.maxOfOrNull { it.id } ?: 0) + 1

    fun replace(e: Element) = copy(elements = elements.map { if (it.id == e.id) e else it })

    fun remove(id: Int) = copy(elements = elements.filter { it.id != id })

    fun add(e: Element) = copy(elements = elements + e)

    /** Верхній елемент під точкою (з запасом [pad] мм — дрібні легше вхопити пальцем). */
    fun hit(x: Float, y: Float, pad: Float, textWidth: (String, Float) -> Float): Element? =
        elements.lastOrNull { it.bounds(textWidth).contains(x, y, pad) }

    /** Нова наклейка іншого розміру: елементи — в тих самих пропорційних місцях. */
    fun resizedTo(newSize: LabelSize): LabelDesign {
        val kx = newSize.widthMm / size.widthMm
        val ky = newSize.heightMm / size.heightMm
        return copy(
            size = newSize,
            elements = elements.map { it.moved(it.cx * kx, it.cy * ky) },
            background = background?.let { it.copy(cx = it.cx * kx, cy = it.cy * ky) },
        )
    }
}

/**
 * Куди лягає елемент, коли його тягнуть: у межах наклейки, з «прилипанням» до центру й країв наклейки, а також до
 * інших елементів — рівно в ряд (центри, верхи, низи, лівий чи правий край) і з однаковими проміжками.
 */
object Snap {
    /** На скільки мм центр/край «притягується». */
    const val DISTANCE = 2f

    /** Проміжок між двома елементами — для позначки «однакова відстань» ([horizontal] — уздовж осі X). */
    data class Gap(val from: Float, val to: Float, val at: Float, val horizontal: Boolean)

    data class Result(
        val cx: Float,
        val cy: Float,
        val vertical: Float?,
        val horizontal: Float?,
        val gaps: List<Gap> = emptyList(),
    )

    private class Candidate(val shift: Float, val line: Float?, val gaps: List<Gap> = emptyList())

    /**
     * [cx], [cy] — куди хоче лягти центр; [element] — межі елемента в поточному місці (для країв і розміру);
     * [others] — межі інших елементів. vertical/horizontal — лінії, до яких прилипло; gaps — однакові проміжки.
     */
    fun place(
        cx: Float,
        cy: Float,
        element: MmRect,
        current: Element,
        size: LabelSize,
        margin: Float = LabelLayout.MARGIN_MM,
        edge: Float = Placement.EDGE,
        others: List<MmRect> = emptyList(),
    ): Result {
        val halfW = element.width / 2
        val halfH = element.height / 2
        // Межі елемента відносно центру можуть бути несиметричні (підпис під кодом) — враховуємо зсув.
        val offY = (element.top + element.bottom) / 2 - current.cy
        val offX = (element.left + element.right) / 2 - current.cx
        // Де б були межі, якби центр став у (cx, cy).
        val l = cx + offX - halfW
        val r = cx + offX + halfW
        val t = cy + offY - halfH
        val b = cy + offY + halfH
        val midX = size.widthMm / 2
        val midY = size.heightMm / 2

        // По X: кандидати — зсув, після якого щось збігається, і лінія, яку показати.
        val xs = ArrayList<Candidate>()
        xs += Candidate(midX - (l + r) / 2, midX)
        xs += Candidate(margin - l, margin)
        xs += Candidate(size.widthMm - margin - r, size.widthMm - margin)
        val ys = ArrayList<Candidate>()
        ys += Candidate(midY - (t + b) / 2, midY)
        ys += Candidate(margin - t, margin)
        ys += Candidate(size.heightMm - margin - b, size.heightMm - margin)
        others.forEach { o ->
            val ocx = (o.left + o.right) / 2
            val ocy = (o.top + o.bottom) / 2
            xs += Candidate(ocx - (l + r) / 2, ocx)
            xs += Candidate(o.left - l, o.left)
            xs += Candidate(o.right - r, o.right)
            ys += Candidate(ocy - (t + b) / 2, ocy)
            ys += Candidate(o.top - t, o.top)
            ys += Candidate(o.bottom - b, o.bottom)
        }
        xs += equalGaps(l, r, t, b, others, horizontal = true)
        ys += equalGaps(t, b, l, r, others, horizontal = false)

        val bestX = xs.filter { abs(it.shift) < DISTANCE }.minByOrNull { abs(it.shift) }
        val bestY = ys.filter { abs(it.shift) < DISTANCE }.minByOrNull { abs(it.shift) }
        var x = cx + (bestX?.shift ?: 0f)
        var y = cy + (bestY?.shift ?: 0f)
        // Не виїжджати за наклейку (якщо елемент більший за неї — тримаємо центр на ній).
        val e = edge
        x = if (element.width <= size.widthMm - 2 * e) x.coerceIn(e + halfW - offX, size.widthMm - e - halfW - offX) else midX - offX
        y = if (element.height <= size.heightMm - 2 * e) y.coerceIn(e + halfH - offY, size.heightMm - e - halfH - offY) else midY - offY
        val keptX = bestX?.takeIf { abs(x - (cx + it.shift)) < 0.01f }
        val keptY = bestY?.takeIf { abs(y - (cy + it.shift)) < 0.01f }
        return Result(x, y, keptX?.line, keptY?.line, keptX?.gaps.orEmpty() + keptY?.gaps.orEmpty())
    }

    /**
     * Однакові проміжки вздовж однієї осі: [a0]..[a1] — межі елемента вздовж осі, [c0]..[c1] — поперек (щоб брати лише
     * сусідів з того самого ряду/стовпця). Пропонує: поставити на такій самій відстані від сусіда, як між сусідами
     * (продовжити ряд), або рівно посередині між двома.
     */
    private fun equalGaps(a0: Float, a1: Float, c0: Float, c1: Float, others: List<MmRect>, horizontal: Boolean): List<Candidate> {
        fun lo(o: MmRect) = if (horizontal) o.left else o.top
        fun hi(o: MmRect) = if (horizontal) o.right else o.bottom
        fun clo(o: MmRect) = if (horizontal) o.top else o.left
        fun chi(o: MmRect) = if (horizontal) o.bottom else o.right
        val row = others.filter { minOf(chi(it), c1) - maxOf(clo(it), c0) > 0f }.sortedBy { lo(it) }
        if (row.isEmpty()) return emptyList()
        val at = { o: MmRect -> (maxOf(clo(o), c0) + minOf(chi(o), c1)) / 2 }
        val out = ArrayList<Candidate>()
        val len = a1 - a0
        // Пари сусідів у ряду і проміжок між ними.
        for (i in 0 until row.size - 1) {
            val p = row[i]
            val q = row[i + 1]
            val g = lo(q) - hi(p)
            if (g <= 0f) continue
            val pairGap = Gap(hi(p), lo(q), (at(p) + at(q)) / 2, horizontal)
            // Продовжити ряд праворуч/знизу від q і ліворуч/зверху від p.
            out += Candidate(hi(q) + g - a0, null, listOf(pairGap, Gap(hi(q), hi(q) + g, at(q), horizontal)))
            out += Candidate(lo(p) - g - a1, null, listOf(pairGap, Gap(lo(p) - g, lo(p), at(p), horizontal)))
        }
        // Посередині між двома сусідніми (якщо поміщається).
        for (i in 0 until row.size - 1) {
            val p = row[i]
            val q = row[i + 1]
            val space = lo(q) - hi(p)
            if (space <= len) continue
            val g = (space - len) / 2
            val start = hi(p) + g
            out += Candidate(start - a0, null, listOf(Gap(hi(p), start, at(p), horizontal), Gap(start + len, lo(q), at(q), horizontal)))
        }
        return out
    }
}

/**
 * «Захист від помилок»: елементи не виходять за край наклейки й не лягають один на одного (на наклейці, де коди
 * накладаються, спрацює лише верхній). Якщо місце зайняте — шукаємо найближче вільне.
 */
object Placement {
    /** Скільки мм від краю наклейки лишати (принтер не друкує впритул). */
    const val EDGE = 1.5f

    /** З рамкою аркуша — ще далі від краю, щоб нічого не налазило на рамку. */
    const val EDGE_WITH_BORDER = 4f

    fun edge(design: LabelDesign): Float = if (design.border == QrFrame.None) EDGE else EDGE_WITH_BORDER

    /** Мінімальний проміжок між елементами. */
    const val GAP = 1f

    fun inside(b: MmRect, size: LabelSize, edge: Float = EDGE) =
        b.left >= edge - 0.01f && b.top >= edge - 0.01f && b.right <= size.widthMm - edge + 0.01f && b.bottom <= size.heightMm - edge + 0.01f

    /** Чи стоїть [e] на своєму місці: всередині й ні на кого не налазить. */
    fun fits(design: LabelDesign, e: Element, tw: (String, Float) -> Float): Boolean {
        val b = e.bounds(tw)
        return inside(b, design.size, edge(design)) && design.elements.none { it.id != e.id && collides(e, it, tw, GAP - 0.01f) }
    }

    /**
     * Найближче до ([wantX], [wantY]) вільне місце для [e]; null — на наклейці для нього немає місця.
     * Перебираємо центри з кроком 1 мм — для наклейки 150×100 це мить.
     */
    fun nearestFree(design: LabelDesign, e: Element, tw: (String, Float) -> Float, wantX: Float = e.cx, wantY: Float = e.cy): Element? {
        val b = e.bounds(tw)
        val offX = (b.left + b.right) / 2 - e.cx
        val offY = (b.top + b.bottom) / 2 - e.cy
        val halfW = b.width / 2
        val halfH = b.height / 2
        val size = design.size
        val edge = edge(design)
        val minX = edge + halfW - offX
        val maxX = size.widthMm - edge - halfW - offX
        val minY = edge + halfH - offY
        val maxY = size.heightMm - edge - halfH - offY
        if (minX > maxX + 0.01f || minY > maxY + 0.01f) return null
        val othersEl = design.elements.filter { it.id != e.id }
        val others = othersEl.map { it.bounds(tw) }
        val precise = e is Element.Drawing || othersEl.any { it is Element.Drawing }
        fun free(x: Float, y: Float): Boolean {
            val r = MmRect(x + offX - halfW, y + offY - halfH, x + offX + halfW, y + offY + halfH)
            if (!precise) return others.none { it.overlaps(r, GAP - 0.01f) }
            // З малюнками — спершу швидко за прямокутниками, точно (по лініях) лише там, де вони перетинаються.
            return othersEl.indices.none { i -> others[i].overlaps(r, GAP - 0.01f) && collides(e, othersEl[i], tw, GAP - 0.01f, x - e.cx, y - e.cy) }
        }
        val x0 = wantX.coerceIn(minX, maxOf(minX, maxX))
        val y0 = wantY.coerceIn(minY, maxOf(minY, maxY))
        if (free(x0, y0)) return e.moved(x0, y0)
        var best: Pair<Float, Float>? = null
        var bestD = Float.MAX_VALUE
        val stepsX = ((maxX - minX) / STEP).toInt().coerceAtLeast(0)
        val stepsY = ((maxY - minY) / STEP).toInt().coerceAtLeast(0)
        for (i in 0..stepsX) {
            val x = if (stepsX == 0) minX else minX + i * (maxX - minX) / stepsX
            for (j in 0..stepsY) {
                val y = if (stepsY == 0) minY else minY + j * (maxY - minY) / stepsY
                val d = (x - wantX) * (x - wantX) + (y - wantY) * (y - wantY)
                if (d < bestD && free(x, y)) {
                    bestD = d
                    best = x to y
                }
            }
        }
        return best?.let { (x, y) -> e.moved(x, y) }
    }

    private const val STEP = 1f
}

/** Розкласти коди сіткою або заповнити наклейку копіями — з урахуванням рамок, підписів і плашок. */
object Arrange {
    private const val GRID_GAP = 3f

    /** Скільки клітинок [cw]×[ch] вміщається вздовж [total] мм. */
    private fun fit(total: Float, cell: Float, edge: Float): Int =
        maxOf(0, kotlin.math.floor((total - 2 * edge + GRID_GAP) / (cell + GRID_GAP)).toInt())

    /** Центри клітинок сітки [cols]×[rows] для [count] елементів, сітка — по центру наклейки. */
    private fun centres(size: LabelSize, cw: Float, ch: Float, cols: Int, count: Int): List<Pair<Float, Float>> {
        val usedCols = minOf(cols, count)
        val usedRows = (count + cols - 1) / cols
        val gridW = usedCols * cw + (usedCols - 1) * GRID_GAP
        val gridH = usedRows * ch + (usedRows - 1) * GRID_GAP
        val left = (size.widthMm - gridW) / 2
        val top = (size.heightMm - gridH) / 2
        return (0 until count).map { i ->
            val col = i % cols
            val row = i / cols
            (left + col * (cw + GRID_GAP) + cw / 2) to (top + row * (ch + GRID_GAP) + ch / 2)
        }
    }

    /** Блок коду (з рамкою, підписом, плашкою) відносно центру: ширина й висота. */
    private fun cell(qr: Element.Qr, tw: (String, Float) -> Float) = qr.bounds(tw).let { it.width to it.height }

    /**
     * Усі коди — сіткою по центру. Якщо не вміщаються — коди зменшуються, поки не влізуть. Текст і значки потім
     * переїжджають на найближче вільне місце.
     */
    fun grid(design: LabelDesign, tw: (String, Float) -> Float): LabelDesign {
        val codes = design.elements.filterIsInstance<Element.Qr>()
        if (codes.isEmpty()) return design
        var side = codes.maxOf { it.side }
        while (true) {
            val sample = codes.maxBy { qrBlockHeight(it.copy(side = side)) }.copy(side = side)
            val (cw, ch) = cell(sample, tw)
            val cols = fit(design.size.widthMm, cw, Placement.edge(design))
            val rows = fit(design.size.heightMm, ch, Placement.edge(design))
            if ((cols * rows >= codes.size && cols > 0) || side <= Element.MIN_QR) {
                val pos = centres(design.size, cw, ch, maxOf(cols, 1), codes.size)
                val placed = codes.mapIndexed { i, qr -> qr.copy(side = side, cx = pos[i].first, cy = pos[i].second) }.associateBy { it.id }
                return settleOthers(design.copy(elements = design.elements.map { placed[it.id] ?: it }), tw)
            }
            side -= 1f
        }
    }

    /** Заповнити наклейку копіями коду [qr] (інші коди прибираються). */
    fun fill(design: LabelDesign, original: Element.Qr, tw: (String, Float) -> Float): LabelDesign {
        // Не вміщається навіть один — зменшуємо код, доки не влізе.
        var qr = original
        while (qr.side > Element.MIN_QR) {
            val (w, h) = cell(qr, tw)
            if (fit(design.size.widthMm, w, Placement.edge(design)) > 0 && fit(design.size.heightMm, h, Placement.edge(design)) > 0) break
            qr = qr.copy(side = qr.side - 1f)
        }
        val (cw, ch) = cell(qr, tw)
        val cols = fit(design.size.widthMm, cw, Placement.edge(design))
        val rows = fit(design.size.heightMm, ch, Placement.edge(design))
        val count = maxOf(1, cols * rows)
        val pos = centres(design.size, cw, ch, maxOf(cols, 1), count)
        var id = design.nextId
        val copies = pos.mapIndexed { i, (x, y) -> (if (i == 0) qr else qr.copy(id = id++)).copy(cx = x, cy = y) }
        return settleOthers(design.copy(elements = copies + design.elements.filter { it !is Element.Qr }), tw)
    }

    /** Текст і значки, що налізли на коди, — на найближче вільне місце (не вміщаються — лишаються, де були). */
    private fun settleOthers(design: LabelDesign, tw: (String, Float) -> Float): LabelDesign {
        var d = design
        d.elements.filter { it !is Element.Qr }.forEach { e ->
            if (!Placement.fits(d, e, tw)) Placement.nearestFree(d, e, tw)?.let { d = d.replace(it) }
        }
        return d
    }
}
