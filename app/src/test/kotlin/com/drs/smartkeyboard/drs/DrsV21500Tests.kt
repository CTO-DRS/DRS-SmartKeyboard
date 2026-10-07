/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.clipboard.ClipEditNotificationPolicy
import com.drs.smartkeyboard.ime.clipboard.DrsClipboardCaptureGate
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

/**
 * DRS v2.15.0 «ذاكرة الحافظة الأمينة» — عقود بوابة التقاط الحافظة:
 * التخزين الدائم في السياقات الخاصة (تخفي، كلمة مرور، مقص حساس، قفل
 * شاشي) يُرفض رفضًا صامتًا حتميًا، والعرض (اللصق ورقاقة الاقتراح) لا
 * يُحكم. الجدول الشامل (32 تركيبة) يثبت أن الالتقاط يحدث في حالة واحدة
 * بالضبط، وتوزيع أسباب الرفض يطابق الحسبة التوافقية المتوقعة — بلا
 * ترتيب فحص مكسور. وعقود الترجمة (storageRefused / allowsMediaClone)
 * تثبت أن دلالات إرجاع مسار التخزين التراثية محفوظة حرفيًا (عقد
 * اللا-انحدار مع S-1)، وأن إشعار «تعديل» لا يعلن أبدًا التقاطًا مرفوضًا.
 */
