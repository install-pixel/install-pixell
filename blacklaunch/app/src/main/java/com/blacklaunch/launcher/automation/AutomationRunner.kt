package com.blacklaunch.launcher.automation

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.blacklaunch.launcher.BlackLaunchApp
import com.blacklaunch.launcher.MainActivity
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.search.SearchNormalizer
import com.blacklaunch.launcher.timer.BlackTimer
import com.blacklaunch.launcher.voice.VoiceAction
import com.blacklaunch.launcher.voice.VoiceActionRunner
import com.blacklaunch.launcher.voice.VoiceCommand
import com.blacklaunch.launcher.voice.VoiceCommandParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Автоматизації в SharedPreferences — лише на телефоні. */
class PrefsAutomationStore(context: Context) : InMemoryAutomationStore(
    decode(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)),
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun persist(list: List<Automation>) = prefs.edit().putString(KEY, encode(list)).apply()

    private companion object {
        const val PREFS = "automations"
        const val KEY = "list"
    }
}

/**
 * Виконує автоматизації за подією. Android сам будить Black Glass у момент події —
 * постійної фонової роботи немає. Кроки, які можна без екрана, виконуються одразу;
 * решта — дотиком до сповіщення або одразу, якщо дано дозвіл «Поверх інших вікон».
 */
class AutomationRunner(private val app: BlackLaunchApp) {
    private var lastEvent: Pair<String, Long>? = null

    fun fire(event: TriggerKind, address: String? = null) {
        // Android інколи надсилає ту саму подію двічі поспіль.
        val key = "$event/$address"
        val now = SystemClock.elapsedRealtime()
        lastEvent?.let { (previous, at) -> if (previous == key && now - at < DEBOUNCE_MS) return }
        lastEvent = key to now
        val matching = app.automations.automations.value.filter { it.matches(event, address) }
        if (matching.isEmpty()) return
        val commands = app.customCommands.commands.value
        matching.forEach { automation ->
            val command = commands.firstOrNull { it.id == automation.commandId } ?: return@forEach
            run(command.phrase, command.steps)
        }
    }

    private fun run(phrase: String, steps: List<String>) {
        val runner = VoiceActionRunner(app, onMessage = {})
        val later = mutableListOf<String>()
        steps.forEach { step ->
            val parsed = VoiceCommandParser.parse(step)
            if (!BackgroundSteps.canRunHidden(parsed)) {
                later += step
                return@forEach
            }
            when (parsed) {
                is VoiceCommand.SwitchProfile -> switchProfile(parsed.name)
                is VoiceCommand.Action -> when (val action = parsed.action) {
                    is VoiceAction.Timer -> if (!(BlackTimer.canRun(app) && BlackTimer.start(app, action.seconds))) later += step
                    // Яскравість без дозволу «Зміна системних налаштувань» не змінити — лишаємо на потім.
                    is VoiceAction.BrightnessLevel, is VoiceAction.BrightnessStep ->
                        if (Settings.System.canWrite(app)) runner.run(action) else later += step
                    else -> runner.run(action)
                }
                else -> Unit
            }
        }
        if (later.isNotEmpty()) finishInBlackGlass(phrase, later)
    }

    private fun switchProfile(name: String) {
        app.appScope.launch {
            val q = SearchNormalizer.normalize(name)
            val profiles = app.profileRepository.profiles.first()
            val profile = profiles.firstOrNull { SearchNormalizer.normalize(it.name) == q }
                ?: profiles.firstOrNull { SearchNormalizer.normalize(it.name).startsWith(q) }
            profile?.let { app.profileRepository.setActive(it.id) }
        }
    }

    /** Кроки, яким потрібен екран: одразу (з дозволом «Поверх інших вікон») або сповіщенням. */
    private fun finishInBlackGlass(phrase: String, steps: List<String>) {
        val intent = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_RUN_STEPS, steps.toTypedArray())
        if (Settings.canDrawOverlays(app) && runCatching { app.startActivity(intent) }.isSuccess) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.automation_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val pending = PendingIntent.getActivity(
            app, phrase.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_automation)
            .setContentTitle(phrase)
            .setContentText(app.getString(R.string.automation_tap, steps.joinToString(" → ")))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager.notify(phrase.hashCode(), notification)
    }

    /**
     * Зарядка й дротові навушники: Android повідомляє лише працюючу програму — лаунчер працює
     * майже завжди. Bluetooth ловить окремий [BluetoothAutomationReceiver] навіть без запущеного Black Glass.
     */
    fun registerLiveEvents() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> fire(TriggerKind.POWER_CONNECTED)
                    Intent.ACTION_POWER_DISCONNECTED -> fire(TriggerKind.POWER_DISCONNECTED)
                    // Стан навушників Android повторює при реєстрації — це не нова подія.
                    AudioManager.ACTION_HEADSET_PLUG -> if (!isInitialStickyBroadcast) {
                        fire(if (intent.getIntExtra("state", 0) == 1) TriggerKind.HEADSET_PLUGGED else TriggerKind.HEADSET_UNPLUGGED)
                    }
                }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private companion object {
        const val CHANNEL = "automations"
        const val DEBOUNCE_MS = 5_000L
    }
}

/** Підключення й відключення Bluetooth-пристроїв (Android будить лише в момент події). */
class BluetoothAutomationReceiver : BroadcastReceiver() {
    // Подію Android надсилає лише з дозволом «Пристрої поблизу» — без нього сюди не потрапляємо.
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? BlackLaunchApp ?: return
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val address = device?.address ?: return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> app.automationRunner.fire(TriggerKind.BLUETOOTH_CONNECTED, address)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> app.automationRunner.fire(TriggerKind.BLUETOOTH_DISCONNECTED, address)
        }
    }
}
