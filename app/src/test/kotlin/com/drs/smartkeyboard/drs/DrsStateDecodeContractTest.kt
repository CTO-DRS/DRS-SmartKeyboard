/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * DRS p9: the persistence decode contract of DrsState/DrsWallet. The Json
 * instance below mirrors the store's own configuration
 * (`ignoreUnknownKeys = true; encodeDefaults = true`, DrsStore.kt) so the
 * tests pin exactly what the app will accept from disk and from backups.
 */
class DrsStateDecodeContractTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    test("a v1.0.6-era state subset decodes with safe defaults for everything newer") {
        val legacy = """
            {"version":1,"onboardingDone":true,"userPath":"TECHNICAL","techToolbarKeys":["tab","esc"]}
        """.trimIndent()
        val state = json.decodeFromString<DrsState>(legacy)
        state.version shouldBe 1
        state.onboardingDone shouldBe true
        state.userPath shouldBe "TECHNICAL"
        state.techToolbarKeys shouldBe listOf("tab", "esc")
        // everything added after v1.0.6 arrives at its documented default
        state.wallet shouldBe DrsWallet()
        state.hybridViewMode shouldBe DrsHybridViewMode.DUAL.name
        state.unifiedStripEnabled shouldBe true
        state.dailyStats shouldBe emptyMap()
        state.lastBackupAt shouldBe 0L
    }

    test("an unknown top-level key is ignored") {
        val state = json.decodeFromString<DrsState>("""{"version":1,"futureField":{"a":[1,2]}}""")
        state.version shouldBe 1
        state.onboardingDone shouldBe false
    }

    test("a type-corrupt wallet field fails decoding loudly") {
        shouldThrow<SerializationException> {
            json.decodeFromString<DrsState>("""{"version":1,"wallet":{"technical":"many"}}""")
        }
    }

    test("a negative balance decodes unclamped — clamping belongs to credit/buy") {
        val state = json.decodeFromString<DrsState>("""{"version":1,"wallet":{"normal":-50}}""")
        state.wallet.normal shouldBe -50L
    }

    test("encode → decode round-trip is stable with encodeDefaults") {
        val state = DrsState(
            version = 1,
            onboardingDone = true,
            userPath = DrsUserPath.HYBRID.name,
            shortcuts = listOf(
                DrsShortcut(id = 3, shortcut = "brb", expansion = "be right back"),
            ),
            wallet = DrsWallet(
                normal = 12, earnedNormal = 40, streakDays = 4,
                dailyBonusDay = "2026-09-24",
            ),
            techToolbarKeys = listOf("tab"),
        )
        val first = json.encodeToString(state)
        val decoded = json.decodeFromString<DrsState>(first)
        decoded shouldBe state
        // stability: re-encoding the decoded state reproduces the same bytes
        json.encodeToString(decoded) shouldBe first
        // encodeDefaults really writes the default-valued fields
        first.contains("hybridViewMode") shouldBe true
        first.contains("unifiedStripEnabled") shouldBe true
    }
})
