package com.blacklaunch.diary.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Штрих пера на сторінці. Точки — у частках ширини сторінки (x / ширина, y / ширина), тож малюнок лишається тим
 * самим на будь-якому екрані. [factors] — множник товщини в кожній точці («живе перо»), [width] — товщина в частках
 * ширини, [time] — коли намальовано (висохлий штрих гумка не стирає).
 */
class InkLine(
    val page: Int,
    val xs: FloatArray,
    val ys: FloatArray,
    val factors: FloatArray,
    val width: Float,
    val time: Long,
)

/** Викреслене у висохлому тексті: [start] включно, [end] — ні (індекси символів [Day.sealed]). */
data class Strike(val start: Int, val end: Int)

/**
 * Один день щоденника. Текст складається з двох частин:
 *  - [sealed] — висохле чорнило: змінити не можна, лише викреслити ([strikes]);
 *  - [fresh] — свіже: пишеться й виправляється вільно, доки не висохне (див. [dried]).
 * Свіже завжди йде після висохлого, тож дописати день можна й пізніше.
 */
data class Day(
    /** День як номер дня від 1970-01-01 (LocalDate.toEpochDay()). */
    val day: Long,
    val place: String = "",
    val sealed: String = "",
    val fresh: String = "",
    /** Коли почали писати свіже (мс) — від цього рахується, коли воно висохне. */
    val freshSince: Long = 0L,
    val strikes: List<Strike> = emptyList(),
    val ink: List<InkLine> = emptyList(),
    val photo: Boolean = false,
    /** Короткий підпис до фото дня (кілька слів). */
    val caption: String = "",
    /** Місце під фото / голос на цей день прибрали (свайпом) — його займає текст. */
    val noPhoto: Boolean = false,
    val noAudio: Boolean = false,
    val audio: Boolean = false,
    val audioMs: Long = 0L,
    /** Обриси звуку (0…1) — для смужки запису. */
    val wave: List<Float> = emptyList(),
    val updated: Long = 0L,
) {
    val text: String get() = sealed + fresh

    /** Нічого не написано / не додано (для номерів сторінок). */
    val isBlank: Boolean
        get() = place.isBlank() && text.isBlank() && ink.isEmpty() && !photo && !audio

    /** Нічого зберігати: порожній і місця під фото / голос не прибирали. */
    val isEmpty: Boolean
        get() = isBlank && !noPhoto && !noAudio

    /**
     * Чорнило висихає: свіже, старше за [hours] годин, переходить у висохле; штрихи пера теж стають висохлими самі
     * собою (за часом, див. [InkLine.time]).
     */
    fun dried(now: Long, hours: Long): Day {
        if (fresh.isEmpty() || freshSince == 0L) return this
        if (now - freshSince < hours * HOUR) return this
        return copy(sealed = sealed + fresh, fresh = "", freshSince = 0L)
    }

    fun isInkDry(line: InkLine, now: Long, hours: Long): Boolean = now - line.time >= hours * HOUR

    companion object {
        const val HOUR = 3_600_000L
    }
}

/** Кодування штрихів і викреслень у короткий JSON для бази. */
object DayCodec {
    fun inkToJson(lines: List<InkLine>): String {
        if (lines.isEmpty()) return ""
        val arr = JSONArray()
        lines.forEach { l ->
            val o = JSONObject()
            o.put("p", l.page)
            o.put("w", r(l.width))
            o.put("t", l.time)
            o.put("x", JSONArray().also { a -> l.xs.forEach { a.put(r(it)) } })
            o.put("y", JSONArray().also { a -> l.ys.forEach { a.put(r(it)) } })
            if (l.factors.isNotEmpty()) o.put("f", JSONArray().also { a -> l.factors.forEach { a.put(r2(it)) } })
            arr.put(o)
        }
        return arr.toString()
    }

    fun inkFromJson(s: String): List<InkLine> {
        if (s.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(s)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val x = o.getJSONArray("x")
                val y = o.getJSONArray("y")
                val f = o.optJSONArray("f")
                InkLine(
                    page = o.optInt("p"),
                    xs = FloatArray(x.length()) { x.getDouble(it).toFloat() },
                    ys = FloatArray(y.length()) { y.getDouble(it).toFloat() },
                    factors = if (f == null) FloatArray(0) else FloatArray(f.length()) { f.getDouble(it).toFloat() },
                    width = o.optDouble("w", 0.006).toFloat(),
                    time = o.optLong("t"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun strikesToJson(list: List<Strike>): String =
        if (list.isEmpty()) "" else JSONArray().also { a -> list.forEach { a.put(JSONArray().put(it.start).put(it.end)) } }.toString()

    fun strikesFromJson(s: String): List<Strike> {
        if (s.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(s)
            List(arr.length()) { i -> arr.getJSONArray(i).let { Strike(it.getInt(0), it.getInt(1)) } }
        }.getOrDefault(emptyList())
    }

    fun waveToString(w: List<Float>): String = w.joinToString(",") { (it * 100).toInt().toString() }

    fun waveFromString(s: String): List<Float> =
        if (s.isBlank()) emptyList() else s.split(',').mapNotNull { it.toIntOrNull()?.let { v -> v / 100f } }

    private fun r(v: Float): Double = Math.round(v * 10000.0) / 10000.0
    private fun r2(v: Float): Double = Math.round(v * 100.0) / 100.0
}
