/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.text.key.KeyCode

/**
 * DRS v2.4.0 — «اللوحة الحية الشاملة»: عقد الحذف المتسارع الحتمي.
 *
 * Holding the delete key used to repeat at ONE fixed delay forever — the
 * first deleted character and the hundredth arrived at the same pace, so
 * erasing a paragraph was a waiting game. This contract adds the industry
 * feel without a milligram of guesswork: the first repeats run at exactly
 * the user-configured rate, then the delay descends a FIXED three-step
 * ladder (×1.5 → ×2 → ×2.5 of the base speed) and floors at
 * [MIN_DELAY_MS] so the main thread is never flooded.
 *
 * Pure structure like every DRS contract: no UI, no Context, no clock —
 * the compiler and the test suite pin it together. The ladder is
 * deliberately a step function (not a continuous curve): each landing is
 * predictable, documentable, and testable, and the user never feels a
 * drifting rubber band — they feel three distinct gears.
 */
object DrsAcceleratedRepeat {

    /**
     * How many initial repeats run at the user's configured rate before
     * the ladder starts. Six steady repeats (~0.3–1s of hold depending on
     * the repeat-rate pref) is the honest threshold: short enough to save
     * the paragraph-erase case, long enough that a deliberate double-tap
     * delete never feels hijacked.
     */
    const val ACCELERATION_START_INDEX = 6

    /** First gear: 1.5× the base speed. */
    const val GEAR_ONE_FACTOR = 1.5f

    /** Second gear: 2× the base speed (repeats 9–11). */
    const val GEAR_TWO_FACTOR = 2f

    /** Third gear: 2.5× the base speed — the final cap (repeats 12+). */
    const val GEAR_THREE_FACTOR = 2.5f

    /**
     * The absolute delay floor. Even at full gear the dispatcher never
     * delays less than this between repeats — a guarantee to the editor
     * thread, independent of the user's repeat-rate percentage.
     */
    const val MIN_DELAY_MS = 15L

    /**
     * The deterministic gear for [repeatIndex]: 1.0× before
     * [ACCELERATION_START_INDEX], then the fixed three-step ladder. Pure
     * and total: any index (including negatives, defensively) yields a
     * factor in [1.0, GEAR_THREE_FACTOR].
     */
    fun speedFactorFor(repeatIndex: Int): Float = when {
        repeatIndex < ACCELERATION_START_INDEX -> 1f
        repeatIndex < ACCELERATION_START_INDEX + 3 -> GEAR_ONE_FACTOR
        repeatIndex < ACCELERATION_START_INDEX + 6 -> GEAR_TWO_FACTOR
        else -> GEAR_THREE_FACTOR
    }

    /**
     * The delay before repeat #[repeatIndex], given the [baseDelayMs] the
     * dispatcher computed from the user's repeat-rate preference and
     * whether acceleration is enabled at all. When [accelerated] is false
     * this is an exact passthrough — the pinned pre-2.4.0 behavior. When
     * true, the ladder divides the base delay by [speedFactorFor] and
     * floors at [MIN_DELAY_MS]. Monotonic non-increasing in the index.
     */
    fun delayFor(baseDelayMs: Long, repeatIndex: Int, accelerated: Boolean): Long {
        if (!accelerated) return baseDelayMs
        val factor = speedFactorFor(repeatIndex)
        return (baseDelayMs / factor).toLong().coerceAtLeast(MIN_DELAY_MS)
    }

    /**
     * The honest scope: acceleration is a DELETE-family contract. Letters,
     * arrows, word-movers, undo/redo and everything else keep the exact
     * fixed rate they always had — cursor motion must not suddenly change
     * gears under a held key, that would be a lie about position.
     */
    fun appliesTo(code: Int): Boolean = when (code) {
        KeyCode.DELETE,
        KeyCode.FORWARD_DELETE,
        KeyCode.DELETE_WORD,
        KeyCode.FORWARD_DELETE_WORD,
        -> true
        else -> false
    }
}
