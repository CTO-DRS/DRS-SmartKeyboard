/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardItem
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * DRS v1.9.0 — the smart clipboard core: every pure, JVM-testable rule of
 * the integrated clipboard system lives here so the engine owns the policy
 * and the UI only renders it.
 *
 * - [ClipboardTextPolicy] caps one stored text at 500,000 characters (the
 *   user-facing contract «النص الواحد يقبل حتى 500,000 حرف» since v1.12.0).
 * - [ClipFileNamer] turns a user-supplied file name into a safe, portable
 *   `.txt` name for the save-as-file feature.
 * - [ClipTextStats] computes the real character/word/line counts shown in
 *   the editor and the item popup.
 * - [ClipboardEditPlan] produces the edited history item (bumping the
 *   timestamp so the item floats to the top, preserving pin and id).
 * - [ClipboardHistoryExport] serializes the whole text history into a
 *   portable JSON document for the settings-screen export.
 */
object ClipboardTextPolicy {
    /**
     * The maximum number of characters one clipboard history entry may
     * retain. DRS v1.12.0 raised the cap from 50,000 to 500,000 at the
     * user's explicit request («تدعم حتى 500000 الف حرف»). The primary
     * (system) clip is never truncated — the cap protects the history
     * database and the panel rendering.
     */
    const val MAX_TEXT_CHARS: Int = 500_000

    /**
     * The editor shows a slowdown warning at or above this length (the
     * user-set limit may be lower; the warning is about responsiveness).
     */
    const val LARGE_TEXT_WARNING_CHARS: Int = 100_000

    fun fits(text: String): Boolean = text.length <= MAX_TEXT_CHARS

    /**
     * The effective limit of a user-chosen [requestedLimit]: clamped into
     * the storage policy's hard cap. A non-positive request falls back to
     * the hard cap so a broken pref can never zero the editor.
     */
    fun effectiveLimit(requestedLimit: Int): Int {
        return if (requestedLimit <= 0) MAX_TEXT_CHARS else requestedLimit.coerceAtMost(MAX_TEXT_CHARS)
    }

    /**
     * Truncates [text] to at most [maxChars] characters (the [effectiveLimit]
     * of the user choice at the call sites). Pure character counting
     * (String.length, UTF-16 units): the result never ends with a lone high
     * surrogate, so the boundary between a kept and a dropped surrogate pair
     * steps back by one character instead of producing corrupted text.
     */
    fun truncateForStorage(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        var end = maxChars
        if (Character.isHighSurrogate(text[end - 1])) end -= 1
        return text.substring(0, end)
    }

    fun truncateForStorage(text: String): String = truncateForStorage(text, MAX_TEXT_CHARS)

    /**
     * DRS v1.22.0: the pure pin-cap decision — «سقف التثبيتات». A pin is
     * allowed when the item is already pinned (idempotent re-pin) or the
     * pinned store still has room under [cap]. A cap of zero refuses every
     * new pin; the pins that already exist are never auto-destroyed by a
     * lowered cap — enforcement happens on new pins only.
     */
    fun pinCapAllows(alreadyPinned: Boolean, pinnedCount: Int, cap: Int): Boolean {
        if (alreadyPinned) return true
        return pinnedCount < cap
    }
}

/**
 * DRS v1.12.0: the editor character limit the user picks in the app
 * settings. The default is the full half-million the storage policy now
 * allows; the effective limit always respects the policy's hard cap.
 */
enum class ClipEditorCharLimit(val id: String, val chars: Int) {
    FIFTY_K("fifty_k", 50_000),
    HUNDRED_K("hundred_k", 100_000),
    TWO_FIFTY_K("two_fifty_k", 250_000),
    FIVE_HUNDRED_K("five_hundred_k", 500_000),
    ;

    /** The limit this choice actually enforces (never above the policy cap). */
    fun effectiveLimit(): Int = ClipboardTextPolicy.effectiveLimit(chars)
}

object ClipFileNamer {
    const val DEFAULT_BASE: String = "drs-clip"
    const val EXTENSION: String = ".txt"
    const val MAX_NAME_CHARS: Int = 80

