/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/**
 * DRS v2.22.0 «الأنظمة المتكاملة لكل ميزة» — عقود قاموسي التشكيلي النقية
 * وإتمام نسيج العدادات لكل ميزة:
 *
 *  - عقد القاموس الشخصي المغلق: كلمة مجردة + تشكيل يجرّد إلى الكلمة
 *    بالضبط (نفس عقد الملفات المرجعية)، الرفض باسمه لا ترقيع.
 *  - «المستخدم تفوز»: كلمة معلَّمة تجيب قبل البذرة والأصل الثلاثي —
 *    تجاوز صريح بيد المستخدم لا خمّن، والغائب يعود صادقًا null.
 *  - إعادة التعليم تُحدّث لا تُوَلّد توأمًا (قاموس لا سجل)، والحد
 *    200 يرفض فشل-مغلق بلا إخلاء صامت.
 *  - إتمام العدادات: myTextsUses كان يُكتب مدى الحياة مُتجاوزًا نقطة
 *    الاستنزاف الوحيدة فلا تراه دوالي اليوم أبدًا — العقد الجديد يثبت
 *    أن mergeInto/sum/hasActivity تحسب المجموعة المغلقة كاملة.
 *  - فك الحالة القديمة: حقول قاموسي الغائبة تفك إلى افتراضاتها.
 */
