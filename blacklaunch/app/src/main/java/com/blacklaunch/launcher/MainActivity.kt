package com.blacklaunch.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.app.KeyguardManager
import android.content.Intent
import android.content.ComponentName
import android.content.ContentUris
import android.provider.ContactsContract
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import com.blacklaunch.launcher.global.SettingsCatalog
import com.blacklaunch.launcher.ui.MonitorSettingsContent
import com.blacklaunch.launcher.ui.MonitorScreen
import com.blacklaunch.launcher.profiles.NotificationMode
import com.blacklaunch.launcher.profiles.SoundMode
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi
import com.blacklaunch.launcher.commands.CustomCommand
import androidx.annotation.StringRes
import com.blacklaunch.launcher.ui.PermissionItem
import com.blacklaunch.launcher.ui.PermissionsScreen
import com.blacklaunch.launcher.ui.PairedDevice
import com.blacklaunch.launcher.ui.CustomCommandsCard
import com.blacklaunch.launcher.ui.OpenDirectlyCard
import com.blacklaunch.launcher.ui.VoiceExtrasCard
import android.bluetooth.BluetoothManager
import android.annotation.SuppressLint
import androidx.compose.ui.graphics.asImageBitmap
import android.provider.MediaStore
import android.util.Size
import android.util.LruCache
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import com.blacklaunch.launcher.profiles.defaultGlobalMode
import com.blacklaunch.launcher.profiles.RolePackages
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import com.blacklaunch.launcher.ui.LocalHaptics
import com.blacklaunch.launcher.ui.Haptics
import com.blacklaunch.launcher.backup.SettingsBackup
import com.blacklaunch.launcher.profiles.SearchScope
import com.blacklaunch.launcher.profiles.ProfileNotificationFilter
import com.blacklaunch.launcher.voice.VoiceActionRunner
import com.blacklaunch.launcher.timer.BlackTimer
import com.blacklaunch.launcher.voice.VoiceAction
import com.blacklaunch.launcher.voice.ContactNumber
import com.blacklaunch.launcher.ui.CallCountdownDialog
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blacklaunch.launcher.apps.AppIconCache
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.system.WallpaperController
import com.blacklaunch.launcher.settings.Appearance
import com.blacklaunch.launcher.global.AndroidGlobalSearch
import com.blacklaunch.launcher.global.AppSettingKind
import com.blacklaunch.launcher.global.GlobalResult
import com.blacklaunch.launcher.global.GroupKind
import com.blacklaunch.launcher.global.NotificationTexts
import com.blacklaunch.launcher.profiles.PinnedEdits
import com.blacklaunch.launcher.profiles.ProfileViewModel
import com.blacklaunch.launcher.ui.AliasDialog
import com.blacklaunch.launcher.ui.AppActionsDialog
import com.blacklaunch.launcher.system.BatterySettings
import com.blacklaunch.launcher.ui.AppPickerScreen
import com.blacklaunch.launcher.ui.AppListScreen
import com.blacklaunch.launcher.ui.ProfileEditScreen
import com.blacklaunch.launcher.ui.ProfilesScreen
import com.blacklaunch.launcher.ui.SettingsNav
import com.blacklaunch.launcher.ui.BlackHomeScreen
import com.blacklaunch.launcher.ui.LockedVoiceScreen
import com.blacklaunch.launcher.ui.SettingsScreen
import com.blacklaunch.launcher.ui.theme.BlackLaunchTheme
import com.blacklaunch.launcher.viewmodel.LauncherViewModel
import com.blacklaunch.launcher.viewmodel.VoiceReply
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.blacklaunch.launcher.system.GlanceInfo
import com.blacklaunch.launcher.system.GlanceParts
import com.blacklaunch.launcher.system.Glance

/** Системний екран «Синтез мовлення» (рушій, голоси, завантаження офлайн). */
private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"

