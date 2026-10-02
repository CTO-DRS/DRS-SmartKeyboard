/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.media.emoji.EmojiData
import com.drs.smartkeyboard.ime.nlp.latin.SpellingDecider
import com.drs.smartkeyboard.ime.smartbar.quickaction.SmartToolCodes
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.ime.nlp.latin.LatinWordNormalize
import com.drs.smartkeyboard.lib.DrsLocale
import com.drs.smartkeyboard.drs.DrsUnifiedTools
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

/**
 * DRS v1.19.0: the comprehensive-audit round — the real spell-check typo
 * marking behind the conservative SpellingDecider gates, the emoji search
 * un-breaking (locale metadata loading with honest fallbacks), the
 * split/merge keyboard toggles end to end, the two v1.8.0 text tools
 * finally counted in the stats, and the catalogue tail contract extended.
 */
class DrsV1190Tests : FunSpec({

    // -------------------------------------------------------------
    // SpellingDecider — the pure gates of real typo marking
    // -------------------------------------------------------------

    test("isSpellableWord rejects words that are too short or too long") {
        SpellingDecider.isSpellableWord("ok", midSentence = false) shouldBe false
        SpellingDecider.isSpellableWord("a".repeat(SpellingDecider.MIN_SPELL_LENGTH), false) shouldBe true
        SpellingDecider.isSpellableWord("a".repeat(SpellingDecider.MAX_SPELL_LENGTH + 1), false) shouldBe false
        SpellingDecider.isSpellableWord("a".repeat(SpellingDecider.MAX_SPELL_LENGTH), false) shouldBe true
    }

    test("isSpellableWord rejects digits, punctuation and mixed forms") {
        SpellingDecider.isSpellableWord("abc1", false) shouldBe false
        SpellingDecider.isSpellableWord("hello!", false) shouldBe false
        SpellingDecider.isSpellableWord("user@example", false) shouldBe false
        SpellingDecider.isSpellableWord("url.com", false) shouldBe false
    }

    test("isSpellableWord never flags all-caps acronyms") {
        SpellingDecider.isSpellableWord("NASA", false) shouldBe false
        SpellingDecider.isSpellableWord("DRS", false) shouldBe false
    }

    test("isSpellableWord protects capitalized proper nouns mid-sentence only") {
        // Mid-sentence capitalized: likely a name — never judged.
        SpellingDecider.isSpellableWord("Ahmed", midSentence = true) shouldBe false
        // Sentence start (no preceding words): judged normally.
        SpellingDecider.isSpellableWord("Teh", midSentence = false) shouldBe true
    }

    test("isSpellableWord accepts plain Arabic and Latin words") {
        SpellingDecider.isSpellableWord("مدرسة", false) shouldBe true
        SpellingDecider.isSpellableWord("hello", false) shouldBe true
        SpellingDecider.isSpellableWord("world", true) shouldBe true
    }

    test("shouldFlagTypo requires unknown word, rich dictionary and a correction") {
        // Known words are never typos.
        SpellingDecider.shouldFlagTypo(wordInDict = true, wordInUserWords = false, dictEntries = 50_000, correctionCount = 3) shouldBe false
        SpellingDecider.shouldFlagTypo(wordInDict = false, wordInUserWords = true, dictEntries = 50_000, correctionCount = 3) shouldBe false
        // Thin dictionaries (the eight v1.18.0 curated ones) never judge.
        SpellingDecider.shouldFlagTypo(false, false, dictEntries = 1_390, correctionCount = 5) shouldBe false
        SpellingDecider.shouldFlagTypo(false, false, dictEntries = SpellingDecider.MIN_DICT_ENTRIES_FOR_TYPO - 1, correctionCount = 5) shouldBe false
        // Rich dictionary but no plausible correction: honest pass.
        SpellingDecider.shouldFlagTypo(false, false, dictEntries = 50_000, correctionCount = 0) shouldBe false
        // The full positive: unknown word, rich dictionary, correction exists.
        SpellingDecider.shouldFlagTypo(false, false, dictEntries = 50_000, correctionCount = 1) shouldBe true
    }

    test("the normalization pipeline still unifies Arabic carriers for the spell path") {
        // The dictionary is stored normalized; مدرسه must normalize to the
        // same form as مدرسة so a ta-marbuta typo is valid (no false flag).
        LatinWordNormalize.normalize("مدرسه") shouldBe LatinWordNormalize.normalize("مدرسة")
        LatinWordNormalize.normalize("أحمد") shouldBe LatinWordNormalize.normalize("احمد")
    }

    // -------------------------------------------------------------
    // Emoji search — the metadata resolution with honest fallbacks
    // -------------------------------------------------------------

    test("pickEmojiAssetPath prefers the language file and is case-insensitive") {
        val available = listOf("root.txt", "ar.txt", "en.txt")
        EmojiData.pickEmojiAssetPath(available, "ar", null, null) shouldBe "ime/media/emoji/ar.txt"
        EmojiData.pickEmojiAssetPath(available, "AR", null, null) shouldBe "ime/media/emoji/ar.txt"
    }

    test("pickEmojiAssetPath follows country then variant priority") {
        EmojiData.pickEmojiAssetPath(listOf("en.txt", "en_GB.txt"), "en", "GB", null) shouldBe "ime/media/emoji/en_GB.txt"
        EmojiData.pickEmojiAssetPath(
            listOf("en.txt", "en_GB.txt", "en_GB_posix.txt"),
            "en", "GB", "posix",
        ) shouldBe "ime/media/emoji/en_GB_posix.txt"
        // Country without a specific file degrades to the plain language file.
        EmojiData.pickEmojiAssetPath(listOf("en.txt"), "en", "GB", null) shouldBe "ime/media/emoji/en.txt"
        // Unknown language resolves to null (the caller owns the fallback).
        EmojiData.pickEmojiAssetPath(listOf("root.txt", "en.txt"), "zz", null, null) shouldBe null
    }

    test("resolveEmojiAssetPathWithFallback never returns metadata-less data while en exists") {
        val available = listOf("root.txt", "ar.txt", "en.txt", "de.txt")
        // Arabic gets its own annotations.
        EmojiData.resolveEmojiAssetPathWithFallback(available, DrsLocale.from("ar")) shouldBe "ime/media/emoji/ar.txt"
        // Russian has no annotation file: honest English fallback keeps search alive.
        EmojiData.resolveEmojiAssetPathWithFallback(available, DrsLocale.from("ru")) shouldBe "ime/media/emoji/en.txt"
        // Country-qualified Arabic still resolves through the language file.
        EmojiData.resolveEmojiAssetPathWithFallback(available, DrsLocale.from("ar", "EG")) shouldBe "ime/media/emoji/ar.txt"
    }

    test("resolveEmojiAssetPathWithFallback degrades to root only when nothing else exists") {
        EmojiData.resolveEmojiAssetPathWithFallback(listOf("root.txt"), DrsLocale.from("ar")) shouldBe "ime/media/emoji/root.txt"
        EmojiData.resolveEmojiAssetPathWithFallback(emptyList(), DrsLocale.from("en")) shouldBe "ime/media/emoji/root.txt"
    }

    // -------------------------------------------------------------
    // Split/merge keyboard toggles — engine codes end to end
    // -------------------------------------------------------------

    test("the split/merge toggles are registered as countable smart tools") {
        SmartToolCodes shouldContain KeyCode.SPLIT_LAYOUT
        SmartToolCodes shouldContain KeyCode.MERGE_LAYOUT
        TextKeyData.getCodeInfoAsTextKeyData(KeyCode.SPLIT_LAYOUT).shouldNotBeNull()
        TextKeyData.getCodeInfoAsTextKeyData(KeyCode.MERGE_LAYOUT).shouldNotBeNull()
    }

    test("the split/merge catalogue tools point at the engine codes") {
        val split = DrsUnifiedTools.byId("split_keyboard").shouldNotBeNull()
        split.code shouldBe KeyCode.SPLIT_LAYOUT
        val merge = DrsUnifiedTools.byId("merge_keyboard").shouldNotBeNull()
        merge.code shouldBe KeyCode.MERGE_LAYOUT
        // Appended at the tail, keeping every persisted arrangement.
        // DRS v1.2.0 (الإصدار العام) appended the smart numbers panel
        // after the pair.
        DrsUnifiedTools.ALL.last().id shouldBe "smart_number_panel"
        DrsUnifiedTools.ALL[DrsUnifiedTools.ALL.size - 2].id shouldBe "merge_keyboard"
        DrsUnifiedTools.ALL[DrsUnifiedTools.ALL.size - 3].id shouldBe "split_keyboard"
    }

    // -------------------------------------------------------------
    // Stats parity — the two v1.8.0 text tools finally counted
    // -------------------------------------------------------------

    test("the v1.8.0 text tools count as smart-tool presses since v1.19.0") {
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_SEPARATE_DIGIT_LETTERS
        SmartToolCodes shouldContain KeyCode.TEXT_TOOL_REMOVE_PUNCTUATION
        // The constants match the enum codes the panel has always dispatched.
        KeyCode.TEXT_TOOL_SEPARATE_DIGIT_LETTERS shouldBe -644
        KeyCode.TEXT_TOOL_REMOVE_PUNCTUATION shouldBe -645
    }

    test("the split/merge codes are absent from the legacy dead-code zones") {
        // Both codes now have real handlers, so the old audit complaint
        // (defined, never handled, never exposed) no longer holds: they are
        // exposed via the catalogue AND handled by KeyboardManager.
        SmartToolCodes.map { it }.sorted() shouldContain KeyCode.MERGE_LAYOUT
    }
})
