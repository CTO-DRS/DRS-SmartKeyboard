/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 * Copyright (C) 2026 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

/**
 * DRS P1 (global roadmap phase 1): the structured local crash registry —
 * ADR 0004 contract tests. The privacy promise is the subject under test:
 * a crash record must NEVER carry a throwable message, stack text, or any
 * free-text field beyond sanitized metadata.
 */
class DrsCrashReportsTest : FunSpec({

    fun newRegistry(
        maxEntries: Int = DrsCrashReports.DEFAULT_MAX_ENTRIES,
        maxFileBytes: Int = DrsCrashReports.DEFAULT_MAX_FILE_BYTES,
        clock: () -> Long = { System.currentTimeMillis() },
    ): Pair<DrsCrashReports, java.nio.file.Path> {
        val dir = createTempDirectory("drs-p1-crashreports")
        val registry = DrsCrashReports(
            dir = dir.toFile(),
            versionProvider = { "2.0.0(42)" },
            deviceApiProvider = { 36 },
            processIdProvider = { 4242 },
            clockMillis = clock,
            maxEntries = maxEntries,
            maxFileBytes = maxFileBytes,
        )
        return registry to dir
    }

    test("a recorded crash keeps metadata but NEVER the throwable message") {
        val (registry, dir) = newRegistry()
        val secret = "user/home/alice/super-secret-path.txt typed words here"
        val boom = IllegalStateException(secret)
        registry.record(Thread("main"), boom)

        val snapshot = registry.snapshot()
        snapshot shouldHaveSize 1

        val report = snapshot.single()
        report.exceptionClass shouldBe "java.lang.IllegalStateException"
        report.threadName shouldBe "main"
        report.appVersion shouldBe "2.0.0(42)"
        report.deviceApi shouldBe 36
        report.processId shouldBe 4242
        report.stackHash.length shouldBe 16

        // The privacy wall: nothing on disk and nothing in memory holds the
        // message text.
        val onDisk = Files.readAllBytes(dir.resolve("drs_crash_reports.json")).decodeToString()
        onDisk shouldNotContain secret
        onDisk shouldNotContain "alice"
        registry.exportText() shouldNotContain secret
    }

    test("the stack hash is a stable 16-hex fingerprint of the crash signature") {
        val (registry, _) = newRegistry()
        val a = RuntimeException("msg-irrelevant-A")
        val b = RuntimeException("msg-irrelevant-B")
        // Same throw site shape → same frames → same fingerprint even when
        // messages differ (the hash must not depend on the message).
        registry.record(Thread("t"), a)
        registry.record(Thread("t"), b)
        val hashes = registry.snapshot().map { it.stackHash }
        hashes[0] shouldBe hashes[1]
        hashes[0].length shouldBe 16
        hashes[0].all { it.isDigit() || it in 'a'..'f' } shouldBe true
    }

    test("the 21st crash evicts the oldest record (newest-first snapshot)") {
        val (registry, _) = newRegistry()
        repeat(21) { i ->
            registry.record(Thread("t$i"), RuntimeException("e$i"))
        }
        registry.size() shouldBe DrsCrashReports.DEFAULT_MAX_ENTRIES
        val snapshot = registry.snapshot()
        // newest first — t20 recorded last, t0 evicted, t1 is the survivor
        snapshot.first().threadName shouldBe "t20"
        snapshot[1].threadName shouldBe "t19"
        snapshot.last().threadName shouldBe "t1"
    }

    test("file-size budget evicts the oldest record until the payload fits") {
        val (registry, dir) = newRegistry(maxFileBytes = 900)
        repeat(8) { registry.record(Thread("t"), RuntimeException()) }
        val kept = registry.snapshot()
        kept.shouldNotBeEmpty()
        val onDisk = Files.readAllBytes(dir.resolve("drs_crash_reports.json"))
        onDisk.size.shouldBeLessThanOrEqualTo(900)
        // Snapshot ordering contract: newest first.
        kept.first().timestampMillis shouldBe kept.maxOf { it.timestampMillis }
    }

    test("non-printable characters in thread names are neutralized") {
        val (registry, _) = newRegistry()
        registry.record(Thread("bad\u0000\u001F\u007Fname"), RuntimeException())
        registry.snapshot().single().threadName shouldBe "bad???name"
    }

    test("weird exception class names are filtered to safe charset") {
        val (registry, _) = newRegistry()
        val weird = object : Throwable("\u0001bad class\nname") {}
        // The class name filter runs on javaClass.name — synthetic classes
        // keep dots/dollars/underscores only; arbitrary text cannot appear.
        registry.record(Thread("t"), weird)
        registry.snapshot().single().exceptionClass.all {
            it.isLetterOrDigit() || it == '.' || it == '_' || it == '$'
        } shouldBe true
    }

    test("session stats and per-session crash rate") {
        val (registry, _) = newRegistry()
        registry.crashRatePerSession().shouldBeNull()

        registry.registerSessionStart()
        registry.registerSessionStart()
        registry.record(Thread("t"), RuntimeException())
        registry.registerSessionStart()

        registry.countSessions() shouldBe 3
        registry.countCrashes() shouldBe 1
        registry.crashRatePerSession()!!.let { rate ->
            (kotlin.math.abs(rate - 1.0 / 3.0) < 1e-9) shouldBe true
        }
    }

    test("clear() wipes reports and counters") {
        val (registry, _) = newRegistry()
        registry.registerSessionStart()
        registry.record(Thread("t"), RuntimeException())
        registry.size() shouldBe 1
        registry.clear()
        registry.size() shouldBe 0
        registry.countSessions() shouldBe 0
        registry.countCrashes() shouldBe 0
        registry.exportText() shouldBe ""
    }

    test("exportText contains only the sanitized fields") {
        val (registry, _) = newRegistry()
        val secret = "typed-text-leak"
        registry.registerSessionStart()
        registry.record(Thread("worker-1"), IllegalStateException(secret))
        val exported = registry.exportText()
        exported shouldContain "exception: java.lang.IllegalStateException"
        exported shouldContain "thread: worker-1"
        exported shouldContain "stack-hash: "
        exported shouldNotContain secret
        exported shouldContain "sessions=1"
    }

    test("corrupted registry file recovers to empty instead of crashing") {
        val (registry, dir) = newRegistry()
        dir.resolve("drs_crash_reports.json").toFile().writeText("{ not json !!!")
        registry.snapshot() shouldHaveSize 0
        registry.size() shouldBe 0
        // And a new record heals the file.
        registry.record(Thread("t"), RuntimeException())
        registry.size() shouldBe 1
    }
})
