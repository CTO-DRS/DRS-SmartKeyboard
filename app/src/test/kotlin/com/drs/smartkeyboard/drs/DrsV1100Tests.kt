/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.clipboard.ClipEditorHistory
import com.drs.smartkeyboard.ime.clipboard.ClipFontOption
import com.drs.smartkeyboard.ime.clipboard.ClipFontSizeOption
import com.drs.smartkeyboard.ime.clipboard.ClipSearchEngine
import com.drs.smartkeyboard.ime.clipboard.ClipTextTransforms
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * DRS v1.10.0: the popup smart editor core — the smart text algorithms
 * (case family, whitespace surgery, line operations, Arabic-aware
 * normalization, smart extractors), the literal find/replace engine with
 * bounded results and wrap-around navigation, the bounded undo/redo
 * history, and the pure font customization choices.
 * Every rule is pinned where the engine owns it — pure, no fakes.
 */
class DrsV1100Tests : FunSpec({

    // -------------------------------------------------------------
    // Case family transforms
    // -------------------------------------------------------------

    test("upper/lower are locale-invariant and pass Arabic through") {
        ClipTextTransforms.toUpper("hello Drs 123") shouldBe "HELLO DRS 123"
        ClipTextTransforms.toLower("HELLO Drs 123") shouldBe "hello drs 123"
        // Arabic has no case — the transform must be a no-op on it.
        val arabic = "مرحبا بالعالم ١٢٣"
        ClipTextTransforms.toUpper(arabic) shouldBe arabic
        ClipTextTransforms.toLower(arabic) shouldBe arabic
    }

    test("title case capitalizes each word and preserves separators") {
        ClipTextTransforms.toTitleCase("hello smart WORLD") shouldBe "Hello Smart World"
        // Tabs, newlines and runs of spaces are preserved exactly.
        ClipTextTransforms.toTitleCase("  first\tsecond\nthird  ") shouldBe "  First\tSecond\nThird  "
        ClipTextTransforms.toTitleCase("") shouldBe ""
    }

    test("invert case swaps letters and leaves everything else alone") {
        ClipTextTransforms.invertCase("aBc123!مرحبا") shouldBe "AbC123!مرحبا"
        ClipTextTransforms.invertCase("") shouldBe ""
    }

    // -------------------------------------------------------------
    // Whitespace surgery
    // -------------------------------------------------------------

    test("trim lines trims every line independently") {
        ClipTextTransforms.trimLines("  a  \n\t b \n c") shouldBe "a\nb\nc"
        ClipTextTransforms.trimLines("") shouldBe ""
    }

    test("collapse spaces merges horizontal runs but keeps line breaks") {
        ClipTextTransforms.collapseHorizontalWhitespace("a \t b\n  c   d") shouldBe "a b\n c d"
        // A non-breaking space run collapses too.
        ClipTextTransforms.collapseHorizontalWhitespace("x\u00a0\u00a0y") shouldBe "x y"
    }

    test("remove empty lines drops only blank lines") {
        ClipTextTransforms.removeEmptyLines("a\n\n  \nb") shouldBe "a\nb"
        ClipTextTransforms.removeEmptyLines("\n\n") shouldBe ""
    }

    // -------------------------------------------------------------
    // Line operations
    // -------------------------------------------------------------

    test("duplicate lines are removed keeping the first occurrence and order") {
        ClipTextTransforms.removeDuplicateLines("b\na\nb\na\n c\n c") shouldBe "b\na\n c"
    }

    test("sort lines ascending and descending pin the natural order") {
        ClipTextTransforms.sortLinesAscending("b\na\nc") shouldBe "a\nb\nc"
        ClipTextTransforms.sortLinesDescending("b\na\nc") shouldBe "c\nb\na"
    }

    test("reverse lines flips the order without touching line content") {
        ClipTextTransforms.reverseLines("1\n2\n3") shouldBe "3\n2\n1"
    }

    // -------------------------------------------------------------
    // Arabic-aware smart normalization
    // -------------------------------------------------------------

    test("arabic diacritics are stripped while letters survive") {
        val diacritized = "مُحَمَّدٌ شَاذَلِي"
        val stripped = ClipTextTransforms.removeArabicDiacritics(diacritized)
        stripped shouldBe "محمد شاذلي"
        // Letters without diacritics pass through unchanged.
        ClipTextTransforms.removeArabicDiacritics("مرحبا") shouldBe "مرحبا"
    }

    test("arabic letter normalization unifies hamza forms maqsura and ta marbuta") {
        ClipTextTransforms.normalizeArabicLetters("أإآٱ") shouldBe "اااا"
        ClipTextTransforms.normalizeArabicLetters("ى") shouldBe "ي"
        ClipTextTransforms.normalizeArabicLetters("ة") shouldBe "ه"
        // Latin text is untouched.
        ClipTextTransforms.normalizeArabicLetters("hello") shouldBe "hello"
    }

    // -------------------------------------------------------------
    // Smart extractors
    // -------------------------------------------------------------

    test("url extraction returns ordered deduplicated links") {
        val text = "see https://drs.app/a then www.example.com then https://drs.app/a"
        ClipTextTransforms.extractUrls(text) shouldContainExactly listOf(
            "https://drs.app/a", "www.example.com",
        )
        ClipTextTransforms.extractUrls("no links here") shouldContainExactly emptyList()
        ClipTextTransforms.extractUrls("") shouldContainExactly emptyList()
    }

    test("email extraction catches real addresses and skips bare words") {
        ClipTextTransforms.extractEmails("mail user@example.com or a.b@sub.domain.co") shouldContainExactly listOf(
            "user@example.com", "a.b@sub.domain.co",
        )
        ClipTextTransforms.extractEmails("not@anemail") shouldContainExactly emptyList()
    }

    test("phone extraction guards on at least seven digits") {
        ClipTextTransforms.extractPhoneNumbers("call 0501234567 or +966 50 123 4567") shouldContainExactly listOf(
            "0501234567", "+966 50 123 4567",
        )
        // Short runs (a year, a small number) are not phone numbers.
        ClipTextTransforms.extractPhoneNumbers("in 2026 there were 123456 items") shouldContainExactly emptyList()
    }

    // -------------------------------------------------------------
    // The literal find/replace engine
    // -------------------------------------------------------------

    test("find matches are literal — regex metacharacters never interpret") {
        val text = "a.c a.c abc"
        val matches = ClipSearchEngine.findMatches(text, "a.c", ignoreCase = false)
        matches.size shouldBe 2
        matches[0].start shouldBe 0
        matches[1].end shouldBe 7
    }

    test("case sensitivity toggle and empty query contracts") {
        ClipSearchEngine.findMatches("Abc abc", "abc", ignoreCase = true).size shouldBe 2
        ClipSearchEngine.findMatches("Abc abc", "abc", ignoreCase = false).size shouldBe 1
        ClipSearchEngine.findMatches("anything", "", ignoreCase = true) shouldContainExactly emptyList()
    }

    test("the match list is capped at MAX_MATCHES") {
        ClipSearchEngine.MAX_MATCHES shouldBe 1000
        val text = "ab".repeat(2000) // 2000 potential matches
        ClipSearchEngine.findMatches(text, "ab", ignoreCase = false).size shouldBe 1000
    }

    test("replace one replaces exactly the chosen range") {
        val text = "abcabc"
        val first = ClipSearchEngine.findMatches(text, "bc", ignoreCase = false).first()
        ClipSearchEngine.replaceOne(text, first, "X") shouldBe "aXabc"
    }

    test("replace all is literal and reports the honest count") {
        // The `$0` replacement must land verbatim — no regex group semantics.
        val (newText, count) = ClipSearchEngine.replaceAll("a.c a.c", "a.c", "\$0x", ignoreCase = false)
        newText shouldBe "\$0x \$0x"
        count shouldBe 2
        // Nothing matched → text unchanged, count zero.
        val (same, zero) = ClipSearchEngine.replaceAll("abc", "zzz", "X", ignoreCase = false)
        same shouldBe "abc"
        zero shouldBe 0
    }

    test("match navigation wraps around and clamps stale indices") {
        // Forward wrap.
        ClipSearchEngine.nextMatchIndex(3, 0) shouldBe 1
        ClipSearchEngine.nextMatchIndex(3, 2) shouldBe 0
        // Backward wrap.
        ClipSearchEngine.prevMatchIndex(3, 0) shouldBe 2
        ClipSearchEngine.prevMatchIndex(3, 1) shouldBe 0
        // Stale index (text changed under the UI) never crashes.
        ClipSearchEngine.nextMatchIndex(3, 99) shouldBe 1
        ClipSearchEngine.prevMatchIndex(3, -5) shouldBe 2
        // No matches → -1.
        ClipSearchEngine.nextMatchIndex(0, 0) shouldBe -1
        ClipSearchEngine.prevMatchIndex(0, 4) shouldBe -1
    }

    // -------------------------------------------------------------
    // The bounded undo/redo history
    // -------------------------------------------------------------

    test("undo and redo alternate cleanly over the pushed states") {
        val history = ClipEditorHistory()
        history.canUndo shouldBe false
        history.canRedo shouldBe false

        history.push("a") // before change 1
        history.push("b") // before change 2

        history.undo("c") shouldBe "b"
        history.canRedo shouldBe true
        history.redo("b") shouldBe "c"
        history.canRedo shouldBe false

        history.undo("c") shouldBe "b"
        history.undo("b") shouldBe "a"
        history.canUndo shouldBe false
        history.undo("a") shouldBe null

        history.redo("a") shouldBe "b"
        history.redo("b") shouldBe "c"
        history.redo("c") shouldBe null
    }

    test("a new push voids the redo branch and the stack caps at its bound") {
        val history = ClipEditorHistory()
        history.push("a")
        history.undo("b")
        history.canRedo shouldBe true
        history.push("x")
        history.canRedo shouldBe false

        val tiny = ClipEditorHistory(maxStates = 3)
        for (state in 1..4) tiny.push(state.toString())
        tiny.undo("5") shouldBe "4"
        tiny.undo("4") shouldBe "3"
        tiny.undo("3") shouldBe "2"
        tiny.undo("2") shouldBe null // "1" was dropped with the oldest entry
        tiny.clear()
        tiny.canUndo shouldBe false
        tiny.canRedo shouldBe false
    }

    // -------------------------------------------------------------
    // The pure font customization choices
    // -------------------------------------------------------------

    test("the editor exposes five distinct font families and four size steps") {
        ClipFontOption.entries.size shouldBe 5
        ClipFontOption.entries.map { it.id }.toSet().size shouldBe 5
        ClipFontOption.entries.map { it.name } shouldContainExactly listOf(
            "DEFAULT", "SANS_SERIF", "SERIF", "MONOSPACE", "CURSIVE",
        )

        ClipFontSizeOption.entries.size shouldBe 4
        ClipFontSizeOption.entries.map { it.spValue } shouldContainExactly listOf(12, 14, 17, 20)
        ClipFontSizeOption.entries.map { it.id }.toSet().size shouldBe 4
    }
})
