/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

/**
 * DRS v1.10.0 — the smart popup editor core: every pure, JVM-testable
 * rule of the full-screen clipboard text editor lives here so the engine
 * owns the intelligence and the popup screen only renders it.
 *
 * - [ClipTextTransforms] holds the smart text algorithms exposed as editor
 *   chips (case family, whitespace surgery, line operations, Arabic-aware
 *   normalization, and the smart extractors for links/emails/phones).
 * - [ClipSearchEngine] is the find/replace engine: literal (never regex)
 *   matching with case control, bounded result lists, and wrap-around
 *   navigation over the match ranges.
 * - [ClipEditorHistory] is the bounded undo/redo stack behind every
 *   transform and every replace action.
 * - [ClipFontOption] / [ClipFontSizeOption] are the font customization
 *   choices of the editor, kept as pure enums so tests can pin them.
 */
object ClipTextTransforms {

    // Case family ------------------------------------------------------

    /** Locale-invariant uppercase (Arabic letters pass through unchanged). */
    fun toUpper(text: String): String = text.uppercase(java.util.Locale.ROOT)

    /** Locale-invariant lowercase. */
    fun toLower(text: String): String = text.lowercase(java.util.Locale.ROOT)

    /**
     * Title case: the first letter of every whitespace-delimited word is
     * uppercased and the rest of the word is lowercased. Separators
     * (spaces, tabs, newlines) are preserved exactly as they were.
     */
    fun toTitleCase(text: String): String {
        if (text.isEmpty()) return text
        return Regex("\\S+").replace(text) { word ->
            val w = word.value.lowercase(java.util.Locale.ROOT)
            w.replaceFirstChar { it.uppercase(java.util.Locale.ROOT) }
        }
    }

    /** Swaps the case of every letter; other characters stay as they are. */
    fun invertCase(text: String): String {
        if (text.isEmpty()) return text
        return buildString(text.length) {
            for (c in text) {
                when {
                    Character.isUpperCase(c) -> append(Character.toLowerCase(c))
                    Character.isLowerCase(c) -> append(Character.toUpperCase(c))
                    else -> append(c)
                }
            }
        }
    }

    // Whitespace surgery ------------------------------------------------

    /** Trims each line independently, keeping the line count. */
    fun trimLines(text: String): String {
        if (text.isEmpty()) return text
        return text.lines().joinToString("\n") { it.trim() }
    }

    /**
     * Collapses every horizontal whitespace run (spaces, tabs, non-breaking
     * spaces) into a single space; line separators are left untouched.
     */
    fun collapseHorizontalWhitespace(text: String): String {
        if (text.isEmpty()) return text
        return Regex("[ \\t\\u00a0\\u2007\\u202f]+").replace(text, " ")
    }

    /** Drops every blank line while keeping the order of the rest. */
    fun removeEmptyLines(text: String): String {
        if (text.isEmpty()) return text
        return text.lines().filter { it.isNotBlank() }.joinToString("\n")
    }

    // Line operations ----------------------------------------------------

    /**
     * Removes duplicate lines keeping the first occurrence of each line
     * (exact, case-sensitive match) and the original order.
     */
    fun removeDuplicateLines(text: String): String {
        if (text.isEmpty()) return text
        val seen = LinkedHashSet<String>()
        for (line in text.lines()) seen.add(line)
        return seen.joinToString("\n")
    }

    /** Sorts the lines ascending (natural [String] order). */
    fun sortLinesAscending(text: String): String {
        if (text.isEmpty()) return text
        return text.lines().sorted().joinToString("\n")
    }

    /** Sorts the lines descending. */
    fun sortLinesDescending(text: String): String {
        if (text.isEmpty()) return text
        return text.lines().sortedDescending().joinToString("\n")
    }

    /** Reverses the order of the lines. */
    fun reverseLines(text: String): String {
        if (text.isEmpty()) return text
        return text.lines().asReversed().joinToString("\n")
    }

