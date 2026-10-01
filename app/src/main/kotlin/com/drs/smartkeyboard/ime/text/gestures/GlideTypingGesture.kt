/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.text.gestures

import android.content.Context
import android.view.MotionEvent
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.keyboard.TextKey
import com.drs.smartkeyboard.lib.devtools.flogDebug
import com.drs.smartkeyboard.lib.util.ViewUtils
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Wrapper class which holds all enums, interfaces and classes for detecting a gesture.
 */
class GlideTypingGesture {
    /**
     * Class which detects swipes based on given [MotionEvent]s. Only supports single-finger swipes
     * and ignores additional pointers provided, if any.
     */
    class Detector(context: Context) {
        private var pointerData: PointerData = PointerData(mutableListOf(), 0)
        private val keySize = ViewUtils.px2dp(context.resources.getDimension(R.dimen.key_width))
        private val listeners: ArrayList<Listener> = arrayListOf()
        private var pointerId: Int = -1

        /** DRS v1.20.0: sliding (position, eventTime) window used for the gesture decision. */
        private val recentWindow = ArrayDeque<Pair<Position, Long>>()

        companion object {
            // DRS v1.20.0: this is no longer a hard deadline that permanently latches the
            // touch as "not a gesture". It is a SLIDING window: velocity is measured over
            // the most recent ~500ms of movement, so a slow start (press, dwell, then
            // glide) still becomes a glide once the finger actually moves — the way
            // world-class keyboards behave. A user who dwells for 2 seconds is no longer
            // locked out of glide typing for that whole touch.
            private const val RECENT_WINDOW_MS = 500
            internal const val VELOCITY_THRESHOLD = 0.10 // dp per ms
            internal val SWIPE_GESTURE_KEYS = arrayOf(KeyCode.DELETE, KeyCode.SHIFT, KeyCode.SPACE, KeyCode.CJK_SPACE)

            /**
             * DRS v1.20.0: pure gesture-start decision, extracted for JVM testing.
             * [distDp] is the distance travelled within the sliding window, [windowMs]
             * the window duration, [keySizeDp] the minimum travel expected of a real
             * glide and [initialKeyCode] the key the touch stream started on (swipe
             * gesture keys — delete/shift/space — are excluded from glide).
             */
            internal fun shouldStartGesture(
                distDp: Float,
                windowMs: Long,
                keySizeDp: Float,
                initialKeyCode: Int?,
            ): Boolean = distDp > keySizeDp &&
                (distDp / windowMs.coerceAtLeast(1L)) > VELOCITY_THRESHOLD &&
                initialKeyCode !in SWIPE_GESTURE_KEYS
        }

        /**
         * Method which evaluates if a given [event] is a gesture.
         *
         * @return whether or not the event was interpreted as part of a gesture.
         */
        fun onTouchEvent(event: MotionEvent, initialKey: TextKey?): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        resetState()
                    }
                    if (pointerId != -1) {
                        // if we already have another pointer, we don't care
                        return false
                    }
                    val pointerIndex = event.actionIndex
                    pointerId = event.getPointerId(pointerIndex)
                    pointerData.apply {
                        positions.add(Position(event.getX(pointerIndex), event.getY(pointerIndex)))
                        startTime = System.currentTimeMillis()
                    }
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    // DRS v1.20.0: the old guard compared our pointer id against
                    // event.getPointerId(event.actionIndex) — but for ACTION_MOVE the
                    // actionIndex is always 0, so a glide tracked on any non-zero pointer
                    // was silently ignored. The correct guard is: does our pointer still
                    // exist in this event?
                    val pointerIndex = event.findPointerIndex(pointerId)
                    if (pointerIndex < 0) {
                        // not our pointer (or it already lifted).
                        return false
                    }

                    for (i in 0..event.historySize) {
                        // Historical batch timestamps are shared across pointers, so the
                        // flat index variant of getHistoricalEventTime is the correct one.
                        val sampleTime = when (i) {
                            event.historySize -> event.eventTime
                            else -> event.getHistoricalEventTime(i)
                        }
                        val pos = when (i) {
                            event.historySize -> Position(event.getX(pointerIndex), event.getY(pointerIndex))
                            else -> Position(event.getHistoricalX(pointerIndex, i), event.getHistoricalY(pointerIndex, i))
                        }
                        pointerData.positions.add(pos)
                        recentWindow.addLast(pos to sampleTime)
                        while (recentWindow.isNotEmpty() && sampleTime - recentWindow.first().second > RECENT_WINDOW_MS) {
                            recentWindow.removeFirst()
                        }
                        if (pointerData.isActuallyGesture == null) {
                            // evaluate whether is actually a gesture — over the SLIDING
                            // window, not since the touch began
                            val (anchorPos, anchorTime) = recentWindow.first()
                            val dist = ViewUtils.px2dp(anchorPos.dist(pos))
                            val time = (sampleTime - anchorTime) + 1
                            flogDebug { "Distance glided: $dist dp with velocity: ${dist / time} dp/ms" }
                            if (shouldStartGesture(dist, time.toLong(), keySize, initialKey?.computedData?.code)) {
                                pointerData.isActuallyGesture = true
                                // Let listener know all those points need to be added.
                                pointerData.positions.take(pointerData.positions.size - 1).forEach { point ->
                                    listeners.forEach {
                                        it.onGlideAddPoint(point)
                                    }
                                }
                            }
                            // DRS v1.20.0: the permanent `isActuallyGesture = false` latch
                            // after 500 ms is gone — with the sliding window the decision
                            // simply re-evaluates on every new sample, so slow starts recover.
                        }

                        if (pointerData.isActuallyGesture == true) {
                            pointerData.positions.last()
                                .let { point -> listeners.forEach { it.onGlideAddPoint(point) } }
                        }
                    }
                    return pointerData.isActuallyGesture ?: false
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    if (pointerId != event.getPointerId(event.actionIndex)) {
                        // not our pointer.
                        return false
                    }
                    if (pointerData.isActuallyGesture == true) {
                        listeners.forEach { listener -> listener.onGlideComplete(pointerData) }
                    }
                    resetState()
                    return false
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (pointerData.isActuallyGesture == true) {
                        listeners.forEach { it.onGlideCancelled() }
                    }
                    resetState()
                }
                else -> return false
            }
            return false
        }

        fun registerListener(listener: Listener) {
            listeners.add(listener)
        }

        fun unregisterListener(listener: Listener) {
            listeners.remove(listener)
        }

        private fun resetState() {
            pointerData.apply {
                positions.clear()
                startTime = 0
                isActuallyGesture = null
            }
            recentWindow.clear()
            pointerId = -1
        }

        data class PointerData(
            val positions: MutableList<Position>,
            var startTime: Long,
            var isActuallyGesture: Boolean? = null,
        )

        data class Position(val x: Float, val y: Float) {
            fun dist(p2: Position): Float {
                return sqrt((p2.x - x).pow(2) + (p2.y - y).pow(2))
            }
        }
    }

    interface Listener {
        /**
         * Called when a gesture is complete.
         */
        fun onGlideComplete(data: Detector.PointerData) {}

        /**
         * Called when a point is added to a gesture.
         * Will not be called before a series of events is detected as a gesture.
         */
        fun onGlideAddPoint(point: Detector.Position) {}

        /**
         * Called to cancel a gesture.
         */
        fun onGlideCancelled() {}
    }
}
