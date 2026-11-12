package com.blacklaunch.diary

import com.blacklaunch.diary.audio.Tape

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacklaunch.diary.data.Backup
import com.blacklaunch.diary.data.Day
import com.blacklaunch.diary.data.DiaryDb
import com.blacklaunch.diary.data.DiaryInfo
import com.blacklaunch.diary.data.DiaryKeys
import com.blacklaunch.diary.data.InkLine
import com.blacklaunch.diary.data.ShelfStore
import com.blacklaunch.diary.data.Strike
import com.blacklaunch.diary.image.Photos
import com.blacklaunch.diary.search.SearchHit
import com.blacklaunch.diary.search.Searcher
import com.blacklaunch.diary.settings.AppSettings
import com.blacklaunch.diary.settings.InkDrying
import com.blacklaunch.diary.ui.Limits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Відкритий (розшифрований) щоденник. */
class OpenDiary(val info: DiaryInfo, val db: DiaryDb) {
    val days = mutableStateMapOf<Long, Day>()
    var bookmark by mutableStateOf<Long?>(null)
}

/** Результат спроби відкрити замок. */
sealed interface OpenResult {
    data object Opened : OpenResult
    data class Wrong(val retryAt: Long) : OpenResult
    data class Wait(val retryAt: Long) : OpenResult
    data object Failed : OpenResult
}

/** Сповіщення вгорі (з «Повернути», якщо є що повертати). */
data class Notice(val text: Int, val undo: (() -> Unit)? = null, val id: Long = System.nanoTime(), val danger: Boolean = false)

class DiaryViewModel(app: Application) : AndroidViewModel(app) {
    val settings = AppSettings(app)
    private val store = ShelfStore(app)

    /** Усі звернення до бази — по черзі, в одному фоновому потоці. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)

    var shelf by mutableStateOf(store.list())
        private set

    /** Щоденник, що лежить перед тобою (обкладинка); null — полиця. */
    var current by mutableStateOf<String?>(null)

    var open by mutableStateOf<OpenDiary?>(null)
        private set

    var notice by mutableStateOf<Notice?>(null)

    /** Шестерня під корінцем на полиці: щоденник відкривається одразу на своїх налаштуваннях. */
    var settingsFor by mutableStateOf<String?>(null)
    private var noticeJob: Job? = null

    /** Чи ще готується (обробка фото, шифрування) — щоб показати, що щось відбувається. */
    var busy by mutableStateOf(false)
        private set

    init {
        if (shelf.isEmpty()) {
            val first = store.newDiary(app.getString(R.string.default_diary_name), 0)
            saveShelf(listOf(first))
        }
        if (shelf.size == 1) current = shelf[0].id
    }

    private fun saveShelf(list: List<DiaryInfo>) {
        shelf = list
        store.save(list)
    }

    fun info(id: String?): DiaryInfo? = shelf.firstOrNull { it.id == id }

    /** Налаштування окремого щоденника (кожен — свої; спільні налаштування їх не чіпають). */
    /** Чорнило висихає через день — для всіх щоденників однаково, без вибору. */
    @Suppress("UNUSED_PARAMETER")
    fun dryingOf(info: DiaryInfo?): InkDrying = InkDrying.Day
    fun openAtBookmarkOf(info: DiaryInfo?): Boolean = info?.openAtBookmark ?: settings.openAtBookmark

    fun setDrying(id: String, drying: InkDrying) = saveShelf(shelf.map { if (it.id == id) it.copy(drying = drying) else it })
    fun setVoiceMode(id: String, mode: Int) = saveShelf(shelf.map { if (it.id == id) it.copy(voiceMode = mode.coerceIn(0, 2)) else it })

    /**
     * Розшифрована копія сторони касети — щоб поділитися (системне вікно «Поділитися»). Лежить у кеші лише до наступного
     * разу (тека щоразу чиститься).
     */
    suspend fun exportAudio(epochDay: Long, side: Int): java.io.File? {
        val bytes = audio(epochDay, side) ?: return null
        return withContext(io) {
            val dir = java.io.File(getApplication<Application>().cacheDir, "share").apply { deleteRecursively(); mkdirs() }
            val date = java.time.LocalDate.ofEpochDay(epochDay)
            java.io.File(dir, "${date}_${if (side == 0) "A" else "B"}.m4a").also { it.writeBytes(bytes) }
        }
    }

