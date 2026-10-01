/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

import com.drs.smartkeyboard.app.ext.containedSubFile
import com.drs.smartkeyboard.app.settings.advanced.containedRestoreMediaFile
import com.drs.smartkeyboard.lib.validate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory

/**
 * DRS p9 (T-3/T-4) — regression contracts for the extension/restore path-safety
 * helpers shipped by the p7/p8 security rounds:
 *  - p7 (E3-4): [ExtensionValidation.isSafeStylesheetPath] + the
 *    ThemeComponentStylesheetPath rule block traversal at validation time, and
 *    ExtensionEditScreen's canonical containment ([containedSubFile]) at write time;
 *  - v1.28.0: [ExtensionValidation.META_ID_REGEX] — the shared id contract also
 *    enforced by ExtensionManager.import and DrsPackageManager;
 *  - p8 (S-4): [containedRestoreMediaFile] sanitizes untrusted clipboard media
 *    file names from a restore archive before they reach the filesystem.
 */
class ExtensionPathSafetyTest : FunSpec({

    val tempDirs = mutableListOf<File>()

    fun tempfile(): File {
        val dir = createTempDirectory("drs-p9-extpath").toFile()
        dir.deleteOnExit()
        tempDirs.add(dir)
        return dir
    }

    afterProject {
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    context("isSafeStylesheetPath (DRS p7 E3-4)") {
        test("empty path is valid (documented default)") {
            ExtensionValidation.isSafeStylesheetPath("") shouldBe true
        }

        test("legit relative subpaths are valid") {
            ExtensionValidation.isSafeStylesheetPath("stylesheets/x.json") shouldBe true
            ExtensionValidation.isSafeStylesheetPath("sub/dir/x.json") shouldBe true
        }

        test("parent traversal and absolute paths are rejected") {
            listOf(
                "../evil.json",
                "..",
                "a/../b",
                "x/../../y/z",
                "/abs/x.json",
            ).forEach { path ->
                ExtensionValidation.isSafeStylesheetPath(path) shouldBe false
            }
        }

        test("mid-dot segments are accepted (only whole '..' segments are rejected)") {
            // Pinned actual contract: the regex gate only rejects whole ".."
            // segments; the canonical containment at write/read time is the real
            // safety net for anything else.
            ExtensionValidation.isSafeStylesheetPath("a..b/x.json") shouldBe true
            ExtensionValidation.isSafeStylesheetPath("...") shouldBe true
        }
    }

    context("META_ID_REGEX (DRS v1.28.0 shared id contract)") {
        test("accepts lowercase dotted ids incl. digit-led inner segments") {
            listOf(
                "a",
                "com.example.keyboard_layout",
                "a.0b", // digit-led inner segment — pinned enforced truth
            ).forEach { id ->
                ExtensionValidation.META_ID_REGEX.matches(id) shouldBe true
            }
        }

        test("rejects uppercase, digit/underscore starts, empty segments and separators") {
            listOf(
                "A",
                "1abc",
                "_abc",
                "a..b",
                "a.",
                "a-1",
                "com/../../etc",
                "لوحة",
            ).forEach { id ->
                ExtensionValidation.META_ID_REGEX.matches(id) shouldBe false
            }
        }
    }

    context("ThemeComponentStylesheetPath rule") {
        test("empty path is valid, blank-only path is invalid") {
            validate(ExtensionValidation.ThemeComponentStylesheetPath, "").isValid() shouldBe true
            validate(ExtensionValidation.ThemeComponentStylesheetPath, " ").isValid() shouldBe false
        }

        test("forbidden characters and traversal are invalid") {
            validate(ExtensionValidation.ThemeComponentStylesheetPath, "a:b.json").isValid() shouldBe false
            validate(ExtensionValidation.ThemeComponentStylesheetPath, "..").isValid() shouldBe false
        }

        test("legit relative subpaths are valid") {
            validate(ExtensionValidation.ThemeComponentStylesheetPath, "sub/dir/x.json").isValid() shouldBe true
        }
    }

    context("containedSubFile (DRS p7 E3-4 / p9 T-3)") {
        test("inside relative path resolves to a file under root") {
            val root = tempfile()
            val resolved = containedSubFile(root, "a/../b.json")!!
            resolved.path shouldBe File(root, "b.json").canonicalPath
        }

        test("escaping relative paths resolve to null") {
            val root = tempfile()
            containedSubFile(root, "../../x") shouldBe null
            containedSubFile(root, "a/../../x") shouldBe null
        }

        test("empty relPath returns the root itself (pinned edge behavior)") {
            val root = tempfile()
            val resolved = containedSubFile(root, "")!!
            resolved.canonicalPath shouldBe root.canonicalPath
        }

        test("NUL-embedded name throws IOException from canonicalization (pinned actual behavior)") {
            // DEVIATION from the audit's "containment still holds" expectation:
            // java.io.File.getCanonicalFile() on the JVM throws IOException
            // ("Invalid file path") for a path containing NUL instead of
            // resolving it — so the helper propagates the exception rather than
            // returning null. Callers reaching this helper do not catch it, so
            // a NUL-bearing stylesheetPath would crash the save flow (reported
            // as a suspected new gap; the validation rule upstream passes NUL).
            val root = tempfile()
            shouldThrow<IOException> { containedSubFile(root, "a\u0000b.json") }
        }
    }

    context("containedRestoreMediaFile (DRS p8 S-4 / p9 T-4)") {
        test("null, empty, separator and dot-segment names are rejected") {
            val root = tempfile()
            containedRestoreMediaFile(root, null) shouldBe null
            containedRestoreMediaFile(root, "") shouldBe null
            containedRestoreMediaFile(root, "a/b.png") shouldBe null
            containedRestoreMediaFile(root, "a\\b.png") shouldBe null
            containedRestoreMediaFile(root, "..") shouldBe null
            containedRestoreMediaFile(root, ".") shouldBe null
        }

        test("real backing file resolves inside the extracted clipboard_files dir") {
            val root = tempfile()
            val clipDir = File(root, "clipboard_files").apply { mkdirs() }
            File(clipDir, "clip.png").writeBytes(byteArrayOf(1, 2, 3))

            val resolved = containedRestoreMediaFile(root, "clip.png")!!

            resolved.path.startsWith(clipDir.canonicalPath + File.separator) shouldBe true
            resolved.name shouldBe "clip.png"
        }

        test("traversal and hidden names are rejected") {
            val root = tempfile()
            val clipDir = File(root, "clipboard_files").apply { mkdirs() }
            File(clipDir, "clip.png").writeBytes(byteArrayOf(1))

            containedRestoreMediaFile(root, "../../drs_state.json") shouldBe null
            containedRestoreMediaFile(root, ".hidden.png") shouldBe null // leading-dot rejection, pinned
        }
    }
})
