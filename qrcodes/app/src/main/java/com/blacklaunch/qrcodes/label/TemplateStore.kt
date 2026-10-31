package com.blacklaunch.qrcodes.label

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Свій шаблон: назва й макет наклейки (розмір, елементи, рамка аркуша). */
data class SavedTemplate(val name: String, val design: LabelDesign)

/** Свої шаблони наклейок — у налаштуваннях програми, на телефоні (не більше [MAX]). */
class TemplateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("templates", Context.MODE_PRIVATE)

    fun load(): List<SavedTemplate> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::templateFrom) }
    }.getOrDefault(emptyList())

    /** Додає (однакова назва — замінює) і повертає оновлений список. */
    fun save(name: String, design: LabelDesign): List<SavedTemplate> {
        val list = (load().filter { it.name != name } + SavedTemplate(name, design)).takeLast(MAX)
        write(list)
        return list
    }

    fun remove(name: String): List<SavedTemplate> {
        val list = load().filter { it.name != name }
        write(list)
        return list
    }

    private fun write(list: List<SavedTemplate>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("design", designJson(it.design))) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun templateFrom(o: JSONObject): SavedTemplate? {
        val design = o.optJSONObject("design") ?: return null
        return SavedTemplate(o.optString("name"), designFrom(design) ?: return null)
    }

    companion object {
        private const val KEY = "list"
        const val MAX = 12

        fun designJson(d: LabelDesign): JSONObject = JSONObject()
            .put("w", d.size.widthMm.toDouble())
            .put("h", d.size.heightMm.toDouble())
            .put("custom", d.size.custom)
            .put("border", d.border.name)
            .put("elements", JSONArray().apply { d.elements.forEach { put(elementJson(it)) } })

        private fun elementJson(e: Element): JSONObject {
            val o = JSONObject().put("id", e.id).put("cx", e.cx.toDouble()).put("cy", e.cy.toDouble()).put("rot", e.rotation.toDouble())
            return when (e) {
                is Element.Qr -> o.put("t", "qr").put("content", e.content).put("title", e.title).put("side", e.side.toDouble())
                    .put("caption", e.caption).put("frame", e.frame.name).put("banner", e.banner).put("barcode", e.barcode)
                is Element.Text -> o.put("t", "text").put("text", e.text).put("letters", e.letters.toDouble())
                is Element.Icon -> o.put("t", "icon").put("sticker", e.sticker.name).put("side", e.side.toDouble())
            }
        }

        fun designFrom(o: JSONObject): LabelDesign? = runCatching {
            val arr = o.getJSONArray("elements")
            LabelDesign(
                size = LabelSize(o.getDouble("w").toFloat(), o.getDouble("h").toFloat(), o.optBoolean("custom")),
                border = QrFrame.entries.firstOrNull { it.name == o.optString("border") } ?: QrFrame.None,
                elements = (0 until arr.length()).mapNotNull { i -> elementFrom(arr.getJSONObject(i)) },
            )
        }.getOrNull()

        private fun elementFrom(o: JSONObject): Element? {
            val id = o.getInt("id")
            val cx = o.getDouble("cx").toFloat()
            val cy = o.getDouble("cy").toFloat()
            val rot = o.optDouble("rot", 0.0).toFloat()
            return when (o.optString("t")) {
                "qr" -> Element.Qr(
                    id, cx, cy, o.optString("content"), o.optString("title"), o.getDouble("side").toFloat(),
                    caption = o.optBoolean("caption", true),
                    frame = QrFrame.entries.firstOrNull { it.name == o.optString("frame") } ?: QrFrame.None,
                    banner = o.optString("banner"),
                    barcode = o.optBoolean("barcode"),
                    rotation = rot,
                )
                "text" -> Element.Text(id, cx, cy, o.optString("text"), o.getDouble("letters").toFloat(), rot)
                "icon" -> Sticker.entries.firstOrNull { it.name == o.optString("sticker") }?.let { Element.Icon(id, cx, cy, it, o.getDouble("side").toFloat(), rot) }
                else -> null
            }
        }
    }
}
