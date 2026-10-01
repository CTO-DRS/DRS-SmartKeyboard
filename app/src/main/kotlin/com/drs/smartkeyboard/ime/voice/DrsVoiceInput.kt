/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.StringRes
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.drs.ai.DrsVoiceCommands
import com.drs.smartkeyboard.lib.devtools.flogError
import com.drs.smartkeyboard.lib.devtools.flogInfo
import com.drs.smartkeyboard.lib.devtools.flogWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.drs.lib.android.AndroidVersion

/**
 * DRS v1.23.0: built-in voice dictation — «الميكروفون يستيقظ». Until this
 * round the mic key ended in one of two dead ends: switch to an external
 * voice IME if the ROM ships one, or a toast saying none was found. The
 * controller below drives the platform recognizer directly, shows the
 * live partial text in a bar above the keyboard, and commits the final
 * transcript into the host editor at the cursor — the same surface any
 * mainstream keyboard offers, with zero network code of our own (the
 * platform recognition service owns its own transport).
 *
 * Privacy contract (mirrors the v1.20 forced-privacy rule):
 * - password / incognito contexts never reach the recognizer;
 * - the RECORD_AUDIO permission is requested through a dedicated
 *   translucent trampoline ([VoicePermissionActivity]) because an IME
 *   service cannot host a permission dialog;
 * - the external voice-IME switch remains the honest fallback when the
 *   ROM has no recognition service at all.
 */

/** The listening UI state observed by the voice bar above the keyboard. */
sealed interface VoiceUiState {
    data object Idle : VoiceUiState

    /**
     * DRS v1.26.0: [onDevice] tells the bar whether this session actually
     * runs on the ROM's on-device recognizer — «الشارة الصادقة». AUTO
     * sessions that got the local engine show it exactly like strict
     * ON_DEVICE_ONLY sessions, because the user deserves to see when
     * their speech stays inside the device; STANDARD sessions and any
     * session the controller could not classify keep it false.
     */
    data class Listening(
        val partial: String,
        val onDevice: Boolean = false,
    ) : VoiceUiState

    data class Error(@StringRes val resId: Int) : VoiceUiState
}

/**
 * DRS v1.26.0: the pure badge truth — does the recognizer this session
 * created run on the device? `ON_DEVICE_ONLY` with an incapable ROM never
 * reaches a listening state at all (createRecognizer yields null → the
 * honest error path), so the only combination that must NOT use the
 * on-device engine is the pinned STANDARD mode. Pure — JVM-tested.
 */
fun usesOnDeviceRecognizer(
    mode: VoiceRecognizerMode,
    onDevicePossible: Boolean,
): Boolean = mode != VoiceRecognizerMode.STANDARD && onDevicePossible

/**
 * Process-wide bus between the permission trampoline activity and the
 * IME-side controller (same process, so a plain shared flow suffices).
 * The activity cannot reach the KeyboardManager directly — it emits a
 * start request after the user grants the microphone, and the controller
 * picks it up.
 */
object DrsVoiceInputBus {
    val uiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)

    /**
     * DRS v1.25.0: the live microphone amplitude channel — [rmsToAmplitude]
     * quantizes every [android.speech.RecognitionListener.onRmsChanged]
     * report into `0f..1f` so the bar can breathe with the user's actual
     * voice instead of a fixed animation. Many services never deliver RMS
     * at all, so consumers must keep the v1.24 pulse as the fallback and
     * only trust this channel while it actually flows.
     */
    val rmsAmplitude = MutableStateFlow(0f)

    internal val startRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** The permission trampoline calls this after a successful grant. */
    fun requestStart(languageTag: String) {
        if (languageTag.isNotBlank()) startRequests.tryEmit(languageTag)
    }

    /** Clears any error/listening state (keyboard hidden, cancelled…). */
    fun reset() {
        uiState.value = VoiceUiState.Idle
        rmsAmplitude.value = 0f
    }
}

