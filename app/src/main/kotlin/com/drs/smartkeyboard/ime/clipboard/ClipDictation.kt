/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

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
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.ime.voice.VoiceRecognizerMode
import com.drs.smartkeyboard.ime.voice.createPlatformRecognizer
import com.drs.smartkeyboard.ime.voice.onDeviceRecognitionPossible
import com.drs.smartkeyboard.lib.devtools.flogError
import com.drs.smartkeyboard.lib.devtools.flogInfo
import com.drs.smartkeyboard.lib.devtools.flogWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DRS v1.27.0: dictation inside the popup clipboard editor — «المحرر
 * يسمع». Until this round the floating editor's only way to receive
 * speech was the system keyboard popping up on top of it. The editor is
 * a real [androidx.activity.ComponentActivity] with a window of its own,
 * so unlike the IME service it may host the microphone permission
 * dialog directly, and its text lives in plain Compose state — there is
 * no InputConnection to route through. The controller below drives the
 * platform recognizer (the same engine the v1.23 IME dictation uses)
 * and appends the transcript straight into the editor's text state.
 *
 * Deliberately NOT wired through [DrsVoiceInputBus]: that bus belongs to
 * the IME-side dictation bar, and a listening session inside the popup
 * must never paint the keyboard's bar (the keyboard is not even visible
 * here) nor be painted by it. The two surfaces share the engine class
 * patterns, nothing else.
 */

/** The listening UI state observed by the popup editor's dictation row. */
sealed interface ClipDictationState {
    data object Idle : ClipDictationState

    /** Live inside the editor card: the partial transcript as it grows. */
    data class Listening(val partial: String) : ClipDictationState

    data class Error(@StringRes val resId: Int) : ClipDictationState
}

/**
 * The pure joining policy for dictated speech — «الإلحاق الصادق». The
 * editor keeps its text as a plain String with no tracked cursor, so
 * the transcript lands at the end of the text. The rules:
 * - blank/whitespace speech changes nothing ([join] yields `null`) — a
 *   recognizer that heard silence never fabricates an edit;
 * - empty text → the trimmed transcript as-is;
 * - otherwise a single space separates the transcript from text that
 *   does not already end (or begin) with whitespace — the user's own
 *   trailing newline or space is preserved, never doubled;
 * - the merged text goes through the same storage-limit truncation the
 *   typed path uses, and if the truncation swallowed the whole
 *   addition (the text already sat at the limit) the result is `null`
 *   too — an edit that lost every spoken character is not an edit.
 *
 * Pure — JVM-tested.
 */
object ClipDictationPlan {
    fun join(existing: String, spoken: String, limit: Int): String? {
        val clean = spoken.trim()
        if (clean.isEmpty()) return null
        val merged = if (existing.isEmpty()) {
            clean
        } else {
            val needsSeparator =
                !existing.last().isWhitespace() && !clean.first().isWhitespace()
            if (needsSeparator) "$existing $clean" else existing + clean
        }
        val truncated = ClipboardTextPolicy.truncateForStorage(merged, limit)
        return truncated.takeIf { it != existing }
    }
}

/**
 * Owns the platform [SpeechRecognizer] lifecycle for one editor window.
 * Every recognizer call is posted to the main thread (the recognizer is
 * main-thread-only), partial and final results flow through [state], and
 * the final transcript is delivered to [onTranscript] once per session.
 * The recognizer's language follows the editor activity's own resolved
 * locale — the popup speaks the app's UI language, which is the only
 * honest choice available this far from the IME's active schema.
 */
