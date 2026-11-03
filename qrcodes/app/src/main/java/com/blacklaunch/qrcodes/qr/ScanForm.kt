package com.blacklaunch.qrcodes.qr

import org.json.JSONArray
import org.json.JSONObject

/**
 * Форма для сканера — «живий» QR: поля по черзі, частину заповнюєш перед скануванням (скан, вибір, число), частина
 * рахується сама (похідне поле, дата). Між полями — Enter, Tab чи свій символ; вимкнене поле — пропуск (курсор
 * у системі просто перескакує на наступне).
 */
enum class FieldType { TEXT, SCAN, CHOICE, NUMBER, DERIVED, DATE, SKIP }

/** Клавіші переходу: -1 — як у формі; 0 Enter, 1 Tab, 2 ↓, 3 →, 4 — нічого. */
object MoveKey {
    const val FORM = -1
    const val ENTER = 0
    const val TAB = 1
    const val DOWN = 2
    const val RIGHT = 3
    const val NONE = 4
}

data class FormField(
    val label: String,
    val type: FieldType,
    /** Для TEXT — сам текст; для інших — типове значення. */
    val value: String = "",
    /** Варіанти для CHOICE (гортати). */
    val options: List<String> = emptyList(),
    /** CHOICE: можна вписати вручну. */
    val allowManual: Boolean = true,
    /** CHOICE: можна вибрати з усіх збережених кодів. */
    val allowSaved: Boolean = false,
    /** DERIVED: номер поля-джерела (з нуля). */
    val source: Int = -1,
    /** DATE: шаблон (dd — день, MM — місяць, yyyy/yy — рік, решта — як є). */
    val datePattern: String = "ddMMyyyy",
    val prefix: String = "",
    val suffix: String = "",
    /** Увімкнене, коли відкриваєш форму. */
    val onByDefault: Boolean = true,
    /** Що натиснути після поля ([MoveKey]) і скільки разів. */
    val move: Int = MoveKey.FORM,
    val moveTimes: Int = 1,
    /** Якщо поле вимкнене: -1 — як зазвичай (лише перехід після нього), інакше клавіша й кількість натискань. */
    val offKey: Int = -1,
    val offTimes: Int = 1,
)

