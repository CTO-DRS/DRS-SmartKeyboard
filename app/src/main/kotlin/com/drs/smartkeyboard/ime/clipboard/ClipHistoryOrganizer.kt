/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardItem
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

/**
 * DRS v1.12.0 — the comprehensive clipboard organization core.
 *
 * - [ClipHistorySort] / [ClipHistorySorter]: the user-facing sort orders
 *   of the history (newest, oldest, longest, shortest) as a pure,
 *   persisted choice.
 * - [ClipHistorySections] / [ClipHistorySection]: the date grouping of the
 *   panel (pinned, today, yesterday, this week, this month, older) that
 *   replaces the old pinned/recent/other split with a calendar-aware,
 *   deterministic organization.
 * - [ClipItemCategory] / [ClipItemCategoryDetector]: the lightweight smart
 *   badge of every text tile (link, email, phone, code, plain text).
 * - [ClipPanelSearchResults]: the colored search-result cards of the
 *   panel — every hit becomes a navigable card with the match highlighted
 *   inside a preview window.
 *
 * Everything is pure and JVM-testable; the panel only renders it.
 */

/** The user-facing sort orders of the clipboard history. */
enum class ClipHistorySort(val id: String) {
    NEWEST("newest"),
    OLDEST("oldest"),
    LONGEST("longest"),
    SHORTEST("shortest"),
}

object ClipHistorySorter {

    /**
     * Sorts [items] by [sort] without touching the input list. Ties are
     * broken by timestamp (newer first) so the order is deterministic.
     * Media items sort as length zero (text length is the metric).
     */
    fun sort(items: List<ClipboardItem>, sort: ClipHistorySort): List<ClipboardItem> {
        return when (sort) {
            ClipHistorySort.NEWEST -> items.sortedWith(
                compareByDescending<ClipboardItem> { it.creationTimestampMs },
            )
            ClipHistorySort.OLDEST -> items.sortedWith(
                compareBy<ClipboardItem> { it.creationTimestampMs },
            )
            ClipHistorySort.LONGEST -> items.sortedWith(
                compareByDescending<ClipboardItem> { it.text?.length ?: 0 }
                    .thenByDescending { it.creationTimestampMs },
            )
            ClipHistorySort.SHORTEST -> items.sortedWith(
                compareBy<ClipboardItem> { it.text?.length ?: 0 }
                    .thenByDescending { it.creationTimestampMs },
            )
        }
    }
}

/** The calendar-aware sections of the panel, pinned first. */
enum class ClipHistorySection {
    PINNED,
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    OLDER,
}

object ClipHistorySections {

    /** One non-empty section with its items, in panel order. */
    data class Group(val section: ClipHistorySection, val items: List<ClipboardItem>)

    /**
     * Groups [items] into [ClipHistorySection]s using [nowMs] and [zone]
     * (both injected so the function stays pure). Pinned items form the
     * first group; unpinned items fall into calendar buckets — today,
     * yesterday, the last seven days, the last thirty days, and older —
     * each bucket keeping the input order. Empty sections are omitted.
     */
    fun group(items: List<ClipboardItem>, nowMs: Long, zone: ZoneId): List<Group> {
        if (items.isEmpty()) return emptyList()
        val pinned = items.filter { it.isPinned }
        val unpinned = items.filterNot { it.isPinned }

        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val yesterday = today.minusDays(1)
        val weekStart = today.minusDays(6)
        val monthStart = today.minusDays(29)

        val buckets = mutableMapOf<ClipHistorySection, MutableList<ClipboardItem>>()
        for (item in unpinned) {
            val day = Instant.ofEpochMilli(item.creationTimestampMs).atZone(zone).toLocalDate()
            val section = when {
                day == today -> ClipHistorySection.TODAY
                day == yesterday -> ClipHistorySection.YESTERDAY
                !day.isBefore(weekStart) -> ClipHistorySection.THIS_WEEK
                !day.isBefore(monthStart) -> ClipHistorySection.THIS_MONTH
                else -> ClipHistorySection.OLDER
            }
            buckets.getOrPut(section) { mutableListOf() }.add(item)
        }

        val orderedSections = listOf(
            ClipHistorySection.PINNED,
            ClipHistorySection.TODAY,
            ClipHistorySection.YESTERDAY,
            ClipHistorySection.THIS_WEEK,
            ClipHistorySection.THIS_MONTH,
            ClipHistorySection.OLDER,
        )
        return orderedSections.mapNotNull { section ->
            val bucket = if (section == ClipHistorySection.PINNED) pinned else buckets[section]
            bucket?.takeIf { it.isNotEmpty() }?.let { Group(section, it) }
        }
    }
}