/** The honest route a mic-key press must take, decided purely. */
enum class VoiceInputRoute {
    /** Permission granted + recognition service present → listen now. */
    START_INTERNAL,
    /** Recognition service present but microphone not granted yet. */
    REQUEST_PERMISSION,
    /** No recognition service on the ROM → legacy external voice IME. */
    FALLBACK_EXTERNAL,
    /** Password fields and incognito mode never see the microphone. */
    DISABLED_SENSITIVE,
    /**
     * DRS v1.24.0: the user switched built-in dictation off in the typing
     * settings — an explicit choice the mic key must honor with an honest
     * toast, never silently resurrecting the external-IME switch either.
     */
    DISABLED_BY_SETTING,
    /**
     * DRS v1.25.0: the user demanded the strictly on-device recognizer
     * ([VoiceRecognizerMode.ON_DEVICE_ONLY]) but this ROM cannot honor
     * that demand — the honest answer is a toast, never a silent cloud
     * fallback the user explicitly refused.
     */
    ON_DEVICE_UNAVAILABLE,
}

/**
 * DRS v1.25.0: which recognizer may listen — «المستخدم يختار أين يُسمع
 * كلامه». [AUTO] keeps the v1.23 behavior (on-device when the ROM offers
 * it, standard otherwise); [ON_DEVICE_ONLY] refuses to speak to any
 * network-backed service (privacy-first users, offline ROMs); [STANDARD]
 * pins the classic recognizer for users whose on-device engine quality
 * disappoints. Stored by name in the settings datastore.
 */
enum class VoiceRecognizerMode {
    AUTO,
    ON_DEVICE_ONLY,
    STANDARD,
}

/**
 * DRS v1.28.0 audit fix (Medium): the single platform recognizer factory.
 * Previously only the IME-side controller consulted the user's privacy
 * mode, while the v1.27 popup-editor dictation always created the classic
 * (cloud-capable) recognizer — a user who pinned ON_DEVICE_ONLY still got
 * the network engine in the editor window. Both surfaces now route through
 * this function so the «أين يُسمع كلامك» contract is one contract.
 */
fun createPlatformRecognizer(
    context: Context,
    mode: VoiceRecognizerMode,
    onDevicePossible: Boolean,
): SpeechRecognizer? {
    return when (mode) {
        // The user demanded on-device only — a ROM that cannot honor the
        // demand gets null, and the caller's honest error path speaks
        // instead of a silent cloud fallback.
        VoiceRecognizerMode.ON_DEVICE_ONLY ->
            if (onDevicePossible) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else null
        // The user pinned the classic recognizer (typically because the
        // on-device engine quality disappoints on their ROM).
        VoiceRecognizerMode.STANDARD ->
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                @Suppress("DEPRECATION") // the direct-call deprecation note targets API 31+
                SpeechRecognizer.createSpeechRecognizer(context)
            } else {
                null
            }
        // AUTO — the exact v1.23.0 preference order: on-device when the
        // ROM offers it, standard otherwise, null when neither.
        VoiceRecognizerMode.AUTO ->
            if (onDevicePossible) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                @Suppress("DEPRECATION") // the direct-call deprecation note targets API 31+
                SpeechRecognizer.createSpeechRecognizer(context)
            } else {
                null
            }
    }
}

/**
 * DRS v1.28.0 audit fix: the one truth for "can this ROM listen on
 * device?", shared by every recognizer creation site.
 */
fun onDeviceRecognitionPossible(context: Context): Boolean =
    AndroidVersion.ATLEAST_API31_S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

/**
 * The pure mic-key decision — JVM-tested. Order matters: sensitivity
 * first (privacy beats everything), then the explicit user setting
 * (DRS v1.24.0 — a chosen-off feature stays off), then the recognizer
 * mode demand (DRS v1.25.0 — a chosen-strict on-device gate answers
 * honestly when the ROM cannot honor it), then availability, then
 * permission. Every call site must pass the pref-backed [userEnabled]
 * and [recognizerMode] so the settings gates are the real gates. The
 * two v1.25.0 parameters default to the v1.23/v1.24 behavior so older
 * call sites and tests stay honest without edits.
 */
fun decideVoiceInputRoute(
    recognitionAvailable: Boolean,
    permissionGranted: Boolean,
    isSensitive: Boolean,
    userEnabled: Boolean = true,
    recognizerMode: VoiceRecognizerMode = VoiceRecognizerMode.AUTO,
    onDeviceAvailable: Boolean = false,
): VoiceInputRoute = when {
    isSensitive -> VoiceInputRoute.DISABLED_SENSITIVE
    !userEnabled -> VoiceInputRoute.DISABLED_BY_SETTING
    recognizerMode == VoiceRecognizerMode.ON_DEVICE_ONLY && !onDeviceAvailable ->
        VoiceInputRoute.ON_DEVICE_UNAVAILABLE
    !recognitionAvailable -> VoiceInputRoute.FALLBACK_EXTERNAL
    permissionGranted -> VoiceInputRoute.START_INTERNAL
    else -> VoiceInputRoute.REQUEST_PERMISSION
}

