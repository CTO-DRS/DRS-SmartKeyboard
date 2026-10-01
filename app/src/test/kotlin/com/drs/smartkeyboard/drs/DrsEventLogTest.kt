/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * DRS p9: the bounded, sanitized DrsEventLog — ring-buffer cap, detail
 * truncation, throwable flattening, snapshot copy semantics and the plain
 * diagnostic report shape. The log is a process-global ring, so every test
 * clears it first and never relies on execution order.
 */
class DrsEventLogTest : FunSpec({

    beforeEach { DrsEventLog.clear() }

    test("the 201st record drops the oldest entry and the snapshot is newest-first") {
        repeat(201) { i -> DrsEventLog.recordInfo(DrsEventLog.Categories.STORE, "e$i") }
        DrsEventLog.size() shouldBe 200
        val snapshot = DrsEventLog.snapshot()
        snapshot.first().detail shouldBe "e200"
        snapshot[1].detail shouldBe "e199"
        snapshot.last().detail shouldBe "e1"
    }

    test("a 200-char detail is truncated to the 160-char cap") {
        DrsEventLog.recordError(DrsEventLog.Categories.ENGINE, "x".repeat(200))
        val recorded = DrsEventLog.snapshot().single()
        recorded.detail.length shouldBe 160
        recorded.level shouldBe DrsEventLog.Level.ERROR
        recorded.category shouldBe DrsEventLog.Categories.ENGINE
    }

    test("throwableDetail flattens to the class name plus a 64-char message cap") {
        val detail = DrsEventLog.throwableDetail(IllegalStateException("x".repeat(200)))
        detail shouldBe "IllegalStateException: " + "x".repeat(64)
    }

    test("snapshot returns a copy — later records never leak into an old snapshot") {
        DrsEventLog.recordInfo(DrsEventLog.Categories.APP, "only")
        val snapshot = DrsEventLog.snapshot()
        snapshot.size shouldBe 1
        DrsEventLog.recordInfo(DrsEventLog.Categories.APP, "second")
        snapshot.size shouldBe 1
        snapshot.single().detail shouldBe "only"
        DrsEventLog.size() shouldBe 2
    }

    test("renderReport carries version, checks, event count and formatted rows") {
        DrsEventLog.recordError(DrsEventLog.Categories.STORE, "boom")
        val report = DrsEventLog.renderReport("1.0.0-test", "5/5 ok")
        report.contains("Version: 1.0.0-test") shouldBe true
        report.contains("Checks: 5/5 ok") shouldBe true
        report.contains("Events: 1") shouldBe true
        report.contains("[ERROR] store: boom") shouldBe true
    }
})
