/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS M2.6 — the onboarding resume contract, as a pure step machine.
 * The onboarding flow has a fixed number of steps; a user who leaves
 * mid-flow resumes EXACTLY where they stopped (their step persists in
 * [DrsState.onboardingStep]), while a fresh user (never onboarded, no
 * saved step) starts at the beginning, and a completed onboarding never
 * re-enters the flow.
 *
 * Resumable = onboarding NOT done AND a real step (≥1) saved AND the
 * step is inside the flow (not the DONE terminal). Pinned by
 * DrsM2DesignTests.
 */
object DrsOnboardingState {

    /** Resumes into the given step index, sanitized against the flow size. */
    fun resumeStep(onboardingDone: Boolean, savedStep: Int, totalSteps: Int): Int {
        val last = (totalSteps - 1).coerceAtLeast(0)
        if (onboardingDone) return 0
        if (savedStep !in 1 until last) return 0 // 0 = fresh start, DONE = finished
        return savedStep
    }

    /** Whether a resume banner should even be offered for this state. */
    fun isResumable(onboardingDone: Boolean, savedStep: Int, totalSteps: Int): Boolean =
        resumeStep(onboardingDone, savedStep, totalSteps) != 0

    /** Persists a step change, sanitized, through the durable [DrsStore] path. */
    fun persistStep(step: Int, totalSteps: Int) {
        val sanitized = step.coerceIn(0, (totalSteps - 1).coerceAtLeast(0))
        DrsStore.update(immediate = true) { it.copy(onboardingStep = sanitized) }
    }
}
