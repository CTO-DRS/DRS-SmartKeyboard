/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

/**
 * DRS p9: the store side of DrsEconomy — buy (payer routing), equip
 * (including the adversarial clear-on-unowned pin) and the balance
 * queries. Only REAL catalog items are used. buy() runs updateNow inside
 * (synchronous), equip() rides the deferred path (flushed explicitly).
 */
class DrsEconomyStoreTest : FunSpec({

    // Real catalog entries (DrsRewardCatalog.ITEMS)
    val explorer = DrsRewardCatalog.itemOf("title_explorer")!!      // common TITLE, price 60
    val pathfinder = DrsRewardCatalog.itemOf("badge_pathfinder")!!  // common BADGE, price 50
    val glow = DrsRewardCatalog.itemOf("card_glow")!!               // common CARD, price 90
    val analytic = DrsRewardCatalog.itemOf("badge_analytic")!!      // TECHNICAL-exclusive BADGE, price 100

    fun seed(
        path: String = DrsUserPath.NORMAL.name,
        wallet: DrsWallet = DrsWallet(normal = 100, technical = 100, hybrid = 100),
    ) {
        runBlocking {
            DrsStore.updateNow { _ -> DrsState(onboardingDone = true, userPath = path, wallet = wallet) }
        }
    }

    test("a happy-path buy deducts, owns, auto-equips and writes the ledger") {
        seed()
        DrsEconomy.buy(explorer).shouldBeTrue()
        val w = DrsStore.state.value.wallet
        w.normal shouldBe 40L // 100 - 60
        w.ownedItems shouldBe listOf("title_explorer")
        w.equippedTitle shouldBe "title_explorer"
        w.ledger.first().reason shouldBe "buy:title_explorer"
        w.ledger.first().delta shouldBe -60L
    }

    test("a second buy of the same item is refused without mutating anything") {
        seed()
        DrsEconomy.buy(explorer)
        val before = DrsStore.state.value
        DrsEconomy.buy(explorer).shouldBeFalse()
        DrsStore.state.value shouldBe before
    }

    test("an unaffordable buy is refused without mutating anything") {
        seed(wallet = DrsWallet(normal = explorer.price - 1))
        DrsEconomy.buy(explorer).shouldBeFalse()
        val w = DrsStore.state.value.wallet
        w.normal shouldBe explorer.price - 1
        w.ownedItems shouldBe emptyList()
        w.ledger shouldBe emptyList()
        w.equippedTitle shouldBe ""
    }

    test("a common item is paid from the ACTIVE system's currency") {
        seed(path = DrsUserPath.TECHNICAL.name)
        DrsEconomy.buy(explorer).shouldBeTrue()
        val w = DrsStore.state.value.wallet
        w.technical shouldBe 40L // 100 - 60, the active payer
        w.normal shouldBe 100L   // untouched
    }

    test("an exclusive item pays from its OWN currency whatever the active path") {
        // DRS p9 (D-1-doc) pin: badge_analytic is TECHNICAL-owned. Buying it
        // while the NORMAL system is active still deducts Tech Chips — the
        // purchase path has NO active-path gate (the old KDoc claimed one).
        seed(path = DrsUserPath.NORMAL.name, wallet = DrsWallet(normal = 500, technical = 500))
        DrsEconomy.buy(analytic).shouldBeTrue()
        val w = DrsStore.state.value.wallet
        w.technical shouldBe 400L // 500 - 100, from the owning currency
        w.normal shouldBe 500L    // untouched despite being the active system
        w.ownedItems shouldBe listOf("badge_analytic")
        w.equippedBadge shouldBe "badge_analytic"
    }

    test("a garbage stored path pays for common items from NORMAL") {
        seed(path = "GARBAGE")
        DrsEconomy.buy(explorer).shouldBeTrue()
        val w = DrsStore.state.value.wallet
        w.normal shouldBe 40L
        w.technical shouldBe 100L
        w.hybrid shouldBe 100L
    }

    test("equipping an unowned item CLEARS the target slot (adversarial pin)") {
        seed(wallet = DrsWallet(normal = 100, equippedTitle = "title_chat_legend"))
        DrsEconomy.equip(explorer) // never bought
        runBlocking { DrsStore.updateNow { it } }
        DrsStore.state.value.wallet.equippedTitle shouldBe ""
    }

    test("equipping an owned item leaves the other slots intact") {
        seed(
            wallet = DrsWallet(
                normal = 100,
                ownedItems = listOf("badge_pathfinder", "card_glow"),
                equippedTitle = "seeded-title",
                equippedCard = "seeded-card",
            ),
        )
        DrsEconomy.equip(pathfinder)
        runBlocking { DrsStore.updateNow { it } }
        val w = DrsStore.state.value.wallet
        w.equippedBadge shouldBe "badge_pathfinder"
        w.equippedTitle shouldBe "seeded-title"
        w.equippedCard shouldBe "seeded-card"
    }

    test("balanceOf and earnedOf map each system onto its own currency (fallback NORMAL)") {
        // DrsUserPath is a closed enum, so the `else -> NORMAL` branch is the
        // NORMAL mapping itself — the spec's "unknown path" case is
        // unreachable by construction; NORMAL is pinned instead.
        val wallet = DrsWallet(
            normal = 1, technical = 2, hybrid = 3,
            earnedNormal = 10, earnedTechnical = 20, earnedHybrid = 30,
        )
        DrsEconomy.balanceOf(wallet, DrsUserPath.NORMAL) shouldBe 1L
        DrsEconomy.balanceOf(wallet, DrsUserPath.TECHNICAL) shouldBe 2L
        DrsEconomy.balanceOf(wallet, DrsUserPath.HYBRID) shouldBe 3L
        DrsEconomy.earnedOf(wallet, DrsUserPath.NORMAL) shouldBe 10L
        DrsEconomy.earnedOf(wallet, DrsUserPath.TECHNICAL) shouldBe 20L
        DrsEconomy.earnedOf(wallet, DrsUserPath.HYBRID) shouldBe 30L
    }
})
