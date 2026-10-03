/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.util.Locale

/**
 * Unit tests for the pure text transformation engine behind the technical
 * text tools panel (DRS v1.0.6). Every tool the UI exposes must behave
 * exactly as its description promises - these tests pin that behavior.
 */
class DrsTextToolsTest : FunSpec({

    val en = Locale.US

    test("uppercase and lowercase") {
        DrsTextTools.apply(DrsTextTool.UPPERCASE, "Hello World", en) shouldBe "HELLO WORLD"
        DrsTextTools.apply(DrsTextTool.LOWERCASE, "Hello World", en) shouldBe "hello world"
    }

    test("title case uppercases word starts only") {
        DrsTextTools.apply(DrsTextTool.TITLE_CASE, "hello world", en) shouldBe "Hello World"
        DrsTextTools.apply(DrsTextTool.TITLE_CASE, "héllo again", en) shouldBe "Héllo Again"
        // Punctuation between words starts a new word.
        DrsTextTools.apply(DrsTextTool.TITLE_CASE, "hello, world!", en) shouldBe "Hello, World!"
    }

    test("sentence case capitalizes after boundaries") {
        DrsTextTools.apply(DrsTextTool.SENTENCE_CASE, "HELLO WORLD. HOW ARE YOU? FINE!", en) shouldBe
            "Hello world. How are you? Fine!"
        DrsTextTools.apply(DrsTextTool.SENTENCE_CASE, "first line\nsecond line", en) shouldBe
            "First line\nSecond line"
    }

    test("trim spaces collapses runs and edges") {
        DrsTextTools.apply(DrsTextTool.TRIM_SPACES, "  a   b  c  ", en) shouldBe "a b c"
        // Newlines are preserved by design (only horizontal spaces collapse).
        DrsTextTools.apply(DrsTextTool.TRIM_SPACES, "a\nb", en) shouldBe "a\nb"
    }

    test("trim line edges keeps empty lines") {
        DrsTextTools.apply(DrsTextTool.TRIM_LINE_EDGES, "  a \n b  ", en) shouldBe "a\nb"
    }

    test("remove empty lines drops blank only") {
        DrsTextTools.apply(DrsTextTool.REMOVE_EMPTY_LINES, "a\n\n   \nb", en) shouldBe "a\nb"
    }

    test("remove line breaks joins with single space") {
        DrsTextTools.apply(DrsTextTool.REMOVE_LINE_BREAKS, "a\n  b\nc", en) shouldBe "a b c"
    }

    test("sort lines orders alphabetically") {
        DrsTextTools.apply(DrsTextTool.SORT_LINES, "cherry\napple\nbanana", en) shouldBe
            "apple\nbanana\ncherry"
    }

    test("remove duplicate lines keeps first occurrence") {
        DrsTextTools.apply(DrsTextTool.REMOVE_DUPLICATE_LINES, "a\nb\na\nc\nb", en) shouldBe "a\nb\nc"
    }

    test("number lines prefixes each line with its 1-based order") {
        DrsTextTools.apply(DrsTextTool.NUMBER_LINES, "a\nb\nc", en) shouldBe "1. a\n2. b\n3. c"
        // A trailing newline is preserved through the transform.
        DrsTextTools.apply(DrsTextTool.NUMBER_LINES, "a\nb\n", en) shouldBe "1. a\n2. b\n"
        // Blank lines are numbered too — the transform never reorders.
        DrsTextTools.apply(DrsTextTool.NUMBER_LINES, "x\n\ny", en) shouldBe "1. x\n2. \n3. y"
    }

    test("reverse lines flips the line order") {
        DrsTextTools.apply(DrsTextTool.REVERSE_LINES, "a\nb\nc", en) shouldBe "c\nb\na"
        // A trailing newline is preserved through the transform.
        DrsTextTools.apply(DrsTextTool.REVERSE_LINES, "a\nb\n", en) shouldBe "b\na\n"
    }

    test("wrap quotes surrounds text with locale-aware marks") {
        val ar = Locale("ar")
        DrsTextTools.apply(DrsTextTool.WRAP_QUOTES, "مرحبا", ar) shouldBe "«مرحبا»"
        DrsTextTools.apply(DrsTextTool.WRAP_QUOTES, "hello", en) shouldBe "\"hello\""
        // Multiline text is wrapped as one block, not per line.
        DrsTextTools.apply(DrsTextTool.WRAP_QUOTES, "a\nb", en) shouldBe "\"a\nb\""
    }

    test("remove diacritics strips arabic harakat") {
        // "مُحَمَّد" with diacritics -> "محمد" without.
        DrsTextTools.apply(DrsTextTool.REMOVE_DIACRITICS, "مُحَمَّد", en) shouldBe "محمد"
        // Plain text passes through unchanged.
        DrsTextTools.apply(DrsTextTool.REMOVE_DIACRITICS, "السلام عليكم", en) shouldBe "السلام عليكم"
    }

    test("normalize punctuation removes space before and adds after") {
        DrsTextTools.apply(DrsTextTool.NORMALIZE_PUNCTUATION, "hello , world !", en) shouldBe
            "hello, world!"
        // Arabic comma and question mark behave the same.
        DrsTextTools.apply(DrsTextTool.NORMALIZE_PUNCTUATION, "مرحبا ، كيف حالك ؟", en) shouldBe
            "مرحبا، كيف حالك؟"
    }

    test("normalize punctuation never breaks domains or numbers") {
        // '.' before an Arabic letter gains a space; before Latin it must not.
        DrsTextTools.apply(DrsTextTool.NORMALIZE_PUNCTUATION, "انتهى.كلمة example.com 3.14", en) shouldBe
            "انتهى. كلمة example.com 3.14"
    }

    test("clean text combines cleanup steps") {
        DrsTextTools.apply(DrsTextTool.CLEAN_TEXT, "  hello , world ! \n\n  ", en) shouldBe
            "hello, world!"
    }

    test("count info reports real counts") {
        val (chars, words, lines) = DrsTextTools.countInfo("hello drs world\nsecond line")
        chars shouldBe 27
        words shouldBe 5
        lines shouldBe 2
    }

    // DRS v1.3.0: digit conversion, tatweel removal, blank-line collapsing.

    test("to arabic digits converts only western digits") {
        DrsTextTools.apply(DrsTextTool.TO_ARABIC_DIGITS, "سنة 2026", en) shouldBe "سنة ٢٠٢٦"
        // Mixed content: letters, punctuation and spacing are untouched.
        DrsTextTools.apply(DrsTextTool.TO_ARABIC_DIGITS, "a1.b/c-2", en) shouldBe "a١.b/c-٢"
        // No digits -> unchanged.
        DrsTextTools.apply(DrsTextTool.TO_ARABIC_DIGITS, "hello", en) shouldBe "hello"
    }

    test("to western digits converts arabic-indic and persian digits") {
        DrsTextTools.apply(DrsTextTool.TO_WESTERN_DIGITS, "سنة ٢٠٢٦", en) shouldBe "سنة 2026"
        // Extended Arabic-Indic (Persian/Urdu) ۴۵۶.
        DrsTextTools.apply(DrsTextTool.TO_WESTERN_DIGITS, "\u06F4\u06F5\u06F6", en) shouldBe "456"
        // Letters and punctuation are untouched.
        DrsTextTools.apply(DrsTextTool.TO_WESTERN_DIGITS, "ابجد، x", en) shouldBe "ابجد، x"
    }

    test("digit conversions are inverse operations") {
        val original = "السطر 1 والأرقام 234 و5678"
        val roundTrip = DrsTextTools.apply(
            DrsTextTool.TO_WESTERN_DIGITS,
            DrsTextTools.apply(DrsTextTool.TO_ARABIC_DIGITS, original, en),
            en,
        )
        roundTrip shouldBe original
    }

    test("remove tatweel strips kashida only") {
        DrsTextTools.apply(DrsTextTool.REMOVE_TATWEEL, "مرحـــبــا", en) shouldBe "مرحبا"
        // Other characters survive untouched.
        DrsTextTools.apply(DrsTextTool.REMOVE_TATWEEL, "aـbـ1", en) shouldBe "ab1"
        // No tatweel -> unchanged.
        DrsTextTools.apply(DrsTextTool.REMOVE_TATWEEL, "بلا تطويل", en) shouldBe "بلا تطويل"
    }

    test("collapse empty lines merges consecutive blank runs into one") {
        DrsTextTools.apply(DrsTextTool.COLLAPSE_EMPTY_LINES, "a\n\n\n\nb\n\nc", en) shouldBe "a\n\nb\n\nc"
        // A single blank separator is preserved as-is.
        DrsTextTools.apply(DrsTextTool.COLLAPSE_EMPTY_LINES, "a\n\nb", en) shouldBe "a\n\nb"
        // Leading blank runs collapse to one leading blank line.
        DrsTextTools.apply(DrsTextTool.COLLAPSE_EMPTY_LINES, "\n\n\na", en) shouldBe "\na"
        // The trailing newline survives.
        DrsTextTools.apply(DrsTextTool.COLLAPSE_EMPTY_LINES, "a\n\n", en) shouldBe "a\n\n"
        // No blank runs -> unchanged.
        DrsTextTools.apply(DrsTextTool.COLLAPSE_EMPTY_LINES, "a\nb", en) shouldBe "a\nb"
    }

    /**
     * DRS p9 (Q3): every tool must honor at least one REAL contract element
     * on hostile input — the old `(result.length >= 0) shouldBe true` could
     * never fail. Grouped by tool family; an uncovered tool fails loudly so
     * future tools must pin an invariant too.
     */
    fun assertMeaningfulResult(tool: DrsTextTool, input: String, result: String) {
        val punctBeforeSpace = Regex("[ \\t\\u00A0]+([,;:.!?،؛…؟)\\]\\}»])")
        when (tool) {
            // 1:1 case mappings, per-char maps and line re-orderings keep the exact length
            DrsTextTool.UPPERCASE, DrsTextTool.LOWERCASE, DrsTextTool.TITLE_CASE,
            DrsTextTool.SENTENCE_CASE, DrsTextTool.TOGGLE_CASE,
            DrsTextTool.SORT_LINES, DrsTextTool.SORT_LINES_DESC, DrsTextTool.SORT_LINES_BY_LENGTH,
            DrsTextTool.REVERSE_LINES, DrsTextTool.NORMALIZE_ARABIC,
            DrsTextTool.TO_ARABIC_DIGITS, DrsTextTool.TO_WESTERN_DIGITS,
            DrsTextTool.TO_ARABIC_PUNCTUATION, DrsTextTool.TO_WESTERN_PUNCTUATION,
            -> (result.length == input.length) shouldBe true

            // removal/cleanup families may only shrink and never blank real content
            DrsTextTool.TRIM_SPACES, DrsTextTool.TRIM_LINE_EDGES, DrsTextTool.REMOVE_EMPTY_LINES,
            DrsTextTool.CLEAN_TEXT, DrsTextTool.TRIM_BLANK_EDGES, DrsTextTool.REMOVE_DIACRITICS,
            DrsTextTool.REMOVE_TATWEEL, DrsTextTool.REMOVE_DUPLICATE_WORDS,
            DrsTextTool.REMOVE_ZERO_WIDTH, DrsTextTool.STRIP_EMOJI, DrsTextTool.REMOVE_PUNCTUATION,
            -> {
                (result.length <= input.length) shouldBe true
                result.isNotBlank().shouldBeTrue()
            }

            DrsTextTool.REMOVE_LINE_BREAKS -> {
                (result.length <= input.length) shouldBe true
                result.contains('\n').shouldBeFalse()
            }

            // word order flips, the words themselves survive
            DrsTextTool.REVERSE_WORDS ->
                result.split(Regex("\\s+")).filter { it.isNotEmpty() }.sorted() shouldBe
                    input.split(Regex("\\s+")).filter { it.isNotEmpty() }.sorted()

            DrsTextTool.NUMBER_LINES -> {
                result.lines().size shouldBe input.lines().size
                result.lines().forEachIndexed { index, line ->
                    line.startsWith("${index + 1}. ").shouldBeTrue()
                }
            }

            DrsTextTool.REMOVE_DUPLICATE_LINES -> {
                (result.lines().size <= input.lines().size) shouldBe true
                result.lines().first() shouldBe input.lines().first() // first occurrence wins
            }

            DrsTextTool.WRAP_QUOTES -> {
                result.length shouldBe input.length + 2
                result.startsWith("\"").shouldBeTrue()
                result.endsWith("\"").shouldBeTrue()
            }

            DrsTextTool.WRAP_PARENS -> {
                result.length shouldBe input.length + 2
                result.startsWith("(").shouldBeTrue()
                result.endsWith(")").shouldBeTrue()
            }

            DrsTextTool.COLLAPSE_EMPTY_LINES ->
                (result.endsWith("\n") == input.endsWith("\n")).shouldBeTrue()

            DrsTextTool.TABS_TO_SPACES -> {
                result.contains('\t').shouldBeFalse()
                (result.length >= input.length) shouldBe true
            }

            DrsTextTool.SPACES_TO_TABS -> result.contains("    ").shouldBeFalse()

            DrsTextTool.NORMALIZE_PUNCTUATION -> punctBeforeSpace.containsMatchIn(result).shouldBeFalse()

            // idempotent normalizers: applying twice changes nothing further
            DrsTextTool.SENTENCE_PER_LINE, DrsTextTool.NORMALIZE_ARABIC_FORMS,
            DrsTextTool.SEPARATE_DIGIT_LETTERS,
            -> DrsTextTools.apply(tool, result, en) shouldBe result

            DrsTextTool.SPLIT_TO_LINES ->
                result.none { it == ',' || it == '،' || it == ';' || it == '؛' }.shouldBeTrue()

            DrsTextTool.JOIN_LINES -> result.contains('\n').shouldBeFalse()

            DrsTextTool.REMOVE_ALL_SPACES ->
                result.none { it == ' ' || it == '\t' || it == '\u00A0' }.shouldBeTrue()

            DrsTextTool.URL_ENCODE -> result.contains(' ').shouldBeFalse()

            // malformed "%s" escapes are preserved verbatim (documented catch path)
            DrsTextTool.URL_DECODE -> result shouldBe input

            DrsTextTool.COUNT -> result shouldBe input // the info tool never transforms

            // DRS v1.4.0 (public): the hostile input carries NO Arabic
            // LETTERS — only bare combining marks (ٍَّ), which strip to
            // nothing and are honestly kept as-is — so nothing can be
            // vocalized: the byte-identical passthrough IS the contract.
            DrsTextTool.TASHKEEL_TEXT -> result shouldBe input

            // DRS v1.6.0: the hostile input carries NO digits — the
            // number-to-words tool is a byte-identical passthrough on
            // anything it cannot parse as a clean integer.
            DrsTextTool.NUMBER_WORDS -> result shouldBe input

            else -> error("DRS p9 (Q3): tool ${tool.name} has no pinned hostile-input invariant")
        }
    }

    test("tools never throw on hostile input and every result honors its contract") {
        val hostile = "\uD83C\uDF0E \u0000 \t\n\r$ { } %s %d ٍَّ most"
        for (tool in DrsTextTool.entries) {
            if (tool.isEditorOp) continue
            val result = DrsTextTools.apply(tool, hostile, en)
            assertMeaningfulResult(tool, hostile, result)
        }
        // Empty input is a no-op for all transformation tools.
        for (tool in DrsTextTool.entries) {
            if (tool.isEditorOp || tool.isInfoOnly) continue
            DrsTextTools.apply(tool, "", en) shouldBe ""
        }
    }

    test("every tool code is inside the dispatch range") {
        for (tool in DrsTextTool.entries) {
            (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
            DrsTextTool.fromCode(tool.code) shouldBe tool
        }
    }

    // -----------------------------------------------------------
    // DRS v1.4.0: SORT_LINES_DESC
    // -----------------------------------------------------------

    test("sort lines descending orders lines with the locale collator") {
        DrsTextTools.apply(DrsTextTool.SORT_LINES_DESC, "banana\napple\ncherry", en) shouldBe
            "cherry\nbanana\napple"
        // The ascending counterpart is the exact inverse on distinct lines.
        DrsTextTools.apply(
            DrsTextTool.SORT_LINES,
            DrsTextTools.apply(DrsTextTool.SORT_LINES_DESC, "b\na\nc", en),
            en,
        ) shouldBe "a\nb\nc"
    }

    test("sort lines descending keeps blank lines and the trailing newline") {
        DrsTextTools.apply(DrsTextTool.SORT_LINES_DESC, "b\n\na\n", en) shouldBe "b\na\n\n"
    }

    // -----------------------------------------------------------
    // DRS v1.4.0: NORMALIZE_ARABIC
    // -----------------------------------------------------------

    test("normalize arabic unifies hamza carriers, ta-marbuta and alef maqsura") {
        // الأعلى: hamza-carrier alef AND final alef maqsura both unify.
        DrsTextTools.apply(DrsTextTool.NORMALIZE_ARABIC, "أحمد في الأعلى", en) shouldBe
            "احمد في الاعلي"
        // آية: hamza-carrier alef AND final ta-marbuta both unify.
        DrsTextTools.apply(DrsTextTool.NORMALIZE_ARABIC, "مدرسة وشاطىء وآية وإسلام", en) shouldBe
            "مدرسه وشاطيء وايه واسلام"
    }

    test("normalize arabic leaves other characters untouched") {
        // Latin, digits, punctuation, diacritics and tatweel all pass
        // through unchanged — the tool unifies LETTERS only.
        DrsTextTools.apply(DrsTextTool.NORMALIZE_ARABIC, "Hello 123! مُحَمَّـد", en) shouldBe
            "Hello 123! مُحَمَّـد"
    }
})
