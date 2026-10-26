package com.blacklaunch.launcher.voice

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.provider.ContactsContract
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.KeyEvent
import com.blacklaunch.launcher.R
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Знайдений контакт для дзвінка чи SMS. */
data class ContactNumber(val name: String, val number: String)

/**
 * Виконує голосові дії через офіційні API Android. Нічого не працює у фоні:
 * лише стан ліхтарика відстежується, поки BlackLaunch на екрані.
 */
class VoiceActionRunner(
    private val context: Context,
    private val onMessage: (String) -> Unit,
    /** Чи відповідати вголос (час). */
    private val speakAnswers: () -> Boolean = { false },
    /** Вибраний рушій синтезу мовлення; null — системний. */
    private val ttsEngine: () -> String? = { null },
) {
    private var tts: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())

    private val camera = context.getSystemService(CameraManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var torchOn = false

    private val torchId: String? by lazy {
        runCatching {
            camera?.cameraIdList?.firstOrNull {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) torchOn = enabled
        }
    }

    /** Поки BlackLaunch на екрані — знаємо, чи світить ліхтарик (щоб «ліхтарик» перемикав). */
    fun onStart() {
        runCatching { camera?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper())) }
    }

    fun onStop() {
        runCatching { camera?.unregisterTorchCallback(torchCallback) }
        tts?.shutdown()
        tts = null
    }

    /** Дії без контактів. Дзвінок і SMS виконує екран — їм потрібен дозвіл на контакти. */
    fun run(action: VoiceAction) {
        when (action) {
            is VoiceAction.Alarm -> start(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
            )
            is VoiceAction.Timer -> start(
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
            )
            is VoiceAction.Flashlight -> flashlight(action.on ?: !torchOn)
            is VoiceAction.Media -> mediaKey(
                when (action.command) {
                    MediaCommand.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    MediaCommand.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
                    MediaCommand.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
                    MediaCommand.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                    MediaCommand.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                },
            )
            is VoiceAction.Volume -> audio?.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                when (action.change) {
                    VolumeChange.UP -> AudioManager.ADJUST_RAISE
                    VolumeChange.DOWN -> AudioManager.ADJUST_LOWER
                    VolumeChange.MUTE -> AudioManager.ADJUST_TOGGLE_MUTE
                },
                AudioManager.FLAG_SHOW_UI,
            )
            is VoiceAction.Panel -> start(panelIntent(action.panel))
            is VoiceAction.Navigate -> navigate(action.destination)
            VoiceAction.Time -> {
                val answer = context.getString(R.string.voice_time, DateFormat.getTimeFormat(context).format(Date()))
                onMessage(answer)
                if (speakAnswers()) speak(answer)
            }
            is VoiceAction.BrightnessLevel -> setBrightness { percentToValue(action.percent) }
            is VoiceAction.BrightnessStep -> setBrightness { current ->
                val percent = valueToPercent(current) + if (action.up) BRIGHTNESS_STEP else -BRIGHTNESS_STEP
                percentToValue(percent.coerceIn(0, 100))
            }
            // Запис у календар — не в головному потоці, щоб екран не підвисав.
            is VoiceAction.CalendarEvent -> Thread {
                if (!addEvent(action)) main.post { start(calendarIntent(action)) }
            }.start()
            is VoiceAction.Call, is VoiceAction.Sms, is VoiceAction.Message -> Unit
        }
    }

    /** Відкрити номер у «Телефоні» (дзвонить користувач). */
    fun dial(contact: ContactNumber) = start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", contact.number, null)))

    /** Подзвонити одразу — лише після відліку й з дозволом на дзвінки. */
    fun call(contact: ContactNumber) = start(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", contact.number, null)))

    /** Відкрити SMS з готовим отримувачем і текстом — надсилає користувач. */
    fun sms(contact: ContactNumber, body: String) = start(
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", contact.number, null)).putExtra("sms_body", body),
    )

    /** Чи вміємо відкрити чат цього месенджера за номером контакту (інакше — «Поділитися» в ньому). */
    fun opensChatByNumber(packageName: String): Boolean = chatUri(packageName, "0", "") != null

    /**
     * Повідомлення через месенджер [packageName]. Де можна — одразу чат контакту з текстом;
     * де текст не передається — чат відкривається, а текст копіюється для вставки;
     * інші месенджери — їхнє вікно «Поділитися» з текстом (контакт вибирає користувач).
     */
    fun message(packageName: String, contact: ContactNumber?, body: String) {
        val uri = contact?.let { chatUri(packageName, internationalDigits(it.number), body) }
        if (uri == null) {
            start(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body).setPackage(packageName))
            return
        }
        if (body.isNotBlank() && !uri.toString().contains("text=")) {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("message", body))
            onMessage(context.getString(R.string.message_text_copied))
        }
        start(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName))
    }

    private fun chatUri(packageName: String, digits: String, body: String): Uri? = when {
        packageName.startsWith("com.whatsapp") ->
            Uri.parse("https://wa.me/$digits" + if (body.isNotBlank()) "?text=" + Uri.encode(body) else "")
        packageName.startsWith("org.telegram") || packageName == "org.thunderdog.challegram" ->
            Uri.parse("tg://resolve?phone=$digits")
        packageName == "com.viber.voip" -> Uri.parse("viber://chat?number=%2B$digits")
        packageName == "org.thoughtcrime.securesms" -> Uri.parse("sgnl://signal.me/#p/+$digits")
        else -> null
    }

    private fun internationalDigits(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.startsWith("00")) digits.drop(2) else digits
    }

    /** Контакт за іменем у будь-якій формі: «Марії» → «Марія», «мамі» → «Мама». */
    fun findContact(name: String): ContactNumber? {
        for (variant in ContactNames.variants(name)) {
            val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(variant))
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            )
            val found = runCatching {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
                )?.use { c ->
                    if (c.moveToFirst()) ContactNumber(c.getString(0).orEmpty(), c.getString(1).orEmpty()) else null
                }
            }.getOrNull()
            if (found != null && found.number.isNotBlank()) return found
        }
        return null
    }

    /** Перевірка голосу з налаштувань. */
    fun speakSample() = speak(context.getString(R.string.tts_sample))

    /** Крок команди «скажи …» — вголос завжди (крок задав сам користувач). */
    fun say(text: String) = speak(text)

    /** «Котра година» як крок команди: текст відповіді (вимовляється разом з іншими фразами команди). */
    fun timeText(): String = context.getString(R.string.voice_time, DateFormat.getTimeFormat(context).format(Date()))

    // Озвучити відповідь: голос створюється лише на цей раз і закривається після мовлення.
    private fun speak(text: String) {
        tts?.shutdown()
        var engine: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            val ready = engine ?: return@OnInitListener
            if (status != TextToSpeech.SUCCESS) {
                ready.shutdown()
                return@OnInitListener
            }
            ready.language = Locale.getDefault()
            // Як підказка навігатора: Android на час фрази приглушує музику й повертає гучність після.
            ready.setAudioAttributes(speechAttributes)
            val focus = requestDuck()
            ready.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    releaseDuck(focus)
                    ready.shutdown()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    releaseDuck(focus)
                    ready.shutdown()
                }
            })
            if (ready.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer") != TextToSpeech.SUCCESS) releaseDuck(focus)
        }
        engine = ttsEngine()?.let { TextToSpeech(context, listener, it) } ?: TextToSpeech(context, listener)
        tts = engine
    }

    private val speechAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Коротке слово з приглушенням інших (музика тихішає, не зупиняється). */
    private fun requestDuck(): AudioFocusRequest? {
        val manager = audio ?: return null
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAttributes)
            .setOnAudioFocusChangeListener { }
            .build()
        return if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) request else null
    }

    private fun releaseDuck(request: AudioFocusRequest?) {
        request?.let { audio?.abandonAudioFocusRequest(it) }
    }

    // Яскравість змінюємо лише з дозволу «Зміна системних налаштувань»; без нього — відкриваємо його.
    private fun setBrightness(target: (current: Int) -> Int) {
        if (!Settings.System.canWrite(context)) {
            onMessage(context.getString(R.string.brightness_permission))
            start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            return
        }
        val resolver = context.contentResolver
        val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, MAX_BRIGHTNESS / 2)
        val value = target(current).coerceIn(1, MAX_BRIGHTNESS)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
        onMessage(context.getString(R.string.brightness_set, valueToPercent(value)))
    }

    // Повзунок Android нелінійний: «50 %» на ньому — це приблизно чверть сирого значення.
    private fun percentToValue(percent: Int): Int = (MAX_BRIGHTNESS * (percent / 100.0).pow(2)).roundToInt()

    private fun valueToPercent(value: Int): Int = (sqrt(value.toDouble() / MAX_BRIGHTNESS) * 100).roundToInt()

    /**
     * Записати подію одразу в основний календар (потрібен дозвіл на запис календаря).
     * false — немає календаря чи запис не вдався; тоді відкриваємо вікно календаря.
     */
    fun addEvent(event: VoiceAction.CalendarEvent): Boolean {
        val calendarId = runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
                    "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}",
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC",
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        }.getOrNull() ?: return false
        val allDay = event.hour == null
        // Події на весь день Android зберігає опівночі за UTC.
        val zone = if (allDay) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
        val begin = eventStart(event, zone)
        val title = event.title.ifBlank { context.getString(R.string.action_row_event_new) }
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, begin)
            put(CalendarContract.Events.DTEND, begin + if (allDay) DAY_MS else HOUR_MS)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
        }
        val inserted = runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) }.getOrNull()
            ?: return false
        val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or
            if (allDay) DateUtils.FORMAT_UTC else DateUtils.FORMAT_SHOW_TIME
        val text = context.getString(R.string.event_added, title, DateUtils.formatDateTime(context, begin, flags))
        main.post { onMessage(text) }
        return inserted.lastPathSegment != null
    }

    private fun eventStart(event: VoiceAction.CalendarEvent, zone: TimeZone): Long {
        val today = Calendar.getInstance()
        return Calendar.getInstance(zone).apply {
            clear()
            set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH), event.hour ?: 0, event.minute)
            add(Calendar.DAY_OF_YEAR, event.dayOffset)
        }.timeInMillis
    }

    /** Вікно календаря з заповненою подією — якщо записати одразу не можна. */
    fun calendarIntent(event: VoiceAction.CalendarEvent): Intent {
        val begin = eventStart(event, TimeZone.getDefault())
        return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, event.title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + if (event.hour == null) DAY_MS else HOUR_MS)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.hour == null)
    }

    private fun flashlight(on: Boolean) {
        val id = torchId
        if (camera == null || id == null) {
            onMessage(context.getString(R.string.voice_no_flashlight))
            return
        }
        runCatching { camera.setTorchMode(id, on) }
            .onSuccess { torchOn = on }
            .onFailure { onMessage(context.getString(R.string.voice_no_flashlight)) }
    }

    private fun mediaKey(code: Int) {
        val manager = audio ?: return
        // Запобіжник для гравців, що «відтворити» обробляють як перемикач: уже грає — не тиснемо «відтворити»,
        // уже тиша — не тиснемо «паузу». Так повторні команди («грай» двічі) ніколи не поставлять на паузу.
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY && manager.isMusicActive) return
        if (code == KeyEvent.KEYCODE_MEDIA_PAUSE && !manager.isMusicActive) return
        manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun panelIntent(panel: SystemPanel): Intent = when (panel) {
        // Android 12+: панель «Інтернет» — маленьке вікно з перемикачем Wi-Fi (панель Wi-Fi деякі прошивки
        // відкривають повною сторінкою налаштувань).
        SystemPanel.WIFI -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Intent(Settings.Panel.ACTION_WIFI)
            else -> Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        SystemPanel.INTERNET ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            }
        SystemPanel.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    // Навігація Google Maps, якщо є, інакше будь-яка карта за адресою.
    private fun navigate(destination: String) {
        val navigation = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination)))
        if (!tryStart(navigation)) start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination))))
    }

    private fun start(intent: Intent) {
        if (!tryStart(intent)) onMessage(context.getString(R.string.voice_no_app))
    }

    private companion object {
        const val MAX_BRIGHTNESS = 255
        const val BRIGHTNESS_STEP = 15
        const val HOUR_MS = 60 * 60 * 1000L
        const val DAY_MS = 24 * HOUR_MS
    }

    private fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
