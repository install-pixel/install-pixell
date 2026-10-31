package com.blacklaunch.launcher.automation

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.text.format.DateFormat
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
import com.blacklaunch.launcher.commands.CommandTrigger
import com.blacklaunch.launcher.commands.StepCommand
import com.blacklaunch.launcher.commands.StepCommands
import com.blacklaunch.launcher.search.SearchNormalizer
import com.blacklaunch.launcher.timer.BlackTimer
import com.blacklaunch.launcher.voice.MediaCommand
import com.blacklaunch.launcher.voice.VoiceAction
import com.blacklaunch.launcher.voice.VoiceActionRunner
import com.blacklaunch.launcher.voice.VoiceCommand
import com.blacklaunch.launcher.voice.VoiceCommandParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** Повертає роботу, поки вона триває (приймач тримає Android, щоб не зупинив процес раніше). */
    /** Остання подія, яку побачив Black Glass, — щоб перевірити, чи доходять події (видно під командами). */
    private val _lastSeen = MutableStateFlow(prefs.getString(KEY_LAST, null))
    val lastSeen: StateFlow<String?> = _lastSeen.asStateFlow()

    private fun noteSeen(event: TriggerKind, address: String?) {
        val device = address?.let { a -> if (event == TriggerKind.PROFILE_ON || event == TriggerKind.PROFILE_OFF) a else deviceName(a) ?: a }
        val time = DateFormat.getTimeFormat(app).format(Date())
        val text = listOfNotNull(app.getString(event.label()), device).joinToString(" · ") + " — " + time
        _lastSeen.value = text
        prefs.edit().putString(KEY_LAST, text).apply()
    }

    /** Спрацювала подія «за часом» — видно в «Остання подія». */
    fun noteTime() = noteSeen(TriggerKind.TIME, null)

    @SuppressLint("MissingPermission")
    private fun deviceName(address: String): String? = runCatching {
        app.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)?.name
    }.getOrNull()

    fun fire(event: TriggerKind, address: String? = null): Job? {
        if (address != null && event == TriggerKind.BLUETOOTH_CONNECTED) app.commandConditions.noteBluetooth(address, true)
        if (address != null && event == TriggerKind.BLUETOOTH_DISCONNECTED) app.commandConditions.noteBluetooth(address, false)
        // Android інколи надсилає ту саму подію двічі поспіль.
        val key = "$event/$address"
        val now = SystemClock.elapsedRealtime()
        // Режими користувач перемикає сам — їх не відсіюємо (туди-назад за кілька секунд — дві різні дії).
        if (event != TriggerKind.PROFILE_ON && event != TriggerKind.PROFILE_OFF) {
            lastEvent?.let { (previous, at) -> if (previous == key && now - at < DEBOUNCE_MS) return null }
            lastEvent = key to now
        }
        noteSeen(event, address)
        val matching = app.customCommands.commands.value.filter { command -> command.triggers.any { it.matches(event, address) } }
        if (matching.isEmpty()) return null
        return app.appScope.launch {
            // Bluetooth уже з'єднався, а звуковий канал до навушників ще налаштовується: чекаємо, поки вони стануть
            // виходом звуку (до 5 с), і ще трохи — інакше перші миті музика йде в динамік.
            if (event == TriggerKind.BLUETOOTH_CONNECTED || event == TriggerKind.HEADSET_PLUGGED) {
                var waited = 0L
                while (!Headphones.connected(app) && waited < AUDIO_ROUTE_WAIT_MS) {
                    delay(AUDIO_ROUTE_POLL_MS)
                    waited += AUDIO_ROUTE_POLL_MS
                }
                delay(AUDIO_ROUTE_SETTLE_MS)
            }
            matching.forEach { command ->
                // «Лише якщо» діє й для автоматичного запуску — і для команди, і для окремих кроків.
                val steps = app.commandConditions.resolve(command)
                if (!steps.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) { runSteps(command.phrase.ifBlank { app.getString(R.string.custom_commands_title) }, steps) }
                }
            }
        }
    }

    /**
     * Автоматизації до 0.32.0 жили окремо — переносимо їх у самі команди (подія → «Запускати саму»)
     * і більше окремо не зберігаємо.
     */
    fun migrateOldAutomations() {
        val old = app.automations.automations.value
        if (old.isEmpty()) return
        old.filter { it.enabled }.groupBy { it.commandId }.forEach { (commandId, list) ->
            val command = app.customCommands.commands.value.firstOrNull { it.id == commandId } ?: return@forEach
            val added = list.map { CommandTrigger(it.kind, it.deviceAddress, it.deviceName) }
            app.customCommands.save(command.copy(triggers = (command.triggers + added).distinct()))
        }
        old.forEach { app.automations.delete(it.id) }
    }

    /**
     * Кроки без екрана: що можна — одразу, решту — дотиком до сповіщення чи поверх інших вікон.
     * «Зачекати» ділить команду: кроки після паузи виконаються пізніше ([StepScheduler]).
     */
    fun runSteps(title: String, steps: List<String>) {
        val (now, wait, rest) = StepCommands.splitAtWait(steps)
        run(title, now)
        if (wait != null && rest.isNotEmpty()) app.stepScheduler.schedule(title, rest, wait)
    }

    private fun run(title: String, steps: List<String>) {
        val runner = VoiceActionRunner(app, onMessage = {}, ttsEngine = { app.voiceActionSettings.ttsEngine.value })
        val later = mutableListOf<String>()
        // Фрази вголос («котра година», «скажи …») — одним мовленням, щоб наступна не обривала попередню.
        val speech = mutableListOf<String>()
        // Крок «режим …» у цій самій команді: перемикання доходить до бази із запізненням, тож наступні кроки
        // не перевіряємо за старим режимом (інакше — «програми немає в режимі, крок пропущено»).
        var switchStep: String? = null
        steps.forEach { step ->
            when (val own = StepCommands.parse(step)) {
                is StepCommand.Say -> {
                    speech += own.text
                    return@forEach
                }
                // Головний екран потрібен на екрані — Black Glass виходить наперед.
                StepCommand.Home -> {
                    later += step
                    return@forEach
                }
                StepCommand.PreviousProfile -> {
                    app.profileRepository.previousActiveId()?.let(::setProfile)
                    return@forEach
                }
                is StepCommand.Wait, null -> Unit
            }
            val parsed = VoiceCommandParser.parse(step)
            // Час у команді — вголос і без екрана.
            if (parsed is VoiceCommand.Action && parsed.action == VoiceAction.Time) {
                speech += runner.timeText()
                return@forEach
            }
            if (!BackgroundSteps.canRunHidden(parsed)) {
                later += step
                return@forEach
            }
            when (parsed) {
                is VoiceCommand.SwitchProfile -> {
                    switchProfile(parsed.name)
                    switchStep = step
                }
                is VoiceCommand.Action -> when (val action = parsed.action) {
                    // «Грай музику» без назви: гравець, якого немає в режимі, не вмикаємо.
                    is VoiceAction.Media -> if (action.command == MediaCommand.PLAY && switchStep == null &&
                        MediaKeys.lastPlayerPackage(app)?.let { !app.allowedInMode(it) } == true
                    ) {
                        notInMode()
                    } else {
                        runner.run(action)
                    }
                    is VoiceAction.Timer -> if (!(BlackTimer.canRun(app) && BlackTimer.start(app, action.seconds))) later += step
                    // Яскравість без дозволу «Зміна системних налаштувань» не змінити — лишаємо на потім.
                    is VoiceAction.BrightnessLevel, is VoiceAction.BrightnessStep ->
                        if (Settings.System.canWrite(app)) runner.run(action) else later += step
                    else -> runner.run(action)
                }
                else -> Unit
            }
        }
        if (speech.isNotEmpty()) runner.say(speech.joinToString(". "))
        // Решта кроків виконається на екрані — разом із кроком «режим …», щоб і там перевірялись за новим режимом.
        if (later.isNotEmpty()) finishInBlackGlass(title, listOfNotNull(switchStep) + later)
    }

    private fun notInMode() {
        app.hint(app.getString(R.string.cmd_not_in_mode))
    }

    private fun switchProfile(name: String) {
        app.appScope.launch {
            val q = SearchNormalizer.normalize(name)
            val profiles = app.profileRepository.profiles.first()
            val profile = profiles.firstOrNull { SearchNormalizer.normalize(it.name) == q }
                ?: profiles.firstOrNull { SearchNormalizer.normalize(it.name).startsWith(q) }
            profile?.let { setProfile(it.id) }
        }
    }

    /** Фокус-таймер не дає автоматизаціям вивести з режиму до кінця; «Важкий вихід» — дає (ви налаштували їх наперед). */
    private fun setProfile(id: String) {
        if (app.focus.automationMaySwitch(id)) {
            app.profileRepository.setActive(id)
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                app.hint(app.getString(R.string.focus_automation_skipped))
            }
        }
    }

    /** Кроки, яким потрібен екран: одразу (з дозволом «Поверх інших вікон») або сповіщенням. */
    private fun finishInBlackGlass(phrase: String, steps: List<String>) {
        val intent = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_RUN_STEPS, steps.toTypedArray())
        // Black Glass уже на екрані (режим перемкнули на головному екрані) — відкриваємо одразу; інакше — з дозволом
        // «Поверх інших вікон» або дотиком до сповіщення.
        if ((app.homeVisible || Settings.canDrawOverlays(app)) && runCatching { app.startActivity(intent) }.isSuccess) return
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
        // Поки Black Glass працює — ще й Bluetooth напряму (на випадок, якщо прошивка не будить приймач з маніфесту).
        ContextCompat.registerReceiver(
            app,
            BluetoothAutomationReceiver(),
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            },
            Manifest.permission.BLUETOOTH_CONNECT,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
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

    private val prefs get() = app.getSharedPreferences("automations", Context.MODE_PRIVATE)

    private fun TriggerKind.label(): Int = when (this) {
        TriggerKind.BLUETOOTH_CONNECTED -> R.string.trigger_bt_on
        TriggerKind.BLUETOOTH_DISCONNECTED -> R.string.trigger_bt_off
        TriggerKind.POWER_CONNECTED -> R.string.trigger_power_on
        TriggerKind.POWER_DISCONNECTED -> R.string.trigger_power_off
        TriggerKind.HEADSET_PLUGGED -> R.string.trigger_headset_on
        TriggerKind.HEADSET_UNPLUGGED -> R.string.trigger_headset_off
        TriggerKind.TIME -> R.string.trigger_time
        TriggerKind.PROFILE_ON -> R.string.trigger_profile_on
        TriggerKind.PROFILE_OFF -> R.string.trigger_profile_off
    }

    private companion object {
        const val KEY_LAST = "last_event"
        const val CHANNEL = "automations"
        const val DEBOUNCE_MS = 5_000L
        const val AUDIO_ROUTE_WAIT_MS = 5_000L
        const val AUDIO_ROUTE_POLL_MS = 200L
        const val AUDIO_ROUTE_SETTLE_MS = 400L
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
        val job = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> app.automationRunner.fire(TriggerKind.BLUETOOTH_CONNECTED, address)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> app.automationRunner.fire(TriggerKind.BLUETOOTH_DISCONNECTED, address)
            else -> null
        } ?: return
        // Black Glass міг бути не запущений — просимо Android дочекатися кінця команди.
        val pending = goAsync()
        job.invokeOnCompletion { pending.finish() }
    }
}
