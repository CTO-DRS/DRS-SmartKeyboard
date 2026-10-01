/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS M2.5 — the adaptive layout contract: keyboard rows GROW with the
 * user's system font scale, but growth is the ONLY allowed direction and
 * the growth is capped.
 *
 *   scale = fontScale → row scale, growth-only, clamped to [1.0, 1.15]
 *
 * A small font (0.85) NEVER shrinks the rows below the design height
 * (touch targets are an accessibility contract), and a huge font grows
 * them at most 15% (the keyboard must never eat the app's screen). The
 * mapping is half-strength on purpose: a 1.3 font scale grows rows by
 * 15%, not 30% — row HEIGHT inherits the growth gently, the LABELS
 * already scale in full through the text pipeline. Pinned by
 * DrsM2DesignTests.
 */
object DrsAdaptiveLayout {

    const val MIN_ROW_SCALE = 1.0f
    const val MAX_ROW_SCALE = 1.15f

    /** Clamps a raw row scale into the legal growth-only range. */
    fun sanitize(scale: Float): Float = scale.coerceIn(MIN_ROW_SCALE, MAX_ROW_SCALE)

    /**
     * The row scale for a system font scale: growth-only (fontScale < 1
     * keeps the design height), half-strength above 1.0, capped at
     * [MAX_ROW_SCALE].
     */
    fun rowScaleForFont(fontScale: Float): Float {
        if (fontScale <= 1f) return MIN_ROW_SCALE
        return sanitize(1f + (fontScale - 1f) / 2f)
    }
}
