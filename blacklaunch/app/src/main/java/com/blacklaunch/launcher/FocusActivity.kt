package com.blacklaunch.launcher

import android.app.ActivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.blacklaunch.launcher.ui.FocusStrictScreen
import com.blacklaunch.launcher.ui.Haptics
import com.blacklaunch.launcher.ui.LocalHaptics
import com.blacklaunch.launcher.ui.theme.BlackLaunchTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Фокус-таймер «Суворо» (етап 16c): окреме вікно лише з відліком, яке Black Glass закріплює системним
 * «Закріпленням програми» (`startLockTask`). Android сам блокує «Недавні», шторку й інші програми;
 * вийти можна системним жестом (за бажання — з PIN, це вмикається в налаштуваннях телефона).
 * Окреме завдання, а не головний екран: закріплюється саме це вікно, лаунчер лишається звичайним.
 */
class FocusActivity : ComponentActivity() {
    private val app get() = application as BlackLaunchApp
    private val pinned = MutableStateFlow(false)

    /** Android питає «Закріпити?» один раз за відкриття — відмовились, то не набридаємо (є кнопка). */
    private var askedPin = false
    private var wasPinned = false

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
        setContent {
            val session by app.focus.session.collectAsStateWithLifecycle()
            val profiles by app.profileRepository.profiles.collectAsStateWithLifecycle(emptyList())
            val isPinned by pinned.collectAsStateWithLifecycle()
            val appearance by app.appearanceSettings.appearance.collectAsStateWithLifecycle()
            // «Назад» нікуди не веде: вийти — лише достроково.
            BackHandler {}
            BlackLaunchTheme {
                CompositionLocalProvider(LocalHaptics provides Haptics(LocalView.current, appearance.haptics)) {
                    val current = session ?: return@CompositionLocalProvider
                    FocusStrictScreen(
                        profileName = profiles.firstOrNull { it.id == current.profileId }?.name.orEmpty(),
                        endsAt = current.endsAt,
                        pinned = isPinned,
                        onPin = ::pin,
                        onEarlyExit = { app.focus.endEarly() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPinned()
        if (!askedPin && !pinned.value && app.focus.running()) {
            askedPin = true
            pin()
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

    /** Відкріпили системним жестом до кінця таймера — це теж спроба відволіктися (для підсумку). */
    private fun refreshPinned() {
        val now = getSystemService(ActivityManager::class.java)?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        if (wasPinned && !now && app.focus.running()) app.focus.noteAttempt()
        wasPinned = now
        pinned.value = now
    }

    private fun pin() {
        runCatching { startLockTask() }
    }

    private fun leave() {
        if (pinned.value) runCatching { stopLockTask() }
        pinned.value = false
        wasPinned = false
        if (!isFinishing) finish()
    }

    private companion object {
        const val KEY_ASKED = "asked_pin"
    }
}