class DrsV22200Tests : FunSpec({

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun stateWith(
        entries: List<DrsMyLexiconEntry> = emptyList(),
        nextId: Long = (entries.maxOfOrNull { it.id } ?: 0L) + 1,
        enabled: Boolean = true,
    ): DrsState = DrsState(myLexicon = entries, nextMyLexiconId = nextId, myLexiconEnabled = enabled)

    fun entry(
        id: Long,
        word: String,
        vocalized: String,
        pinned: Boolean = false,
        updatedAtMs: Long = 0,
    ) = DrsMyLexiconEntry(
        id = id,
        word = word,
        vocalized = vocalized,
        pinned = pinned,
        createdAtMs = updatedAtMs,
        updatedAtMs = updatedAtMs,
    )

    // ------------------------------------------------------------------
    // عقد التحقق المغلق
    // ------------------------------------------------------------------

    test("العقد: الكلمة الفارغة أو التشكيل الفارغ يُرفضان باسمهما") {
        DrsMyLexicon.validate("", "") shouldBe DrsMyLexicon.Error.EMPTY_WORD
        DrsMyLexicon.validate("   ", "مِنْ") shouldBe DrsMyLexicon.Error.EMPTY_WORD
        DrsMyLexicon.validate("من", "") shouldBe DrsMyLexicon.Error.EMPTY_VOCALIZED
        DrsMyLexicon.validate("من", "   ") shouldBe DrsMyLexicon.Error.EMPTY_VOCALIZED
    }

    test("العقد: حدّا الطول حرفيان بلا تسامح") {
        DrsMyLexicon.validate("ا".repeat(33), "ا".repeat(33)) shouldBe DrsMyLexicon.Error.WORD_TOO_LONG
        DrsMyLexicon.validate("ا".repeat(32), "ا".repeat(32)) shouldBe null
        DrsMyLexicon.validate("ا".repeat(5), "ا".repeat(5) + "ـ".repeat(43)) shouldBe null
        // الأصل 49 حرفًا يُرفض بالطول قبل فحص التطابق.
        DrsMyLexicon.validate("ا".repeat(5), "ا".repeat(5) + "ـ".repeat(44)) shouldBe DrsMyLexicon.Error.VOCALIZED_TOO_LONG
    }

    test("العقد: حقل الكلمة لا يقبل حركة ولا تطويل — المفتاح هو المجرد") {
        DrsMyLexicon.validate("مُنْ", "مُنْ") shouldBe DrsMyLexicon.Error.WORD_HAS_MARKS
        DrsMyLexicon.validate("منـ", "منـ") shouldBe DrsMyLexicon.Error.WORD_HAS_MARKS
    }

    test("العقد: التشكيل يجرّد إلى الكلمة بالضبط — نفس عقد الملفات المرجعية") {
        DrsMyLexicon.validate("من", "مُنْ") shouldBe null
        DrsMyLexicon.validate("كتاب", "كِتَاب") shouldBe null
        DrsMyLexicon.validate("كتاب", "كُتُب") shouldBe DrsMyLexicon.Error.VOCALIZED_MISMATCH
        DrsMyLexicon.validate("من", "مِيْ") shouldBe DrsMyLexicon.Error.VOCALIZED_MISMATCH
    }

    // ------------------------------------------------------------------
    // الإضافة وإعادة التعليم
    // ------------------------------------------------------------------

    test("الإضافة: معرّف تسلسلي لا يقفز، والتوقيتان تُثبتان") {
        val s = stateWith()
        val r = DrsMyLexicon.addAt(s, "كتاب", "كِتَاب", nowMs = 100)
        r.shouldBeInstanceOf<DrsMyLexicon.Result.Accepted>()
        r.state.myLexicon.single().id shouldBe 1L
        r.state.nextMyLexiconId shouldBe 2L
        r.state.myLexicon.single().createdAtMs shouldBe 100L
        r.state.myLexicon.single().updatedAtMs shouldBe 100L
    }

    test("إعادة التعليم: الكلمة نفسها تُحدَّث في مكانها — لا توأم ولا معرّف جديد") {
        val s = stateWith(entries = listOf(entry(7, "من", "مِنْ", updatedAtMs = 10)), nextId = 8)
        val r = DrsMyLexicon.addAt(s, "من", "مُنْ", nowMs = 99)
        r.shouldBeInstanceOf<DrsMyLexicon.Result.Accepted>()
        r.state.myLexicon.size shouldBe 1
        r.state.myLexicon.single().id shouldBe 7L
        r.state.myLexicon.single().vocalized shouldBe "مُنْ"
        r.state.myLexicon.single().updatedAtMs shouldBe 99L
        r.state.nextMyLexiconId shouldBe 8L
    }

    test("الحد 200 يرفض فشل-مغلق — المعرفة المعلَّمة لا تُدَمَّر خلف ظهر المستخدم") {
        val full = stateWith(
            entries = (1..DrsMyLexicon.MAX_ITEMS).map { entry(it.toLong(), "w$it", "w$it") },
            nextId = (DrsMyLexicon.MAX_ITEMS + 1).toLong(),
        )
        val r = DrsMyLexicon.addAt(full, "جديد", "جَدِيد", nowMs = 1)
        r.shouldBeInstanceOf<DrsMyLexicon.Result.Rejected>()
        r.error shouldBe DrsMyLexicon.Error.FULL
        // إعادة تعليم كلمة موجودة داخل الحد تظل تجيب — تحديث لا إدراج.
        DrsMyLexicon.addAt(full, "w1", "w1ّ", nowMs = 1)
            .shouldBeInstanceOf<DrsMyLexicon.Result.Accepted>()
    }

    // ------------------------------------------------------------------
    // التعديل والحذف والتثبيت
    // ------------------------------------------------------------------

    test("التعديل: معرّف غائب عمل صامت صادق، والملكية تبقى لمعرّفها") {
        val s = stateWith(entries = listOf(entry(1, "من", "مِنْ")))
        val r = DrsMyLexicon.editAt(s, 99, "كتاب", "كِتَاب", nowMs = 5)
        r.shouldBeInstanceOf<DrsMyLexicon.Result.Accepted>()
        r.state.myLexicon.size shouldBe 1
        r.state.myLexicon.single().word shouldBe "من"
    }

    test("التعديل: إعادة تسمية على مفتاح مملوك لغيره تُرفض — لا مفتاحان لمدخلين") {
        val s = stateWith(
            entries = listOf(entry(1, "من", "مِنْ"), entry(2, "في", "فِي")),
        )
        val r = DrsMyLexicon.editAt(s, 2, "من", "مُنْ", nowMs = 5)
        r.shouldBeInstanceOf<DrsMyLexicon.Result.Rejected>()
        r.error shouldBe DrsMyLexicon.Error.DUPLICATE_WORD
        // التسمية على مفتاح صاحبها نفسه تجيب (لا تكرار حدث).
        DrsMyLexicon.editAt(s, 1, "من", "مُنْ", nowMs = 5)
            .shouldBeInstanceOf<DrsMyLexicon.Result.Accepted>()
    }

    test("الحذف والإزالة الشاملة والتثبيت: عمليات حتمية بلا مفاجآت") {
        val s = stateWith(
            entries = listOf(entry(1, "من", "مِنْ", pinned = true), entry(2, "في", "فِي")),
        )
        DrsMyLexicon.removeAt(s, 99).myLexicon.size shouldBe 2
        DrsMyLexicon.removeAt(s, 1).myLexicon.map { it.id } shouldBe listOf(2L)
        DrsMyLexicon.removeAllAt(s).myLexicon.isEmpty() shouldBe true
        val toggled = DrsMyLexicon.togglePinAt(s, 2)
        toggled.myLexicon.first { it.id == 2L }.pinned shouldBe true
        toggled.myLexicon.first { it.id == 1L }.pinned shouldBe true
    }

    // ------------------------------------------------------------------
    // الترتيب والبحث
    // ------------------------------------------------------------------

    test("الترتيب: المثبت أولًا ثم الأبجدي، وكسر التعادل بالمعرّف الأعلى") {
        val ordered = DrsMyLexicon.order(
            listOf(
                entry(1, "كتاب", "كِتَاب"),
                entry(2, "أكل", "أَكَلَ"),
                entry(3, "باب", "بَابٌ", pinned = true),
                entry(4, "أكل", "أَكَلْتُ"),
            ),
        )
        // المثبت أولًا، ثم الأبجدي (أكل قبل كتاب)، وتعادل الكلمة
        // الواحدة يكسره المعرّف الأعلى (4 قبل 2).
        ordered.map { it.id } shouldBe listOf(3L, 4L, 2L, 1L)
    }

    test("البحث: حرفي بلا تطبيع يخفي، فوق الكلمة والتشكيل معًا") {
        val entries = listOf(entry(1, "كتاب", "كِتَاب"), entry(2, "في", "فِي"))
        DrsMyLexicon.search(entries, "").size shouldBe 2
        DrsMyLexicon.search(entries, "كت").map { it.id } shouldBe listOf(1L)
        DrsMyLexicon.search(entries, "فِي").map { it.id } shouldBe listOf(2L)
        DrsMyLexicon.search(entries, "غائب").isEmpty() shouldBe true
    }

    // ------------------------------------------------------------------
    // «المستخدم تفوز» — نقطة الدمج
    // ------------------------------------------------------------------

    test("overridesOf: المفعّل يعطي الخريطة والموقوف يعطي الفراغ") {
        val s = stateWith(entries = listOf(entry(1, "من", "مُنْ")))
        DrsMyLexicon.overridesOf(s) shouldBe mapOf("من" to "مُنْ")
        DrsMyLexicon.overridesOf(s.copy(myLexiconEnabled = false)) shouldBe emptyMap()
    }

    test("«المستخدم تفوز»: المعلَّم يجيب قبل البذرة، والغائب عن المراجع يُعلَّم فيجيب") {
        // «من» في البذرة = مِنْ — التعليم يتجاوزها صراحة.
        val overrides = mapOf("من" to "مُنْ")
        DrsWordTashkeel.vocalize("من", overrides) shouldBe "مُنْ"
        DrsWordTashkeel.vocalize("من") shouldBe "مِنْ"
        // كلمة لا تعرفها البذرة ولا الأصل: بلا تعليم null صادق، وبعده تجيب.
        // التطابق على المجرد: كلمة مشكَّلة تُجرَّد ثم يجيب المعلَّم.
        DrsWordTashkeel.vocalize("مُنْ", overrides) shouldBe "مُنْ"
    }

    // ------------------------------------------------------------------
    // إتمام نسيج العدادات — تسرب myTextsUses ما ضاع بعد اليوم
    // ------------------------------------------------------------------

    test("mergeInto: المجموعة المغلقة الست للأنظمة تصل دوالي اليوم كاملة") {
        val delta = DrsUsageStats(
            myTextsUses = 3,
            myLexiconUses = 2,
            harakatUses = 7,
            symbolUses = 5,
            letterUses = 4,
            numberUses = 9,
        )
        val merged = DrsDailyStats.mergeInto(emptyMap(), delta)
        val today = merged[DrsDailyStats.todayStamp()]!!
        today.myTextsUses shouldBe 3L
        today.myLexiconUses shouldBe 2L
        today.harakatUses shouldBe 7L
        today.symbolUses shouldBe 5L
        today.letterUses shouldBe 4L
        today.numberUses shouldBe 9L
        // دلتا صفرية كليةً لا تُنشئ يومًا (عقد isZero القائم يمتد للجديد).
        DrsDailyStats.mergeInto(emptyMap(), DrsUsageStats()).isEmpty() shouldBe true
    }

    test("sum وhasActivity: العدادات الست تُجمع وتُعتبر نشاطًا حقيقيًا") {
        val b1 = DrsDayStats(day = "2026-10-08", myTextsUses = 1, harakatUses = 2)
        val b2 = DrsDayStats(day = "2026-10-09", myLexiconUses = 3, symbolUses = 4, letterUses = 5, numberUses = 6)
        val total = DrsDailyStats.sum(listOf(b1, b2))
        total.myTextsUses shouldBe 1L
        total.myLexiconUses shouldBe 3L
        total.harakatUses shouldBe 2L
        total.symbolUses shouldBe 4L
        total.letterUses shouldBe 5L
        total.numberUses shouldBe 6L
        with(DrsDailyStats) {
            b1.hasActivity() shouldBe true
            b2.hasActivity() shouldBe true
            DrsDayStats(day = "2026-10-07", myLexiconUses = 1).hasActivity() shouldBe true
            DrsDayStats(day = "2026-10-06", numberUses = 1).hasActivity() shouldBe true
            DrsDayStats(day = "2026-10-05").hasActivity() shouldBe false
        }
    }

    // ------------------------------------------------------------------
    // فك الحالة القديمة
    // ------------------------------------------------------------------

    test("فك حالة قديمة بلا حقول قاموسي: افتراضات صامتة آمنة") {
        val legacy = """{"version":1,"userPath":"NORMAL","onboardingDone":true}"""
        val decoded = json.decodeFromString(DrsState.serializer(), legacy)
        decoded.myLexicon.isEmpty() shouldBe true
        decoded.nextMyLexiconId shouldBe 1L
        decoded.myLexiconEnabled shouldBe true
        decoded.usage.myTextsUses shouldBe 0L
        decoded.usage.harakatUses shouldBe 0L
    }

    test("النسخ الاحتياطي بنيويًا: قاموسي جزء من الحالة الواحدة يُرمَّز ويُفك") {
        val s = stateWith(entries = listOf(entry(1, "من", "مُنْ"), entry(2, "كتاب", "كِتَاب")))
        val encoded = json.encodeToString(DrsState.serializer(), s)
        val decoded = json.decodeFromString(DrsState.serializer(), encoded)
        decoded.myLexicon.size shouldBe 2
        decoded.myLexicon[0].vocalized shouldBe "مُنْ"
        decoded.myLexiconEnabled shouldBe true
    }
})
