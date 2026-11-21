package com.offlinekeyboard.keyboard

import android.content.Context

/**
 * Whether the glass lies over the blurred app, as the keyboard last found it, for the settings to show:
 * on; off because the phone does not allow window blur now (battery saver, a developer option, or the phone
 * cannot); off because the keyboard's window reaches above the keys (blurring would blur the app there).
 */
enum class BlurState {
    UNKNOWN, ON, NOT_ALLOWED, WINDOW, OLD_ANDROID;

    companion object {
        private const val PREFS = "keyboard_runtime"
        private const val KEY = "blur_state"
        private const val ABOVE = "blur_above"

        fun save(context: Context, state: BlurState, above: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY, null) == state.name && prefs.getInt(ABOVE, -1) == above) return
            prefs.edit().putString(KEY, state.name).putInt(ABOVE, above).apply()
        }

        /** The last state, and how far above the keys the window reached then (px). */
        fun load(context: Context): Pair<BlurState, Int> {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return OLD_ANDROID to 0
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val state = entries.firstOrNull { it.name == prefs.getString(KEY, null) } ?: UNKNOWN
            return state to prefs.getInt(ABOVE, 0)
        }
    }
}
