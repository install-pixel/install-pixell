package com.blacklaunch.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacklaunch.launcher.games.GameKind
import com.blacklaunch.launcher.ui.GameScreen
import com.blacklaunch.launcher.ui.Haptics
import com.blacklaunch.launcher.ui.LocalHaptics
import com.blacklaunch.launcher.ui.SheetTheme
import com.blacklaunch.launcher.ui.theme.BlackLaunchTheme

/**
 * Гра на весь екран. Згорнули чи вийшли — стан зберігається, наступного разу гра продовжується з того самого місця.
 * Під час фокус-таймера ігри не відкриваються — інакше таймер втратив би сенс.
 */
class GameActivity : ComponentActivity() {
    private val app get() = application as BlackLaunchApp
    private var active by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = GameKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) }
        if (kind == null || !app.games.enabled.value || app.focus.running()) {
            if (app.focus.running()) Toast.makeText(this, R.string.game_focus_blocked, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (intent.getBooleanExtra(EXTRA_FRESH, false) && savedInstanceState == null) app.games.save(kind, null)
        enableEdgeToEdge()
        setContent {
            val best by app.games.best.collectAsStateWithLifecycle()
            val appearance by app.appearanceSettings.appearance.collectAsStateWithLifecycle()
            BlackLaunchTheme {
                CompositionLocalProvider(LocalHaptics provides Haptics(LocalView.current, appearance.haptics)) {
                    SheetTheme(appearance.sheet) {
                    GameScreen(
                        kind = kind,
                        saved = app.games.state(kind),
                        best = best[kind] ?: 0,
                        onSave = { app.games.save(kind, it) },
                        onRecord = { app.games.record(kind, it) },
                        onExit = ::finish,
                        active = active,
                    )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        active = true
    }

    // Згорнули, прийшов дзвінок — гра на паузі й збережена (зберігає сам екран гри, коли стає неактивним).
    override fun onPause() {
        active = false
        super.onPause()
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_FRESH = "fresh"

        fun intent(context: Context, kind: GameKind, fresh: Boolean): Intent =
            Intent(context, GameActivity::class.java).putExtra(EXTRA_KIND, kind.name).putExtra(EXTRA_FRESH, fresh)
    }
}
