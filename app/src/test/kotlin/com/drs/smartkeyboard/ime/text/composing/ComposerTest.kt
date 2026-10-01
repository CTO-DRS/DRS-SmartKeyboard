/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.text.composing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ComposerTest : FunSpec({

    context("Appender") {
        test("appends any toInsert without deleting anything") {
            Appender.getActions("", "x") shouldBe (0 to "x")
            Appender.getActions("some preceding text", "x") shouldBe (0 to "x")
        }

        test("exposes the fallback identity") {
            Appender.id shouldBe "appender"
            Appender.toRead shouldBe 0
        }
    }

    context("WithRules") {
        val composer = WithRules(
            id = "test-composer",
            label = "Test Composer",
            rules = mapOf("a" to "A", "ab" to "B"),
        )

        test("exposes id, label and the toRead window") {
            composer.id shouldBe "test-composer"
            composer.label shouldBe "Test Composer"
            // toRead is maxKeyLen - 1 (the last char arrives as toInsert).
            composer.toRead shouldBe 1
        }

        test("a single-char rule set needs no read window") {
            WithRules("tiny", "Tiny", mapOf("a" to "A")).toRead shouldBe 0
        }

        test("matches the longest applicable rule first") {
            // str = "xab" ends with both "ab" (2) and... "a" does not match here,
            // so also pin a truly ambiguous set below.
            composer.getActions("x", "ab") shouldBe (1 to "B")
        }

        test("longest-key-first wins over shorter keys covering the same tail") {
            // str = "xab" ends with "b" (len 1) and "ab" (len 2): the longer key
            // must win. A shortest-first implementation would return "small".
            val ambiguous = WithRules(
                id = "ambiguous",
                label = "Ambiguous",
                rules = mapOf("b" to "small", "ab" to "BIG"),
            )
            ambiguous.getActions("x", "ab") shouldBe (1 to "BIG")
        }

        test("deletes the matched key length minus one characters") {
            // "ab" matched on 2 chars → delete 1 (the toInsert char stays).
            composer.getActions("x", "ab").first shouldBe 1
            // "a" matched on 1 char → delete 0.
            composer.getActions("x", "a").first shouldBe 0
        }

        test("uppercase first char of the matched run uppercases the value") {
            // str = "xA" matches rule "a" case-insensitively; the matched run
            // starts with an uppercase char → value is uppercased. A 1-char key
            // deletes 0 chars (the committed text is untouched).
            composer.getActions("x", "A") shouldBe (0 to "A")

            val cased = WithRules("cased", "Cased", mapOf("a" to "ay"))
            cased.getActions("x", "a") shouldBe (0 to "ay")
            cased.getActions("x", "A") shouldBe (0 to "AY")
        }

        test("uppercase detection looks at the whole matched run, not the insert char") {
            // Rule "ab", typed as "xA" + "b": the matched run "Ab" starts with
            // an uppercase char → uppercased value.
            val ab = WithRules("ab2", "Ab2", mapOf("ab" to "b"))
            ab.getActions("xA", "b") shouldBe (1 to "B")
        }

        test("no matching rule passes toInsert through") {
            composer.getActions("q", "z") shouldBe (0 to "z")
            composer.getActions("", "z") shouldBe (0 to "z")
        }
    }
})
