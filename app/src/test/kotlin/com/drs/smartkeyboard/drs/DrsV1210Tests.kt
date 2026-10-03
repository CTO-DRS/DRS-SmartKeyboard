/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.appAssetsDir
import com.drs.smartkeyboard.ime.clipboard.ClipCodeDetector
import com.drs.smartkeyboard.ime.clipboard.ClipEditNotificationPolicy
import com.drs.smartkeyboard.ime.clipboard.ClipMatch
import com.drs.smartkeyboard.ime.clipboard.ClipResultPalette
import com.drs.smartkeyboard.ime.clipboard.ClipSearchResults
import com.drs.smartkeyboard.ime.clipboard.ClipTextTransforms
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import com.drs.smartkeyboard.ime.dictionary.FREQUENCY_MAX
import com.drs.smartkeyboard.ime.dictionary.FREQUENCY_MIN
import com.drs.smartkeyboard.ime.dictionary.UserDictionaryEntry
import com.drs.smartkeyboard.ime.dictionary.UserDictionaryFormats
import com.drs.smartkeyboard.ime.media.emoji.EmojiSkinTone
import com.drs.smartkeyboard.ime.theme.ThemeManager
import com.drs.smartkeyboard.drs.DrsTextTool
import com.drs.smartkeyboard.drs.DrsTextTools
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * DRS v1.21.0: the safety, precision and accessibility round — the
 * tri-front audit found a harakat backspace inert on quick taps, a real
 * crash window where stale search matches indexed the shrunken editor
 * text out of bounds, a Markdown bullet instant-labeled as code, an
 * inconsistent tashkeel strip that kept Quranic annotation marks, a
 * FOLLOW_TIME window that forced night forever on inverted schedules,
 * and the never-shipped notification edit window — plus 16 bare Arabic
 * letters with no long-press harakat and an archive that lost the user
 * dictionary. Every pure contract introduced by this round is pinned here.
 */
