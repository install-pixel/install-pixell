package com.blacklaunch.qrcodes.label

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Чернетка наклейки: останній макет на екрані друку зберігається сам (після кожної зміни), тож вийшов — і нічого не
 * пропало. Відкриваєш друк тих самих кодів — повертається, як був.
 */
class DraftStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("label_draft", Context.MODE_PRIVATE)

    fun load(): LabelDesign? = prefs.getString(KEY, null)?.let { runCatching { TemplateStore.designFrom(JSONObject(it)) }.getOrNull() }

    fun save(design: LabelDesign) {
        prefs.edit().putString(KEY, TemplateStore.designJson(design).toString()).apply()
    }

    private companion object {
        const val KEY = "design"
    }
}

/** Файли фону (свої фото), скопійовані в пам'ять програми. */
object BackgroundFiles {
    fun dir(context: Context): File = File(context.applicationContext.filesDir, "backgrounds").apply { mkdirs() }

    /**
     * Прибрати фото, якими вже ніщо не користується (ні шаблони, ні чернетка) — інакше кожне вибране фото лишалося б
     * назавжди. Свіжі (останні [GRACE_MS]) не чіпаємо: їх могли щойно вибрати.
     */
    fun cleanup(context: Context) {
        runCatching {
            val used = HashSet<String>()
            TemplateStore(context).load().forEach { t -> t.design.background?.source?.let(used::add) }
            DraftStore(context).load()?.background?.source?.let(used::add)
            val now = System.currentTimeMillis()
            dir(context).listFiles()?.forEach { f ->
                if (f.absolutePath !in used && now - f.lastModified() > GRACE_MS) f.delete()
            }
        }
    }

    /** Фото фону — у JSON макета (base64), щоб резервна копія переносила й картинку. */
    fun embed(designJson: JSONObject): JSONObject {
        val bg = designJson.optJSONObject("bgi") ?: return designJson
        val src = bg.optString("src")
        if (src.isNotEmpty() && !src.startsWith("asset:")) {
            val f = File(src)
            if (f.exists() && f.length() < MAX_EMBED_BYTES) {
                bg.put("data", android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
            }
        }
        return designJson
    }

    /** Зворотне до [embed]: картинку з JSON — у новий файл, шлях — у макет. */
    fun unpack(context: Context, designJson: JSONObject): JSONObject {
        val bg = designJson.optJSONObject("bgi") ?: return designJson
        val data = bg.optString("data")
        if (data.isNotEmpty()) {
            runCatching {
                val file = File(dir(context), "bg_${System.currentTimeMillis()}_${(0..9999).random()}.jpg")
                file.writeBytes(android.util.Base64.decode(data, android.util.Base64.NO_WRAP))
                bg.put("src", file.absolutePath)
            }
            bg.remove("data")
        }
        return designJson
    }

    private const val GRACE_MS = 10 * 60 * 1000L
    private const val MAX_EMBED_BYTES = 6L * 1024 * 1024
}
