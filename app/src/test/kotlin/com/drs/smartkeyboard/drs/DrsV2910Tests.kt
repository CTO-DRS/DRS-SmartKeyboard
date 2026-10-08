/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import android.provider.Settings
import com.drs.smartkeyboard.lib.util.InputMethodUtils
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainDuplicates
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank

/**
 * DRS v2.9.1 «زر التفعيل لا يقتل التطبيق» — contract tests for the enable
 * button's degradation chain. The field report: pressing the enable step on
 * a ROM without a dedicated keyboard-settings screen closed the whole app
 * instantly — the old `showImeEnablerActivity` fired a bare
 * `startActivity` and died on `ActivityNotFoundException`.
 *
 * The pure [InputMethodUtils.imeSettingsActionChain] is the contract the
 * integration leans on: the dedicated IME settings screen first, the generic
 * system settings second, and only after both refuse does the caller surface
 * the honest localized toast. The chain itself is what this spec pins down —
 * ordering, completeness, and determinism — because the runtime mechanics
 * (resolver quirks per ROM) are framework glue that JVM tests cannot reach.
 */
class DrsV2910Tests : FunSpec({

    test("the chain opens with the dedicated IME settings screen") {
        // The primary path must stay the purpose-built screen every stock
        // Android ships — the fallback exists for the ROMs that do not.
        InputMethodUtils.imeSettingsActionChain.first() shouldBe
            Settings.ACTION_INPUT_METHOD_SETTINGS
    }

    test("the chain ends with the generic system settings, never empty") {
        // Even the strangest ROM still resolves the generic settings screen;
        // the toast is only reached after BOTH actions refuse.
        val chain = InputMethodUtils.imeSettingsActionChain
        chain shouldHaveSize 2
        chain.last() shouldBe Settings.ACTION_SETTINGS
    }

    test("the chain carries no duplicates and no blank actions") {
        // A duplicated action would burn a second doomed attempt; a blank
        // one would be resolver garbage. Both betray the degradation idea.
        val chain = InputMethodUtils.imeSettingsActionChain
        chain.shouldNotContainDuplicates()
        chain.forEach { action -> action.shouldNotBeBlank() }
    }

    test("the chain is deterministic across reads") {
        // Same degradation path on every press — a crash-free button must
        // not gamble on evaluation order.
        InputMethodUtils.imeSettingsActionChain shouldBe
            InputMethodUtils.imeSettingsActionChain
    }
})
