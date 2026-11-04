package com.blacklaunch.qrcodes.ui

import com.blacklaunch.qrcodes.qr.CodeKind
import com.blacklaunch.qrcodes.qr.Symbol

/** Розумні межі вводу — щоб мільйон символів не гальмував і не ламав вигляд. */
object Limits {
    /** Назва коду, папки, форми. */
    const val NAME = 80

    /** Назва поля форми. */
    const val FIELD_LABEL = 40

    /** Текст на наклейці. */
    const val LABEL_TEXT = 200

    /** Варіанти вибору в полі форми (рядків). */
    const val OPTIONS = 100

    /** Скільки тексту взагалі можна вставити в поле «Зі списку». */
    const val LIST_TEXT = 200_000

    /** Від скількох кодів попереджаємо, що список може гальмувати. */
    const val MANY_CODES = 20_000
}

/**
 * Готові коди для маленьких картинок у списку й вибраному: рядок зник за край і з'явився знову — код береться з
 * пам'яті, а не рахується заново (легше для процесора під час гортання).
 */
object SymbolCache {
    private class Entry(val symbol: Symbol?)

    private val cache = android.util.LruCache<String, Entry>(400)

    fun get(content: String, kind: CodeKind): Symbol? {
        val key = kind.storage + "\u0000" + content
        cache.get(key)?.let { return it.symbol }
        val s = Symbol.encode(content, kind)
        cache.put(key, Entry(s))
        return s
    }
}
