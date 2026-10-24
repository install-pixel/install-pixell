package com.blacklaunch.launcher

import android.app.Application
import android.os.Build
import android.widget.Toast
import com.blacklaunch.launcher.profiles.PrefsSearchScopes
import com.blacklaunch.launcher.profiles.ProfileApplier
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
import com.blacklaunch.launcher.apps.PrefsLaunchHistory
import com.blacklaunch.launcher.commands.PrefsCustomCommandStore
import com.blacklaunch.launcher.data.AppDatabase
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
    val recentQueries: PrefsRecentQueries by lazy { PrefsRecentQueries(this) }
    val hiddenApps: PrefsHiddenApps by lazy { PrefsHiddenApps(this) }
    val voiceModelStore: VoiceModelStore by lazy { VoiceModelStore(this) }
    val learnedQueries: PrefsLearnedQueries by lazy { PrefsLearnedQueries(this) }
    val globalSearchSettings: GlobalSearchSettings by lazy { GlobalSearchSettings(this) }
    val homeLayoutStore: PrefsHomeLayoutStore by lazy { PrefsHomeLayoutStore(this) }
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
        appScope.launch { applyProfileOnSwitch() }
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
                        Toast.makeText(this@BlackLaunchApp, getString(text, result.profileName), Toast.LENGTH_LONG).show()
                    }
                }
            }
    }
}
