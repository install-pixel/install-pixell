package com.blacklaunch.qrcodes.label

/**
 * Готові сцени для тла: малюнок із місцями під коди. Вибрав сцену — картинка лягає на наклейку цілою, а коди самі
 * стають у квадратики потрібного розміру. Місця — частки ширини/висоти картинки (центр) і сторона коду — частка ширини.
 */
object Scenes {
    /** [rot] — поворот місця (картка в повітрі), градуси. */
    data class Slot(val fx: Float, val fy: Float, val side: Float, val rot: Float = 0f)

    data class Scene(val id: String, val aspect: Float, val slots: List<Slot>) {
        val source: String get() = "asset:scenes/$id.jpg"
    }

    /** Котик-персонаж у одному стилі (олівцем): табличка, очі, торбинки, картки, жонглювання, код поруч. */
    val all = listOf(
        Scene("cat_sign", 1024f / 1050f, listOf(Slot(0.5067f, 0.5039f, 0.1771f, 0f))),
        Scene("cat_eyes", 1024f / 1050f, listOf(Slot(0.3724f, 0.4785f, 0.0819f, 0f), Slot(0.6343f, 0.5020f, 0.0838f, 0f))),
        Scene("cat_bags", 1024f / 1050f, listOf(Slot(0.3181f, 0.7861f, 0.1105f, 0f), Slot(0.7733f, 0.7861f, 0.1524f, 0f))),
        Scene("cat_cards", 1024f / 1050f, listOf(Slot(0.2638f, 0.2051f, 0.1029f, 28.4f), Slot(0.7819f, 0.3311f, 0.0895f, 17.3f), Slot(0.2648f, 0.6211f, 0.0990f, 31.8f), Slot(0.7790f, 0.5977f, 0.0990f, -35.2f))),
        Scene("cat_juggle", 1024f / 1050f, listOf(Slot(0.3133f, 0.1816f, 0.0895f, 40.3f), Slot(0.5533f, 0.0996f, 0.0933f, 21.7f), Slot(0.7629f, 0.2900f, 0.0914f, -24.3f), Slot(0.2743f, 0.4824f, 0.0933f, 24.4f), Slot(0.7448f, 0.5049f, 0.0933f, -20.9f))),
        Scene("cat_stand", 999f / 1024f, listOf(Slot(0.8398f, 0.4705f, 0.2246f, 0f))),
    )

    /**
     * Сцена на наклейці: аркуш повертається так само, як картинка (вертикальна — вертикально), картинка — ціла по центру,
     * коди з [codes] (по черзі, якщо їх менше, ніж місць) — у її квадратиках, без рамки (підпис — [caption]). Інші
     * елементи прибираються, щоб не налазили на малюнок. Кодів більше, ніж місць, — зайві тут не ставляться (їх
     * розкладає екран наклейки на вільне місце поруч, див. [extra]).
     */
    fun apply(scene: Scene, design: LabelDesign, codes: List<Element.Qr>, caption: Boolean = true, tw: ((String, Float) -> Float)? = null): LabelDesign {
        if (codes.isEmpty()) return design
        // Помітно вертикальна чи горизонтальна сцена — аркуш повертаємо так само; майже квадратна — лишаємо як є.
        val size = when {
            design.size.screen -> design.size
            scene.aspect > 1.2f && design.size.widthMm > design.size.heightMm -> design.size.rotated()
            scene.aspect < 0.83f && design.size.heightMm > design.size.widthMm -> design.size.rotated()
            else -> design.size
        }
        val bg = BgImage(scene.source, scene.aspect, 0f, 0f, 1f).fitted(size)
        // Коди не повторюються: скільки є (зазвичай один) — стільки й місць зайнято; решта місць лишається вільною
        // під інші коди (новий код сам стане в наступне вільне місце).
        var result = LabelDesign(size = size, elements = emptyList(), border = QrFrame.None, background = bg)
        scene.slots.take(codes.size).forEachIndexed { i, slot ->
            val src = codes[i]
            val (x, y) = bg.pointMm(slot.fx, slot.fy)
            // Трохи більший за квадратик (разом із білою підкладкою) — лінії місця не видно.
            val side = (slot.side * bg.width * SLOT_COVER).coerceIn(Element.MIN_QR, Element.MAX_QR)
            val qr = src.copy(id = i + 1, cx = x, cy = y, side = side, caption = caption, frame = QrFrame.None, rotation = normalizeAngle(slot.rot + bg.rotation))
            // Підпис лишаємо, якщо він вміщається (не за краєм і не на сусідньому коді); інакше — код без підпису.
            val placed = if (caption && tw != null && !Placement.fits(result, qr, tw)) qr.copy(caption = false) else qr
            result = result.add(placed)
        }
        return result
    }

    /** Коди, яким у сцені [scene] не вистачило місця (у [apply] вони не потрапили). */
    fun extra(scene: Scene, codes: List<Element.Qr>): List<Element.Qr> = codes.drop(scene.slots.size)

    /** Наскільки код більший за квадратик сцени. */
    private const val SLOT_COVER = 1.06f

    fun byBackground(bg: BgImage?): Scene? = bg?.let { b -> all.firstOrNull { it.source == b.source } }

    /** Вільне місце сцени на тлі (центр, сторона, поворот у мм наклейки) або null — немає сцени чи всі місця зайняті. */
    fun freeSlot(design: LabelDesign): Triple<Pair<Float, Float>, Float, Float>? {
        val bg = design.background ?: return null
        val scene = byBackground(bg) ?: return null
        val codes = design.elements.filterIsInstance<Element.Qr>()
        return scene.slots.asSequence().map { slot ->
            Triple(bg.pointMm(slot.fx, slot.fy), (slot.side * bg.width * SLOT_COVER).coerceIn(Element.MIN_QR, Element.MAX_QR), normalizeAngle(slot.rot + bg.rotation))
        }.firstOrNull { (c, side, _) ->
            codes.none { q -> kotlin.math.hypot(q.cx - c.first, q.cy - c.second) < side / 2 }
        }
    }
}