    fun setCarryPlace(id: String, on: Boolean) = saveShelf(shelf.map { if (it.id == id) it.copy(carryPlace = on) else it })

    /**
     * Місце, перенесене з попереднього дня: для свіжої сторінки без місця й без нічого написаного (якщо в щоденнику це
     * ввімкнено). Показується на сторінці одразу, а записується в день, щойно на ньому щось з'явиться.
     */
    fun carriedPlace(epochDay: Long): String? {
        val o = open ?: return null
        if (shelf.firstOrNull { it.id == o.info.id }?.carryPlace == false) return null
        val d = day(epochDay)
        if (d.place.isNotBlank() || hasContent(d) || isDry(epochDay)) return null
        return o.days.values.filter { it.day < epochDay && it.place.isNotBlank() }.maxByOrNull { it.day }?.place
    }

    private fun hasContent(d: Day) = d.text.isNotBlank() || d.ink.isNotEmpty() || d.photo || d.audio

    fun setPhotosOn(id: String, on: Boolean) = saveShelf(shelf.map { if (it.id == id) it.copy(photos = on) else it })
    fun setVoiceOn(id: String, on: Boolean) = saveShelf(shelf.map { if (it.id == id) it.copy(voice = on) else it })

    /** Прибрати порожнє місце під фото / голос на цей день (з «Повернути»): його займе текст. */
    fun dismissSlot(epochDay: Long, photo: Boolean) {
        update(day(epochDay).let { if (photo) it.copy(noPhoto = true) else it.copy(noAudio = true) })
        show(R.string.slot_removed) { update(day(epochDay).let { if (photo) it.copy(noPhoto = false) else it.copy(noAudio = false) }) }
    }

    fun setOpenAtBookmark(id: String, on: Boolean) = saveShelf(shelf.map { if (it.id == id) it.copy(openAtBookmark = on) else it })

    /** Попередження червоним — тримається, доки його не приберуть ([clearNotice]). */
    fun warn(message: Int) {
        noticeJob?.cancel()
        notice = Notice(message, danger = true)
    }

    fun clearNotice(message: Int) {
        if (notice?.text == message) notice = null
    }

