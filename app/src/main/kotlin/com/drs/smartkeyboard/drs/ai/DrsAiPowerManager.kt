/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * DRS Phase 2 (roadmap task 13): energy efficiency for the AI features —
 * device capability tiers, on-demand model loading and battery awareness.
 *
 * The AI stack (quantized corrections, learning, bigrams, smart replies)
 * adapts to the DEVICE it runs on through three tiers:
 *
 *  - [Tier.LIGHT]: small RAM (< 3 GB) or battery saver active. The
 *    heavy tables (bigrams) are NOT loaded at all, corrections run at
 *    the tight budget, personal learning is paused. The keyboard stays
 *    instant on old hardware.
 *  - [Tier.STANDARD]: default phones. Everything on with the standard
 *    budget.
 *  - [Tier.FULL]: large-RAM devices, battery not saver. The full
 *    budget (wider length band) is available.
 *
 * Loading is ON-DEMAND by construction: every table in the stack loads
 * lazily on its first real use (the provider's dictionary/bigram caches
 * already work this way) and [tier] is consulted at every suggest call
 * — switching Battery Saver on mid-session downgrades the engine
 * without a restart.
 *
 * The classification itself is PURE ([classify]) and unit-tested; the
 * Android wrapper just feeds it real numbers.
 */
object DrsAiPowerManager {

    enum class Tier { LIGHT, STANDARD, FULL }

    /** Effective capability switches derived from the tier. */
    data class Caps(
        val tier: Tier,
        val bigramsEnabled: Boolean,
        val smartRepliesEnabled: Boolean,
        val learningEnabled: Boolean,
        val maxCorrectionCost: Int,
        val lengthBand: Int,
        val vocalizationEnabled: Boolean,
    )

    private val LIGHT = Caps(
        tier = Tier.LIGHT,
        bigramsEnabled = false,
        smartRepliesEnabled = true,
        learningEnabled = false,
        maxCorrectionCost = 8,
        lengthBand = 3,
        vocalizationEnabled = false,
    )

    private val STANDARD = Caps(
        tier = Tier.STANDARD,
        bigramsEnabled = true,
        smartRepliesEnabled = true,
        learningEnabled = true,
        maxCorrectionCost = 12,
        lengthBand = 4,
        vocalizationEnabled = true,
    )

    private val FULL = Caps(
        tier = Tier.FULL,
        bigramsEnabled = true,
        smartRepliesEnabled = true,
        learningEnabled = true,
        // Budgets stay STRICTLY below DrsQuantizedEngine.MAX_DISTANCE (16)
        // — 16 is the saturation sentinel, never a usable match cost.
        maxCorrectionCost = 14,
        lengthBand = 5,
        vocalizationEnabled = true,
    )

    /**
     * Pure classification: [totalRamMb] is the device's total memory,
     * [batterySaver] true when the OS power saver is active. Deterministic
     * and side-effect free.
     */
    fun classify(totalRamMb: Long, batterySaver: Boolean): Caps {
        if (batterySaver) return LIGHT
        return when {
            totalRamMb < 3_000L -> LIGHT
            totalRamMb >= 6_000L -> FULL
            else -> STANDARD
        }
    }

    /**
     * Android probe: classifies THIS device right now. Never throws —
     * any failure degrades to [STANDARD] (the historical behavior).
     */
    fun capsFor(context: Context): Caps = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val memClassMb = am?.memoryClass?.toLong() ?: 256L
        val totalMb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val memInfo = ActivityManager.MemoryInfo()
                am?.getMemoryInfo(memInfo)
                (memInfo.totalMem / (1024L * 1024L))
            } catch (_: Throwable) {
                memClassMb * 4
            }
        } else {
            memClassMb * 4
        }
        val saver = pm?.isPowerSaveMode ?: false
        classify(totalMb, saver)
    } catch (_: Throwable) {
        STANDARD
    }

    /** Convenience gate used by the suggestion pipeline. */
    fun caps(context: Context): Caps = capsFor(context)
}