    // Characters rejected by Windows/Android file systems plus control
    // characters — all replaced with a safe underscore.
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\u0000-\u001f]")

    /**
     * Normalizes a user-supplied file name: illegal characters become
     * underscores, edges lose their spaces and trailing dots (a Windows
     * hazard), the full name (base + extension) is capped at
     * [MAX_NAME_CHARS] characters, and the result always ends with a
     * `.txt` extension so the saved clip always opens as plain text
     * (a user extension like `.json` survives inside the base). An empty
     * result falls back to `drs-clip.txt`.
     */
    fun sanitize(raw: String?, fallback: String = DEFAULT_BASE): String {
        var name = (raw ?: "").replace(ILLEGAL, "_").trim()
        while (name.endsWith(".")) name = name.dropLast(1).trimEnd()
        if (name.isEmpty()) name = fallback
        val hasExtension = name.endsWith(EXTENSION, ignoreCase = true)
        val extension = if (hasExtension) {
            name.substring(name.length - EXTENSION.length)
        } else {
            EXTENSION
        }
        var base = if (hasExtension) {
            name.substring(0, name.length - EXTENSION.length)
        } else {
            name
        }
        if (base.length + extension.length > MAX_NAME_CHARS) {
            base = base.substring(0, MAX_NAME_CHARS - extension.length)
        }
        if (base.isEmpty()) base = fallback
        return base + extension
    }

    /**
     * The default file name for a clip saved at [timestampMs]:
     * `drs-clip-20260925-1430.txt`. The [zone] parameter keeps the function
     * pure so tests pin an exact output; production uses the system zone.
     */
    fun defaultFileName(timestampMs: Long, zone: java.time.ZoneId): String {
        val time = java.time.Instant.ofEpochMilli(timestampMs).atZone(zone)
        // DRS p7 (F11): Locale.ROOT pins Latin digits — the default-locale
        // format produced Arabic-Indic digits in saved file names on ar
        // devices (drs-clip-٢٠٢٦...).
        val stamp = "%04d%02d%02d-%02d%02d".format(
            java.util.Locale.ROOT,
            time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute,
        )
        return "${DEFAULT_BASE}-${stamp}${EXTENSION}"
    }
}

data class ClipTextStats(val chars: Int, val words: Int, val lines: Int) {
    companion object {
        /**
         * Real counts for the popup/editor stats line. Words are maximal
         * whitespace-separated runs; lines count non-empty text as
         * `newlines + 1` (a trailing newline opens a new, still-empty
         * line) and empty text as zero lines.
         */
        fun of(text: String): ClipTextStats {
            val chars = text.length
            val words = text.split(Regex("\\s+")).count { it.isNotEmpty() }
            val lines = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
            return ClipTextStats(chars, words, lines)
        }
    }
}

object ClipboardEditPlan {
    /**
     * The edited variant of [item]: the text is replaced under the storage
     * policy, the timestamp is bumped to [nowMs] so the item floats to the
     * top of the history, and the id / pinned state / media fields are
     * preserved untouched.
     */
    fun plan(item: ClipboardItem, newText: String, nowMs: Long): ClipboardItem {
        return item.copy(
            text = ClipboardTextPolicy.truncateForStorage(newText),
            creationTimestampMs = nowMs,
        )
    }
}

object ClipboardHistoryExport {
    /**
     * The portable shape of one exported entry — text items only, because
     * media bytes live in a content provider and cannot round-trip
     * through a JSON document.
     */
    @Serializable
    data class Entry(val text: String, val createdAt: Long, val pinned: Boolean)

    private val json = Json { encodeDefaults = true }

    fun toJson(items: List<ClipboardItem>): String {
        return json.encodeToString(
            items
                .filter { it.type == ItemType.TEXT && it.text != null }
                .map { Entry(it.text!!, it.creationTimestampMs, it.isPinned) },
        )
    }

    fun parse(document: String): List<Entry> {
        return runCatching { json.decodeFromString<List<Entry>>(document) }.getOrDefault(emptyList())
    }

    const val DEFAULT_FILE_NAME: String = "drs-clipboard-history.json"
}
