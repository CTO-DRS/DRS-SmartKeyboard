/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

/**
 * DRS v1.12.0 — the colored search-result cards of the smart editor.
 * Every match becomes a card showing its line number and the surrounding
 * same-line context with the matched span identified; the cards cycle a
 * fixed color palette so the results are visually navigable, and tapping
 * a card navigates the editor to that match.
 *
 * Everything here is pure and JVM-testable — the UI layers (the popup
 * window and the in-panel fallback editor) only render these models.
 */

/** The fixed color palette the result cards cycle through. */
object ClipResultPalette {

    /** Six distinct, theme-independent accent colors (ARGB longs). */
    val COLORS: LongArray = longArrayOf(
        0xFFE53935, // red
        0xFF1E88E5, // blue
        0xFF00897B, // teal
        0xFF8E24AA, // purple
        0xFFF4511E, // deep orange
        0xFF2E7D32, // green
    )

    /** The palette size the cards cycle through. */
    const val SIZE: Int = 6

    /** The palette slot of match [index] (negative-safe). */
    fun slotFor(index: Int): Int {
        return if (SIZE == 0) 0 else ((index % SIZE) + SIZE) % SIZE
    }
}

/**
 * One colored result card: the match at [matchIndex] (the index inside
 * the editor's match list) located at [start, end) in the text, found on
 * 1-based [lineNumber], with the same-line context [before]/[after] and
 * the [matched] span itself. The context is capped by the builder; the
 * two truncation flags say whether text was cut off on that side.
 */
data class ClipResultCard(
    val matchIndex: Int,
    val start: Int,
    val end: Int,
    val lineNumber: Int,
    val before: String,
    val matched: String,
    val after: String,
    val truncatedBefore: Boolean,
    val truncatedAfter: Boolean,
) {
    /** The palette slot this card paints with. */
    val colorSlot: Int get() = ClipResultPalette.slotFor(matchIndex)
}

object ClipSearchResults {

    /** Same-line context characters shown on each side of the match. */
    const val DEFAULT_CONTEXT_CHARS: Int = 24

    /** The maximum number of cards built (matches beyond this are not carded). */
    const val MAX_CARDS: Int = 50

    /**
     * DRS v1.21.0 — guards against the stale-snapshot crash window: the
     * matches list is computed on a debounced (150 ms old) text snapshot,
     * while the text the cards and highlights render can shrink underneath
     * (replace-with-shorter, undo). A match computed on the old text must
     * never index the current text out of bounds — matches fully outside
     * the current text are dropped honestly, partially-out ones are clamped,
     * and each survivor carries its ORIGINAL list index so card navigation
     * (activeMatch = card.matchIndex) still targets the real match list.
     */
    private fun normalizedWithIndexes(text: String, matches: List<ClipMatch>): List<Pair<Int, ClipMatch>> {
        if (matches.isEmpty()) return emptyList()
        val length = text.length
        val normalized = ArrayList<Pair<Int, ClipMatch>>(matches.size)
        matches.forEachIndexed { index, match ->
            if (match.start >= length) return@forEachIndexed // wholly beyond the shrunken text
            val end = if (match.end > length) length else match.end
            if (end <= match.start) return@forEachIndexed
            normalized.add(index to (if (end == match.end) match else ClipMatch(match.start, end)))
        }
        return normalized
    }

    /**
     * DRS v1.21.0 — the stale-snapshot crash guard: matches wholly outside
     * the current text are dropped, partially-out ones clamped to bounds.
     */
    fun normalizeAgainst(text: String, matches: List<ClipMatch>): List<ClipMatch> {
        return normalizedWithIndexes(text, matches).map { it.second }
    }