private const val GLANCE_REFRESH_MS = 60_000L

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
                    globalSource = app.globalSearch,
                    globalKinds = app.globalSearchSettings.enabled,
                    layoutStore = app.homeLayoutStore,
                    voiceActionsEnabled = { app.voiceActionSettings.enabled.value },
                    customCommands = { app.customCommands.commands.value },
                    recentQueries = app.recentQueries,
                    showSuggestions = { activeDisplay().suggestions },
                    showRecent = { activeDisplay().recentQueries },
                    inQuickSwitch = { app.profileDisplay.of(it).quickSwitch },
                    resolveCommand = app.commandConditions::resolve,
                    scheduleSteps = { steps, seconds -> app.stepScheduler.schedule(getString(R.string.custom_commands_title), steps, seconds) },
                    previousProfileId = app.profileRepository::previousActiveId,
                    playMedia = { pkg -> com.blacklaunch.launcher.automation.MediaKeys.playIn(app, pkg) },
                    isMusicApp = { info -> com.blacklaunch.launcher.automation.MediaKeys.isPlayer(app, info.packageName, info.category) },
                    speakSteps = { parts -> app.speech.say(parts.joinToString(". ") { it ?: app.speech.timeText() }) },
                    hiddenApps = app.hiddenApps.hidden,
                    searchScopes = app.searchScopes.scopes,
                    globalModes = app.searchScopes.globalModes,
                    rolePackages = ::rolePackages,
                )
            }
        }
    }

    private val iconCache by lazy {
        val metrics = resources.displayMetrics
        AppIconCache(
            repository = repository,
            density = metrics.densityDpi,
            sizePx = (40 * metrics.density).toInt(),
            monochrome = { app.appearanceSettings.appearance.value.monochromeIcons },
            ownIcon = { it.key in app.ownIcons.keys.value },
        )
    }

    private val profileViewModel: ProfileViewModel by viewModels {
        viewModelFactory { initializer { ProfileViewModel(app.profileRepository, repository) } }
    }

    private lateinit var voice: VoiceSearchManager
    private val wallpaper by lazy { WallpaperController(applicationContext) }
    private val offlineStatus = MutableStateFlow<Map<String, OfflineStatus>>(emptyMap())
    private val profileBanner = MutableStateFlow<String?>(null)
    private val versionName by lazy { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() }

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
            onResult = ::onVoiceResult,
            onMessage = ::toast,
        )
        // Голос поверх блокування закінчився без результату — ховаємо вікно (екран блокування знову зверху).
        lifecycleScope.launch {
            var active = false
            voice.state.collect { state ->
                if (!lockedVoice.value) return@collect
                if (state != VoiceState.Idle) {
                    active = true
                } else if (active) {
                    active = false
                    // Результат приходить одразу після «Idle» — даємо йому мить, щоб не закрити вікно раніше.
                    delay(LOCKED_RESULT_GRACE_MS)
                    if (!awaitingUnlock) endLockedVoice()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                for (message in viewModel.messages) toast(message.text())
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                for (action in viewModel.voiceActions) runVoiceAction(action)
            }
        }
        // «Відповідати голосом»: калькулятор, «Відкриваю …», «Кілька варіантів…», режим.
        lifecycleScope.launch {
            for (reply in viewModel.voiceReplies) {
                if (app.voiceActionSettings.speakAnswers.value) app.speech.say(replyText(reply))
            }
        }
        // «Короткий огляд» оновлюється раз на хвилину лише поки BlackLaunch на екрані.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    if (activeDisplay().glance) {
                        glance.value = withContext(Dispatchers.IO) { Glance.read(this@MainActivity, glanceParts()) }
                    }
                    delay(GLANCE_REFRESH_MS)
                }
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
                    // Разом з назвою — що профіль робить зі звуком, щоб тихий режим не вмикався непомітно.
                    val text = bannerText(name)
                    profileBanner.value = text
                    delay(PROFILE_BANNER_MS)
                    if (profileBanner.value == text) profileBanner.value = null
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                for (unused in viewModel.openMonitor) openMonitor()
            }
        }
        // Запущено як «цифровий асистент» — одразу пошук і мікрофон.
        if (savedInstanceState == null && !handleAssist(intent)) handleRunSteps(intent)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val home by viewModel.home.collectAsStateWithLifecycle()
            val banner by profileBanner.collectAsStateWithLifecycle()
            val voiceState by voice.state.collectAsStateWithLifecycle()
            val appearance by app.appearanceSettings.appearance.collectAsStateWithLifecycle()
            val offline by offlineStatus.collectAsStateWithLifecycle()
            val installedModels by app.voiceModelStore.installed.collectAsStateWithLifecycle()
            val modelInfo by app.voiceModelStore.info.collectAsStateWithLifecycle()
            val voiceMode by app.voiceSettings.mode.collectAsStateWithLifecycle()
            val userAliases by app.userAliasStore.aliases.collectAsStateWithLifecycle()
            val globalKinds by app.globalSearchSettings.enabled.collectAsStateWithLifecycle()
            val crashLog by app.crashLog.text.collectAsStateWithLifecycle()
            val voiceActions by app.voiceActionSettings.enabled.collectAsStateWithLifecycle()
            val directCall by app.voiceActionSettings.directCall.collectAsStateWithLifecycle()
            val speakAnswers by app.voiceActionSettings.speakAnswers.collectAsStateWithLifecycle()
            val ttsEngine by app.voiceActionSettings.ttsEngine.collectAsStateWithLifecycle()
            val customCommands by app.customCommands.commands.collectAsStateWithLifecycle()
            val commandProfiles by profileViewModel.profiles.collectAsStateWithLifecycle()
            val commandApps by app.appRepository.apps.collectAsStateWithLifecycle()
            val allApps by viewModel.allApps.collectAsStateWithLifecycle()
            val privateSpace by app.privateSpace.state.collectAsStateWithLifecycle()
            val ownIcons by app.ownIcons.keys.collectAsStateWithLifecycle()
            // Новий екземпляр — значки перемальовуються (свій значок, сірий режим).
            val iconFor: suspend (AppInfo) -> ImageBitmap? = remember(ownIcons, appearance.monochromeIcons) {
                { target -> iconCache.load(target) }
            }
            val glanceInfo by glance.collectAsStateWithLifecycle()
            // Увімкнули «Короткий огляд» — показуємо одразу, не чекаючи хвилини.
            val displays by app.profileDisplay.displays.collectAsStateWithLifecycle()
            // Екран і пошук активного профілю (без власного вибору — колишні загальні налаштування).
            val display = remember(displays, home.profileId, appearance) { app.profileDisplay.of(home.profileId) }
            // Перемкнули профіль — у нового своє рішення щодо рядка стану.
            LaunchedEffect(display.hideStatusBar) { applyStatusBar() }
            LaunchedEffect(display.glance, display.ownGlance, appearance.glanceClock, appearance.glanceAlarm, appearance.glanceEvent, appearance.glanceBattery) {
                if (display.glance) glance.value = withContext(Dispatchers.IO) { Glance.read(this@MainActivity, glanceParts()) }
            }
            val hiddenKeys by app.hiddenApps.hidden.collectAsStateWithLifecycle()
            val installedApps by repository.apps.collectAsStateWithLifecycle()
            val monitorLook by app.monitorSettings.look.collectAsStateWithLifecycle()
            val monitorShown by monitorOpen.collectAsStateWithLifecycle()
            // Монітор відкрили командою з іншого профілю — «Назад» повертає звичайний екран.
            BackHandler(enabled = monitorShown) { monitorOpen.value = false }
            BlackLaunchTheme {
            CompositionLocalProvider(LocalHaptics provides Haptics(LocalView.current, appearance.haptics)) {
                BlackHomeScreen(
                    state = state,
                    // «Показувати програми на екрані» вимкнено — значків не видно, профіль працює як раніше.
                    home = if (display.showApps) home else home.copy(pinned = emptyList()),
                    profileBanner = banner,
                    monitor = if (display.monitor || monitorShown) {
                        { modifier ->
                            val title = if (display.monitor) home.profileName else stringResource(R.string.mon_title)
                            MonitorScreen(monitorLook, app.systemStats, ::appLabel, title.uppercase(), modifier)
                        }
                    } else {
                        null
                    },
                    onSwitchProfile = viewModel::switchProfile,
                    onMoveApp = viewModel::moveApp,
                    onRemoveApp = viewModel::unpin,
                    profileSwipe = appearance.profileSwipe,
                    showProfileLabel = display.profileLabel,
                    glance = if (display.glance) glanceInfo else null,
                    onIdleChange = { idle ->
                        homeIdle = idle
                        applyStatusBar()
                    },
                    onOpenSearch = {
                        instantSearchClose = false
                        viewModel.openSearch()
                    },
                    animateSearchClose = !instantSearchClose,
                    onQueryChange = viewModel::type,
                    onFieldFocused = viewModel::onFieldFocused,
                    onClearQuery = viewModel::clearQuery,
                    onCloseSearch = viewModel::closeSearch,
                    onLaunch = { app -> viewModel.launch(app) },
                    onGlobal = ::openGlobalResult,
                    onAction = viewModel::runAction,
                    onCustom = viewModel::runCustom,
                    onRecent = viewModel::useRecent,
                    onRemoveRecent = viewModel::removeRecent,
                    allApps = if (display.allAppsButton) allApps else emptyList(),
                    privateSpace = privateSpace,
                    onTogglePrivate = app.privateSpace::toggle,
                    onExpand = viewModel::expandGroup,
                    thumbFor = ::thumbnail,
                    onShareMedia = ::shareMedia,
                    onTrashMedia = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ::trashMedia else null,
                    onContactAction = ::contactAction,
                    iconFor = iconFor,
                    voiceState = voiceState,
                    onMicClick = ::onMicClick,
                    settingsRequest = settingsRequest.collectAsStateWithLifecycle().value,
                    settingsScreen = { dismiss ->
                        var page by remember { mutableStateOf(requestedSettingsPage?.also { requestedSettingsPage = null } ?: SettingsNav.Main) }
                        when (val current = page) {
                            SettingsNav.Main, SettingsNav.HomeScreen, SettingsNav.Appearance, SettingsNav.Search,
                            SettingsNav.Voice, SettingsNav.VoiceActions, SettingsNav.Recognition, SettingsNav.CustomCommands, SettingsNav.Monitor, SettingsNav.Battery, SettingsNav.About,
                            -> SettingsScreen(
                                page = current,
                                onNavigate = { page = it },
                                versionName = versionName,
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
                                modelInfo = modelInfo,
                                voiceMode = voiceMode,
                                onVoiceModeChange = app.voiceSettings::setMode,
                                onDownloadModel = ::downloadModel,
                                onDeleteModel = app.voiceModelStore::delete,
                                onImportModel = { modelPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                                onOpenBattery = { BatterySettings.openBattery(this) },
                                globalKinds = globalKinds,
                                onGlobalKindChange = ::setGlobalKind,
                                voiceActions = voiceActions,
                                onVoiceActionChange = app.voiceActionSettings::set,
                                directCall = directCall,
                                onDirectCallChange = ::setDirectCall,
                                speakAnswers = speakAnswers,
                                onSpeakAnswersChange = app.voiceActionSettings::setSpeakAnswers,
                                ttsEngines = remember(page) { ttsEngines() },
                                ttsEngine = ttsEngine,
                                onTtsEngineChange = app.voiceActionSettings::setTtsEngine,
                                onTestVoice = actionRunner::speakSample,
                                onOpenTtsSettings = { startOrToast(Intent(TTS_SETTINGS)) },
                                hiddenApps = installedApps.filter { it.key in hiddenKeys }.sortedBy { it.label.lowercase() },
                                onShowApp = app.hiddenApps::show,
                                monitorPage = {
                                    resumeTick.collectAsStateWithLifecycle().value
                                    MonitorSettingsContent(
                                        look = monitorLook,
                                        usageAccess = app.systemStats.hasUsageAccess(),
                                        onTile = app.monitorSettings::setTile,
                                        onAccent = app.monitorSettings::setAccent,
                                        onOpenUsageAccess = ::openUsageAccess,
                                        onOpenMonitor = { dismiss(); openMonitor() },
                                    )
                                },
                                commandsPage = {
                                    val bluetoothAllowed by bluetoothAllowedState.collectAsStateWithLifecycle()
                                    val canOpenDirectly by overlayAllowedState.collectAsStateWithLifecycle()
                                    CustomCommandsCard(
                                        commands = customCommands,
                                        apps = commandApps,
                                        profiles = commandProfiles.map { it.name },
                                        iconFor = iconFor,
                                        bluetoothAllowed = bluetoothAllowed,
                                        pairedDevices = ::pairedDevices,
                                        onRequestBluetooth = ::requestBluetooth,
                                        onSave = ::saveCommand,
                                        onDelete = app.customCommands::delete,
                                        canOpenDirectly = canOpenDirectly,
                                    )
                                    val smart by app.automationSettings.smartProfiles.collectAsStateWithLifecycle()
                                    val hourly by app.automationSettings.hourlyChime.collectAsStateWithLifecycle()
                                    val readAloud by app.automationSettings.readMessages.collectAsStateWithLifecycle()
                                    val access by notificationAccessState.collectAsStateWithLifecycle()
                                    VoiceExtrasCard(
                                        smartProfiles = smart,
                                        hourlyChime = hourly,
                                        readMessages = readAloud,
                                        notificationAccess = access,
                                        onSmartProfiles = app.automationSettings::setSmartProfiles,
                                        onHourlyChime = app.automationSettings::setHourlyChime,
                                        onReadMessages = app.automationSettings::setReadMessages,
                                        onOpenNotificationAccess = ::openNotificationAccess,
                                    )
                                    val lastSeen by app.automationRunner.lastSeen.collectAsStateWithLifecycle()
                                    OpenDirectlyCard(canOpenDirectly, lastSeen) {
                                        startOrToast(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", packageName, null)))
                                    }
                                },
                                onOpenDefaultApps = { startOrToast(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) },
                                crashLog = crashLog,
                                onCopyCrashLog = ::copyCrashLog,
                                onExportSettings = { exportFile.launch("black-glass-$versionName.json") },
                                onImportSettings = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                                onClearHistory = {
                                    app.launchHistory.clear()
                                    app.learnedQueries.clear()
                                    app.recentQueries.clear()
                                    NotificationTexts.clear()
                                    toast(R.string.history_cleared)
                                },
                                onClearCrashLog = app.crashLog::clear,
                                onOpenAppInfo = {
                                    awaitingKind = GroupKind.SMS
                                    openAppInfo(packageName)
                                },
                                onDismiss = dismiss,
                            )
                            SettingsNav.Permissions -> {
                                // Повернулись із налаштувань Android — стан перевіряється знову.
                                resumeTick.collectAsStateWithLifecycle().value
                                PermissionsScreen(permissionItems(), onBack = { page = SettingsNav.Main })
                            }
                            else -> ProfileSettings(current, onNavigate = { page = it })
                        }
                    },
                    appActions = { target, onNames, dismiss ->
                        val pinned = viewModel.isPinned(target)
                        AppActionsDialog(
                            app = target,
                            pinned = pinned,
                            onTogglePin = { viewModel.setPinned(target, !pinned) },
                            onNames = onNames,
                            onAppInfo = { openAppInfo(target.packageName) },
                            onUninstall = {
                                startOrToast(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", target.packageName, null)))
                            },
                            onHide = { app.hiddenApps.hide(target.key) },
                            ownIcon = target.key in ownIcons,
                            onToggleOwnIcon = { app.ownIcons.set(target.key, target.key !in ownIcons) },
                            shortcuts = remember(target.key) {
                                app.globalSearch.shortcutsFor(target.packageName, target.userSerial).map { shortcut ->
                                    shortcut.title to { openGlobalResult(shortcut) }
                                }
                            },
                            onDismiss = dismiss,
                        )
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
                pendingCall?.let { contact ->
                    CallCountdownDialog(
                        contact = contact,
                        onCall = {
                            pendingCall = null
                            actionRunner.call(contact)
                        },
                        onCancel = { pendingCall = null },
                    )
                }
            }
            // Голос поверх заблокованого екрана: лише чорний екран із мікрофоном — нічого з телефона не видно.
            val lockedShown by lockedVoice.collectAsStateWithLifecycle()
            if (lockedShown) LockedVoiceScreen(voiceState, onCancel = ::endLockedVoice)
            }
        }
    }

    private val actionRunner by lazy {
        VoiceActionRunner(
            context = this,
            onMessage = { message -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show() },
            speakAnswers = { app.voiceActionSettings.speakAnswers.value },
            ttsEngine = { app.voiceActionSettings.ttsEngine.value },
        )
    }

    /** Голосовий дзвінок, що чекає кінця відліку. */
    private var pendingCall by mutableStateOf<ContactNumber?>(null)

    /** Дія з контактом, що чекає дозволу на контакти. */
    private var pendingContactAction: VoiceAction? = null

    private val contactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingContactAction
        pendingContactAction = null
        if (granted && action != null) runVoiceAction(action) else if (!granted) toast(R.string.voice_permission_contacts)
    }

    private val callPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.voiceActionSettings.setDirectCall(true) else toast(R.string.direct_call_denied)
    }

    /**
     * Доступ до сповіщень: на Android 11+ — одразу сторінка Black Glass з одним перемикачем,
     * щоб не чіпати інші програми в загальному списку; на старших — список.
     */
    private fun openNotificationAccess() {
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, ProfileNotificationFilter::class.java).flattenToString(),
            )
            startOrToast(detail, list)
        } else {
            startOrToast(list)
        }
    }

    /**
     * Програми за замовчуванням для дзвінків, SMS, календаря й налаштувань — для режиму
     * глобального пошуку «За програмами профілю». Читаються в момент пошуку (без фону).
     */
    private fun rolePackages(): RolePackages {
        fun resolve(intent: Intent): Set<String> = runCatching {
            packageManager.queryIntentActivities(intent, 0).mapTo(HashSet()) { it.activityInfo.packageName }
        }.getOrDefault(emptySet())
        val dialer = setOfNotNull(runCatching { getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull())
        val sms = setOfNotNull(runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull())
        return RolePackages(
            dialer = dialer + resolve(Intent(Intent.ACTION_DIAL)),
            contacts = resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS)),
            // Лише SMS за замовчуванням: месенджери теж приймають «smsto:», але «Написати» відкриває SMS.
            sms = sms,
            calendar = resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)),
            settings = resolve(Intent(Settings.ACTION_SETTINGS)),
        )
    }

    /** Встановлені рушії синтезу мовлення (пакет → назва), щоб вибрати голос відповідей. */
    private fun ttsEngines(): List<Pair<String, String>> =
        packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .map { it.serviceInfo.packageName to it.loadLabel(packageManager).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }

    /** Подія, що чекає дозволу на запис календаря. */
    private var pendingCalendarEvent: VoiceAction.CalendarEvent? = null

    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val event = pendingCalendarEvent ?: return@registerForActivityResult
        pendingCalendarEvent = null
        // Без дозволу — вікно календаря з заповненою подією, як раніше.
        if (granted) actionRunner.run(event) else startOrToast(actionRunner.calendarIntent(event))
    }

    /** Таймер, що чекає дозволу на сповіщення. */
    private var pendingTimerSeconds = 0

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        val seconds = pendingTimerSeconds
        pendingTimerSeconds = 0
        if (seconds > 0) startTimer(seconds, askPermission = false)
    }

    /**
     * Власний таймер BlackLaunch (відлік у сповіщенні / капсулі, без вікна «Годинника»).
     * Без дозволу на сповіщення чи точний будильник — таймер ставить «Годинник».
     */
    private fun startTimer(seconds: Int, askPermission: Boolean = true) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission && askPermission) {
            pendingTimerSeconds = seconds
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (BlackTimer.canRun(this) && BlackTimer.start(this, seconds)) {
            toast(R.string.timer_started)
        } else {
            actionRunner.run(VoiceAction.Timer(seconds))
        }
    }

    private fun setDirectCall(on: Boolean) {
        if (on && ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            callPermission.launch(Manifest.permission.CALL_PHONE)
            return
        }
        app.voiceActionSettings.setDirectCall(on)
    }

    /** Голосова дія: дзвінок і SMS шукають контакт (з дозволом), решту виконує [actionRunner]. */
    private fun runVoiceAction(action: VoiceAction) {
        if (action is VoiceAction.Timer) {
            startTimer(action.seconds)
            return
        }
        // Подія записується одразу — потрібен дозвіл на запис календаря (питаємо раз).
        if (action is VoiceAction.CalendarEvent &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingCalendarEvent = action
            calendarPermission.launch(Manifest.permission.WRITE_CALENDAR)
            return
        }
        var messenger: AppInfo? = null
        if (action is VoiceAction.Message) {
            messenger = viewModel.findApp(action.app)
            if (messenger == null) {
                Toast.makeText(this, getString(R.string.app_not_found, action.app), Toast.LENGTH_SHORT).show()
                return
            }
            // Месенджер без чату за номером — його «Поділитися», контакт не потрібен.
            if (!actionRunner.opensChatByNumber(messenger.packageName)) {
                actionRunner.message(messenger.packageName, null, action.body)
                return
            }
        }
        val name = when (action) {
            is VoiceAction.Call -> action.name
            is VoiceAction.Sms -> action.name
            is VoiceAction.Message -> action.name
            else -> {
                actionRunner.run(action)
                return
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingContactAction = action
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            return
        }
        val contact = actionRunner.findContact(name)
        if (contact == null) {
            Toast.makeText(this, getString(R.string.voice_contact_not_found, name), Toast.LENGTH_SHORT).show()
            return
        }
        when (action) {
            is VoiceAction.Sms -> actionRunner.sms(contact, action.body)
            is VoiceAction.Message -> actionRunner.message(messenger?.packageName ?: return, contact, action.body)
            else -> {
                val canCall = app.voiceActionSettings.directCall.value &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                if (canCall) pendingCall = contact else actionRunner.dial(contact)
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

    /** Швидка дія біля контакту: подзвонити (номер у «Телефоні») чи написати SMS. */
    private fun contactAction(contact: GlobalResult.Contact, call: Boolean) {
        val number = contactNumber(contact)
        if (number == null) {
            Toast.makeText(this, getString(R.string.voice_contact_not_found, contact.name), Toast.LENGTH_SHORT).show()
            return
        }
        val found = ContactNumber(contact.name, number)
        if (call) actionRunner.dial(found) else actionRunner.sms(found, "")
    }

    private fun contactNumber(contact: GlobalResult.Contact): String? = runCatching {
        val uri = ContactsContract.Contacts.lookupContact(contentResolver, Uri.parse(contact.lookupUri)) ?: return null
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(ContentUris.parseId(uri).toString()),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /** Дія на результат глобального пошуку. */
    private fun openGlobalResult(result: GlobalResult) {
        when (result) {
            is GlobalResult.Calculation -> {
                getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText(result.expression, result.value))
                toast(R.string.copied)
            }
            is GlobalResult.Profile -> viewModel.activateProfile(result.profileId)
            is GlobalResult.Setting -> if (result.action == SettingsCatalog.MONITOR_ACTION) {
                openMonitor()
            } else if (result.action == SettingsCatalog.COMMANDS_ACTION) {
                // Власна сторінка Black Glass — налаштування лаунчера одразу на «Моїх командах».
                requestedSettingsPage = SettingsNav.CustomCommands
                viewModel.forgetSearch()
                settingsRequest.value++
            } else {
                startOrToast(Intent(result.action), fallback = Intent(Settings.ACTION_SETTINGS))
            }
            is GlobalResult.Shortcut -> if (!app.globalSearch.startShortcut(result)) toast(R.string.launch_failed)
            is GlobalResult.Contact -> startOrToast(Intent(Intent.ACTION_VIEW, Uri.parse(result.lookupUri)))
            is GlobalResult.Event -> startOrToast(Intent(Intent.ACTION_VIEW, app.globalSearch.eventUri(result)))
            is GlobalResult.Sms -> startOrToast(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${result.address}")))
            is GlobalResult.AppSetting -> openAppSetting(result)
            is GlobalResult.Call -> actionRunner.dial(ContactNumber(result.title, result.number))
            is GlobalResult.NotificationText -> {
                val entry = NotificationTexts.find(result.id)
                if (entry == null || !NotificationTexts.open(this, entry)) {
                    packageManager.getLaunchIntentForPackage(result.packageName)?.let { startOrToast(it) } ?: toast(R.string.launch_failed)
                }
            }
            is GlobalResult.Media -> startOrToast(
                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(result.uri), result.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }
    }

    private val backup by lazy { SettingsBackup(app) }

    // Резервна копія — через системний вибір файлу, без дозволів на пам'ять.
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = runCatching {
                val text = backup.export(versionName)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("No stream")
                }
            }.isSuccess
            toast(if (ok) R.string.backup_saved else R.string.backup_failed)
        }
    }

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("No stream")
                }
                backup.restore(text)
            }
            result.onSuccess {
                Toast.makeText(this@MainActivity, getString(R.string.backup_restored, it.profiles, it.waitingApps), Toast.LENGTH_SHORT).show()
            }.onFailure { toast(R.string.backup_failed) }
        }
    }

    /** Копіює журнал вильотів, щоб вставити його в чат. */
    private fun copyCrashLog() {
        val text = app.crashLog.text.value ?: return
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Black Glass crash log", text))
        toast(R.string.crash_log_copied)
    }

    /** Сторінка налаштувань конкретної програми; де її немає — «Про програму». */
    private fun openAppSetting(result: GlobalResult.AppSetting) {
        val pkg = result.packageName
        val packageUri = Uri.fromParts("package", pkg, null)
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        when (result.kind) {
            AppSettingKind.NOTIFICATIONS -> startOrToast(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
                details,
            )
            AppSettingKind.BATTERY -> BatterySettings.openForApp(this, pkg)
            AppSettingKind.INFO -> startOrToast(details)
            AppSettingKind.DATA -> startOrToast(Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, packageUri), details)
            AppSettingKind.LINKS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startOrToast(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, packageUri), details)
            } else {
                startOrToast(details)
            }
        }
    }

    /** Вибрані фото й відео — у звичайне системне меню «Поділитися». */
    private fun shareMedia(items: List<GlobalResult.Media>) {
        if (items.isEmpty()) return
        val uris = ArrayList(items.map { Uri.parse(it.uri) })
        val type = when {
            items.all { it.mime.startsWith("image/") } -> "image/*"
            items.all { it.mime.startsWith("video/") } -> "video/*"
            else -> "*/*"
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newUri(contentResolver, items.first().title, uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        startOrToast(Intent.createChooser(send, null))
    }

    // «У кошик»: Android сам питає підтвердження, фото можна відновити 30 днів (Android 11+).
    private val trashRequest = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            toast(R.string.moved_to_trash)
            viewModel.refreshResults()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun trashMedia(items: List<GlobalResult.Media>) {
        if (items.isEmpty()) return
        val request = runCatching { MediaStore.createTrashRequest(contentResolver, items.map { Uri.parse(it.uri) }, true) }.getOrNull()
            ?: return toast(R.string.launch_failed)
        trashRequest.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    // Мініатюри фото й відео в пошуку — офіційним API сховища, у пам'яті до 200 штук.
    private val thumbnails = LruCache<String, ImageBitmap>(THUMB_CACHE)

    private suspend fun thumbnail(uri: String): ImageBitmap? {
        if (uri.isEmpty()) return null
        thumbnails.get(uri)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val parsed = Uri.parse(uri)
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentResolver.loadThumbnail(parsed, Size(THUMB_PX, THUMB_PX), null)
                } else {
                    @Suppress("DEPRECATION")
                    if ("/video/" in uri) {
                        MediaStore.Video.Thumbnails.getThumbnail(contentResolver, ContentUris.parseId(parsed), MediaStore.Video.Thumbnails.MINI_KIND, null)
                    } else {
                        MediaStore.Images.Thumbnails.getThumbnail(contentResolver, ContentUris.parseId(parsed), MediaStore.Images.Thumbnails.MINI_KIND, null)
                    }
                }
                bitmap?.asImageBitmap()
            }.getOrNull()
        }?.also { thumbnails.put(uri, it) }
    }

    // ---- Дозволи: усі на одній сторінці ----
    private val resumeTick = MutableStateFlow(0)

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun isDefaultLauncher(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

    private fun permissionItems(): List<PermissionItem> {
        val appInfo = { openAppInfo(packageName) }
        fun runtime(@StringRes title: Int, @StringRes purpose: Int, permission: String) =
            PermissionItem(getString(title), getString(purpose), granted(permission), appInfo)
        return buildList {
            add(PermissionItem(getString(R.string.perm_home), getString(R.string.perm_home_why), isDefaultLauncher()) {
                startOrToast(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            })
            add(PermissionItem(getString(R.string.perm_notification_access), getString(R.string.perm_notification_access_why),
                ProfileNotificationFilter.hasAccess(this@MainActivity), ::openNotificationAccess))
            add(PermissionItem(getString(R.string.perm_dnd), getString(R.string.perm_dnd_why), app.profileApplier.hasDndAccess()) {
                startOrToast(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            })
            add(PermissionItem(getString(R.string.perm_write_settings), getString(R.string.perm_write_settings_why), Settings.System.canWrite(this@MainActivity)) {
                startOrToast(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.fromParts("package", packageName, null)))
            })
            add(PermissionItem(getString(R.string.perm_overlay), getString(R.string.perm_overlay_why), Settings.canDrawOverlays(this@MainActivity)) {
                startOrToast(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", packageName, null)))
            })
            add(runtime(R.string.perm_microphone, R.string.perm_microphone_why, Manifest.permission.RECORD_AUDIO))
            add(PermissionItem(getString(R.string.monitor_usage_title), getString(R.string.perm_usage_why), app.systemStats.hasUsageAccess(), ::openUsageAccess))
            add(runtime(R.string.group_contacts, R.string.perm_contacts_why, Manifest.permission.READ_CONTACTS))
            add(runtime(R.string.group_calls, R.string.perm_calls_why, Manifest.permission.READ_CALL_LOG))
            add(runtime(R.string.perm_call_phone, R.string.perm_call_phone_why, Manifest.permission.CALL_PHONE))
            add(runtime(R.string.group_sms, R.string.perm_sms_why, Manifest.permission.READ_SMS))
            add(runtime(R.string.group_calendar, R.string.perm_calendar_why, Manifest.permission.WRITE_CALENDAR))
            AndroidGlobalSearch.MEDIA_KINDS.forEach { kind ->
                val title = when (kind) {
                    GroupKind.MUSIC -> R.string.group_music
                    GroupKind.VIDEO -> R.string.group_video
                    else -> R.string.group_photos
                }
                add(PermissionItem(getString(title), getString(R.string.perm_media_why),
                    AndroidGlobalSearch.mediaPermissions(kind).any(::granted), appInfo))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(runtime(R.string.perm_bluetooth, R.string.perm_bluetooth_why, Manifest.permission.BLUETOOTH_CONNECT))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(runtime(R.string.perm_post_notifications, R.string.perm_post_notifications_why, Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    // ---- Автоматизації ----
    private val bluetoothAllowedState = MutableStateFlow(false)
    private val overlayAllowedState = MutableStateFlow(false)

    private fun bluetoothGranted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        bluetoothAllowedState.value = granted
        if (!granted) toast(R.string.global_permission_denied)
    }

    private fun requestBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    /** Спарені пристрої — щоб вибрати навушники чи авто; лише з дозволом «Пристрої поблизу». */
    @SuppressLint("MissingPermission")
    private fun pairedDevices(): List<PairedDevice> {
        if (!bluetoothGranted()) return emptyList()
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.orEmpty().map { PairedDevice(it.name ?: it.address, it.address) }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    // Сповіщення «Торкніться, щоб виконати» — питаємо дозвіл, коли зберігають першу автоматизацію.
    private val automationNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Команда, що запускається сама, може попросити дотик сповіщенням — питаємо дозвіл на сповіщення. */
    private fun saveCommand(command: CustomCommand) {
        app.customCommands.save(command)
        if (command.triggers.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            automationNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** «Відпочинок · Без звуку · Не турбувати»; звичайний звук не дописуємо. */
    private fun bannerText(name: String): String {
        val profile = profileViewModel.profiles.value.firstOrNull { it.name == name } ?: return name
        val sound = when (profile.soundMode) {
            SoundMode.VIBRATE -> getString(R.string.sound_vibrate)
            SoundMode.SILENT -> getString(R.string.sound_silent)
            else -> null
        }
        val dnd = if (profile.notificationMode == NotificationMode.DO_NOT_DISTURB) getString(R.string.notif_dnd) else null
        return listOfNotNull(name, sound, dnd).joinToString(" · ")
    }

    // ---- Монітор системи ----
    /** Монітор, відкритий командою «монітор» з будь-якого профілю (у профілі-моніторі він і так на екрані). */
    private val monitorOpen = MutableStateFlow(false)

    private fun openMonitor() {
        viewModel.forgetSearch()
        monitorOpen.value = true
    }

    private fun appLabel(pkg: String): String? = app.appRepository.apps.value.firstOrNull { it.packageName == pkg }?.label

    private fun openUsageAccess() = startOrToast(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    private fun openAppInfo(pkg: String) =
        startOrToast(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))

    /**
     * Дозвіл SMS чи журналу дзвінків дали в системних налаштуваннях (обмежені налаштування Android 15+),
     * доступ до сповіщень — на окремій сторінці: після повернення вмикаємо джерело саме.
     */
    private var awaitingKind: GroupKind? = null

    private fun enableAwaitedKind() {
        val kind = awaitingKind ?: return
        val ready = if (kind == GroupKind.NOTIFICATIONS) {
            ProfileNotificationFilter.hasAccess(this)
        } else {
            globalPermissions(kind).any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        }
        if (ready) {
            awaitingKind = null
            app.globalSearchSettings.set(kind, true)
        }
    }

    private fun startOrToast(intent: Intent, fallback: Intent? = null) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (fallback != null) startOrToast(fallback) else toast(R.string.launch_failed)
        }
    }

    // Контакти, календар, SMS, дзвінки й медіа вмикаються лише з дозволом; без нього джерело лишається вимкненим.
    private var pendingGlobalKind: GroupKind? = null
    private val globalPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val kind = pendingGlobalKind ?: return@registerForActivityResult
        pendingGlobalKind = null
        when {
            // Фото й відео: досить і «лише вибраних» (Android 14+).
            result.values.any { it } -> app.globalSearchSettings.set(kind, true)
            // Android 15+ блокує SMS і журнал дзвінків для програм не з Play, доки користувач не зніме обмеження.
            kind == GroupKind.SMS || kind == GroupKind.CALLS -> {
                Toast.makeText(this, if (kind == GroupKind.SMS) R.string.sms_restricted else R.string.calls_restricted, Toast.LENGTH_LONG).show()
                awaitingKind = kind
                openAppInfo(packageName)
            }
            else -> toast(R.string.global_permission_denied)
        }
    }

    private fun globalPermissions(kind: GroupKind): List<String> = when (kind) {
        GroupKind.CONTACTS -> listOf(Manifest.permission.READ_CONTACTS)
        GroupKind.CALENDAR -> listOf(Manifest.permission.READ_CALENDAR)
        GroupKind.SMS -> listOf(Manifest.permission.READ_SMS)
        GroupKind.CALLS -> listOf(Manifest.permission.READ_CALL_LOG)
        in AndroidGlobalSearch.MEDIA_KINDS -> AndroidGlobalSearch.mediaPermissions(kind)
        else -> emptyList()
    }

    private fun setGlobalKind(kind: GroupKind, on: Boolean) {
        if (!on) {
            if (kind == GroupKind.NOTIFICATIONS) NotificationTexts.clear()
            app.globalSearchSettings.set(kind, false)
            return
        }
        // Тексти сповіщень — через той самий «Доступ до сповіщень», що й фільтр профілю.
        if (kind == GroupKind.NOTIFICATIONS && !ProfileNotificationFilter.hasAccess(this)) {
            Toast.makeText(this, R.string.notifications_search_access, Toast.LENGTH_LONG).show()
            awaitingKind = kind
            openNotificationAccess()
            return
        }
        // Перший дозвіл — основний: «вибрані фото й відео», дані для відео, не відкривають самих фото.
        val permissions = globalPermissions(kind)
        if (permissions.isNotEmpty() &&
            ContextCompat.checkSelfPermission(this, permissions.first()) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingGlobalKind = kind
            globalPermission.launch(permissions.toTypedArray())
            return
        }
        app.globalSearchSettings.set(kind, true)
    }

    /** Профілі в налаштуваннях: список → редагування → вибір програми. */
    @Composable
    private fun ProfileSettings(page: SettingsNav, onNavigate: (SettingsNav) -> Unit) {
        val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
        val activeId by profileViewModel.activeId.collectAsStateWithLifecycle()
        val apps by profileViewModel.apps.collectAsStateWithLifecycle()
        val scopes by app.searchScopes.scopes.collectAsStateWithLifecycle()
        val globalModes by app.searchScopes.globalModes.collectAsStateWithLifecycle()
        val notifFilter by app.searchScopes.notificationFilter.collectAsStateWithLifecycle()
        val notifExtras by app.searchScopes.notificationExtras.collectAsStateWithLifecycle()
        val displays by app.profileDisplay.displays.collectAsStateWithLifecycle()
        val notificationAccess by notificationAccessState.collectAsStateWithLifecycle()
        // Доступ «Не турбувати» перевіряємо щоразу при поверненні з налаштувань Android.
        val dndAccess by dndAccessState.collectAsStateWithLifecycle()
        when (page) {
            SettingsNav.BatteryApps -> AppListScreen(
                title = stringResource(R.string.battery_apps_open),
                note = stringResource(R.string.battery_profiles_note),
                apps = apps,
                iconFor = iconCache::load,
                onBack = { onNavigate(SettingsNav.Battery) },
                onPick = { BatterySettings.openForApp(this, it.packageName) },
            )
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
                    hasDndAccess = dndAccess,
                    onGrantDnd = { startOrToast(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                    searchScope = scopes[profile.id] ?: SearchScope.ALL,
                    onSearchScopeChange = { app.searchScopes.set(profile.id, it) },
                    globalMode = globalModes[profile.id] ?: defaultGlobalMode(scopes[profile.id] ?: SearchScope.ALL),
                    onGlobalModeChange = { app.searchScopes.setGlobalMode(profile.id, it) },
                    notificationFilter = profile.id in notifFilter,
                    onNotificationFilterChange = { app.searchScopes.setNotificationFilter(profile.id, it) },
                    hasNotificationAccess = notificationAccess,
                    onGrantNotificationAccess = ::openNotificationAccess,
                    display = displays[profile.id] ?: app.profileDisplay.of(profile.id),
                    globalGlance = globalGlanceParts(),
                    onDisplayChange = { app.profileDisplay.set(profile.id, it) },
                    onApplyDisplayToAll = {
                        app.profileDisplay.setAll(profiles.map { it.id }, displays[profile.id] ?: app.profileDisplay.of(profile.id))
                        toast(R.string.display_applied)
                    },
                    notificationExtras = notifExtras[profile.id].orEmpty()
                        .mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
                        .sortedBy { it.label.lowercase() },
                    onAddNotificationExtras = { onNavigate(SettingsNav.PickNotificationApps(profile.id)) },
                    onRemoveNotificationExtra = { removed ->
                        app.searchScopes.setNotificationExtras(profile.id, notifExtras[profile.id].orEmpty() - removed.packageName)
                    },
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
                            val keys = picked.fold(profile.pinnedAppKeys) { keys, app -> PinnedEdits.add(keys, app.key) }
                            profileViewModel.update(profile.copy(pinnedAppKeys = keys))
                        }
                        onNavigate(SettingsNav.Edit(page.profileId))
                    },
                )
            }
            is SettingsNav.PickNotificationApps -> {
                val profile = profiles.firstOrNull { it.id == page.profileId }
                // Програми профілю й уже дозволені — не пропонуємо.
                val allowed = profile?.pinnedAppKeys.orEmpty().map { it.substringBefore('/') }.toSet() +
                    notifExtras[page.profileId].orEmpty()
                AppPickerScreen(
                    apps = apps,
                    alreadyPinned = apps.filter { it.packageName in allowed }.map { it.key }.toSet(),
                    iconFor = iconCache::load,
                    onBack = { onNavigate(SettingsNav.Edit(page.profileId)) },
                    onPick = { picked ->
                        app.searchScopes.setNotificationExtras(
                            page.profileId,
                            notifExtras[page.profileId].orEmpty() + picked.map { it.packageName },
                        )
                        onNavigate(SettingsNav.Edit(page.profileId))
                    },
                )
            }
            // Невідома сторінка не лишає порожній чорний екран без «Назад» — повертаємось на головну налаштувань.
            else -> LaunchedEffect(page) { onNavigate(SettingsNav.Main) }
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
        UiMessage.CONDITION_NOT_MET -> R.string.condition_not_met
        UiMessage.NOT_A_PLAYER -> R.string.not_a_player
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        repository.refresh()
        ensureBlackWallpaper()
        enableAwaitedKind()
        app.privateSpace.refresh()
        bluetoothAllowedState.value = bluetoothGranted()
        overlayAllowedState.value = Settings.canDrawOverlays(this)
        resumeTick.value++
        wasStopped = false
        dndAccessState.value = app.profileApplier.hasDndAccess()
        notificationAccessState.value = ProfileNotificationFilter.hasAccess(this)
    }

    // Натискання Home, коли BlackLaunch уже на екрані, повертає чистий чорний екран.
    // Повернення з іншої програми жестом «Додому» — теж Home, але запит не стираємо:
    // він повернеться свайпом угору протягом хвилини.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleAssist(intent)) return
        if (handleRunSteps(intent)) return
        monitorOpen.value = false
        voice.cancel()
        if (!wasStopped) viewModel.forgetSearch()
    }

    /** Автоматизація: кроки, яким потрібен екран, — після дотику до сповіщення чи одразу. */
    private fun handleRunSteps(intent: Intent?): Boolean {
        val steps = intent?.getStringArrayExtra(EXTRA_RUN_STEPS) ?: return false
        intent.removeExtra(EXTRA_RUN_STEPS)
        viewModel.runSteps(steps.toList())
        return true
    }

    /**
     * BlackLaunch як «цифровий асистент» (довге натискання кнопки живлення / жест):
     * відкриває пошук і одразу слухає.
     */
    private fun handleAssist(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_ASSIST && intent?.action != Intent.ACTION_VOICE_COMMAND) return false
        viewModel.forgetSearch()
        viewModel.openSearch()
        if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) {
            // Екран заблоковано: показуємо лише мікрофон поверх блокування (Android 8.1+), а виконуємо — після розблокування.
            // Старіші Android так не вміють — тоді мовчимо, а не пікаємо марно.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return true
            setShowWhenLocked(true)
            lockedVoice.value = true
        }
        if (voice.state.value == VoiceState.Idle) onMicClick()
        return true
    }

    /** Голос поверх заблокованого екрана: зараз показано (лише чорний екран із мікрофоном). */
    private val lockedVoice = MutableStateFlow(false)

    /** Почуте вже є — чекаємо, поки людина розблокує телефон. */
    private var awaitingUnlock = false

    /** Почуте з-під блокування виконуємо лише після розблокування (відбиток, PIN) — інакше нічого. */
    private fun onVoiceResult(texts: List<String>) {
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if ((!lockedVoice.value && !locked) || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            viewModel.onVoiceResult(texts)
            return
        }
        // Телефон заблоковано — жодної дії без розблокування.
        setShowWhenLocked(true)
        lockedVoice.value = true
        awaitingUnlock = true
        val keyguard = getSystemService(KeyguardManager::class.java)
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                endLockedVoice()
                viewModel.openSearch()
                viewModel.onVoiceResult(texts)
            }

            override fun onDismissCancelled() = endLockedVoice()

            override fun onDismissError() = endLockedVoice()
        })
    }

    private fun replyText(reply: VoiceReply): String = when (reply) {
        is VoiceReply.Result -> getString(R.string.reply_result, reply.value)
        is VoiceReply.Opening -> getString(R.string.reply_opening, reply.label)
        is VoiceReply.Playing -> getString(R.string.reply_playing, reply.label)
        is VoiceReply.NotPlayer -> getString(R.string.reply_not_player, reply.label)
        VoiceReply.Choose -> getString(R.string.reply_choose)
        VoiceReply.SearchShown -> getString(R.string.reply_search)
        is VoiceReply.Profile -> getString(R.string.reply_profile, reply.name)
        VoiceReply.ProfileMissing -> getString(R.string.reply_profile_missing)
    }

    private fun endLockedVoice() {
        awaitingUnlock = false
        if (!lockedVoice.value) return
        lockedVoice.value = false
        if (voice.state.value != VoiceState.Idle) voice.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(false)
        viewModel.forgetSearch()
    }

    // Перейшли в іншу програму — пошук лишається відкритим з тими самими результатами
    // (клавіатуру ховає сам екран пошуку). Повернулись протягом хвилини — усе на місці.
    override fun onStop() {
        super.onStop()
        app.homeVisible = false
        // Поверх блокування вікно живе лише поки на екрані.
        if (!awaitingUnlock) endLockedVoice()
        if (!isChangingConfigurations) {
            voice.cancel()
            actionRunner.onStop()
            pendingCall = null
            wasStopped = true
            stoppedAt = SystemClock.elapsedRealtime()
        }
    }

    // Не було довше хвилини — повертаємось на чистий чорний екран, без анімації.
    override fun onStart() {
        super.onStart()
        app.homeVisible = true
        actionRunner.onStart()
        if (wasStopped && SystemClock.elapsedRealtime() - stoppedAt > LauncherViewModel.RESTORE_QUERY_MS) {
            instantSearchClose = true
            viewModel.forgetSearch()
        }
    }

    private var stoppedAt = 0L

    /** Свій вибір профілю, інакше — загальний. */
    private fun glanceParts() = activeDisplay().ownGlance ?: globalGlanceParts()

    private fun globalGlanceParts() = app.appearanceSettings.appearance.value.let {
        GlanceParts(clock = it.glanceClock, alarm = it.glanceAlarm, event = it.glanceEvent, battery = it.glanceBattery)
    }

    /** Екран і пошук активного профілю. */
    private fun activeDisplay() = app.profileDisplay.of(app.activeProfile.value?.id)

    private val glance = MutableStateFlow<GlanceInfo?>(null)
    private val dndAccessState = MutableStateFlow(false)
    private val notificationAccessState = MutableStateFlow(false)

    /** Відкрити налаштування лаунчера з пошуку: сторінка й лічильник запитів. */
    private var requestedSettingsPage: SettingsNav? = null
    private val settingsRequest = MutableStateFlow(0)

    /** Чорний головний екран без відкритого пошуку чи налаштувань. */
    private var homeIdle = true

    // Повністю чорний екран: час, мережа й батарея сховані (свайп від верхнього краю показує їх на мить).
    private fun applyStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (homeIdle && activeDisplay().hideStatusBar) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStatusBar()
    }

    /** BlackLaunch був прихований (інша програма) — до наступного onResume. */
    private var wasStopped = false

    /** Закрити пошук без анімації; скидається з наступним відкриттям пошуку. */
    private var instantSearchClose by mutableStateOf(false)

    override fun onDestroy() {
        voice.destroy()
        super.onDestroy()
    }

    companion object {
        /** Кроки автоматизації, яким потрібен екран (відкрити програму тощо). */
        const val EXTRA_RUN_STEPS = "com.blacklaunch.launcher.RUN_STEPS"
        private const val PROFILE_BANNER_MS = 1_500L
        private const val THUMB_CACHE = 200
        private const val THUMB_PX = 256
        private const val LOCKED_RESULT_GRACE_MS = 400L
    }
}
