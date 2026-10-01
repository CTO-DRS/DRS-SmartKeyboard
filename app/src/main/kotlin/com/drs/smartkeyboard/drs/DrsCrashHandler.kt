/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Complementary crash diagnostics for the DRS layer. Writes a sanitized,
 * local-only crash log (never any typed/user content) that the
 * DRS Keyboard Diagnostics screen can display. The previously installed
 * handler (e.g. CrashUtility) still runs afterwards.
 */
object DrsCrashHandler {

    private const val FILE_NAME = "drs_crash_log.txt"
    private const val MAX_LOG_CHARS = 24_000

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var logFile: File? = null

    fun install(context: Context) {
        if (defaultHandler != null) return
        synchronized(this) {
            if (defaultHandler != null) return
            logFile = File(context.noBackupFilesDir, FILE_NAME)
            // DRS P1 (ADR 0004): bind the structured local crash registry to
            // the same path and count this process start as one session.
            runCatching {
                DrsCrashReports.install(context).registerSessionStart()
            }
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    append(thread, throwable)
                } catch (_: Throwable) {
                    // Logging must never break crash handling.
                }
                runCatching {
                    // DRS P1: also persist into the structured, sanitized
                    // local registry (ADR 0004) — class name and stack hash
                    // only, never any user content.
                    DrsCrashReports.get()?.record(thread, throwable)
                }
                runCatching {
                    // DRS v1.0.6: also surface the crash in the sanitized
                    // in-memory event log (class name only, no user data).
                    DrsEventLog.record(
                        DrsEventLog.Level.ERROR,
                        DrsEventLog.Categories.CRASH,
                        "${thread.name}: ${DrsEventLog.throwableDetail(throwable)}",
                    )
                }
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun append(thread: Thread, throwable: Throwable) {
        val f = logFile ?: return
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stack = sw.toString().lines().take(24).joinToString("\n")
        val entry = buildString {
            append("==== $stamp ====\n")
            append("thread: ${thread.name}\n")
            append("app: ${com.drs.smartkeyboard.BuildConfig.VERSION_NAME} (${com.drs.smartkeyboard.BuildConfig.VERSION_CODE})\n")
            append("android: ${Build.VERSION.SDK_INT}\n")
            append("$stack\n\n")
        }
        synchronized(this) {
            val existing = if (f.isFile) f.readText() else ""
            var combined = existing + entry
            if (combined.length > MAX_LOG_CHARS) {
                combined = combined.substring(combined.length - MAX_LOG_CHARS)
            }
            f.writeText(combined)
        }
    }

    fun readLog(): String {
        val f = logFile ?: return ""
        return try {
            if (f.isFile) f.readText() else ""
        } catch (_: Throwable) {
            ""
        }
    }

    fun clearLog() {
        try {
            logFile?.delete()
        } catch (_: Throwable) {
        }
    }
}
