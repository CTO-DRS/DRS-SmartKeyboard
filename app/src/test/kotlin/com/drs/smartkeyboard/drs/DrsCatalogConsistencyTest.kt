/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */
package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.app.drsupdater.DrsPackageManager
import com.drs.smartkeyboard.lib.ext.DrsPackageTrust
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.security.MessageDigest

/**
 * DRS — the catalog consistency guard (the theme-install incident guard).
 *
 * The v2.1.0 incident: packages/manifest.json shipped download URLs pointing
 * to a release that never carried the .flex assets (404 on device), and
 * sha256/file_size fields computed over a DIFFERENT build than the bytes the
 * signatures covered. Every on-device theme install failed with the generic
 * "file verification failed" message while the catalog looked perfectly
 * healthy in the UI. Nothing in the unit suite pinned the real catalog to
 * the real package bytes — this test closes that hole permanently.
 *
 * It reads the REAL `packages/manifest.json` committed to the repository and
 * asserts, for every entry:
 *  1. it survives [DrsPackageManager.parseManifest] (no gate drops it),
 *  2. the referenced .flex file exists in `packages/`,
 *  3. file_size and sha256 describe the EXACT bytes of that file,
 *  4. the ed25519 signature verifies against the pinned release key
 *     ([DrsPackageTrust]) over those same bytes,
 *  5. the download URL is HTTPS and names exactly that file.
 *
 * Fail-closed: if the repository root cannot be located, the test fails
 * loudly — a guard that silently passes when it cannot see the catalog is
 * not a guard.
 */
class DrsCatalogConsistencyTest : FunSpec({

    val repoRoot: File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .firstOrNull { File(it, "packages/manifest.json").isFile }
        ?: error("packages/manifest.json not found from ${System.getProperty("user.dir")} — run from within the repository")

    val packagesDir = File(repoRoot, "packages")
    val manifestPayload = File(repoRoot, "packages/manifest.json").readText()

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    test("real catalog survives parseManifest — every entry passes every gate") {
        val parsed = DrsPackageManager.parseManifest(manifestPayload)
        parsed.size shouldBe 8
        parsed.map { it.id }.toSet() shouldBe parsed.map { it.id }.toSet()
    }

    test("every entry: hash, size and signature describe the exact shipped bytes") {
        val parsed = DrsPackageManager.parseManifest(manifestPayload).associateBy { it.id }
        parsed.forEach { (id, pkg) ->
            val fileName = pkg.url.substringAfterLast('/')
            val file = File(packagesDir, fileName)
            file.isFile shouldBe true // entry 2: the referenced archive exists
            val bytes = file.readBytes()

            pkg.sizeBytes shouldBe bytes.size // entry 3a: size is the real size
            pkg.sha256 shouldBe sha256Hex(bytes) // entry 3b: hash is the real hash

            // entry 4: the pinned release key verifies over the same bytes
            DrsPackageTrust.verifyPackageSignature(bytes, pkg.signature, pkg.pubkeyId) shouldBe true

            // entry 5: URL is HTTPS and names exactly this file
            pkg.url.startsWith("https://") shouldBe true
            pkg.url.substringAfterLast('/') shouldBe fileName
        }
    }
})
