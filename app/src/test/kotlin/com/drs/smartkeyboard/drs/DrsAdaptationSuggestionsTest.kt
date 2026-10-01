/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

/**
 * DRS p9: the local adaptation layer — computeSuggestions thresholds and
 * gates (pure over DrsState) plus the flush bookkeeping of the recording
 * counters.
 *
 * The engine counters are process globals that cannot be reset (drain() is
 * private), and nothing else in the suite records into them, so the flush
 * test drives a guaranteed drain by window arithmetic: ANY 128 consecutive
 * increments of a counter contain exactly one multiple of the 128-event
 * flush interval, so a flush fires no matter where the counter started.
 *
 * NOTE on the 32-entry toolUses cap: the cap is applied inside the private
 * merge when the drained map exceeds 32 tools; from the public API the
 * drain timing cannot be controlled finely enough to guarantee >32 distinct
 * tools in one drain window, so only the counter bookkeeping is pinned
 * here — the cap itself stays covered by reading (DrsUsageStats.merge in
 * DrsAdaptationEngine).
 */
class DrsAdaptationSuggestionsTest : FunSpec({

    fun profile(
        id: String,
        numberRow: Boolean = false,
        techStripEnabled: Boolean = false,
        clipboardHistoryEnabled: Boolean = false,
    ) = DrsProfile(
        id = id, name = id, path = DrsUserPath.NORMAL.name,
        dayThemeId = "t:day", nightThemeId = "t:night",
        numberRow = numberRow, techStripEnabled = techStripEnabled,
        suggestionsEnabled = true, clipboardHistoryEnabled = clipboardHistoryEnabled,
        audioFeedbackEnabled = false, hapticFeedbackEnabled = false,
    )

    fun stateOf(
        userPath: String = DrsUserPath.NORMAL.name,
        usage: DrsUsageStats = DrsUsageStats(),
        profiles: List<DrsProfile> = emptyList(),
        activeProfileId: String = "",
        shortcuts: List<DrsShortcut> = emptyList(),
        dismissed: List<String> = emptyList(),
        adaptationEnabled: Boolean = true,
    ): DrsState = DrsState(
        onboardingDone = true,
        userPath = userPath,
        usage = usage,
        profiles = profiles,
        activeProfileId = activeProfileId,
        shortcuts = shortcuts,
        dismissedSuggestionIds = dismissed,
        adaptationEnabled = adaptationEnabled,
    )

    test("the all-fire usage state produces all five suggestions in engine order") {
        val state = stateOf(
            usage = DrsUsageStats(
                keyPresses = 1000,
                numberPresses = 100,   // > 60 and 20% of 1000
                symbolPresses = 200,   // > 40 and > 120
                clipboardUses = 20,    // > 15
            ),
        )
        DrsAdaptationEngine.computeSuggestions(state).map { it.id } shouldBe listOf(
            DrsSuggestionIds.NUMBER_ROW,
            DrsSuggestionIds.TECH_STRIP,
            DrsSuggestionIds.CLIPBOARD_HISTORY,
            DrsSuggestionIds.TECHNICAL_PATH,
            DrsSuggestionIds.SHORTCUTS,
        )
    }

    test("an active profile with a number row suppresses the number-row suggestion") {
        val state = stateOf(
            usage = DrsUsageStats(keyPresses = 1000, numberPresses = 100),
            profiles = listOf(profile("p1", numberRow = true)),
            activeProfileId = "p1",
        )
        DrsAdaptationEngine.computeSuggestions(state).map { it.id } shouldNotContain
            DrsSuggestionIds.NUMBER_ROW
    }

    test("the tech-strip suggestion is NORMAL-path-only (gate on the active path)") {
        val usage = DrsUsageStats(keyPresses = 1000, symbolPresses = 200)
        val normal = stateOf(usage = usage)
        DrsAdaptationEngine.computeSuggestions(normal).map { it.id } shouldContain
            DrsSuggestionIds.TECH_STRIP
        val technical = stateOf(userPath = DrsUserPath.TECHNICAL.name, usage = usage)
        val ids = DrsAdaptationEngine.computeSuggestions(technical).map { it.id }
        ids shouldNotContain DrsSuggestionIds.TECH_STRIP
        ids shouldNotContain DrsSuggestionIds.TECHNICAL_PATH
        ids shouldContain DrsSuggestionIds.SHORTCUTS
    }

    test("TECHNICAL_PATH needs strictly more than 120 symbol presses") {
        val atBoundary = stateOf(usage = DrsUsageStats(keyPresses = 400, symbolPresses = 120))
        DrsAdaptationEngine.computeSuggestions(atBoundary).map { it.id } shouldNotContain
            DrsSuggestionIds.TECHNICAL_PATH
        val oneOver = stateOf(usage = DrsUsageStats(keyPresses = 400, symbolPresses = 121))
        DrsAdaptationEngine.computeSuggestions(oneOver).map { it.id } shouldContain
            DrsSuggestionIds.TECHNICAL_PATH
    }

    test("the shortcuts suggestion needs an empty shortcut list AND zero uses") {
        val usage = DrsUsageStats(keyPresses = 1000)
        val withShortcut = stateOf(
            usage = usage,
            shortcuts = listOf(DrsShortcut(id = 1, shortcut = "brb", expansion = "be right back")),
        )
        DrsAdaptationEngine.computeSuggestions(withShortcut).map { it.id } shouldNotContain
            DrsSuggestionIds.SHORTCUTS
        val withUses = stateOf(usage = usage.copy(shortcutUses = 1))
        DrsAdaptationEngine.computeSuggestions(withUses).map { it.id } shouldNotContain
            DrsSuggestionIds.SHORTCUTS
        DrsAdaptationEngine.computeSuggestions(stateOf(usage = usage)).map { it.id } shouldContain
            DrsSuggestionIds.SHORTCUTS
    }

    test("dismissed ids are filtered and the master switch mutes everything") {
        val usage = DrsUsageStats(
            keyPresses = 1000, numberPresses = 100,
            symbolPresses = 200, clipboardUses = 20,
        )
        val dismissed = stateOf(
            usage = usage,
            dismissed = listOf(DrsSuggestionIds.NUMBER_ROW, DrsSuggestionIds.SHORTCUTS),
        )
        val ids = DrsAdaptationEngine.computeSuggestions(dismissed).map { it.id }
        ids shouldNotContain DrsSuggestionIds.NUMBER_ROW
        ids shouldNotContain DrsSuggestionIds.SHORTCUTS
        ids shouldContain DrsSuggestionIds.TECH_STRIP

        val muted = stateOf(usage = usage, adaptationEnabled = false)
        DrsAdaptationEngine.computeSuggestions(muted) shouldBe emptyList()
    }

    test("tool uses are counted and reach usage.toolUses after the interval flush") {
        runBlocking {
            DrsStore.updateNow { _ -> DrsState(onboardingDone = true) }
        }
        val toolCode = 987654
        repeat(128) { DrsAdaptationEngine.recordToolUse(toolCode) }
        // the key counter crosses its own 128-window here, guaranteeing at
        // least one drain that sweeps up everything the tool counter had
        // left pending
        repeat(128) { DrsAdaptationEngine.recordKey('a'.code) }
        runBlocking { DrsStore.updateNow { it } }
        val usage = DrsStore.state.value.usage
        usage.toolUses[toolCode] shouldBe 128L
        usage.keyPresses shouldBe 128L
    }
})
