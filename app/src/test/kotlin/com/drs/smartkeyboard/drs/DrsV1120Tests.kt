/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.clipboard.ClipCodeDetector
import com.drs.smartkeyboard.ime.clipboard.ClipCodeLanguage
import com.drs.smartkeyboard.ime.clipboard.ClipEditorCharLimit
import com.drs.smartkeyboard.ime.clipboard.ClipHighlightRange
import com.drs.smartkeyboard.ime.clipboard.ClipHistorySection
import com.drs.smartkeyboard.ime.clipboard.ClipHistorySections
import com.drs.smartkeyboard.ime.clipboard.ClipHistorySort
import com.drs.smartkeyboard.ime.clipboard.ClipHistorySorter
import com.drs.smartkeyboard.ime.clipboard.ClipItemCategoryDetector
import com.drs.smartkeyboard.ime.clipboard.ClipItemCategory
import com.drs.smartkeyboard.ime.clipboard.ClipMatch
import com.drs.smartkeyboard.ime.clipboard.ClipPanelSearchResults
import com.drs.smartkeyboard.ime.clipboard.ClipResultPalette
import com.drs.smartkeyboard.ime.clipboard.ClipResultCard
import com.drs.smartkeyboard.ime.clipboard.ClipSearchEngine
import com.drs.smartkeyboard.ime.clipboard.ClipSearchResults
import com.drs.smartkeyboard.ime.clipboard.ClipTextTransforms
import com.drs.smartkeyboard.ime.clipboard.ClipboardTextPolicy
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardItem
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.ZoneId

/**
 * DRS v1.12.0: the complete smart clipboard system contracts — the
 * colored search-result cards with navigation (line numbers, same-line
 * context, palette cycling, caps), the in-text highlight ranges, the
 * programming-line detector (per-line verdicts, language guesses,
 * extract/remove surgery), the 500,000-character policy with the
 * user-chosen editor limits, the comprehensive organization core (the
 * persisted sort orders, the calendar sections, the smart category
 * badges, the panel result cards), and the line-numbering transform.
 * Every rule is pinned where the engine owns it — pure, no fakes.
 */
