/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS M2.2 — the motion contract of the Design-2030 surface. One source
 * of truth for the key-tap pulse: a press compresses the key to
 * [PULSE_PRESSED_SCALE] in [PULSE_DURATION_MS], and the release springs
 * back slower ([PULSE_RELEASE_DURATION_MS]) — the asymmetry is what makes
 * the tap feel physical instead of mechanical.
 *
 * The pulse is DRAW-ONLY (graphicsLayer scale): layout is never
 * re-measured per frame, so a 60fps pulse costs nothing to the row
 * geometry. Pinned by DrsM2DesignTests.
 */
object DrsMotion {

    /** Pressed key scale — perceptibly compressed, not collapsed. */
    const val PULSE_PRESSED_SCALE = 0.94f

    /** Idle scale (the release target). */
    const val PULSE_IDLE_SCALE = 1f

    /** Press direction: fast — the finger already announced the intent. */
    const val PULSE_DURATION_MS = 60

    /** Release direction: slower spring back — the tactile afterglow. */
    const val PULSE_RELEASE_DURATION_MS = 140

    /** The animation spec durations for a given pressed state. */
    fun durationFor(pressed: Boolean): Int =
        if (pressed) PULSE_DURATION_MS else PULSE_RELEASE_DURATION_MS

    /** The target scale for a given pressed state. */
    fun scaleFor(pressed: Boolean): Float =
        if (pressed) PULSE_PRESSED_SCALE else PULSE_IDLE_SCALE
}