    /**
     * DRS p7 (F3): the stale-match-safe Replace-One guard shared by the
     * panel editor and the popup window. The match list is debounced
     * (150 ms) while the live field text keeps moving; a stale match must
     * never reach [ClipSearchEngine.replaceOne], whose raw substrings throw
     * IndexOutOfBoundsException when `end` outruns the current text (panel:
     * kills the IME process; popup: kills the activity). Returns null — an
     * honest no-op — unless the match is still fully inside the LIVE text;
     * otherwise the replaced text.
     */
    fun replaceOneIfLive(text: String, match: ClipMatch?, replacement: String): String? {
        if (match == null) return null
        if (match.start < 0) return null
        if (match.end <= match.start) return null
        if (match.end > text.length) return null
        return ClipSearchEngine.replaceOne(text, match, replacement)
    }

    /**
     * DRS v1.21.0 — the highlight ranges filtered against the CURRENT field
     * text length. The UI transformation loop applies these to the text it
     * receives at transform time, which can be newer than the matches; a
     * range beyond the field text would throw inside addStyle, so anything
     * out of bounds is dropped here first.
     */
    fun inBounds(ranges: List<ClipHighlightRange>, textLength: Int): List<ClipHighlightRange> {
        if (ranges.isEmpty()) return ranges
        return ranges.filter { it.start < textLength && it.end <= textLength }
    }

    /**
     * The 1-based line number of [offset] in [text] (counting newlines
     * strictly before the offset). Offset 0 or an empty text is line 1.
     */
    fun lineNumberAt(text: String, offset: Int): Int {
        val safe = offset.coerceIn(0, text.length)
        var line = 1
        for (i in 0 until safe) {
            if (text[i] == '\n') line += 1
        }
        return line
    }

    /**
     * Builds one card per match (capped at [maxCards]). The context never
     * crosses a line boundary — a match on its own line simply shows empty
     * sides — and the truncation flags report a clipped side honestly.
     */
    fun buildCards(
        text: String,
        matches: List<ClipMatch>,
        contextChars: Int = DEFAULT_CONTEXT_CHARS,
        maxCards: Int = MAX_CARDS,
    ): List<ClipResultCard> {
        if (matches.isEmpty()) return emptyList()
        // DRS v1.21.0: stale matches never index the current text out of
        // bounds, and each survivor keeps its ORIGINAL match index so the
        // navigation arrows and the active highlight stay on target.
        val safeMatches = normalizedWithIndexes(text, matches)
        if (safeMatches.isEmpty()) return emptyList()
        val cards = ArrayList<ClipResultCard>(safeMatches.size.coerceAtMost(maxCards))
        for ((position, pair) in safeMatches.withIndex()) {
            if (position >= maxCards) break
            val (originalIndex, match) = pair
            val lineStart = text.lastIndexOf('\n', (match.start - 1).coerceAtLeast(-1)) + 1
            val lineEnd = text.indexOf('\n', match.end).let { if (it < 0) text.length else it }
            val wantBeforeStart = match.start - contextChars
            val beforeStart = wantBeforeStart.coerceAtLeast(lineStart)
            val wantAfterEnd = match.end + contextChars
            val afterEnd = wantAfterEnd.coerceAtMost(lineEnd)
            cards.add(
                ClipResultCard(
                    matchIndex = originalIndex,
                    start = match.start,
                    end = match.end,
                    lineNumber = lineNumberAt(text, match.start),
                    before = text.substring(beforeStart, match.start),
                    matched = text.substring(match.start, match.end),
                    after = text.substring(match.end, afterEnd),
                    truncatedBefore = wantBeforeStart > lineStart,
                    truncatedAfter = wantAfterEnd < lineEnd,
                ),
            )
        }
        return cards
    }

    /**
     * The highlight ranges for the editor text: one range per match with
     * an active flag for the currently navigated match and the palette
     * slot the match paints with. Pure — the UI wraps these into styles.
     */
    fun highlightRanges(matches: List<ClipMatch>, activeIndex: Int): List<ClipHighlightRange> {
        if (matches.isEmpty()) return emptyList()
        return matches.mapIndexed { index, match ->
            ClipHighlightRange(
                match.start,
                match.end,
                index == activeIndex,
                ClipResultPalette.slotFor(index),
            )
        }
    }

