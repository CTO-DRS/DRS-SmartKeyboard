/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.keyboard

/**
 * DRS v1.18.0: split keyboard mode — when active, the character keyboard
 * is laid out in two halves with a vertical gap in the middle of every
 * row, so thumbs reach both edges comfortably on wide screens (landscape
 * phones, foldables, tablets) instead of stretching across the full width.
 *
 * The decision logic is fully pure and unit-tested; the rendering side
 * (TextKeyboard.layout) receives a computed gap in pixels and inserts it
 * after the row's midpoint key, in both the grow and the shrink branch,
 * so the split composes with every existing width factor and margin rule.
 */
enum class SplitMode {
    /** Split on wide keyboards only (width >= [SplitLayout.AUTO_MIN_WIDTH_DP] dp). */
    AUTO,

    /** Always split the character keyboard when the window is the normal fixed mode. */
    ALWAYS,

    /** Never split (portrait phones, users who prefer the classic full layout). */
    NEVER,
}

/**
 * Pure decision + geometry engine for the split keyboard.
 */
object SplitLayout {

    /** AUTO mode splits only when the keyboard is at least this wide (in dp). */
    const val AUTO_MIN_WIDTH_DP = 560

    /** The gap is a fraction of the keyboard width, clamped to [GAP_MIN_DP, GAP_MAX_DP]. */
    const val GAP_FRACTION = 0.09f
    const val GAP_MIN_DP = 24f
    const val GAP_MAX_DP = 64f

    /**
     * True when the split should apply for [mode] at the given keyboard width
     * in density-independent pixels.
     */
    fun splitApplies(mode: SplitMode, keyboardWidthDp: Float): Boolean = when (mode) {
        SplitMode.NEVER -> false
        SplitMode.ALWAYS -> true
        SplitMode.AUTO -> keyboardWidthDp >= AUTO_MIN_WIDTH_DP
    }

    /**
     * The split gap in raw pixels for the given [mode], or 0f when the split
     * must not apply. [densityDp] is the DisplayMetrics.density factor used to
     * convert the dp clamp bounds; the gap itself scales with the keyboard
     * width so it never eats more than [GAP_FRACTION] of the row.
     */
    fun gapWidthPx(mode: SplitMode, keyboardWidthPx: Float, densityDp: Float): Float {
        if (keyboardWidthPx <= 0f || densityDp <= 0f) return 0f
        if (!splitApplies(mode, keyboardWidthPx / densityDp)) return 0f
        val gap = keyboardWidthPx * GAP_FRACTION
        return gap.coerceIn(GAP_MIN_DP * densityDp, GAP_MAX_DP * densityDp)
    }

    /**
     * The index of the row key AFTER which the split gap is inserted: the
     * first key whose cumulative width factor reaches half of the row's
     * total, so both halves carry an equal share of the requested width.
     * Returns -1 for an empty row and the last index when nothing reaches
     * the midpoint (float rounding), i.e. the gap degenerates to trailing
     * space where it harms nothing.
     */
    fun splitIndexForRow(widthFactors: FloatArray): Int {
        if (widthFactors.isEmpty()) return -1
        val total = widthFactors.sum()
        if (total <= 0f) return widthFactors.size - 1
        var cumulative = 0f
        for (i in widthFactors.indices) {
            cumulative += widthFactors[i]
            if (cumulative >= total / 2f) return i
        }
        return widthFactors.size - 1
    }
}
