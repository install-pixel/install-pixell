package com.blacklaunch.launcher

import androidx.compose.runtime.getValue
import android.app.ActivityManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.blacklaunch.launcher.system.RefreshRate
import com.blacklaunch.launcher.ui.FocusTimerScreen
import com.blacklaunch.launcher.ui.Haptics
import com.blacklaunch.launcher.ui.LocalHaptics
import com.blacklaunch.launcher.ui.SheetTheme
import com.blacklaunch.launcher.ui.PinState
import com.blacklaunch.launcher.ui.theme.BlackLaunchTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Екран фокус-таймера (етап 16): великий відлік і котик. Звичайний таймер можна згорнути;
 * «Суворо» (16c) Black Glass закріплює системним «Закріпленням програми» (`startLockTask`) — Android сам блокує
 * «Недавні», шторку й інші програми; вийти можна системним жестом (за бажання — з PIN у налаштуваннях телефона).
 * Окреме завдання, а не головний екран: закріплюється саме це вікно, лаунчер лишається звичайним.
 */
class FocusActivity : ComponentActivity() {
    private val app get() = application as BlackLaunchApp
    private val pin = MutableStateFlow(PinState.NONE)

    /** Android питає «Закріпити?» один раз за відкриття — відмовились, то не набридаємо (є кнопка «Закріпити»). */
    private var askedPin = false
    private var wasPinned = false
    private var askedAt = 0L

    private val strict get() = app.focus.session.value?.strict == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askedPin = savedInstanceState?.getBoolean(KEY_ASKED) == true
        // Таймер скінчився (чи завершено достроково) — відкріплюємо й повертаємось на головний екран.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.focus.session.collect { if (it == null || !app.focus.running()) leave() }
            }
        }
        // Час вийшов — завершуємо таймер самі, не чекаючи будильника: Android інколи відкладає його, і відлік
        // висів на 00:00 разом із котиком.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.focus.session.collectLatest { session ->
                    if (session == null) return@collectLatest
                    kotlinx.coroutines.delay((session.endsAt - System.currentTimeMillis()).coerceAtLeast(0L) + END_GRACE_MS)
                    app.focus.restore()
                }
            }
        }
        // Android змінює стан закріплення трохи пізніше, ніж повертає фокус вікну, — тож поки екран видно,
        // перевіряємо його ще й раз на секунду (інакше лишалось би «Android питає…» після «Закріпити»).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    refreshPinned()
                    kotlinx.coroutines.delay(PIN_CHECK_MS)
                }
            }
        }
        setContent {
            val session by app.focus.session.collectAsStateWithLifecycle()
            val profiles by app.profileRepository.profiles.collectAsStateWithLifecycle(emptyList())
            val pinState by pin.collectAsStateWithLifecycle()
            val cat by app.focus.cat.collectAsStateWithLifecycle()
            val appearance by app.appearanceSettings.appearance.collectAsStateWithLifecycle()
            // Плавність: найвища частота екрана (120 Гц), якщо ввімкнено в Оформленні.
            androidx.compose.runtime.LaunchedEffect(appearance.highRefresh) { RefreshRate.apply(this@FocusActivity, appearance.highRefresh) }
            val current = session
            // «Суворо»: «Назад» нікуди не веде (вийти — лише достроково); звичайний — згортає.
            BackHandler { if (current?.strict != true) minimize() }
            BlackLaunchTheme {
                CompositionLocalProvider(LocalHaptics provides Haptics(LocalView.current, appearance.haptics)) {
                    SheetTheme(appearance.sheet) {
                    if (current != null) {
                        FocusTimerScreen(
                            profileName = profiles.firstOrNull { it.id == current.profileId }?.name.orEmpty(),
                            endsAt = current.endsAt,
                            pin = if (current.strict) pinState else PinState.NONE,
                            cat = cat,
                            onPin = ::requestPin,
                            onMinimize = if (current.strict) null else ::minimize,
                            onEarlyExit = { app.focus.endEarly() },
                        )
                    }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPinned()
        if (strict && !askedPin && !wasPinned && app.focus.running()) {
            askedPin = true
            requestPin()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        refreshPinned()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_ASKED, askedPin)
    }

    /**
     * Чи закріплено зараз. Поки Android показує своє питання, вікно втрачає фокус — тоді «Android питає…»;
     * повернувся фокус без закріплення — «Не закріплено» з кнопкою. Відкріпили жестом до кінця — спроба (для підсумку).
     */
    private fun refreshPinned() {
        if (!strict) return
        val pinnedNow = getSystemService(ActivityManager::class.java)?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        if (wasPinned && !pinnedNow && app.focus.running()) app.focus.noteAttempt()
        wasPinned = pinnedNow
        pin.value = when {
            pinnedNow -> PinState.PINNED
            // Питання Android може з'явитися не одразу — перші півтори секунди теж «питає».
            pin.value == PinState.ASKING && (!hasWindowFocus() || SystemClock.elapsedRealtime() - askedAt < ASK_GRACE_MS) -> PinState.ASKING
            else -> PinState.NOT_PINNED
        }
    }

    private fun requestPin() {
        pin.value = PinState.ASKING
        askedAt = SystemClock.elapsedRealtime()
        if (runCatching { startLockTask() }.isFailure) pin.value = PinState.NOT_PINNED
    }

    /** Звичайний таймер згортається, але йде далі — кажемо про це, щоб не здавалось, що вийшли. */
    private fun minimize() {
        android.widget.Toast.makeText(this, R.string.focus_minimized, android.widget.Toast.LENGTH_LONG).show()
        finish()
    }

    private fun leave() {
        if (wasPinned) runCatching { stopLockTask() }
        wasPinned = false
        pin.value = PinState.NONE
        if (!isFinishing) finish()
    }

    private companion object {
        const val KEY_ASKED = "asked_pin"
        const val ASK_GRACE_MS = 1_500L
        const val PIN_CHECK_MS = 1_000L

        /** Трохи після кінця — щоб «кінець» точно настав за годинником. */
        const val END_GRACE_MS = 300L
    }
}
