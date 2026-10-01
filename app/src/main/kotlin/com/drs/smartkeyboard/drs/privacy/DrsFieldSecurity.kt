/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.privacy

/**
 * DRS M3.6 — field security: how the keyboard treats a field the user is
 * typing into, derived from ABSTRACTED input facts (not raw android.* ints
 * — the classification is pure JVM and unit-tested; the mapping from
 * EditorInfo bits happens once, at the EditorInstance boundary).
 *
 * Levels (highest protection wins):
 *  - BLOCKED: never learn, never suggest-personalize, never persist a
 *    trace. Password-kind fields live here — this line is NEVER
 *    negotiated down, not even by the user's own gate.
 *  - SENSITIVE: incognito-style behavior — the session works, nothing
 *    about it feeds learning or diagnostics.
 *  - NORMAL: everyday fields.
 *
 * An explicit user FORCE_OFF gate disables the feature everywhere it
 * CAN be disabled — but the password line stays BLOCKED regardless
 * (opting out of protection never re-enables learning in a password
 * field).
 *
 * Honest note: TYPE_TEXT_VARIATION_OTP is often assumed to exist in
 * the Android API — it does NOT (checked against the actual android.jar
 * of the target SDK), so no OTP classification is invented here.
 */
object DrsFieldSecurity {

    enum class Level { NORMAL, SENSITIVE, BLOCKED }

    /** The user-side gate for the feature. */
    enum class UserGate { FEATURE_OFF, STANDARD, STRICT }

    /** The abstracted field facts — mapped from EditorInfo at the boundary. */
    data class FieldFacts(
        val isPasswordKind: Boolean,
        val isEmailAddress: Boolean = false,
        val isUri: Boolean = false,
        val isAutoCompleteBlocked: Boolean = false,
        val isMultiLine: Boolean = false,
    )

    /** Pure classification — BLOCKED beats SENSITIVE beats NORMAL. */
    fun classify(facts: FieldFacts): Level = when {
        facts.isPasswordKind -> Level.BLOCKED
        facts.isEmailAddress || facts.isUri || facts.isAutoCompleteBlocked -> Level.SENSITIVE
        else -> Level.NORMAL
    }

    /**
     * The effective level after the user's gate. BLOCKED is
     * non-negotiable; STRICT lifts everything else to SENSITIVE;
     * FEATURE_OFF drops the protection the gate MAY drop.
     */
    fun effective(classified: Level, gate: UserGate): Level = when {
        classified == Level.BLOCKED -> Level.BLOCKED
        gate == UserGate.STRICT -> Level.SENSITIVE
        gate == UserGate.FEATURE_OFF -> Level.NORMAL
        else -> classified
    }

    /** Convenience: should this field suppress personal learning entirely? */
    fun suppressesLearning(level: Level): Boolean = level != Level.NORMAL
}
