package com.offlinekeyboard.dictionary

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.offlinekeyboard.core.dictionary.UserDictionary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.Executors

/**
 * Holds the [UserDictionary] for the whole app process (keyboard service and settings screen share it).
 * The file lives in no-backup storage, so it never leaves the device. All access is on the main thread;
 * only the file write runs in the background.
 */
class UserDictionaryStore private constructor(context: Context) {

    private val file = File(context.noBackupFilesDir, FILE_NAME)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var savedVersion = 0L

    val dictionary: UserDictionary = runCatching {
        if (file.isFile) UserDictionary.parse(file.readText(Charsets.UTF_8)) else UserDictionary()
    }.getOrElse { UserDictionary() }

    private val _changes = MutableStateFlow(0L)

    /** Emits on every change, for the settings screen. */
    val changes: StateFlow<Long> = _changes

    private val saveRunnable = Runnable { saveNow() }

    /** Call after changing [dictionary]; writes are batched. */
    fun changed() {
        _changes.value = dictionary.version
        main.removeCallbacks(saveRunnable)
        main.postDelayed(saveRunnable, SAVE_DELAY_MS)
    }

    fun saveNow() {
        main.removeCallbacks(saveRunnable)
        if (dictionary.version == savedVersion) return
        savedVersion = dictionary.version
        val snapshot = dictionary.serialize()
        io.execute {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(snapshot, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    companion object {
        private const val FILE_NAME = "user_dictionary.tsv"
        private const val SAVE_DELAY_MS = 3000L

        @Volatile
        private var instance: UserDictionaryStore? = null

        fun get(context: Context): UserDictionaryStore =
            instance ?: synchronized(this) {
                instance ?: UserDictionaryStore(context.applicationContext).also { instance = it }
            }
    }
}
