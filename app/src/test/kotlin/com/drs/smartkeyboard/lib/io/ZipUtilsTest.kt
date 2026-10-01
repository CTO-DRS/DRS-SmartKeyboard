/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.io

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File
import kotlin.io.path.createTempDirectory
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

/**
 * DRS p9 (T-1/T-2) — regression contracts for the ZipUtils hardening shipped
 * by the p7/p8 security rounds and the p9 test seam:
 *  - p7 (E3-2): atomic .flex publish ([ZipUtils.publishAtomic]);
 *  - p8 (S-3): zip-bomb caps enforced on the bytes ACTUALLY written, with the
 *    partially written file removed on abort;
 *  - p9 (T-2): the caps became defaulted parameters of [ZipUtils.unzip] so
 *    this suite can exercise the skip/abort boundaries with tiny budgets
 *    instead of 100MB+ fixtures.
 */
class ZipUtilsTest : FunSpec({

    val tempDirs = mutableListOf<File>()

    fun tempfile(): File {
        val dir = createTempDirectory("drs-p9-zip").toFile()
        dir.deleteOnExit()
        tempDirs.add(dir)
        return dir
    }

    afterProject {
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    context("unzip — honest archives") {
        test("honest archive extracts fully, byte-identical, incl. nested dir path") {
            val root = tempfile()
            val dst = File(root, "dst")
            val zip = File(root, "honest.zip")
            writeZip(
                zip,
                listOf(
                    "a.txt" to "hello".toByteArray(),
                    "sub/" to null,
                    "sub/b.json" to "{\"k\":1}".toByteArray(),
                ),
            )

            shouldNotThrowAny { ZipUtils.unzip(zip, dst) }

            File(dst, "a.txt").readBytes() shouldBe "hello".toByteArray()
            File(dst, "sub").isDirectory shouldBe true
            File(dst, "sub/b.json").readText() shouldBe "{\"k\":1}"
        }

        test("round-trip zip(srcDir, dstFile) then unzip preserves entries incl. dir entries") {
            val root = tempfile()
            val srcDir = File(root, "src").apply { mkdirs() }
            File(srcDir, "a.txt").writeText("alpha")
            File(srcDir, "sub").apply { mkdirs() }
            File(srcDir, "sub/b.json").writeText("{\"k\":2}")
            File(srcDir, "empty").apply { mkdirs() }
            val zipFile = File(root, "out.flex")

            shouldNotThrowAny { ZipUtils.zip(srcDir, zipFile) }

            val outDir = File(root, "out-extracted")
            shouldNotThrowAny { ZipUtils.unzip(zipFile, outDir) }

            val srcFiles = srcDir.walkTopDown().filter { it.isFile }
                .map { it.relativeTo(srcDir).invariantSeparatorsPath }.toSortedSet()
            val outFiles = outDir.walkTopDown().filter { it.isFile }
                .map { it.relativeTo(outDir).invariantSeparatorsPath }.toSortedSet()
            outFiles shouldBe srcFiles
            File(outDir, "a.txt").readText() shouldBe "alpha"
            File(outDir, "sub/b.json").readText() shouldBe "{\"k\":2}"
            File(outDir, "empty").isDirectory shouldBe true
        }
    }

    context("unzip — zip-slip and path bounds") {
        test("zip-slip entries are skipped: nothing outside dstDir, no throw") {
            val root = tempfile()
            val dst = File(root, "dst")
            val zip = File(root, "slip.zip")
            writeZip(
                zip,
                listOf(
                    "../../evil-p9-a.txt" to "evil".toByteArray(),
                    "a/../../evil-p9-b.txt" to "evil2".toByteArray(),
                    "ok.txt" to "ok".toByteArray(),
                ),
            )

            shouldNotThrowAny { ZipUtils.unzip(zip, dst) }

            File(dst, "ok.txt").readText() shouldBe "ok"
            // Canonical escape targets of both hostile entries — nothing was written there.
            File(dst, "../../evil-p9-a.txt").canonicalFile.exists() shouldBe false
            File(dst, "a/../../evil-p9-b.txt").canonicalFile.exists() shouldBe false
            dst.listFiles()?.map { it.name }?.toSortedSet() shouldBe sortedSetOf("ok.txt")
        }

        test("entry name longer than 255 chars is skipped, exactly 255 still extracts") {
            val root = tempfile()
            val dst = File(root, "dst")
            val zip = File(root, "longname.zip")
            val okName = "a".repeat(251) + ".txt" // 255 chars — at the boundary, kept
            val skipName = "a".repeat(252) + ".txt" // 256 chars — skipped
            writeZip(
                zip,
                listOf(
                    okName to "ok".toByteArray(),
                    skipName to "skipped".toByteArray(),
                ),
            )

            shouldNotThrowAny { ZipUtils.unzip(zip, dst) }

            File(dst, okName).readText() shouldBe "ok"
            File(dst, skipName).exists() shouldBe false
        }

        test("canonical path longer than 1023 chars is skipped silently") {
            val root = tempfile()
            var deep: File = root
            repeat(5) { deep = File(deep, "d".repeat(230)) }
            deep.mkdirs()
            val zip = File(root, "deeppath.zip")
            writeZip(zip, listOf("f.txt" to "x".toByteArray()))

            shouldNotThrowAny { ZipUtils.unzip(zip, deep) }

            deep.canonicalPath.length.shouldBeGreaterThan(1023)
            File(deep, "f.txt").exists() shouldBe false
            deep.listFiles()?.isEmpty() shouldBe true
        }
    }

    context("unzip — argument validation") {
        test("missing src file throws IllegalArgumentException") {
            val root = tempfile()
            shouldThrow<IllegalArgumentException> {
                ZipUtils.unzip(File(root, "nope.zip"), File(root, "dst"))
            }
        }

        test("directory as src file throws IllegalArgumentException") {
            val root = tempfile()
            val asDir = File(root, "dir.zip").apply { mkdirs() }
            shouldThrow<IllegalArgumentException> {
                ZipUtils.unzip(asDir, File(root, "dst"))
            }
        }
    }

    context("unzip — per-entry cap seam (DRS p9 T-2)") {
        test("declared size above maxEntryBytes skips entry without stub file, no throw") {
            val root = tempfile()
            val dst = File(root, "dst")
            val zip = File(root, "bigentry.zip")
            writeZip(
                zip,
                listOf(
                    "big.txt" to "0123456789".toByteArray(), // 10 bytes > cap 4
                    "small.txt" to "abc".toByteArray(), // control: proves unzip ran
                ),
            )

            shouldNotThrowAny { ZipUtils.unzip(zip, dst, maxEntryBytes = 4) }

            File(dst, "big.txt").exists() shouldBe false
            File(dst, "small.txt").readText() shouldBe "abc"
        }

        test("declared size exactly maxEntryBytes is extracted (strict > boundary)") {
            val root = tempfile()
            val dst = File(root, "dst")
            val zip = File(root, "exactentry.zip")
            writeZip(zip, listOf("exact.txt" to "abcd".toByteArray()))

            shouldNotThrowAny { ZipUtils.unzip(zip, dst, maxEntryBytes = 4) }

            File(dst, "exact.txt").readText() shouldBe "abcd"
        }
    }

    context("unzip — archive-total cap seam (DRS p8 S-3 / p9 T-2)") {
        test("archive total exactly maxTotalBytes extracts all entries (strict > boundary)") {
            val root = tempfile()
            val dst = File(root, "dst")
            val zip = File(root, "exacttotal.zip")
            writeZip(
                zip,
                listOf(
                    "a.txt" to "12345".toByteArray(),
                    "b.txt" to "67890".toByteArray(),
                ),
            )

            shouldNotThrowAny { ZipUtils.unzip(zip, dst, maxEntryBytes = 100, maxTotalBytes = 10) }

            File(dst, "a.txt").readText() shouldBe "12345"
            File(dst, "b.txt").readText() shouldBe "67890"
        }

        test("entry after budget exhaustion throws ISE, earlier files intact, no next file") {
            val root = tempfile()
            val dst = File(root, "dst")
            val zip = File(root, "overbudget.zip")
            writeZip(
                zip,
                listOf(
                    "a.txt" to "12345".toByteArray(),
                    "b.txt" to "67890".toByteArray(),
                    "c.txt" to "xyz".toByteArray(),
                ),
            )

            shouldThrow<IllegalStateException> {
                ZipUtils.unzip(zip, dst, maxEntryBytes = 100, maxTotalBytes = 10)
            }

            File(dst, "a.txt").readText() shouldBe "12345"
            File(dst, "b.txt").readText() shouldBe "67890"
            File(dst, "c.txt").exists() shouldBe false
        }

        test("mid-stream overrun deletes the partial file, earlier files intact") {
            val root = tempfile()
            val dst = File(root, "dst")
            val zip = File(root, "midstream.zip")
            writeZip(
                zip,
                listOf(
                    "a.txt" to "123456".toByteArray(),
                    "b.txt" to "12345678".toByteArray(), // streams past the remaining budget of 4
                ),
            )

            shouldThrow<IllegalStateException> {
                ZipUtils.unzip(zip, dst, maxEntryBytes = 100, maxTotalBytes = 10)
            }

            File(dst, "a.txt").readText() shouldBe "123456"
            // The partially written file was removed by the abort path (p8 S-3).
            File(dst, "b.txt").exists() shouldBe false
        }
    }

    context("publishAtomic (DRS p7 E3-2)") {
        test("happy path publishes dst content, no tmp left behind") {
            val root = tempfile()
            val src = File(root, "payload.bin").apply { writeText("hello flex") }
            val dst = File(root, "out/published.flex")

            shouldNotThrowAny { ZipUtils.publishAtomic(src, dst) }

            dst.readText() shouldBe "hello flex"
            dst.parentFile?.list()?.filter { it.endsWith(".tmp") } shouldBe emptyList()
        }

        test("republish over existing dst replaces content, tmp gone") {
            val root = tempfile()
            val src = File(root, "payload.bin").apply { writeText("new content") }
            val dst = File(root, "out/published.flex").apply { parentFile?.mkdirs(); writeText("old content") }

            shouldNotThrowAny { ZipUtils.publishAtomic(src, dst) }

            dst.readText() shouldBe "new content"
            dst.parentFile?.list()?.filter { it.endsWith(".tmp") } shouldBe emptyList()
        }

        test("missing src propagates honestly, pre-existing dst byte-identical, no tmp left") {
            val root = tempfile()
            val missing = File(root, "missing.bin")
            val dst = File(root, "out/published.flex").apply { parentFile?.mkdirs(); writeText("keep me") }

            // src.copyTo(tmp) on a missing source throws NIO's
            // NoSuchFileException (a FileSystemException, NOT a
            // java.io.FileNotFoundException subclass) — pin the real type.
            shouldThrow<NoSuchFileException> { ZipUtils.publishAtomic(missing, dst) }

            dst.readText() shouldBe "keep me"
            dst.parentFile?.list()?.filter { it.endsWith(".tmp") } shouldBe emptyList()
        }

        test("non-empty directory at dst throws honestly, no tmp left") {
            val root = tempfile()
            val src = File(root, "payload.bin").apply { writeText("payload") }
            val dstDir = File(root, "out/occupied").apply { mkdirs() }
            File(dstDir, "inner.txt").writeText("occupied")

            // rename file→dir fails, the fallback direct copy then throws on the directory.
            shouldThrow<IOException> { ZipUtils.publishAtomic(src, dstDir) }

            File(dstDir, "inner.txt").readText() shouldBe "occupied"
            dstDir.parentFile?.list()?.filter { it.endsWith(".tmp") } shouldBe emptyList()
        }

        test("unicode/space name publishes") {
            val root = tempfile()
            val src = File(root, "payload.bin").apply { writeText("theme bytes") }
            val dst = File(root, "out/my theme.flex")

            shouldNotThrowAny { ZipUtils.publishAtomic(src, dst) }

            dst.readText() shouldBe "theme bytes"
        }
    }
})

/** Builds a zip at [dst] from `(name, bytes)` pairs; `null` bytes = directory entry (name ends with '/'). */
private fun writeZip(dst: File, entries: List<Pair<String, ByteArray?>>) {
    dst.parentFile?.mkdirs()
    ZipOutputStream(FileOutputStream(dst)).use { zipOut ->
        for ((name, bytes) in entries) {
            zipOut.putNextEntry(ZipEntry(name))
            if (bytes != null) {
                zipOut.write(bytes)
            }
            zipOut.closeEntry()
        }
    }
}
