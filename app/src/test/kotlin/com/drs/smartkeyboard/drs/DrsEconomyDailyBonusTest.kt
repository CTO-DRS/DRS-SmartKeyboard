/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/**
 * DRS p9: the daily-bonus and streak logic of DrsEconomy.onProcessStart,
 * including the DRS p9 (D-2) midnight-straddle regression pin.
 *
 * TIMEZONE NOTE (documented limitation): production reads LocalDate.now()
 * in the system-default zone and the clock is NOT injectable, so these
 * tests derive their reference stamps the SAME way production does (one
 * LocalDate.now() here, yesterday/two-days-ago derived from it). A run
 * started exactly at local midnight could theoretically straddle the two
 * computations; every stamp comparison in production is now derived from a
 * single now() call, which is exactly what the regression test pins.
 */
class DrsEconomyDailyBonusTest : FunSpec({

    val now = LocalDate.now()
    val today = now.toString()
    val yesterday = now.minusDays(1).toString()
    val twoDaysAgo = now.minusDays(2).toString()

    fun seedWallet(wallet: DrsWallet) {
        runBlocking {
            DrsStore.updateNow { _ -> DrsState(onboardingDone = true, wallet = wallet) }
        }
    }

    fun act() {
        DrsEconomy.onProcessStart()
        // the grant rides the DEFERRED store path — flush before asserting
        runBlocking { DrsStore.updateNow { it } }
    }

    test("a fresh wallet earns streak 1 and the base +25 bonus") {
        seedWallet(DrsWallet())
        act()
        val w = DrsStore.state.value.wallet
        w.streakDays shouldBe 1
        w.dailyBonusDay shouldBe today
        w.lastActiveDay shouldBe today
        w.normal shouldBe 25L
        w.earnedNormal shouldBe 25L
        w.ledger.first().reason shouldBe "daily_bonus"
        w.ledger.first().delta shouldBe 25L
        w.ledger.first().day shouldBe today
    }

    test("yesterday's activity continues the streak at bonus +30") {
        seedWallet(DrsWallet(streakDays = 1, lastActiveDay = yesterday))
        act()
        val w = DrsStore.state.value.wallet
        w.streakDays shouldBe 2
        w.normal shouldBe 30L // 25 + (2 - 1) * 5
    }

    test("a two-day gap resets the streak to 1 — typing today cannot rescue it") {
        seedWallet(DrsWallet(streakDays = 9, lastActiveDay = twoDaysAgo, dailyBonusDay = twoDaysAgo))
        act()
        DrsStore.state.value.wallet.streakDays shouldBe 1
        DrsStore.state.value.wallet.normal shouldBe 25L

        seedWallet(DrsWallet(streakDays = 9, lastActiveDay = today, dailyBonusDay = twoDaysAgo))
        act()
        // the continuation check is strictly about YESTERDAY: typing today
        // does not keep a streak whose last bonus was two days ago alive
        DrsStore.state.value.wallet.streakDays shouldBe 1
        DrsStore.state.value.wallet.dailyBonusDay shouldBe today
    }

    test("the streak caps at 365 days and the bonus at the 7-day cap (+55)") {
        seedWallet(DrsWallet(streakDays = 365, lastActiveDay = yesterday))
        act()
        val w = DrsStore.state.value.wallet
        w.streakDays shouldBe 365 // 366 coerced back down
        w.normal shouldBe 55L     // 25 + (7 - 1) * 5
    }

    test("the grant is idempotent for the same day (state untouched)") {
        val seeded = DrsWallet(
            normal = 100, streakDays = 5,
            lastActiveDay = today, dailyBonusDay = today,
        )
        seedWallet(seeded)
        act()
        DrsStore.state.value.wallet shouldBe seeded
    }

    test("a typed-today wallet with yesterday's bonus still continues the streak") {
        seedWallet(DrsWallet(streakDays = 3, lastActiveDay = today, dailyBonusDay = yesterday))
        act()
        val w = DrsStore.state.value.wallet
        w.streakDays shouldBe 4
        w.normal shouldBe 40L // 25 + (4 - 1) * 5
    }

    test("the bonus write is deferred — nothing is visible before a flush") {
        seedWallet(DrsWallet())
        DrsEconomy.onProcessStart()
        DrsStore.state.value.wallet.normal shouldBe 0L
        DrsStore.state.value.wallet.dailyBonusDay shouldBe ""
        runBlocking { DrsStore.updateNow { it } }
        DrsStore.state.value.wallet.normal shouldBe 25L
        DrsStore.state.value.wallet.dailyBonusDay shouldBe today
    }

    test("yesterday is derived from the SAME today computation (midnight-straddle regression)") {
        // DRS p9 (D-2): before the fix onProcessStart called now() a second
        // time for `yesterday`; a process straddling midnight compared the
        // streak against a stale day and reset a live streak. Behavioral
        // pin: a wallet whose ONLY yesterday trace is dailyBonusDay (it
        // already typed today) must still continue the streak.
        seedWallet(DrsWallet(streakDays = 3, lastActiveDay = today, dailyBonusDay = yesterday))
        act()
        val w = DrsStore.state.value.wallet
        w.streakDays shouldBe 4
        w.ledger.first().reason shouldBe "daily_bonus"
        w.ledger.first().delta shouldBe 40L
        w.dailyBonusDay shouldBe today
    }
})