    /**
     * DRS v1.12.0: prefixes every line with its 1-based number
     * (`1. first`) — the code-review and list-editing helper.
     */
    fun numberLines(text: String): String {
        if (text.isEmpty()) return text
        return text.lines()
            .mapIndexed { index, line -> "${index + 1}. $line" }
            .joinToString("\n")
    }

    // Arabic-aware smart normalization ----------------------------------

    // DRS v1.21.0: unified with DrsTextTools.ARABIC_DIACRITICS — the same
    // tashkeel (harakat + superscript alef + wasla + the small Quranic
    // annotation marks U+06D6–U+06ED) is stripped in the editor and the
    // text-tools panel alike, so «طمس التشكيل» means one thing everywhere.
    private val ARABIC_DIACRITICS = Regex("[\u064B-\u065F\u0670\u06D6-\u06ED]")

    /** Strips Arabic diacritics (tashkeel); letters and spaces are kept. */
    fun removeArabicDiacritics(text: String): String {
        if (text.isEmpty()) return text
        return text.replace(ARABIC_DIACRITICS, "")
    }

    /**
     * Normalizes the common Arabic letter variations used by search and
     * dedup flows: hamza alef forms (أ إ آ) and alef wasla (ٱ) become a
     * plain alef, alef maqsura (ى) becomes ya (ي), and ta marbuta (ة)
     * becomes ha (ه).
     */
    fun normalizeArabicLetters(text: String): String {
        if (text.isEmpty()) return text
        return text
            .replace('\u0623', '\u0627')
            .replace('\u0625', '\u0627')
            .replace('\u0622', '\u0627')
            .replace('\u0671', '\u0627')
            .replace('\u0649', '\u064A')
            .replace('\u0629', '\u0647')
    }

    // Smart extractors ----------------------------------------------------

    private val URL_PATTERN = Regex("(?:https?://|www\\.)\\S+")
    private val EMAIL_PATTERN = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+")
    private val PHONE_CANDIDATE = Regex("\\+?\\d[\\d\\-() ]{5,}\\d")

    /**
     * Extracts links (http/https or www-prefixed) in order of appearance,
     * deduplicated. Trailing sentence punctuation is not stripped — the
     * match ends at the first whitespace.
     */
    fun extractUrls(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        return URL_PATTERN.findAll(text).map { it.value }.distinct().toList()
    }

    /** Extracts email addresses in order of appearance, deduplicated. */
    fun extractEmails(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        return EMAIL_PATTERN.findAll(text).map { it.value }.distinct().toList()
    }

    /**
     * Extracts phone-number-like sequences: at least seven digits spread
     * over a run of digits/spaces/dashes/parentheses, optionally led by a
     * plus sign. The digit-count guard keeps dates and short numbers out.
     */
    fun extractPhoneNumbers(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        return PHONE_CANDIDATE.findAll(text)
            .map { it.value }
            .filter { candidate -> candidate.count { it.isDigit() } >= 7 }
            .distinct()
            .toList()
    }
}

/** One literal match range inside the editor text. */
data class ClipMatch(val start: Int, val end: Int) {
    init {
        require(start >= 0 && end >= start) { "invalid match range [$start, $end)" }
    }
}

object ClipSearchEngine {

    /**
     * The UI renders matches as «active / count»; a runaway query on a
     * 500,000-character text is bounded so navigation stays meaningful.
     */
    const val MAX_MATCHES: Int = 1000

    /**
     * Finds every literal (never regex) occurrence of [query] in [text].
     * An empty/blank query yields no matches. [ignoreCase] uses the
     * locale-invariant region matches; the result is capped at
     * [MAX_MATCHES] non-overlapping ranges scanned left to right.
     */
    fun findMatches(text: String, query: String, ignoreCase: Boolean = true): List<ClipMatch> {
        if (query.isEmpty()) return emptyList()
        val matches = ArrayList<ClipMatch>()
        var from = 0
        val queryLength = query.length
        while (from + queryLength <= text.length && matches.size < MAX_MATCHES) {
            val found = text.indexOf(query, from, ignoreCase)
            if (found < 0) break
            matches.add(ClipMatch(found, found + queryLength))
            from = found + queryLength
        }
        return matches
    }