class DrsV1210Tests : FunSpec({

    // -------------------------------------------------------------
    // ClipSearchResults.normalizeAgainst — the stale-snapshot crash guard
    // -------------------------------------------------------------
    test("normalizeAgainst drops matches wholly beyond the shrunken text") {
        val text = "abc" // shrank from 100 chars
        // (10,20) is wholly beyond; (3,5) starts exactly at the text end — out of bounds too
        val normalized = ClipSearchResults.normalizeAgainst(
            text,
            listOf(ClipMatch(10, 20), ClipMatch(3, 5)),
        )
        normalized shouldHaveSize 0
    }

    test("normalizeAgainst clamps matches that poke past the new end") {
        val normalized = ClipSearchResults.normalizeAgainst(
            "abc",
            listOf(ClipMatch(2, 50)),
        )
        normalized shouldHaveSize 1
        normalized[0].start shouldBe 2
        normalized[0].end shouldBe 3
    }

    test("normalizeAgainst keeps in-bounds matches untouched") {
        val match = ClipMatch(1, 3)
        ClipSearchResults.normalizeAgainst("abcd", listOf(match)) shouldBe listOf(match)
    }

    test("normalizeAgainst on an empty text drops everything") {
        ClipSearchResults.normalizeAgainst("", listOf(ClipMatch(0, 1))) shouldHaveSize 0
    }

    test("buildCards with stale matches beyond the text never throws and drops them") {
        val text = "hello world"
        // matches computed on a 500k text that got replaced with 11 chars
        val stale = listOf(ClipMatch(0, 5), ClipMatch(100, 120), ClipMatch(6, 11))
        val cards = ClipSearchResults.buildCards(text, stale)
        // the survivors keep their ORIGINAL indexes so navigation stays on target
        cards.map { it.matchIndex } shouldBe listOf(0, 2)
        cards[0].matched shouldBe "hello"
        cards[1].matched shouldBe "world"
    }

    test("highlightRanges text-aware variant never exceeds the current text") {
        val ranges = ClipSearchResults.highlightRanges("ab", listOf(ClipMatch(0, 1), ClipMatch(5, 9)), 0)
        ranges shouldHaveSize 1
        ranges[0].isActive.shouldBeTrue()
        ranges[0].end shouldBe 1
    }

    test("inBounds filters highlight ranges against the live field text length") {
        val ranges = ClipSearchResults.highlightRanges(listOf(ClipMatch(0, 3), ClipMatch(1, 8)), -1)
        val safe = ClipSearchResults.inBounds(ranges, textLength = 5)
        safe shouldHaveSize 1
        safe[0].start shouldBe 0
        safe[0].end shouldBe 3
    }

    // -------------------------------------------------------------
    // ClipCodeDetector — a Markdown bullet is not a JSDoc continuation
    // -------------------------------------------------------------
    test("markdown bullet lists are no longer instant-labeled as code") {
        val md = """
            * item one
            * item two
            * item three
        """.trimIndent()
        ClipCodeDetector.isCodeLine("* item one").shouldBeFalse()
        val analysis = ClipCodeDetector.analyze(md)
        analysis.isCode.shouldBeFalse()
    }

    test("a leading star followed by real code signals still scores") {
        ClipCodeDetector.isCodeLine("* let x = 5;").shouldBeTrue()
        ClipCodeDetector.isCodeLine("* console.log(x);").shouldBeTrue()
    }

    test("unambiguous comment markers still decide immediately") {
        ClipCodeDetector.isCodeLine("// plain comment").shouldBeTrue()
        ClipCodeDetector.isCodeLine("/** doc block start").shouldBeTrue()
        ClipCodeDetector.isCodeLine(" * block end */").shouldBeTrue()
        ClipCodeDetector.isCodeLine("#!/bin/bash").shouldBeTrue()
    }

    // -------------------------------------------------------------
    // ClipTextTransforms — the tashkeel strip is unified with the panel
    // -------------------------------------------------------------
    test("removeArabicDiacritics also strips the small Quranic annotation marks") {
        val quranicMark = "\u06D6" // U+06D6 small high ligature — was kept before
        val text = "قَالَ$quranicMark"
        val stripped = ClipTextTransforms.removeArabicDiacritics(text)
        stripped shouldBe "قال"
    }

    test("removeArabicDiacritics keeps letters, digits and spaces") {
        // harakat go, hamza carriers stay — letter normalization is another tool
        ClipTextTransforms.removeArabicDiacritics("قَالَ آلٌ 123") shouldBe "قال آل 123"
    }

    // -------------------------------------------------------------
    // ThemeManager.ThemeDayWindow — the FOLLOW_TIME day decision
    // -------------------------------------------------------------
    test("normal sunrise/sunset range keeps the classic inside-window rule") {
        val nine = java.time.LocalTime.of(9, 0)
        val sunrise = java.time.LocalTime.of(6, 0)
        val sunset = java.time.LocalTime.of(18, 0)
        ThemeManager.ThemeDayWindow.isDaytime(nine, sunrise, sunset).shouldBeTrue()
        ThemeManager.ThemeDayWindow.isDaytime(java.time.LocalTime.of(23, 0), sunrise, sunset).shouldBeFalse()
    }

    test("inverted range (night shift) wraps midnight instead of forcing night forever") {
        val sunrise = java.time.LocalTime.of(22, 0)
        val sunset = java.time.LocalTime.of(6, 0)
        ThemeManager.ThemeDayWindow.isDaytime(java.time.LocalTime.of(23, 30), sunrise, sunset).shouldBeTrue()
        ThemeManager.ThemeDayWindow.isDaytime(java.time.LocalTime.of(4, 0), sunrise, sunset).shouldBeTrue()
        ThemeManager.ThemeDayWindow.isDaytime(java.time.LocalTime.of(12, 0), sunrise, sunset).shouldBeFalse()
    }

    test("degenerate equal pair falls back to day") {
        val noon = java.time.LocalTime.of(12, 0)
        ThemeManager.ThemeDayWindow.isDaytime(noon, noon, noon).shouldBeTrue()
    }

    test("window boundaries are inclusive") {
        ThemeManager.ThemeDayWindow.isDaytime(
            java.time.LocalTime.of(6, 0),
            java.time.LocalTime.of(6, 0),
            java.time.LocalTime.of(18, 0),
        ).shouldBeTrue()
    }

    // -------------------------------------------------------------
    // EmojiSkinTone — the in-palette selector cycle and swatches
    // -------------------------------------------------------------
    test("skin tone cycle walks every tone and wraps back to default") {
        var tone = EmojiSkinTone.DEFAULT
        repeat(EmojiSkinTone.entries.size) { tone = tone.next() }
        tone shouldBe EmojiSkinTone.DEFAULT
        EmojiSkinTone.DARK_SKIN_TONE.next() shouldBe EmojiSkinTone.DEFAULT
        EmojiSkinTone.DEFAULT.next() shouldBe EmojiSkinTone.LIGHT_SKIN_TONE
    }

    test("every tone paints a distinct swatch") {
        val swatches = EmojiSkinTone.entries.map { it.swatchColor() }
        swatches.distinct() shouldHaveSize EmojiSkinTone.entries.size
    }

    // -------------------------------------------------------------
    // UserDictionaryFormats — the archive round trip of learned words
    // -------------------------------------------------------------
    test("entry line round-trips through parseEntryLine") {
        val entry = UserDictionaryEntry(42, "مدرسة", 200, "ar", "مذ")
        val parsed = UserDictionaryFormats.parseEntryLine(UserDictionaryFormats.entryLine(entry))
        parsed.shouldNotBeNull()
        parsed.word shouldBe "مدرسة"
        parsed.freq shouldBe 200
        parsed.locale shouldBe "ar"
        parsed.shortcut shouldBe "مذ"
    }

    test("a global entry (null locale) round-trips through the literal null marker") {
        val entry = UserDictionaryEntry(0, "widget", 64, null, null)
        val line = UserDictionaryFormats.entryLine(entry)
        val parsed = UserDictionaryFormats.parseEntryLine(line)
        parsed.shouldNotBeNull()
        parsed.locale.shouldBeNull()
        parsed.shortcut.shouldBeNull()
        parsed.freq shouldBe 64
    }

    test("malformed lines and out-of-range frequencies parse to null honestly") {
        UserDictionaryFormats.parseEntryLine("nowordonly").shouldBeNull()
        UserDictionaryFormats.parseEntryLine(" w=word;f=notanumber").shouldBeNull()
        UserDictionaryFormats.parseEntryLine(" w=word;f=${FREQUENCY_MAX + 1}").shouldBeNull()
        UserDictionaryFormats.parseEntryLine(" f=100;missing").shouldBeNull()
        UserDictionaryFormats.parseEntryLine(" w=word;f=${FREQUENCY_MIN}").shouldNotBeNull()
    }

    // -------------------------------------------------------------
    // ClipEditNotificationPolicy — the v1.11 notification promise
    // -------------------------------------------------------------
    test("a plain text capture with everything on notifies") {
        ClipEditNotificationPolicy.shouldNotify(
            prefEnabled = true, historyEnabled = true,
            type = ItemType.TEXT, text = "مرحبا بالعالم", isSensitive = false,
        ).shouldBeTrue()
    }

    test("sensitive text never rides a notification") {
        ClipEditNotificationPolicy.shouldNotify(
            prefEnabled = true, historyEnabled = true,
            type = ItemType.TEXT, text = "password123", isSensitive = true,
        ).shouldBeFalse()
    }

    test("non-text items, blank text, disabled pref and disabled history never notify") {
        ClipEditNotificationPolicy.shouldNotify(
            true, true, ItemType.IMAGE, null, false,
        ).shouldBeFalse()
        ClipEditNotificationPolicy.shouldNotify(
            true, true, ItemType.TEXT, "   ", false,
        ).shouldBeFalse()
        ClipEditNotificationPolicy.shouldNotify(
            false, true, ItemType.TEXT, "text", false,
        ).shouldBeFalse()
        ClipEditNotificationPolicy.shouldNotify(
            true, false, ItemType.TEXT, "text", false,
        ).shouldBeFalse()
    }

    test("preview flattens newlines and caps honestly") {
        ClipEditNotificationPolicy.previewOf("سطر\nآخر\nثالث") shouldBe "سطر آخر ثالث"
        val long = "ا".repeat(400)
        val preview = ClipEditNotificationPolicy.previewOf(long)
        preview.length shouldBe ClipEditNotificationPolicy.PREVIEW_CHARS + 1 // + the ellipsis
        preview.endsWith("…").shouldBeTrue()
        ClipEditNotificationPolicy.previewOf("قصير") shouldBe "قصير"
    }

    // -------------------------------------------------------------
    // DrsTextTool — the invisible mark insertion tools
    // -------------------------------------------------------------
    test("every insertion tool maps to its exact Unicode mark") {
        DrsTextTools.insertionMarkFor(DrsTextTool.INSERT_RLM) shouldBe '\u200F'
        DrsTextTools.insertionMarkFor(DrsTextTool.INSERT_LRM) shouldBe '\u200E'
        DrsTextTools.insertionMarkFor(DrsTextTool.INSERT_ZWJ) shouldBe '\u200D'
        DrsTextTools.insertionMarkFor(DrsTextTool.INSERT_ZWNJ) shouldBe '\u200C'
        DrsTextTools.insertionMarkFor(DrsTextTool.UPPERCASE).shouldBeNull()
        DrsTextTools.insertionMarkFor(DrsTextTool.DELETE_LINE).shouldBeNull()
    }

    test("the insertion tools are editor ops inside the dispatch range") {
        for (tool in listOf(
            DrsTextTool.INSERT_RLM, DrsTextTool.INSERT_LRM,
            DrsTextTool.INSERT_ZWJ, DrsTextTool.INSERT_ZWNJ,
        )) {
            tool.isEditorOp.shouldBeTrue()
            tool.isInsertMark.shouldBeTrue()
            DrsTextTool.CODE_RANGE.contains(tool.code).shouldBeTrue()
            DrsTextTool.fromCode(tool.code) shouldBe tool
        }
        // DRS v1.4.0 (public): the sentence vocalization extends the range;
        // DRS v1.6.0 (public): the number-to-words tool extends it once more;
        // DRS v1.7.0 (public): the financial tafqit extends it last;
        // DRS v1.9.0 (public): the clock-time tool extends it last;
        // DRS v1.10.0 (public): the fraction, weekday and ordinal tools
        // extend it last.
        DrsTextTool.CODE_RANGE.first shouldBe -665
    }

    // -------------------------------------------------------------
    // Asset contracts — the 16 bare Arabic letters + the eastern symbols
    // -------------------------------------------------------------
    // resolved lazily on first test use — never during spec construction,
    // and never relative to the process working directory
    val assetsDir by lazy { appAssetsDir("ime", "keyboard") }
    val harakatCodes = setOf(1614, 1615, 1616, 1617, 1618)

    test("every previously-bare Arabic letter now carries long-press harakat popups") {
        val mappings = Json.parseToJsonElement(
            File(assetsDir, "org.drs.localization/popupMappings/ar.json").readText(),
        ).jsonObject["all"]!!.jsonObject
        // The 16 letters the audit found with ZERO popup entries.
        val bareLetters = listOf("ت", "ث", "ح", "خ", "د", "ذ", "ر", "س", "ص", "ض", "ط", "ظ", "ع", "غ", "م", "ن")
        for (letter in bareLetters) {
            val entry = mappings[letter]?.jsonObject
            entry.shouldNotBeNull()
            val relevant = entry["relevant"]?.jsonArray
            relevant.shouldNotBeNull()
            val codes = relevant.map { it.jsonObject["code"]!!.jsonPrimitive.int }
            codes.any { it in harakatCodes }.shouldBeTrue()
        }
    }

    test("the eastern symbols page carries the Arabic comma and the tatweel") {
        val page = Json.parseToJsonElement(
            File(assetsDir, "org.drs.layouts/layouts/symbols/eastern.json").readText(),
        ).jsonArray
        val codes = mutableSetOf<Int>()
        fun walk(node: kotlinx.serialization.json.JsonElement) {
            when (node) {
                is kotlinx.serialization.json.JsonObject -> {
                    node["code"]?.let { codes.add(it.jsonPrimitive.int) }
                    node.values.forEach { walk(it) }
                }
                is kotlinx.serialization.json.JsonArray -> node.forEach { walk(it) }
                else -> Unit
            }
        }
        walk(page)
        codes.contains(1548).shouldBeTrue() // ، U+060C — was missing entirely
        codes.contains(1600).shouldBeTrue() // ـ U+0640 — was missing entirely
        codes.contains(1563).shouldBeTrue() // ؛ stays
    }
})
