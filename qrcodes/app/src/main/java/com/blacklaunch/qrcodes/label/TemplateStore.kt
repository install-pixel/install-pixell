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

    /** Готові шаблони, які сховали (довгим натисканням). */
    fun hiddenBuiltIns(): Set<String> = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()

    fun setHiddenBuiltIns(names: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN, names).apply()
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
        private const val KEY_HIDDEN = "hidden_builtin"
        const val MAX = 12

        fun designJson(d: LabelDesign): JSONObject = JSONObject()
            .put("w", d.size.widthMm.toDouble())
            .put("h", d.size.heightMm.toDouble())
            .put("custom", d.size.custom)
            .put("screen", d.size.screen)
            .put("border", d.border.name)
            .apply {
                d.background?.let { b ->
                    put("bgi", JSONObject().put("src", b.source).put("a", b.aspect.toDouble()).put("cx", b.cx.toDouble()).put("cy", b.cy.toDouble()).put("w", b.width.toDouble()).put("r", b.rotation.toDouble())
                        .put("e", JSONArray().apply { b.erased.forEach { st -> put(JSONArray().apply { st.forEach { v -> put(Math.round(v * 10000) / 10000.0) } }) } }))
                }
            }
            .put("elements", JSONArray().apply { d.elements.forEach { put(elementJson(it)) } })

        private fun elementJson(e: Element): JSONObject {
            val o = JSONObject().put("id", e.id).put("cx", e.cx.toDouble()).put("cy", e.cy.toDouble()).put("rot", e.rotation.toDouble())
            return when (e) {
                is Element.Qr -> o.put("t", "qr").put("content", e.content).put("title", e.title).put("side", e.side.toDouble())
                    .put("caption", e.caption).put("frame", e.frame.name).put("banner", e.banner).put("barcode", e.barcode)
                is Element.Text -> o.put("t", "text").put("text", e.text).put("letters", e.letters.toDouble())
                is Element.Icon -> o.put("t", "icon").put("sticker", e.sticker.name).put("side", e.side.toDouble())
                is Element.Drawing -> o.put("t", "draw").put("w", e.width.toDouble()).put("a", e.aspect.toDouble()).put("thick", e.thick)
                    .put("s", JSONArray().apply { e.strokes.forEach { st -> put(JSONArray().apply { st.forEach { v -> put(Math.round(v * 10000) / 10000.0) } }) } })
            }
        }

        fun designFrom(o: JSONObject): LabelDesign? = runCatching {
            val arr = o.getJSONArray("elements")
            LabelDesign(
                size = LabelSize(o.getDouble("w").toFloat(), o.getDouble("h").toFloat(), o.optBoolean("custom"), o.optBoolean("screen")),
                border = QrFrame.entries.firstOrNull { it.name == o.optString("border") } ?: QrFrame.None,
                background = backgroundFrom(o),
                elements = (0 until arr.length()).mapNotNull { i -> elementFrom(arr.getJSONObject(i)) },
            )
        }.getOrNull()

        /** Тло: нове ({src, a, cx, cy, w, r}); з 1.8.0 — лише шлях до файлу (тоді — на весь аркуш). */
        private fun backgroundFrom(o: JSONObject): BgImage? {
            o.optJSONObject("bgi")?.let { b ->
                val src = b.optString("src")
                if (src.isEmpty() || (!src.startsWith("asset:") && !java.io.File(src).exists())) return null
                val er = b.optJSONArray("e") ?: JSONArray()
                val erased = (0 until er.length()).map { i -> er.getJSONArray(i).let { st -> (0 until st.length()).map { j -> st.getDouble(j).toFloat() } } }
                return BgImage(src, b.optDouble("a", 1.0).toFloat(), b.optDouble("cx").toFloat(), b.optDouble("cy").toFloat(), b.optDouble("w").toFloat(), b.optDouble("r", 0.0).toFloat(), erased)
            }
            val old = o.optString("bg").takeIf { it.isNotEmpty() && java.io.File(it).exists() } ?: return null
            val size = LabelSize(o.getDouble("w").toFloat(), o.getDouble("h").toFloat())
            return BgImage(old, imageAspect(old), 0f, 0f, 1f).filled(size)
        }

        /** Висота / ширина зображення у файлі (без декодування самої картинки). */
        fun imageAspect(path: String): Float = runCatching {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, o)
            if (o.outWidth > 0) o.outHeight.toFloat() / o.outWidth else 1f
        }.getOrDefault(1f)

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
                "draw" -> {
                    val arr = o.optJSONArray("s") ?: JSONArray()
                    val strokes = (0 until arr.length()).map { i ->
                        val st = arr.getJSONArray(i)
                        (0 until st.length()).map { j -> st.getDouble(j).toFloat() }
                    }
                    Element.Drawing(id, cx, cy, strokes, o.optDouble("a", 1.0).toFloat(), o.getDouble("w").toFloat(), o.optBoolean("thick"), rot)
                }
                "icon" -> Sticker.entries.firstOrNull { it.name == o.optString("sticker") }?.let { Element.Icon(id, cx, cy, it, o.getDouble("side").toFloat(), rot) }
                else -> null
            }
        }
    }
}
