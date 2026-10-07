/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.drs.ai.DrsWritingAssistant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain

/**
 * DRS v2.16.0 «المساعد الكتابي الصادق» — عقود إحياء محرك M1.5 اليتيم عبر
 * أدوات النص: أوردة الربط الثلاثة مثبتة اختبارًا —
 *
 *  1. **الإحالة الحرفية:** الأداتان الجديدتان (IMPROVE_PHRASES -674 و
 *     NORMALIZE_SPACING -675) تفوضان حرفيًا إلى الدالتين العامتين للمحرك
 *     (improveTail / normalizeSpacing) — ومِنَ الأعلى عبر DrsTextTools.apply
 *     كما كل الأدوات، فيسري عليها مسار المحرر والتحديد مجانًا.
 *  2. **عقد الإطلاق محفوظ بلا تخفيف:** إعادة الصياغة تنطلق على ذيل النص
 *     كامل التطابق بعد التطبيع فقط — الاحتواء مرفوض (حالة الفساد التاريخية
 *     «انشاء اللهية» تبقى حرفيًا)، وما لا يُطلق يعود بايتيًا.
 *  3. **الترشيح مرآة العقد:** المستشار السياقي يقترح IMPROVE_PHRASES فقط
 *     عندما تكون الأداة ستُطلق فعلًا على الذيل — توصية بنتيجة مضمونة
 *     حتميًا، والصمت صادق حيث لا إطلاق.
 *
 * والثبات (idempotence) مثبت عبر apply للأداتين: تطبيق مرتين يساوي مرة —
 * فلا نتيجة معاد عرضها تكبر تحريرات إضافية أبدًا.
 */
