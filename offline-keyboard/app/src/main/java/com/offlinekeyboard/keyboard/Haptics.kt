package com.offlinekeyboard.keyboard

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * The tap vibration of each strength, shared by the keyboard and the settings preview. The phone's
 * predefined "click" feels as light as a "tick" on many phones, so medium is its own pulse: longer and
 * stronger than light, shorter than strong.
 */
internal fun tapEffect(strength: VibrationStrength, vibrator: Vibrator): VibrationEffect {
    val amplitude = vibrator.hasAmplitudeControl()
    return when (strength) {
        VibrationStrength.LIGHT ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            else VibrationEffect.createOneShot(8, if (amplitude) 60 else VibrationEffect.DEFAULT_AMPLITUDE)
        VibrationStrength.MEDIUM -> when {
            amplitude -> VibrationEffect.createOneShot(20, 190)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            else -> VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        VibrationStrength.STRONG ->
            VibrationEffect.createOneShot(32, if (amplitude) 255 else VibrationEffect.DEFAULT_AMPLITUDE)
    }
}
