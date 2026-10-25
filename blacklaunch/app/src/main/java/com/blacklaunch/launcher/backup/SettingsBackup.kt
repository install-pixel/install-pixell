package com.blacklaunch.launcher.backup

import com.blacklaunch.launcher.BlackLaunchApp
import com.blacklaunch.launcher.automation.InMemoryAutomationStore
import com.blacklaunch.launcher.commands.CustomCommand
import com.blacklaunch.launcher.global.GroupKind
import com.blacklaunch.launcher.profiles.GlobalMode
import com.blacklaunch.launcher.profiles.LauncherProfile
import com.blacklaunch.launcher.profiles.NotificationMode
import com.blacklaunch.launcher.profiles.ProfileDisplay
import com.blacklaunch.launcher.profiles.SearchScope
import com.blacklaunch.launcher.profiles.SoundMode
import com.blacklaunch.launcher.voice.VoiceActionKind
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** Підсумок відновлення: [waitingApps] — ще не встановлені програми, що з'являться самі після встановлення. */
data class RestoreResult(val profiles: Int, val waitingApps: Int)

/**
 * Резервна копія налаштувань Black Glass в одному файлі JSON: профілі (програми, розкладка,
 * звук, «Не турбувати», екран і пошук, фільтр сповіщень), власні назви, «Мої команди» й автоматизації,
 * приховані програми, власні значки, оформлення, «Короткий огляд» і голос. Історія запусків і запитів
 * не зберігається — вона особиста й накопичується сама. Без мережі й без дозволів на пам'ять.
 */
class SettingsBackup(private val app: BlackLaunchApp) {

