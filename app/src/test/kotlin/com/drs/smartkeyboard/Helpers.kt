/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.FloatToleranceMatcher
import io.kotest.matchers.floats.gt
import io.kotest.matchers.floats.gte
import io.kotest.matchers.floats.lt
import io.kotest.matchers.floats.lte
import io.kotest.matchers.shouldBe
import java.io.File

fun Dp.shouldBeLessThan(other: Dp, tolerance: Dp = 0.dp): Dp {
    this.value shouldBe lt((other + tolerance).value)
    return this
}

fun Dp.shouldBeLessThanOrEqualTo(other: Dp, tolerance: Dp = 0.dp): Dp {
    this.value shouldBe lte((other + tolerance).value)
    return this
}

fun Dp.shouldBeGreaterThan(other: Dp, tolerance: Dp = 0.dp): Dp {
    this.value shouldBe gt((other - tolerance).value)
    return this
}

fun Dp.shouldBeGreaterThanOrEqualTo(other: Dp, tolerance: Dp = 0.dp): Dp {
    this.value shouldBe gte((other - tolerance).value)
    return this
}

infix fun Dp.plusOrMinus(tolerance: Dp): DpToleranceMatcher = DpToleranceMatcher(this, tolerance)

class DpToleranceMatcher(expected: Dp, tolerance: Dp) : Matcher<Dp> {
    val matcher = FloatToleranceMatcher(expected.value, tolerance.value)

    override fun test(value: Dp): MatcherResult {
        return matcher.test(value.value)
    }
}

// -------------------------------------------------------------------------
// CWD-independent repo discovery (DRS p9 test hardening): the unit-test
// working directory differs between module and root Gradle invocations, so
// resource-backed specs must never build File("src/main/...") relative to
// the CWD. These helpers walk up to the repository root (the directory
// holding settings.gradle.kts) — the same discovery contract DrsV1200Tests
// pins with its own repoRoot() — and resolve every path from there.
// -------------------------------------------------------------------------

/** Walks up from the current working directory to the repository root. */
internal fun repoRoot(): File {
    var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
    repeat(6) {
        if (File(dir, "settings.gradle.kts").exists()) return dir
        dir = dir.parentFile ?: File("/").absoluteFile
    }
    error("repo root (settings.gradle.kts) not found from ${System.getProperty("user.dir")}")
}

/** A file or directory under the app module's src/main/res. */
internal fun appResDir(vararg parts: String): File =
    parts.fold(File(repoRoot(), "app/src/main/res")) { parent, child -> File(parent, child) }

/** A file or directory under the app module's src/main/assets. */
internal fun appAssetsDir(vararg parts: String): File =
    parts.fold(File(repoRoot(), "app/src/main/assets")) { parent, child -> File(parent, child) }

/** The full text of app/src/main/res/[dir]/strings.xml, failing with the absolute path when missing. */
internal fun readStringsXml(dir: String): String {
    val file = appResDir(dir, "strings.xml")
    return file.takeIf { it.exists() }?.readText()
        ?: error("strings.xml not found at ${file.absolutePath} (cwd=${System.getProperty("user.dir")})")
}

/** Asserts every key appears as a name="$key" entry in the given strings.xml text. */
internal fun assertStringsExist(stringsXmlText: String, keys: List<String>) {
    for (key in keys) {
        ("""name="$key"""" in stringsXmlText).shouldBeTrue()
    }
}