/** The smart badge category of a text item. */
enum class ClipItemCategory(val id: String) {
    URL("url"),
    EMAIL("email"),
    PHONE("phone"),
    CODE("code"),
    TEXT("text"),
}

object ClipItemCategoryDetector {

    private val URL_PATTERN = Regex("^(?:https?://|www\\.)\\S+$")
    private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$")

    /**
     * The single trimmed line matching a link or an email, a phone-like
     * number (at least seven digits), a code snippet (by the detector's
     * verdict), or plain text. Null / blank text is TEXT.
     */
    fun detect(text: String?): ClipItemCategory {
        if (text.isNullOrBlank()) return ClipItemCategory.TEXT
        val trimmed = text.trim()
        if (!trimmed.contains('\n')) {
            if (URL_PATTERN.matches(trimmed)) return ClipItemCategory.URL
            if (EMAIL_PATTERN.matches(trimmed)) return ClipItemCategory.EMAIL
            if (trimmed.count { it.isDigit() } >= 7 && trimmed.length <= 24 &&
                !trimmed.contains('\n') && trimmed.none { it.isLetter() }
            ) {
                return ClipItemCategory.PHONE
            }
        }
        // DRS p7 (F6): the same LARGE_TEXT_CHARS guard the popup editor
        // applies — analyze runs per history tile during composition, and a
        // 500k-char text must never feed the per-tile regex storm. Oversized
        // texts are honestly classified TEXT.
        if (trimmed.length <= ClipCodeDetector.LARGE_TEXT_CHARS && ClipCodeDetector.analyze(trimmed).isCode) {
            return ClipItemCategory.CODE
        }
        return ClipItemCategory.TEXT
    }
}

/**
 * One colored search-result card of the panel: the [item] that matched,
 * a preview window around the first hit with [before]/[matched]/[after],
 * and the [colorSlot] the card paints with.
 */
data class ClipPanelResultCard(
    val item: ClipboardItem,
    val before: String,
    val matched: String,
    val after: String,
    val truncatedBefore: Boolean,
    val truncatedAfter: Boolean,
    val colorSlot: Int,
)

object ClipPanelSearchResults {

    /** The maximum number of result cards rendered in the panel. */
    const val MAX_CARDS: Int = 30

    /** The preview window around the first hit. */
    const val PREVIEW_CHARS: Int = 80

    /**
     * Builds the result cards for the [items] that contain [query]
     * (case-insensitive, literal — the same rule the panel filter uses),
     * capped at [maxCards] with a [PREVIEW_CHARS] window around the first
     * hit of each.
     */
    fun buildCards(
        items: List<ClipboardItem>,
        query: String,
        maxCards: Int = MAX_CARDS,
        previewChars: Int = PREVIEW_CHARS,
    ): List<ClipPanelResultCard> {
        if (query.isEmpty()) return emptyList()
        val cards = ArrayList<ClipPanelResultCard>()
        for (item in items) {
            if (cards.size >= maxCards) break
            if (item.type != ItemType.TEXT) continue
            val text = item.text ?: continue
            val found = text.indexOf(query, ignoreCase = true)
            if (found < 0) continue
            val windowStart = (found - previewChars / 2).coerceAtLeast(0)
            val windowEnd = (found + query.length + previewChars / 2).coerceAtMost(text.length)
            cards.add(
                ClipPanelResultCard(
                    item = item,
                    before = text.substring(windowStart, found),
                    matched = text.substring(found, found + query.length),
                    after = text.substring(found + query.length, windowEnd),
                    truncatedBefore = windowStart > 0,
                    truncatedAfter = windowEnd < text.length,
                    colorSlot = ClipResultPalette.slotFor(cards.size),
                ),
            )
        }
        return cards
    }
}
