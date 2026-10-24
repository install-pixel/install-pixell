package com.blacklaunch.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.ComponentName
import android.content.ContentUris
import android.provider.ContactsContract
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import com.blacklaunch.launcher.ui.LocalHaptics
import com.blacklaunch.launcher.ui.Haptics
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
import com.blacklaunch.launcher.global.GlobalResult
import com.blacklaunch.launcher.global.GroupKind
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.blacklaunch.launcher.system.GlanceInfo
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
                    showSuggestions = { app.appearanceSettings.appearance.value.suggestions },
                    showRecent = { app.appearanceSettings.appearance.value.recentQueries },
                    hiddenApps = app.hiddenApps.hidden,
                    searchScopes = app.searchScopes.scopes,
                    globalSearchOff = app.searchScopes.globalOff,
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
                for (action in viewModel.voiceActions) runVoiceAction(action)
            }
        }
        // «Короткий огляд» оновлюється раз на хвилину лише поки BlackLaunch на екрані.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    if (app.appearanceSettings.appearance.value.glance) {
                        glance.value = withContext(Dispatchers.IO) { Glance.read(this@MainActivity) }
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
                    profileBanner.value = name
                    delay(PROFILE_BANNER_MS)
                    if (profileBanner.value == name) profileBanner.value = null
                }
            }
        }
        // Запущено як «цифровий асистент» — одразу пошук і мікрофон.
        if (savedInstanceState == null) handleAssist(intent)
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
            val globalKinds by app.globalSearchSettings.enabled.collectAsStateWithLifecycle()
            val crashLog by app.crashLog.text.collectAsStateWithLifecycle()
            val voiceActions by app.voiceActionSettings.enabled.collectAsStateWithLifecycle()
            val directCall by app.voiceActionSettings.directCall.collectAsStateWithLifecycle()
            val speakAnswers by app.voiceActionSettings.speakAnswers.collectAsStateWithLifecycle()
            val ttsEngine by app.voiceActionSettings.ttsEngine.collectAsStateWithLifecycle()
            val customCommands by app.customCommands.commands.collectAsStateWithLifecycle()
            val allApps by viewModel.allApps.collectAsStateWithLifecycle()
            val glanceInfo by glance.collectAsStateWithLifecycle()
            // Увімкнули «Короткий огляд» — показуємо одразу, не чекаючи хвилини.
            LaunchedEffect(appearance.glance) {
                if (appearance.glance) glance.value = withContext(Dispatchers.IO) { Glance.read(this@MainActivity) }
            }
            val hiddenKeys by app.hiddenApps.hidden.collectAsStateWithLifecycle()
            val installedApps by repository.apps.collectAsStateWithLifecycle()
            BlackLaunchTheme {
            CompositionLocalProvider(LocalHaptics provides Haptics(LocalView.current, appearance.haptics)) {
                BlackHomeScreen(
                    state = state,
                    home = home,
                    profileBanner = banner,
                    onSwitchProfile = viewModel::switchProfile,
                    onMoveApp = viewModel::moveApp,
                    onRemoveApp = viewModel::unpin,
                    profileSwipe = appearance.profileSwipe,
                    showProfileLabel = appearance.showProfileLabel,
                    glance = if (appearance.glance) glanceInfo else null,
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
                    allApps = if (appearance.allAppsButton) allApps else emptyList(),
                    onContactAction = ::contactAction,
                    iconFor = iconCache::load,
                    voiceState = voiceState,
                    onMicClick = ::onMicClick,
                    settingsScreen = { dismiss ->
                        var page by remember { mutableStateOf<SettingsNav>(SettingsNav.Main) }
                        when (val current = page) {
                            SettingsNav.Main, SettingsNav.HomeScreen, SettingsNav.Appearance, SettingsNav.Search,
                            SettingsNav.Voice, SettingsNav.VoiceActions, SettingsNav.CustomCommands, SettingsNav.Battery, SettingsNav.About,
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
                                customCommands = customCommands,
                                onSaveCustomCommand = app.customCommands::save,
                                onDeleteCustomCommand = app.customCommands::delete,
                                onOpenDefaultApps = { startOrToast(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) },
                                crashLog = crashLog,
                                onCopyCrashLog = ::copyCrashLog,
                                onClearCrashLog = app.crashLog::clear,
                                onOpenAppInfo = {
                                    awaitingSmsPermission = true
                                    openAppInfo(packageName)
                                },
                                onDismiss = dismiss,
                            )
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
            }
        }
    }

    private val actionRunner by lazy {
        VoiceActionRunner(
            context = this,
            onMessage = { message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show() },
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
                Toast.makeText(this, getString(R.string.app_not_found, action.app), Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, getString(R.string.voice_contact_not_found, name), Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, getString(R.string.voice_contact_not_found, contact.name), Toast.LENGTH_LONG).show()
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
            is GlobalResult.Setting -> startOrToast(Intent(result.action), fallback = Intent(Settings.ACTION_SETTINGS))
            is GlobalResult.Shortcut -> if (!app.globalSearch.startShortcut(result)) toast(R.string.launch_failed)
            is GlobalResult.Contact -> startOrToast(Intent(Intent.ACTION_VIEW, Uri.parse(result.lookupUri)))
            is GlobalResult.Event -> startOrToast(Intent(Intent.ACTION_VIEW, app.globalSearch.eventUri(result)))
            is GlobalResult.Sms -> startOrToast(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${result.address}")))
        }
    }

    /** Копіює журнал вильотів, щоб вставити його в чат. */
    private fun copyCrashLog() {
        val text = app.crashLog.text.value ?: return
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Black Glass crash log", text))
        toast(R.string.crash_log_copied)
    }

    private fun openAppInfo(pkg: String) =
        startOrToast(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))

    /**
     * Дозвіл SMS дали в системних налаштуваннях (обмежені налаштування Android 15+) —
     * після повернення вмикаємо пошук у SMS сам, без повторного натискання.
     */
    private var awaitingSmsPermission = false

    private fun enableSmsIfGranted() {
        if (!awaitingSmsPermission) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            awaitingSmsPermission = false
            app.globalSearchSettings.set(GroupKind.SMS, true)
        }
    }

    private fun startOrToast(intent: Intent, fallback: Intent? = null) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (fallback != null) startOrToast(fallback) else toast(R.string.launch_failed)
        }
    }

    // Контакти, календар і SMS вмикаються лише з дозволом; без нього джерело лишається вимкненим.
    private var pendingGlobalKind: GroupKind? = null
    private val globalPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = pendingGlobalKind ?: return@registerForActivityResult
        pendingGlobalKind = null
        when {
            granted -> app.globalSearchSettings.set(kind, true)
            // Android 15+ блокує SMS-дозволи для програм не з Play, доки користувач не зніме обмеження.
            kind == GroupKind.SMS -> {
                Toast.makeText(this, R.string.sms_restricted, Toast.LENGTH_LONG).show()
                awaitingSmsPermission = true
                openAppInfo(packageName)
            }
            else -> toast(R.string.global_permission_denied)
        }
    }

    private fun setGlobalKind(kind: GroupKind, on: Boolean) {
        val permission = when (kind) {
            GroupKind.CONTACTS -> Manifest.permission.READ_CONTACTS
            GroupKind.CALENDAR -> Manifest.permission.READ_CALENDAR
            GroupKind.SMS -> Manifest.permission.READ_SMS
            else -> null
        }
        if (on && permission != null &&
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingGlobalKind = kind
            globalPermission.launch(permission)
            return
        }
        app.globalSearchSettings.set(kind, on)
    }

    /** Профілі в налаштуваннях: список → редагування → вибір програми. */
    @Composable
    private fun ProfileSettings(page: SettingsNav, onNavigate: (SettingsNav) -> Unit) {
        val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
        val activeId by profileViewModel.activeId.collectAsStateWithLifecycle()
        val apps by profileViewModel.apps.collectAsStateWithLifecycle()
        val scopes by app.searchScopes.scopes.collectAsStateWithLifecycle()
        val globalOff by app.searchScopes.globalOff.collectAsStateWithLifecycle()
        val notifFilter by app.searchScopes.notificationFilter.collectAsStateWithLifecycle()
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
                    globalSearch = profile.id !in globalOff,
                    onGlobalSearchChange = { app.searchScopes.setGlobal(profile.id, it) },
                    notificationFilter = profile.id in notifFilter,
                    onNotificationFilterChange = { app.searchScopes.setNotificationFilter(profile.id, it) },
                    hasNotificationAccess = notificationAccess,
                    onGrantNotificationAccess = ::openNotificationAccess,
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
            else -> Unit
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
        enableSmsIfGranted()
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
        voice.cancel()
        if (!wasStopped) viewModel.forgetSearch()
    }

    /**
     * BlackLaunch як «цифровий асистент» (довге натискання кнопки живлення / жест):
     * відкриває пошук і одразу слухає.
     */
    private fun handleAssist(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_ASSIST && intent?.action != Intent.ACTION_VOICE_COMMAND) return false
        viewModel.forgetSearch()
        viewModel.openSearch()
        if (voice.state.value == VoiceState.Idle) onMicClick()
        return true
    }

    // Перейшли в іншу програму — пошук лишається відкритим з тими самими результатами
    // (клавіатуру ховає сам екран пошуку). Повернулись протягом хвилини — усе на місці.
    override fun onStop() {
        super.onStop()
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
        actionRunner.onStart()
        if (wasStopped && SystemClock.elapsedRealtime() - stoppedAt > LauncherViewModel.RESTORE_QUERY_MS) {
            instantSearchClose = true
            viewModel.forgetSearch()
        }
    }

    private var stoppedAt = 0L

    private val glance = MutableStateFlow<GlanceInfo?>(null)
    private val dndAccessState = MutableStateFlow(false)
    private val notificationAccessState = MutableStateFlow(false)

    /** Чорний головний екран без відкритого пошуку чи налаштувань. */
    private var homeIdle = true

    // Повністю чорний екран: час, мережа й батарея сховані (свайп від верхнього краю показує їх на мить).
    private fun applyStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (homeIdle && app.appearanceSettings.appearance.value.hideStatusBar) {
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

    private companion object {
        const val PROFILE_BANNER_MS = 1_000L
    }
}