data class ScanForm(
    val id: String,
    val name: String,
    val fields: List<FormField>,
    /** 0 — Enter, 1 — Tab, 2 — свій символ [custom], 3 / 4 — стрілка вниз терміналу (ESC [ B / ESC O B). */
    val separator: Int = 0,
    val custom: String = "|",
    val endEnter: Boolean = true,
    /** Пам'ятати введене до наступного разу. */
    val remember: Boolean = false,
    val lastValues: List<String> = emptyList(),
    val lastEnabled: List<Boolean> = emptyList(),
    /** Показувати по одному QR на поле (для сканерів, що не приймають Tab/Enter у коді). */
    val steps: Boolean = false,
) {
    val sep: String get() = when (separator) {
        1 -> "\t"
        2 -> custom.ifEmpty { "|" }
        3 -> "\u001B[B"
        4 -> "\u001BOB"
        else -> "\n"
    }

    /** Що саме вводиться в поле [i] (без префікса й допису) — з урахуванням похідних і дати. */
    fun raw(i: Int, values: List<String>, today: java.util.Date, depth: Int = 0): String {
        val f = fields.getOrNull(i) ?: return ""
        return when (f.type) {
            FieldType.TEXT -> f.value
            FieldType.DERIVED -> if (depth > fields.size || f.source == i) "" else raw(f.source, values, today, depth + 1)
            FieldType.DATE -> formatDate(f.datePattern, today)
            FieldType.SKIP -> ""
            else -> values.getOrNull(i).orEmpty()
        }
    }

    /** Готове значення поля (з префіксом і дописом) або порожньо — пропуск. */
    fun part(i: Int, values: List<String>, enabled: List<Boolean>, today: java.util.Date): String {
        val f = fields.getOrNull(i) ?: return ""
        if (enabled.getOrElse(i) { f.onByDefault }.not()) return ""
        val r = raw(i, values, today)
        return if (r.isEmpty()) "" else f.prefix + r + f.suffix
    }

    private val arrowPrefix: String get() = if (separator == 4) "\u001BO" else "\u001B["

    /** Символи клавіші [k] ([MoveKey]) для термінала. */
    fun key(k: Int): String = when (k) {
        MoveKey.FORM -> sep
        MoveKey.ENTER -> "\n"
        MoveKey.TAB -> "\t"
        MoveKey.DOWN -> arrowPrefix + "B"
        MoveKey.RIGHT -> arrowPrefix + "C"
        else -> ""
    }

    /** Що натискається після поля [i] (з урахуванням вимкнення). */
    fun tail(i: Int, enabled: List<Boolean>): String {
        val f = fields[i]
        val on = enabled.getOrElse(i) { f.onByDefault }
        return when {
            !on && f.offKey >= 0 -> key(f.offKey).repeat(f.offTimes.coerceIn(0, 99))
            !on && f.type == FieldType.SKIP -> ""
            else -> key(f.move).repeat(f.moveTimes.coerceIn(0, 99))
        }
    }

    /**
     * Увесь зміст QR: кожне поле + його перехід (вимкнені — лише перехід або свої клавіші). Переходи після останнього
     * заповненого поля відкидаються; у кінці Enter, якщо треба.
     */
    fun content(values: List<String>, enabled: List<Boolean>, today: java.util.Date): String {
        val texts = fields.indices.map { part(it, values, enabled, today) }
        val last = texts.indexOfLast { it.isNotEmpty() }
        val body = if (last < 0) "" else buildString {
            for (i in 0 until last) append(texts[i]).append(tail(i, enabled))
            append(texts[last])
        }
        return body + if (endEnter) "\n" else ""
    }

    /** Поля, які треба вводити покроково: увімкнені й не порожні. */
    fun activeSteps(values: List<String>, enabled: List<Boolean>, today: java.util.Date): List<Int> =
        fields.indices.filter { part(it, values, enabled, today).isNotEmpty() }

    /** Скільки полів пропустити (стрілкою) перед кроком [step]. */
    fun skipBefore(active: List<Int>, step: Int): Int {
        val cur = active.getOrNull(step) ?: return 0
        val prev = if (step == 0) -1 else active[step - 1]
        return cur - prev - 1
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("sep", separator).put("custom", custom).put("end", endEnter).put("remember", remember).put("steps", steps)
        .put("last", JSONArray(lastValues)).put("lastOn", JSONArray(lastEnabled))
        .put("fields", JSONArray().apply {
            fields.forEach { f ->
                put(
                    JSONObject().put("label", f.label).put("type", f.type.name).put("value", f.value).put("options", JSONArray(f.options))
                        .put("manual", f.allowManual).put("saved", f.allowSaved).put("source", f.source).put("date", f.datePattern)
                        .put("prefix", f.prefix).put("suffix", f.suffix).put("on", f.onByDefault)
                        .put("move", f.move).put("moveN", f.moveTimes).put("off", f.offKey).put("offN", f.offTimes),
                )
            }
        })

    companion object {
        fun formatDate(pattern: String, date: java.util.Date): String = runCatching {
            java.text.SimpleDateFormat(pattern, java.util.Locale.ROOT).format(date)
        }.getOrDefault(pattern)

        fun fromJson(o: JSONObject): ScanForm? = runCatching {
            val arr = o.getJSONArray("fields")
            ScanForm(
                id = o.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                name = o.optString("name"),
                fields = (0 until arr.length()).map { i ->
                    val f = arr.getJSONObject(i)
                    FormField(
                        label = f.optString("label"),
                        type = FieldType.entries.firstOrNull { it.name == f.optString("type") } ?: FieldType.TEXT,
                        value = f.optString("value"),
                        options = f.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
                        allowManual = f.optBoolean("manual", true),
                        allowSaved = f.optBoolean("saved"),
                        source = f.optInt("source", -1),
                        datePattern = f.optString("date", "ddMMyyyy"),
                        prefix = f.optString("prefix"),
                        suffix = f.optString("suffix"),
                        onByDefault = f.optBoolean("on", true),
                        move = f.optInt("move", MoveKey.FORM),
                        moveTimes = f.optInt("moveN", 1),
                        offKey = f.optInt("off", -1),
                        offTimes = f.optInt("offN", 1),
                    )
                },
                separator = o.optInt("sep"),
                custom = o.optString("custom", "|"),
                endEnter = o.optBoolean("end", true),
                remember = o.optBoolean("remember"),
                steps = o.optBoolean("steps"),
                lastValues = o.optJSONArray("last")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
                lastEnabled = o.optJSONArray("lastOn")?.let { a -> (0 until a.length()).map { a.optBoolean(it, true) } }.orEmpty(),
            )
        }.getOrNull()
    }
}
