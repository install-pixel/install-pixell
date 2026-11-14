package com.installpixel.pelmen

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Оболонка гри «Пельмень: Втеча від виделки».
 * Уся гра — це assets/index.html; тут лише повний екран, вібрація, кнопка «Назад» і пауза.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#1D3A34"))
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isHapticFeedbackEnabled = false
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true            // рекорди, скіни, досягнення
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true              // лише для власного assets/index.html
                allowContentAccess = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                textZoom = 100                      // гра не ламається від системного розміру шрифту
            }
            addJavascriptInterface(Bridge(), "PelmenAndroid")
            webViewClient = object : WebViewClient() {
                // гра нікуди не переходить; будь-які посилання ігноруємо
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            }
        }
        setContentView(web)
        hideSystemBars()
        web.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.pelmenBack ? window.pelmenBack() : 'exit')") { result ->
                    if (result == null || result.contains("exit")) confirmExit()
                }
            }
        })
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("Вийти з гри?")
            .setMessage("Пельмень чекатиме на тебе 🥟")
            .setPositiveButton("Вийти") { _, _ -> finish() }
            .setNegativeButton("Грати далі", null)
            .show()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, web).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        web.evaluateJavascript("window.pelmenPause && window.pelmenPause()", null)
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        web.evaluateJavascript("window.pelmenResume && window.pelmenResume()", null)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    /** Те, що гра може викликати з JavaScript: window.PelmenAndroid.* */
    inner class Bridge {
        /** "40" — один імпульс, "60,40,90" — візерунок вібрація/пауза/вібрація. */
        @JavascriptInterface
        fun vibrate(pattern: String) {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            val parts = pattern.split(',').mapNotNull { it.trim().toLongOrNull()?.coerceIn(1, 1000) }
            if (parts.isEmpty()) return
            if (Build.VERSION.SDK_INT >= 26) {
                val effect = if (parts.size == 1) {
                    VibrationEffect.createOneShot(parts[0], VibrationEffect.DEFAULT_AMPLITUDE)
                } else {
                    VibrationEffect.createWaveform(longArrayOf(0) + parts.toLongArray(), -1)
                }
                v.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                if (parts.size == 1) v.vibrate(parts[0]) else v.vibrate(longArrayOf(0) + parts.toLongArray(), -1)
            }
        }

        @JavascriptInterface
        fun version(): String = BuildConfig.VERSION_NAME
    }
}
