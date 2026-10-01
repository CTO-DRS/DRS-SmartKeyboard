/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS M2.3 — the smartbar corner-shape contract. The strip's corners are
 * user-tunable through the `smartbar__drs_corner_radius` preference
 * (0..28dp, persisted as an Int); this object is the pure sanitizer the
 * slider and the IME clip both go through, so no out-of-range value can
 * ever reach the drawing code by either path.
 *
 * 0dp = square (the classic strip), 28dp = the pill-shaped 2030 look.
 * Pinned by DrsM2DesignTests.
 */
object DrsSmartbarShape {

    const val MIN_RADIUS_DP = 0
    const val MAX_RADIUS_DP = 28
    const val DEFAULT_RADIUS_DP = 12

    /** Clamps a raw preference value into the legal radius range. */
    fun sanitize(radiusDp: Int): Int = radiusDp.coerceIn(MIN_RADIUS_DP, MAX_RADIUS_DP)

    /** The radius as a Float dp for Compose shape constructors. */
    fun radiusDp(radiusDp: Int): Float = sanitize(radiusDp).toFloat()
}
