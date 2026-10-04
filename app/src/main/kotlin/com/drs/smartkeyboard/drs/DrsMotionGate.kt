/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * DRS v2.2.2 — «عقد احترام الإيقاع» at the composition boundary.
 *
 * Reads the system-wide animator duration scale (the same signal Android's
 * «Remove animations» accessibility switch zeroes) and reports whether the
 * DrsMotion contracts should animate or snap. Deterministic and honest:
 * the scale is either zero (motion off — every DrsMotion duration collapses
 * to 0 while the final targets stay identical) or non-zero (motion on — the
 * pinned v2.2.1 timings run verbatim). Nothing here guesses, averages or
 * invents a threshold.
 *
 * The value is remembered for the lifetime of the calling composition; a
 * scale change while the keyboard stays continuously open lands on the next
 * recomposition cycle of the IME view — documented, acceptable drift for a
 * system setting that changes outside the typing session.
 */
@Composable
fun rememberDrsMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
