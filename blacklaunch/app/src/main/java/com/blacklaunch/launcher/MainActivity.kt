package com.blacklaunch.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blacklaunch.launcher.apps.AppIconCache
import com.blacklaunch.launcher.system.WallpaperController
import com.blacklaunch.launcher.settings.Appearance
import com.blacklaunch.launcher.profiles.PinnedEdits
import com.blacklaunch.launcher.profiles.ProfileViewModel
import com.blacklaunch.launcher.ui.AliasDialog
import com.blacklaunch.launcher.ui.AppPickerScreen
import com.blacklaunch.launcher.ui.ProfileEditScreen
import com.blacklaunch.launcher.ui.ProfilesScreen
import com.blacklaunch.launcher.ui.SettingsNav
import com.blacklaunch.launcher.ui.BlackHomeScreen
import com.blacklaunch.launcher.ui.SettingsScreen
import com.blacklaunch.launcher.ui.theme.BlackLaunchTheme
import com.blacklaunch.launcher.viewmodel.LauncherViewModel
import com.blacklaunch.launcher.viewmodel.UiMessage
import com.blacklaunch.launcher.voice.ModelImportActivity
import com.blacklaunch.launcher.voice.OfflineStatus
import com.blacklaunch.launcher.voice.VoiceModelStore
import com.blacklaunch.launcher.voice.VoiceLanguages
import com.blacklaunch.launcher.voice.VoiceSearchManager
import com.blacklaunch.launcher.voice.VoiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app by lazy { application as BlackLaunchApp }
    private val repository by lazy { app.appRepository }

    private val viewModel: LauncherViewModel by viewModels {
        viewModelFactory {
            initializer {
                LauncherViewModel(
                    appSource = repository,
                    userAliases = app.userAliasStore.aliases,
                    history = app.launchHistory,
                    profileSource = app.profileRepository,
                    learnedQueries = app.learnedQueries,
                )
            }
        }
    }

    private val iconCache by lazy {
        val metrics = resources.displayMetrics
        AppIconCache(repository, metrics.densityDpi, (40 * metrics.density).toInt()) {
            app.appearanceSettings.appearance.value.monochromeIcons
        }
    }

    private val profileViewModel: ProfileViewModel by viewModels {
        viewModelFactory { initializer { ProfileViewModel(app.profileRepository, repository) } }
    }

    private lateinit var voice: VoiceSearchManager
    private val wallpaper by lazy { WallpaperController(applicationContext) }
    private val offlineStatus = MutableStateFlow<Map<String, OfflineStatus>>(emptyMap())
    private val profileBanner = MutableStateFlow<String?>(null)

    // Дозвіл на мікрофон запитується лише при першому натисканні кнопки.
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start() else toast(R.string.mic_denied)
    }

    // Уже завантажені моделі (наприклад, для клавіатури): можна вибрати одразу всі три.
    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE, null, this, ModelImportActivity::class.java)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        }
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
            scope = lifecycleScope,
            models = app.voiceModelStore,
            settings = app.voiceSettings,
            onResult = viewModel::onVoiceResult,
            onMessage = ::toast,
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                for (message in viewModel.messages) toast(message.text())
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                for (message in profileViewModel.messages) {
                    toast(
                        when (message) {
                            ProfileViewModel.Message.LAST_PROFILE -> R.string.profile_last
                            ProfileViewModel.Message.SAVE_FAILED -> R.string.profile_save_failed
                        },
                    )
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                for (name in viewModel.profileSwitched) {
                    profileBanner.value = name
                    delay(PROFILE_BANNER_MS)
                    if (profileBanner.value == name) profileBanner.value = null
                }
            }
        }
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val home by viewModel.home.collectAsStateWithLifecycle()
            val banner by profileBanner.collectAsStateWithLifecycle()
            val voiceState by voice.state.collectAsStateWithLifecycle()
            val appearance by app.appearanceSettings.appearance.collectAsStateWithLifecycle()
            val offline by offlineStatus.collectAsStateWithLifecycle()
            val installedModels by app.voiceModelStore.installed.collectAsStateWithLifecycle()
            val voiceMode by app.voiceSettings.mode.collectAsStateWithLifecycle()
            val userAliases by app.userAliasStore.aliases.collectAsStateWithLifecycle()
            BlackLaunchTheme {
                BlackHomeScreen(
                    state = state,
                    home = home,
                    profileBanner = banner,
                    onSwitchProfile = viewModel::switchProfile,
                    onOpenSearch = viewModel::openSearch,
                    onQueryChange = viewModel::updateQuery,
                    onClearQuery = viewModel::clearQuery,
                    onCloseSearch = viewModel::closeSearch,
                    onLaunch = { app -> viewModel.launch(app) },
                    iconFor = iconCache::load,
                    voiceState = voiceState,
                    onMicClick = ::onMicClick,
                    settingsScreen = { dismiss ->
                        var page by remember { mutableStateOf<SettingsNav>(SettingsNav.Main) }
                        when (val current = page) {
                            SettingsNav.Main -> SettingsScreen(
                                appearance = appearance,
                                onChange = ::changeAppearance,
                                onSetBlackWallpaperNow = { setBlackWallpaper(quiet = false) },
                                onOpenSystemTheme = ::openSystemTheme,
                                offlineStatus = offline,
                                onCheckOffline = ::checkOffline,
                                onDownloadOffline = { language ->
                                    voice.downloadOffline(language)
                                    toast(R.string.download_started)
                                },
                                installedModels = installedModels,
                                voiceMode = voiceMode,
                                onVoiceModeChange = app.voiceSettings::setMode,
                                onDownloadModel = ::downloadModel,
                                onDeleteModel = app.voiceModelStore::delete,
                                onImportModel = { modelPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                                onOpenProfiles = { page = SettingsNav.Profiles },
                                onDismiss = dismiss,
                            )
                            else -> ProfileSettings(current, onNavigate = { page = it })
                        }
                    },
                    aliasDialog = { target, dismiss ->
                        AliasDialog(
                            app = target,
                            aliases = userAliases[target.packageName].orEmpty(),
                            onAdd = { app.userAliasStore.add(target.packageName, it) },
                            onRemove = { app.userAliasStore.remove(target.packageName, it) },
                            onDismiss = dismiss,
                        )
                    },
                )
            }
        }
    }

    private fun onMicClick() {
        when {
            voice.state.value is VoiceState.Listening -> voice.stop()
            voice.state.value == VoiceState.Preparing -> voice.cancel()
            !voice.isAvailable() -> launchSystemRecognizer()
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> voice.start()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Профілі в налаштуваннях: список → редагування → вибір програми. */
    @Composable
    private fun ProfileSettings(page: SettingsNav, onNavigate: (SettingsNav) -> Unit) {
        val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
        val activeId by profileViewModel.activeId.collectAsStateWithLifecycle()
        val apps by profileViewModel.apps.collectAsStateWithLifecycle()
        when (page) {
            SettingsNav.Main -> Unit
            SettingsNav.Profiles -> ProfilesScreen(
                profiles = profiles,
                activeId = activeId,
                onBack = { onNavigate(SettingsNav.Main) },
                onSetActive = profileViewModel::setActive,
                onEdit = { onNavigate(SettingsNav.Edit(it)) },
                onCreate = { name -> profileViewModel.create(name) { id -> onNavigate(SettingsNav.Edit(id)) } },
            )
            is SettingsNav.Edit -> {
                // Щойно створений профіль може ще не встигнути прийти з бази — чекаємо мить.
                val profile = profiles.firstOrNull { it.id == page.profileId } ?: return
                ProfileEditScreen(
                    profile = profile,
                    appsByKey = apps.associateBy { it.key },
                    canDelete = profiles.size > 1,
                    iconFor = iconCache::load,
                    onBack = { onNavigate(SettingsNav.Profiles) },
                    onChange = profileViewModel::update,
                    onAddApp = { onNavigate(SettingsNav.PickApp(profile.id)) },
                    onDelete = { profileViewModel.delete(profile.id) { onNavigate(SettingsNav.Profiles) } },
                )
            }
            is SettingsNav.PickApp -> {
                val profile = profiles.firstOrNull { it.id == page.profileId }
                AppPickerScreen(
                    apps = apps,
                    alreadyPinned = profile?.pinnedAppKeys.orEmpty().toSet(),
                    iconFor = iconCache::load,
                    onBack = { onNavigate(SettingsNav.Edit(page.profileId)) },
                    onPick = { picked ->
                        if (profile != null) {
                            profileViewModel.update(profile.copy(pinnedAppKeys = PinnedEdits.add(profile.pinnedAppKeys, picked.key)))
                        }
                        onNavigate(SettingsNav.Edit(page.profileId))
                    },
                )
            }
        }
    }

    /** Завантаження — браузером: у BlackLaunch немає дозволу INTERNET. */
    private fun downloadModel(language: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(VoiceModelStore.downloadUrl(language))))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.browser_missing)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) voice.trimMemory()
    }

    private fun checkOffline() {
        offlineStatus.value = emptyMap()
        voice.checkOffline(VoiceLanguages.relevant()) { offlineStatus.value = it }
    }

    private fun launchSystemRecognizer() {
        try {
            systemRecognizer.launch(VoiceSearchManager.recognizerIntent(this))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.voice_unavailable)
        }
    }

    private fun changeAppearance(transform: (Appearance) -> Appearance) {
        val before = app.appearanceSettings.appearance.value
        app.appearanceSettings.update(transform)
        val after = app.appearanceSettings.appearance.value
        // Увімкнули автоматичний режим або змінили, які екрани чорнити, — застосовуємо одразу.
        if (after.autoBlackWallpaper &&
            (!before.autoBlackWallpaper || before.wallpaperTarget != after.wallpaperTarget)
        ) {
            setBlackWallpaper(quiet = false)
        }
    }

    private fun setBlackWallpaper(quiet: Boolean) {
        lifecycleScope.launch {
            val settings = app.appearanceSettings
            val id = wallpaper.setBlack(settings.appearance.value.wallpaperTarget)
            when {
                id != null -> {
                    settings.blackWallpaperId = id
                    if (!quiet) toast(R.string.wallpaper_set)
                }
                quiet -> {
                    // Не повторюємо невдалу спробу при кожному поверненні на головний екран.
                    settings.update { it.copy(autoBlackWallpaper = false) }
                    toast(R.string.wallpaper_auto_disabled)
                }
                else -> toast(R.string.wallpaper_failed)
            }
        }
    }

    /** Автоматичний режим: якщо шпалери замінили, повертаємо чорні. */
    private fun ensureBlackWallpaper() {
        val settings = app.appearanceSettings
        if (settings.appearance.value.autoBlackWallpaper && wallpaper.currentId() != settings.blackWallpaperId) {
            setBlackWallpaper(quiet = true)
        }
    }

    private fun openSystemTheme() {
        try {
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun UiMessage.text(): Int = when (this) {
        UiMessage.LAUNCH_FAILED -> R.string.launch_failed
        UiMessage.NO_HISTORY -> R.string.no_history
        UiMessage.PROFILE_NOT_FOUND -> R.string.profile_not_found
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        repository.refresh()
        ensureBlackWallpaper()
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
        voice.destroy()
        super.onDestroy()
    }

    private companion object {
        const val PROFILE_BANNER_MS = 1_000L
    }
}
