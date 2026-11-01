package com.blacklaunch.qrcodes.label

/**
 * Готові сцени для тла: малюнок із місцями під коди. Вибрав сцену — картинка лягає на наклейку цілою, а коди самі
 * стають у квадратики потрібного розміру. Місця — частки ширини/висоти картинки (центр) і сторона коду — частка ширини.
 */
object Scenes {
    data class Slot(val fx: Float, val fy: Float, val side: Float)

    data class Scene(val id: String, val aspect: Float, val slots: List<Slot>) {
        val source: String get() = "asset:scenes/$id.png"
    }

    val all = listOf(
        // Мої (QRaft): котик із табличкою й телевізор-персонаж.
        Scene("cat", 1000f / 1400f, listOf(Slot(0.6889f, 0.3895f, 0.2686f))),
        Scene("tv", 1300f / 1100f, listOf(Slot(0.4995f, 0.4381f, 0.4745f))),
        // Від користувача.
        Scene("robot", 1592f / 988f, listOf(Slot(0.7206f, 0.3643f, 0.2632f))),
        Scene("clown", 1591f / 988f, listOf(Slot(0.3537f, 0.5154f, 0.1296f), Slot(0.6463f, 0.5154f, 0.1296f))),
        Scene("birds", 750f / 1210f, listOf(Slot(0.2178f, 0.784f, 0.1314f), Slot(0.4926f, 0.848f, 0.1314f), Slot(0.7640f, 0.784f, 0.1314f))),
        Scene("dog", 900f / 855f, listOf(Slot(0.2339f, 0.2244f, 0.3404f))),
    )

    /**
     * Сцена на наклейці: аркуш повертається так само, як картинка (вертикальна — вертикально), картинка — ціла по центру,
     * коди з [codes] (по черзі, якщо їх менше, ніж місць) — у її квадратиках, без підпису й рамки. Інші елементи
     * прибираються, щоб не налазили на малюнок.
     */
    fun apply(scene: Scene, design: LabelDesign, codes: List<Element.Qr>): LabelDesign {
        if (codes.isEmpty()) return design
        val portrait = scene.aspect > 1f
        val size = if (!design.size.screen && (design.size.heightMm > design.size.widthMm) != portrait) design.size.rotated() else design.size
        val bg = BgImage(scene.source, scene.aspect, 0f, 0f, 1f).fitted(size)
        val qrs = scene.slots.mapIndexed { i, slot ->
            val src = codes[i % codes.size]
            val (x, y) = bg.pointMm(slot.fx, slot.fy)
            val side = (slot.side * bg.width).coerceIn(Element.MIN_QR, Element.MAX_QR)
            src.copy(id = i + 1, cx = x, cy = y, side = side, caption = false, frame = QrFrame.None, rotation = 0f)
        }
        return LabelDesign(size = size, elements = qrs, border = QrFrame.None, background = bg)
    }
}
