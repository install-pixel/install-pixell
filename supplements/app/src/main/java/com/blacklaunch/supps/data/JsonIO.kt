package com.blacklaunch.supps.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * Схема у JSON: можна вставити готову (наприклад, складену в чаті) або зберегти свою. Формат — FORMAT, версія VERSION.
 *
 * {
 *   "format": "supps", "version": 1,
 *   "groups": [ { "key": "adapt", "name": "Адаптогени", "breakDays": 0 } ],
 *   "supplements": [
 *     { "name": "Омега-3", "form": "capsule", "mg": 750, "perDose": 2, "slot": "wake",
 *       "schedule": { "type": "daily" }, "stock": null, "pack": 60, "price": null, "note": "з жирною їжею" },
 *     { "name": "Бакопа", "schedule": { "type": "cycle", "on": 84, "off": 28, "doneDays": 30 } },
 *     { "name": "Родіола", "schedule": { "type": "group", "group": "adapt", "days": 56, "order": 0, "doneDays": 20 } },
 *     { "name": "Мелатонін", "schedule": { "type": "asNeeded" } }
 *   ],
 *   "backup": { … }   // лише в повній копії: уся історія з ідентифікаторами
 * }
 *
 * form: capsule | tablet | powder | drops | other;  slot: wake | day | dinner | bed.
 * Невідоме (мг, залишок, банка, ціна) — null або просто не писати. doneDays — скільки днів уже приймаєш.
 */
object JsonIO {
    const val FORMAT = "supps"
    const val VERSION = 1

    data class ParsedGroup(val key: String, val name: String, val breakDays: Int)

    data class ParsedItem(val supplement: Supplement, val groupKey: String?, val doneDays: Int)

    data class Backup(
        val supplements: List<Supplement>,
        val groups: List<AltGroup>,
        val intakes: List<Intake>,
        val checkIns: List<CheckIn>,
        val purchases: List<Purchase>,
    )

    data class Parsed(val groups: List<ParsedGroup>, val items: List<ParsedItem>, val backup: Backup?)

    class BadFile(message: String) : Exception(message)

    /** Розбір тексту. Кидає [BadFile], якщо це не наш JSON. */
    fun parse(text: String): Parsed {
        val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = try {
            if (trimmed.startsWith("[")) JSONObject().put("supplements", JSONArray(trimmed)) else JSONObject(trimmed)
        } catch (e: Exception) {
            throw BadFile("json")
        }
        val format = root.optString("format", FORMAT)
        if (format != FORMAT) throw BadFile("format")
        if (root.optInt("version", VERSION) > VERSION) throw BadFile("version")
        val groups = root.optJSONArray("groups").objects().mapIndexedNotNull { i, g ->
            val name = g.optString("name").trim().take(NAME_MAX)
            val key = g.optString("key").trim().ifEmpty { name.ifEmpty { "g$i" } }
            if (name.isEmpty() && key.isEmpty()) null else ParsedGroup(key, name.ifEmpty { key }, g.optInt("breakDays", 0).coerceIn(0, 3650))
        }
        val items = root.optJSONArray("supplements").objects().take(MAX_ITEMS).mapNotNull { o -> item(o) }
        val backup = root.optJSONObject("backup")?.let { backup(it) }
        if (items.isEmpty() && backup == null) throw BadFile("empty")
        return Parsed(groups, items, backup)
    }