class DrsV1120Tests : FunSpec({

    // -------------------------------------------------------------
    // The colored search-result cards
    // -------------------------------------------------------------

    test("buildCards produces one card per match with correct line numbers") {
        val text = "first line\nsecond line\nthird line"
        val matches = ClipSearchEngine.findMatches(text, "line", ignoreCase = false)
        matches.size shouldBe 3
        val cards = ClipSearchResults.buildCards(text, matches)
        cards.size shouldBe 3
        cards[0].lineNumber shouldBe 1
        cards[1].lineNumber shouldBe 2
        cards[2].lineNumber shouldBe 3
        cards[0].matched shouldBe "line"
        cards[0].start shouldBe matches[0].start
        cards[0].end shouldBe matches[0].end
    }

    test("the card context never crosses a line boundary") {
        val text = "ab\nneedle here\nxy"
        val match = ClipSearchEngine.findMatches(text, "needle", ignoreCase = false).single()
        val card = ClipSearchResults.buildCards(text, listOf(match), contextChars = 24).single()
        card.lineNumber shouldBe 2
        card.before shouldBe ""
        card.after shouldBe " here"
        card.truncatedBefore shouldBe false
        card.truncatedAfter shouldBe false
    }

    test("the card context truncates honestly with ellipsis flags") {
        // "needle" starts at offset 30; a 10-char context window starts at 20.
        val text = "aaaa bbbb cccc dddd eeee ffff needle gggg hhhh iiii jjjj kkkk llll mmmm"
        val match = ClipSearchEngine.findMatches(text, "needle", ignoreCase = false).single()
        match.start shouldBe 30
        val card = ClipSearchResults.buildCards(text, listOf(match), contextChars = 10).single()
        card.before shouldBe "eeee ffff "
        card.truncatedBefore shouldBe true
        card.after shouldBe " gggg hhhh"
        card.truncatedAfter shouldBe true
    }

    test("the palette cycles deterministically and is negative-safe") {
        ClipResultPalette.slotFor(0) shouldBe 0
        ClipResultPalette.slotFor(6) shouldBe 0
        ClipResultPalette.slotFor(7) shouldBe 1
        ClipResultPalette.slotFor(-1) shouldBe ClipResultPalette.SIZE - 1
        ClipResultPalette.COLORS.size shouldBe ClipResultPalette.SIZE
    }

    test("the card color slot follows the match index") {
        val text = "a b a b a b"
        val matches = ClipSearchEngine.findMatches(text, "a", ignoreCase = false)
        val cards = ClipSearchResults.buildCards(text, matches)
        cards.forEachIndexed { index, card ->
            card.matchIndex shouldBe index
            card.colorSlot shouldBe ClipResultPalette.slotFor(index)
        }
    }

    test("buildCards is capped at MAX_CARDS and empty input yields no cards") {
        val text = "x".repeat(ClipSearchResults.MAX_CARDS + 10)
        val matches = ClipSearchEngine.findMatches(text, "x", ignoreCase = false)
        val cards = ClipSearchResults.buildCards(text, matches)
        cards.size shouldBe ClipSearchResults.MAX_CARDS
        ClipSearchResults.buildCards("anything", emptyList()).shouldBeEmpty()
    }

    test("empty text and empty matches yield no cards") {
        ClipSearchResults.buildCards("", emptyList()).shouldBeEmpty()
    }

    // -------------------------------------------------------------
    // The in-text highlight ranges
    // -------------------------------------------------------------

    test("highlightRanges marks the active match and carries color slots") {
        val matches = listOf(ClipMatch(0, 2), ClipMatch(4, 6), ClipMatch(8, 10))
        val ranges = ClipSearchResults.highlightRanges(matches, activeIndex = 1)
        ranges.size shouldBe 3
        ranges[0].isActive shouldBe false
        ranges[1].isActive shouldBe true
        ranges[2].isActive shouldBe false
        ranges[2].colorSlot shouldBe ClipResultPalette.slotFor(2)
    }

    test("highlightRanges of an empty match list is empty") {
        ClipSearchResults.highlightRanges(emptyList(), 0).shouldBeEmpty()
    }

    test("a highlight range rejects an invalid span") {
        val thrown = runCatching { ClipHighlightRange(5, 3, true, 0) }
        thrown.isFailure shouldBe true
    }

    // -------------------------------------------------------------
    // The programming-line detector
    // -------------------------------------------------------------

    test("a kotlin snippet is detected as code with the kotlin/java language") {
        val code = """
            fun main() {
                val name = "DRS"
                println("hello " + name)
            }
        """.trimIndent()
        val analysis = ClipCodeDetector.analyze(code)
        analysis.isCode shouldBe true
        analysis.language shouldBe ClipCodeLanguage.KOTLIN_JAVA
        // The bare closing brace carries no signals — the three statement
        // lines carry the verdict.
        analysis.codeLines shouldBe 3
        analysis.totalLines shouldBe 4
    }

    test("a python snippet is detected as code with the python language") {
        val code = """
            def greet(name):
                print("hello " + name)
                self.value = 1
        """.trimIndent()
        val analysis = ClipCodeDetector.analyze(code)
        analysis.isCode shouldBe true
        analysis.language shouldBe ClipCodeLanguage.PYTHON
    }

    test("a javascript snippet is detected with the javascript language") {
        val code = """
            const add = (a, b) => {
                console.log(a + b);
                return a + b;
            };
        """.trimIndent()
        val analysis = ClipCodeDetector.analyze(code)
        analysis.isCode shouldBe true
        analysis.language shouldBe ClipCodeLanguage.JAVASCRIPT
    }

    test("a json document is detected as code with the json language") {
        val json = """
            {
                "name": "DRS",
                "version": 12,
                "smart": true
            }
        """.trimIndent()
        val analysis = ClipCodeDetector.analyze(json)
        analysis.isCode shouldBe true
        analysis.language shouldBe ClipCodeLanguage.JSON
    }

    test("an html fragment is detected as code with the html/xml language") {
        val html = """
            <html>
                <div class="box">
                    <p>hello</p>
                </div>
            </html>
        """.trimIndent()
        val analysis = ClipCodeDetector.analyze(html)
        analysis.isCode shouldBe true
        analysis.language shouldBe ClipCodeLanguage.HTML_XML
    }

    test("arabic prose is never detected as code") {
        val prose = """
            العقل السليم في الجسم السليم
            الصديق وقت الضيق
            من جد وجد ومن زرع حصد
            هذا نص عربي عادي ليس برمجيًا
        """.trimIndent()
        val analysis = ClipCodeDetector.analyze(prose)
        analysis.isCode shouldBe false
        analysis.language shouldBe ClipCodeLanguage.UNKNOWN
    }

    test("fewer code lines than the minimum never qualifies") {
        val short = "val x = 1\nprintln(x)"
        ClipCodeDetector.analyze(short).isCode shouldBe false
        ClipCodeDetector.isCodeLine("val x = 1") shouldBe true
    }

    test("blank text yields a non-code analysis with zero totals") {
        val analysis = ClipCodeDetector.analyze("   \n  ")
        analysis.isCode shouldBe false
        analysis.totalLines shouldBe 0
        analysis.codeLines shouldBe 0
        analysis.language shouldBe ClipCodeLanguage.UNKNOWN
    }

    test("confidence stays bounded within 0.0 to 1.0") {
        val code = "fun a() {\nval b = 2;\n}\nprintf(\"x\");\nint main;"
        val analysis = ClipCodeDetector.analyze(code)
        analysis.isCode shouldBe true
        (analysis.confidence >= 0.0 && analysis.confidence <= 1.0) shouldBe true
    }

    test("extractCodeLines keeps only code lines in order") {
        val text = "مقدمة عربية\nval x = 1;\nسطر آخر\nprintln(x)\n"
        val extracted = ClipCodeDetector.extractCodeLines(text)
        extracted.split('\n').filter { it.isNotBlank() } shouldBe listOf("val x = 1;", "println(x)")
    }

    test("removeCodeLines drops the code lines and keeps the rest") {
        val text = "مقدمة عربية\nval x = 1;\nسطر آخر"
        val remaining = ClipCodeDetector.removeCodeLines(text)
        remaining.split('\n').filter { it.isNotBlank() } shouldBe listOf("مقدمة عربية", "سطر آخر")
    }

    test("extract and remove on code-free text are honest") {
        val prose = "السلام عليكم\nهذا نص عادي"
        ClipCodeDetector.extractCodeLines(prose) shouldBe ""
        ClipCodeDetector.removeCodeLines(prose).lines().filter { it.isNotBlank() } shouldHaveSize 2
    }

    // -------------------------------------------------------------
    // The 500,000-character policy and the editor limits
    // -------------------------------------------------------------

    // ClipboardTextPolicy.MAX_TEXT_CHARS == 500_000 is pinned once, in
    // DrsV190Tests — this suite keeps only its unique warning-threshold pin.
    test("the policy slowdown-warning threshold stays at 100000 characters") {
        ClipboardTextPolicy.LARGE_TEXT_WARNING_CHARS shouldBe 100_000
    }

    test("the effective limit clamps into the hard cap and rescues broken values") {
        ClipboardTextPolicy.effectiveLimit(500_000) shouldBe 500_000
        ClipboardTextPolicy.effectiveLimit(50_000) shouldBe 50_000
        ClipboardTextPolicy.effectiveLimit(2_000_000) shouldBe 500_000
        ClipboardTextPolicy.effectiveLimit(0) shouldBe 500_000
        ClipboardTextPolicy.effectiveLimit(-5) shouldBe 500_000
    }

    test("the two-arg truncation honors the requested limit with emoji safety") {
        val text = "أ".repeat(120) + "😀"
        ClipboardTextPolicy.truncateForStorage(text, 120).length shouldBe 120
        ClipboardTextPolicy.truncateForStorage(text, 121).length shouldBe 120
        ClipboardTextPolicy.truncateForStorage(text, 500_000) shouldBe text
    }

    test("every editor limit choice maps to its own effective cap") {
        ClipEditorCharLimit.FIFTY_K.chars shouldBe 50_000
        ClipEditorCharLimit.HUNDRED_K.chars shouldBe 100_000
        ClipEditorCharLimit.TWO_FIFTY_K.chars shouldBe 250_000
        ClipEditorCharLimit.FIVE_HUNDRED_K.chars shouldBe 500_000
        ClipEditorCharLimit.FIFTY_K.effectiveLimit() shouldBe 50_000
        ClipEditorCharLimit.FIVE_HUNDRED_K.effectiveLimit() shouldBe 500_000
    }

    // -------------------------------------------------------------
    // The line-numbering transform
    // -------------------------------------------------------------

    test("numberLines prefixes 1-based numbers per line") {
        ClipTextTransforms.numberLines("a\nb\nc") shouldBe "1. a\n2. b\n3. c"
    }

    test("numberLines is honest on empty text and keeps blank lines") {
        ClipTextTransforms.numberLines("") shouldBe ""
        ClipTextTransforms.numberLines("x\n\ny") shouldBe "1. x\n2. \n3. y"
    }

    // -------------------------------------------------------------
    // The comprehensive organization core
    // -------------------------------------------------------------

    test("the sorter orders by newest, oldest, longest, and shortest") {
        val items = listOf(
            ClipboardItem.text("قصير").copy(creationTimestampMs = 100L),
            ClipboardItem.text("نص أطول قليلًا").copy(creationTimestampMs = 300L),
            ClipboardItem.text("النص الأطول في هذا السجل كله").copy(creationTimestampMs = 200L),
        )
        ClipHistorySorter.sort(items, ClipHistorySort.NEWEST).map { it.creationTimestampMs } shouldBe
            listOf(300L, 200L, 100L)
        ClipHistorySorter.sort(items, ClipHistorySort.OLDEST).map { it.creationTimestampMs } shouldBe
            listOf(100L, 200L, 300L)
        ClipHistorySorter.sort(items, ClipHistorySort.LONGEST).first().creationTimestampMs shouldBe 200L
        ClipHistorySorter.sort(items, ClipHistorySort.SHORTEST).first().creationTimestampMs shouldBe 100L
    }

    test("the sorter does not mutate the input list") {
        val items = listOf(
            ClipboardItem.text("b").copy(creationTimestampMs = 1L),
            ClipboardItem.text("a").copy(creationTimestampMs = 2L),
        )
        ClipHistorySorter.sort(items, ClipHistorySort.OLDEST)
        items[0].text shouldBe "b"
    }

    test("the calendar sections group pinned first, then today, yesterday, week, month, older") {
        val zone = ZoneId.of("UTC")
        val now = java.time.LocalDateTime.of(2026, 9, 25, 12, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val day = 24 * 60 * 60 * 1000L
        val items = listOf(
            ClipboardItem.text("pinned item").copy(isPinned = true, creationTimestampMs = now - 40 * day),
            ClipboardItem.text("today item").copy(creationTimestampMs = now - 60 * 60 * 1000L),
            ClipboardItem.text("yesterday item").copy(creationTimestampMs = now - 1 * day),
            ClipboardItem.text("this week item").copy(creationTimestampMs = now - 4 * day),
            ClipboardItem.text("this month item").copy(creationTimestampMs = now - 15 * day),
            ClipboardItem.text("older item").copy(creationTimestampMs = now - 60 * day),
        )
        val groups = ClipHistorySections.group(items, now, zone)
        groups.map { it.section } shouldBe listOf(
            ClipHistorySection.PINNED,
            ClipHistorySection.TODAY,
            ClipHistorySection.YESTERDAY,
            ClipHistorySection.THIS_WEEK,
            ClipHistorySection.THIS_MONTH,
            ClipHistorySection.OLDER,
        )
        groups.first { it.section == ClipHistorySection.PINNED }.items.single().text shouldBe "pinned item"
        groups.first { it.section == ClipHistorySection.TODAY }.items.single().text shouldBe "today item"
    }

    test("empty sections are omitted and an empty history yields no groups") {
        val zone = ZoneId.of("UTC")
        val now = java.time.LocalDateTime.of(2026, 9, 25, 12, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val items = listOf(
            ClipboardItem.text("today only").copy(creationTimestampMs = now - 1000L),
        )
        val groups = ClipHistorySections.group(items, now, zone)
        groups.map { it.section } shouldBe listOf(ClipHistorySection.TODAY)
        ClipHistorySections.group(emptyList(), now, zone).shouldBeEmpty()
    }

    test("items exactly on the seven-day and thirty-day edges fall forward correctly") {
        val zone = ZoneId.of("UTC")
        val now = java.time.LocalDateTime.of(2026, 9, 25, 12, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val day = 24 * 60 * 60 * 1000L
        // Six days ago is still THIS_WEEK (today - 6 .. today - 3).
        val sixDays = listOf(ClipboardItem.text("six days").copy(creationTimestampMs = now - 6 * day))
        ClipHistorySections.group(sixDays, now, zone).single().section shouldBe ClipHistorySection.THIS_WEEK
        // Eight days ago is THIS_MONTH (last thirty days except the week).
        val eightDays = listOf(ClipboardItem.text("eight days").copy(creationTimestampMs = now - 8 * day))
        ClipHistorySections.group(eightDays, now, zone).single().section shouldBe ClipHistorySection.THIS_MONTH
        // Thirty-one days ago is OLDER.
        val thirtyOneDays = listOf(ClipboardItem.text("thirty one").copy(creationTimestampMs = now - 31 * day))
        ClipHistorySections.group(thirtyOneDays, now, zone).single().section shouldBe ClipHistorySection.OLDER
    }

    // -------------------------------------------------------------
    // The smart category badges
    // -------------------------------------------------------------

    test("a bare link is detected as the URL category") {
        ClipItemCategoryDetector.detect("https://drs.example.com/path?q=1") shouldBe ClipItemCategory.URL
        ClipItemCategoryDetector.detect("www.drs.example.com") shouldBe ClipItemCategory.URL
    }

    test("a bare email is detected as the EMAIL category") {
        ClipItemCategoryDetector.detect("user@drs.example.com") shouldBe ClipItemCategory.EMAIL
    }

    test("a phone-like number is detected as the PHONE category") {
        ClipItemCategoryDetector.detect("+966 50 123 4567") shouldBe ClipItemCategory.PHONE
        ClipItemCategoryDetector.detect("0501234567") shouldBe ClipItemCategory.PHONE
    }

    test("a code snippet is detected as the CODE category") {
        ClipItemCategoryDetector.detect(
            "fun main() {\nval x = 1;\nprintln(x)\n}",
        ) shouldBe ClipItemCategory.CODE
    }

    test("plain arabic text, null, and blank stay TEXT") {
        ClipItemCategoryDetector.detect("السلام عليكم ورحمة الله") shouldBe ClipItemCategory.TEXT
        ClipItemCategoryDetector.detect(null) shouldBe ClipItemCategory.TEXT
        ClipItemCategoryDetector.detect("   ") shouldBe ClipItemCategory.TEXT
        ClipItemCategoryDetector.detect("50") shouldBe ClipItemCategory.TEXT
    }

    // -------------------------------------------------------------
    // The panel search-result cards
    // -------------------------------------------------------------

    test("buildCards previews the first hit with the matched span identified") {
        val items = listOf(
            ClipboardItem.text("مرحبا بالعالم الجميل، مرحبا بالخير"),
            ClipboardItem.text("no match here"),
        )
        val cards = ClipPanelSearchResults.buildCards(items, "مرحبا")
        cards.size shouldBe 1
        cards[0].matched shouldBe "مرحبا"
        cards[0].before shouldBe ""
        // The window is wide enough for the whole short preview.
        cards[0].after shouldBe " بالعالم الجميل، مرحبا بالخير"
        cards[0].truncatedBefore shouldBe false
        cards[0].truncatedAfter shouldBe false
        cards[0].colorSlot shouldBe 0
    }

    test("panel result cards cap at MAX_CARDS and skip empty queries") {
        val items = (1..(ClipPanelSearchResults.MAX_CARDS + 10)).map {
            ClipboardItem.text("hit $it target")
        }
        val cards = ClipPanelSearchResults.buildCards(items, "target")
        cards.size shouldBe ClipPanelSearchResults.MAX_CARDS
        ClipPanelSearchResults.buildCards(items, "").shouldBeEmpty()
    }

    test("panel result cards cycle the palette and keep the item reference") {
        val items = (0 until 7).map { ClipboardItem.text("item $it needle") }
        val cards = ClipPanelSearchResults.buildCards(items, "needle")
        cards.size shouldBe 7
        // The palette cycles every SIZE cards — index 6 wraps back to 0.
        cards[6].colorSlot shouldBe 0
        cards[1].colorSlot shouldBe 1
        cards[0].item.text shouldBe "item 0 needle"
    }

    test("media items are skipped by the panel result cards") {
        val media = ClipboardItem.text("").copy(type = ItemType.IMAGE, text = null)
        val cards = ClipPanelSearchResults.buildCards(listOf(media), "x")
        cards.shouldBeEmpty()
    }
})