    fun show(message: Int, undo: (() -> Unit)? = null) {
        notice = Notice(message, undo)
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(if (undo != null) 4500 else 2200)
            notice = null
        }
    }

    // ——— Полиця ———

    fun createDiary(name: String): DiaryInfo {
        val d = store.newDiary(name.trim().take(Limits.DIARY_NAME).ifEmpty { getApplication<Application>().getString(R.string.default_diary_name) }, shelf.size % 6)
        saveShelf(shelf + d)
        return d
    }

    fun rename(id: String, name: String) {
        val n = name.trim().take(Limits.DIARY_NAME)
        if (n.isEmpty()) return
        saveShelf(shelf.map { if (it.id == id) it.copy(name = n) else it })
        open?.let { if (it.info.id == id) open = OpenDiary(it.info.copy(name = n), it.db).also { o -> o.days.putAll(it.days); o.bookmark = it.bookmark } }
    }

    /** Стерти щоденник назавжди (і коли забули код). */
    fun erase(id: String) {
        if (open?.info?.id == id) closeDiary()
        store.erase(id)
        val rest = shelf.filterNot { it.id == id }
        if (rest.isEmpty()) {
            saveShelf(listOf(store.newDiary(getApplication<Application>().getString(R.string.default_diary_name), 0)))
            current = shelf[0].id
        } else {
            saveShelf(rest)
            current = if (rest.size == 1) rest[0].id else null
        }
    }

    fun coverBitmap(id: String, glass: Boolean): ImageBitmap? {
        val key = "cover_${id}_$glass"
        Photos.cache.get(key)?.let { return it.asImageBitmap() }
        return store.loadCover(id, glass)?.also { Photos.cache.put(key, it) }?.asImageBitmap()
    }

    fun setCover(id: String, uri: Uri, temp: java.io.File? = null) {
        busy = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                val src = Photos.load(getApplication(), uri, Limits.PHOTO_SIDE).also { temp?.delete() } ?: return@withContext false
                store.saveCover(id, Photos.stylize(src, glass = true), Photos.stylize(src, glass = false, onPaper = true))
                true
            }
            Photos.cache.remove("cover_${id}_true")
            Photos.cache.remove("cover_${id}_false")
            if (ok) saveShelf(shelf.map { if (it.id == id) it.copy(cover = true) else it }) else show(R.string.photo_failed)
            busy = false
        }
    }

    fun removeCover(id: String) {
        store.removeCover(id)
        Photos.cache.remove("cover_${id}_true")
        Photos.cache.remove("cover_${id}_false")
        saveShelf(shelf.map { if (it.id == id) it.copy(cover = false) else it })
    }

    // ——— Відкрити / закрити ———

    private fun saltOf(info: DiaryInfo) = android.util.Base64.decode(info.salt, android.util.Base64.NO_WRAP)

    /** Відкрити щоденник: без замка — одразу ([pin] порожній), із замком — кодом з барабанів. */
    suspend fun tryOpen(id: String, pin: String): OpenResult {
        val info = info(id) ?: return OpenResult.Failed
        val now = System.currentTimeMillis()
        if (info.locked && info.retryAt > now) return OpenResult.Wait(info.retryAt)
        val result = withContext(io) {
            runCatching {
                val pass = DiaryKeys.passphrase(id, if (info.locked) pin else "", saltOf(info))
                DiaryDb.open(store.dir(id), pass)
            }
        }
        val db = result.getOrNull()
        if (db == null) {
            android.util.Log.w("Diary", "open failed", result.exceptionOrNull())
            if (!info.locked) return OpenResult.Failed
            val fails = info.fails + 1
            // Після 5 невдалих спроб — пауза, що подвоюється: 30 с, 1 хв, 2 хв… (не більше години).
            val wait = if (fails >= 5) (30_000L shl (fails - 5).coerceAtMost(7)).coerceAtMost(3_600_000L) else 0L
            val updated = info.copy(fails = fails, retryAt = now + wait)
            saveShelf(shelf.map { if (it.id == id) updated else it })
            return OpenResult.Wrong(updated.retryAt)
        }
        if (info.fails != 0) saveShelf(shelf.map { if (it.id == id) it.copy(fails = 0, retryAt = 0) else it })
        val o = OpenDiary(info(id)!!, db)
        withContext(io) {
            val hours = dryingOf(info).hours
            val all = db.days().map { d ->
                val dried = d.dried(now, hours)
                if (dried !== d) db.save(dried)
                dried
            }
            o.days.putAll(all.associateBy { it.day })
            o.bookmark = db.meta("bookmark")?.toLongOrNull()
        }
        open?.let { closeDiary() }
        open = o
        return OpenResult.Opened
    }

    /** Закрити (зберегти все й забути ключ). Із замком — так само при виході з програми. */
    fun closeDiary() {
        val o = open ?: return
        flushAll()
        open = null
        Photos.cache.evictAll()
        viewModelScope.launch(io) { runCatching { o.db.close() } }
    }

    /**
     * Ми самі відкриваємо системний вибір фото чи файлу (програма на мить іде у фон) — щоденник лишається відкритим.
     */
    var keepOpenOnce = false

    /** Програма пішла у фон: щоденник із замком закривається — повернувшись, треба знову набрати код. */
    fun onBackground() {
        val o = open ?: return
        if (keepOpenOnce) {
            keepOpenOnce = false
            flushAll()
            return
        }
        if (o.info.locked) closeDiary() else flushAll()
    }

    /** Поставити код (барабани) — база перешифровується новим ключем. */
    fun setPin(pin: String) {
        val o = open ?: return
        viewModelScope.launch {
            busy = true
            withContext(io) { o.db.rekey(DiaryKeys.passphrase(o.info.id, pin, saltOf(o.info))) }
            saveShelf(shelf.map { if (it.id == o.info.id) it.copy(locked = true, fails = 0, retryAt = 0) else it })
            open = OpenDiary(info(o.info.id)!!, o.db).also { n -> n.days.putAll(o.days); n.bookmark = o.bookmark }
            busy = false
            show(R.string.lock_set_done)
        }
    }

    fun removePin() {
        val o = open ?: return
        viewModelScope.launch {
            busy = true
            withContext(io) { o.db.rekey(DiaryKeys.passphrase(o.info.id, "", saltOf(o.info))) }
            saveShelf(shelf.map { if (it.id == o.info.id) it.copy(locked = false) else it })
            open = OpenDiary(info(o.info.id)!!, o.db).also { n -> n.days.putAll(o.days); n.bookmark = o.bookmark }
            busy = false
            show(R.string.lock_removed)
        }
    }

    // ——— Дні ———

    fun day(epochDay: Long): Day = open?.days?.get(epochDay) ?: Day(epochDay)

    /** День із урахуванням висихання «зараз» — так, як його показує сторінка. */
    fun dayNow(epochDay: Long): Day = day(epochDay).dried(System.currentTimeMillis(), dryingOf(info(open?.info?.id)).hours)

    private val pending = HashMap<Long, Job>()

    /** Зберегти день — у стані одразу, на диск трохи згодом (поки пишеш, не на кожну літеру). */
    fun update(d0: Day) {
        val o = open ?: return
        // Перше, що з'явилося на сторінці, — і місце з попереднього дня записується разом із ним.
        val before = o.days[d0.day] ?: Day(d0.day)
        val d = if (d0.place.isBlank() && before.place.isBlank() && !hasContent(before) && hasContent(d0)) {
            carriedPlace(d0.day)?.let { d0.copy(place = it) } ?: d0
        } else d0
        val stamped = d.copy(updated = System.currentTimeMillis())
        if (stamped.isEmpty) o.days.remove(d.day) else o.days[d.day] = stamped
        pending[d.day]?.cancel()
        pending[d.day] = viewModelScope.launch {
            delay(500)
            withContext(io) { o.db.save(stamped) }
            pending.remove(d.day)
        }
    }

    private fun flushAll() {
        val o = open ?: return
        pending.values.forEach { it.cancel() }
        pending.clear()
        val snapshot = o.days.values.toList()
        viewModelScope.launch(io) { snapshot.forEach { runCatching { o.db.save(it) } } }
    }

    /** Новий текст дня. Висохла частина лишається як була (це перевіряє сторінка), решта — свіже чорнило. */
    /**
     * Вирвати сторінку дня — назавжди, без «Повернути». [split] — де текст переходить на другу сторінку. Перша: зникають
     * фото, голос, місце, підпис, текст до [split] і малюнок першої сторінки; друга: текст від [split] і її малюнок.
     */
    fun tearPage(epochDay: Long, part: Int, split: Int) {
        val o = open ?: return
        val d = dayNow(epochDay)
        val text = d.text
        val cut = split.coerceIn(0, text.length)
        // Свіжа (ще не висохла) сторінка вирвалась — на її місці нова чиста: місце дня не зменшується, клаптика немає.
        // Висохла — назавжди: лишається клаптик, сторінку дня втрачено.
        val renew = !isDry(epochDay)
        val torn = if (part == 0) {
            o.db.photoFile(epochDay).delete()
            o.db.audioFile(epochDay).delete()
            Photos.cache.remove("photo_${epochDay}_true")
            Photos.cache.remove("photo_${epochDay}_false")
            val keep = text.substring(cut)
            val sealed = if (cut < d.sealed.length) d.sealed.substring(cut) else ""
            d.copy(
                place = "", caption = "", photo = false, audio = false, audioMs = 0, wave = emptyList(),
                sealed = sealed, fresh = keep.substring(sealed.length),
                strikes = d.strikes.filter { it.start >= cut }.map { Strike(it.start - cut, it.end - cut) },
                ink = d.ink.filter { it.page != 0 },
                torn = if (renew) d.torn else d.torn or 1,
                tearTry0 = if (renew) 0f else d.tearTry0,
            )
        } else {
            val keep = text.substring(0, cut)
            val sealed = d.sealed.take(cut)
            d.copy(
                sealed = sealed, fresh = keep.substring(sealed.length),
                strikes = d.strikes.filter { it.end <= cut },
                ink = d.ink.filter { it.page != 1 },
                torn = if (renew) d.torn else d.torn or 2,
                tearTry1 = if (renew) 0f else d.tearTry1,
            )
        }
        update(torn)
    }

    /** Передумали вирвати: запам'ятати, до якої висоти надірвано (лише найвищу межу). */
    fun setTearTry(epochDay: Long, part: Int, k: Float) {
        val d = day(epochDay)
        update(if (part == 0) d.copy(tearTry0 = maxOf(d.tearTry0, k)) else d.copy(tearTry1 = maxOf(d.tearTry1, k)))
    }

    fun editText(epochDay: Long, full: String) {
        val d = dayNow(epochDay)
        if (!full.startsWith(d.sealed)) return
        val fresh = full.substring(d.sealed.length)
        val since = when {
            fresh.isEmpty() -> 0L
            d.freshSince == 0L -> System.currentTimeMillis()
            else -> d.freshSince
        }
        update(d.copy(fresh = fresh, freshSince = since))
    }

    fun setPlace(epochDay: Long, place: String) = update(day(epochDay).copy(place = place.trim().take(Limits.PLACE)))

    fun setCaption(epochDay: Long, caption: String) = update(day(epochDay).copy(caption = caption.trim().take(Limits.CAPTION)))

    /** Куди на обкладинці пересунули назву (частка висоти). */
    fun setTitleY(id: String, y: Float) = saveShelf(shelf.map { if (it.id == id) it.copy(titleY = y.coerceIn(0.08f, 0.92f)) else it })

    /** Викреслити (лише висохле). Перетини зливаються в одне викреслення. */
    fun addStrike(epochDay: Long, start: Int, end: Int) {
        val d = dayNow(epochDay)
        val s = start.coerceIn(0, d.sealed.length)
        val e = end.coerceIn(0, d.sealed.length)
        if (e <= s) return
        val before = d.strikes
        val merged = mergeStrikes(before + Strike(s, e))
        update(d.copy(strikes = merged))
        show(R.string.struck) { update(day(epochDay).copy(strikes = before)) }
    }

    fun setInk(epochDay: Long, ink: List<InkLine>) = update(day(epochDay).copy(ink = ink))

    fun setBookmark(epochDay: Long?) {
        val o = open ?: return
        o.bookmark = epochDay
        viewModelScope.launch(io) { o.db.setMeta("bookmark", epochDay?.toString()) }
    }

    // ——— Фото дня ———

    /** [temp] — тимчасовий знімок камери: стерти щойно прочитали (у щоденнику фото лежить уже зашифрованим). */
    fun setPhoto(epochDay: Long, uri: Uri, temp: java.io.File? = null) {
        val o = open ?: return
        busy = true
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.Default) {
                Photos.load(getApplication(), uri, Limits.PHOTO_SIDE).also { temp?.delete() }?.let { Photos.toJpeg(it) }
            }
            if (bytes == null) {
                show(R.string.photo_failed)
            } else {
                withContext(io) { o.db.writeMedia(o.db.photoFile(epochDay), bytes) }
                Photos.cache.remove("photo_${epochDay}_true")
                Photos.cache.remove("photo_${epochDay}_false")
                update(day(epochDay).copy(photo = true, caption = ""))
            }
            busy = false
        }
    }

    fun removePhoto(epochDay: Long) {
        val o = open ?: return
        val file = o.db.photoFile(epochDay)
        val keep = java.io.File(file.path + ".undo")
        file.renameTo(keep)
        // Підпис — частина фото: зникає разом із ним (і повертається разом із ним через «Повернути»).
        val caption = day(epochDay).caption
        // Місце під фото лишається порожнім (можна вставити інше); прибирається лише окремо — свайпом по порожньому місцю
        // або в налаштуваннях щоденника.
        update(day(epochDay).copy(photo = false, caption = "", noPhoto = false))
        show(R.string.photo_removed) {
            keep.renameTo(file)
            update(day(epochDay).copy(photo = true, caption = caption, noPhoto = false))
        }
        viewModelScope.launch { delay(6000); if (!day(epochDay).photo) keep.delete() }
    }

    /** Фото дня, оброблене під тему (з пам'яті, якщо вже рахувалось). */
    /** Уже оброблене фото — одразу, без очікування (сторінка малюється готовою, без «проявлення»). */
    fun cachedPhoto(epochDay: Long, glass: Boolean): ImageBitmap? = Photos.cache.get("photo_${epochDay}_$glass")?.asImageBitmap()

    suspend fun photo(epochDay: Long, glass: Boolean): ImageBitmap? {
        val o = open ?: return null
        val key = "photo_${epochDay}_$glass"
        Photos.cache.get(key)?.let { return it.asImageBitmap() }
        val bmp: Bitmap = withContext(io) { o.db.readMedia(o.db.photoFile(epochDay)) }
            ?.let { bytes -> withContext(Dispatchers.Default) { Photos.decode(bytes)?.let { Photos.stylize(it, glass, onPaper = true) } } }
            ?: return null
        Photos.cache.put(key, bmp)
        return bmp.asImageBitmap()
    }

    // ——— Аудіо дня ———

    /**
     * Записане — на сторону [side] (0 — A, 1 — B) касети дня. Якщо там уже є запис — допис у кінець (той самий файл далі),
     * а місце, де він почався, позначається ([Day.takes]).
     */
    /**
     * Записане — на сторону [side] касети дня. [cutFrom] = null — допис у кінець; інакше — заміна шматка
     * [cutFrom, cutTo) (поточного відрізка): що до нього й після — лишається, новий запис стає на його місце.
     * [mode] — положення шумодава, на якому писали.
     */
    fun saveAudio(epochDay: Long, bytes: ByteArray, ms: Long, wave: List<Float>, side: Int = 0, cutFrom: Long? = null, cutTo: Long? = null, mode: Int = 0) {
        val o = open ?: return
        viewModelScope.launch {
            val d0 = day(epochDay)
            val had = if (side == 0) d0.audio else d0.audioB
            val fullMs = if (side == 0) d0.audioMs else d0.audioMsB
            val replace = had && cutFrom != null && cutFrom < fullMs - 100
            val from = if (replace) cutFrom!!.coerceAtLeast(0) else fullMs
            val to = if (replace) (cutTo ?: fullMs).coerceIn(from, fullMs) else fullMs
            val file = if (side == 0) o.db.audioFile(epochDay) else o.db.audioFileB(epochDay)
            val cache = getApplication<Application>().cacheDir
            val result = withContext(io) {
                val old = if (had) o.db.readMedia(file) else null
                val prefix = when {
                    old == null -> null
                    !replace -> old
                    from > 300 -> Tape.trim(old, from, cache)
                    else -> null
                }
                val suffix = if (old != null && replace && to < fullMs - 100) Tape.slice(old, to, cache) else null
                var all: ByteArray? = bytes
                if (prefix != null) all = Tape.concat(prefix, bytes, cache)
                if (all != null && suffix != null) all = Tape.concat(all, suffix, cache)
                all?.also { o.db.writeMedia(file, it) }
                Triple(all, prefix != null, suffix != null)
            }
            val joined = result.first ?: run { show(R.string.record_failed); return@launch }
            val hasPrefix = result.second
            val hasSuffix = result.third
            val d = day(epochDay)
            // Тривалість — з самих файлів: що насправді записано й скільки тепер на стороні.
            val newMs = withContext(io) { Tape.durationMs(bytes) } ?: ms
            val total = withContext(io) { Tape.durationMs(joined) } ?: ((if (hasPrefix) from else 0L) + newMs + (if (hasSuffix) fullMs - to else 0L))
            val start = if (hasPrefix) from else 0L
            val oldTakes = if (side == 0) d.takes else d.takesB
            val oldWave = if (side == 0) d.wave else d.waveB
            val shift = start + newMs - to
            val takes = buildList {
                if (hasPrefix) { addAll(oldTakes.filter { it < from }); add(start) }
                if (hasSuffix) { add(start + newMs); addAll(oldTakes.filter { it > to }.map { it + shift }) }
            }.filter { it in 1 until total }.distinct().sorted()
            fun part(a: Long, b: Long): List<Float> {
                if (oldWave.isEmpty() || fullMs <= 0) return emptyList()
                val i0 = (oldWave.size * a / fullMs).toInt().coerceIn(0, oldWave.size)
                val i1 = (oldWave.size * b / fullMs).toInt().coerceIn(i0, oldWave.size)
                return oldWave.subList(i0, i1)
            }
            val newWave = stitchWave(listOfNotNull(
                if (hasPrefix) part(0, from) to start else null,
                wave to newMs,
                if (hasSuffix) part(to, fullMs) to (fullMs - to) else null,
            ))
            val oldMode = if (side == 0) d.modeA else d.modeB
            val newMode = if (!hasPrefix && !hasSuffix) mode else maxOf(oldMode, mode)
            update(
                if (side == 0) d.copy(audio = true, audioMs = total, wave = newWave, takes = takes, modeA = newMode, noAudio = false)
                else d.copy(audioB = true, audioMsB = total, waveB = newWave, takesB = takes, modeB = newMode, noAudio = false),
            )
        }
    }

    /** Прибрати касету дня (обидві сторони); кілька секунд можна повернути. */
    fun removeAudio(epochDay: Long) {
        val o = open ?: return
        val files = listOf(o.db.audioFile(epochDay), o.db.audioFileB(epochDay))
        val keeps = files.map { java.io.File(it.path + ".undo") }
        files.zip(keeps).forEach { (f, k) -> if (f.exists()) f.renameTo(k) }
        val before = day(epochDay)
        // Місце під голос лишається порожнім; прибирається окремо — свайпом по порожньому місцю або в налаштуваннях.
        update(before.copy(audio = false, audioB = false, takes = emptyList(), takesB = emptyList(), noAudio = false))
        show(R.string.audio_removed) {
            files.zip(keeps).forEach { (f, k) -> if (k.exists()) k.renameTo(f) }
            update(day(epochDay).copy(
                audio = before.audio, audioMs = before.audioMs, wave = before.wave, takes = before.takes,
                audioB = before.audioB, audioMsB = before.audioMsB, waveB = before.waveB, takesB = before.takesB, noAudio = false,
            ))
        }
        viewModelScope.launch { delay(6000); if (!day(epochDay).let { it.audio || it.audioB }) keeps.forEach { it.delete() } }
    }

    suspend fun audio(epochDay: Long, side: Int = 0): ByteArray? {
        val o = open ?: return null
        return withContext(io) { o.db.readMedia(if (side == 0) o.db.audioFile(epochDay) else o.db.audioFileB(epochDay)) }
    }

    // ——— Пошук ———

    fun search(query: String, photo: Boolean, audio: Boolean): List<SearchHit> {
        val o = open ?: return emptyList()
        // Лише ті дні, що є сторінками щоденника (порожні дні в книжці не показуються — і в пошуку теж).
        return Searcher.search(o.days.values.filter { !it.isBlank }, query, photo, audio, getApplication()).take(Limits.SEARCH_RESULTS)
    }

    /** Пошук у режимі (нотатки / фото / голос) з періодами й «найкраще» — див. [Searcher.find]. */
    fun find(query: String, mode: com.blacklaunch.diary.search.SearchMode): com.blacklaunch.diary.search.Found {
        val o = open ?: return com.blacklaunch.diary.search.Found(emptyList(), null, false)
        val f = Searcher.find(o.days.values.filter { !it.isBlank }, query, mode, java.time.LocalDate.now().toEpochDay())
        return f.copy(hits = f.hits.take(Limits.SEARCH_RESULTS))
    }

    // ——— Резервна копія ———

    fun backup(uri: Uri, password: String) {
        val o = open ?: return
        busy = true
        viewModelScope.launch {
            val ok = withContext(io) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        Backup.write(
                            out, password, o.info, o.days.values.sortedBy { it.day },
                            photo = { o.db.readMedia(o.db.photoFile(it)) },
                            audio = { o.db.readMedia(o.db.audioFile(it)) },
                            audioB = { o.db.readMedia(o.db.audioFileB(it)) },
                            coverGlass = store.coverFile(o.info.id, true).takeIf { it.exists() }?.readBytes(),
                            coverSheet = store.coverFile(o.info.id, false).takeIf { it.exists() }?.readBytes(),
                        )
                    } ?: error("no stream")
                }.isSuccess
            }
            busy = false
            show(if (ok) R.string.backup_done else R.string.backup_failed)
        }
    }

    /** Відновити з копії — новим щоденником на полиці (без замка; поставити можна згодом). */
    fun restore(uri: Uri, password: String, onWrong: () -> Unit) {
        busy = true
        viewModelScope.launch {
            val result = withContext(io) {
                runCatching {
                    val contents = getApplication<Application>().contentResolver.openInputStream(uri)?.use { Backup.read(it, password) } ?: error("no stream")
                    val info = store.newDiary(contents.name.ifBlank { getApplication<Application>().getString(R.string.default_diary_name) }, contents.tint)
                        .copy(created = contents.created.takeIf { it > 0 } ?: System.currentTimeMillis())
                    val db = DiaryDb.open(store.dir(info.id), DiaryKeys.passphrase(info.id, "", saltOf(info)))
                    db.use {
                        contents.days.forEach { d -> db.save(d) }
                        contents.photos.forEach { (day, bytes) -> db.writeMedia(db.photoFile(day), bytes) }
                        contents.audios.forEach { (day, bytes) -> db.writeMedia(db.audioFile(day), bytes) }
                        contents.audiosB.forEach { (day, bytes) -> db.writeMedia(db.audioFileB(day), bytes) }
                    }
                    var cover = false
                    if (contents.coverGlass != null && contents.coverSheet != null) {
                        java.io.File(getApplication<Application>().filesDir, "covers").mkdirs()
                        store.coverFile(info.id, true).writeBytes(contents.coverGlass)
                        store.coverFile(info.id, false).writeBytes(contents.coverSheet)
                        cover = true
                    }
                    info.copy(cover = cover)
                }
            }
            busy = false
            result.onSuccess { info ->
                saveShelf(shelf + info)
                current = info.id
                show(R.string.restore_done)
            }.onFailure {
                if (it is Backup.WrongPassword) onWrong() else show(R.string.restore_failed)
            }
        }
    }

    override fun onCleared() {
        closeDiary()
        super.onCleared()
    }

    companion object {
        fun today(): Long = LocalDate.now().toEpochDay()

        /** День висох: через день після нього вже нічого не змінити (ні текст, ні фото, ні голос) — лише олівець. */
        fun isDry(epochDay: Long): Boolean = epochDay < today() - 1

        fun mergeStrikes(list: List<Strike>): List<Strike> {
            val sorted = list.sortedBy { it.start }
            val out = ArrayList<Strike>()
            for (s in sorted) {
                val last = out.lastOrNull()
                if (last != null && s.start <= last.end) out[out.size - 1] = Strike(last.start, maxOf(last.end, s.end)) else out += s
            }
            return out
        }

        /** Обриси звуку — не більше 120 стовпчиків (стільки вміщається в смужку). */
        /** Обриси кількох шматків разом — кожен займає місце за своєю тривалістю. */
        fun stitchWave(parts: List<Pair<List<Float>, Long>>, n: Int = 120): List<Float> {
            val total = parts.sumOf { it.second }.coerceAtLeast(1)
            val out = ArrayList<Float>(n)
            parts.forEachIndexed { k, (w, ms) ->
                val cnt = if (k == parts.size - 1) n - out.size else (n * ms / total).toInt().coerceIn(1, n - out.size)
                if (cnt > 0) out += resample(w, cnt)
            }
            return out
        }

        /** Обриси старого запису й допису разом — кожен займає місце за своєю тривалістю. */
        fun mergeWave(a: List<Float>, aMs: Long, b: List<Float>, bMs: Long, n: Int = 120): List<Float> {
            val total = (aMs + bMs).coerceAtLeast(1)
            val na = (n * aMs / total).toInt().coerceIn(1, n - 1)
            return resample(a, na) + resample(b, n - na)
        }

        private fun resample(w: List<Float>, n: Int): List<Float> {
            if (w.isEmpty()) return List(n) { 0f }
            return List(n) { i ->
                val a = (i * w.size / n).coerceAtMost(w.size - 1)
                val b = ((i + 1) * w.size / n).coerceAtLeast(a + 1).coerceAtMost(w.size)
                w.subList(a, b).maxOrNull() ?: 0f
            }
        }

        fun compactWave(w: List<Float>, n: Int = 120): List<Float> {
            if (w.size <= n) return w
            return List(n) { i ->
                val a = i * w.size / n
                val b = ((i + 1) * w.size / n).coerceAtLeast(a + 1)
                w.subList(a, b).maxOrNull() ?: 0f
            }
        }
    }
}
