package com.blacklaunch.launcher.global

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.MediaStore
import android.provider.ContactsContract
import android.provider.Telephony
import android.text.format.DateUtils
import android.util.Log
import androidx.core.content.ContextCompat
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.profiles.ProfileNotificationFilter
import com.blacklaunch.launcher.search.IndexedName
import com.blacklaunch.launcher.search.NameStarts
import com.blacklaunch.launcher.search.NameMatcher
import com.blacklaunch.launcher.search.PreparedQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Глобальний пошук через офіційні API. Читає лише в момент пошуку, без фонового
 * індексування; контакти, календар і SMS — тільки з дозволом, який дає користувач.
 */
class AndroidGlobalSearch(
    context: Context,
    /** Назва програми за пакетом — щоб підписати ярлик. */
    private val appLabel: (String) -> String?,
    /** Назва розділу налаштувань мовою телефону. */
    private val settingTitle: (String) -> String,
) : GlobalSearchSource {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    private var shortcutsCache: List<ShortcutInfo> = emptyList()
    private var shortcutsLoadedAt = 0L

    override suspend fun search(query: String, kinds: Set<GroupKind>, appPackages: List<String>, expanded: GroupKind?): List<ResultGroup> =
        withContext(Dispatchers.IO) {
        val prepared = PreparedQuery(query)
        if (prepared.isEmpty) return@withContext emptyList()
        // «фото вчора», «дзвінки 12 жовтня»: джерела з датою шукають за нею (й рештою слів), назване джерело — лише воно.
        val date = DateQuery.parse(query, LocalDate.now())
        fun dated(kind: GroupKind) = date != null && kind in DateQuery.DATED && (date.sources.isEmpty() || kind in date.sources)
        fun plain(kind: GroupKind) = date == null || date.sources.isEmpty() && kind !in DateQuery.DATED
        fun wanted(kind: GroupKind) = kind in kinds && (dated(kind) || plain(kind))
        // Розгорнута група — до EXPANDED_LIMIT; на один більше, щоб знати, чи є ще.
        fun limit(kind: GroupKind) = (if (kind == expanded) EXPANDED_LIMIT else LIMIT) + 1
        buildList {
            if (GroupKind.SETTINGS in kinds) {
                group(GroupKind.SETTINGS) {
                    SettingsCatalog.search(prepared)
                        // Ігри вимкнено — у пошуку їх теж немає.
                        .filter { it.action != SettingsCatalog.GAMES_ACTION || (appContext as? com.blacklaunch.launcher.BlackLaunchApp)?.games?.enabled?.value != false }
                        .map { it.copy(title = settingTitle(it.entryId)) }
                }
            }
            if (GroupKind.SHORTCUTS in kinds) group(GroupKind.SHORTCUTS) { shortcuts(prepared, appPackages) }
            if (GroupKind.CONTACTS in kinds && granted(Manifest.permission.READ_CONTACTS)) {
                group(GroupKind.CONTACTS) { contacts(prepared) }
            }
            if (wanted(GroupKind.CALLS) && granted(Manifest.permission.READ_CALL_LOG)) {
                group(GroupKind.CALLS, expanded) { calls(prepared, date.takeIf { dated(GroupKind.CALLS) }, limit(GroupKind.CALLS)) }
            }
            if (GroupKind.CALENDAR in kinds && granted(Manifest.permission.READ_CALENDAR)) {
                group(GroupKind.CALENDAR) { events(query.trim()) }
            }
            if (GroupKind.SMS in kinds && granted(Manifest.permission.READ_SMS)) {
                group(GroupKind.SMS) { sms(query.trim()) }
            }
            if (wanted(GroupKind.NOTIFICATIONS)) {
                group(GroupKind.NOTIFICATIONS, expanded) {
                    notifications(query.trim(), date.takeIf { dated(GroupKind.NOTIFICATIONS) }, limit(GroupKind.NOTIFICATIONS))
                }
            }
            for (kind in MEDIA_KINDS) {
                if (wanted(kind) && hasMediaAccess(kind)) group(kind, expanded) { media(kind, query.trim(), date.takeIf { dated(kind) }, limit(kind)) }
            }
        }
    }

    /**
     * Помилка одного джерела не ламає інші. Джерело з [EXPANDABLE_KINDS] повертає на один більше,
     * ніж показуємо: так видно, чи потрібне «Показати всі».
     */
    private inline fun MutableList<ResultGroup>.group(kind: GroupKind, expanded: GroupKind? = null, block: () -> List<GlobalResult>) {
        val items = try {
            block()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Global search source $kind failed", e)
            emptyList()
        }
        if (items.isEmpty()) return
        if (kind !in EXPANDABLE_KINDS) {
            add(ResultGroup(kind, items))
            return
        }
        val shown = if (kind == expanded) EXPANDED_LIMIT else LIMIT
        add(ResultGroup(kind, items.take(shown), more = items.size > shown && kind != expanded, expanded = kind == expanded))
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    // Ярлики програм (чати, швидкі дії) доступні лише лаунчеру за замовчуванням.
    private fun shortcuts(query: PreparedQuery, appPackages: List<String>): List<GlobalResult> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val now = SystemClock.elapsedRealtime()
        if (now - shortcutsLoadedAt > SHORTCUTS_TTL_MS) {
            var flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            // Android 11+: «розмовні» ярлики — останні чати месенджерів (Telegram, WhatsApp…).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) flags = flags or LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
            // Заблокований профіль (приватний простір, призупинена робоча) — без його ярликів.
            shortcutsCache = userManager.userProfiles.filterNot { userManager.isQuietModeEnabled(it) }.flatMap { user ->
                runCatching { launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setQueryFlags(flags), user).orEmpty() }
                    .getOrDefault(emptyList())
            }.filter { it.isEnabled }
            shortcutsLoadedAt = now
        }
        // «Відкрий підписки в YouTube»: ярлики саме цієї програми — що підходить, а якщо нічого, то всі (що вона вміє).
        phraseShortcuts(query.text)?.let { return it }
        return shortcutsCache
            .map { info ->
                val names = listOfNotNull(info.shortLabel, info.longLabel).map { IndexedName(it.toString()) }
                // Ярлики знайденої програми («Telegram» → її чати й «Новий чат») або збіг за назвою ярлика.
                val ofFoundApp = if (info.`package` in appPackages) NameMatcher.ALL_WORDS else 0
                info to maxOf(NameMatcher.score(names, query), ofFoundApp)
            }
            .filter { it.second >= NameMatcher.ALL_WORDS }
            .sortedByDescending { it.second }
            .take(LIMIT)
            .map { (info, _) ->
                GlobalResult.Shortcut(
                    packageName = info.`package`,
                    shortcutId = info.id,
                    userSerial = userManager.getSerialNumberForUser(info.userHandle),
                    title = info.shortLabel?.toString().orEmpty(),
                    appLabel = appLabel(info.`package`) ?: info.`package`,
                )
            }
    }

    private fun contacts(prepared: PreparedQuery): List<GlobalResult> {
        val query = prepared.text
        if (query.length < MIN_TEXT) return emptyList()
        // Фільтр Android шукає й по пошті, організації, прізвиську — тоді «по» знаходило «…Миколайович».
        // Лишаємо лише тих, у кого збігається ім'я (або номер, якщо шукають цифрами).
        val byNumber = query.any { it.isDigit() }
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        )
        return appContext.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext() && size < LIMIT) {
                    val name = c.getString(2).orEmpty()
                    if (!byNumber && !NameStarts.matches(name, prepared)) continue
                    val lookup = ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))
                    add(GlobalResult.Contact(lookup.toString(), name, null))
                }
            }
        }.orEmpty()
    }

    private fun events(query: String): List<GlobalResult> {
        if (query.length < MIN_TEXT) return emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_SEARCH_URI.buildUpon()
            .appendPath((now - PAST_MS).toString())
            .appendPath((now + FUTURE_MS).toString())
            .appendPath(query)
            .build()
        val projection = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN)
        val instances = appContext.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2))) }
        }.orEmpty()
        // Найближчі майбутні, потім недавні минулі.
        val (future, past) = instances.partition { it.third >= now }
        return (future + past.reversed()).distinctBy { it.first }.take(LIMIT).map { (id, title, begin) ->
            GlobalResult.Event(id, title, DateUtils.formatDateTime(appContext, begin, DATE_FLAGS))
        }
    }

    // Лише звичайні SMS: чати RCS (Google Messages) стороннім програмам недоступні.
    private fun sms(query: String): List<GlobalResult> {
        if (query.length < MIN_TEXT) return emptyList()
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY)
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return appContext.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.BODY} LIKE ? ESCAPE '\\'",
            arrayOf("%$escaped%"),
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            buildList {
                while (c.moveToNext() && size < LIMIT) {
                    val address = c.getString(0) ?: continue
                    val body = c.getString(1).orEmpty()
                    add(GlobalResult.Sms(address, contactName(address) ?: address, snippet(body, query)))
                }
            }
        }.orEmpty()
    }

    // Журнал дзвінків: за іменем (початки слів, як у контактах) або за цифрами номера.
    private fun calls(prepared: PreparedQuery, date: DateQuery?, limit: Int): List<GlobalResult> {
        val name = if (date != null) PreparedQuery(date.text) else prepared
        val query = name.text.trim()
        if (date == null && query.length < MIN_TEXT) return emptyList()
        val byNumber = query.any { it.isDigit() }
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (query.isNotEmpty()) {
            clauses += if (byNumber) "${CallLog.Calls.NUMBER} LIKE ? ESCAPE '\\'" else "${CallLog.Calls.CACHED_NAME} LIKE ? ESCAPE '\\'"
            args += "%${escapeLike(query)}%"
        }
        if (date != null) {
            clauses += "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} < ?"
            args += millis(date.start).toString()
            args += millis(date.endExclusive).toString()
        }
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE, CallLog.Calls.TYPE)
        return appContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, clauses.joinToString(" AND ") { "($it)" }, args.toTypedArray(), "${CallLog.Calls.DATE} DESC",
        )?.use { c ->
            val seen = HashSet<String>()
            buildList {
                while (c.moveToNext() && size < limit) {
                    val number = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                    val cached = c.getString(1)?.takeIf { it.isNotBlank() }
                    if (query.isNotEmpty() && !byNumber && (cached == null || !NameStarts.matches(cached, name))) continue
                    if (!seen.add(number)) continue
                    val type = when (c.getInt(3)) {
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> R.string.call_missed
                        CallLog.Calls.OUTGOING_TYPE -> R.string.call_outgoing
                        else -> R.string.call_incoming
                    }
                    val whenText = DateUtils.formatDateTime(appContext, c.getLong(2), DATE_FLAGS)
                    add(GlobalResult.Call(number, cached, appContext.getString(type) + " · " + whenText))
                }
            }
        }.orEmpty()
    }

    private fun notifications(query: String, date: DateQuery?, limit: Int): List<GlobalResult> {
        val text = date?.text ?: query
        if (date == null && text.length < MIN_TEXT) return emptyList()
        val window = date?.let { millis(it.start) until millis(it.endExclusive) }
        val current = ProfileNotificationFilter.current()
        return NotificationTexts.search(text, current, appContext.packageName, limit, window).map { entry ->
            val app = appLabel(entry.packageName) ?: entry.packageName
            val title = entry.title.ifBlank { app }
            val body = entry.text.ifBlank { entry.title }
            val detail = if (date != null) DateUtils.formatDateTime(appContext, entry.postedAt, DATE_FLAGS) else TextMatch.snippet(body, text)
            GlobalResult.NotificationText(entry.id, entry.packageName, title, "$app · $detail")
        }
    }

    private fun hasMediaAccess(kind: GroupKind): Boolean = mediaPermissions(kind).any(::granted)

    // Пісні — за назвою, виконавцем, альбомом; відео й фото — за назвою файлу або альбомом («Camera», «Screenshots»).
    private fun media(kind: GroupKind, query: String, date: DateQuery?, limit: Int): List<GlobalResult> {
        val text = date?.text ?: query
        if (date == null && text.length < MIN_TEXT) return emptyList()
        val like = "%${escapeLike(text)}%"
        val (uri, columns, sort) = when (kind) {
            GroupKind.MUSIC -> Triple(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                listOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM),
                MediaStore.Audio.Media.TITLE,
            )
            GroupKind.VIDEO -> Triple(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                listOf(MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.TITLE),
                "${MediaStore.Video.VideoColumns.DATE_TAKEN} DESC, ${MediaStore.Video.Media.DATE_ADDED} DESC",
            )
            else -> Triple(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                listOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.TITLE),
                "${MediaStore.Images.ImageColumns.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC",
            )
        }
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (text.isNotEmpty()) {
            clauses += columns.joinToString(" OR ") { "$it LIKE ? ESCAPE '\\'" }
            repeat(columns.size) { args += like }
        }
        if (kind == GroupKind.MUSIC) clauses += "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        // Дата зйомки (мс); якщо її немає — коли файл з'явився на телефоні (с).
        val taken = if (kind == GroupKind.VIDEO) MediaStore.Video.VideoColumns.DATE_TAKEN else MediaStore.Images.ImageColumns.DATE_TAKEN
        val added = MediaStore.MediaColumns.DATE_ADDED
        if (date != null) {
            clauses += "($taken >= ? AND $taken < ?) OR ($taken IS NULL AND $added >= ? AND $added < ?)"
            val from = millis(date.start)
            val to = millis(date.endExclusive)
            args += listOf(from.toString(), to.toString(), (from / 1000).toString(), (to / 1000).toString())
        }
        val dated = kind != GroupKind.MUSIC
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.MIME_TYPE) + columns.take(2) +
            (if (dated) arrayOf(taken, added) else emptyArray())
        val selection = clauses.joinToString(" AND ") { "($it)" }
        return appContext.contentResolver.query(uri, projection, selection, args.toTypedArray(), sort)?.use { c ->
            buildList {
                while (c.moveToNext() && size < limit) {
                    val title = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val album = c.getString(3)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                    // Фото й відео — з датою, щоб було видно, з якого дня.
                    val time = if (!dated) 0L else if (!c.isNull(4)) c.getLong(4) else c.getLong(5) * 1000
                    val detail = if (time > 0) listOfNotNull(album, DateUtils.formatDateTime(appContext, time, DATE_FLAGS)).joinToString(" · ") else album
                    add(GlobalResult.Media(ContentUris.withAppendedId(uri, c.getLong(0)).toString(), c.getString(1) ?: "*/*", title, detail))
                }
            }
        }.orEmpty()
    }

    private fun millis(day: LocalDate) = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun escapeLike(text: String) = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun contactName(number: String): String? {
        if (!granted(Manifest.permission.READ_CONTACTS)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return try {
            appContext.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun snippet(body: String, query: String): String {
        val flat = body.replace('\n', ' ')
        val at = flat.indexOf(query, ignoreCase = true).coerceAtLeast(0)
        val start = (at - SNIPPET_BEFORE).coerceAtLeast(0)
        val end = (at + query.length + SNIPPET_AFTER).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    }

    /**
     * Фраза «відкрий <що> в <програма>»: програма — за назвою серед тих, що мають ярлики. Ярлики, що підходять під «що»,
     * першими; [onlyMatching] = false — якщо таких немає, усі ярлики програми. null — це не така фраза чи програми немає.
     */
    fun phraseShortcuts(text: String, onlyMatching: Boolean = false): List<GlobalResult.Shortcut>? {
        if (!launcherApps.hasShortcutHostPermission()) return null
        val splits = com.blacklaunch.launcher.voice.AppActionPhrase.parse(text)
        if (splits.isEmpty()) return null
        if (shortcutsCache.isEmpty() || SystemClock.elapsedRealtime() - shortcutsLoadedAt > SHORTCUTS_TTL_MS) shortcuts(PreparedQuery(""), emptyList())
        val byPackage = shortcutsCache.groupBy { it.`package` }
        for ((what, appName) in splits) {
            val wanted = com.blacklaunch.launcher.search.SearchNormalizer.normalize(appName)
            val pkg = byPackage.keys.firstOrNull { pkg ->
                val label = com.blacklaunch.launcher.search.SearchNormalizer.normalize(appLabel(pkg).orEmpty())
                label.isNotEmpty() && (label == wanted || label.startsWith(wanted) || wanted.startsWith(label))
            } ?: continue
            val all = byPackage.getValue(pkg).sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            val matching = all.filter { info ->
                listOfNotNull(info.shortLabel, info.longLabel).any { com.blacklaunch.launcher.voice.ShortcutMatch.matches(what, it.toString()) }
            }
            val chosen = if (matching.isNotEmpty() || onlyMatching) matching else all
            return chosen.map { info ->
                GlobalResult.Shortcut(
                    packageName = info.`package`,
                    shortcutId = info.id,
                    userSerial = userManager.getSerialNumberForUser(info.userHandle),
                    title = info.shortLabel?.toString().orEmpty(),
                    appLabel = appLabel(info.`package`) ?: info.`package`,
                )
            }
        }
        return null
    }

    /** Назва програми з фрази «відкрий <що> в <програма>», якщо така програма має ярлики, — для відповіді «не вміє». */
    fun phraseAppLabel(text: String): String? {
        val splits = com.blacklaunch.launcher.voice.AppActionPhrase.parse(text)
        val packages = shortcutsCache.map { it.`package` }.distinct()
        for ((_, appName) in splits) {
            val wanted = com.blacklaunch.launcher.search.SearchNormalizer.normalize(appName)
            packages.firstOrNull { pkg ->
                val label = com.blacklaunch.launcher.search.SearchNormalizer.normalize(appLabel(pkg).orEmpty())
                label.isNotEmpty() && (label == wanted || label.startsWith(wanted) || wanted.startsWith(label))
            }?.let { return appLabel(it) }
        }
        return null
    }

    /** Ярлики однієї програми — для її меню за довгим натисканням (до 4, як у Pixel). */
    fun shortcutsFor(packageName: String, userSerial: Long): List<GlobalResult.Shortcut> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val user = userManager.getUserForSerialNumber(userSerial) ?: Process.myUserHandle()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        return runCatching { launcherApps.getShortcuts(query, user).orEmpty() }.getOrDefault(emptyList())
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(MENU_SHORTCUTS)
            .map { info ->
                GlobalResult.Shortcut(
                    packageName = info.`package`,
                    shortcutId = info.id,
                    userSerial = userSerial,
                    title = info.shortLabel?.toString().orEmpty(),
                    appLabel = appLabel(info.`package`) ?: info.`package`,
                )
            }
    }

    /** Ярлик запускається через LauncherApps — це дозволено лише лаунчеру за замовчуванням. */
    fun startShortcut(result: GlobalResult.Shortcut): Boolean {
        val user = userManager.getUserForSerialNumber(result.userSerial) ?: Process.myUserHandle()
        return try {
            launcherApps.startShortcut(result.packageName, result.shortcutId, null, null, user)
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot start shortcut ${result.key}", e)
            false
        }
    }

    fun eventUri(result: GlobalResult.Event): Uri =
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, result.eventId)

    companion object {
        val MEDIA_KINDS = listOf(GroupKind.MUSIC, GroupKind.VIDEO, GroupKind.PHOTOS)

        /** Дозволи сховища для пісень, відео й фото; Android 14+ — і «вибрані фото й відео». */
        fun mediaPermissions(kind: GroupKind): List<String> = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            kind == GroupKind.MUSIC -> listOf(Manifest.permission.READ_MEDIA_AUDIO)
            else -> listOfNotNull(
                if (kind == GroupKind.VIDEO) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED else null,
            )
        }

        private const val MENU_SHORTCUTS = 4
        private const val TAG = "AndroidGlobalSearch"
        private const val LIMIT = 5
        private const val EXPANDED_LIMIT = 300
        private const val MIN_TEXT = 2
        private const val SHORTCUTS_TTL_MS = 30_000L
        private const val PAST_MS = 30L * 24 * 60 * 60 * 1000
        private const val FUTURE_MS = 365L * 24 * 60 * 60 * 1000
        private const val SNIPPET_BEFORE = 20
        private const val SNIPPET_AFTER = 50
        private const val DATE_FLAGS = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    }
}