class ClipDictationController(
    private val context: Context,
    private val onTranscript: (String) -> Unit,
    private val languageTag: () -> String = {
        context.resources.configuration.locales[0].toLanguageTag()
    },
    /**
     * DRS v1.28.0 audit fix (Medium): this window's dictation used to always
     * create the classic cloud-capable recognizer and never consulted
     * voice__recognizer_mode — a user who pinned ON_DEVICE_ONLY in the
     * typing settings still got the network engine here. The mode is now
     * read at every start via the shared createPlatformRecognizer() factory,
     * exactly like the IME-side controller; injectable for tests.
     */
    private val recognizerMode: () -> VoiceRecognizerMode = {
        runCatching {
            val prefs by DrsPreferenceStore
            prefs.voice.recognizerMode.get()
        }.getOrDefault(VoiceRecognizerMode.AUTO)
    },
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<ClipDictationState>(ClipDictationState.Idle)
    val state: StateFlow<ClipDictationState> = _state.asStateFlow()

    /** Starts a dictation session (caller has verified the permission). */
    fun start() {
        mainHandler.post {
            // One session at a time — a re-start tears the old one down.
            destroyRecognizer()
            val mode = recognizerMode()
            val onDevicePossible = onDeviceRecognitionPossible(context)
            if (mode == VoiceRecognizerMode.ON_DEVICE_ONLY && !onDevicePossible) {
                // The honest ON_DEVICE_ONLY answer: refuse to listen rather
                // than silently falling back to the network recognizer the
                // user explicitly refused (mirrors decideVoiceInputRoute).
                flogWarning { "clip dictation: on-device demanded but unavailable" }
                _state.value = ClipDictationState.Error(R.string.voice__error)
                return@post
            }
            if (mode != VoiceRecognizerMode.STANDARD && !onDevicePossible &&
                !SpeechRecognizer.isRecognitionAvailable(context)
            ) {
                flogWarning { "clip dictation: recognizer unavailable" }
                _state.value = ClipDictationState.Error(R.string.voice__error)
                return@post
            }
            val tag = languageTag()
            val sr = try {
                createPlatformRecognizer(context, mode, onDevicePossible)
            } catch (e: Throwable) {
                flogError { "clip dictation: recognizer creation failed: $e" }
                _state.value = ClipDictationState.Error(R.string.voice__error)
                return@post
            }
            if (sr == null) {
                flogWarning { "clip dictation: recognizer unavailable at start time" }
                _state.value = ClipDictationState.Error(R.string.voice__error)
                return@post
            }
            recognizer = sr
            sr.setRecognitionListener(Listener())
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            _state.value = ClipDictationState.Listening(partial = "")
            try {
                sr.startListening(intent)
                flogInfo { "clip dictation: listening started lang=$tag" }
            } catch (e: Throwable) {
                flogError { "clip dictation: startListening failed: $e" }
                _state.value = ClipDictationState.Error(R.string.voice__error)
                destroyRecognizer()
            }
        }
    }

    /** Cancels any live session and returns the row to rest. */
    fun stop() {
        mainHandler.post {
            try {
                recognizer?.cancel()
            } catch (_: Throwable) {
            }
            destroyRecognizer()
            _state.value = ClipDictationState.Idle
        }
    }

    /** Final teardown (editor window closed) — kills the scope too. */
    fun destroy() {
        mainHandler.post {
            try {
                recognizer?.destroy()
            } catch (_: Throwable) {
            }
            recognizer = null
            _state.value = ClipDictationState.Idle
        }
        scope.cancel()
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.destroy()
        } catch (_: Throwable) {
        }
        recognizer = null
    }

    private inner class Listener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = ClipDictationState.Listening(partial = "")
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            flogWarning { "clip dictation: recognition error=$error" }
            _state.value = ClipDictationState.Error(errorResOf(error))
            destroyRecognizer()
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
                .trim()
            destroyRecognizer()
            if (text.isEmpty()) {
                _state.value = ClipDictationState.Error(R.string.voice__error_no_match)
            } else {
                _state.value = ClipDictationState.Idle
                onTranscript(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _state.value = ClipDictationState.Listening(partial = partial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        fun errorResOf(error: Int): Int = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> R.string.voice__error_no_match
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice__error_no_speech
            else -> R.string.voice__error
        }
    }
}
