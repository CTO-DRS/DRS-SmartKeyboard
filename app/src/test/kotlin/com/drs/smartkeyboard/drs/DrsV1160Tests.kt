/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.ImeUiMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * DRS v1.16.0: «شريط المهام ثابت يعرض 10 مهام فقط مع إمكانية تغييرها
 * وتعديلها» + «لوحة حركات كلوحة مفاتيح كاملة مشابهة تمامًا للوحة الحروف
 * أو الأرقام» + «أعد ترتيب وتطوير جميع الوحات بنظام مرتب وذكي». Every
 * contract here is pure and pinned on the JVM: the fixed ten slots, the
 * slot-replacement materialization, the smart panel ordering and the
 * harakat KEYBOARD arrangement.
 */
class DrsV1160Tests : FunSpec({

    // -------------------------------------------------------------
    // The fixed tasks bar: exactly ten slots
    // -------------------------------------------------------------

    test("fixedSlots keeps user pins first in pin order") {
        val slots = DrsUnifiedTools.fixedSlots(
            userPinned = listOf("undo", "redo"),
            defaultPinnedIds = listOf("emoji"),
            visibleCatalogueOrder = listOf("paste", "copy"),
        )
        slots.take(2) shouldContainExactly listOf("undo", "redo")
        slots shouldContainExactly listOf("undo", "redo", "emoji", "paste", "copy")
    }

    test("fixedSlots always resolves exactly ten when the catalogue allows") {
        val catalogue = (1..40).map { "tool$it" }
        val slots = DrsUnifiedTools.fixedSlots(
            userPinned = listOf("a", "b"),
            defaultPinnedIds = listOf("c", "d", "e"),
            visibleCatalogueOrder = catalogue,
        )
        slots shouldHaveSize DrsUnifiedTools.MAX_PINNED_TOOLS
        slots.first() shouldBe "a"
        // 2 pins + 3 defaults = 5 explicit, then the catalogue pads five more.
        slots.last() shouldBe "tool5"
    }

    test("fixedSlots never duplicates a tool across the segments") {
        val slots = DrsUnifiedTools.fixedSlots(
            userPinned = listOf("undo", "undo", "copy"),
            defaultPinnedIds = listOf("emoji", "copy"),
            visibleCatalogueOrder = listOf("paste", "emoji", "copy", "cut"),
        )
        slots.distinct() shouldContainExactly slots
        slots shouldContainExactly listOf("undo", "copy", "emoji", "paste", "cut")
    }

    test("fixedSlots honors the cap with huge user input") {
        val pins = (1..30).map { "p$it" }
        val slots = DrsUnifiedTools.fixedSlots(pins, listOf("d1"), emptyList())
        slots shouldHaveSize DrsUnifiedTools.MAX_PINNED_TOOLS
        slots.first() shouldBe "p1"
        slots.last() shouldBe "p10"
    }

    test("fixedSlots returns what exists when the whole catalogue is smaller than ten") {
        val slots = DrsUnifiedTools.fixedSlots(
            listOf("x"), listOf("y"), listOf("z"),
        )
        slots shouldContainExactly listOf("x", "y", "z")
    }

    // -------------------------------------------------------------
    // The slot editor: «إمكانية تغيير المهام»
    // -------------------------------------------------------------

    test("materializeSlotReplace swaps exactly the targeted slot") {
        val current = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")
        val replaced = DrsUnifiedTools.materializeSlotReplace(current, 3, "emoji")
        replaced shouldHaveSize 10
        replaced[3] shouldBe "emoji"
        replaced.withIndex().filter { it.index != 3 }.map { it.value } shouldBe
            listOf("a", "b", "c", "e", "f", "g", "h", "i", "j")
    }

    test("materializeSlotReplace dedupes and keeps the cap when the swap collapses a duplicate") {
        val current = listOf("undo", "copy", "paste")
        val replaced = DrsUnifiedTools.materializeSlotReplace(current, 2, "undo")
        // distinct() keeps the FIRST occurrence, so the original head 'undo' wins.
        replaced shouldContainExactly listOf("undo", "copy")
    }

    test("materializeSlotReplace is honest about bad indexes and unknown ids") {
        val current = listOf("a", "b", "c")
        DrsUnifiedTools.materializeSlotReplace(current, 7, "emoji") shouldBe current
        DrsUnifiedTools.materializeSlotReplace(current, -1, "emoji") shouldBe current
        DrsUnifiedTools.materializeSlotReplace(current, 1, "not-a-tool") shouldBe current
    }

    test("the cap stays ten in v1.16.0 (the bar's slot count contract)") {
        DrsUnifiedTools.MAX_PINNED_TOOLS shouldBe 10
    }

    // -------------------------------------------------------------
    // «أعد ترتيب جميع الوحات بنظام مرتب وذكي»: the smart switcher
    // -------------------------------------------------------------

    val catalogue = listOf(ImeUiMode.DIACRITICS, ImeUiMode.SMART_SYMBOLS, ImeUiMode.ARABIC_LETTERS)

    test("smartSwitcher leads with the current panel always") {
        val ordered = DrsPanelOrder.smartSwitcher(
            ImeUiMode.ARABIC_LETTERS, emptyMap(), catalogue,
        )
        ordered.first() shouldBe ImeUiMode.ARABIC_LETTERS
        ordered shouldHaveSize 3
    }

    test("smartSwitcher orders the rest by their open counts") {
        val usage = mapOf(
            ImeUiMode.DIACRITICS.name to 5,
            ImeUiMode.SMART_SYMBOLS.name to 9,
            ImeUiMode.ARABIC_LETTERS.name to 1,
        )
        val ordered = DrsPanelOrder.smartSwitcher(ImeUiMode.DIACRITICS, usage, catalogue)
        ordered shouldContainExactly listOf(
            ImeUiMode.DIACRITICS,      // current leads even though symbols is hotter
            ImeUiMode.SMART_SYMBOLS,   // 9 opens
            ImeUiMode.ARABIC_LETTERS,  // 1 open
        )
    }

    test("smartSwitcher breaks ties by catalogue order") {
        val usage = mapOf(
            ImeUiMode.SMART_SYMBOLS.name to 3,
            ImeUiMode.ARABIC_LETTERS.name to 3,
        )
        val ordered = DrsPanelOrder.smartSwitcher(ImeUiMode.DIACRITICS, usage, catalogue)
        ordered shouldContainExactly listOf(
            ImeUiMode.DIACRITICS,
            ImeUiMode.SMART_SYMBOLS,
            ImeUiMode.ARABIC_LETTERS,
        )
    }

    test("smartSwitcher treats a missing counter as zero") {
        val ordered = DrsPanelOrder.smartSwitcher(
            ImeUiMode.SMART_SYMBOLS,
            mapOf(ImeUiMode.ARABIC_LETTERS.name to 2),
            catalogue,
        )
        ordered shouldContainExactly listOf(
            ImeUiMode.SMART_SYMBOLS,
            ImeUiMode.ARABIC_LETTERS,
            ImeUiMode.DIACRITICS,
        )
    }

    test("smartSwitcher with a single-panel catalogue is the identity") {
        val ordered = DrsPanelOrder.smartSwitcher(ImeUiMode.DIACRITICS, emptyMap(), listOf(ImeUiMode.DIACRITICS))
        ordered shouldContainExactly listOf(ImeUiMode.DIACRITICS)
    }

    test("recordOpen grows the local panel counters") {
        var counts = emptyMap<String, Int>()
        counts = DrsPanelOrder.recordOpen(counts, ImeUiMode.DIACRITICS)
        counts = DrsPanelOrder.recordOpen(counts, ImeUiMode.DIACRITICS)
        counts = DrsPanelOrder.recordOpen(counts, ImeUiMode.SMART_SYMBOLS)
        counts[ImeUiMode.DIACRITICS.name] shouldBe 2
        counts[ImeUiMode.SMART_SYMBOLS.name] shouldBe 1
        DrsPanelOrder.USAGE_NAMESPACE shouldBe "panels"
    }

    // -------------------------------------------------------------
    // لوحة الحركات كلوحة مفاتيح: the 4x4 keyboard arrangement
    // -------------------------------------------------------------

    test("the harakat keyboard is four rows of four keys (the numeric panel anatomy)") {
        DrsKeyboardHarakat.ROWS shouldHaveSize 4
        DrsKeyboardHarakat.ROWS.forEach { row -> row shouldHaveSize 4 }
    }

    test("every mark, the tatweel, delete and space are present") {
        val keys = DrsKeyboardHarakat.ROWS.flatten()
        val harakas = keys.filterIsInstance<DrsKeyboardHarakatKey.Haraka>().map { it.char }
        // The keyboard order puts the tanween row first — compare as sets.
        harakas.toSet() shouldBe DrsHarakat.MARKS.toSet()
        keys.count { it is DrsKeyboardHarakatKey.Tatweel } shouldBe 1
        keys.count { it is DrsKeyboardHarakatKey.Delete } shouldBe 1
        keys.count { it is DrsKeyboardHarakatKey.Space } shouldBe 1
    }

    test("the combos row is the four shadda pairs") {
        val combos = DrsKeyboardHarakat.ROWS.last()
            .filterIsInstance<DrsKeyboardHarakatKey.Combo>()
            .map { it.text }
        combos shouldContainExactly DrsHarakat.COMBOS
    }

    test("labels render combining marks on the dotted circle") {
        val fatha = DrsKeyboardHarakat.label(DrsKeyboardHarakatKey.Haraka(DrsHarakat.FATHA))
        fatha shouldBe "${DrsKeyboardHarakat.DOTTED_CIRCLE}${DrsHarakat.FATHA}"
        val combo = DrsKeyboardHarakat.label(
            DrsKeyboardHarakatKey.Combo("${DrsHarakat.SHADDA}${DrsHarakat.FATHA}"),
        )
        combo shouldBe "${DrsKeyboardHarakat.DOTTED_CIRCLE}${DrsHarakat.SHADDA}${DrsHarakat.FATHA}"
        DrsKeyboardHarakat.label(DrsKeyboardHarakatKey.Tatweel) shouldBe "${DrsHarakat.TATWEEL}"
        DrsKeyboardHarakat.label(DrsKeyboardHarakatKey.Delete) shouldBe "\u232B"
        DrsKeyboardHarakat.label(DrsKeyboardHarakatKey.Space) shouldBe "\u2423"
        DrsKeyboardHarakat.DOTTED_CIRCLE shouldBe '◌'
    }

    test("HARAKAT_KEYS excludes the delete and space keys") {
        DrsKeyboardHarakat.HARAKAT_KEYS.forEach { key ->
            (key !is DrsKeyboardHarakatKey.Delete) shouldBe true
            (key !is DrsKeyboardHarakatKey.Space) shouldBe true
        }
        DrsKeyboardHarakat.HARAKAT_KEYS shouldHaveSize 14
        (DrsKeyboardHarakat.HARAKAT_KEYS.first() is DrsKeyboardHarakatKey.Haraka) shouldBe true
    }

    test("the smart insert engine contract is untouched by the keyboard rebuild") {
        // The keyboard panel drives the same engine the v1.15.0 grid used.
        HarakatSmartInsert.decide(DrsHarakat.FATHA, DrsHarakat.DAMMA, true) shouldBe
            HarakaInsertMode.REPLACE_PREVIOUS
        HarakatSmartInsert.decide(DrsHarakat.SHADDA, DrsHarakat.FATHA, true) shouldBe
            HarakaInsertMode.APPEND
        HarakatSmartInsert.decide(null, DrsHarakat.FATHA, true) shouldBe
            HarakaInsertMode.APPEND
    }
})
