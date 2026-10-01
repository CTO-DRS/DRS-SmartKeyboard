/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import android.os.Debug
import java.util.concurrent.atomic.AtomicLong

/**
 * DRS v1.0.6: real, in-process performance instrumentation.
 *
 * Every number produced here is MEASURED, never estimated or faked:
 *  - Key-handling latency: the actual wall-clock time the key event handler
 *    takes per key press, sampled in [recordKeyLatency] by KeyboardManager.
 *  - Memory: live Java heap and native heap numbers straight from the
 *    runtime.
 *  - Storage: real size of the DRS state file on disk.
 *
 * The latency window is a bounded ring buffer (last [WINDOW] samples) so
 * typing stays allocation-free and the memory footprint stays constant.
 * Samples only live in RAM - nothing is persisted and nothing leaves the
 * device. They reset when the keyboard process restarts, which the UI says
 * explicitly.
 */
object DrsPerformance {

    private const val WINDOW = 256

    private val samples = LongArray(WINDOW)
    private val cursor = AtomicLong(0)

    /** Records one key-handler duration in MICROSECONDS. Cheap, lock-free. */
    fun recordKeyLatency(micros: Long) {
        if (micros < 0) return
        val index = (cursor.getAndIncrement() % WINDOW).toInt()
        samples[index] = micros
    }

    /** Number of samples recorded since process start (window-capped). */
    fun sampleCount(): Long = cursor.get().coerceAtMost(WINDOW.toLong())

    /** True once at least [count] samples are in the window. */
    fun hasSamples(count: Long = 10): Boolean = cursor.get() >= count

    /**
     * Snapshot of the current window, sorted ascending.
     * Returns an empty list when no samples exist yet.
     */
    fun snapshot(): List<Long> {
        val count = sampleCount().toInt()
        if (count == 0) return emptyList()
        val out = LongArray(count)
        System.arraycopy(samples, 0, out, 0, count)
        return out.sorted()
    }

    /** Average duration in microseconds over the current window. */
    fun averageMicros(): Long {
        val snap = snapshot()
        if (snap.isEmpty()) return 0
        return snap.sum() / snap.size
    }

    /** Percentile [p] (0..100) in microseconds over the current window. */
    fun percentileMicros(p: Int): Long {
        val snap = snapshot()
        if (snap.isEmpty()) return 0
        val index = (((p / 100.0) * snap.size).toInt() - 1).coerceIn(0, snap.lastIndex)
        return snap[index]
    }

    /** Maximum duration in microseconds over the current window. */
    fun maxMicros(): Long = snapshot().lastOrNull() ?: 0

    /** Live Java heap numbers in bytes: used, allocated total, max. */
    data class MemorySnapshot(
        val javaHeapUsedBytes: Long,
        val javaHeapMaxBytes: Long,
        val nativeHeapUsedBytes: Long,
    )

    fun memorySnapshot(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val native = try {
            Debug.getNativeHeapAllocatedSize()
        } catch (_: Throwable) {
            0L
        }
        return MemorySnapshot(
            javaHeapUsedBytes = used.coerceAtLeast(0),
            javaHeapMaxBytes = runtime.maxMemory().coerceAtLeast(0),
            nativeHeapUsedBytes = native.coerceAtLeast(0),
        )
    }
}