    suspend fun export(versionName: String): String {
        val profiles = app.profileRepository.profiles.first()
        val layouts = app.homeLayoutStore.layouts.value
        val scopes = app.searchScopes
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("app", versionName)
            put("activeProfile", app.activeProfile.value?.id)
            put("profiles", JSONArray(profiles.map { p ->
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("sound", p.soundMode.name)
                    put("notification", p.notificationMode.name)
                    put("pinned", JSONArray(p.pinnedAppKeys))
                    put("layout", JSONObject(layouts[p.id].orEmpty()))
                    put("display", app.profileDisplay.of(p.id).encode())
                    scopes.scopes.value[p.id]?.let { put("searchScope", it.name) }
                    scopes.globalModes.value[p.id]?.let { put("globalMode", it.name) }
                    put("notificationFilter", p.id in scopes.notificationFilter.value)
                    put("notificationExtras", JSONArray(scopes.notificationExtras.value[p.id].orEmpty().toList()))
                }
            }))
            put("aliases", JSONObject(app.userAliasStore.aliases.value.mapValues { JSONArray(it.value.toList()) }))
            put("commands", JSONArray(app.customCommands.commands.value.map { c ->
                JSONObject().put("id", c.id).put("phrase", c.phrase).put("steps", JSONArray(c.steps))
            }))
            put("hidden", JSONArray(app.hiddenApps.hidden.value.toList()))
            put("ownIcons", JSONArray(app.ownIcons.keys.value.toList()))
            val look = app.appearanceSettings.appearance.value
            put("appearance", JSONObject().apply {
                put("autoBlackWallpaper", look.autoBlackWallpaper)
                put("wallpaperTarget", look.wallpaperTarget.name)
                put("monochromeIcons", look.monochromeIcons)
                put("profileSwipe", look.profileSwipe)
                put("haptics", look.haptics)
                put("glanceClock", look.glanceClock)
                put("glanceAlarm", look.glanceAlarm)
                put("glanceEvent", look.glanceEvent)
                put("glanceBattery", look.glanceBattery)
            })
            val voice = app.voiceActionSettings
            put("voiceActions", JSONObject().apply {
                put("enabled", JSONArray(voice.enabled.value.map { it.name }))
                put("directCall", voice.directCall.value)
                put("speakAnswers", voice.speakAnswers.value)
                voice.ttsEngine.value?.let { put("ttsEngine", it) }
            })
            put("globalKinds", JSONArray(app.globalSearchSettings.enabled.value.map { it.name }))
            put("offlineVoice", app.voiceSettings.mode.value.name)
            put("automations", InMemoryAutomationStore.encode(app.automations.automations.value))
        }.toString(2)
    }

    /** Замінює налаштування збереженими. Програм, яких на телефоні немає, — пропускає. */
    suspend fun restore(text: String): RestoreResult {
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "Not a Black Glass backup" }
        val remap = AppKeyRemap(app.appRepository.apps.value.map { it.key })
        val scopes = app.searchScopes

        val imported = root.getJSONArray("profiles").objects()
        require(imported.isNotEmpty()) { "No profiles" }
        imported.forEachIndexed { index, p ->
            val id = p.getString("id")
            val profile = LauncherProfile(
                id = id,
                name = p.getString("name"),
                pinnedAppKeys = remap.mapAllOrKeep(p.getJSONArray("pinned").strings()),
                soundMode = enumOr(p.optString("sound"), SoundMode.NORMAL),
                notificationMode = enumOr(p.optString("notification"), NotificationMode.SYSTEM_DEFAULT),
            )
            app.profileRepository.save(profile, index).getOrThrow()
            val layout = p.optJSONObject("layout")?.let { obj ->
                obj.keys().asSequence().map { key -> remap.mapOrKeep(key) to obj.getInt(key) }.toMap()
            }.orEmpty()
            app.homeLayoutStore.save(id, layout)
            ProfileDisplay.decode(p.optString("display"))?.let { app.profileDisplay.set(id, it) }
            p.optString("searchScope").takeIf { it.isNotEmpty() }?.let { scopes.set(id, enumOr(it, SearchScope.ALL)) }
            p.optString("globalMode").takeIf { it.isNotEmpty() }?.let { scopes.setGlobalMode(id, enumOr(it, GlobalMode.ON)) }
            scopes.setNotificationFilter(id, p.optBoolean("notificationFilter"))
            scopes.setNotificationExtras(id, p.optJSONArray("notificationExtras")?.strings().orEmpty().toSet())
        }
        // Профілі, яких немає в копії, прибираємо — після збереження нових, тож останній не зникне.
        val keep = imported.map { it.getString("id") }.toSet()
        app.profileRepository.profiles.first().filter { it.id !in keep }.forEach { app.profileRepository.delete(it.id) }
        root.optString("activeProfile").takeIf { it in keep }?.let { app.profileRepository.setActive(it) }

        root.optJSONObject("aliases")?.let { aliases ->
            app.userAliasStore.aliases.value.forEach { (pkg, names) -> names.forEach { app.userAliasStore.remove(pkg, it) } }
            aliases.keys().forEach { pkg -> aliases.getJSONArray(pkg).strings().forEach { app.userAliasStore.add(pkg, it) } }
        }
        root.optJSONArray("commands")?.let { commands ->
            app.customCommands.commands.value.forEach { app.customCommands.delete(it.id) }
            commands.objects().forEach { c ->
                app.customCommands.save(CustomCommand(c.getString("id"), c.getString("phrase"), c.getJSONArray("steps").strings()))
            }
        }
        root.optJSONArray("hidden")?.let { hidden ->
            app.hiddenApps.hidden.value.forEach(app.hiddenApps::show)
            remap.mapAllOrKeep(hidden.strings()).forEach(app.hiddenApps::hide)
        }
        root.optJSONArray("ownIcons")?.let { icons ->
            app.ownIcons.keys.value.forEach { app.ownIcons.set(it, false) }
            remap.mapAllOrKeep(icons.strings()).forEach { app.ownIcons.set(it, true) }
        }
        root.optJSONObject("appearance")?.let { a ->
            app.appearanceSettings.update {
                it.copy(
                    autoBlackWallpaper = a.optBoolean("autoBlackWallpaper", it.autoBlackWallpaper),
                    wallpaperTarget = enumOr(a.optString("wallpaperTarget"), it.wallpaperTarget),
                    monochromeIcons = a.optBoolean("monochromeIcons", it.monochromeIcons),
                    profileSwipe = a.optBoolean("profileSwipe", it.profileSwipe),
                    haptics = a.optBoolean("haptics", it.haptics),
                    glanceClock = a.optBoolean("glanceClock", it.glanceClock),
                    glanceAlarm = a.optBoolean("glanceAlarm", it.glanceAlarm),
                    glanceEvent = a.optBoolean("glanceEvent", it.glanceEvent),
                    glanceBattery = a.optBoolean("glanceBattery", it.glanceBattery),
                )
            }
        }
        root.optJSONObject("voiceActions")?.let { v ->
            val enabled = v.optJSONArray("enabled")?.strings()?.toSet()
            if (enabled != null) VoiceActionKind.entries.forEach { app.voiceActionSettings.set(it, it.name in enabled) }
            app.voiceActionSettings.setDirectCall(v.optBoolean("directCall"))
            app.voiceActionSettings.setSpeakAnswers(v.optBoolean("speakAnswers"))
            app.voiceActionSettings.setTtsEngine(v.optString("ttsEngine").takeIf { it.isNotEmpty() })
        }
        root.optJSONArray("globalKinds")?.strings()?.toSet()?.let { kinds ->
            GroupKind.entries.forEach { app.globalSearchSettings.set(it, it.name in kinds) }
        }
        root.optString("offlineVoice").takeIf { it.isNotEmpty() }?.let {
            app.voiceSettings.setMode(enumOr(it, app.voiceSettings.mode.value))
        }
        // Автоматизації посилаються на «Мої команди» за id — команди відновлено вище з тими самими id.
        if (root.has("automations")) {
            app.automations.automations.value.forEach { app.automations.delete(it.id) }
            InMemoryAutomationStore.decode(root.optString("automations")).forEach(app.automations::save)
        }
        return RestoreResult(imported.size, remap.waiting)
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    private companion object {
        const val FORMAT = "black-glass-backup"
        const val VERSION = 1
    }
}
