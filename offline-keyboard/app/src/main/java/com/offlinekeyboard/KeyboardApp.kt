package com.offlinekeyboard

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the last crash report on the phone (never sent anywhere), so the user can copy it from
 * Settings → About when something goes wrong and there is no computer with adb at hand.
 */
class KeyboardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val report = "OffKey ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
                    "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), " +
                    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nthread ${thread.name}\n$trace"
                crashFile(this).writeText(report)
                // Also on the clipboard, so the user can paste it into a message right away.
                runCatching {
                    getSystemService(android.content.ClipboardManager::class.java)
                        ?.setPrimaryClip(android.content.ClipData.newPlainText("OffKey error", report.take(MAX_CLIP)))
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        private const val MAX_CLIP = 8000

        fun crashFile(app: android.content.Context) = File(app.noBackupFilesDir, "last_crash.txt")
    }
}
