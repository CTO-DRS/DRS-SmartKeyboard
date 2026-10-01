/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.android

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

fun Context.systemVibratorOrNull(): Vibrator? {
    return if (AndroidVersion.ATLEAST_API31_S) {
        this.systemServiceOrNull(VibratorManager::class)?.defaultVibrator
    } else {
        this.systemServiceOrNull(Vibrator::class)
    }?.takeIf { it.hasVibrator() }
}

fun Vibrator.vibrate(duration: Int, strength: Int, factor: Double = 1.0) {
    if (duration == 0 || strength == 0) return
    val effectiveDuration = (duration * factor).toLong().coerceAtLeast(1L)
    val effectiveStrength = when {
        this.hasAmplitudeControl() -> (255.0 * ((strength * factor) / 100.0)).toInt().coerceIn(1, 255)
        else -> VibrationEffect.DEFAULT_AMPLITUDE
    }
    Log.d("Vibrator", "Perform haptic with duration=$effectiveDuration and strength=$effectiveStrength")
    val effect = VibrationEffect.createOneShot(effectiveDuration, effectiveStrength)
    this.vibrate(effect)
}
