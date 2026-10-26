package com.blacklaunch.launcher

import kotlinx.coroutines.flow.first
import android.app.Application
import android.os.Build
import android.widget.Toast
import com.blacklaunch.launcher.profiles.PrefsSearchScopes
import com.blacklaunch.launcher.profiles.SearchScope
import com.blacklaunch.launcher.profiles.ProfileApplier
import com.blacklaunch.launcher.profiles.PrefsProfileDisplay
import com.blacklaunch.launcher.profiles.ProfileDisplay
import com.blacklaunch.launcher.profiles.LauncherProfile
import com.blacklaunch.launcher.profiles.ProfileNotificationFilter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.blacklaunch.launcher.diagnostics.CrashLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.room.withTransaction
import com.blacklaunch.launcher.apps.AppRepository
import com.blacklaunch.launcher.apps.OwnIcons
import com.blacklaunch.launcher.apps.PrefsLaunchHistory
import com.blacklaunch.launcher.commands.PrefsCustomCommandStore
import com.blacklaunch.launcher.data.AppDatabase
import com.blacklaunch.launcher.automation.AutomationRunner
import com.blacklaunch.launcher.commands.CommandConditions
import com.blacklaunch.launcher.automation.PrefsAutomationStore
import com.blacklaunch.launcher.global.AndroidGlobalSearch
import com.blacklaunch.launcher.global.GlobalSearchSettings
import com.blacklaunch.launcher.global.SettingsTitles
import com.blacklaunch.launcher.profiles.DefaultProfiles
import com.blacklaunch.launcher.profiles.PrefsActiveProfileStore
import com.blacklaunch.launcher.profiles.PrefsHomeLayoutStore
import com.blacklaunch.launcher.profiles.ProfileRepository
import com.blacklaunch.launcher.search.PrefsLearnedQueries
import com.blacklaunch.launcher.search.PrefsHiddenApps
import com.blacklaunch.launcher.search.PrefsRecentQueries
import com.blacklaunch.launcher.search.UserAliasStore
import com.blacklaunch.launcher.settings.AppearanceSettings
import com.blacklaunch.launcher.settings.VoiceActionSettings
import com.blacklaunch.launcher.settings.VoiceSettings
import com.blacklaunch.launcher.system.PrivateSpace
import com.blacklaunch.launcher.system.SystemStats
import com.blacklaunch.launcher.settings.MonitorSettings
import com.blacklaunch.launcher.voice.VoiceModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BlackLaunchApp : Application() {
    /** Для коротких фонових записів, що мають пережити закриття екрана. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val appRepository: AppRepository by lazy { AppRepository(this) }
    val userAliasStore: UserAliasStore by lazy { UserAliasStore(this) }
    val launchHistory: PrefsLaunchHistory by lazy { PrefsLaunchHistory(this) }
    val appearanceSettings: AppearanceSettings by lazy { AppearanceSettings(this) }
    val voiceSettings: VoiceSettings by lazy { VoiceSettings(this) }
    val voiceActionSettings: VoiceActionSettings by lazy { VoiceActionSettings(this) }
    val customCommands: PrefsCustomCommandStore by lazy { PrefsCustomCommandStore(this) }
    val automations: PrefsAutomationStore by lazy { PrefsAutomationStore(this) }
    val automationRunner: AutomationRunner by lazy { AutomationRunner(this) }
    val stepScheduler: com.blacklaunch.launcher.automation.StepScheduler by lazy { com.blacklaunch.launcher.automation.StepScheduler(this) }
    val timeTriggers: com.blacklaunch.launcher.automation.TimeTriggers by lazy { com.blacklaunch.launcher.automation.TimeTriggers(this) }
    val automationSettings: com.blacklaunch.launcher.settings.AutomationSettings by lazy { com.blacklaunch.launcher.settings.AutomationSettings(this) }
    val smartProfiles: com.blacklaunch.launcher.automation.SmartProfiles by lazy { com.blacklaunch.launcher.automation.SmartProfiles(this) }
    val hourlyChime: com.blacklaunch.launcher.automation.HourlyChime by lazy { com.blacklaunch.launcher.automation.HourlyChime(this) }
    /** «Зосередження»: «Важкий вихід» режимів і фокус-таймер (етап 16). */
    val focus: com.blacklaunch.launcher.profiles.FocusMode by lazy { com.blacklaunch.launcher.profiles.FocusMode(this) }
    val messageReader: com.blacklaunch.launcher.automation.MessageReader by lazy { com.blacklaunch.launcher.automation.MessageReader(this) }

    /** Голос відповідей поза екраном (не обривається, коли відкривається програма) — рушій із налаштувань голосу. */
    val speech: com.blacklaunch.launcher.voice.VoiceActionRunner by lazy {
        com.blacklaunch.launcher.voice.VoiceActionRunner(this, onMessage = {}, ttsEngine = { voiceActionSettings.ttsEngine.value })
    }

    /**
     * «Команди — лише програми режиму»: чи може крок команди відкрити (чи ввімкнути музику в) програму [pkg] зараз —
     * лише якщо вона серед програм поточного режиму. Вимкнено — завжди так.
     */
    fun allowedInMode(pkg: String): Boolean {
        if (pkg == packageName) return true
        val profile = activeProfile.value ?: return true
        // «Лише програми профілю» в пошуку режиму обмежує й команди — без окремого перемикача.
        val profileOnly = searchScopes.scopes.value[profile.id] == SearchScope.PROFILE_ONLY
        if (!automationSettings.modeAppsOnly.value && !profileOnly) return true
        return profile.pinnedAppKeys.any { it.substringBefore('/') == pkg }
    }

    /** Головний екран Black Glass зараз видно (для «розумного перемикання» — чи треба виходити наперед). */
    @Volatile
    var homeVisible = false
    val commandConditions: CommandConditions by lazy { CommandConditions(this) { profileRepository.activeProfile.first()?.name } }
    val systemStats: SystemStats by lazy { SystemStats(this) }
    val monitorSettings: MonitorSettings by lazy { MonitorSettings(this) }
    val recentQueries: PrefsRecentQueries by lazy { PrefsRecentQueries(this) }
    val hiddenApps: PrefsHiddenApps by lazy { PrefsHiddenApps(this) }
    val ownIcons: OwnIcons by lazy { OwnIcons(this) }

    /** Екран і пошук для кожного профілю; без власного вибору — колишні загальні налаштування. */
    val profileDisplay: PrefsProfileDisplay by lazy {
        PrefsProfileDisplay(this) {
            appearanceSettings.appearance.value.let {
                ProfileDisplay(
                    glance = it.glance,
                    profileLabel = it.showProfileLabel,
                    hideStatusBar = it.hideStatusBar,
                    suggestions = it.suggestions,
                    recentQueries = it.recentQueries,
                    allAppsButton = it.allAppsButton,
                )
            }
        }
    }
    val voiceModelStore: VoiceModelStore by lazy { VoiceModelStore(this) }
    val learnedQueries: PrefsLearnedQueries by lazy { PrefsLearnedQueries(this) }
    val globalSearchSettings: GlobalSearchSettings by lazy { GlobalSearchSettings(this) }
    val homeLayoutStore: PrefsHomeLayoutStore by lazy { PrefsHomeLayoutStore(this) }
    /** Приватний простір Android 15+ (якщо є); зміна стану оновлює список програм. */
    val privateSpace: PrivateSpace by lazy { PrivateSpace(this) { appRepository.refresh() } }
    val globalSearch: AndroidGlobalSearch by lazy {
        AndroidGlobalSearch(
            context = this,
            appLabel = { pkg -> appRepository.apps.value.firstOrNull { it.packageName == pkg }?.label },
            settingTitle = { id -> getString(SettingsTitles.of(id)) },
        )
    }

    /** Локальний журнал вильотів (лише файл на телефоні). */
    val crashLog: CrashLog by lazy {
        CrashLog(File(filesDir, "crashes.txt")) {
            val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            "Black Glass $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · $time"
        }
    }

    val profileApplier: ProfileApplier by lazy { ProfileApplier(this) }
    val searchScopes: PrefsSearchScopes by lazy { PrefsSearchScopes(this) }

    /** Активний профіль — для фільтра сповіщень, що працює поза екраном. */
    val activeProfile: StateFlow<LauncherProfile?> by lazy {
        profileRepository.activeProfile.stateIn(appScope, SharingStarted.Eagerly, null)
    }

    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val profileRepository: ProfileRepository by lazy {
        ProfileRepository(
            dao = database.profileDao(),
            activeStore = PrefsActiveProfileStore(this),
            transaction = { block -> database.withTransaction { block() } },
        )
    }

    override fun onCreate() {
        super.onCreate()
        crashLog.install()
        automationRunner.registerLiveEvents()
        automationRunner.migrateOldAutomations()
        // Події «за часом»: будильники заново після кожної зміни команд (і при запуску).
        appScope.launch { customCommands.commands.collect { timeTriggers.rescheduleAll() } }
        appScope.launch { automationSettings.hourlyChime.collect { hourlyChime.reschedule() } }
        appScope.launch { watchProfileSwitches() }
        // Фокус-таймер пережив перезапуск: минув — завершуємо (назад у попередній режим), інакше — будильник знову.
        focus.restore()
        appScope.launch { applyProfileOnSwitch() }
        // Змінився профіль, його програми чи фільтр — перевірити сповіщення ще раз.
        appScope.launch {
            combine(activeProfile, searchScopes.notificationFilter, searchScopes.notificationExtras) { profile, _, _ -> profile }
                .collect { ProfileNotificationFilter.refreshNow() }
        }
        appScope.launch {
            profileRepository.ensureDefaults(
                DefaultProfiles.create(
                    workName = getString(R.string.profile_work),
                    homeName = getString(R.string.profile_home),
                    restName = getString(R.string.profile_rest),
                ),
            )
        }
    }

    /**
     * Перемкнули профіль (свайп, плитка, голос, пошук) або змінили звук активного —
     * застосовуємо звук і «Не турбувати». Під час запуску нічого не чіпаємо.
     */
    private suspend fun applyProfileOnSwitch() {
        var started = false
        profileRepository.activeProfile
            .filterNotNull()
            .map { Triple(it.id, it.soundMode, it.notificationMode) to it }
            .distinctUntilChangedBy { it.first }
            .collect { (_, profile) ->
                if (!started) {
                    started = true
                    return@collect
                }
                val result = profileApplier.apply(profile)
                if (!result.ok) {
                    withContext(Dispatchers.Main) {
                        val text = if (result.needsAccess) R.string.profile_needs_dnd else R.string.profile_sound_failed
                        Toast.makeText(this@BlackLaunchApp, getString(text, result.profileName), Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    /**
     * Перемкнули режим (будь-де: екран, плитка, голос, команда): події «Увімкнено/Вимкнено режим» і «розумне перемикання».
     * Перший стан після запуску — не перемикання.
     */
    private suspend fun watchProfileSwitches() {
        var previous: LauncherProfile? = null
        var started = false
        profileRepository.activeProfile.collect { profile ->
            if (profile == null) return@collect
            val from = previous
            if (started && from?.id != profile.id) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    smartProfiles.onSwitch(from, profile)
                    from?.let { automationRunner.fire(com.blacklaunch.launcher.automation.TriggerKind.PROFILE_OFF, it.name) }
                    automationRunner.fire(com.blacklaunch.launcher.automation.TriggerKind.PROFILE_ON, profile.name)
                }
            }
            started = true
            previous = profile
        }
    }
}
