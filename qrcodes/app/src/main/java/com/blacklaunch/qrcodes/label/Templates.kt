package com.blacklaunch.qrcodes.label

/**
 * Вбудовані шаблони наклейок. Кожен будується під поточний розмір аркуша й поточний код (і ставить усе так, щоб нічого
 * не налазило й не виходило за край):
 * - [Kind.Simple] — великий код по центру з підписом;
 * - [Kind.Shelf] — «полиця / склад»: код збоку, поруч великий напис (назва); штрихкод — зверху на всю ширину, напис під ним;
 * - [Kind.ScanMe] — «Скануй мене»: значок за змістом коду (Wi‑Fi, контакт, сайт…), назва великими літерами й код у рамці
 *   з плашкою;
 * - [Kind.Copies] — увесь аркуш копіями коду.
 */
object Templates {
    enum class Kind { Simple, Shelf, ScanMe, Copies }

    /** [code] — код, з якого будуємо (зміст, назва, QR чи штрихкод); [scanMe] — напис плашки мовою телефону. */
    fun build(kind: Kind, size: LabelSize, code: Element.Qr, scanMe: String, tw: (String, Float) -> Float): LabelDesign {
        val base = LabelDesign(size = size)
        val e = Placement.EDGE + 1.5f
        val w = size.widthMm - 2 * e
        val h = size.heightMm - 2 * e
        val design = when (kind) {
            Kind.Simple -> {
                val qr = code.copy(id = 1, caption = true, frame = QrFrame.None, rotation = 0f)
                val sized = fitSide(qr, w * if (qr.barcode) 0.9f else 0.8f, h * 0.9f)
                base.add(sized.moved(size.widthMm / 2, size.heightMm / 2))
            }
            Kind.Shelf -> if (code.barcode) {
                val bar = fitSide(code.copy(id = 1, caption = false, frame = QrFrame.None, rotation = 0f), w * 0.92f, h * 0.5f)
                val barH = bar.bounds(tw).height
                val top = e + barH / 2
                val textArea = h - barH - 2f
                val letters = fitLetters(code.title, w, (textArea * 0.6f).coerceIn(Element.MIN_LETTERS, 30f), tw)
                base.add(bar.moved(size.widthMm / 2, top))
                    .add(Element.Text(2, size.widthMm / 2, e + barH + 2f + textArea / 2, code.title, letters))
            } else {
                val side = minOf(h, w * 0.45f)
                val qr = code.copy(id = 1, side = side, caption = false, frame = QrFrame.None, rotation = 0f)
                val textLeft = e + side + 4f
                val textW = size.widthMm - e - textLeft
                val letters = fitLetters(code.title, textW, (h * 0.3f).coerceIn(Element.MIN_LETTERS, 30f), tw)
                base.add(qr.moved(e + side / 2, size.heightMm / 2))
                    .add(Element.Text(2, textLeft + textW / 2, size.heightMm / 2, code.title, letters))
            }
            Kind.ScanMe -> {
                // Праворуч — код у рамці «Скануй мене»; ліворуч — значок і назва.
                val qr = fitSide(
                    code.copy(id = 1, caption = false, frame = QrFrame.ScanMe, banner = code.banner.ifEmpty { scanMe }, rotation = 0f),
                    w * 0.55f,
                    h,
                )
                val qb = qr.bounds(tw)
                val qrCx = size.widthMm - e - qb.width / 2
                val leftW = size.widthMm - 2 * e - qb.width - 4f
                val icon = Element.Icon(2, e + leftW / 2, 0f, stickerFor(code.content), (minOf(leftW, h) * 0.42f).coerceIn(Element.MIN_ICON, 40f))
                val letters = fitLetters(code.title, leftW, (h * 0.16f).coerceIn(Element.MIN_LETTERS, 16f), tw)
                val text = Element.Text(3, e + leftW / 2, 0f, code.title, letters)
                val gap = 3f
                val blockH = icon.side + gap + letters * 1.35f
                val top = size.heightMm / 2 - blockH / 2
                base.add(qr.moved(qrCx, size.heightMm / 2))
                    .add(icon.moved(icon.cx, top + icon.side / 2))
                    .add(text.moved(text.cx, top + icon.side + gap + letters * 1.35f / 2))
            }
            Kind.Copies -> {
                val qr = code.copy(id = 1, rotation = 0f)
                Arrange.fill(base.add(qr), qr, tw)
            }
        }
        return settle(design, tw)
    }

    /** Найбільший розмір коду, щоб його блок (з підписом і рамкою) уміщався в [maxW]×[maxH]. */
    private fun fitSide(qr: Element.Qr, maxW: Float, maxH: Float): Element.Qr {
        var lo = Element.MIN_QR
        var hi = Element.MAX_QR
        repeat(30) {
            val mid = (lo + hi) / 2
            val t = qr.copy(side = mid)
            val blockW = mid + 2 * framePad(t)
            if (blockW <= maxW && qrBlockHeight(t) <= maxH) lo = mid else hi = mid
        }
        return qr.copy(side = lo)
    }

    /** Найбільші літери (не більше [max]), з якими [text] уміщається в ширину [maxW]. */
    private fun fitLetters(text: String, maxW: Float, max: Float, tw: (String, Float) -> Float): Float {
        var letters = max
        while (letters > Element.MIN_LETTERS && tw(text, letters) > maxW) letters -= 0.5f
        return letters.coerceAtLeast(Element.MIN_LETTERS)
    }

    /** Що не вмістилось — на найближче вільне місце; зовсім нема місця — прибираємо (крім коду). */
    private fun settle(design: LabelDesign, tw: (String, Float) -> Float): LabelDesign {
        var d = design
        d.elements.forEach { el ->
            if (!Placement.fits(d, el, tw)) {
                val placed = Placement.nearestFree(d, el, tw)
                d = when {
                    placed != null -> d.replace(placed)
                    el is Element.Qr -> d
                    else -> d.remove(el.id)
                }
            }
        }
        return d
    }

    /** Значок за змістом коду: Wi‑Fi, контакт, сайт, пошта, телефон, інакше — «скануй». */
    fun stickerFor(content: String): Sticker {
        val c = content.trim()
        return when {
            c.startsWith("WIFI:", ignoreCase = true) -> Sticker.Wifi
            c.startsWith("BEGIN:VCARD", ignoreCase = true) || c.startsWith("MECARD:", ignoreCase = true) -> Sticker.Contact
            c.startsWith("mailto:", ignoreCase = true) -> Sticker.Email
            c.startsWith("tel:", ignoreCase = true) -> Sticker.Phone
            c.startsWith("geo:", ignoreCase = true) -> Sticker.Location
            Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(c) -> Sticker.Link
            else -> Sticker.ScanPhone
        }
    }

    /**
     * Свій шаблон до інших кодів: розмір аркуша, місця, розміри, рамки, тексти й значки — з шаблону; коди в ньому по черзі
     * замінюються на [codes]. Якщо QR став штрихкодом (чи навпаки) і вже не вміщається — переїжджає на вільне місце.
     */
    fun apply(template: LabelDesign, codes: List<Element.Qr>, tw: (String, Float) -> Float): LabelDesign {
        if (codes.isEmpty()) return template
        var i = 0
        val filled = template.copy(
            elements = template.elements.map { el ->
                if (el is Element.Qr) {
                    val src = codes[i++ % codes.size]
                    el.copy(content = src.content, title = src.title, barcode = src.barcode)
                } else {
                    el
                }
            },
        )
        return settle(filled, tw)
    }
}
