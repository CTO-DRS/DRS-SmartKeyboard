/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe

/**
 * DRS M0.4 — pins the mirror ([DrsPerformanceBudgets]) to the source of
 * truth ([ci/perf_budgets.json]) in BOTH directions: every constant must
 * equal the JSON value, and the JSON must carry exactly the mirrored key
 * set (no unmirrored budget may sneak in, none may be dropped).
 */
class DrsM04PerformanceBudgetTest : FunSpec({

    context("budgets source of truth ↔ code mirror") {
        test("the budgets file exists in the repo") {
            DrsPerformanceBudgets.readBudgetsFile().shouldExist()
        }

        test("every JSON value equals its Kotlin mirror constant") {
            val json = DrsPerformanceBudgets.parseBudgetsJson(
                DrsPerformanceBudgets.readBudgetsFile().readText(),
            ).getOrThrow()
            json["startup_warm_ms_p50"] shouldBe DrsPerformanceBudgets.STARTUP_WARM_MS_P50
            json["startup_cold_ms_p50"] shouldBe DrsPerformanceBudgets.STARTUP_COLD_MS_P50
            json["frame_p90_ms"] shouldBe DrsPerformanceBudgets.FRAME_P90_MS
            json["frame_p99_ms"] shouldBe DrsPerformanceBudgets.FRAME_P99_MS
        }

        test("the key sets are identical — no budget without a mirror, no mirror without a budget") {
            val json = DrsPerformanceBudgets.parseBudgetsJson(
                DrsPerformanceBudgets.readBudgetsFile().readText(),
            ).getOrThrow()
            json.keys shouldContainExactlyInAnyOrder DrsPerformanceBudgets.ALL.keys.toList()
        }
    }

    context("parse honesty") {
        test("a budgets file without the budgets object fails") {
            DrsPerformanceBudgets.parseBudgetsJson("""{"nope": 1}""").isFailure shouldBe true
        }

        test("a non-numeric budget fails") {
            DrsPerformanceBudgets.parseBudgetsJson(
                """{"budgets": {"frame_p90_ms": "fast"}}""",
            ).isFailure shouldBe true
        }
    }
})