class DrsV21600Tests : FunSpec({

    // ------------------------------------------------------------------
    // 1) الإحالة الحرفية عبر DrsTextTools.apply
    // ------------------------------------------------------------------

    test("العقد: IMPROVE_PHRASES يفوض حرفيًا إلى improveTail عبر apply") {
        val out = DrsTextTools.apply(DrsTextTool.IMPROVE_PHRASES, "وشكرًا، انشاء الله")
        out shouldBe "وشكرًا، إن شاء الله"
        out shouldBe DrsWritingAssistant.improveTail("وشكرًا، انشاء الله")
    }

    test("العقد: NORMALIZE_SPACING يفوض حرفيًا إلى normalizeSpacing عبر apply") {
        val out = DrsTextTools.apply(DrsTextTool.NORMALIZE_SPACING, "مرحبا  ؟")
        out shouldBe "مرحبا؟"
        out shouldBe DrsWritingAssistant.normalizeSpacing("مرحبا  ؟")
    }

    test("العقد: الإحالة البايتية عبر بطارية نصوص قذرة للأداتين معًا") {
        val battery = listOf(
            "انشاء الله",
            "  كيف حاك   ",
            "تمام الحمد الله ،  شكرا جزيلا  ؟؟",
            "اهلا وسهلا",
            "السلام عليكم ورحمة الله",
            "على الرحب والسعه",
            "ممكن سوال   ؟",
            "hello world",
            "نص  عربي  عادي  بلا مشغلات",
            "",
        )
        for (text in battery) {
            DrsTextTools.apply(DrsTextTool.IMPROVE_PHRASES, text) shouldBe
                DrsWritingAssistant.improveTail(text)
            DrsTextTools.apply(DrsTextTool.NORMALIZE_SPACING, text) shouldBe
                DrsWritingAssistant.normalizeSpacing(text)
        }
    }

    // ------------------------------------------------------------------
    // 2) عقد الإطلاق — الذيل كامل التطابق فقط، والاحتواء مرفوض
    // ------------------------------------------------------------------

    test("العقد: الست عشرة عبارة كلها تُطلق كأذيل عبر apply") {
        for ((trigger, replacement) in DrsWritingAssistant.PHRASES) {
            val text = "بادئة، $trigger"
            val out = DrsTextTools.apply(DrsTextTool.IMPROVE_PHRASES, text)
            // الإطلاق حتمي بترتيب PHRASES: مشغّلٌ أسبق قصارٌ قد يسبق
            // («الحمد الله» قبل «تمام الحمد الله») — الفصل النهائي
            // دائمًا للإحالة الحرفية إلى المحرك، والمطلوب هنا: أُطلق
            // شيء (تغيّر النص) والنتيجة هي قرار المحرك نفسه.
            out shouldBe DrsWritingAssistant.improveTail(text)
            out shouldNotBe text
        }
        // والحال القياسي غير الملتبس: العبارة وحدها آخر النص —
        DrsTextTools.apply(DrsTextTool.IMPROVE_PHRASES, "تمام الحمد الله") shouldBe
            "تمام الحمد لله" // «الحمد الله» الأسبق يُطلق على الذيل
        DrsTextTools.apply(DrsTextTool.IMPROVE_PHRASES, "لا شكر على واجب") shouldBe "العفو"
        DrsTextTools.apply(DrsTextTool.IMPROVE_PHRASES, "يعطيك العافيه") shouldBe "يعطيك العافية"
    }

    test("العقد: الاحتواء مرفوض — حالة الفساد التاريخية تبقى حرفيًا") {
        // «انشاء الله» داخل كلمة أطول في الذيل: لا نافذة ذيل كاملة التطابق
        DrsTextTools.apply(DrsTextTool.IMPROVE_PHRASES, "انشاء اللهية") shouldBe "انشاء اللهية"
        // والاحتواء في وسط النص لا يعني شيئًا أيضًا
        DrsTextTools.apply(DrsTextTool.IMPROVE_PHRASES, "انشاء اللهية هي الجملة كلها") shouldBe
            "انشاء اللهية هي الجملة كلها"
    }

    test("العقد: النص السليم لا يُطلق شيئًا ويعود بايتيًا") {
        val clean = "هذا نص عربي سليم لا يحمل أي عبارة من قائمة المشغلات"
        DrsTextTools.apply(DrsTextTool.IMPROVE_PHRASES, clean) shouldBe clean
    }

    // ------------------------------------------------------------------
    // 3) الثبات — تطبيق مرتين يساوي مرة (عبر apply)
    // ------------------------------------------------------------------

    test("العقد: IMPROVE_PHRASES ثابت — النتيجة ذاتها مشغل ثابت") {
        val dirty = listOf(
            "انشاء الله",
            "وشكرًا، ان شاء الله",
            "صباح الخيرات",
            "تمام الحمد الله",
        )
        for (text in dirty) {
            val once = DrsTextTools.apply(DrsTextTool.IMPROVE_PHRASES, text)
            val twice = DrsTextTools.apply(DrsTextTool.IMPROVE_PHRASES, once)
            twice shouldBe once
        }
    }

    test("العقد: NORMALIZE_SPACING ثابت — النقطة الثابتة محدودة عبر apply") {
        val dirty = listOf(
            "مرحبا  ؟",
            "كيف   حالك     ؟؟",
            "  مسافات    متراكمة  ",
            "علامة ،،، مكررة ؛؛",
            "نص نظيف بلا شيء",
        )
        for (text in dirty) {
            val once = DrsTextTools.apply(DrsTextTool.NORMALIZE_SPACING, text)
            val twice = DrsTextTools.apply(DrsTextTool.NORMALIZE_SPACING, once)
            twice shouldBe once
        }
    }

    // ------------------------------------------------------------------
    // 4) عقد جدول الأكواد — الامتداد والترميز بلا تصادم
    // ------------------------------------------------------------------

    test("العقد: CODE_RANGE امتد إلى -675 والأداتان مرمّحتان بلا تصادم") {
        DrsTextTool.CODE_RANGE shouldBe -675..-601
        DrsTextTool.fromCode(-674) shouldBe DrsTextTool.IMPROVE_PHRASES
        DrsTextTool.fromCode(-675) shouldBe DrsTextTool.NORMALIZE_SPACING
        // لا تصادم: عدد الأكواد المميزة = عدد المدخلات
        DrsTextTool.entries.map { it.code }.toSet().size shouldBe DrsTextTool.entries.size
    }

    // ------------------------------------------------------------------
    // 5) الترشيح مرآة العقد — المستشار السياقي
    // ------------------------------------------------------------------

    test("العقد: المستشار يقترح IMPROVE_PHRASES أولًا حيث الأداة ستُطلق فعلًا") {
        val picks = DrsTileContextAdvisor.advise("انشاء الله")
        picks shouldContain DrsTextTool.IMPROVE_PHRASES
        picks.first() shouldBe DrsTextTool.IMPROVE_PHRASES
    }

    test("العقد: المستشار صامت حيث لا إطلاق — الصمت هو الصدق") {
        // عربية عارية بلا مشغل عبارات: تشكيل نعم، عبارات لا
        DrsTileContextAdvisor.advise("كتاب") shouldNotContain DrsTextTool.IMPROVE_PHRASES
        // لاتيني بلا أي إشارة: صمت كامل
        DrsTileContextAdvisor.advise("hello world") shouldBe emptyList()
    }

    test("العقد: سقف الترشيح أربعة يصمد مع المرشح الجديد الحاضر") {
        // ذيل بإطلاق مضمون (المشغّل آخر النص) + ثلاثة أسباب أخرى:
        // صفريات + تطويل + عربية عارية — السقف أربع وIMPROVE_PHRASES يتصدر.
        val picks = DrsTileContextAdvisor.advise("لــنص\u200F  مزدوج انشاء الله")
        picks.size shouldBe 4
        picks.first() shouldBe DrsTextTool.IMPROVE_PHRASES
        picks shouldContain DrsTextTool.REMOVE_ZERO_WIDTH
        picks shouldContain DrsTextTool.REMOVE_TATWEEL
        picks shouldContain DrsTextTool.TASHKEEL_TEXT
    }

    // ------------------------------------------------------------------
    // 6) اللا-انحدار مع محرك M1.5 التراثي
    // ------------------------------------------------------------------

    test("العقد: المحرك التراثي لم يُمس — ست عشرة عبارة والدالتان عامتان") {
        DrsWritingAssistant.PHRASES.size shouldBe 16
        // القيم التراثية كما هي: الدالة عبر الأداة والدالة المباشرة وجهان لأصل واحد
        DrsWritingAssistant.improveTail("انا بخير") shouldBe "أنا بخير"
        DrsWritingAssistant.normalizeSpacing("تمام  ،") shouldNotBe "تمام  ،"
    }
})