/**
 * DRS v1.25.0: normalizes the recognizer's raw RMS dB report onto the
 * `0f..1f` amplitude the bar's live wave renders. Android services
 * typically report roughly `-2..12` dB with silence wobbling around
 * `0`, so the linear window below maps that band to the full range and
 * clamps everything else. The result is quantized to 2% steps to keep
 * the StateFlow from recomposing the bar on every jitter of the last
 * decimal. Pure — JVM-tested.
 */
fun rmsToAmplitude(rmsdB: Float): Float {
    val normalized = (rmsdB - RMS_DB_FLOOR) / (RMS_DB_CEIL - RMS_DB_FLOOR)
    val amplitude = normalized.coerceIn(0f, 1f)
    return (amplitude * 50f).toInt() / 50f
}

private const val RMS_DB_FLOOR = -2f
private const val RMS_DB_CEIL = 12f

/**
 * Owns the platform [SpeechRecognizer] lifecycle. Every recognizer call
 * is posted to the main thread (the recognizer is main-thread-only),
 * results commit through [onCommit] and the bar state flows through
 * [DrsVoiceInputBus.uiState]. A single listening session at a time — a
 * new start tears the previous session down first.
 */
class DrsVoiceInputController(
    private val context: Context,
    private val onCommit: (String) -> Unit,
    /**
     * DRS v1.25.0: read lazily at every start so a settings change
     * between sessions is honored without recreating the controller.
     */
    private val recognizerMode: () -> VoiceRecognizerMode = { VoiceRecognizerMode.AUTO },
    /**
     * DRS Phase 2 (roadmap task 12): continuous dictation — read lazily at
     * every RESULT so the toggle applies mid-flow. When true, a completed
     * result (command or dictation) restarts listening automatically until
     * the user presses the mic again or speaks a terminating command
     * (STOP / CLEAR_ALL).
     */
    private val continuous: () -> Boolean = { false },
    /**
     * DRS Phase 2 (roadmap task 12): the spoken EDIT-command executor —
     * invoked on the main thread with the parsed command when the
     * recognized phrase is a command (never committed as text).
     */
    private val onCommand: (DrsVoiceCommands.Command) -> Unit = {},
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recognizer: SpeechRecognizer? = null

    /**
     * DRS v1.26.0: whether the recognizer of the live session is the
     * on-device one — decided once per session in [start] alongside the
     * recognizer creation, and carried on every [VoiceUiState.Listening]
     * the session publishes so the bar's badge stays honest.
     */
    @Volatile
    private var sessionOnDevice = false

    /** DRS Phase 2 (task 12): language of the live/last session, for continuous restarts. */
    @Volatile
    private var lastLanguageTag: String? = null

    init {
        // The permission trampoline grants the mic and emits a start
        // request — this collector turns it into a real listening session
        // on the main thread.
        scope.launch {
            DrsVoiceInputBus.startRequests.collect { tag -> start(tag) }
        }
    }

    /** Starts a dictation session in [languageTag] (BCP-47, e.g. ar-SA). */
    fun start(languageTag: String) {
        lastLanguageTag = languageTag
        mainHandler.post {
            // One session at a time — a re-start tears the old one down.
            destroyRecognizer()
            // DRS v1.26.0: classify the session once, before creating the
            // recognizer, so the badge flag and the created instance can
            // never disagree.
            val mode = recognizerMode()
            val onDevicePossible = onDeviceRecognitionPossible(context)
            sessionOnDevice = usesOnDeviceRecognizer(mode, onDevicePossible)
            val sr = try {
                createPlatformRecognizer(context, mode, onDevicePossible)
            } catch (e: Throwable) {
                flogError { "recognizer creation failed: $e" }
                DrsVoiceInputBus.uiState.value = VoiceUiState.Error(R.string.voice__error)
                return@post
            }
            if (sr == null) {
                flogWarning { "recognizer unavailable at start time" }
                DrsVoiceInputBus.uiState.value = VoiceUiState.Error(R.string.voice__error)
                return@post
            }
            recognizer = sr
            sr.setRecognitionListener(Listener())
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            // DRS v1.25.0: a fresh session starts from silence, never
            // from the previous session's last amplitude.
            DrsVoiceInputBus.rmsAmplitude.value = 0f
            DrsVoiceInputBus.uiState.value =
                VoiceUiState.Listening(partial = "", onDevice = sessionOnDevice)
            try {
                sr.startListening(intent)
                flogInfo { "listening started lang=$languageTag" }
            } catch (e: Throwable) {
                flogError { "startListening failed: $e" }
                DrsVoiceInputBus.uiState.value = VoiceUiState.Error(R.string.voice__error)
                destroyRecognizer()
            }
        }
    }

    /** Cancels any live session and clears the bar state. */
    fun stop() {
        mainHandler.post {
            try {
                recognizer?.cancel()
            } catch (_: Throwable) {
            }
            destroyRecognizer()
            DrsVoiceInputBus.reset()
        }
    }

    /** Final teardown (service destroyed) — stops the collector too. */
    fun destroy() {
        mainHandler.post {
            try {
                recognizer?.destroy()
            } catch (_: Throwable) {
            }
            recognizer = null
            DrsVoiceInputBus.reset()
        }
        scope.cancel()
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.destroy()
        } catch (_: Throwable) {
        }
        recognizer = null
        sessionOnDevice = false
    }

    // DRS v1.28.0 audit fix: the private createRecognizer() body moved to the
    // shared top-level createPlatformRecognizer() so the popup-editor
    // dictation honors the same privacy mode (see file header note).

    private inner class Listener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            DrsVoiceInputBus.uiState.value =
                VoiceUiState.Listening(partial = "", onDevice = sessionOnDevice)
        }

        override fun onBeginningOfSpeech() = Unit

        // DRS v1.25.0: the live amplitude channel — quantized to 2%
        // steps so the bar recomposes with the voice, not with jitter.
        override fun onRmsChanged(rmsdB: Float) {
            DrsVoiceInputBus.rmsAmplitude.value = rmsToAmplitude(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            flogWarning { "recognition error=$error" }
            DrsVoiceInputBus.uiState.value = VoiceUiState.Error(errorResOf(error))
            destroyRecognizer()
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
                .trim()
            destroyRecognizer()
            // DRS Phase 2 (roadmap task 12): the result is first parsed as a
            // spoken EDIT command («سطر جديد», «احذف الكلمة الأخيرة», «تراجع»…).
            // A command is EXECUTED — never committed as text — while plain
            // dictation flows to the editor exactly as before. With the
            // continuous preference on, either outcome restarts listening
            // (unless the command terminates the session), so a user can
            // dictate a whole message voice-first without touching the mic
            // button again.
            val isContinuous = continuous()
            when (val parsed = DrsVoiceCommands.parse(text)) {
                is DrsVoiceCommands.Parsed.IsCommand -> {
                    flogInfo { "voice command=${parsed.command}" }
                    try {
                        onCommand(parsed.command)
                    } catch (e: Throwable) {
                        flogError { "voice command execution failed: $e" }
                    }
                    DrsVoiceInputBus.reset()
                    if (isContinuous && !DrsVoiceCommands.isTerminating(parsed.command)) {
                        restartForContinuous()
                    }
                }
                is DrsVoiceCommands.Parsed.Dictation -> {
                    if (parsed.text.isEmpty()) {
                        if (isContinuous) {
                            DrsVoiceInputBus.reset()
                            restartForContinuous()
                            return
                        }
                        DrsVoiceInputBus.uiState.value =
                            VoiceUiState.Error(R.string.voice__error_no_match)
                        return
                    }
                    DrsVoiceInputBus.reset()
                    onCommit(parsed.text)
                    if (isContinuous) restartForContinuous()
                }
            }
        }

        /**
         * DRS Phase 2 (task 12): continuous-mode restart. Posted — not
         * called inline — so the recognizer teardown from onResults fully
         * lands before the next session starts (the recognizer is
         * main-thread-only and single-session).
         */
        private fun restartForContinuous() {
            val tag = lastLanguageTag ?: return
            mainHandler.postDelayed({ start(tag) }, CONTINUOUS_RESTART_DELAY_MS)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            DrsVoiceInputBus.uiState.value =
                VoiceUiState.Listening(partial = partial, onDevice = sessionOnDevice)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        /** DRS Phase 2 (task 12): pause between continuous sessions (recognizer teardown lands). */
        private const val CONTINUOUS_RESTART_DELAY_MS = 350L

        fun errorResOf(error: Int): Int = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> R.string.voice__error_no_match
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice__error_no_speech
            else -> R.string.voice__error
        }
    }
}
