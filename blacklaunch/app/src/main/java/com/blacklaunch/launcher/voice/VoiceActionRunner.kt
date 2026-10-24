package com.blacklaunch.launcher.voice

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.text.format.DateFormat
import android.view.KeyEvent
import com.blacklaunch.launcher.R
import java.util.Date

/** Знайдений контакт для дзвінка чи SMS. */
data class ContactNumber(val name: String, val number: String)

/**
 * Виконує голосові дії через офіційні API Android. Нічого не працює у фоні:
 * лише стан ліхтарика відстежується, поки BlackLaunch на екрані.
 */
class VoiceActionRunner(
    private val context: Context,
    private val onMessage: (String) -> Unit,
) {
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
            VoiceAction.Time -> onMessage(context.getString(R.string.voice_time, DateFormat.getTimeFormat(context).format(Date())))
            is VoiceAction.Call, is VoiceAction.Sms -> Unit
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
        manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun panelIntent(panel: SystemPanel): Intent = when (panel) {
        SystemPanel.WIFI ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
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

    private fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
