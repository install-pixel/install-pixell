package com.blacklaunch.launcher

import android.app.Application
import androidx.room.withTransaction
import com.blacklaunch.launcher.apps.AppRepository
import com.blacklaunch.launcher.apps.PrefsLaunchHistory
import com.blacklaunch.launcher.data.AppDatabase
import com.blacklaunch.launcher.global.AndroidGlobalSearch
import com.blacklaunch.launcher.global.GlobalSearchSettings
import com.blacklaunch.launcher.global.SettingsTitles
import com.blacklaunch.launcher.profiles.DefaultProfiles
import com.blacklaunch.launcher.profiles.PrefsActiveProfileStore
import com.blacklaunch.launcher.profiles.PrefsHomeLayoutStore
import com.blacklaunch.launcher.profiles.ProfileRepository
import com.blacklaunch.launcher.search.PrefsLearnedQueries
import com.blacklaunch.launcher.search.UserAliasStore
import com.blacklaunch.launcher.settings.AppearanceSettings
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
}
