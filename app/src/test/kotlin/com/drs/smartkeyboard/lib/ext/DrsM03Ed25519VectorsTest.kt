/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * DRS M0.3 — DrsEd25519 pinned against the OFFICIAL RFC 8032 §7.1 vectors
 * (TEST 1–3: empty message, single byte, two bytes — covering sign
 * determinism, public-key derivation and verification) plus a golden
 * cross-implementation vector: produced and self-verified by
 * tools/drs_package_sign.py under the pinned release key, accepted here
 * by the independent Kotlin implementation.
 */
class DrsM03Ed25519VectorsTest : FunSpec({

    fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    context("RFC 8032 §7.1 — official vectors") {
        test("TEST 1 — empty message: seed derives the public key and signs identically") {
            val seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
            val expectedPub = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
            val expectedSig = hex(
                "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155" +
                    "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
            )
            DrsEd25519.publicKeyFromSeed(seed) shouldBe expectedPub
            val sig = DrsEd25519.sign(seed, ByteArray(0))
            sig shouldBe expectedSig
            DrsEd25519.verify(expectedPub, ByteArray(0), sig) shouldBe true
        }

        test("TEST 2 — single byte 0x72") {
            val seed = hex("4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb")
            val pub = hex("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c")
            val msg = hex("72")
            val sig = hex(
                "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69d" +
                    "a085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00",
            )
            DrsEd25519.publicKeyFromSeed(seed) shouldBe pub
            DrsEd25519.sign(seed, msg) shouldBe sig
            DrsEd25519.verify(pub, msg, sig) shouldBe true
        }

        test("TEST 3 — two bytes 0xaf82") {
            val seed = hex("c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7")
            val pub = hex("fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025")
            val msg = hex("af82")
            val sig = hex(
                "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3a" +
                    "c18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a",
            )
            DrsEd25519.publicKeyFromSeed(seed) shouldBe pub
            DrsEd25519.sign(seed, msg) shouldBe sig
            DrsEd25519.verify(pub, msg, sig) shouldBe true
        }
    }

    context("golden cross-implementation vector") {
        test("a signature produced by the Python signing tool under the pinned key verifies here") {
            // Produced by tools/drs_package_sign.py sign (self-tested on the
            // RFC vectors) with the cto-drs-release-1 seed over the exact
            // bytes of "drs-m03-golden-cross-vector-v1" (no trailing newline).
            val message = hex("6472732d6d30332d676f6c64656e2d63726f73732d766563746f722d7631")
            val signature = hex(
                "49f49200a790b1582bdbda52842e7144c76284899edd51ca1c557b7afdfd2d2f" +
                    "608b7068216b7d0f41acebe204ff870b2959c8fd87439ec3ab79127c8029010f",
            )
            val pub = hex(DrsPackageTrust.RELEASE_PUBKEY_HEX)
            DrsEd25519.verify(pub, message, signature) shouldBe true
            // One flipped message byte must break it.
            val tampered = message.copyOf().also { it[0] = (it[0] + 1).toByte() }
            DrsEd25519.verify(pub, tampered, signature) shouldBe false
        }
    }

    context("strict gates") {
        val seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
        val pub = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val sig = DrsEd25519.sign(seed, ByteArray(0))

        test("S >= L is rejected (malleability gate)") {
            val malleable = sig.copyOf()
            // Set S := S + L (little-endian) — a naive implementation accepts.
            val lBytes = hex("edd3f55c1a631258d69cf7a2def9de14")
            var carry = 0
            for (i in 0 until 16) {
                val sum = (malleable[32 + i].toInt() and 0xFF) + (lBytes[i].toInt() and 0xFF) + carry
                malleable[32 + i] = sum.toByte()
                carry = sum shr 8
            }
            DrsEd25519.verify(pub, ByteArray(0), malleable) shouldBe false
        }

        test("y >= p point encodings are rejected") {
            // An encoding whose y exceeds p must fail, not silently reduce.
            val badKey = ByteArray(32) { 0xFF.toByte() }
            DrsEd25519.verify(badKey, ByteArray(0), sig) shouldBe false
        }

        test("wrong signature length is rejected") {
            DrsEd25519.verify(pub, ByteArray(0), sig.copyOf(63)) shouldBe false
        }

        test("tampered message is rejected") {
            DrsEd25519.verify(pub, byteArrayOf(1), sig) shouldBe false
        }
    }

    context("DrsPackageTrust") {
        test("unknown key id fails closed") {
            val seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
            val sig = DrsEd25519.sign(seed, "x".toByteArray())
            DrsPackageTrust.verifyPackageSignature(
                "x".toByteArray(),
                sig.joinToString("") { "%02x".format(it) },
                "some-other-key",
            ) shouldBe false
            DrsPackageTrust.verifyPackageSignature("x".toByteArray(), null, DrsPackageTrust.RELEASE_KEY_ID) shouldBe false
            DrsPackageTrust.verifyPackageSignature("x".toByteArray(), "", DrsPackageTrust.RELEASE_KEY_ID) shouldBe false
            DrsPackageTrust.verifyPackageSignature("x".toByteArray(), "zz-not-hex", DrsPackageTrust.RELEASE_KEY_ID) shouldBe false
        }
    }
})
