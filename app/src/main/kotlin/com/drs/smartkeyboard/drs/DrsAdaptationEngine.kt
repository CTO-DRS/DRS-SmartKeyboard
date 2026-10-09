/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * A single adaptation suggestion computed from local usage statistics.
 * Suggestions are OPTIONAL: the user accepts or dismisses them from the
 * DRS Control Center; settings are never changed automatically.
 */
data class DrsSuggestion(
    val id: String,
    val metric: Long,
)

/**
 * Local, privacy-first adaptation engine. Counts anonymous usage signals
 * (numbers, symbols, clipboard, gestures, tech tools) in memory and flushes
 * aggregated counters to [DrsStore] periodically. No keystroke content is
 * ever stored or transmitted.
 */
object DrsAdaptationEngine {

    private const val FLUSH_INTERVAL = 128L

    private val keyPresses = AtomicLong()
    private val numberPresses = AtomicLong()
    private val symbolPresses = AtomicLong()
    private val emojiUses = AtomicLong()
    private val clipboardUses = AtomicLong()
    private val shortcutUses = AtomicLong()
    private val techToolUses = AtomicLong()
    private val gestureUses = AtomicLong()
    // DRS v2.22.0: the per-feature counters (نصوصي، قاموسي التشكيلي،
    // الحركات، الرموز، الحروف، الأرقام) — completing the single-drain
    // doctrine for every feature: v2.21.0's saved-texts counter wrote the
    // lifetime stat directly, bypassing the one drain point and silently
    // dropping from the day buckets. Every feature counter now flows
    // through the same drain the rest of the counters obey.
    private val myTextsUses = AtomicLong()
    private val myLexiconUses = AtomicLong()
    private val harakatUses = AtomicLong()
    private val symbolUses = AtomicLong()
    private val letterUses = AtomicLong()
    private val numberUses = AtomicLong()
    // DRS v1.6.0: committed suggestion-row entries (the engine already
    // routes every accept through KeyboardManager.commitCandidate —
    // this counts the event, never the word itself).
    private val suggestionAccepts = AtomicLong()
    // DRS v1.0.5: per-tool counts for the "most used tools" surface. Only
    // tool KeyCodes are counted (never characters), in-memory, flushed
    // aggregated with the rest of the usage stats.
    private val toolCounts = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private val toolUseTotal = AtomicLong()
    // DRS v1.7.0: input starts per context mode — recorded from
    // DrsRuntimeState.onInputStarted (a DETECTED attribute of the focused
    // field, never its content) and drained into the day buckets so the
    // stats screen can show how typing time splits across contexts.
    private val contextCounts = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val flushing = AtomicBoolean(false)

    /** Cheap per-keystroke recording. Must never block or throw. */
    fun recordKey(code: Int) {
        val state = DrsStore.state.value
        if (!state.adaptationEnabled) return
        keyPresses.incrementAndGet()
        when {
            code in '0'.code..'9'.code -> numberPresses.incrementAndGet()
            code >= 0x1F300 -> emojiUses.incrementAndGet()
            code > 0x20 && !Character.isLetterOrDigit(code) -> symbolPresses.incrementAndGet()
        }
        maybeFlush(keyPresses)
    }

    fun recordClipboardUse() {
        DrsEconomy.recordFeatureUse("earn_feature_clipboard")
        if (DrsStore.state.value.adaptationEnabled) {
            clipboardUses.incrementAndGet()
            maybeFlush(clipboardUses)
        }
    }

    fun recordShortcutUse() {
        DrsEconomy.recordFeatureUse("earn_feature_shortcut")
        if (DrsStore.state.value.adaptationEnabled) {
            shortcutUses.incrementAndGet()
            maybeFlush(shortcutUses)
        }
    }

    fun recordTechToolUse() {
        DrsEconomy.recordFeatureUse("earn_feature_tech_tool")
        if (DrsStore.state.value.adaptationEnabled) {
            techToolUses.incrementAndGet()
            maybeFlush(techToolUses)
        }
    }

    fun recordGestureUse() {
        DrsEconomy.recordFeatureUse("earn_feature_gesture")
        if (DrsStore.state.value.adaptationEnabled) {
            gestureUses.incrementAndGet()
            maybeFlush(gestureUses)
        }
    }

    /**
     * DRS v1.6.0: records one committed suggestion-row entry. The call
     * site is KeyboardManager.commitCandidate — the single real accept
     * path (tapped candidates AND auto-committed completions). Anonymous
     * count only; which word was accepted is never stored.
     */
    fun recordSuggestionAccept() {
        if (DrsStore.state.value.adaptationEnabled) {
            suggestionAccepts.incrementAndGet()
            maybeFlush(suggestionAccepts)
        }
    }

    /**
     * DRS v1.0.5: records one use of a smart tool (quick action) by its
     * KeyCode. Anonymous count only — the tool code tells which button was
     * pressed, never what was typed. Powers the "most used tools" section.
     */
    fun recordToolUse(code: Int) {
        toolCounts.merge(code, 1L, Long::plus)
        if (DrsStore.state.value.adaptationEnabled) {
            // DRS p9 (D-6): the flush gate counts tool events — without the
            // increment maybeFlush fired its every-128th-event CAS on EVERY
            // event, flushing (and rewriting the state) per tool press.
            toolUseTotal.incrementAndGet()
            maybeFlush(toolUseTotal)
        }
    }

