/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.input

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.collection.SparseArrayCompat
import androidx.collection.isNotEmpty
import androidx.collection.set
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.drs.DrsAcceleratedRepeat
import com.drs.smartkeyboard.ime.keyboard.KeyData
import com.drs.smartkeyboard.ime.text.gestures.SwipeAction
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import org.drs.lib.android.removeAndReturn
import com.drs.smartkeyboard.lib.devtools.flogDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InputEventDispatcher private constructor(private val repeatableKeyCodes: IntArray) {
    companion object {
        private val DoubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
        private val KeyRepeatDelay = ViewConfiguration.getKeyRepeatDelay().toLong()

        // DRS perf (r0-D): a lost key-up (window hidden mid-press, gesture
        // teardown race, ...) used to keep the repeat loop alive for as long
        // as the dispatcher existed. One held key may never repeat longer
        // than this ceiling — after it the loop self-terminates and the
        // next down starts a fresh press cycle.
        private const val REPEAT_CEILING_MS = 60_000L

        fun new(repeatableKeyCodes: IntArray = intArrayOf()) = InputEventDispatcher(repeatableKeyCodes.clone())
    }

    private val prefs by DrsPreferenceStore
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // DRS perf (r0-D): pressedKeys is guarded by a plain monitor lock now —
    // the suspend-only GuardedByLock Mutex forced a runBlocking + mutex
    // handshake onto EVERY sendDown/sendUp/sendDownUp/isPressed call, which
    // is the hottest path in the IME. All critical sections below are
    // non-suspending, so a monitor lock is sufficient and de-blocks the
    // dispatch path.
    private val pressedKeysLock = Any()
    private val pressedKeys = SparseArrayCompat<PressedKeyInfo>()

    // DRS perf (r0-D): written on dispatch threads, read cross-thread by
    // isConsecutiveDown/isConsecutiveUp/isUninterruptedEventSequence —
    // publication must be immediate.
    @Volatile
    private var lastKeyEventDown: EventData = EventData(0L, TextKeyData.UNSPECIFIED)
    @Volatile
    private var lastKeyEventUp: EventData = EventData(0L, TextKeyData.UNSPECIFIED)

    /**
     * The input key event register. If null, the dispatcher will still process input, but won't dispatch them to an
     * event receiver.
     */
    var keyEventReceiver: InputKeyEventReceiver? = null

    private fun determineLongPressDelay(data: KeyData): Long {
        val delayMillis = prefs.keyboard.longPressDelay.get().toLong()
        val factor = when (data.code) {
            KeyCode.SPACE, KeyCode.CJK_SPACE, KeyCode.SHIFT -> 2.5f
            KeyCode.LANGUAGE_SWITCH -> 2.0f
            else -> 1.0f
        }
        return (delayMillis * factor).toLong()
    }

    private fun determineRepeatDelay(data: KeyData): Long {
        val factor = when (data.code) {
            KeyCode.DELETE_WORD, KeyCode.FORWARD_DELETE_WORD, KeyCode.UNDO, KeyCode.REDO -> 5.0f
            else -> 1.0f
        }
        // DRS v1.0.6: honor the user's repeat-rate percentage (50..300).
        // Higher rate = shorter delay, so the platform delay is divided by
        // the factor. The pref read is cheap (jetpref in-memory cache).
        val ratePercent = try {
            prefs.keyboard.keyRepeatRatePercent.get().coerceIn(50, 300)
        } catch (_: Throwable) {
            100
        }
        val platformDelay = KeyRepeatDelay * 100f / ratePercent
        return (platformDelay * factor).toLong()
    }

    private fun determineRepeatData(data: KeyData): KeyData {
        return when (data.code) {
            KeyCode.DELETE -> when (prefs.gestures.deleteKeyLongPress.get()) {
                SwipeAction.DELETE_WORD -> TextKeyData.DELETE_WORD
                else -> TextKeyData.DELETE
            }
            KeyCode.FORWARD_DELETE -> when (prefs.gestures.deleteKeyLongPress.get()) {
                SwipeAction.DELETE_WORD -> TextKeyData.FORWARD_DELETE_WORD
                else -> TextKeyData.FORWARD_DELETE
            }
            else -> data
        }
    }

    fun sendDown(
        data: KeyData,
        onLongPress: () -> Boolean = { false },
        onRepeat: () -> Boolean = { true },
    ): PressedKeyInfo? {
        flogDebug { data.toString() }
        val eventTime = SystemClock.uptimeMillis()
        val result: PressedKeyInfo? = synchronized(pressedKeysLock) {
            if (pressedKeys.containsKey(data.code)) return@synchronized null
            val pressedKeyInfo = PressedKeyInfo(eventTime).also { pressedKeyInfo ->
                pressedKeyInfo.job = scope.launch {
                    val longPressDelay = determineLongPressDelay(data)
                    delay(longPressDelay)
                    val longPressResult = withContext(Dispatchers.Main) { onLongPress() }
                    if (longPressResult) {
                        pressedKeyInfo.blockUp = true
                    } else if (repeatableKeyCodes.contains(data.code)) {
                        val repeatData = determineRepeatData(data)
                        val repeatDelay = determineRepeatDelay(repeatData)
                        // DRS v2.4.0: the accelerating delete ladder — the
                        // delay is re-derived PER REPEAT through the pure
                        // DrsAcceleratedRepeat contract, so the first six
                        // repeats run at the user's exact configured rate
                        // and then the fixed three-gear ladder engages.
                        // Delete-family codes only; every other repeatable
                        // key keeps the pinned fixed rate (passthrough).
                        val accelerated = DrsAcceleratedRepeat.appliesTo(repeatData.code) &&
                            prefs.keyboard.acceleratedDelete.get()
                        // DRS perf (r0-D): bounded repeat loop — terminates
                        // after [REPEAT_CEILING_MS] even if the matching up
                        // event was lost.
                        var repeatedForMs = 0L
                        var repeatIndex = 0
                        while (isActive && repeatedForMs < REPEAT_CEILING_MS) {
                            val onRepeatResult = withContext(Dispatchers.Main) { onRepeat() }
                            if (onRepeatResult) {
                                // DRS p6 (E1): the repeat dispatch mutates the
                                // editor exactly like a key-up does — it must
                                // run on the main thread, not concurrently with
                                // typing on the Default dispatcher.
                                withContext(Dispatchers.Main) {
                                    keyEventReceiver?.onInputKeyRepeat(repeatData)
                                }
                                pressedKeyInfo.blockUp = true
                            }
                            val currentDelay = DrsAcceleratedRepeat.delayFor(
                                repeatDelay,
                                repeatIndex,
                                accelerated,
                            )
                            delay(currentDelay)
                            repeatedForMs += currentDelay
                            repeatIndex += 1
                        }
                    }
                }
            }
            pressedKeys[data.code] = pressedKeyInfo
            pressedKeyInfo
        }
        if (result != null) {
            keyEventReceiver?.onInputKeyDown(data)
            lastKeyEventDown = EventData(eventTime, data)
        }
        return result
    }

    fun sendUp(data: KeyData) {
        flogDebug { data.toString() }
        val (result, isBlocked) = synchronized(pressedKeysLock) {
            if (pressedKeys.containsKey(data.code)) {
                val pressedKeyInfo = pressedKeys.removeAndReturn(data.code)?.also { it.cancelJobs() }
                return@synchronized true to (pressedKeyInfo?.blockUp == true)
            }
            return@synchronized false to false
        }
        if (result) {
            if (!isBlocked) {
                keyEventReceiver?.onInputKeyUp(data)
                lastKeyEventUp = EventData(SystemClock.uptimeMillis(), data)
            } else {
                keyEventReceiver?.onInputKeyCancel(data)
            }
        }
    }

    fun sendDownUp(data: KeyData) {
        flogDebug { data.toString() }
        synchronized(pressedKeysLock) {
            pressedKeys.removeAndReturn(data.code)?.also { it.cancelJobs() }
        }
        val eventData = EventData(SystemClock.uptimeMillis(), data)
        keyEventReceiver?.onInputKeyDown(data)
        lastKeyEventDown = eventData
        keyEventReceiver?.onInputKeyUp(data)
        lastKeyEventUp = eventData
    }

    fun sendCancel(data: KeyData) {
        flogDebug { data.toString() }
        val result = synchronized(pressedKeysLock) {
            if (pressedKeys.containsKey(data.code)) {
                pressedKeys.removeAndReturn(data.code)?.also { it.cancelJobs() }
                return@synchronized true
            }
            return@synchronized false
        }
        if (result) {
            keyEventReceiver?.onInputKeyCancel(data)
        }
    }

    /**
     * Checks if there's currently a key down with given [code].
     *
     * @param code The key code to check for.
     *
     * @return True if the given [code] is currently down, false otherwise.
     */
    fun isPressed(code: Int): Boolean = synchronized(pressedKeysLock) {
        pressedKeys.containsKey(code)
    }

    fun isAnyPressed(): Boolean = synchronized(pressedKeysLock) {
        pressedKeys.isNotEmpty()
    }

    fun isConsecutiveDown(data: KeyData): Boolean {
        val event = lastKeyEventDown
        return event.data.code == data.code && (SystemClock.uptimeMillis() - event.time) < DoubleTapTimeout
    }

    fun isConsecutiveUp(data: KeyData): Boolean {
        val event = lastKeyEventUp
        return event.data.code == data.code && (SystemClock.uptimeMillis() - event.time) < DoubleTapTimeout
    }

    fun isUninterruptedEventSequence(data: KeyData): Boolean {
        return lastKeyEventDown.data.code == data.code
    }

    fun isRepeatable(data: KeyData): Boolean {
        return repeatableKeyCodes.contains(data.code)
    }

    fun isRepeatableCodeLastDown(): Boolean {
        val event = lastKeyEventDown
        return repeatableKeyCodes.contains(event.data.code)
    }

    /**
     * Closes this dispatcher and cancels the local coroutine scope.
     */
    fun close() {
        keyEventReceiver = null
        scope.cancel()
    }

    data class PressedKeyInfo(
        val eventTimeDown: Long,
        var job: Job? = null,
        var blockUp: Boolean = false,
    ) {
        fun cancelJobs() {
            job?.cancel()
        }
    }

    data class EventData(
        val time: Long,
        val data: KeyData,
    )
}

/**
 * Interface which represents an input key event receiver.
 */
interface InputKeyEventReceiver {
    /**
     * Event method which gets called when a key went down.
     *
     * @param data The associated input key data.
     */
    fun onInputKeyDown(data: KeyData)

    /**
     * Event method which gets called when a key went up.
     *
     * @param data The associated input key data.
     */
    fun onInputKeyUp(data: KeyData)

    /**
     * Event method which gets called when a key is called repeatedly while being pressed down.
     *
     * @param data The associated input key data.
     */
    fun onInputKeyRepeat(data: KeyData)

    /**
     * Event method which gets called when a key press is cancelled.
     *
     * @param data The associated input key data.
     */
    fun onInputKeyCancel(data: KeyData)
}
