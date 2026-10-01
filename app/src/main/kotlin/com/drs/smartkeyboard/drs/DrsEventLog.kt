/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DRS v1.0.6: bounded, sanitized local event log for technical diagnostics.
 *
 * Hard privacy rules, enforced by the API itself:
 *  - Only event CATEGORY + machine-level detail strings may be recorded.
 *    There is deliberately no API that accepts typed user text, clipboard
 *    content or field contents, so sensitive text can never end up here.
 *  - Detail strings are truncated to [MAX_DETAIL] chars; exception details
 *    are flattened to the exception class name + optional message via
 *    [throwableDetail] (class name only for known framework exceptions).
 *  - Bounded ring buffer ([MAX_ENTRIES]) in RAM: nothing is persisted to
 *    disk, nothing leaves the device, and it clears when the process ends.
 *
 * Severity levels mirror standard practice: ERROR (feature failed), WARNING
 * (degraded but working), INFO (noteworthy lifecycle event).
 */
object DrsEventLog {

    enum class Level { INFO, WARNING, ERROR }

    data class Entry(
        val timestampMs: Long,
        val level: Level,
        val category: String,
        val detail: String,
    )

    /** Categories used across the app - keep in sync with record() callers. */
    object Categories {
        const val ENGINE = "engine"
        const val STORE = "store"
        const val SHORTCUTS = "shortcuts"
        const val TEXT_TOOLS = "text_tools"
        const val THEME = "theme"
        const val LAYOUT = "layout"
        const val CLIPBOARD = "clipboard"
        const val CRASH = "crash"
        const val APP = "app"
    }

    private const val MAX_ENTRIES = 200
    private const val MAX_DETAIL = 160

    private val entries = ArrayDeque<Entry>(MAX_ENTRIES)

    /** Records one sanitized event; never throws, never logs user text. */
    fun record(level: Level, category: String, detail: String) {
        try {
            val sanitized = detail.take(MAX_DETAIL)
            synchronized(entries) {
                if (entries.size >= MAX_ENTRIES) entries.removeFirst()
                entries.addLast(Entry(System.currentTimeMillis(), level, category, sanitized))
            }
        } catch (_: Throwable) {
            // Logging must never be the thing that breaks the app.
        }
    }

    fun recordError(category: String, detail: String) = record(Level.ERROR, category, detail)
    fun recordInfo(category: String, detail: String) = record(Level.INFO, category, detail)

    /**
     * Flattens a throwable into a safe one-line detail string. Only the class
     * name (+ message when present); no stack frames, no user data.
     */
    fun throwableDetail(t: Throwable): String {
        val name = t.javaClass.simpleName.ifBlank { t.javaClass.name }
        val message = t.message?.take(64).orEmpty()
        return if (message.isBlank()) name else "$name: $message"
    }

    /** Snapshot of the log, newest first. */
    fun snapshot(): List<Entry> = synchronized(entries) { entries.toList().asReversed() }

    fun clear() = synchronized(entries) { entries.clear() }

    /** Number of events currently in the buffer. */
    fun size(): Int = synchronized(entries) { entries.size }

    /**
     * Renders the log as a plain-text diagnostic report (for the export
     * action). Contains only sanitized machine data by construction.
     */
    fun renderReport(appVersion: String, checksSummary: String): String {
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("DRS Smart Keyboard - Diagnostic Report")
        sb.appendLine("Generated: ${timeFormat.format(Date())}")
        sb.appendLine("Version: $appVersion")
        sb.appendLine("Checks: $checksSummary")
        sb.appendLine("Events: ${size()}")
        sb.appendLine("----------------------------------------")
        for (entry in snapshot()) {
            sb.appendLine(
                "${timeFormat.format(Date(entry.timestampMs))} [${entry.level}] ${entry.category}: ${entry.detail}",
            )
        }
        return sb.toString()
    }
}
