/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.drs.privacy.DrsFieldSecurity
import com.drs.smartkeyboard.drs.privacy.DrsSyncFlow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * DRS M3.1–M3.6 — the ecosystem contracts: the four-way trust
 * classification, the sync flow (deterministic pairing, binary-safe hex
 * chunking, fail-closed reassembly, merge-by-max), and the field
 * security classifier (abstracted facts, highest protection wins,
 * non-negotiable password line, FORCE_OFF honored where it may be).
 */
class DrsM3EcosystemTests : FunSpec({

    val validSig = "ab".repeat(32)

    context("M3.2 — DrsTrustState (four-way)") {
        test("signed with the pinned key → VERIFIED") {
            DrsTrustState.of(validSig, "cto-drs-release-1") shouldBe DrsTrustState.VERIFIED
        }

        test("signed with an unknown key → UNKNOWN_KEY (amber, never green)") {
            DrsTrustState.of(validSig, "rogue-key-9") shouldBe DrsTrustState.UNKNOWN_KEY
        }

        test("no signature → UNSIGNED (red)") {
            DrsTrustState.of(null, "cto-drs-release-1") shouldBe DrsTrustState.UNSIGNED
            DrsTrustState.of("", "cto-drs-release-1") shouldBe DrsTrustState.UNSIGNED
        }

        test("a malformed signature → INVALID_SIG (red)") {
            DrsTrustState.of("xyz-not-hex", "cto-drs-release-1") shouldBe DrsTrustState.INVALID_SIG
            DrsTrustState.of("ab".repeat(16), "cto-drs-release-1") shouldBe DrsTrustState.INVALID_SIG // too short
        }
    }

    context("M3.3 — DrsSyncFlow pairing") {
        test("pairing is DETERMINISTIC regardless of argument order") {
            val a = DrsSyncFlow.pairingKey("device-alpha", "device-beta")
            val b = DrsSyncFlow.pairingKey("device-beta", "device-alpha")
            a shouldBe b
            a.length shouldBe 32 // 16 bytes hex
        }

        test("different devices pair differently") {
            DrsSyncFlow.pairingKey("a", "b") shouldNotBe DrsSyncFlow.pairingKey("a", "c")
        }

        test("empty ids are refused") {
            shouldThrow<IllegalArgumentException> { DrsSyncFlow.pairingKey("", "b") }
        }
    }

    context("M3.3 — DrsSyncFlow chunking") {
        test("round trip: chunk → reassemble in ANY order restores the exact bytes") {
            // binary payload with bytes that would corrupt a naive UTF-8 cut
            val payload = ByteArray(700) { (it * 37 % 251).toByte() }
            val frames = DrsSyncFlow.chunk(payload)
            frames.size shouldBe 4 // 700 bytes → 1400 hex chars → ceil(1400/400)
            val shuffled = frames.reversed()
            DrsSyncFlow.reassemble(shuffled) shouldBe payload
        }

        test("frames are binary-safe: no raw byte is ever split mid-character") {
            val payload = "مرحبا بالعالم".toByteArray(Charsets.UTF_8) + byteArrayOf(0.toByte(), 255.toByte())
            val frames = DrsSyncFlow.chunk(payload)
            DrsSyncFlow.reassemble(frames.asReversed()) shouldBe payload
            frames.forEach { frame -> frame.startsWith("DRS-SYNC1:") shouldBe true }
        }

        test("incomplete transfer fails CLOSED (no silent partial restore)") {
            val payload = ByteArray(600) { it.toByte() }
            val frames = DrsSyncFlow.chunk(payload)
            shouldThrow<IllegalArgumentException> { DrsSyncFlow.reassemble(frames.dropLast(1)) }
        }

        test("a malformed frame is rejected outright") {
            shouldThrow<IllegalArgumentException> { DrsSyncFlow.reassemble(listOf("NOT-A-FRAME:0/1:ff")) }
            shouldThrow<IllegalArgumentException> { DrsSyncFlow.reassemble(listOf("DRS-SYNC1:garbage")) }
            shouldThrow<IllegalArgumentException> { DrsSyncFlow.reassemble(emptyList()) }
        }

        test("conflicting duplicates (same index, different bytes) are rejected") {
            val frames = DrsSyncFlow.chunk(ByteArray(500) { 1 })
            val corrupted = frames[0].dropLast(2) + "ff"
            shouldThrow<IllegalArgumentException> {
                DrsSyncFlow.reassemble(frames + corrupted)
            }
        }
    }

    context("M3.3 — DrsSyncFlow merge-by-max") {
        test("merge is deterministic: max per key, either operand order") {
            val a = mapOf("word1" to 5L, "word2" to 10L)
            val b = mapOf("word1" to 9L, "word3" to 2L)
            val ab = DrsSyncFlow.mergeByMax(a, b)
            val ba = DrsSyncFlow.mergeByMax(b, a)
            ab shouldBe mapOf("word1" to 9L, "word2" to 10L, "word3" to 2L)
            ab shouldBe ba
        }
    }

    context("M3.6 — DrsFieldSecurity") {
        test("password fields classify BLOCKED") {
            val level = DrsFieldSecurity.classify(DrsFieldSecurity.FieldFacts(isPasswordKind = true))
            level shouldBe DrsFieldSecurity.Level.BLOCKED
        }

        test("email/URI/no-autocomplete fields classify SENSITIVE") {
            DrsFieldSecurity.classify(DrsFieldSecurity.FieldFacts(isPasswordKind = false, isEmailAddress = true)) shouldBe
                DrsFieldSecurity.Level.SENSITIVE
            DrsFieldSecurity.classify(DrsFieldSecurity.FieldFacts(isPasswordKind = false, isAutoCompleteBlocked = true)) shouldBe
                DrsFieldSecurity.Level.SENSITIVE
        }

        test("plain fields classify NORMAL") {
            DrsFieldSecurity.classify(DrsFieldSecurity.FieldFacts(isPasswordKind = false)) shouldBe
                DrsFieldSecurity.Level.NORMAL
        }

        test("highest protection wins: BLOCKED beats everything") {
            DrsFieldSecurity.classify(
                DrsFieldSecurity.FieldFacts(isPasswordKind = true, isEmailAddress = true),
            ) shouldBe DrsFieldSecurity.Level.BLOCKED
        }

        test("STRICT lifts NORMAL to SENSITIVE but never demotes BLOCKED") {
            DrsFieldSecurity.effective(DrsFieldSecurity.Level.NORMAL, DrsFieldSecurity.UserGate.STRICT) shouldBe
                DrsFieldSecurity.Level.SENSITIVE
            DrsFieldSecurity.effective(DrsFieldSecurity.Level.BLOCKED, DrsFieldSecurity.UserGate.STRICT) shouldBe
                DrsFieldSecurity.Level.BLOCKED
        }

        test("FORCE_OFF honors the user gate EXCEPT the password line") {
            DrsFieldSecurity.effective(DrsFieldSecurity.Level.SENSITIVE, DrsFieldSecurity.UserGate.FEATURE_OFF) shouldBe
                DrsFieldSecurity.Level.NORMAL
            DrsFieldSecurity.effective(DrsFieldSecurity.Level.BLOCKED, DrsFieldSecurity.UserGate.FEATURE_OFF) shouldBe
                DrsFieldSecurity.Level.BLOCKED
        }

        test("learning suppression: everything above NORMAL suppresses") {
            DrsFieldSecurity.suppressesLearning(DrsFieldSecurity.Level.NORMAL) shouldBe false
            DrsFieldSecurity.suppressesLearning(DrsFieldSecurity.Level.SENSITIVE) shouldBe true
            DrsFieldSecurity.suppressesLearning(DrsFieldSecurity.Level.BLOCKED) shouldBe true
        }
    }
})
