/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * الإصدار العام v1.4.0 — التشكيل الحتمي للنص الكامل (the deterministic
 * whole-text vocalization): the TASHKEEL_TEXT text tool walks the field
 * (or the selection) once, looks every Arabic word run up in the local
 * lexicons — the reviewed seed first, then the 3000-word asset — and
 * vocalizes exactly the words it KNOWS. An unknown word is emitted
 * byte-identical (never an invented mark), and every non-Arabic
 * character — spaces, newlines, punctuation, digits, Latin — passes
 * through verbatim. The tests below run on the SEED lexicon alone (the
 * asset lexicon installs on the app process, not in JVM tests).
 */
class DrsPublicV140Tests : FunSpec({

    // تعريفات محلية داخل جسم المواصفة — Kotest FunSpec lambda.
    val tool = DrsTextTool.TASHKEEL_TEXT
    fun f(text: String) = DrsTextTools.apply(tool, text)

    // -------------------------------------------------------------
    // الكلمات المعروفة تُشكَّل — known words take the canonical form
    // -------------------------------------------------------------

    test("a known function word is vocalized canonically") {
        f("من") shouldBe "مِنْ"
        f("في") shouldBe "فِي"
        f("إلى") shouldBe "إِلَى"
        f("على") shouldBe "عَلَى"
        f("هذا") shouldBe "هَٰذَا"
        f("الذي") shouldBe "الَّذِي"
    }

    test("a mixed sentence vocalizes only the known words") {
        // «المغرب» و«الشرق» ليستا في البذرة — تُركتا حرفيًا كما هما
        // (وفي وقت التشغيل قد يحسمهما قاموس الأصول الثلاث آلاف —
        // وهنا في اختبارات JVM البذرة وحدها هي المثبتة).
        f("من المغرب إلى الشرق") shouldBe "مِنْ المغرب إِلَى الشرق"
        f("هذا نص تجريبي") shouldBe "هَٰذَا نص تجريبي"
    }

    test("a word typed with partial marks still resolves") {
        f("مِن") shouldBe "مِنْ"      // kasra only — the stripped form matches
        f("فِيْ") shouldBe "فِي"       // over-marked — the canonical form wins
    }

    // -------------------------------------------------------------
    // الكلمات المجهولة تبقى حرفيًا — the honest rule
    // -------------------------------------------------------------

    test("an unknown word is emitted byte-identical") {
        val unknown = "زخرفضشقث"
        f(unknown) shouldBe unknown
        val unknownMarked = "زُخْرَف"
        f(unknownMarked) shouldBe unknownMarked
    }

    test("an unknown word inside a known sentence stays untouched") {
        f("في زخرفضشقث من") shouldBe "فِي زخرفضشقث مِنْ"
    }

    // -------------------------------------------------------------
    // البنية تُحفظ حرفيًا — structure passes through verbatim
    // -------------------------------------------------------------

    test("spaces, newlines and punctuation are preserved exactly") {
        f("من، إلى: ثم!") shouldBe "مِنْ، إِلَى: ثُمَّ!"
        f("من\nإلى\n\nثم") shouldBe "مِنْ\nإِلَى\n\nثُمَّ"
        f("  من   في  ") shouldBe "  مِنْ   فِي  "
    }

    test("digits and Latin words pass through untouched") {
        f("في 2026 العام") shouldBe "فِي 2026 العام"
        f("Hello من world") shouldBe "Hello مِنْ world"
        f("v2.0 في beta") shouldBe "v2.0 فِي beta"
    }

    test("a text with no Arabic words returns unchanged") {
        val latin = "Hello, world! 123 456."
        f(latin) shouldBe latin
    }

    test("empty text returns unchanged (the apply gate)") {
        f("") shouldBe ""
    }

    // -------------------------------------------------------------
    // التعديل مرتان لا يغير شيئًا — idempotency
    // -------------------------------------------------------------

    test("applying the tool to its own output is a fixed point") {
        val before = "من المغرب إلى الشرق، في 2026"
        val once = f(before)
        val twice = f(once)
        twice shouldBe once
        // and the known words did move at least once:
        once shouldNotBe before
    }

    // -------------------------------------------------------------
    // عقود الأداة — the catalogue contracts
    // -------------------------------------------------------------

    test("TASHKEEL_TEXT registers in the dispatch range and catalogue") {
        tool.code shouldBe -658
        DrsTextTool.fromCode(-658) shouldBe tool
        (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
        // 51 tools through v1.21.0, + the sentence vocalization (v1.4.0)
        // and + the number-to-words tool (v1.6.0) and + the tafqit tool
        // (v1.7.0) and + the date tool (v1.8.0) and + the clock-time
        // tool (v1.9.0), and the v1.10.0 fraction, weekday and ordinal tools.
        DrsTextTool.entries.size shouldBe 63
        (tool.isInfoOnly) shouldBe false
        (tool.isEditorOp) shouldBe false
        (tool.isInsertMark) shouldBe false
    }

    test("the tool is the honest counterpart of REMOVE_DIACRITICS") {
        val marked = f("من زخرف")
        DrsTextTools.apply(DrsTextTool.REMOVE_DIACRITICS, marked) shouldBe "من زخرف"
    }

    // -------------------------------------------------------------
    // حدود يونيكود — the boundary behavior
    // -------------------------------------------------------------

    test("an Arabic word hugging punctuation resolves without bleeding") {
        f("(من)") shouldBe "(مِنْ)"
        f("\"في\"") shouldBe "\"فِي\""
        f("«ثم»") shouldBe "«ثُمَّ»"
    }

    test("Arabic-Indic digits inside a sentence are not word chars") {
        // الأرقام العربية ليست حروف كلمات: «عام ١٤٤٧» كلمتان ورقم.
        f("عندما ١٤٤٧") shouldBe "عَنْدَمَا ١٤٤٧"
    }
})
