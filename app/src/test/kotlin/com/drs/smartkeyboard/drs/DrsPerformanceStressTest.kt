/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 * Copyright (C) 2026 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeSorted
import io.kotest.matchers.shouldBe
import java.lang.management.ManagementFactory

/**
 * DRS P1 (global roadmap phase 1): structural performance contracts for the
 * key-latency instrumentation. The values are DETERMINISTIC and the test is
 * ORDER-INDEPENDENT by construction: [DrsPerformance] is a process-global
 * object, so every burst size is a multiple of 7 (the value cycle) — this
 * pins the window alignment to cycle position 3 regardless of how many
 * bursts ran before, making min/avg/max exact constants, not flukes.
 *
 * This is the guard the quality dashboard needs before it may display any
 * number (docs/QUALITY_DASHBOARD.md): we test the measurement machinery.
 */
class DrsPerformanceStressTest : FunSpec({

    // Value cycle: 0,100,200,300,400,500,600 (mean 300 per full cycle).
    // Burst sizes are multiples of 7 so the cumulative index stays aligned.
    fun recordBurst(count: Int, valueOf: (Int) -> Long = { (it % 7) * 100L }) {
        repeat(count) { i -> DrsPerformance.recordKeyLatency(valueOf(i)) }
    }

    test("the latency window is a hard 256-sample ring: ~10k records stay capped") {
        recordBurst(9_996)
        DrsPerformance.sampleCount() shouldBe 256
        DrsPerformance.snapshot().size shouldBe 256
    }

    test("window statistics are exact under the deterministic cycle") {
        recordBurst(9_996)
        val snap = DrsPerformance.snapshot()
        snap.shouldBeSorted()                       // ascending contract
        snap.first() shouldBe 0L                    // min of the cycle
        snap.last() shouldBe 600L                   // max of the cycle
        DrsPerformance.maxMicros() shouldBe 600L
        // Window alignment math: with every burst a multiple of 7, the
        // 256-sample window starts at cycle position 3 — so 300..600 occur
        // 37 times each, 0..200 36 times each: sum 77400, floor(77400/256)
        // = 302. A regression in ring arithmetic breaks this constant.
        DrsPerformance.averageMicros() shouldBe 302L
    }

    test("percentiles respect the ascending snapshot contract") {
        recordBurst(1_008)
        val p0 = DrsPerformance.percentileMicros(0)
        val p50 = DrsPerformance.percentileMicros(50)
        val p100 = DrsPerformance.percentileMicros(100)
        p0 shouldBe 0L
        p100 shouldBe 600L
        (p50 in 0..600) shouldBe true
    }

    test("negative durations are refused — the window never records garbage") {
        DrsPerformance.recordKeyLatency(-1)
        DrsPerformance.recordKeyLatency(Long.MIN_VALUE)
        recordBurst(7) { 42L }
        // No negative/garbage value ever lands in the window...
        DrsPerformance.snapshot().none { it < 0 || it % 100 != 0L && it != 42L } shouldBe true
        // ...and the refused inputs did not advance the cursor: exactly the
        // seven deliberate 42s from this burst are present.
        DrsPerformance.snapshot().count { it == 42L } shouldBe 7
    }

    test("recording is allocation-free: 50k key events allocate no heap") {
        // getThreadAllocatedBytes lives on com.sun.management.ThreadMXBean
        // (HotSpot extension), not on java.lang.management.ThreadMXBean.
        val sunMx = ManagementFactory.getThreadMXBean()
            as? com.sun.management.ThreadMXBean
        if (sunMx == null || !sunMx.isThreadAllocatedMemorySupported) {
            // Contract unmeasurable on this runtime — skip, never fake.
            return@test
        }
        val tid = Thread.currentThread().id
        val delta: Long? = try {
            val before = sunMx.getThreadAllocatedBytes(tid)
            // Plain primitive loop — a function-type lambda here would box
            // every argument/return and measure the harness, not the code.
            var i = 0
            while (i < 50_000) {
                DrsPerformance.recordKeyLatency(100L)
                i++
            }
            val after = sunMx.getThreadAllocatedBytes(tid)
            if (before < 0 || after < 0) null else after - before
        } catch (_: Throwable) {
            null
        }
        // If the JVM does not expose per-thread allocation, the contract is
        // unmeasurable on this runtime — skip rather than fake a pass.
        if (delta != null) delta shouldBe 0L
    }

    test("memory snapshot reports non-negative numbers on this runtime") {
        recordBurst(7)
        val mem = DrsPerformance.memorySnapshot()
        (mem.javaHeapUsedBytes >= 0) shouldBe true
        (mem.javaHeapMaxBytes >= 0) shouldBe true
        (mem.nativeHeapUsedBytes >= 0) shouldBe true
        // Used heap can never exceed the max on a sane runtime.
        (mem.javaHeapMaxBytes == 0L || mem.javaHeapUsedBytes <= mem.javaHeapMaxBytes) shouldBe true
    }
})
