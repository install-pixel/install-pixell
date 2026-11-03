package com.blacklaunch.qrcodes.qr

import org.json.JSONArray
import org.json.JSONObject

/**
 * Форма для сканера — «живий» QR: поля по черзі, частину заповнюєш перед скануванням (скан, вибір, число), частина
 * рахується сама (похідне поле, дата). Між полями — Enter, Tab чи свій символ; вимкнене поле — пропуск (курсор
 * у системі просто перескакує на наступне).
 */
enum class FieldType { TEXT, SCAN, CHOICE, NUMBER, DERIVED, DATE }

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
)

data class ScanForm(
    val id: String,
    val name: String,
    val fields: List<FormField>,
    /** 0 — Enter, 1 — Tab, 2 — свій символ [custom]. */
    val separator: Int = 0,
    val custom: String = "|",
    val endEnter: Boolean = true,
    /** Пам'ятати введене до наступного разу. */
    val remember: Boolean = false,
    val lastValues: List<String> = emptyList(),
    val lastEnabled: List<Boolean> = emptyList(),
) {
    val sep: String get() = when (separator) {
        1 -> "\t"
        2 -> custom.ifEmpty { "|" }
        else -> "\n"
    }

    /** Що саме вводиться в поле [i] (без префікса й допису) — з урахуванням похідних і дати. */
    fun raw(i: Int, values: List<String>, today: java.util.Date, depth: Int = 0): String {
        val f = fields.getOrNull(i) ?: return ""
        return when (f.type) {
            FieldType.TEXT -> f.value
            FieldType.DERIVED -> if (depth > fields.size || f.source == i) "" else raw(f.source, values, today, depth + 1)
            FieldType.DATE -> formatDate(f.datePattern, today)
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

    /** Увесь зміст QR: поля через розділювач (вимкнені — пропуском), у кінці Enter, якщо треба. */
    fun content(values: List<String>, enabled: List<Boolean>, today: java.util.Date): String =
        fields.indices.joinToString(sep) { part(it, values, enabled, today) } + if (endEnter) "\n" else ""

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("sep", separator).put("custom", custom).put("end", endEnter).put("remember", remember)
        .put("last", JSONArray(lastValues)).put("lastOn", JSONArray(lastEnabled))
        .put("fields", JSONArray().apply {
            fields.forEach { f ->
                put(
                    JSONObject().put("label", f.label).put("type", f.type.name).put("value", f.value).put("options", JSONArray(f.options))
                        .put("manual", f.allowManual).put("saved", f.allowSaved).put("source", f.source).put("date", f.datePattern)
                        .put("prefix", f.prefix).put("suffix", f.suffix).put("on", f.onByDefault),
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
                    )
                },
                separator = o.optInt("sep"),
                custom = o.optString("custom", "|"),
                endEnter = o.optBoolean("end", true),
                remember = o.optBoolean("remember"),
                lastValues = o.optJSONArray("last")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
                lastEnabled = o.optJSONArray("lastOn")?.let { a -> (0 until a.length()).map { a.optBoolean(it, true) } }.orEmpty(),
            )
        }.getOrNull()
    }
}
