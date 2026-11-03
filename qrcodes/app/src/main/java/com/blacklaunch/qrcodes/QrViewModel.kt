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
    val folders: StateFlow<List<com.blacklaunch.qrcodes.data.Folder>> = dao.folders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Новий порядок у списку (ключі «f<id>» / «c<id>» зверху вниз). */
    fun reorder(keys: List<String>) = viewModelScope.launch { dao.reorder(keys) }

    /** Нова папка з кодами [ids] — на місці верхнього з них. */
    fun createFolder(name: String, ids: List<Long>, order: Long, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        onDone(dao.createFolder(name.trim().ifEmpty { "…" }, ids, order))
    }

    /** Код — у папку ([folderId] null — вийняти в загальний список, нагору). */
    fun moveToFolder(code: QrCode, folderId: Long?) = viewModelScope.launch {
        dao.update(code.copy(folderId = folderId, sortOrder = System.currentTimeMillis()))
    }

    fun renameFolder(id: Long, name: String) = viewModelScope.launch { dao.renameFolder(id, name.trim().ifEmpty { "…" }) }

    /** Розпустити папку — коди лишаються в загальному списку. */
    fun ungroup(id: Long) = viewModelScope.launch { dao.ungroup(id) }
    fun deleteFolder(id: Long) = viewModelScope.launch { dao.deleteFolderWithCodes(id) }

    /**
     * Зберігає код. Такий самий зміст уже є — не дублюємо, а піднімаємо його нагору списку (з новим форматом і, якщо
     * дали, назвою). [name] — запропонована назва (з форми Wi‑Fi / контакту / сайту).
     */
    fun save(content: String, kind: com.blacklaunch.qrcodes.qr.CodeKind, name: String?, onDone: (SaveResult) -> Unit) {
        if (content.isEmpty()) return
        viewModelScope.launch {
            val existing = dao.byContent(content)
            if (existing != null) {
                val now = System.currentTimeMillis()
                dao.update(existing.copy(createdAt = now, sortOrder = now, format = kind.storage, name = name ?: existing.name))
                onDone(SaveResult.AlreadySaved)
            } else {
                dao.insert(QrCode(content = content, name = name, format = kind.storage))
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

    /**
     * Виправити зміст коду (назва, папка, вибране лишаються). [onResult]: true — змінено; false — такий код уже є
     * серед збережених.
     */
    fun editContent(code: QrCode, content: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val text = content.trim()
        if (text.isEmpty() || text == code.content) {
            onResult(true)
            return@launch
        }
        val other = dao.byContent(text)
        if (other != null && other.id != code.id) {
            onResult(false)
        } else {
            dao.update(code.copy(content = text))
            onResult(true)
        }
    }

    /**
     * Багато кодів одразу (серія чи список) — усі в одну нову папку [folderName]. Ті, що вже є, не дублюються, а теж
     * переходять у цю папку. [onDone] — коди в тому самому порядку.
     */
    fun createBatch(contents: List<String>, kind: com.blacklaunch.qrcodes.qr.CodeKind, folderName: String, onDone: (List<QrCode>) -> Unit) = viewModelScope.launch {
        val base = System.currentTimeMillis()
        val ids = contents.mapIndexed { i, text ->
            val existing = dao.byContent(text)
            // Порядок у папці — як у серії (перший — нагорі).
            val order = base - i
            if (existing != null) {
                dao.update(existing.copy(sortOrder = order))
                existing.id
            } else {
                dao.insert(QrCode(content = text, format = kind.storage, createdAt = base, sortOrder = order))
            }
        }
        dao.createFolder(folderName.trim().ifEmpty { "…" }, ids, base + 1)
        onDone(contents.mapNotNull { dao.byContent(it) })
    }

    fun delete(code: QrCode) = viewModelScope.launch { dao.delete(code.id) }

    /**
     * Експорт: усі коди одним JSON-файлом (зміст, назва, QR чи штрихкод, вибране, дата) — резервна копія чи перенесення
     * на інший телефон. Пишемо в [out] (файл, який вибрав користувач). Результат — скільки кодів.
     */
    suspend fun exportTo(out: java.io.OutputStream): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val codes = dao.snapshot()
        val folderNames = dao.foldersSnapshot().associate { it.id to it.name }
        val arr = org.json.JSONArray()
        codes.forEach { c ->
            arr.put(
                org.json.JSONObject()
                    .put("folder", c.folderId?.let { folderNames[it] } ?: org.json.JSONObject.NULL)
                    .put("content", c.content)
                    .put("name", c.name ?: org.json.JSONObject.NULL)
                    .put("format", c.format)
                    .put("favoriteAt", c.favoriteAt ?: org.json.JSONObject.NULL)
                    .put("createdAt", c.createdAt),
            )
        }
        // Свої шаблони наклейок — теж у файл, щоб переїхали на новий телефон разом із кодами.
        val templates = org.json.JSONArray()
        com.blacklaunch.qrcodes.label.TemplateStore(getApplication()).load().forEach { t ->
            // Разом із фото фону (base64) — інакше на новому телефоні шаблон відкрився б без картинки.
            templates.put(org.json.JSONObject().put("name", t.name).put("design", com.blacklaunch.qrcodes.label.BackgroundFiles.embed(com.blacklaunch.qrcodes.label.TemplateStore.designJson(t.design))))
        }
        // Форми для сканера — теж у файл.
        val forms = org.json.JSONArray().apply { com.blacklaunch.qrcodes.data.FormStore(getApplication()).load().forEach { put(it.toJson()) } }
        val root = org.json.JSONObject().put("app", "QRaft").put("version", 1).put("codes", arr).put("templates", templates).put("forms", forms)
        out.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
        codes.size
    }

    /**
     * Імпорт із файлу експорту: нові коди додаються; ті, що вже є (той самий зміст), не дублюються — лише отримують
     * назву, якщо своєї не було. Результат — скільки додано; -1 — файл не наш чи пошкоджений.
     */
    suspend fun importFrom(input: java.io.InputStream): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val text = runCatching { input.bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull() ?: return@withContext -1
        val root = runCatching { org.json.JSONObject(text) }.getOrNull() ?: return@withContext -1
        val arr = root.optJSONArray("codes") ?: return@withContext -1
        // Шаблони з файлу: додаємо ті, яких ще немає (за назвою).
        root.optJSONArray("templates")?.let { list ->
            val store = com.blacklaunch.qrcodes.label.TemplateStore(getApplication())
            val have = store.load().map { it.name }.toSet()
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val name = o.optString("name")
                val design = o.optJSONObject("design")?.let { com.blacklaunch.qrcodes.label.TemplateStore.designFrom(com.blacklaunch.qrcodes.label.BackgroundFiles.unpack(getApplication(), it)) } ?: continue
                if (name.isNotEmpty() && name !in have) store.save(name, design)
            }
        }
        // Форми для сканера: додаємо ті, яких ще немає (за id).
        root.optJSONArray("forms")?.let { list ->
            val store = com.blacklaunch.qrcodes.data.FormStore(getApplication())
            val have = store.load().map { it.id }.toSet()
            for (i in 0 until list.length()) {
                val f = list.optJSONObject(i)?.let(com.blacklaunch.qrcodes.qr.ScanForm::fromJson) ?: continue
                if (f.id !in have) store.save(f)
            }
        }
        var added = 0
        // Папки — за назвою: є така — туди, немає — створюємо.
        val folderIds = dao.foldersSnapshot().associate { it.name to it.id }.toMutableMap()
        suspend fun folderFor(name: String?): Long? {
            if (name.isNullOrBlank()) return null
            return folderIds[name] ?: dao.insertFolder(com.blacklaunch.qrcodes.data.Folder(name = name)).also { folderIds[name] = it }
        }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val content = o.optString("content")
            if (content.isEmpty()) continue
            val folderName = if (o.isNull("folder")) null else o.optString("folder")
            val name = if (o.isNull("name")) null else o.optString("name").ifEmpty { null }
            val existing = dao.byContent(content)
            if (existing != null) {
                if (existing.name == null && name != null) dao.update(existing.copy(name = name))
            } else {
                dao.insert(
                    QrCode(
                        content = content,
                        name = name,
                        format = com.blacklaunch.qrcodes.qr.CodeKind.of(o.optString("format")).storage,
                        favoriteAt = if (o.isNull("favoriteAt")) null else o.optLong("favoriteAt"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        folderId = folderFor(folderName),
                        sortOrder = o.optLong("createdAt", System.currentTimeMillis()),
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
