/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.drs.ai.DrsLearningEngine
import com.drs.smartkeyboard.drs.privacy.DrsE2ECrypto
import com.drs.smartkeyboard.drs.privacy.DrsE2ECrypto.DrsE2EError
import com.drs.smartkeyboard.drs.privacy.DrsNetworkSentinel
import com.drs.smartkeyboard.drs.privacy.DrsPrivacyDashboard
import com.drs.smartkeyboard.drs.privacy.DrsPrivacyLock
import com.drs.smartkeyboard.drs.privacy.DrsSyncBundle
import com.drs.smartkeyboard.ime.voice.VoiceRecognizerMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.nio.file.Files

/**
 * DRS v2.2.0-alpha1 — Phase 3 of the grand roadmap (privacy & trust):
 * the provable-privacy stack in drs/privacy. Every unit here is PURE
 * (JVM only, no Android framework), matching the ADR 0005/0006
 * testability constraint:
 *  - the E2E container (DRSYNC1): AES-256-GCM + PBKDF2-HMAC-SHA256,
 *    header-as-AAD, honest failure modes, text channel — task 3;
 *  - the sync payload allowlist (caps, entry shapes, versioning) — task 3;
 *  - the absolute privacy lock gates (surfaces, recognizer coercion,
 *    fail-closed provider) — task 2;
 *  - the network sentinel (delta semantics, unsupported counters,
 *    fail-to-zero honesty) — task 2;
 *  - the privacy dashboard engine (inventory, real deletion, E2E
 *    export round-trip) — task 4;
 *  - the manifest contract (permission allowlist + security config),
 *    resolved from the working tree — the JVM twin of the CI canary —
 *    and the learning-table import/merge discipline.
 */
