/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * DRS M0.2 — the installed-extension lifecycle contracts on a real module
 * directory: sibling `.drs-disabled` markers, archive naming FROM THE
 * MANIFEST INSIDE THE PACKAGE (never a caller-supplied version), the
 * limit-3 numeric-ordered archive, fail-closed rejection of dangerous
 * versions, and the fully reversible disable → archive → rollback →
 * enable round trip.
 */
class DrsM02ExtensionLifecycleTest : FunSpec({

    fun newModuleDir(): File = File(
        System.getProperty("java.io.tmpdir"),
        "drs-m02-module-" + System.nanoTime(),
    ).apply { mkdirs() }

    /** Builds a real .flex (zip) whose inner manifest carries [version]. */
    fun writeFlex(moduleDir: File, extId: String, version: String): File {
        val flex = File(moduleDir, ExtensionDefaults.createFlexName(extId))
        ZipOutputStream(flex.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(ExtensionDefaults.MANIFEST_FILE_NAME))
            zip.write(
                """{"$":"ime.extension.theme","meta":{"id":"$extId","version":"$version"}}"""
                    .toByteArray(),
            )
            zip.closeEntry()
        }
        return flex
    }

    context("disable / enable — sibling markers") {
        test("disable creates a SIBLING marker, never touches the archive; enable removes it") {
            val moduleDir = newModuleDir()
            val lifecycle = ExtensionLifecycle(moduleDir)
            writeFlex(moduleDir, "org.drs.test.simple", "1.0.0")
            lifecycle.disable("org.drs.test.simple").getOrThrow()
            val marker = File(moduleDir, "org.drs.test.simple.flex.drs-disabled")
            marker.isFile shouldBe true
            lifecycle.isDisabled("org.drs.test.simple") shouldBe true
            // the archive itself is untouched
            File(moduleDir, "org.drs.test.simple.flex").isFile shouldBe true
            lifecycle.enable("org.drs.test.simple").getOrThrow()
            marker.exists() shouldBe false
            lifecycle.isDisabled("org.drs.test.simple") shouldBe false
        }

        test("enable is idempotent; disable without an install fails honestly") {
            val moduleDir = newModuleDir()
            val lifecycle = ExtensionLifecycle(moduleDir)
            lifecycle.enable("org.drs.test.never").getOrThrow() // no-op is fine
            lifecycle.disable("org.drs.test.never").isFailure shouldBe true
        }
    }

    context("archive — naming from the manifest INSIDE the package") {
        test("archive moves the current flex to archive/<id>/<version>.flex (version read from the inner manifest)") {
            val moduleDir = newModuleDir()
            val lifecycle = ExtensionLifecycle(moduleDir)
            val extId = "org.drs.test.simple"
            writeFlex(moduleDir, extId, "1.2.0")
            val version = lifecycle.archive(extId).getOrThrow()
            version shouldBe "1.2.0"
            File(moduleDir, "archive/$extId/1.2.0.flex").isFile shouldBe true
            File(moduleDir, ExtensionDefaults.createFlexName(extId)).exists() shouldBe false
        }

        test("archive without an install fails; re-archiving the same version replaces, not duplicates") {
            val moduleDir = newModuleDir()
            val lifecycle = ExtensionLifecycle(moduleDir)
            lifecycle.archive("org.drs.test.nope").isFailure shouldBe true

            val extId = "org.drs.test.dup"
            writeFlex(moduleDir, extId, "2.0.0")
            lifecycle.archive(extId).getOrThrow()
            writeFlex(moduleDir, extId, "2.0.0")
            lifecycle.archive(extId).getOrThrow()
            lifecycle.archivedVersions(extId) shouldHaveSize 1
        }

        test("a DANGEROUS version inside the manifest is rejected fail-closed — nothing moves") {
            val moduleDir = newModuleDir()
            val lifecycle = ExtensionLifecycle(moduleDir)
            val extId = "org.drs.test.evil"
            val flex = writeFlex(moduleDir, extId, "../escape")
            lifecycle.archive(extId).isFailure shouldBe true
            // the crafted archive stays exactly where it was
            flex.isFile shouldBe true
            File(moduleDir, "archive/$extId").exists() shouldBe false
        }
    }

    context("archive limit + numeric ordering") {
        test("the limit is 3, ordered numerically — the OLDEST versions are pruned") {
            val moduleDir = newModuleDir()
            val lifecycle = ExtensionLifecycle(moduleDir)
            val extId = "org.drs.test.limit"
            for (version in listOf("1.0.0", "1.2.0", "2.9", "2.10", "3.0.0")) {
                writeFlex(moduleDir, extId, version)
                lifecycle.archive(extId).getOrThrow()
            }
            val versions = lifecycle.archivedVersions(extId)
            versions shouldHaveSize 3
            versions shouldBe listOf("3.0.0", "2.10", "2.9") // numeric, not lexicographic
            File(moduleDir, "archive/$extId/1.0.0.flex").exists() shouldBe false
            File(moduleDir, "archive/$extId/1.2.0.flex").exists() shouldBe false
        }

        test("compareVersions is part-wise numeric (2.10 > 2.9, 1.0.0 < 1.0.1)") {
            ExtensionLifecycle.compareVersions("2.10", "2.9") shouldBe 1
            ExtensionLifecycle.compareVersions("2.9", "2.10") shouldBe -1
            ExtensionLifecycle.compareVersions("1.0.0", "1.0.1") shouldBe -1
            ExtensionLifecycle.compareVersions("1.0.0", "1.0.0") shouldBe 0
        }
    }

    context("rollback — reversible both ways") {
        test("the full round trip: disable → archive → rollback → enable") {
            val moduleDir = newModuleDir()
            val lifecycle = ExtensionLifecycle(moduleDir)
            val extId = "org.drs.test.roundtrip"
            val flex = writeFlex(moduleDir, extId, "1.0.0")
            val bytesV1 = flex.readBytes()

            lifecycle.disable(extId).getOrThrow()
            lifecycle.archive(extId).getOrThrow()
            lifecycle.rollback(extId).getOrThrow() shouldBe "1.0.0"
            lifecycle.enable(extId).getOrThrow()

            flex.isFile shouldBe true
            flex.readBytes() shouldBe bytesV1 // byte-for-byte restore
            lifecycle.isDisabled(extId) shouldBe false
        }

        test("rollback with a current install archives the current FIRST (rollback stays reversible)") {
            val moduleDir = newModuleDir()
            val lifecycle = ExtensionLifecycle(moduleDir)
            val extId = "org.drs.test.rb2"
            writeFlex(moduleDir, extId, "1.0.0")
            lifecycle.archive(extId).getOrThrow()
            writeFlex(moduleDir, extId, "2.0.0") // current (newer) version
            lifecycle.rollback(extId).getOrThrow() shouldBe "1.0.0"
            // the 2.0.0 was archived before the restore — nothing was lost
            lifecycle.archivedVersions(extId).first() shouldBe "2.0.0"
            File(moduleDir, "archive/$extId/1.0.0.flex").exists() shouldBe false // moved back
        }

        test("rollback with no archive fails honestly") {
            val moduleDir = newModuleDir()
            val lifecycle = ExtensionLifecycle(moduleDir)
            lifecycle.rollback("org.drs.test.empty").isFailure shouldBe true
        }
    }

    context("versionInsidePackage honesty") {
        test("a flex without a usable manifest version reads as null") {
            val moduleDir = newModuleDir()
            val lifecycle = ExtensionLifecycle(moduleDir)
            val flex = File(moduleDir, "org.drs.test.broken.flex")
            ZipOutputStream(flex.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry(ExtensionDefaults.MANIFEST_FILE_NAME))
                zip.write("""{"no":"version here"}""".toByteArray())
                zip.closeEntry()
            }
            lifecycle.versionInsidePackage(flex) shouldBe null
        }

        test("the marker name contract ends with .flex.drs-disabled") {
            val markerName = ExtensionDefaults.createFlexName("org.x.y") + ExtensionLifecycle.DISABLED_SUFFIX
            markerName shouldContain ".flex.drs-disabled"
        }
    }
})
