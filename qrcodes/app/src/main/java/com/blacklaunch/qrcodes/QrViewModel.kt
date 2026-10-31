package com.blacklaunch.qrcodes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacklaunch.qrcodes.data.QrCode
import com.blacklaunch.qrcodes.data.QrDatabase
import com.blacklaunch.qrcodes.qr.CodeFormat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Що сталося після «Зберегти». */
enum class SaveResult { Saved, AlreadySaved }

class QrViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = QrDatabase.get(app).codes()

    val all: StateFlow<List<QrCode>?> = dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val favorites: StateFlow<List<QrCode>?> = dao.favorites().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Зберігає код. Такий самий зміст уже є — не дублюємо, а піднімаємо його нагору списку (з новим форматом і, якщо
     * дали, назвою). [name] — запропонована назва (з форми Wi‑Fi / контакту / сайту).
     */
    fun save(content: String, format: CodeFormat, name: String?, onDone: (SaveResult) -> Unit) {
        if (content.isEmpty()) return
        viewModelScope.launch {
            val existing = dao.byContent(content)
            if (existing != null) {
                dao.update(existing.copy(createdAt = System.currentTimeMillis(), format = format.name, name = name ?: existing.name))
                onDone(SaveResult.AlreadySaved)
            } else {
                dao.insert(QrCode(content = content, name = name, format = format.name))
                onDone(SaveResult.Saved)
            }
        }
    }

    fun toggleFavorite(code: QrCode) = viewModelScope.launch {
        dao.update(code.copy(favoriteAt = if (code.isFavorite) null else System.currentTimeMillis()))
    }

    /** Вибрані в новому порядку (після перетягування на головному). */
    fun reorderFavorites(ids: List<Long>) = viewModelScope.launch { dao.reorderFavorites(ids) }

    /** Прибрати з вибраного (сам код лишається серед збережених). */
    fun unfavorite(code: QrCode) = viewModelScope.launch { dao.setFavoriteAt(code.id, null) }

    /** «Повернути» — на те саме місце серед вибраних. */
    fun refavorite(code: QrCode) = viewModelScope.launch { dao.setFavoriteAt(code.id, code.favoriteAt ?: System.currentTimeMillis()) }

    /** Своя назва; порожня — знову підписується змістом. */
    fun rename(code: QrCode, name: String) = viewModelScope.launch {
        dao.update(code.copy(name = name.trim().ifEmpty { null }))
    }

    fun delete(code: QrCode) = viewModelScope.launch { dao.delete(code.id) }

    /**
     * Експорт: усі коди одним JSON-файлом (зміст, назва, QR чи штрихкод, вибране, дата) — резервна копія чи перенесення
     * на інший телефон. Пишемо в [out] (файл, який вибрав користувач). Результат — скільки кодів.
     */
    suspend fun exportTo(out: java.io.OutputStream): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val codes = dao.snapshot()
        val arr = org.json.JSONArray()
        codes.forEach { c ->
            arr.put(
                org.json.JSONObject()
                    .put("content", c.content)
                    .put("name", c.name ?: org.json.JSONObject.NULL)
                    .put("format", c.format)
                    .put("favoriteAt", c.favoriteAt ?: org.json.JSONObject.NULL)
                    .put("createdAt", c.createdAt),
            )
        }
        val root = org.json.JSONObject().put("app", "QRaft").put("version", 1).put("codes", arr)
        out.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
        codes.size
    }

    /**
     * Імпорт із файлу експорту: нові коди додаються; ті, що вже є (той самий зміст), не дублюються — лише отримують
     * назву, якщо своєї не було. Результат — скільки додано; -1 — файл не наш чи пошкоджений.
     */
    suspend fun importFrom(input: java.io.InputStream): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val text = runCatching { input.bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull() ?: return@withContext -1
        val arr = runCatching { org.json.JSONObject(text).getJSONArray("codes") }.getOrNull() ?: return@withContext -1
        var added = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val content = o.optString("content")
            if (content.isEmpty()) continue
            val name = if (o.isNull("name")) null else o.optString("name").ifEmpty { null }
            val existing = dao.byContent(content)
            if (existing != null) {
                if (existing.name == null && name != null) dao.update(existing.copy(name = name))
            } else {
                dao.insert(
                    QrCode(
                        content = content,
                        name = name,
                        format = if (o.optString("format") == CodeFormat.BARCODE.name) CodeFormat.BARCODE.name else CodeFormat.QR.name,
                        favoriteAt = if (o.isNull("favoriteAt")) null else o.optLong("favoriteAt"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    ),
                )
                added++
            }
        }
        added
    }

    /** «Повернути» після видалення — той самий запис (з назвою, вибраним і датою). */
    fun restore(code: QrCode) = viewModelScope.launch { dao.insert(code) }
}
