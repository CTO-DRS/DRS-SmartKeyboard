/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.ImeUiMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * DRS v1.15.0: «المهام المثبتة 10 فقط» (the pinned-tasks cap with honest
 * refusal) and the three smart panels' pure engines — the smart harakat
 * stacking, the context-aware symbol suggestions and the shared most-used
 * recents tracker. Every contract here is pure and side-effect free.
 */
class DrsV1150Tests : FunSpec({

    // -------------------------------------------------------------
    // The pinned cap: «المهام المثبتة 10 فقط»
    // -------------------------------------------------------------

    test("capPinned keeps the user's pins first, in pin order") {
        val defaults = listOf("emoji", "clipboard")
        val capped = DrsUnifiedTools.capPinned(
            userPinned = listOf("undo", "redo", "settings"),
            defaultPinnedIds = defaults,
        )
        capped.take(3) shouldContainExactly listOf("undo", "redo", "settings")
        capped shouldContainExactly listOf("undo", "redo", "settings", "emoji", "clipboard")
    }

    test("capPinned never exceeds ten even with huge input") {
        val userPins = (1..30).map { "tool$it" }
        val capped = DrsUnifiedTools.capPinned(userPins, emptyList())
        capped shouldHaveSize DrsUnifiedTools.MAX_PINNED_TOOLS
        capped.first() shouldBe "tool1"
        capped.last() shouldBe "tool10"
    }

    test("capPinned drops duplicate ids deterministically") {
        val capped = DrsUnifiedTools.capPinned(
            userPinned = listOf("a", "b", "a", "c", "b"),
            defaultPinnedIds = emptyList(),
        )
        capped shouldContainExactly listOf("a", "b", "c")
    }

    test("capPinned fills leftover slots with the defaults") {
        val capped = DrsUnifiedTools.capPinned(
            userPinned = listOf("u1", "u2"),
            defaultPinnedIds = listOf("d1", "d2", "d3"),
        )
        capped shouldContainExactly listOf("u1", "u2", "d1", "d2", "d3")
    }

    test("canPinMore is true under the cap and false at it") {
        val userPins = (1..9).map { "tool$it" }
        DrsUnifiedTools.canPinMore(userPins) shouldBe true
        DrsUnifiedTools.canPinMore(userPins + "tool10") shouldBe false
        DrsUnifiedTools.canPinMore(userPins + "tool10" + "tool11") shouldBe false
    }

    test("resolveFor caps the rendered pinned head") {
        // The catalogue defaults pin emoji/clipboard/text_tools — three
        // defaults. Pinning eleven more tools must still render a head of
        // exactly ten pinned tools, user pins first — and the 11th user
        // pin (number_row) must fall out of the head.
        val userPins = listOf("undo", "redo", "settings", "paste", "share", "language",
            "numbers", "symbols", "hide_keyboard", "one_handed", "number_row")
        val resolved = DrsUnifiedTools.resolveFor(
            view = DrsHybridViewMode.DUAL,
            hidden = emptyList(),
            pinned = userPins,
            order = emptyList(),
            viewOverrides = emptyMap(),
        )
        val pinnedHead = resolved.take(DrsUnifiedTools.MAX_PINNED_TOOLS)
        pinnedHead shouldHaveSize 10
        pinnedHead.none { it.id == "number_row" } shouldBe true
        // The head keeps catalogue order among the pinned set — the first
        // pinned tool in catalogue order among the kept pins is numbers.
        pinnedHead.map { it.id } shouldContainExactly listOf(
            "numbers", "symbols", "language", "undo", "redo", "paste",
            "share", "hide_keyboard", "settings", "one_handed",
        )
    }

    test("the three new panel tools are real catalogue entries") {
        val diacritics = DrsUnifiedTools.byId("diacritics_panel")
        val symbols = DrsUnifiedTools.byId("smart_symbols")
        val letters = DrsUnifiedTools.byId("arabic_letters")
        diacritics shouldNotBe null
        symbols shouldNotBe null
        letters shouldNotBe null
        diacritics!!.code shouldBe com.drs.smartkeyboard.ime.text.key.KeyCode.IME_UI_MODE_DIACRITICS
        symbols!!.code shouldBe com.drs.smartkeyboard.ime.text.key.KeyCode.IME_UI_MODE_SMART_SYMBOLS
        letters!!.code shouldBe com.drs.smartkeyboard.ime.text.key.KeyCode.IME_UI_MODE_ARABIC_LETTERS
    }

    // -------------------------------------------------------------
    // The smart harakat stacking engine
    // -------------------------------------------------------------

    test("a haraka after a plain letter appends") {
        val prev = 'ش'
        HarakatSmartInsert.decide(prev, DrsHarakat.FATHA, smartReplace = true) shouldBe
            HarakaInsertMode.APPEND
    }

    test("a haraka after nothing appends") {
        HarakatSmartInsert.decide(null, DrsHarakat.SHADDA, smartReplace = true) shouldBe
            HarakaInsertMode.APPEND
    }

    test("the same mark re-tap is idempotent — replace, never double") {
        HarakatSmartInsert.decide(DrsHarakat.FATHA, DrsHarakat.FATHA, true) shouldBe
            HarakaInsertMode.REPLACE_PREVIOUS
        HarakatSmartInsert.decide(DrsHarakat.SHADDA, DrsHarakat.SHADDA, true) shouldBe
            HarakaInsertMode.REPLACE_PREVIOUS
    }

    test("two different vowel marks replace each other") {
        HarakatSmartInsert.decide(DrsHarakat.FATHA, DrsHarakat.DAMMA, true) shouldBe
            HarakaInsertMode.REPLACE_PREVIOUS
        HarakatSmartInsert.decide(DrsHarakat.KASRA, DrsHarakat.FATHATAN, true) shouldBe
            HarakaInsertMode.REPLACE_PREVIOUS
    }

    test("shadda followed by a vowel appends — the legit combo") {
        HarakatSmartInsert.decide(DrsHarakat.SHADDA, DrsHarakat.FATHA, true) shouldBe
            HarakaInsertMode.APPEND
        HarakatSmartInsert.decide(DrsHarakat.SHADDA, DrsHarakat.KASRA, true) shouldBe
            HarakaInsertMode.APPEND
    }

    test("a vowel followed by shadda replaces — one vowel slot") {
        HarakatSmartInsert.decide(DrsHarakat.FATHA, DrsHarakat.SHADDA, true) shouldBe
            HarakaInsertMode.REPLACE_PREVIOUS
    }

    test("tatweel is not a mark — a haraka after it appends") {
        HarakatSmartInsert.decide(DrsHarakat.TATWEEL, DrsHarakat.FATHA, true) shouldBe
            HarakaInsertMode.APPEND
    }

    test("smart replace off always appends — classic behavior") {
        HarakatSmartInsert.decide(DrsHarakat.FATHA, DrsHarakat.DAMMA, false) shouldBe
            HarakaInsertMode.APPEND
        HarakatSmartInsert.decide(DrsHarakat.FATHA, DrsHarakat.FATHA, false) shouldBe
            HarakaInsertMode.APPEND
    }

    test("the harakat classification is honest") {
        DrsHarakat.MARKS.forEach { DrsHarakat.isCombiningMark(it) shouldBe true }
        DrsHarakat.isCombiningMark(DrsHarakat.TATWEEL) shouldBe false
        DrsHarakat.isCombiningMark('ش') shouldBe false
        DrsHarakat.isHarakatTile(DrsHarakat.TATWEEL) shouldBe true
        DrsHarakat.GRID shouldHaveSize 10
    }

    test("the shadda combos commit exactly two characters") {
        DrsHarakat.COMBOS shouldHaveSize 4
        DrsHarakat.COMBOS.forEach { combo ->
            combo.length shouldBe 2
            combo.first() shouldBe DrsHarakat.SHADDA
            DrsHarakat.isCombiningMark(combo.last()) shouldBe true
        }
    }

    // -------------------------------------------------------------
    // The context-aware symbol suggestions
    // -------------------------------------------------------------

    test("digits lead to percent, degree and currency") {
        val sugg = SymbolSmartSuggestor.suggest("سعر 25")
        sugg.first() shouldBe "%"
        sugg shouldContainExactly listOf("%", "°", "÷", "×", "$", "﷼")
    }

    test("Arabic-Indic digits suggest like western digits") {
        SymbolSmartSuggestor.suggest("٢٥").first() shouldBe "%"
    }

    test("Arabic letters lead to tatweel and Arabic punctuation") {
        val sugg = SymbolSmartSuggestor.suggest("السلام عليكم")
        sugg shouldContainExactly listOf("ـ", "،", "؛", "؟", "«", "»")
    }

    test("an equals sign extends the math relation") {
        SymbolSmartSuggestor.suggest("x=").first() shouldBe "≠"
    }

    test("an open bracket leads with its closer") {
        SymbolSmartSuggestor.suggest("foo(").first() shouldBe ")"
        SymbolSmartSuggestor.suggest("foo[").first() shouldBe "]"
        SymbolSmartSuggestor.suggest("foo{").first() shouldBe "}"
    }

    test("a code-ish window suggests brackets") {
        val sugg = SymbolSmartSuggestor.suggest("if (a; b)")
        sugg shouldContainExactly listOf("{", "}", "(", ")", "<", "=")
    }

    test("an unknown context falls back to the default row") {
        SymbolSmartSuggestor.suggest("") shouldContainExactly SymbolSmartSuggestor.DEFAULT
        SymbolSmartSuggestor.suggest("   ").first() shouldBe "@"
    }

    test("suggestions never exceed the cap of six") {
        SymbolSmartSuggestor.suggest("hello").size shouldBe SymbolSmartSuggestor.MAX_SUGGESTIONS
    }

    test("the Arabic letter classification excludes marks, digits and tatweel") {
        SymbolSmartSuggestor.isArabicLetter('ش') shouldBe true
        SymbolSmartSuggestor.isArabicLetter('5') shouldBe false
        SymbolSmartSuggestor.isArabicLetter('٥') shouldBe false
        SymbolSmartSuggestor.isArabicLetter(DrsHarakat.FATHA) shouldBe false
        SymbolSmartSuggestor.isArabicLetter(DrsHarakat.TATWEEL) shouldBe false
        SymbolSmartSuggestor.isArabicLetter('a') shouldBe false
    }

    // -------------------------------------------------------------
    // The shared most-used recents engine
    // -------------------------------------------------------------

    test("record grows the count for the pressed tile") {
        val counts = PanelUsageTracker.record(emptyMap(), "َ")
        counts["َ"] shouldBe 1
        val twice = PanelUsageTracker.record(counts, "َ")
        twice["َ"] shouldBe 2
    }

    test("topRecents leads with the most-used and breaks ties by catalogue order") {
        var counts = mapOf("َ" to 1, "ُ" to 3, "ِ" to 2)
        counts = PanelUsageTracker.record(counts, "َ") // َ becomes 2 — ties with ِ
        val top = PanelUsageTracker.topRecents(counts, listOf("َ", "ُ", "ِ", "ْ"))
        top.first() shouldBe "ُ"
        // The َ / ِ tie (2 == 2) breaks towards the catalogue head.
        top[1] shouldBe "َ"
        top[2] shouldBe "ِ"
    }

    test("topRecents skips never-used and unknown tiles") {
        val top = PanelUsageTracker.topRecents(mapOf("ُ" to 3, "z" to 9), listOf("َ", "ُ", "ِ"))
        top shouldContainExactly listOf("ُ")
    }

    test("topRecents caps at the requested size and handles zero") {
        val counts = (1..10).associate { "t$it" to it }
        PanelUsageTracker.topRecents(counts, counts.keys.toList(), 3) shouldHaveSize 3
        PanelUsageTracker.topRecents(counts, counts.keys.toList(), 0) shouldHaveSize 0
    }

    test("record evicts the least-used when the map grows beyond its bound") {
        var counts = emptyMap<String, Int>()
        // Fill beyond the internal bound with distinct hot tiles.
        for (i in 1..70) {
            counts = PanelUsageTracker.record(counts, "tile$i")
        }
        // The map stays bounded; ties break towards the catalogue tail, so
        // the lexicographically largest keys (tile9, tile8, tile70, tile7,
        // tile69, tile68) go first, and the early tiles survive.
        counts.size shouldBe 64
        counts.containsKey("tile70") shouldBe false
        counts.containsKey("tile1") shouldBe true
    }

    // -------------------------------------------------------------
    // The ImeUiMode round-trips for the new panels
    // -------------------------------------------------------------

    test("the three new UI modes round-trip through their ints") {
        ImeUiMode.fromInt(4) shouldBe ImeUiMode.DIACRITICS
        ImeUiMode.fromInt(5) shouldBe ImeUiMode.SMART_SYMBOLS
        ImeUiMode.fromInt(6) shouldBe ImeUiMode.ARABIC_LETTERS
        // DRS v1.2.0: mode 7 joined as the smart numbers panel — the
        // defensive fallback moved to 8.
        ImeUiMode.fromInt(7) shouldBe ImeUiMode.SMART_NUMBER
        ImeUiMode.fromInt(8) shouldBe ImeUiMode.TEXT // defensive fallback
        ImeUiMode.fromInt(99) shouldBe ImeUiMode.TEXT
    }
})