    private fun item(o: JSONObject): ParsedItem? {
        val name = o.optString("name").trim().take(NAME_MAX)
        if (name.isEmpty()) return null
        val schedule = o.optJSONObject("schedule")
        val type = schedule?.optString("type")?.lowercase() ?: "daily"
        val plan = when (type) {
            "cycle" -> Plan.Cycle
            "group", "alternate", "alternation" -> Plan.Group
            "asneeded", "as_needed", "needed" -> Plan.AsNeeded
            else -> Plan.Daily
        }
        val on = when (plan) {
            Plan.Cycle -> schedule?.optInt("on", 0) ?: 0
            Plan.Group -> schedule?.let { it.optInt("days", it.optInt("on", 0)) } ?: 0
            else -> 0
        }
        val s = Supplement(
            name = name,
            form = enumByName<Form>(o.optString("form"))?.name ?: Form.Capsule.name,
            mgPerUnit = o.optDoubleOrNull("mg")?.takeIf { it > 0 },
            unitsPerDose = (o.optDoubleOrNull("perDose") ?: 1.0).coerceIn(0.01, 1000.0),
            slot = enumByName<Slot>(o.optString("slot"))?.name ?: Slot.Wake.name,
            plan = plan.name,
            onDays = on.coerceIn(0, 3650),
            offDays = (if (plan == Plan.Cycle) schedule?.optInt("off", 0) ?: 0 else 0).coerceIn(0, 3650),
            groupOrder = schedule?.optInt("order", 0) ?: 0,
            unitsLeft = o.optDoubleOrNull("stock")?.coerceIn(0.0, 100_000.0),
            packUnits = o.optDoubleOrNull("pack")?.toInt()?.takeIf { it > 0 },
            packPrice = o.optDoubleOrNull("price")?.takeIf { it >= 0 },
            note = o.optString("note").trim().take(NOTE_MAX),
        )
        val groupKey = if (plan == Plan.Group) schedule?.optString("group")?.trim()?.ifEmpty { null } else null
        return ParsedItem(s, groupKey, schedule?.optInt("doneDays", 0)?.coerceIn(0, 3650) ?: 0)
    }

    private fun backup(b: JSONObject): Backup = Backup(
        supplements = b.optJSONArray("supplements").objects().map { o ->
            Supplement(
                id = o.getLong("id"),
                name = o.optString("name").take(NAME_MAX),
                form = o.optString("form", Form.Capsule.name),
                mgPerUnit = o.optDoubleOrNull("mgPerUnit"),
                unitsPerDose = o.optDouble("unitsPerDose", 1.0),
                slot = o.optString("slot", Slot.Wake.name),
                plan = o.optString("plan", Plan.Daily.name),
                onDays = o.optInt("onDays", 0),
                offDays = o.optInt("offDays", 0),
                startDay = o.optLong("startDay", 0),
                groupId = if (o.isNull("groupId") || !o.has("groupId")) null else o.getLong("groupId"),
                groupOrder = o.optInt("groupOrder", 0),
                unitsLeft = o.optDoubleOrNull("unitsLeft"),
                packUnits = o.optDoubleOrNull("packUnits")?.toInt(),
                packPrice = o.optDoubleOrNull("packPrice"),
                note = o.optString("note"),
                sortOrder = o.optInt("sortOrder", 0),
                archived = o.optBoolean("archived", false),
            )
        },
        groups = b.optJSONArray("groups").objects().map { o ->
            AltGroup(id = o.getLong("id"), name = o.optString("name"), breakDays = o.optInt("breakDays", 0), startDay = o.optLong("startDay", 0))
        },
        intakes = b.optJSONArray("intakes").objects().map { o ->
            Intake(supplementId = o.getLong("s"), day = o.getLong("d"), units = o.optDouble("u", 1.0), at = o.optLong("t", 0))
        },
        checkIns = b.optJSONArray("checkIns").objects().map { o ->
            CheckIn(
                day = o.getLong("d"),
                a1 = o.optIntOrNull("a1"),
                a2 = o.optIntOrNull("a2"),
                a3 = o.optIntOrNull("a3"),
                a4 = o.optIntOrNull("a4"),
                dayType = if (o.has("type") && !o.isNull("type")) o.optString("type") else null,
                note = o.optString("note"),
            )
        },
        purchases = b.optJSONArray("purchases").objects().map { o ->
            Purchase(supplementId = o.getLong("s"), day = o.getLong("d"), units = o.optDoubleOrNull("u")?.toInt(), price = o.optDoubleOrNull("p"))
        },
    )

