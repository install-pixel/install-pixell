package com.blacklaunch.launcher.automation

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/** «Відтворити» для останньої програми з музикою (без перемикача: уже грає — нічого не тиснемо). */
object MediaKeys {
    fun play(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        if (audio.isMusicActive) return
        val time = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
    }
}
