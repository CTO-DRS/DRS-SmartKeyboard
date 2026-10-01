/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

/**
 * DRS p9: the coalescing contract of DrsStore (the deferred `update` path,
 * the absorbing `updateNow` path and `resetAll`). The store is never
 * init()ed in unit tests, so `write()` short-circuits on the null file and
 * everything here runs purely in memory. Every test re-seeds a fresh state
 * first so nothing depends on execution order.
 */
class DrsStoreStateCoalescingTest : FunSpec({

    fun freshState(): DrsState = DrsState(onboardingDone = true)

    fun seedFresh() {
        runBlocking { DrsStore.updateNow { _ -> freshState() } }
    }

    test("a deferred update does not change the state synchronously") {
        seedFresh()
        DrsStore.update { it.copy(techToolbarKeys = it.techToolbarKeys + "a") }
        // the coalescing path schedules its trailing flush ~15s out — the
        // visible state must be untouched right after the call
        DrsStore.state.value.techToolbarKeys shouldBe emptyList()
    }

    test("two deferred updates fold in request order") {
        seedFresh()
        DrsStore.update { it.copy(techToolbarKeys = it.techToolbarKeys + "a") }
        DrsStore.update { it.copy(techToolbarKeys = it.techToolbarKeys + "b") }
        runBlocking { DrsStore.updateNow { it } }
        DrsStore.state.value.techToolbarKeys shouldBe listOf("a", "b")
    }

    test("updateNow absorbs the pending change before its own transform") {
        seedFresh()
        DrsStore.update { it.copy(techToolbarKeys = it.techToolbarKeys + "a") }
        DrsStore.update { it.copy(techToolbarKeys = it.techToolbarKeys + "b") }
        runBlocking { DrsStore.updateNow { it.copy(unifiedStripEnabled = false) } }
        // both deferred changes were applied AND the immediate transform ran
        DrsStore.state.value.techToolbarKeys shouldBe listOf("a", "b")
        DrsStore.state.value.unifiedStripEnabled shouldBe false
    }

    test("deferred updates on the same field are last-write-wins") {
        seedFresh()
        DrsStore.update { it.copy(techToolbarKeys = listOf("first")) }
        DrsStore.update { it.copy(techToolbarKeys = listOf("second")) }
        runBlocking { DrsStore.updateNow { it } }
        DrsStore.state.value.techToolbarKeys shouldBe listOf("second")
    }

    test("updateNow clears the pending change so it is never applied twice") {
        seedFresh()
        DrsStore.update { it.copy(techToolbarKeys = it.techToolbarKeys + "a") }
        runBlocking { DrsStore.updateNow { it } }
        // a second identity flush must find nothing left to apply
        runBlocking { DrsStore.updateNow { it } }
        DrsStore.state.value.techToolbarKeys shouldBe listOf("a")
    }

    test("resetAll wipes wallet, shortcuts and profiles but keeps onboarding done") {
        seedFresh()
        runBlocking {
            DrsStore.updateNow { state ->
                state.copy(
                    userPath = DrsUserPath.TECHNICAL.name,
                    shortcuts = listOf(
                        DrsShortcut(id = 1, shortcut = "brb", expansion = "be right back"),
                    ),
                    profiles = listOf(
                        DrsProfile(
                            id = "p1", name = "p1", path = DrsUserPath.NORMAL.name,
                            dayThemeId = "t:day", nightThemeId = "t:night",
                            numberRow = false, techStripEnabled = false, suggestionsEnabled = true,
                            clipboardHistoryEnabled = true, audioFeedbackEnabled = false,
                            hapticFeedbackEnabled = false,
                        ),
                    ),
                    wallet = DrsWallet(normal = 999, earnedNormal = 999),
                )
            }
            DrsStore.resetAll()
        }
        val state = DrsStore.state.value
        state.wallet shouldBe DrsWallet()
        state.shortcuts shouldBe emptyList()
        state.profiles shouldBe emptyList()
        state.onboardingDone shouldBe true
    }
})
