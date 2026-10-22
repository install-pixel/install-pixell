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
                crashFile(this).writeText(
                    "Offline Keyboard ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
                        "Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                        "thread ${thread.name}\n$trace",
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        fun crashFile(app: android.content.Context) = File(app.noBackupFilesDir, "last_crash.txt")
    }
}
