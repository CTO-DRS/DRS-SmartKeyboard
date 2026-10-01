/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 * Copyright (C) 2026 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * DRS P1 (global roadmap phase 1): structured, bounded, local-only crash
 * registry — the privacy-first answer to crash reporting (ADR 0004).
 *
 * Contract, in one line each:
 *  - ZERO user content: no throwable message, no stack text, no field names.
 *    Only an exception CLASS NAME, a hashed stack fingerprint and metadata.
 *  - BOUNDED: keeps the newest [maxEntries] reports and never exceeds
 *    [maxFileBytes] on disk; the oldest record is dropped first.
 *  - LOCAL: lives in noBackupFilesDir, never leaves the device unless the
 *    user explicitly shares the opt-in export produced by [exportText].
 *  - CRASH-SAFE: every operation is exception-contained; the registry is
 *    written from the uncaught-exception path and must never throw.
 *  - TESTABLE: all platform touchpoints are injected providers; unit tests
 *    construct instances directly against a temporary directory.
 *
 * A tiny monotonic session counter ([registerSessionStart]) accompanies the
 * registry so [crashRatePerSession] can express "crashes per recorded
 * session" — the honest, local-only quality metric for the team dashboard
 * (docs/QUALITY_DASHBOARD.md).
 */
class DrsCrashReports(
    private val dir: File,
    private val versionProvider: () -> String,
    private val deviceApiProvider: () -> Int = { Build.VERSION.SDK_INT },
    private val processIdProvider: () -> Int = { 0 },
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxFileBytes: Int = DEFAULT_MAX_FILE_BYTES,
) {

    // ------------------------------------------------------------ data model

    @Serializable
    data class CrashReport(
        val format: Int,
        val timestampMillis: Long,
        val processId: Int,
        val threadName: String,
        val exceptionClass: String,
        /** First 16 hex chars of SHA-256 over class+method stack frames. */
        val stackHash: String,
        val appVersion: String,
        val deviceApi: Int,
    )

    @Serializable
    private data class SessionStats(val sessions: Int = 0, val crashes: Int = 0)

    // ------------------------------------------------------------ companion

    companion object {
        const val FORMAT_VERSION = 1
        const val DEFAULT_MAX_ENTRIES = 20
        const val DEFAULT_MAX_FILE_BYTES = 128_000

        private const val CRASHES_FILE = "drs_crash_reports.json"
        private const val STATS_FILE = "drs_session_stats.json"

        private const val MAX_THREAD_CHARS = 64
        private const val MAX_CLASS_CHARS = 120
        private const val MAX_VERSION_CHARS = 40
        private const val MAX_STACK_FRAMES = 32

        @Volatile
        private var installed: DrsCrashReports? = null

        /**
         * Binds the registry to the process. Called once from
         * [DrsCrashHandler.install] during application startup; later calls
         * return the same instance.
         */
        fun install(context: Context): DrsCrashReports =
            installed ?: synchronized(this) {
                installed ?: DrsCrashReports(
                    dir = context.noBackupFilesDir,
                    versionProvider = {
                        "${com.drs.smartkeyboard.BuildConfig.VERSION_NAME}(${com.drs.smartkeyboard.BuildConfig.VERSION_CODE})"
                    },
                    processIdProvider = { android.os.Process.myPid() },
                ).also { installed = it }
            }

        /** The installed instance, or null when running before install. */
        fun get(): DrsCrashReports? = installed
    }

    // ------------------------------------------------------------ json

    private val json = Json { encodeDefaults = true }

    private fun crashesFile(): File = File(dir, CRASHES_FILE)
    private fun statsFile(): File = File(dir, STATS_FILE)

    // ------------------------------------------------------------ record

    /**
     * Records one crash. Called from the uncaught-exception path — must
     * never throw and never contain user content.
     */
    fun record(thread: Thread, throwable: Throwable) {
        try {
            val report = CrashReport(
                format = FORMAT_VERSION,
                timestampMillis = clockMillis(),
                processId = processIdProvider(),
                threadName = sanitizeLine(thread?.name ?: "unknown", MAX_THREAD_CHARS),
                exceptionClass = sanitizeClass(throwable?.javaClass?.name ?: "Unknown"),
                stackHash = stackFingerprint(throwable),
                appVersion = sanitizeLine(versionProvider().take(MAX_VERSION_CHARS), MAX_VERSION_CHARS),
                deviceApi = deviceApiProvider(),
            )
            synchronized(this) {
                val existing = readReportsLocked()
                val combined = (existing + report).takeLast(maxEntries)
                writeReportsLocked(combined)
                bumpStatsLocked { it.copy(crashes = it.crashes + 1) }
            }
        } catch (_: Throwable) {
            // The crash registry must never become the second crash.
        }
    }

    /**
     * Registers one session (process start). Called once during install;
     * feeds the per-session crash rate.
     */
    fun registerSessionStart() {
        try {
            synchronized(this) {
                bumpStatsLocked { it.copy(sessions = it.sessions + 1) }
            }
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------ queries

    /** Newest-first snapshot of retained reports. */
    fun snapshot(): List<CrashReport> = synchronized(this) {
        readReportsLocked().asReversed()
    }

    /** Number of retained reports. */
    fun size(): Int = synchronized(this) { readReportsLocked().size }

    /** Total recorded sessions since the stats file was created. */
    fun countSessions(): Int = synchronized(this) { readStatsLocked().sessions }

    /** Total recorded crashes since the stats file was created. */
    fun countCrashes(): Int = synchronized(this) { readStatsLocked().crashes }

    /**
     * Crashes per recorded session, or null when no session was registered
     * yet (the metric is undefined, not zero — callers must not fake it).
     */
    fun crashRatePerSession(): Double? {
        val stats = synchronized(this) { readStatsLocked() }
        if (stats.sessions <= 0) return null
        return stats.crashes.toDouble() / stats.sessions.toDouble()
    }

    /** Clears both the registry and the session counters (user action). */
    fun clear() {
        try {
            synchronized(this) {
                crashesFile().delete()
                statsFile().delete()
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * DRS roadmap phase 3: on-disk footprint for the privacy dashboard —
     * (bytes, entries). Zero reads as "nothing on disk", which is the
     * normal state for most users since recording is rare by design.
     */
    fun diskUsage(): Pair<Long, Long> = synchronized(this) {
        val file = crashesFile()
        val bytes = if (file.exists()) file.length() else 0L
        val entries = readReportsLocked().size.toLong()
        Pair(bytes, entries)
    }

    // ------------------------------------------------------------ export

    /**
     * Builds the opt-in shareable text. Still sanitized: only the same
     * metadata fields the registry stores — never a throwable message.
     */
    fun exportText(maxReports: Int = maxEntries): String {
        val reports = snapshot().take(maxReports)
        if (reports.isEmpty()) return ""
        return buildString {
            appendLine("DRS Smart Keyboard — crash registry export")
            appendLine("format=$FORMAT_VERSION reports=${reports.size} sessions=${countSessions()}")
            for (r in reports) {
                appendLine("---")
                appendLine("at: ${r.timestampMillis}")
                appendLine("version: ${r.appVersion}")
                appendLine("api: ${r.deviceApi}")
                appendLine("pid: ${r.processId}")
                appendLine("thread: ${r.threadName}")
                appendLine("exception: ${r.exceptionClass}")
                appendLine("stack-hash: ${r.stackHash}")
            }
        }
    }

    // ------------------------------------------------------------ sanitize

    private fun sanitizeLine(raw: String, cap: Int): String {
        val clean = raw.map { ch ->
            if (ch.code in 0x20..0x7E) ch else '?'
        }.joinToString("")
        return clean.take(cap)
    }

    private fun sanitizeClass(raw: String): String {
        val filtered = raw.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '$' }
        return (if (filtered.isEmpty()) "Unknown" else filtered).take(MAX_CLASS_CHARS)
    }

    /**
     * Hashes class+method names of the top stack frames. Line numbers and
     * messages are deliberately excluded: the hash identifies a crash
     * signature while provably carrying no user content.
     */
    private fun stackFingerprint(throwable: Throwable): String {
        val frames = throwable.stackTrace
            .take(MAX_STACK_FRAMES)
            .joinToString("|") { "${it.className}.${it.methodName}" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(frames.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    // ------------------------------------------------------------ io

    private fun readReportsLocked(): List<CrashReport> {
        val f = crashesFile()
        if (!f.isFile) return emptyList()
        return try {
            json.decodeFromString<List<CrashReport>>(f.readText())
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun readStatsLocked(): SessionStats {
        val f = statsFile()
        if (!f.isFile) return SessionStats()
        return try {
            json.decodeFromString<SessionStats>(f.readText())
        } catch (_: Throwable) {
            SessionStats()
        }
    }

    private fun bumpStatsLocked(transform: (SessionStats) -> SessionStats) {
        val next = transform(readStatsLocked())
        writeAtomic(statsFile(), json.encodeToString(next))
    }

    private fun writeReportsLocked(reports: List<CrashReport>) {
        var payload = json.encodeToString(reports)
        var kept = reports
        // Hard disk budget: drop the OLDEST record until we fit.
        while (payload.toByteArray(Charsets.UTF_8).size > maxFileBytes && kept.isNotEmpty()) {
            kept = kept.drop(1)
            payload = json.encodeToString(kept)
        }
        if (kept.isEmpty()) {
            crashesFile().delete()
            return
        }
        writeAtomic(crashesFile(), payload)
    }

    /** tmp + rename atomic write (the repo-wide persistence contract). */
    private fun writeAtomic(target: File, payload: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(payload)
        if (!tmp.renameTo(target)) {
            // Same-volume rename of a fresh tmp cannot legitimately fail;
            // fall back to a direct write rather than losing the record.
            target.writeText(payload)
            tmp.delete()
        }
    }

}
