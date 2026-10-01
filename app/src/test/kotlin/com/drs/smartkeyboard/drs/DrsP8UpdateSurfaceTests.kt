/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.app.drsupdater.DrsPackageManager
import com.drs.smartkeyboard.app.drsupdater.DrsUpdateCenter
import com.drs.smartkeyboard.lib.ext.DrsPackageTrust
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.serialization.SerializationException

/**
 * DRS p9 — regression contracts for the p8 update-surface security fixes:
 *  - p8 (C-4) + p9 (D-1): [DrsPackageManager.parseManifest] drops catalog
 *    entries whose version/id/sha256/url violate the path-interpolation
 *    contract — the version suffix may no longer carry path separators
 *    ("1.0+../../x" used to pass and escape cache/packages via
 *    packageFile()'s "<id>-<version>.flex" join);
 *  - p8 (C-5) + p9 (T-1): [DrsUpdateCenter.sanitizeAssetFileName] reduces the
 *    API-supplied asset name to a single path segment, with a SHA-derived
 *    fallback for malformed names.
 */
class DrsP8UpdateSurfaceTests : FunSpec({

    val sha256 = "ab".repeat(32) // valid 64-hex checksum

    fun pkgJson(
        id: String = "com.example.theme",
        name: String? = "Example Theme",
        type: String? = "ime/theme",
        version: String? = "1.2.3",
        checksum: String = sha256,
        url: String = "https://example.com/example-theme.flex",
        signature: String? = "ab".repeat(64),
        pubkeyId: String? = DrsPackageTrust.RELEASE_KEY_ID,
    ): String = buildString {
        append("{\"package_id\":\"").append(id).append("\"")
        if (name != null) append(",\"name\":\"").append(name).append("\"")
        append(",\"description\":\"A test package\"")
        if (version != null) append(",\"version\":\"").append(version).append("\"")
        if (type != null) append(",\"type\":\"").append(type).append("\"")
        append(",\"file_size\":12345")
        append(",\"sha256\":\"").append(checksum).append("\"")
        append(",\"download_url\":\"").append(url).append("\"")
        append(",\"release_date\":\"2025-01-01\",\"author\":\"DRS\",\"min_app_version\":\"1.0.0\"")
        if (signature != null) append(",\"signature\":\"").append(signature).append("\"")
        if (pubkeyId != null) append(",\"pubkey_id\":\"").append(pubkeyId).append("\"")
        append(",\"colors\":[\"#112233\"]}")
    }

    fun manifest(vararg packages: String): String =
        "{\"packages\":[" + packages.joinToString(",") + "]}"

    context("parseManifest — well-formed catalog entry") {
        test("parses fully with all fields mapped") {
            val pkgs = DrsPackageManager.parseManifest(manifest(pkgJson()))
            pkgs.size shouldBe 1
            val pkg = pkgs[0]
            pkg.id shouldBe "com.example.theme"
            pkg.name shouldBe "Example Theme"
            pkg.description shouldBe "A test package"
            pkg.version shouldBe "1.2.3"
            pkg.type shouldBe "ime/theme"
            pkg.sizeBytes shouldBe 12345L // numeric JSON field is read via jsonPrimitive.content
            pkg.sha256 shouldBe sha256
            pkg.url shouldBe "https://example.com/example-theme.flex"
            pkg.releaseDate shouldBe "2025-01-01"
            pkg.author shouldBe "DRS"
            pkg.minAppVersion shouldBe "1.0.0"
            pkg.colors shouldBe listOf("#112233")
        }
    }

    context("parseManifest — version gate (DRS p8 C-4 + p9 D-1)") {
        test("path-bearing and over-dotted versions drop the entry") {
            listOf(
                "../x",
                "1.2.3.4.5",
                "1.0+../../x", // THE D-1 regression pin: suffix chars are restricted now
                "", // empty version cannot pass the dotted-numeric contract
            ).forEach { badVersion ->
                DrsPackageManager.parseManifest(manifest(pkgJson(version = badVersion))) shouldBe emptyList()
            }
        }

        test("dotted-numeric versions with legit -/+ suffixes pass") {
            listOf(
                "1.2.3.4",
                "1.2-beta2",
                "1.2.3+rc1",
                "1.0+build.1",
            ).forEach { goodVersion ->
                val pkgs = DrsPackageManager.parseManifest(manifest(pkgJson(version = goodVersion)))
                pkgs.size shouldBe 1
                pkgs[0].version shouldBe goodVersion
            }
        }

        test("missing version key defaults to 0.0.0 and passes") {
            val pkgs = DrsPackageManager.parseManifest(manifest(pkgJson(version = null)))
            pkgs.size shouldBe 1
            pkgs[0].version shouldBe "0.0.0"
        }
    }

    context("parseManifest — remaining catalog gates") {
        test("id gates: uppercase start, separators and digit start drop the entry") {
            listOf(
                "Com.Bad",
                "com/bad",
                "1abc",
            ).forEach { badId ->
                DrsPackageManager.parseManifest(manifest(pkgJson(id = badId))) shouldBe emptyList()
            }
        }

        test("sha256 gates: short and non-hex checksums drop the entry") {
            listOf(
                "deadbeef",
                "z".repeat(64),
            ).forEach { badSha ->
                DrsPackageManager.parseManifest(manifest(pkgJson(checksum = badSha))) shouldBe emptyList()
            }
        }

        test("http:// download url drops the entry") {
            DrsPackageManager.parseManifest(
                manifest(pkgJson(url = "http://example.com/example-theme.flex")),
            ) shouldBe emptyList()
        }

        test("entry missing name or type drops the entry") {
            DrsPackageManager.parseManifest(manifest(pkgJson(name = null))) shouldBe emptyList()
            DrsPackageManager.parseManifest(manifest(pkgJson(type = null))) shouldBe emptyList()
        }

        test("malformed JSON throws SerializationException (caller wraps in runCatching)") {
            shouldThrow<SerializationException> {
                DrsPackageManager.parseManifest("{\"packages\": [")
            }
        }
    }

    context("sanitizeAssetFileName (DRS p8 C-5 + p9 T-1)") {
        test("honest asset name is unchanged") {
            DrsUpdateCenter.sanitizeAssetFileName("app-release.apk") shouldBe "app-release.apk"
        }

        test("path-bearing names reduce to their basename") {
            listOf(
                "https://evil/x/shell.apk" to "shell.apk",
                "..\\evil.apk" to "evil.apk",
                "../../etc/passwd" to "passwd",
            ).forEach { (raw, expected) ->
                DrsUpdateCenter.sanitizeAssetFileName(raw) shouldBe expected
            }
        }

        test("malformed names fall back to a SHA-derived 16-hex + .apk name") {
            for (raw in listOf("..", ".", "")) {
                val fallback = DrsUpdateCenter.sanitizeAssetFileName(raw)
                fallback shouldMatch Regex("""^[0-9a-f]{16}\.apk$""")
                fallback.length shouldBe 20
            }
        }

        test("distinct malformed names never collide in the fallback") {
            DrsUpdateCenter.sanitizeAssetFileName("..") shouldNotBe DrsUpdateCenter.sanitizeAssetFileName(".")
        }

        test("surrounding whitespace is trimmed") {
            DrsUpdateCenter.sanitizeAssetFileName("  spaced.apk  ") shouldBe "spaced.apk"
        }
    }

    context("parseManifest — DRS M0.3 signature pair gate (fail-closed)") {
        test("an entry WITHOUT the signature/pubkey_id pair is dropped") {
            DrsPackageManager.parseManifest(manifest(pkgJson(signature = null))) shouldBe emptyList()
            DrsPackageManager.parseManifest(manifest(pkgJson(signature = ""))) shouldBe emptyList()
            DrsPackageManager.parseManifest(manifest(pkgJson(pubkeyId = null))) shouldBe emptyList()
        }

        test("an entry naming an UNKNOWN key is dropped — no trust-on-first-use") {
            DrsPackageManager.parseManifest(manifest(pkgJson(pubkeyId = "rogue-key-9"))) shouldBe emptyList()
        }

        test("an entry with the pinned key id passes the pair gate (signature itself verified later on the bytes)") {
            val pkgs = DrsPackageManager.parseManifest(manifest(pkgJson()))
            pkgs.size shouldBe 1
            pkgs[0].pubkeyId shouldBe DrsPackageTrust.RELEASE_KEY_ID
            pkgs[0].signature shouldBe "ab".repeat(64)
        }
    }
})
