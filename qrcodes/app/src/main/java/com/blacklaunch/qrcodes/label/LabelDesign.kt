package com.blacklaunch.qrcodes.label

import kotlin.math.abs

/** Рамка навколо QR-коду, намальована олівцем. */
enum class QrFrame { None, Pencil, Double }

/** Значки, які можна покласти на наклейку (малюються олівцем, як значки програми). */
enum class Sticker { Camera, Box, Arrow, Star, Wifi, Warning, Phone, Scan }

/**
 * Елемент наклейки. Координати — центр елемента в мм від лівого верхнього кута наклейки, тож розмір змінюється
 * навколо центру, а «по центру наклейки» — це просто x = ширина/2.
 */
sealed interface Element {
    val id: Int
    val cx: Float
    val cy: Float

    fun moved(cx: Float, cy: Float): Element

    /** Новий «основний розмір» (сторона коду, висота літер, розмір значка). */
    fun resized(size: Float): Element

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
    ) : Element {
        override val size get() = side
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
    ) : Element {
        override val size get() = letters
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
    ) : Element {
        override val size get() = side
        override fun moved(cx: Float, cy: Float) = copy(cx = cx, cy = cy)
        override fun resized(size: Float) = copy(side = size.coerceIn(MIN_ICON, MAX_ICON))
    }

    companion object {
        const val MIN_QR = 8f
        const val MAX_QR = 280f
        const val MIN_LETTERS = 2f
        const val MAX_LETTERS = 40f
        const val MIN_ICON = 4f
        const val MAX_ICON = 120f
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
    QrFrame.Pencil -> (qr.side * 0.06f).coerceIn(1.5f, 5f)
    QrFrame.Double -> (qr.side * 0.09f).coerceIn(2.2f, 7f)
}

/**
 * Межі елемента в мм. Ширину тексту дає [textWidth] (міряє програма тим самим шрифтом, яким малює:
 * текст, висота літер → ширина в мм).
 */
fun Element.bounds(textWidth: (String, Float) -> Float): MmRect = when (this) {
    is Element.Qr -> {
        val pad = framePad(this)
        val h = side + captionArea(this)
        MmRect(cx - side / 2 - pad, cy - h / 2 - pad, cx + side / 2 + pad, cy + h / 2 + pad)
    }
    is Element.Text -> {
        val w = textWidth(text, letters).coerceAtLeast(letters)
        val h = letters * 1.35f
        MmRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }
    is Element.Icon -> MmRect(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
}

/** Наклейка: розмір, елементи (останній — зверху), рамка олівцем по краю. */
data class LabelDesign(
    val size: LabelSize = LabelSize.DEFAULT,
    val elements: List<Element> = emptyList(),
    val border: Boolean = false,
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
        return copy(size = newSize, elements = elements.map { it.moved(it.cx * kx, it.cy * ky) })
    }
}

/** Куди лягає елемент, коли його тягнуть: у межах наклейки, з «прилипанням» до центру й країв. */
object Snap {
    /** На скільки мм центр/край «притягується». */
    const val DISTANCE = 2f

    data class Result(val cx: Float, val cy: Float, val vertical: Float?, val horizontal: Float?)

    /**
     * [cx], [cy] — куди хоче лягти центр; [box] — межі елемента в поточному місці (для країв і розміру).
     * vertical/horizontal — лінії, до яких прилипло (щоб показати їх на екрані).
     */
    fun place(cx: Float, cy: Float, element: MmRect, current: Element, size: LabelSize, margin: Float = LabelLayout.MARGIN_MM): Result {
        val halfW = element.width / 2
        val halfH = element.height / 2
        // Межі елемента відносно центру можуть бути несиметричні (підпис під кодом) — враховуємо зсув.
        val offY = (element.top + element.bottom) / 2 - current.cy
        val offX = (element.left + element.right) / 2 - current.cx
        var x = cx
        var y = cy
        var vertical: Float? = null
        var horizontal: Float? = null
        val midX = size.widthMm / 2
        val midY = size.heightMm / 2
        if (abs(x + offX - midX) < DISTANCE) { x = midX - offX; vertical = midX }
        if (abs(y + offY - midY) < DISTANCE) { y = midY - offY; horizontal = midY }
        // Поля по краях.
        if (vertical == null) {
            if (abs(x + offX - halfW - margin) < DISTANCE) { x = margin + halfW - offX; vertical = margin }
            if (abs(x + offX + halfW - (size.widthMm - margin)) < DISTANCE) { x = size.widthMm - margin - halfW - offX; vertical = size.widthMm - margin }
        }
        if (horizontal == null) {
            if (abs(y + offY - halfH - margin) < DISTANCE) { y = margin + halfH - offY; horizontal = margin }
            if (abs(y + offY + halfH - (size.heightMm - margin)) < DISTANCE) { y = size.heightMm - margin - halfH - offY; horizontal = size.heightMm - margin }
        }
        // Не виїжджати за наклейку (якщо елемент більший за неї — тримаємо центр на ній).
        x = if (element.width <= size.widthMm) x.coerceIn(halfW - offX, size.widthMm - halfW - offX) else midX - offX
        y = if (element.height <= size.heightMm) y.coerceIn(halfH - offY, size.heightMm - halfH - offY) else midY - offY
        return Result(x, y, vertical, horizontal)
    }
}

/** Розкласти коди сіткою (як раніше), решту елементів не чіпати. */
object Arrange {
    /** Усі коди — сіткою по центру наклейки, розміром найбільшого з них (якщо вміщається). */
    fun grid(design: LabelDesign): LabelDesign {
        val codes = design.elements.filterIsInstance<Element.Qr>()
        if (codes.isEmpty()) return design
        val side = codes.maxOf { it.side }
        val caption = codes.any { it.caption }
        val layout = LabelLayout(LabelSettings(design.size, side, 1, caption), codes.size)
        val placed = layout.pages.flatten()
        // Що не вмістилось на одну наклейку — лишається, де було.
        val byIndex = codes.mapIndexed { i, qr ->
            val p = placed.getOrNull(i)
            if (p == null) qr else qr.copy(
                cx = p.xMm + layout.cellWidthMm / 2,
                cy = p.yMm + layout.cellHeightMm / 2,
                side = layout.qrMm,
            )
        }.associateBy { it.id }
        return design.copy(elements = design.elements.map { byIndex[it.id] ?: it })
    }

    /** Заповнити наклейку копіями коду [qr] (інші коди прибираються, текст і значки лишаються). */
    fun fill(design: LabelDesign, qr: Element.Qr): LabelDesign {
        val layout = LabelLayout(LabelSettings(design.size, qr.side, 1, qr.caption), 1)
        var id = design.nextId
        val copies = (0 until layout.perPage).map { i ->
            if (i == 0) qr else qr.copy(id = id++)
        }
        val filled = LabelLayout(LabelSettings(design.size, qr.side, layout.perPage, qr.caption), 1).pages.first()
        val placed = copies.zip(filled) { c, p -> c.copy(cx = p.xMm + layout.cellWidthMm / 2, cy = p.yMm + layout.cellHeightMm / 2, side = layout.qrMm) }
        val others = design.elements.filter { it !is Element.Qr }
        return design.copy(elements = placed + others)
    }
}
