package com.offlinekeyboard.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.offlinekeyboard.R

/**
 * Web addresses the settings open in the browser. The app itself has no network permission; the
 * browser downloads files, and the user imports them from local storage.
 */
internal object Links {
    private const val REPO = "https://github.com/install-pixel/install-pixell"
    private const val BRANCH = "claude/offline-keyboard-android-w9kw2a"

    fun modelFile(name: String) = "$REPO/releases/download/keyboard-models/$name"

    fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_browser, Toast.LENGTH_LONG).show()
        }
    }
}
