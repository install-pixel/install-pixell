package com.blacklaunch.diary.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.UUID

/**
 * Щоденник на полиці. Тут лише те, що видно без коду: назва, обкладинка (уже оброблена під обидві теми), чи стоїть
 * замок. Усе написане — у зашифрованій базі самого щоденника ([DiaryDb]).
 */
data class DiaryInfo(
    val id: String,
    val name: String,
    val created: Long,
    /** Чи поставлено код (4 барабани). Без коду щоденник теж зашифрований — ключем телефона. */
    val locked: Boolean,
    /** Сіль для коду (base64), окрема для кожного щоденника. */
    val salt: String,
    /** Невдалі спроби підряд і час, раніше якого нову спробу не приймаємо (захист від перебору). */
    val fails: Int = 0,
    val retryAt: Long = 0L,
    /** Своє фото на обкладинці (лежить уже оброблене: [ShelfStore.coverFile]). */
    val cover: Boolean = false,
    /** Відтінок обкладинки без фото — щоб щоденники на полиці розрізнялись (0…5). */
    val tint: Int = 0,
)

/** Полиця — маленький JSON-файл у приватній теці програми. */
class ShelfStore(context: Context) {
    private val root = context.applicationContext.filesDir
    private val file = File(root, "shelf.json")

    fun list(): List<DiaryInfo> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i -> arr.getJSONObject(i).toInfo() }
        }.getOrDefault(emptyList())
    }

    fun save(list: List<DiaryInfo>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        // Спершу в тимчасовий файл, потім заміна — щоб обрив на півдорозі не зіпсував полицю.
        val tmp = File(root, "shelf.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    fun newDiary(name: String, tint: Int): DiaryInfo {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return DiaryInfo(
            id = UUID.randomUUID().toString().replace("-", "").take(16),
            name = name,
            created = System.currentTimeMillis(),
            locked = false,
            salt = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP),
            tint = tint,
        )
    }

    /** Тека щоденника: база й зашифровані фото та аудіо. */
    fun dir(id: String): File = File(root, "diaries/$id").also { it.mkdirs() }

    /** Обкладинка, уже оброблена під тему ([glass] — «Скло», інакше «Аркуш»). */
    fun coverFile(id: String, glass: Boolean): File = File(root, "covers/${id}_${if (glass) "glass" else "sheet"}.jpg")

    fun saveCover(id: String, glass: Bitmap, sheet: Bitmap) {
        File(root, "covers").mkdirs()
        coverFile(id, true).outputStream().use { glass.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        coverFile(id, false).outputStream().use { sheet.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    fun loadCover(id: String, glass: Boolean): Bitmap? =
        coverFile(id, glass).takeIf { it.exists() }?.let { f -> runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() }

    fun removeCover(id: String) {
        coverFile(id, true).delete()
        coverFile(id, false).delete()
    }

    /** Стерти щоденник повністю: базу, фото, аудіо, обкладинку й ключ у сховищі ключів телефона. */
    fun erase(id: String) {
        File(root, "diaries/$id").deleteRecursively()
        removeCover(id)
        DiaryKeys.deleteKey(id)
    }

    private fun JSONObject.toInfo() = DiaryInfo(
        id = getString("id"),
        name = optString("name"),
        created = optLong("created"),
        locked = optBoolean("locked"),
        salt = getString("salt"),
        fails = optInt("fails"),
        retryAt = optLong("retryAt"),
        cover = optBoolean("cover"),
        tint = optInt("tint"),
    )

    private fun DiaryInfo.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("created", created)
        .put("locked", locked)
        .put("salt", salt)
        .put("fails", fails)
        .put("retryAt", retryAt)
        .put("cover", cover)
        .put("tint", tint)
}
