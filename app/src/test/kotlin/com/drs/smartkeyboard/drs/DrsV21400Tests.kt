/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.app.DrsPreferenceModel
import com.drs.smartkeyboard.ime.media.emoji.DrsEmojiHistoryGate
import com.drs.smartkeyboard.ime.media.emoji.Emoji
import com.drs.smartkeyboard.ime.media.emoji.EmojiHistory
import com.drs.smartkeyboard.ime.media.emoji.EmojiHistoryHelper
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.drs.jetpref.datastore.jetprefDataStoreOf

/**
 * DRS v2.14.0 «ذاكرة الإيموجي الأمينة» — عقود بوابة التسجيل: الالتقاط في
 * السياقات الخاصة (تخفي، كلمة مرور، محرر خام، بلا تكوين، قفل شاشي) يُرفض
 * رفضًا صامتًا حتميًا، والعرض والإدارة لا يُحكمان. الجدول الشامل (64
 * تركيبة) يثبت أن التسجيل يحدث في حالة واحدة بالضبط، وتوزيع أسباب الرفض
 * يطابق الحسبة التوافقية المتوقعة — بلا ترتيب فحص مكسور.
 */
class DrsV21400Tests : FunSpec({

    // ------------------------------------------------------------------
    // البوابة النقية — عقود الحكم الفردي
    // ------------------------------------------------------------------

    test("العقد: سياق سليم كليًا والسجل مفعّل → سجّل") {
        DrsEmojiHistoryGate.decide(
            historyEnabled = true,
            isIncognitoMode = false,
            isPasswordVariation = false,
            isRawInputEditor = false,
            isComposingEnabled = true,
            isDeviceLocked = false,
        ) shouldBe DrsEmojiHistoryGate.Verdict.Record
    }

    test("العقد: كل سبب رفض منفردًا يُذكر باسمه الصريح") {
        fun verdict(
            enabled: Boolean = true,
            incognito: Boolean = false,
            password: Boolean = false,
            raw: Boolean = false,
            composing: Boolean = true,
            locked: Boolean = false,
        ) = DrsEmojiHistoryGate.decide(
            historyEnabled = enabled,
            isIncognitoMode = incognito,
            isPasswordVariation = password,
            isRawInputEditor = raw,
            isComposingEnabled = composing,
            isDeviceLocked = locked,
        )

        verdict(incognito = true) shouldBe
            DrsEmojiHistoryGate.Verdict.Refuse(DrsEmojiHistoryGate.Refusal.PRIVATE_SESSION)
        verdict(password = true) shouldBe
            DrsEmojiHistoryGate.Verdict.Refuse(DrsEmojiHistoryGate.Refusal.CREDENTIALS_FIELD)
        verdict(raw = true) shouldBe
            DrsEmojiHistoryGate.Verdict.Refuse(DrsEmojiHistoryGate.Refusal.RAW_EDITOR)
        verdict(composing = false) shouldBe
            DrsEmojiHistoryGate.Verdict.Refuse(DrsEmojiHistoryGate.Refusal.NON_COMPOSING_FIELD)
        verdict(locked = true) shouldBe
            DrsEmojiHistoryGate.Verdict.Refuse(DrsEmojiHistoryGate.Refusal.DEVICE_LOCKED)
        verdict(enabled = false) shouldBe
            DrsEmojiHistoryGate.Verdict.Refuse(DrsEmojiHistoryGate.Refusal.HISTORY_DISABLED)
    }

    test("العقد: ترتيب الأولوية مصمم — أقوى سبب يُذكر أولًا في كل زوج") {
        // التخفي يسبق كلمة المرور
        DrsEmojiHistoryGate.decide(true, true, true, false, true, false) shouldBe
            DrsEmojiHistoryGate.Verdict.Refuse(DrsEmojiHistoryGate.Refusal.PRIVATE_SESSION)
        // كلمة المرور تسبق المحرر الخام
        DrsEmojiHistoryGate.decide(true, false, true, true, true, false) shouldBe
            DrsEmojiHistoryGate.Verdict.Refuse(DrsEmojiHistoryGate.Refusal.CREDENTIALS_FIELD)
        // المحرر الخام يسبق عطْل التكوين
        DrsEmojiHistoryGate.decide(true, false, false, true, false, false) shouldBe
            DrsEmojiHistoryGate.Verdict.Refuse(DrsEmojiHistoryGate.Refusal.RAW_EDITOR)
        // عطْل التكوين يسبق القفل
        DrsEmojiHistoryGate.decide(true, false, false, false, false, true) shouldBe
            DrsEmojiHistoryGate.Verdict.Refuse(DrsEmojiHistoryGate.Refusal.NON_COMPOSING_FIELD)
        // عطْل السجل يسبق كل شيء
        DrsEmojiHistoryGate.decide(false, true, true, true, false, true) shouldBe
            DrsEmojiHistoryGate.Verdict.Refuse(DrsEmojiHistoryGate.Refusal.HISTORY_DISABLED)
    }

    test("العقد: الجدول الشامل 64 تركيبة — سجل واحد بالضبط وتوزيع أسباب توافقي") {
        var records = 0
        val reasonCounts = mutableMapOf<DrsEmojiHistoryGate.Refusal, Int>()
        for (enabled in listOf(false, true)) {
            for (incognito in listOf(false, true)) {
                for (password in listOf(false, true)) {
                    for (raw in listOf(false, true)) {
                        for (composing in listOf(false, true)) {
                            for (locked in listOf(false, true)) {
                                when (val v = DrsEmojiHistoryGate.decide(
                                    enabled, incognito, password, raw, composing, locked,
                                )) {
                                    DrsEmojiHistoryGate.Verdict.Record -> records++
                                    is DrsEmojiHistoryGate.Verdict.Refuse ->
                                        reasonCounts.merge(v.reason, 1, Int::plus)
                                }
                            }
                        }
                    }
                }
            }
        }
        records shouldBe 1
        // 2^5 = 32 تركيبة بعطْل السجل تُرفض كلها بسببه الأول
        reasonCounts[DrsEmojiHistoryGate.Refusal.HISTORY_DISABLED] shouldBe 32
        // ثم 16 تخفي، 8 كلمة مرور، 4 محرر خام، 2 بلا تكوين، 1 قفل
        reasonCounts[DrsEmojiHistoryGate.Refusal.PRIVATE_SESSION] shouldBe 16
        reasonCounts[DrsEmojiHistoryGate.Refusal.CREDENTIALS_FIELD] shouldBe 8
        reasonCounts[DrsEmojiHistoryGate.Refusal.RAW_EDITOR] shouldBe 4
        reasonCounts[DrsEmojiHistoryGate.Refusal.NON_COMPOSING_FIELD] shouldBe 2
        reasonCounts[DrsEmojiHistoryGate.Refusal.DEVICE_LOCKED] shouldBe 1
        reasonCounts.values.sum() shouldBe 63
    }

    // ------------------------------------------------------------------
    // الحلقة الكاملة — المساعد على نموذج تفضيلات حقيقي في الذاكرة
    // ------------------------------------------------------------------

    fun emojiOf(value: String) = Emoji(value, "name_$value", emptyList())

    fun cleanContext() = DrsEmojiHistoryGate.RecordContext(
        isIncognitoMode = false,
        isPasswordVariation = false,
        isRawInputEditor = false,
        isComposingEnabled = true,
        isDeviceLocked = false,
    )

    test("الحلقة الكاملة: التقاط في سياق سليم ينمو بالمؤخر ويمضي الأقدم") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        val a = emojiOf("👍"); val b = emojiOf("🔥"); val c = emojiOf("✨")
        EmojiHistoryHelper.markEmojiUsed(prefs, a, cleanContext())
        EmojiHistoryHelper.markEmojiUsed(prefs, b, cleanContext())
        EmojiHistoryHelper.markEmojiUsed(prefs, c, cleanContext())
        // AUTO_SORT_PREPEND الافتراضي: الأحدث أولًا
        prefs.emoji.historyData.get().recent.map { it.value } shouldContainExactly
            listOf("✨", "🔥", "👍")
        prefs.emoji.historyData.get().pinned shouldHaveSize 0
    }

    test("الحلقة الكاملة: إعادة الالتقاط تُقدّم ولا تُكرر") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        val a = emojiOf("👍"); val b = emojiOf("🔥")
        EmojiHistoryHelper.markEmojiUsed(prefs, a, cleanContext())
        EmojiHistoryHelper.markEmojiUsed(prefs, b, cleanContext())
        EmojiHistoryHelper.markEmojiUsed(prefs, a, cleanContext())
        prefs.emoji.historyData.get().recent.map { it.value } shouldContainExactly
            listOf("👍", "🔥")
    }

    test("الحلقة الكاملة: كل سياق رفض يترك السجل فارغًا كما وُلد") {
        val contexts = listOf(
            "تخفي" to DrsEmojiHistoryGate.RecordContext(true, false, false, true, false),
            "كلمة مرور" to DrsEmojiHistoryGate.RecordContext(false, true, false, true, false),
            "محرر خام" to DrsEmojiHistoryGate.RecordContext(false, false, true, true, false),
            "بلا تكوين" to DrsEmojiHistoryGate.RecordContext(false, false, false, false, false),
            "قفل شاشي" to DrsEmojiHistoryGate.RecordContext(false, false, false, true, true),
        )
        for ((label, ctx) in contexts) {
            val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
            withClue("السياق المرفوض: $label") {
                EmojiHistoryHelper.markEmojiUsed(prefs, emojiOf("👍"), ctx)
                prefs.emoji.historyData.get() shouldBe EmojiHistory.Empty
            }
        }
    }

    test("الحلقة الكاملة: عطْل السجل من الإعدادات يمنع حتى السياق السليم") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        prefs.emoji.historyEnabled.set(false)
        EmojiHistoryHelper.markEmojiUsed(prefs, emojiOf("👍"), cleanContext())
        prefs.emoji.historyData.get() shouldBe EmojiHistory.Empty
    }

    test("الحلقة الكاملة: سقف المؤخر يظل محترمًا في السياق السليم (لا انحدار)") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        prefs.emoji.historyRecentMaxSize.set(2)
        EmojiHistoryHelper.markEmojiUsed(prefs, emojiOf("👍"), cleanContext())
        EmojiHistoryHelper.markEmojiUsed(prefs, emojiOf("🔥"), cleanContext())
        EmojiHistoryHelper.markEmojiUsed(prefs, emojiOf("✨"), cleanContext())
        prefs.emoji.historyData.get().recent.map { it.value } shouldContainExactly
            listOf("✨", "🔥")
    }

    test("الحلقة الكاملة: الإدارة لا تُحكم بالبوابة — تثبيت وإزالة يعملان دائمًا") {
        val prefs by jetprefDataStoreOf(DrsPreferenceModel::class)
        val a = emojiOf("👍")
        // بذرة سجل بسياق سليم
        EmojiHistoryHelper.markEmojiUsed(prefs, a, cleanContext())
        // ثم جلسة تخفي: الالتقاط مرفوض…
        EmojiHistoryHelper.markEmojiUsed(
            prefs, emojiOf("🔥"),
            DrsEmojiHistoryGate.RecordContext(true, false, false, true, false),
        )
        prefs.emoji.historyData.get().recent.map { it.value } shouldContainExactly listOf("👍")
        // …لكن الإدارة على ما هو محفوظ سلفًا تعمل: التثبيت ينقل من المؤخر للمثبت
        EmojiHistoryHelper.pinEmoji(prefs, a)
        prefs.emoji.historyData.get().pinned.map { it.value } shouldContainExactly listOf("👍")
        prefs.emoji.historyData.get().recent shouldHaveSize 0
        EmojiHistoryHelper.unpinEmoji(prefs, a)
        prefs.emoji.historyData.get().recent.map { it.value } shouldContainExactly listOf("👍")
    }
})
