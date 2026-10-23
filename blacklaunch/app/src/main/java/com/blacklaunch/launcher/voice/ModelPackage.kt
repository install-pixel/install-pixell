package com.blacklaunch.launcher.voice

/**
 * Розпізнавання вмісту zip-архіву моделі без Android-залежностей (тестується JVM-тестами).
 * Мова береться з мітки `blacklaunch-model.json`; якщо її немає — з назви теки моделі
 * (наприклад, `vosk-model-small-uk-v3-small`).
 */
object ModelPackage {
    const val MARKER = "blacklaunch-model.json"

    private val markerLanguage = Regex("\"language\"\\s*:\\s*\"([a-zA-Z]{2})(?:-[a-zA-Z]{2})?\"")
    private val nameLanguage = Regex("(?:^|[-_/])(uk|pl|en)(?:[-_]|$)", RegexOption.IGNORE_CASE)

    fun detectLanguage(marker: String?, paths: List<String>): String? {
        marker?.let { markerLanguage.find(it)?.groupValues?.get(1) }?.let { return tagFor(it) }
        val root = modelRoot(paths) ?: return null
        val folder = root.trimEnd('/').substringAfterLast('/')
        return nameLanguage.find(folder)?.groupValues?.get(1)?.let(::tagFor)
    }

    /** Тека, що містить модель Vosk (у ній є `am/` або `conf/model.conf`). "" — корінь архіву. */
    fun modelRoot(paths: List<String>): String? {
        val normalized = paths.map { it.replace('\\', '/').trimStart('/') }
        val markers = normalized.mapNotNull { path ->
            when {
                path.endsWith("conf/model.conf") -> path.removeSuffix("conf/model.conf")
                "am/" in path -> path.substringBefore("am/")
                else -> null
            }
        }
        return markers.minByOrNull { it.length }
    }

    /** Захист від шляхів на кшталт `../../x` в архіві. */
    fun isSafe(path: String): Boolean {
        val p = path.replace('\\', '/')
        return !p.startsWith("/") && p.split('/').none { it == ".." }
    }

    private fun tagFor(language: String): String? = when (language.lowercase()) {
        "uk" -> VoiceLanguages.UKRAINIAN
        "pl" -> VoiceLanguages.POLISH
        "en" -> VoiceLanguages.ENGLISH
        else -> null
    }
}
