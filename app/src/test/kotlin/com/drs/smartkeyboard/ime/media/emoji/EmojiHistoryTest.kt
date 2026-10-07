/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.media.emoji

import com.drs.smartkeyboard.app.DrsPreferenceModel
import org.drs.jetpref.datastore.jetprefDataStoreOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * DRS p9 (T-C4) — regression contracts for [EmojiHistoryHelper] and
 * [EmojiHistory.Serializer].
 *
 * The history is persisted as a jetpref custom pref
 * (`prefs.emoji.historyData`, serializer [EmojiHistory.Serializer]), so the
 * helpers are exercised exactly the way production code drives them
 * (EmojiPaletteView.kt / EmojiSuggestionProvider.kt) against a fresh
 * in-memory preference store per test.
 */
class EmojiHistoryTest : FunSpec({

    /**
     * DRS v2.14.0: the recording path now requires the privacy gate's
     * record context. These heritage contracts exercise the mutation
     * strategies in a fully clean context — the gate's own contracts live
     * in DrsV21400Tests (exhaustive refusal table + full-loop gating).
     */
    suspend fun markEmojiUsed(prefs: DrsPreferenceModel, emoji: Emoji) {
        EmojiHistoryHelper.markEmojiUsed(
            prefs, emoji,
            DrsEmojiHistoryGate.RecordContext(
                isIncognitoMode = false,
                isPasswordVariation = false,
                isRawInputEditor = false,
                isComposingEnabled = true,
                isDeviceLocked = false,
            ),
        )
    }

    // U+1F600 grinning face
    val grinning = Emoji("😀", "grinning face", emptyList())
    // U+1F60E smiling face with sunglasses
    val sunglasses = Emoji("😎", "smiling face with sunglasses", emptyList())
    // U+1F973 partying face
    val partying = Emoji("🥳", "partying face", emptyList())
    // U+1F431 cat face
    val cat = Emoji("🐱", "cat face", emptyList())
    // U+1F984 unicorn face
    val unicorn = Emoji("🦄", "unicorn face", emptyList())

    test("markEmojiUsed with AUTO_SORT_PREPEND moves an existing pinned emoji to front") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        prefs.emoji.historyPinnedUpdateStrategy.set(EmojiHistory.UpdateStrategy.AUTO_SORT_PREPEND)

        // pinEmoji only moves emojis that exist in recents (EmojiHistory.kt:131-135),
        // so seed the recents first (AUTO prepend → newest first).
        markEmojiUsed(prefs, grinning)
        markEmojiUsed(prefs, sunglasses)

        EmojiHistoryHelper.pinEmoji(prefs, grinning)
        EmojiHistoryHelper.pinEmoji(prefs, sunglasses)
        // pinEmoji prepends under AUTO_SORT_PREPEND: last pinned ends up first
        prefs.emoji.historyData.get().pinned shouldBe listOf(sunglasses, grinning)

        markEmojiUsed(prefs, grinning)
        // grinning was already pinned, so the automatic branch (EmojiHistory.kt:93-100)
        // removes and re-adds it with the strategy → moved to front.
        prefs.emoji.historyData.get().pinned shouldBe listOf(grinning, sunglasses)
        // it must not leak into recents
        prefs.emoji.historyData.get().recent shouldBe emptyList()
    }

    test("markEmojiUsed with AUTO_SORT_PREPEND moves an existing recent emoji to front") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        prefs.emoji.historyRecentUpdateStrategy.set(EmojiHistory.UpdateStrategy.AUTO_SORT_PREPEND)

        markEmojiUsed(prefs, grinning)
        markEmojiUsed(prefs, sunglasses)
        markEmojiUsed(prefs, partying)
        prefs.emoji.historyData.get().recent shouldBe listOf(partying, sunglasses, grinning)

        markEmojiUsed(prefs, grinning)
        // automatic branch for existing recents (EmojiHistory.kt:102-109): moved to front
        prefs.emoji.historyData.get().recent shouldBe listOf(grinning, partying, sunglasses)
    }

    test("markEmojiUsed with MANUAL sort keeps an existing pinned emoji in place") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        prefs.emoji.historyPinnedUpdateStrategy.set(EmojiHistory.UpdateStrategy.MANUAL_SORT_APPEND)

        // Seed via recents: pinEmoji only moves emojis present in recents, and
        // it APPENDS to pinned under MANUAL_SORT_APPEND (addWithStrategy).
        markEmojiUsed(prefs, grinning)
        EmojiHistoryHelper.pinEmoji(prefs, grinning)
        markEmojiUsed(prefs, sunglasses)
        EmojiHistoryHelper.pinEmoji(prefs, sunglasses)
        markEmojiUsed(prefs, partying)
        EmojiHistoryHelper.pinEmoji(prefs, partying)
        prefs.emoji.historyData.get().pinned shouldBe listOf(grinning, sunglasses, partying)

        markEmojiUsed(prefs, sunglasses)
        // manual sort: item must stay in place (EmojiHistory.kt:98-100)
        prefs.emoji.historyData.get().pinned shouldBe listOf(grinning, sunglasses, partying)
    }

    test("markEmojiUsed with MANUAL sort keeps an existing recent emoji in place") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        prefs.emoji.historyRecentUpdateStrategy.set(EmojiHistory.UpdateStrategy.MANUAL_SORT_PREPEND)

        markEmojiUsed(prefs, grinning)
        markEmojiUsed(prefs, sunglasses)
        markEmojiUsed(prefs, partying)
        // new emojis are PREPENDED even under MANUAL_SORT_PREPEND
        // (addWithStrategy) — 'manual' only means existing items are never
        // repositioned by a later markEmojiUsed.
        prefs.emoji.historyData.get().recent shouldBe listOf(partying, sunglasses, grinning)

        markEmojiUsed(prefs, sunglasses)
        // manual sort: item must stay in place (EmojiHistory.kt:107-109)
        prefs.emoji.historyData.get().recent shouldBe listOf(partying, sunglasses, grinning)
    }

    test("PREPEND strategy with maxSize 2 keeps the two newest recent emojis") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        prefs.emoji.historyRecentUpdateStrategy.set(EmojiHistory.UpdateStrategy.AUTO_SORT_PREPEND)
        prefs.emoji.historyRecentMaxSize.set(2)

        markEmojiUsed(prefs, grinning)
        markEmojiUsed(prefs, sunglasses)
        markEmojiUsed(prefs, partying)

        // newest is prepended to the front, take(2) keeps the front (EmojiHistory.kt:225-227)
        prefs.emoji.historyData.get().recent shouldBe listOf(partying, sunglasses)
    }

    test("APPEND strategy with maxSize 2 keeps the two newest recent emojis at the tail") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        prefs.emoji.historyRecentUpdateStrategy.set(EmojiHistory.UpdateStrategy.AUTO_SORT_APPEND)
        prefs.emoji.historyRecentMaxSize.set(2)

        markEmojiUsed(prefs, grinning)
        markEmojiUsed(prefs, sunglasses)
        markEmojiUsed(prefs, partying)

        // newest is appended to the tail, takeLast(2) keeps the tail (EmojiHistory.kt:227-229)
        prefs.emoji.historyData.get().recent shouldBe listOf(sunglasses, partying)
    }

    test("MaxSizeUnlimited (0) disables recent trimming") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        prefs.emoji.historyRecentUpdateStrategy.set(EmojiHistory.UpdateStrategy.AUTO_SORT_PREPEND)
        prefs.emoji.historyRecentMaxSize.set(EmojiHistory.MaxSizeUnlimited)

        markEmojiUsed(prefs, grinning)
        markEmojiUsed(prefs, sunglasses)
        markEmojiUsed(prefs, partying)

        prefs.emoji.historyData.get().recent shouldBe listOf(partying, sunglasses, grinning)
    }

    test("disabled history makes all helper mutations no-ops") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        prefs.emoji.historyEnabled.set(false)

        markEmojiUsed(prefs, grinning)
        EmojiHistoryHelper.pinEmoji(prefs, sunglasses)
        EmojiHistoryHelper.unpinEmoji(prefs, grinning)
        EmojiHistoryHelper.moveEmoji(prefs, grinning, 5)
        EmojiHistoryHelper.removeEmoji(prefs, grinning)
        EmojiHistoryHelper.deleteHistory(prefs)

        prefs.emoji.historyData.get() shouldBe EmojiHistory.Empty
    }

    test("pinEmoji only moves an emoji that is present in recents") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)

        markEmojiUsed(prefs, grinning)
        EmojiHistoryHelper.pinEmoji(prefs, sunglasses)
        // sunglasses is not in recents → no-op (EmojiHistory.kt:131-135)
        prefs.emoji.historyData.get() shouldBe EmojiHistory(
            pinned = emptyList(),
            recent = listOf(grinning),
        )

        EmojiHistoryHelper.pinEmoji(prefs, grinning)
        prefs.emoji.historyData.get() shouldBe EmojiHistory(
            pinned = listOf(grinning),
            recent = emptyList(),
        )
    }

    test("moveEmoji clamps out-of-bounds offsets to the valid range") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        prefs.emoji.historyData.set(
            EmojiHistory(
                pinned = listOf(grinning, sunglasses, partying),
                recent = listOf(cat),
            ),
        )

        EmojiHistoryHelper.moveEmoji(prefs, grinning, 100)
        // index 0 + 100 clamps to the last position (EmojiHistory.kt:232-240)
        prefs.emoji.historyData.get().pinned shouldBe listOf(sunglasses, partying, grinning)

        EmojiHistoryHelper.moveEmoji(prefs, grinning, -100)
        // index 2 - 100 clamps to the first position
        prefs.emoji.historyData.get().pinned shouldBe listOf(grinning, sunglasses, partying)

        EmojiHistoryHelper.moveEmoji(prefs, sunglasses, 1)
        prefs.emoji.historyData.get().pinned shouldBe listOf(grinning, partying, sunglasses)

        // an emoji not present anywhere is a no-op
        EmojiHistoryHelper.moveEmoji(prefs, unicorn, 3)
        prefs.emoji.historyData.get() shouldBe EmojiHistory(
            pinned = listOf(grinning, partying, sunglasses),
            recent = listOf(cat),
        )

        // offset 0 is skipped entirely (EmojiHistory.kt:158)
        EmojiHistoryHelper.moveEmoji(prefs, sunglasses, 0)
        prefs.emoji.historyData.get().pinned shouldBe listOf(grinning, partying, sunglasses)
    }

    test("deleteHistory clears recents but keeps pinned emojis") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        prefs.emoji.historyRecentUpdateStrategy.set(EmojiHistory.UpdateStrategy.AUTO_SORT_PREPEND)

        // pinEmoji only moves emojis that exist in recents — seed first.
        markEmojiUsed(prefs, grinning)
        EmojiHistoryHelper.pinEmoji(prefs, grinning)
        markEmojiUsed(prefs, sunglasses)
        markEmojiUsed(prefs, partying)
        prefs.emoji.historyData.get().recent shouldBe listOf(partying, sunglasses)

        EmojiHistoryHelper.deleteHistory(prefs)
        prefs.emoji.historyData.get() shouldBe EmojiHistory(
            pinned = listOf(grinning),
            recent = emptyList(),
        )
    }

    test("Serializer round-trips pinned and recent emojis") {
        val history = EmojiHistory(
            pinned = listOf(grinning),
            recent = listOf(sunglasses, partying),
        )

        val json = EmojiHistory.Serializer.serialize(history)
        EmojiHistory.Serializer.deserialize(json) shouldBe history
    }

    test("Serializer survives garbage input by returning Empty") {
        // EmojiHistory.kt:57-64 — any decode failure must degrade to Empty, never throw
        EmojiHistory.Serializer.deserialize("gobbledygook") shouldBe EmojiHistory.Empty
        EmojiHistory.Serializer.deserialize("") shouldBe EmojiHistory.Empty
        EmojiHistory.Serializer.deserialize("[1, 2, 3]") shouldBe EmojiHistory.Empty
        EmojiHistory.Serializer.deserialize("{\"pinned\":") shouldBe EmojiHistory.Empty
        // valid JSON with a required field missing degrades to Empty as well
        EmojiHistory.Serializer.deserialize("{\"pinned\": [\"😀\"]}") shouldBe EmojiHistory.Empty
        // a fully valid payload decodes value-only emojis
        EmojiHistory.Serializer.deserialize("{\"pinned\": [\"😀\"], \"recent\": []}") shouldBe
            EmojiHistory(pinned = listOf(grinning), recent = emptyList())
    }
})
