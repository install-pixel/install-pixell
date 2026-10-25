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

    override suspend fun search(query: String, kinds: Set<GroupKind>, appPackages: List<String>): List<ResultGroup> =
        withContext(Dispatchers.IO) {
        val prepared = PreparedQuery(query)
        if (prepared.isEmpty) return@withContext emptyList()
        buildList {
            if (GroupKind.SETTINGS in kinds) {
                group(GroupKind.SETTINGS) { SettingsCatalog.search(prepared).map { it.copy(title = settingTitle(it.entryId)) } }
            }
            if (GroupKind.SHORTCUTS in kinds) group(GroupKind.SHORTCUTS) { shortcuts(prepared, appPackages) }
            if (GroupKind.CONTACTS in kinds && granted(Manifest.permission.READ_CONTACTS)) {
                group(GroupKind.CONTACTS) { contacts(prepared) }
            }
            if (GroupKind.CALLS in kinds && granted(Manifest.permission.READ_CALL_LOG)) {
                group(GroupKind.CALLS) { calls(prepared) }
            }
            if (GroupKind.CALENDAR in kinds && granted(Manifest.permission.READ_CALENDAR)) {
                group(GroupKind.CALENDAR) { events(query.trim()) }
            }
            if (GroupKind.SMS in kinds && granted(Manifest.permission.READ_SMS)) {
                group(GroupKind.SMS) { sms(query.trim()) }
            }
            if (GroupKind.NOTIFICATIONS in kinds) group(GroupKind.NOTIFICATIONS) { notifications(query.trim()) }
            for (kind in MEDIA_KINDS) {
                if (kind in kinds && hasMediaAccess(kind)) group(kind) { media(kind, query.trim()) }
            }
        }
    }

    /** Помилка одного джерела не ламає інші. */
    private inline fun MutableList<ResultGroup>.group(kind: GroupKind, block: () -> List<GlobalResult>) {
        val items = try {
            block()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Global search source $kind failed", e)
            emptyList()
        }
        if (items.isNotEmpty()) add(ResultGroup(kind, items))
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
            shortcutsCache = userManager.userProfiles.flatMap { user ->
                launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setQueryFlags(flags), user).orEmpty()
            }.filter { it.isEnabled }
            shortcutsLoadedAt = now
        }
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
    private fun calls(prepared: PreparedQuery): List<GlobalResult> {
        val query = prepared.text
        if (query.length < MIN_TEXT) return emptyList()
        val byNumber = query.any { it.isDigit() }
        val like = "%${escapeLike(query.trim())}%"
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE, CallLog.Calls.TYPE)
        val selection = if (byNumber) "${CallLog.Calls.NUMBER} LIKE ? ESCAPE '\\'" else "${CallLog.Calls.CACHED_NAME} LIKE ? ESCAPE '\\'"
        return appContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, selection, arrayOf(like), "${CallLog.Calls.DATE} DESC",
        )?.use { c ->
            val seen = HashSet<String>()
            buildList {
                while (c.moveToNext() && size < LIMIT) {
                    val number = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                    val name = c.getString(1)?.takeIf { it.isNotBlank() }
                    if (!byNumber && (name == null || !NameStarts.matches(name, prepared))) continue
                    if (!seen.add(number)) continue
                    val type = when (c.getInt(3)) {
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> R.string.call_missed
                        CallLog.Calls.OUTGOING_TYPE -> R.string.call_outgoing
                        else -> R.string.call_incoming
                    }
                    val whenText = DateUtils.formatDateTime(appContext, c.getLong(2), DATE_FLAGS)
                    add(GlobalResult.Call(number, name, appContext.getString(type) + " · " + whenText))
                }
            }
        }.orEmpty()
    }

    private fun notifications(query: String): List<GlobalResult> {
        if (query.length < MIN_TEXT) return emptyList()
        val current = ProfileNotificationFilter.current()
        return NotificationTexts.search(query, current, appContext.packageName, LIMIT).map { entry ->
            val app = appLabel(entry.packageName) ?: entry.packageName
            val title = entry.title.ifBlank { app }
            GlobalResult.NotificationText(entry.id, entry.packageName, title, app + " · " + TextMatch.snippet(entry.text.ifBlank { entry.title }, query))
        }
    }

    private fun hasMediaAccess(kind: GroupKind): Boolean = mediaPermissions(kind).any(::granted)

    // Пісні — за назвою, виконавцем, альбомом; відео й фото — за назвою файлу або альбомом («Camera», «Screenshots»).
    private fun media(kind: GroupKind, query: String): List<GlobalResult> {
        if (query.length < MIN_TEXT) return emptyList()
        val like = "%${escapeLike(query)}%"
        val (uri, columns, sort) = when (kind) {
            GroupKind.MUSIC -> Triple(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                listOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM),
                MediaStore.Audio.Media.TITLE,
            )
            GroupKind.VIDEO -> Triple(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                listOf(MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.TITLE),
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )
            else -> Triple(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                listOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.TITLE),
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )
        }
        val selection = columns.joinToString(" OR ", prefix = "(", postfix = ")") { "$it LIKE ? ESCAPE '\\'" } +
            if (kind == GroupKind.MUSIC) " AND ${MediaStore.Audio.Media.IS_MUSIC} != 0" else ""
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.MIME_TYPE) + columns.take(2)
        return appContext.contentResolver.query(uri, projection, selection, Array(columns.size) { like }, sort)?.use { c ->
            buildList {
                while (c.moveToNext() && size < LIMIT) {
                    val title = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val detail = c.getString(3)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                    add(GlobalResult.Media(ContentUris.withAppendedId(uri, c.getLong(0)).toString(), c.getString(1) ?: "*/*", title, detail))
                }
            }
        }.orEmpty()
    }

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
        private const val MIN_TEXT = 2
        private const val SHORTCUTS_TTL_MS = 30_000L
        private const val PAST_MS = 30L * 24 * 60 * 60 * 1000
        private const val FUTURE_MS = 365L * 24 * 60 * 60 * 1000
        private const val SNIPPET_BEFORE = 20
        private const val SNIPPET_AFTER = 50
        private const val DATE_FLAGS = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    }
}