    /** Повна копія: схема (зручно читати й правити) + уся історія. */
    fun export(
        supplements: List<Supplement>,
        groups: List<AltGroup>,
        intakes: List<Intake>,
        checkIns: List<CheckIn>,
        purchases: List<Purchase>,
        today: Long,
    ): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        val keys = groups.associate { it.id to "g${it.id}" }
        root.put("groups", JSONArray().apply {
            groups.forEach { g -> put(JSONObject().put("key", keys[g.id]).put("name", g.name).put("breakDays", g.breakDays)) }
        })
        root.put("supplements", JSONArray().apply {
            supplements.filter { !it.archived }.forEach { s ->
                val schedule = JSONObject()
                when (s.planE) {
                    Plan.Daily -> schedule.put("type", "daily")
                    Plan.AsNeeded -> schedule.put("type", "asNeeded")
                    Plan.Cycle -> schedule.put("type", "cycle").put("on", s.onDays).put("off", s.offDays)
                        .put("doneDays", (today - s.startDay).coerceAtLeast(0).toInt())
                    Plan.Group -> {
                        schedule.put("type", "group").put("group", keys[s.groupId] ?: "").put("days", s.onDays).put("order", s.groupOrder)
                        val g = groups.firstOrNull { it.id == s.groupId }
                        if (g != null) {
                            val st = Schedule.status(s, today, groups, supplements)
                            if (st.phase == Phase.Active) schedule.put("doneDays", st.dayNo - 1)
                        }
                    }
                }
                put(
                    JSONObject()
                        .put("name", s.name)
                        .put("form", s.formE.name.lowercase())
                        .put("mg", s.mgPerUnit ?: JSONObject.NULL)
                        .put("perDose", s.unitsPerDose)
                        .put("slot", s.slotE.name.lowercase())
                        .put("schedule", schedule)
                        .put("stock", s.unitsLeft ?: JSONObject.NULL)
                        .put("pack", s.packUnits ?: JSONObject.NULL)
                        .put("price", s.packPrice ?: JSONObject.NULL)
                        .put("note", s.note),
                )
            }
        })
        val backup = JSONObject()
        backup.put("supplements", JSONArray().apply {
            supplements.forEach { s ->
                put(
                    JSONObject().put("id", s.id).put("name", s.name).put("form", s.form).put("mgPerUnit", s.mgPerUnit ?: JSONObject.NULL)
                        .put("unitsPerDose", s.unitsPerDose).put("slot", s.slot).put("plan", s.plan).put("onDays", s.onDays)
                        .put("offDays", s.offDays).put("startDay", s.startDay).put("groupId", s.groupId ?: JSONObject.NULL)
                        .put("groupOrder", s.groupOrder).put("unitsLeft", s.unitsLeft ?: JSONObject.NULL)
                        .put("packUnits", s.packUnits ?: JSONObject.NULL).put("packPrice", s.packPrice ?: JSONObject.NULL)
                        .put("note", s.note).put("sortOrder", s.sortOrder).put("archived", s.archived),
                )
            }
        })
        backup.put("groups", JSONArray().apply {
            groups.forEach { g -> put(JSONObject().put("id", g.id).put("name", g.name).put("breakDays", g.breakDays).put("startDay", g.startDay)) }
        })
        backup.put("intakes", JSONArray().apply {
            intakes.forEach { i -> put(JSONObject().put("s", i.supplementId).put("d", i.day).put("u", i.units).put("t", i.at)) }
        })
        backup.put("checkIns", JSONArray().apply {
            checkIns.forEach { c ->
                put(
                    JSONObject().put("d", c.day).put("a1", c.a1 ?: JSONObject.NULL).put("a2", c.a2 ?: JSONObject.NULL)
                        .put("a3", c.a3 ?: JSONObject.NULL).put("a4", c.a4 ?: JSONObject.NULL)
                        .put("type", c.dayType ?: JSONObject.NULL).put("note", c.note),
                )
            }
        })
        backup.put("purchases", JSONArray().apply {
            purchases.forEach { p -> put(JSONObject().put("s", p.supplementId).put("d", p.day).put("u", p.units ?: JSONObject.NULL).put("p", p.price ?: JSONObject.NULL)) }
        })
        root.put("backup", backup)
        return root.toString(2)
    }

    private const val NAME_MAX = 80
    private const val NOTE_MAX = 300
    private const val MAX_ITEMS = 300

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isNaN() || v.isInfinite()) null else v
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = optDoubleOrNull(key)?.toInt()

    private inline fun <reified T : Enum<T>> enumByName(name: String?): T? {
        val n = name?.trim()?.lowercase()?.replace("_", "") ?: return null
        return enumValues<T>().firstOrNull { it.name.lowercase() == n }
    }
}
