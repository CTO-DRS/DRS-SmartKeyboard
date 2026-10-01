/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

/**
 * DRS p9: the earning hooks of DrsEconomy — recordKeyEarn (with the emoji
 * bonus and the 40-key typing counter), recordFeatureUse, the ledger rule
 * and the per-path currency isolation. Awards ride the DEFERRED store
 * path, so every test flushes with a synchronous updateNow before asserting
 * (case 6 pins that deferral itself). Every test re-seeds a fresh state to
 * stay order-independent.
 *
 * The typing counter is a process-global the tests cannot reset; the tests
 * therefore rely only on window arithmetic: ANY 40 consecutive counter
 * values contain exactly one multiple of 40, so exactly-40 calls award
 * exactly one point no matter where the counter started — and after those
 * 40 calls the counter sits exactly on a multiple, making the 39-call
 * window deterministic too.
 */
class DrsEconomyEarnTest : FunSpec({

    fun seed(path: String = DrsUserPath.NORMAL.name) {
        runBlocking {
            DrsStore.updateNow { _ -> DrsState(onboardingDone = true, userPath = path) }
        }
    }

    fun wallet(): DrsWallet = DrsStore.state.value.wallet

    test("exactly 40 recordKeyEarn award one typing point, the next 39 award none") {
        seed(DrsUserPath.TECHNICAL.name)
        repeat(40) { DrsEconomy.recordKeyEarn('a'.code) }
        runBlocking { DrsStore.updateNow { it } }
        wallet().technical shouldBe 1L
        wallet().earnedTechnical shouldBe 1L
        // the counter now sits exactly on a multiple of 40, so the next
        // multiple is 40 ticks away — 39 more keystrokes cannot reach it
        repeat(39) { DrsEconomy.recordKeyEarn('a'.code) }
        runBlocking { DrsStore.updateNow { it } }
        wallet().technical shouldBe 1L
        wallet().earnedTechnical shouldBe 1L
    }

    test("an emoji key awards feature points AND still counts toward the 40-key counter") {
        seed(DrsUserPath.NORMAL.name)
        // order in code: award(+2, earn_emoji) runs BEFORE the counter tick
        DrsEconomy.recordKeyEarn(0x1F600)
        repeat(39) { DrsEconomy.recordKeyEarn('a'.code) }
        // 40 counter ticks total → exactly one typing point, plus the +2 emoji
        runBlocking { DrsStore.updateNow { it } }
        wallet().normal shouldBe 3L
        wallet().earnedNormal shouldBe 3L
        // the typing reward stays silent in the ledger, the emoji one does not
        wallet().ledger.map { it.reason } shouldBe listOf("earn_emoji")
    }

    test("the ledger records feature and emoji rewards but never typing rewards") {
        seed(DrsUserPath.NORMAL.name)
        DrsEconomy.recordKeyEarn(0x1F600)                      // → ledger entry
        DrsEconomy.recordFeatureUse("earn_feature_clipboard")  // → ledger entry
        DrsEconomy.recordFeatureUse("earn_typing")             // +2 but silent BY RULE
        repeat(5) { DrsEconomy.recordKeyEarn('k'.code) }       // maybe a typing point, never an entry
        runBlocking { DrsStore.updateNow { it } }
        wallet().ledger.map { it.reason } shouldBe
            listOf("earn_feature_clipboard", "earn_emoji")
    }

    test("earning while TECHNICAL is active leaves the other currencies untouched") {
        seed(DrsUserPath.TECHNICAL.name)
        runBlocking {
            DrsStore.updateNow {
                it.copy(wallet = it.wallet.copy(normal = 5, hybrid = 5, earnedNormal = 7, earnedHybrid = 7))
            }
        }
        repeat(40) { DrsEconomy.recordKeyEarn('a'.code) }      // +1 typing point
        DrsEconomy.recordFeatureUse("earn_feature_shortcut")   // +2
        runBlocking { DrsStore.updateNow { it } }
        wallet().technical shouldBe 3L
        wallet().earnedTechnical shouldBe 3L
        wallet().normal shouldBe 5L
        wallet().hybrid shouldBe 5L
        wallet().earnedNormal shouldBe 7L
        wallet().earnedHybrid shouldBe 7L
    }

    test("a garbage stored path falls back to the NORMAL currency") {
        seed("GARBAGE")
        DrsEconomy.recordFeatureUse("earn_feature_gesture")
        runBlocking { DrsStore.updateNow { it } }
        wallet().normal shouldBe 2L
        wallet().earnedNormal shouldBe 2L
        wallet().technical shouldBe 0L
        wallet().hybrid shouldBe 0L
    }

    test("earning is deferred — totals appear only after a flush") {
        seed(DrsUserPath.NORMAL.name)
        DrsEconomy.recordFeatureUse("earn_feature_clipboard")
        wallet().normal shouldBe 0L
        wallet().earnedNormal shouldBe 0L
        runBlocking { DrsStore.updateNow { it } }
        wallet().normal shouldBe 2L
        wallet().earnedNormal shouldBe 2L
    }

    test("the ledger keeps the newest 24 entries only (LEDGER_CAP)") {
        seed(DrsUserPath.NORMAL.name)
        repeat(30) { i -> DrsEconomy.recordFeatureUse("earn_feature_$i") }
        runBlocking { DrsStore.updateNow { it } }
        val ledger = wallet().ledger
        ledger.size shouldBe 24
        // newest-first: 29 survived at the head, 0..5 were dropped at the tail
        ledger.first().reason shouldBe "earn_feature_29"
        ledger.last().reason shouldBe "earn_feature_6"
        ledger.map { it.delta } shouldBe List(24) { 2L }
    }
})