    /**
     * DRS v2.22.0: records one use of a smart FEATURE (panel insertion,
     * saved-text insertion, personal-lexicon hit) — the closed set the
     * per-feature surfaces report. Anonymous count only; never WHAT was
     * inserted, never WHICH word was vocalized. The call sites gate
     * incognito before calling (same contract as recordClipboardUse).
     */
    fun recordMyTextsUse() {
        if (DrsStore.state.value.adaptationEnabled) {
            myTextsUses.incrementAndGet()
            maybeFlush(myTextsUses)
        }
    }

    fun recordMyLexiconUse() {
        if (DrsStore.state.value.adaptationEnabled) {
            myLexiconUses.incrementAndGet()
            maybeFlush(myLexiconUses)
        }
    }

    fun recordHarakatUse() {
        if (DrsStore.state.value.adaptationEnabled) {
            harakatUses.incrementAndGet()
            maybeFlush(harakatUses)
        }
    }

    fun recordSymbolUse() {
        if (DrsStore.state.value.adaptationEnabled) {
            symbolUses.incrementAndGet()
            maybeFlush(symbolUses)
        }
    }

    fun recordLetterUse() {
        if (DrsStore.state.value.adaptationEnabled) {
            letterUses.incrementAndGet()
            maybeFlush(letterUses)
        }
    }

    fun recordNumberUse() {
        if (DrsStore.state.value.adaptationEnabled) {
            numberUses.incrementAndGet()
            maybeFlush(numberUses)
        }
    }

    /**
     * DRS v1.7.0: records one input session start in a context mode (the
     * mode name only — a detected field attribute, never content). Cheap,
     * never throws.
     */
    fun recordContextStart(modeName: String) {
        if (!DrsStore.state.value.adaptationEnabled) return
        contextCounts.merge(modeName, 1L, Long::plus)
        // DRS p9 (D-6): same fix as recordToolUse — the shared gate counter
        // must actually count its events before maybeFlush consults it.
        toolUseTotal.incrementAndGet()
        maybeFlush(toolUseTotal)
    }

    private fun maybeFlush(counter: AtomicLong) {
        if (counter.get() % FLUSH_INTERVAL != 0L) return
        if (!flushing.compareAndSet(false, true)) return
        flushPending()
        flushing.set(false)
    }

    /**
     * DRS v1.0.8: drains the pending counters ONCE and merges them into
     * both the lifetime usage stats and (when enabled) today's daily
     * bucket. Single drain point keeps the two views consistent.
     */
    private fun flushPending() {
        val delta = drain()
        DrsStore.update { state ->
            val merged = state.copy(usage = state.usage.merge(delta))
            if (merged.dailyStatsEnabled) {
                merged.copy(dailyStats = DrsDailyStats.mergeInto(merged.dailyStats, delta))
            } else {
                merged
            }
        }
    }

    private fun drain(): DrsUsageStats {
        val drainedTools: Map<Int, Long> = if (toolCounts.isEmpty()) {
            emptyMap()
        } else {
            val snapshot = HashMap(toolCounts)
            toolCounts.clear()
            snapshot
        }
        val drainedContexts: Map<String, Long> = if (contextCounts.isEmpty()) {
            emptyMap()
        } else {
            val snapshot = HashMap(contextCounts)
            contextCounts.clear()
            snapshot
        }
        return DrsUsageStats(
            keyPresses = keyPresses.getAndSet(0),
            numberPresses = numberPresses.getAndSet(0),
            symbolPresses = symbolPresses.getAndSet(0),
            emojiUses = emojiUses.getAndSet(0),
            clipboardUses = clipboardUses.getAndSet(0),
            shortcutUses = shortcutUses.getAndSet(0),
            techToolUses = techToolUses.getAndSet(0),
            gestureUses = gestureUses.getAndSet(0),
            myTextsUses = myTextsUses.getAndSet(0),
            myLexiconUses = myLexiconUses.getAndSet(0),
            harakatUses = harakatUses.getAndSet(0),
            symbolUses = symbolUses.getAndSet(0),
            letterUses = letterUses.getAndSet(0),
            numberUses = numberUses.getAndSet(0),
            suggestionAccepts = suggestionAccepts.getAndSet(0),
            toolUses = drainedTools,
            contextStarts = drainedContexts,
        )
    }