    /**
     * DRS v1.21.0 — the text-aware variant the editors render through:
     * the matches are normalized against the current text first, so the
     * produced ranges can never exceed the text they will paint over,
     * and the original indexes drive both the active flag and the palette.
     */
    fun highlightRanges(
        text: String,
        matches: List<ClipMatch>,
        activeIndex: Int,
    ): List<ClipHighlightRange> {
        if (matches.isEmpty()) return emptyList()
        return normalizedWithIndexes(text, matches).map { (originalIndex, match) ->
            ClipHighlightRange(
                match.start,
                match.end,
                originalIndex == activeIndex,
                ClipResultPalette.slotFor(originalIndex),
            )
        }
    }
}

/** One highlighted span inside the editor text. Pure data. */
data class ClipHighlightRange(
    val start: Int,
    val end: Int,
    val isActive: Boolean,
    val colorSlot: Int,
) {
    init {
        require(start >= 0 && end >= start) { "invalid highlight range [$start, $end)" }
    }
}

/**
 * DRS v1.13.0 — a minimal read-only view of the editor's laid-out text:
 * the visual rows (wrapping included) the direct line jump positions
 * itself by. The UI adapts the real TextLayoutResult into this; the JVM
 * tests pin the jump math against tiny fakes. Offsets are characters,
 * rows are 0-based visual lines, and every edge value is in pixels.
 */
interface ClipLineLayout {

    /** The length of the laid-out text. */
    val textLength: Int

    /** The number of visual rows (wrapped lines included). */
    val rowCount: Int

    /** The visual row of [offset] (0-based). */
    fun rowForOffset(offset: Int): Int

    /** The top edge (px) of [row], relative to the text top. */
    fun rowTop(row: Int): Int

    /** The bottom edge (px) of [row], relative to the text top. */
    fun rowBottom(row: Int): Int
}

/**
 * DRS v1.13.0 — the direct line jump the user asked for («اضغط على البطاقة
 * فينتقل مباشرة إلى السطر»): tapping a result card — or walking the matches
 * with the navigation arrows — scrolls the editor straight to the row that
 * holds that match. The row is centered inside the viewport when it fits
 * and pinned just under the top edge when it is taller than the viewport
 * (a long wrapped line); the returned offset is always clamped to the real
 * scroll range. Pure — the UI supplies the layout snapshot and animates to
 * the returned offset; a null layout or an out-of-bounds offset honestly
 * yields null (no scroll).
 */
object ClipResultJump {

    /** The peek gap (px) kept above a pinned taller-than-viewport row. */
    const val TOP_PEEK_PX: Int = 16

    /**
     * The scroll offset (px) that brings the row holding [offset] into
     * view, or null when there is no layout yet or [offset] is outside
     * the laid-out text. [viewportPx] is the visible height of the editor
     * viewport and [maxScrollPx] the scroll range's upper bound.
     *
     * DRS v1.14.0: [centerRow] is the user's chosen jump alignment from
     * the comprehensive app settings — true centers the row in the
     * viewport (the v1.13.0 behavior), false always pins the row head
     * just under the top edge. Both are clamped to the real scroll range.
     */
    fun scrollOffsetFor(
        layout: ClipLineLayout?,
        offset: Int,
        viewportPx: Int,
        maxScrollPx: Int,
        topPeekPx: Int = TOP_PEEK_PX,
        centerRow: Boolean = true,
    ): Int? {
        if (layout == null) return null
        if (offset < 0 || offset > layout.textLength) return null
        if (layout.rowCount <= 0) return null
        val row = layout.rowForOffset(offset).coerceIn(0, layout.rowCount - 1)
        val top = layout.rowTop(row)
        val bottom = layout.rowBottom(row)
        val rowHeight = bottom - top
        val target = if (!centerRow || viewportPx <= 0 || rowHeight >= viewportPx) {
            // The pinned alignment, no viewport knowledge yet, or a wrapped
            // giant row: pin the row head just under the top edge.
            top - topPeekPx
        } else {
            // Center the row inside the viewport.
            top - (viewportPx - rowHeight) / 2
        }
        return target.coerceIn(0, maxScrollPx.coerceAtLeast(0))
    }
}
