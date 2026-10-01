/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

/**
 * DRS Phase 2 (roadmap task 14): one-handed mode geometry.
 *
 * The split keyboard (SplitLayout) serves two-thumb typing on wide
 * screens; one-handed mode serves the opposite need — the WHOLE keyboard
 * shifts toward the thumb of the holding hand so every key is reachable,
 * while keys keep their normal size and hit targets stay honest.
 *
 * Like SplitLayout, this is a PURE decision + geometry engine: it
 * computes the horizontal offset for a given keyboard width and side
 * preference, plus a width squeeze that keeps the moved keyboard inside
 * the screen edge without scaling rows. No Android types, fully tested.
 */
object DrsOneHanded {

    /** Which side the keyboard leans toward (from the USER's view). */
    enum class Side {
        /** Lean toward the right edge — natural for left-thumb holding. */
        RIGHT,

        /** Lean toward the left edge — natural for right-thumb holding. */
        LEFT,
    }

    /** Offset strength presets, as a fraction of the free horizontal space. */
    enum class Strength(val fraction: Float) {
        OFF(0f),
        SUBTLE(0.4f),
        BALANCED(0.7f),
        FULL(1f),
    }

    /**
     * The horizontal offset (in the same pixel unit as [keyboardWidthPx])
     * to apply to the keyboard content for [side] at [strength].
     *
     * The offset is a fraction of the FREE space (keyboard width minus
     * the squeezed keyboard width), not of the whole width — so the
     * keyboard never collides with the opposite screen edge:
     *
     *   squeezed = keyboardWidth * (1 - SQUEEZE * strength)
     *   free     = keyboardWidth - squeezed
     *   offset   = free * strength.fraction
     *
     * Positive result means "shift toward the [side] edge"; the caller
     * applies the sign according to layout direction.
     */
    fun offsetPx(
        keyboardWidthPx: Float,
        side: Side,
        strength: Strength,
    ): Float {
        if (strength == Strength.OFF || keyboardWidthPx <= 0f) return 0f
        val squeezed = keyboardWidthPx * (1f - SQUEEZE * strength.fraction)
        val free = keyboardWidthPx - squeezed
        val offset = free * strength.fraction
        return if (side == Side.RIGHT) offset else -offset
    }

    /**
     * The width factor to multiply every row's key width by for
     * [strength] (1f = unchanged). The squeeze keeps the shifted keyboard
     * fully visible; the constant was chosen so the widest FULL squeeze
     * still leaves every key above the 48dp a11y minimum at common phone
     * widths.
     */
    fun widthScaleFor(strength: Strength): Float =
        1f - SQUEEZE * strength.fraction

    /**
     * Width squeeze ceiling. 0.12 keeps FULL at 88% width — the largest
     * squeeze that preserves comfortable hit targets on a 360dp keyboard.
     */
    const val SQUEEZE = 0.12f

    /** Sanitizes a user-selected strength percent (0..100) to a preset. */
    fun strengthFromPercent(percent: Int): Strength = when {
        percent <= 0 -> Strength.OFF
        percent <= 40 -> Strength.SUBTLE
        percent <= 70 -> Strength.BALANCED
        else -> Strength.FULL
    }
}