    private fun DrsUsageStats.merge(delta: DrsUsageStats): DrsUsageStats {
        val mergedTools = if (delta.toolUses.isEmpty()) {
            toolUses
        } else {
            val merged = HashMap(toolUses)
            for ((code, count) in delta.toolUses) {
                merged[code] = (merged[code] ?: 0L) + count
            }
            // Cap so the stored map stays tiny: keep the 32 most used tools.
            merged.entries
                .sortedByDescending { it.value }
                .take(32)
                .associate { it.toPair() }
        }
        // DRS v1.7.0: context-mode starts merge too, capped to the known
        // mode names so the stored map stays tiny by construction.
        val mergedContexts = if (delta.contextStarts.isEmpty()) {
            contextStarts
        } else {
            val merged = HashMap(contextStarts)
            for ((mode, count) in delta.contextStarts) {
                merged[mode] = (merged[mode] ?: 0L) + count
            }
            merged.entries
                .filter { it.key in DrsContextMode.entries.map { m -> m.name } }
                .associate { it.toPair() }
        }
        return DrsUsageStats(
            keyPresses = keyPresses + delta.keyPresses,
            numberPresses = numberPresses + delta.numberPresses,
            symbolPresses = symbolPresses + delta.symbolPresses,
            emojiUses = emojiUses + delta.emojiUses,
            clipboardUses = clipboardUses + delta.clipboardUses,
            shortcutUses = shortcutUses + delta.shortcutUses,
            techToolUses = techToolUses + delta.techToolUses,
            gestureUses = gestureUses + delta.gestureUses,
            myTextsUses = myTextsUses + delta.myTextsUses,
            myLexiconUses = myLexiconUses + delta.myLexiconUses,
            harakatUses = harakatUses + delta.harakatUses,
            symbolUses = symbolUses + delta.symbolUses,
            letterUses = letterUses + delta.letterUses,
            numberUses = numberUses + delta.numberUses,
            suggestionAccepts = suggestionAccepts + delta.suggestionAccepts,
            toolUses = mergedTools,
            contextStarts = mergedContexts,
        )
    }

    /**
     * Computes optional suggestions from usage statistics. Suggestions fire
     * only when a clear preference signal exists AND the corresponding
     * feature is currently disabled.
     */
    fun computeSuggestions(state: DrsState): List<DrsSuggestion> {
        if (!state.adaptationEnabled) return emptyList()
        val usage = state.usage
        val activeProfile = DrsProfileManager.activeProfile(state)
        val suggestions = mutableListOf<DrsSuggestion>()

        if (usage.numberPresses > 60 &&
            usage.numberPresses * 20 > usage.keyPresses &&
            activeProfile?.numberRow != true
        ) {
            suggestions.add(DrsSuggestion(DrsSuggestionIds.NUMBER_ROW, usage.numberPresses))
        }
        if (usage.symbolPresses > 40 &&
            usage.symbolPresses * 20 > usage.keyPresses &&
            activeProfile?.techStripEnabled != true &&
            state.userPath == DrsUserPath.NORMAL.name
        ) {
            suggestions.add(DrsSuggestion(DrsSuggestionIds.TECH_STRIP, usage.symbolPresses))
        }
        if (usage.clipboardUses > 15 && activeProfile?.clipboardHistoryEnabled != true) {
            suggestions.add(DrsSuggestion(DrsSuggestionIds.CLIPBOARD_HISTORY, usage.clipboardUses))
        }
        if (usage.symbolPresses > 120 && state.userPath == DrsUserPath.NORMAL.name) {
            suggestions.add(DrsSuggestion(DrsSuggestionIds.TECHNICAL_PATH, usage.symbolPresses))
        }
        if (usage.keyPresses > 300 && state.shortcuts.isEmpty() && usage.shortcutUses == 0L) {
            suggestions.add(DrsSuggestion(DrsSuggestionIds.SHORTCUTS, usage.keyPresses))
        }
        return suggestions.filter { it.id !in state.dismissedSuggestionIds }
    }

    fun accept(suggestion: DrsSuggestion) {
        when (suggestion.id) {
            DrsSuggestionIds.NUMBER_ROW -> {
                val state = DrsStore.state.value
                DrsProfileManager.activeProfile(state)?.let { profile ->
                    DrsProfileManager.applyProfile(profile.copy(numberRow = true))
                }
            }
            DrsSuggestionIds.TECH_STRIP -> {
                val state = DrsStore.state.value
                DrsProfileManager.activeProfile(state)?.let { profile ->
                    DrsProfileManager.applyProfile(profile.copy(techStripEnabled = true))
                }
            }
            DrsSuggestionIds.CLIPBOARD_HISTORY -> {
                val state = DrsStore.state.value
                DrsProfileManager.activeProfile(state)?.let { profile ->
                    DrsProfileManager.applyProfile(profile.copy(clipboardHistoryEnabled = true))
                }
            }
            DrsSuggestionIds.TECHNICAL_PATH -> setUserPath(DrsUserPath.TECHNICAL)
        }
        dismiss(suggestion.id)
    }

    fun dismiss(suggestionId: String) {
        DrsStore.update { state ->
            state.copy(dismissedSuggestionIds = state.dismissedSuggestionIds + suggestionId)
        }
    }

    /**
     * Changes the active system. Delegates to [DrsProfileManager.applySystem]
     * so the system's own profile (with its own full configuration) is
     * applied and the path is stored in one coherent step, keeping data.
     */
    fun setUserPath(path: DrsUserPath) {
        DrsProfileManager.applySystem(path)
    }
}
