/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.input

import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.staticCompositionLocalOf
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.ime.keyboard.KeyData
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import org.drs.lib.android.AndroidVersion
import org.drs.lib.android.systemServiceOrNull
import org.drs.lib.android.systemVibratorOrNull
import org.drs.lib.android.vibrate
import com.drs.smartkeyboard.lib.devtools.flogDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val LocalInputFeedbackController = staticCompositionLocalOf<InputFeedbackController> { error("not init") }

/**
 * Input feedback controller is responsible to process and perform audio and haptic
 * feedback for user interactions based on the system and DRS preferences.
 */
class InputFeedbackController private constructor(private val ims: InputMethodService) {
    companion object {
        fun new(ims: InputMethodService) = InputFeedbackController(ims)
    }

    private val prefs by DrsPreferenceStore

    private val audioManager = ims.systemServiceOrNull(AudioManager::class)
    private val vibrator = ims.systemVibratorOrNull()
    private val contentResolver = ims.contentResolver
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        // Prepare the DRS bundled key sound packs (lazy-loaded SoundPool).
        try {
            DrsSoundPlayer.ensureInitialized(ims)
        } catch (_: Throwable) {
            // Custom sounds are optional and must never break the keyboard.
        }
    }

    private var systemAudioEnabled: Boolean = false
    private var systemHapticEnabled: Boolean = false

    fun updateSystemPrefsState() {
        systemAudioEnabled = systemPref(Settings.System.SOUND_EFFECTS_ENABLED)
        systemHapticEnabled = systemPref(Settings.System.HAPTIC_FEEDBACK_ENABLED)
    }

    fun keyPress(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatKeyPress.get()) performAudioFeedback(data, 1.0)
        if (prefs.inputFeedback.hapticFeatKeyPress.get()) performHapticFeedback(data, 1.0)
    }

    fun keyLongPress(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatKeyLongPress.get()) performAudioFeedback(data, 0.7)
        if (prefs.inputFeedback.hapticFeatKeyLongPress.get()) performHapticFeedback(data, 0.4)
    }

    fun keyRepeatedAction(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatKeyRepeatedAction.get()) performAudioFeedback(data, 0.4)
        if (prefs.inputFeedback.hapticFeatKeyRepeatedAction.get()) performHapticFeedback(data, 0.05)
    }


    fun gestureMovingSwipe(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatGestureMovingSwipe.get()) performAudioFeedback(data, 0.4)
        if (prefs.inputFeedback.hapticFeatGestureMovingSwipe.get()) performHapticFeedback(data, 0.05)
    }

    private fun systemPref(id: String): Boolean {
        if (contentResolver == null) return false
        return Settings.System.getInt(contentResolver, id, 0) != 0
    }

    private fun performAudioFeedback(data: KeyData, factor: Double) {
        if (audioManager == null) return
        if (!prefs.inputFeedback.audioEnabled.get()) return
        if (prefs.inputFeedback.audioActivationMode.get() ==
            InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS && !systemAudioEnabled) return

        scope.launch {
            val volume = (prefs.inputFeedback.audioVolume.get() * factor) / 100.0
            if (volume < 0.01 || volume > 1.00) return@launch
            val style = try {
                prefs.inputFeedback.drsSoundStyle.get()
            } catch (_: Throwable) {
                DrsSoundStyle.SYSTEM
            }
            val customRes = style.resId
            if (customRes != null) {
                // DRS custom sound pack: play the bundled sample. DRS
                // v1.3.0: the playback rate shifts the pitch per key
                // category, so space/enter/delete sound distinct instead
                // of one identical tone for everything.
                val rate = when (data.code) {
                    KeyCode.SPACE -> 0.92f
                    KeyCode.ENTER -> 0.86f
                    KeyCode.DELETE, KeyCode.FORWARD_DELETE,
                    KeyCode.DELETE_WORD, KeyCode.FORWARD_DELETE_WORD,
                    -> 1.12f
                    else -> 1f
                }
                flogDebug { "Perform DRS sound style=$style volume=$volume rate=$rate" }
                DrsSoundPlayer.play(customRes, volume.toFloat(), rate)
            } else {
                // Upstream behavior: system sound effects.
                val effect = when (data.code) {
                    KeyCode.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                    KeyCode.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                    KeyCode.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                    else -> AudioManager.FX_KEYPRESS_STANDARD
                }
                flogDebug { "Perform audio with volume=$volume and effect=$effect" }
                audioManager.playSoundEffect(effect, volume.toFloat())
            }
        }
    }

    private fun performHapticFeedback(data: KeyData, factor: Double) {
        if (vibrator == null) return
        if (!prefs.inputFeedback.hapticEnabled.get()) return
        if (prefs.inputFeedback.hapticActivationMode.get() ==
            InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS && !systemHapticEnabled) return

        scope.launch {
            if (prefs.inputFeedback.hapticVibrationMode.get() == HapticVibrationMode.USE_HAPTIC_FEEDBACK_INTERFACE) {
                val view = ims.window?.window?.decorView ?: return@launch
                val hfc = if (factor < 1.0 && AndroidVersion.ATLEAST_API27_O_MR1) {
                    HapticFeedbackConstants.TEXT_HANDLE_MOVE
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
                val didPerform = view.performHapticFeedback(hfc,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                if (didPerform) return@launch
                // If not performed fall back to using the vibrator directly
            }

            vibrator.vibrate(
                duration = prefs.inputFeedback.hapticVibrationDuration.get(),
                strength = prefs.inputFeedback.hapticVibrationStrength.get(),
                factor = factor,
            )
        }
    }
}