    /** Replaces exactly the one [match] range with [replacement]. */
    fun replaceOne(text: String, match: ClipMatch, replacement: String): String {
        return text.substring(0, match.start) + replacement + text.substring(match.end)
    }

    /**
     * Replaces every literal occurrence of [query] with [replacement] —
     * the replacement is inserted verbatim (no `$` group semantics), which
     * is the safe behavior for a clipboard editor. Returns the new text
     * and the number of replaced occurrences.
     */
    fun replaceAll(text: String, query: String, replacement: String, ignoreCase: Boolean = true): Pair<String, Int> {
        val matches = findMatches(text, query, ignoreCase)
        if (matches.isEmpty()) return text to 0
        val builder = StringBuilder(text.length + (matches.size * (replacement.length - query.length)).coerceAtLeast(0))
        var cursor = 0
        for (match in matches) {
            builder.append(text, cursor, match.start).append(replacement)
            cursor = match.end
        }
        builder.append(text, cursor, text.length)
        return builder.toString() to matches.size
    }

    /**
     * The next active match index with wrap-around; -1 when there are no
     * matches. [current] is clamped into the valid range first so a stale
     * index (text changed under the UI) never crashes navigation.
     */
    fun nextMatchIndex(count: Int, current: Int): Int {
        if (count <= 0) return -1
        val safe = if (current < 0 || current >= count) 0 else current
        return (safe + 1) % count
    }

    /** The previous active match index with wrap-around; -1 when none. */
    fun prevMatchIndex(count: Int, current: Int): Int {
        if (count <= 0) return -1
        val safe = if (current < 0 || current >= count) 0 else current
        return (safe - 1 + count) % count
    }
}

/**
 * The bounded undo/redo stack of the popup editor. Every transform or
 * replace pushes the previous text; [undo] and [redo] swap the stacks so
 * sequences alternate cleanly. The history caps at [MAX_STATES] entries,
 * dropping the oldest.
 */
class ClipEditorHistory(private val maxStates: Int = MAX_STATES) {

    companion object {
        const val MAX_STATES: Int = 50
    }

    private val past = ArrayDeque<String>()
    private val future = ArrayDeque<String>()

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /** Records [previousText] before a change; a new change voids redo. */
    fun push(previousText: String) {
        past.addLast(previousText)
        while (past.size > maxStates) past.removeFirst()
        future.clear()
    }

    /** Steps back from [currentText]; null when there is nothing to undo. */
    fun undo(currentText: String): String? {
        if (past.isEmpty()) return null
        future.addLast(currentText)
        return past.removeLast()
    }

    /** Steps forward; null when there is nothing to redo. */
    fun redo(currentText: String): String? {
        if (future.isEmpty()) return null
        past.addLast(currentText)
        return future.removeLast()
    }

    fun clear() {
        past.clear()
        future.clear()
    }
}

/**
 * The editor font families. The Compose [androidx.compose.ui.text.font.FontFamily]
 * mapping lives in the UI layer; this pure enum keeps the identity stable
 * for tests and for a future persistence.
 */
enum class ClipFontOption(val id: String) {
    DEFAULT("default"),
    SANS_SERIF("sans_serif"),
    SERIF("serif"),
    MONOSPACE("monospace"),
    CURSIVE("cursive"),
}

/**
 * The editor font sizes in sp — four fixed steps so the chips stay simple
 * and the values stay testable. The property is named [spValue] (not `sp`)
 * so call sites can still use the `Int.sp` Compose extension without the
 * member shadowing it.
 */
enum class ClipFontSizeOption(val id: String, val spValue: Int) {
    SMALL("small", 12),
    NORMAL("normal", 14),
    LARGE("large", 17),
    EXTRA_LARGE("extra_large", 20),
}