class DrsP3PrivacyTests : FunSpec({

    val testIterations = 2_000
    val passphrase = "عبارة-سر-قوية-8".toCharArray()

    // ------------------------------------------------------- DRSYNC1 E2E

    context("DrsE2ECrypto") {
        test("seal → open round-trips arbitrary bytes exactly") {
            val plaintext = "كتابة عربية و English و 12345 — سنترال!".toByteArray(Charsets.UTF_8)
            val container = DrsE2ECrypto.seal(plaintext, passphrase, testIterations)
            DrsE2ECrypto.open(container, passphrase, testIterations) shouldBe plaintext
        }

        test("two seals of the same plaintext never produce the same container") {
            val pt = "same".toByteArray()
            val a = DrsE2ECrypto.seal(pt, passphrase, testIterations)
            val b = DrsE2ECrypto.seal(pt, passphrase, testIterations)
            a.contentEquals(b).shouldBeFalse()
            DrsE2ECrypto.open(a, passphrase, testIterations) shouldBe pt
            DrsE2ECrypto.open(b, passphrase, testIterations) shouldBe pt
        }

        test("a wrong passphrase answers AuthenticationFailed, never garbage") {
            val container = DrsE2ECrypto.seal("secret".toByteArray(), passphrase, testIterations)
            val thrown = runCatching {
                DrsE2ECrypto.open(container, "عبارة-سر-أخرى-8".toCharArray(), testIterations)
            }.exceptionOrNull()
            thrown.shouldBeInstanceOf<DrsE2EError.AuthenticationFailed>()
        }

        test("flipping one ciphertext bit is indistinguishable from a wrong passphrase") {
            val container = DrsE2ECrypto.seal("secret".toByteArray(), passphrase, testIterations)
            container[container.size - 1] = (container[container.size - 1].toInt() xor 0x01).toByte()
            runCatching { DrsE2ECrypto.open(container, passphrase, testIterations) }
                .exceptionOrNull()
                .shouldBeInstanceOf<DrsE2EError.AuthenticationFailed>()
        }

        test("tampered framing (magic) fails as BadMagic before any crypto runs") {
            val container = DrsE2ECrypto.seal("secret".toByteArray(), passphrase, testIterations)
            container[0] = 'X'.code.toByte()
            runCatching { DrsE2ECrypto.open(container, passphrase, testIterations) }
                .exceptionOrNull()
                .shouldBeInstanceOf<DrsE2EError.BadMagic>()
        }

        test("short and version-stamped inputs fail honestly") {
            runCatching { DrsE2ECrypto.open(ByteArray(10), passphrase, testIterations) }
                .exceptionOrNull()
                .shouldBeInstanceOf<DrsE2EError.ContainerTooShort>()
            val container = DrsE2ECrypto.seal("x".toByteArray(), passphrase, testIterations)
            container[DrsE2ECrypto.MAGIC.size] = 0x7F
            runCatching { DrsE2ECrypto.open(container, passphrase, testIterations) }
                .exceptionOrNull()
                .shouldBeInstanceOf<DrsE2EError.UnsupportedVersion>()
        }

        test("the text channel round-trips and rejects garbage") {
            val payload = "بيانات المزامنة".toByteArray(Charsets.UTF_8)
            val text = DrsE2ECrypto.sealToText(payload, passphrase, testIterations)
            // Base64("DRSYNC1\u0001\u00010") — the version + salt-length bytes are
            // constants, so this prefix is stable across all containers.
            text.shouldStartWith("RFJTWU5DMQ")
            DrsE2ECrypto.openFromText(text, passphrase, testIterations) shouldBe payload
            runCatching { DrsE2ECrypto.openFromText("!!!not base64!!!", passphrase, testIterations) }
                .exceptionOrNull()
                .shouldBeInstanceOf<DrsE2EError.BadEncoding>()
        }
    }

    // ------------------------------------------------- sync payload

    context("DrsSyncBundle") {
        val sample = DrsSyncBundle.Payload(
            app = "2.2.0-alpha1",
            exportedAt = 1_760_000_000_000L,
            words = mapOf("مرحبا" to 12L, "world" to 3L),
            bigrams = mapOf("مرحبا" to mapOf("بالعالم" to 5L)),
        )

        test("encode → decode round-trips the payload") {
            DrsSyncBundle.decode(DrsSyncBundle.encode(sample)) shouldBe sample
        }

        test("seal → open text round-trips through the E2E container") {
            val text = DrsSyncBundle.seal(sample, passphrase, testIterations)
            DrsSyncBundle.open(text, passphrase, testIterations) shouldBe sample
        }

        test("an oversized word table is rejected before encryption") {
            val flood = (1..(DrsSyncBundle.MAX_WORDS + 1)).associate { "w$it" to 1L }
            runCatching {
                DrsSyncBundle.encode(sample.copy(words = flood))
            }.exceptionOrNull().shouldBeInstanceOf<DrsSyncBundle.BundleError.TooManyEntries>()
        }

        test("entries violating the word shape (unnormalized) are rejected") {
            runCatching {
                DrsSyncBundle.encode(sample.copy(words = mapOf("مرحبا" to 1L, "UPPER" to 2L)))
            }.exceptionOrNull().shouldBeInstanceOf<DrsSyncBundle.BundleError.MalformedEntry>()
        }

        test("corrupt JSON fails as MalformedJson, not as a crash") {
            val bytes = "not json at all".toByteArray()
            runCatching { DrsSyncBundle.decode(bytes) }
                .exceptionOrNull()
                .shouldBeInstanceOf<DrsSyncBundle.BundleError.MalformedJson>()
        }
    }

    // --------------------------------------- absolute privacy lock

    context("DrsPrivacyLock") {
        afterTest {
            // Restore the fail-open-in-unlocked-state default provider so
            // other tests never observe a leaked lock state.
            DrsPrivacyLock.init { false }
        }

        test("locked mode refuses every network surface; unlocked allows all") {
            DrsPrivacyLock.init { true }
            DrsPrivacyLock.NetworkSurface.entries.forEach { surface ->
                DrsPrivacyLock.allows(surface).shouldBeFalse()
            }
            DrsPrivacyLock.init { false }
            DrsPrivacyLock.NetworkSurface.entries.forEach { surface ->
                DrsPrivacyLock.allows(surface).shouldBeTrue()
            }
        }

        test("locked mode coerces AUTO/STANDARD recognizers to ON_DEVICE_ONLY") {
            DrsPrivacyLock.init { true }
            DrsPrivacyLock.effectiveRecognizer(VoiceRecognizerMode.AUTO) shouldBe VoiceRecognizerMode.ON_DEVICE_ONLY
            DrsPrivacyLock.effectiveRecognizer(VoiceRecognizerMode.STANDARD) shouldBe VoiceRecognizerMode.ON_DEVICE_ONLY
            DrsPrivacyLock.effectiveRecognizer(VoiceRecognizerMode.ON_DEVICE_ONLY) shouldBe VoiceRecognizerMode.ON_DEVICE_ONLY
        }

        test("unlocked mode passes the requested recognizer through untouched") {
            DrsPrivacyLock.init { false }
            DrsPrivacyLock.effectiveRecognizer(VoiceRecognizerMode.STANDARD) shouldBe VoiceRecognizerMode.STANDARD
            DrsPrivacyLock.effectiveRecognizer(VoiceRecognizerMode.AUTO) shouldBe VoiceRecognizerMode.AUTO
        }

        test("a throwing provider fails CLOSED (locked), not open") {
            DrsPrivacyLock.init { throw IllegalStateException("prefs unavailable") }
            DrsPrivacyLock.isAbsoluteMode().shouldBeTrue()
            DrsPrivacyLock.allows(DrsPrivacyLock.NetworkSurface.UPDATE_CHECK).shouldBeFalse()
        }
    }

    // ------------------------------------------------ network sentinel

    context("DrsNetworkSentinel") {
        test("unchanged counters read as zero traffic") {
            val sentinel = DrsNetworkSentinel(
                traffic = { DrsNetworkSentinel.TrafficSnapshot(100L, 50L, true) },
                permissions = { emptyList() },
                clock = { 42L },
            )
            val att = sentinel.attestation(absoluteMode = true)
            att.zeroTraffic.shouldBeTrue()
            att.rxSinceBaseline shouldBe 0L
            att.txSinceBaseline shouldBe 0L
            att.checkedAt shouldBe 42L
        }

        test("a counter delta moves the verdict to non-zero with honest numbers") {
            var now = DrsNetworkSentinel.TrafficSnapshot(100L, 50L, true)
            val sentinel = DrsNetworkSentinel(
                traffic = { now },
                permissions = { emptyList() },
                clock = { 0L },
            )
            sentinel.attestation(absoluteMode = false).zeroTraffic.shouldBeTrue()
            now = DrsNetworkSentinel.TrafficSnapshot(724L, 91L, true)
            val att = sentinel.attestation(absoluteMode = false)
            att.zeroTraffic.shouldBeFalse()
            att.rxSinceBaseline shouldBe 624L
            att.txSinceBaseline shouldBe 41L
            // Second look: delta-since-previous is relative to the last look.
            val att2 = sentinel.attestation(absoluteMode = false)
            att2.rxSincePrevious shouldBe 0L
        }

        test("unsupported counters refuse to claim a zero") {
            val sentinel = DrsNetworkSentinel(
                traffic = { DrsNetworkSentinel.TrafficSnapshot(0L, 0L, false) },
                permissions = { emptyList() },
                clock = { 0L },
            )
            val att = sentinel.attestation(absoluteMode = false)
            att.countersSupported.shouldBeFalse()
            att.zeroTraffic.shouldBeFalse()
        }

        test("the permission inventory passes through verbatim") {
            val perms = listOf(
                DrsNetworkSentinel.PermissionGrant("android.permission.VIBRATE", true),
                DrsNetworkSentinel.PermissionGrant("android.permission.RECORD_AUDIO", false),
            )
            val sentinel = DrsNetworkSentinel(
                traffic = { DrsNetworkSentinel.TrafficSnapshot(0L, 0L, true) },
                permissions = { perms },
                clock = { 0L },
            )
            sentinel.attestation(absoluteMode = false).permissions shouldBe perms
        }
    }

    // -------------------------------------------- privacy dashboard

    context("DrsPrivacyDashboard") {
        test("the inventory aggregates real probes into honest totals") {
            val mediaDir = Files.createTempDirectory("drs-p3-media").toFile()
            File(mediaDir, "a.bin").writeBytes(ByteArray(1200))
            File(mediaDir, "b.bin").writeBytes(ByteArray(300))
            val learningDir = Files.createTempDirectory("drs-p3-learn").toFile()
            val usageFile = File(learningDir, "learning.json")
            usageFile.writeText("{}")

            val dashboard = DrsPrivacyDashboard(
                stores = listOf(
                    DrsPrivacyDashboard.StoreProbe(
                        id = DrsPrivacyDashboard.STORE_LEARNING,
                        location = "tmp/learning.json",
                        measure = { DrsPrivacyDashboard.fileUsage(usageFile) },
                        destroy = { DrsPrivacyDashboard.destroyPath(usageFile) },
                    ),
                    DrsPrivacyDashboard.StoreProbe(
                        id = DrsPrivacyDashboard.STORE_CLIPBOARD_MEDIA,
                        location = "tmp/media/",
                        measure = { DrsPrivacyDashboard.dirUsage(mediaDir) },
                        destroy = { DrsPrivacyDashboard.destroyPath(mediaDir) },
                    ),
                ),
                attestationProvider = {
                    DrsNetworkSentinel(
                        traffic = { DrsNetworkSentinel.TrafficSnapshot(0L, 0L, true) },
                        permissions = { emptyList() },
                    ).attestation(absoluteMode = false)
                },
            )
            val summary = dashboard.summary()
            summary.stores shouldHaveSize 2
            summary.totalBytes shouldBe usageFile.length() + 1500L
            summary.totalItems shouldBe 3L
        }

        test("deleteAll deletes for real and one failing store never blocks the rest") {
            val gone = Files.createTempFile("drs-p3-gone", null).toFile()
            val stubborn = Files.createTempFile("drs-p3-stubborn", null).toFile()
            val dashboard = DrsPrivacyDashboard(
                stores = listOf(
                    DrsPrivacyDashboard.StoreProbe(
                        id = DrsPrivacyDashboard.STORE_LEARNING,
                        location = "tmp/gone",
                        measure = { DrsPrivacyDashboard.fileUsage(gone) },
                        destroy = { DrsPrivacyDashboard.destroyPath(gone) },
                    ),
                    DrsPrivacyDashboard.StoreProbe(
                        id = DrsPrivacyDashboard.STORE_CRASH,
                        location = "tmp/stubborn",
                        measure = { DrsPrivacyDashboard.StoreUsage(0, 0, 0) },
                        destroy = { throw IllegalStateException("simulated failure") },
                    ),
                ),
                attestationProvider = {
                    DrsNetworkSentinel(
                        traffic = { DrsNetworkSentinel.TrafficSnapshot(0L, 0L, true) },
                        permissions = { emptyList() },
                    ).attestation(absoluteMode = false)
                },
            )
            val report = dashboard.deleteAll()
            report shouldHaveSize 2
            report.first { it.id == DrsPrivacyDashboard.STORE_LEARNING }.deleted.shouldBeTrue()
            report.first { it.id == DrsPrivacyDashboard.STORE_CRASH }.deleted.shouldBeFalse()
            gone.exists().shouldBeFalse()
            stubborn.exists().shouldBeTrue()
        }

        test("exportEncrypted produces a container that opens back to the payload") {
            val learning = Pair(
                mapOf("مرحبا" to 7L),
                mapOf("مرحبا" to mapOf("بالعالم" to 2L)),
            )
            val dashboard = DrsPrivacyDashboard(
                stores = emptyList(),
                attestationProvider = {
                    DrsNetworkSentinel(
                        traffic = { DrsNetworkSentinel.TrafficSnapshot(0L, 0L, true) },
                        permissions = { emptyList() },
                    ).attestation(absoluteMode = false)
                },
            )
            val sealed = dashboard.exportEncrypted(
                learning = learning,
                appVersion = "2.2.0-alpha1",
                exportedAt = 42L,
                passphrase = passphrase,
                iterations = testIterations,
            )!!
            val payload = DrsSyncBundle.open(sealed, passphrase, testIterations)
            payload.words shouldBe learning.first
            payload.bigrams shouldBe learning.second
            payload.app shouldBe "2.2.0-alpha1"
        }

        test("an empty learning table answers nothing-to-export honestly") {
            val dashboard = DrsPrivacyDashboard(
                stores = emptyList(),
                attestationProvider = {
                    DrsNetworkSentinel(
                        traffic = { DrsNetworkSentinel.TrafficSnapshot(0L, 0L, true) },
                        permissions = { emptyList() },
                    ).attestation(absoluteMode = false)
                },
            )
            dashboard.exportEncrypted(
                learning = Pair(emptyMap(), emptyMap()),
                appVersion = "x",
                exportedAt = 0L,
                passphrase = passphrase,
                iterations = testIterations,
            ).shouldBeNull()
        }
    }

    // ------------------------------------- learning import/merge

    context("DrsLearningEngine sync import") {
        test("merge import adds counts without clobbering local state") {
            DrsLearningEngine.clear()
            DrsLearningEngine.learnWord("محلي")
            DrsLearningEngine.importState(
                importedWords = mapOf("محلي" to 1L, "قادم" to 5L),
                importedBigrams = emptyMap(),
                replace = false,
            )
            DrsLearningEngine.knowsWord("قادم").shouldBeTrue()
            DrsLearningEngine.knowsWord("محلي").shouldBeTrue()
            DrsLearningEngine.size() shouldBe 2
        }

        test("replace import swaps the tables wholesale and respects caps") {
            DrsLearningEngine.clear()
            DrsLearningEngine.learnWord("قديم")
            val flood = (1..(DrsSyncBundle.MAX_WORDS + 50)).associate { "w$it" to 300L }
            DrsLearningEngine.importState(
                importedWords = flood,
                importedBigrams = emptyMap(),
                replace = true,
            )
            // The cap discipline keeps the table at the same bound as local learning.
            (DrsLearningEngine.size() <= DrsSyncBundle.MAX_WORDS).shouldBeTrue()
            DrsLearningEngine.knowsWord("قديم").shouldBeFalse()
            DrsLearningEngine.clear()
        }
    }

    // ---------------------------------- manifest contract (canary twin)

    context("manifest contract") {
        val manifest = resolveManifest()
        val manifestText = manifest.readText()

        test("the manifest permission set equals the sentinel allowlist") {
            val actual = Regex("uses-permission android:name=\"([^\"]+)\"")
                .findAll(manifestText)
                .map { it.groupValues[1] }
                .sorted()
                .toList()
            actual shouldContainExactlyInAnyOrder DrsNetworkSentinel.EXPECTED_PERMISSIONS
        }

        test("cleartext stays denied through the network security config") {
            manifestText.shouldContain("networkSecurityConfig")
            val config = File(manifest.parentFile, "res/xml/network_security_config.xml")
            config.exists().shouldBeTrue()
            config.readText().lowercase().shouldNotContain("cleartexttrafficpermitted=\"true\"")
        }
    }
}) {
    companion object {
        /**
         * Walks up from the working directory until it finds the module's
         * manifest — AGP's unit-test working directory varies between
         * environments, so the resolver must not assume one.
         */
        internal fun resolveManifest(): File {
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            repeat(6) {
                val candidate = File(dir, "app/src/main/AndroidManifest.xml")
                if (candidate.exists()) return candidate
                dir = dir?.parentFile
            }
            error("AndroidManifest.xml not found from ${System.getProperty("user.dir")}")
        }
    }
}

private fun String.shouldContain(needle: String) {
    if (!contains(needle)) throw AssertionError("expected to contain `$needle`: $this")
}

private fun String.shouldNotContain(needle: String) {
    if (contains(needle)) throw AssertionError("expected NOT to contain `$needle`: $this")
}
