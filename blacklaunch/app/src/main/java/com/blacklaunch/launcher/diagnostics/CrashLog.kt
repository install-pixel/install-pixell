package com.blacklaunch.launcher.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Локальний журнал вильотів: якщо BlackLaunch падає, опис помилки записується у файл
 * на телефоні (без мережі). Користувач бачить і копіює його в «Про BlackLaunch».
 * Жодних фонових процесів: запис відбувається лише в момент падіння.
 */
class CrashLog(private val file: File, private val header: () -> String) {
    private val _text = MutableStateFlow(read())

    /** Останні помилки (найновіша перша) або null, якщо їх не було. */
    val text: StateFlow<String?> = _text

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Журнал не має заважати системі показати своє вікно про виліт.
            runCatching { record(thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(threadName: String, error: Throwable) {
        val entry = CrashEntries.format(header(), threadName, error.stackTraceToString())
        val updated = CrashEntries.append(read().orEmpty(), entry)
        file.writeText(updated)
        _text.value = updated
    }

    fun clear() {
        file.delete()
        _text.value = null
    }

    private fun read(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
}

/** Формат журналу: кілька останніх записів, найновіший перший. */
object CrashEntries {
    const val SEPARATOR = "\n\n=====\n\n"
    const val MAX_ENTRIES = 3
    const val MAX_STACK_CHARS = 8_000

    fun format(header: String, threadName: String, stackTrace: String): String =
        "$header\nThread: $threadName\n\n${stackTrace.trim().take(MAX_STACK_CHARS)}"

    fun append(existing: String, entry: String): String =
        (listOf(entry) + existing.split(SEPARATOR).filter { it.isNotBlank() })
            .take(MAX_ENTRIES)
            .joinToString(SEPARATOR)

    /** Перший (найновіший) запис — для показу на екрані. */
    fun latest(text: String): String = text.substringBefore(SEPARATOR)
}
