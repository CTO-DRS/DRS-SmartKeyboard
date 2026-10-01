/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import java.io.File

/**
 * DRS M0.4 — the in-app mirror of [ci/perf_budgets.json], the repo's
 * single source of truth for performance budgets. The JSON is authoritative;
 * this object mirrors the values for code paths (and the diag screen) that
 * need them, and [DrsM04PerformanceBudgetTest] PINS the two together —
 * editing one without the other breaks the build.
 */
object DrsPerformanceBudgets {

    const val STARTUP_WARM_MS_P50 = 220.0
    const val STARTUP_COLD_MS_P50 = 350.0
    const val FRAME_P90_MS = 16.7
    const val FRAME_P99_MS = 33.0

    /** Every budget, keyed exactly like the JSON keys (order-insensitive compare). */
    val ALL: Map<String, Double> = mapOf(
        "startup_warm_ms_p50" to STARTUP_WARM_MS_P50,
        "startup_cold_ms_p50" to STARTUP_COLD_MS_P50,
        "frame_p90_ms" to FRAME_P90_MS,
        "frame_p99_ms" to FRAME_P99_MS,
    )

    /** Relative path from the :app module dir (JVM test working dir) to the source of truth. */
    const val BUDGETS_JSON_PATH = "../ci/perf_budgets.json"

    /**
     * Parses the budgets JSON into a map — honest: unknown budgets are
     * reported (not silently dropped), a missing "budgets" object fails.
     */
    fun parseBudgetsJson(text: String): Result<Map<String, Double>> = runCatching {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(text)
            as? kotlinx.serialization.json.JsonObject
            ?: error("budgets JSON root is not an object")
        val budgets = root["budgets"] as? kotlinx.serialization.json.JsonObject
            ?: error("budgets JSON carries no 'budgets' object")
        budgets.entries.associate { (key, value) ->
            val parsed = (value as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                ?: error("budget '$key' is not a number")
            key to parsed
        }
    }

    /** Reads the real budgets file from the repo (test-side convenience). */
    fun readBudgetsFile(): File = File(BUDGETS_JSON_PATH)
}