class DrsV21500Tests : FunSpec({

    // ------------------------------------------------------------------
    // البوابة النقية — عقود الحكم الفردي
    // ------------------------------------------------------------------

    test("العقد: سياق سليم كليًا والسجل مفعّل → التقط") {
        DrsClipboardCaptureGate.decide(
            historyEnabled = true,
            isIncognitoMode = false,
            isPasswordVariation = false,
            isSensitiveClip = false,
            isDeviceLocked = false,
        ) shouldBe DrsClipboardCaptureGate.Verdict.Capture
    }

    test("العقد: كل سبب رفض منفردًا يُذكر باسمه الصريح") {
        fun verdict(
            enabled: Boolean = true,
            incognito: Boolean = false,
            password: Boolean = false,
            sensitive: Boolean = false,
            locked: Boolean = false,
        ) = DrsClipboardCaptureGate.decide(
            historyEnabled = enabled,
            isIncognitoMode = incognito,
            isPasswordVariation = password,
            isSensitiveClip = sensitive,
            isDeviceLocked = locked,
        )

        verdict(incognito = true) shouldBe
            DrsClipboardCaptureGate.Verdict.Refuse(DrsClipboardCaptureGate.Refusal.PRIVATE_SESSION)
        verdict(password = true) shouldBe
            DrsClipboardCaptureGate.Verdict.Refuse(DrsClipboardCaptureGate.Refusal.CREDENTIALS_FIELD)
        verdict(sensitive = true) shouldBe
            DrsClipboardCaptureGate.Verdict.Refuse(DrsClipboardCaptureGate.Refusal.SENSITIVE_CLIP)
        verdict(locked = true) shouldBe
            DrsClipboardCaptureGate.Verdict.Refuse(DrsClipboardCaptureGate.Refusal.DEVICE_LOCKED)
        verdict(enabled = false) shouldBe
            DrsClipboardCaptureGate.Verdict.Refuse(DrsClipboardCaptureGate.Refusal.HISTORY_DISABLED)
    }

    test("العقد: ترتيب الأولوية مصمم — سلّم الحماية في كل زوج متجاور") {
        // التخفي يسبق كلمة المرور (الجلسة قبل الحقل)
        DrsClipboardCaptureGate.decide(true, true, true, false, false) shouldBe
            DrsClipboardCaptureGate.Verdict.Refuse(DrsClipboardCaptureGate.Refusal.PRIVATE_SESSION)
        // كلمة المرور تسبق الحساس (الحقل قبل المحتوى)
        DrsClipboardCaptureGate.decide(true, false, true, true, false) shouldBe
            DrsClipboardCaptureGate.Verdict.Refuse(DrsClipboardCaptureGate.Refusal.CREDENTIALS_FIELD)
        // الحساس يسبق القفل (المحتوى قبل البيئة)
        DrsClipboardCaptureGate.decide(true, false, false, true, true) shouldBe
            DrsClipboardCaptureGate.Verdict.Refuse(DrsClipboardCaptureGate.Refusal.SENSITIVE_CLIP)
        // القفل يسبق عطْل السجل (البيئة قبل الميزة)
        DrsClipboardCaptureGate.decide(false, false, false, false, true) shouldBe
            DrsClipboardCaptureGate.Verdict.Refuse(DrsClipboardCaptureGate.Refusal.DEVICE_LOCKED)
    }

    test("العقد: الجدول الشامل 32 تركيبة — التقط واحد بالضبط وتوزيع أسباب توافقي") {
        var captures = 0
        val reasonCounts = mutableMapOf<DrsClipboardCaptureGate.Refusal, Int>()
        for (enabled in listOf(false, true)) {
            for (incognito in listOf(false, true)) {
                for (password in listOf(false, true)) {
                    for (sensitive in listOf(false, true)) {
                        for (locked in listOf(false, true)) {
                            when (val v = DrsClipboardCaptureGate.decide(
                                enabled, incognito, password, sensitive, locked,
                            )) {
                                DrsClipboardCaptureGate.Verdict.Capture -> captures++
                                is DrsClipboardCaptureGate.Verdict.Refuse ->
                                    reasonCounts.merge(v.reason, 1, Int::plus)
                            }
                        }
                    }
                }
            }
        }
        captures shouldBe 1
        // 2^4 = 16 تركيبة بالتخفي تُرفض كلها بسببه الأول
        reasonCounts[DrsClipboardCaptureGate.Refusal.PRIVATE_SESSION] shouldBe 16
        // ثم 8 كلمة مرور، 4 حساس، 2 قفل، 1 عطْل سجل
        reasonCounts[DrsClipboardCaptureGate.Refusal.CREDENTIALS_FIELD] shouldBe 8
        reasonCounts[DrsClipboardCaptureGate.Refusal.SENSITIVE_CLIP] shouldBe 4
        reasonCounts[DrsClipboardCaptureGate.Refusal.DEVICE_LOCKED] shouldBe 2
        reasonCounts[DrsClipboardCaptureGate.Refusal.HISTORY_DISABLED] shouldBe 1
        reasonCounts.values.sum() shouldBe 31
    }

    // ------------------------------------------------------------------
    // عقود الترجمة — دلالات الإرجاع التراثية محفوظة حرفيًا
    // ------------------------------------------------------------------

    test("الترجمة: storageRefused — أسباب الخصوصية الأربعة ترفض التخزين وعطْل السجل لا") {
        // كل أسباب الخصوصية (بما فيها القفل الجديد) → false من المُدرج
        for (reason in listOf(
            DrsClipboardCaptureGate.Refusal.PRIVATE_SESSION,
            DrsClipboardCaptureGate.Refusal.CREDENTIALS_FIELD,
            DrsClipboardCaptureGate.Refusal.SENSITIVE_CLIP,
            DrsClipboardCaptureGate.Refusal.DEVICE_LOCKED,
        )) {
            withClue("السبب: $reason") {
                DrsClipboardCaptureGate.storageRefused(
                    DrsClipboardCaptureGate.Verdict.Refuse(reason),
                ).shouldBeTrue()
            }
        }
        // عطْل السجل: لا شيء خُزِن ولا شيء يُنظَّف — عقد اللا-انحدار مع S-1
        DrsClipboardCaptureGate.storageRefused(
            DrsClipboardCaptureGate.Verdict.Refuse(DrsClipboardCaptureGate.Refusal.HISTORY_DISABLED),
        ).shouldBeFalse()
        // الالتقاط السليم: تخزين طبيعي
        DrsClipboardCaptureGate.storageRefused(DrsClipboardCaptureGate.Verdict.Capture).shouldBeFalse()
    }

    test("الترجمة: allowsMediaClone — الاستنساخ كتابة دائمة فلا يقع تحت رفض خصوصي") {
        // عطْل السجل وحده يجيز الاستنساخ (ظهر المقص الحي — سلوك v1.x الموروث)
        DrsClipboardCaptureGate.allowsMediaClone(
            DrsClipboardCaptureGate.Verdict.Refuse(DrsClipboardCaptureGate.Refusal.HISTORY_DISABLED),
        ).shouldBeTrue()
        DrsClipboardCaptureGate.allowsMediaClone(DrsClipboardCaptureGate.Verdict.Capture).shouldBeTrue()
        // كل رفض خصوصي يمنع الاستنساخ — لا صف مزود ولا ملف ظهر
        for (reason in listOf(
            DrsClipboardCaptureGate.Refusal.PRIVATE_SESSION,
            DrsClipboardCaptureGate.Refusal.CREDENTIALS_FIELD,
            DrsClipboardCaptureGate.Refusal.SENSITIVE_CLIP,
            DrsClipboardCaptureGate.Refusal.DEVICE_LOCKED,
        )) {
            withClue("السبب: $reason") {
                DrsClipboardCaptureGate.allowsMediaClone(
                    DrsClipboardCaptureGate.Verdict.Refuse(reason),
                ).shouldBeFalse()
            }
        }
    }

    test("الترجمة: الجدول الشامل عبر الترجمتين — 30 رفض تخزين و2 إذن، و16 رفض استنساخ و16 إذنًا") {
        var storageRefusals = 0
        var cloneAllowances = 0
        for (enabled in listOf(false, true)) {
            for (incognito in listOf(false, true)) {
                for (password in listOf(false, true)) {
                    for (sensitive in listOf(false, true)) {
                        for (locked in listOf(false, true)) {
                            val v = DrsClipboardCaptureGate.decide(
                                enabled, incognito, password, sensitive, locked,
                            )
                            if (DrsClipboardCaptureGate.storageRefused(v)) storageRefusals++
                            if (DrsClipboardCaptureGate.allowsMediaClone(v)) cloneAllowances++
                        }
                    }
                }
            }
        }
        // التخزين: 31 رفضًا (16+8+4+2+1) ناقص عطْل السجل المسموح = 30 رفضًا فعليًا
        storageRefusals shouldBe 30
        // الاستنساخ: كل رفض خصوصي (16+8+4+2=30) يمنعه، والباقي (2) يجوزه
        cloneAllowances shouldBe 2
    }

    // ------------------------------------------------------------------
    // سياسة الإشعار — التقاط مرفوض لا يُعلن ولا يُسرَّب
    // ------------------------------------------------------------------

    test("السياسة: التقاط المرفوض لا يُعلن مهما كانت كل الشاشات الأخرى خضراء") {
        ClipEditNotificationPolicy.shouldNotify(
            captureAccepted = false,
            prefEnabled = true,
            historyEnabled = true,
            type = ItemType.TEXT,
            text = "نص منسوخ في جلسة تخفي — كان يُسرَّب قبل v2.15.0",
            isSensitive = false,
        ).shouldBeFalse()
    }

    test("السياسة: الالتقاط المقبول يحتفظ بسلوك التراث كاملًا (لا انحدار)") {
        ClipEditNotificationPolicy.shouldNotify(
            captureAccepted = true,
            prefEnabled = true,
            historyEnabled = true,
            type = ItemType.TEXT,
            text = "مرحبا",
            isSensitive = false,
        ).shouldBeTrue()
        // عطْل السجل مع التقاط مقبول (HISTORY_DISABLED يعيد true للمُدرج):
        // السياسة نفسها ترفض — لا إشعار بلا ذاكرة خلفه
        ClipEditNotificationPolicy.shouldNotify(
            captureAccepted = true,
            prefEnabled = true,
            historyEnabled = false,
            type = ItemType.TEXT,
            text = "مرحبا",
            isSensitive = false,
        ).shouldBeFalse()
    }

    test("الحلقة الكاملة: البوابة والسياسة معًا — لا إشعار إلا للالتقاط الوحيد المسموح") {
        // الحلقة: حكم البوابة → دلالة الإرجاع → سياسة الإشعار (كل الشاشات مفعّلة
        // والعنصر نصي غير حساس غير فارغ — العوامل الوحيدة الفاصلة هي البوابة والسجل)
        var notifiedRows = 0
        for (enabled in listOf(false, true)) {
            for (incognito in listOf(false, true)) {
                for (password in listOf(false, true)) {
                    for (sensitive in listOf(false, true)) {
                        for (locked in listOf(false, true)) {
                            val verdict = DrsClipboardCaptureGate.decide(
                                enabled, incognito, password, sensitive, locked,
                            )
                            val captureAccepted = !DrsClipboardCaptureGate.storageRefused(verdict)
                            val notified = ClipEditNotificationPolicy.shouldNotify(
                                captureAccepted = captureAccepted,
                                prefEnabled = true,
                                historyEnabled = enabled,
                                type = ItemType.TEXT,
                                text = "نص",
                                isSensitive = sensitive,
                            )
                            if (notified) notifiedRows++
                        }
                    }
                }
            }
        }
        // صف واحد بالضبط يُعلن: السياق السليم كليًا والسجل مفعّل —
        // وصف عطْل السجل (الإرجاع true) ترفضه السياسة بلا ذاكرة خلف الإشعار
        notifiedRows shouldBe 1
    }

    // ------------------------------------------------------------------
    // سياق الالتقاط — لا قيم افتراضية فلا بناء صامل يفتح باب الالتقاط
    // ------------------------------------------------------------------

    test("العقد: CaptureContext بلا قيم افتراضية — البناء الصامل مستحيل") {
        val ctx = DrsClipboardCaptureGate.CaptureContext(
            isIncognitoMode = true,
            isPasswordVariation = false,
            isDeviceLocked = true,
        )
        // حقائق البيئة تصل البوابة كما بُنيت — لا تحويل ولا افتراض
        val verdict = DrsClipboardCaptureGate.decide(
            historyEnabled = true,
            isIncognitoMode = ctx.isIncognitoMode,
            isPasswordVariation = ctx.isPasswordVariation,
            isSensitiveClip = false,
            isDeviceLocked = ctx.isDeviceLocked,
        )
        verdict shouldBe DrsClipboardCaptureGate.Verdict.Refuse(DrsClipboardCaptureGate.Refusal.PRIVATE_SESSION)
    }
})
