package com.blacklaunch.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blacklaunch.launcher.apps.AppIconCache
import com.blacklaunch.launcher.system.WallpaperController
import com.blacklaunch.launcher.system.WallpaperTarget
import com.blacklaunch.launcher.ui.BlackHomeScreen
import com.blacklaunch.launcher.ui.theme.BlackLaunchTheme
import com.blacklaunch.launcher.viewmodel.LauncherViewModel
import com.blacklaunch.launcher.voice.VoiceSearchManager
import com.blacklaunch.launcher.voice.VoiceState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app by lazy { application as BlackLaunchApp }
    private val repository by lazy { app.appRepository }

    private val viewModel: LauncherViewModel by viewModels {
        viewModelFactory { initializer { LauncherViewModel(repository, app.userAliasStore.aliases) } }
    }

    private val iconCache by lazy {
        val metrics = resources.displayMetrics
        AppIconCache(repository, metrics.densityDpi, (36 * metrics.density).toInt())
    }

    private lateinit var voice: VoiceSearchManager

    // Дозвіл на мікрофон запитується лише при першому натисканні кнопки.
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start() else toast(R.string.mic_denied)
    }

    // Системне вікно розпізнавання — якщо вбудованого сервісу немає.
    private val systemRecognizer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val texts = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
        if (texts.isNotEmpty()) viewModel.onVoiceResult(texts)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Прозорі системні панелі зі світлими іконками поверх чорного фону.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        voice = VoiceSearchManager(
            context = this,
            onResult = viewModel::onVoiceResult,
            onError = { error -> Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show() },
        )
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val voiceState by voice.state.collectAsStateWithLifecycle()
            BlackLaunchTheme {
                BlackHomeScreen(
                    state = state,
                    onOpenSearch = viewModel::openSearch,
                    onQueryChange = viewModel::updateQuery,
                    onClearQuery = viewModel::clearQuery,
                    onCloseSearch = viewModel::closeSearch,
                    onLaunch = { app ->
                        if (!viewModel.launch(app)) toast(R.string.launch_failed)
                    },
                    iconFor = iconCache::load,
                    onSetBlackWallpaper = ::setBlackWallpaper,
                    voiceState = voiceState,
                    onMicClick = ::onMicClick,
                )
            }
        }
    }

    private fun onMicClick() {
        when {
            voice.state.value is VoiceState.Listening -> voice.stop()
            !voice.isAvailable() -> launchSystemRecognizer()
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> voice.start()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchSystemRecognizer() {
        try {
            systemRecognizer.launch(VoiceSearchManager.recognizerIntent())
        } catch (e: ActivityNotFoundException) {
            toast(R.string.voice_unavailable)
        }
    }

    private fun setBlackWallpaper(target: WallpaperTarget) {
        lifecycleScope.launch {
            val ok = WallpaperController(applicationContext).setBlack(target)
            toast(if (ok) R.string.wallpaper_set else R.string.wallpaper_failed)
        }
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        repository.refresh()
    }

    // Натискання Home, коли BlackLaunch уже відкритий, повертає чистий чорний екран.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        voice.cancel()
        viewModel.closeSearch()
    }

    // Після переходу в іншу програму пошук закривається; поворот екрана його не скидає.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            voice.cancel()
            viewModel.closeSearch()
        }
    }

    override fun onDestroy() {
        voice.cancel()
        super.onDestroy()
    }
}
