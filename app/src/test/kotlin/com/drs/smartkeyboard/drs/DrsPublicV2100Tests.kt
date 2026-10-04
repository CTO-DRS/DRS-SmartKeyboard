/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * الإصدار العام v2.1.0 — التوسعة السابعة: التقويم يتكلم.
 *
 * الدفعة A: أداة التحويل الميلادي → هجري GREGORIAN_TO_HIJRI(-668).
 *
 * العقود المثبتة هنا:
 *  - الاصطلاح الموثق: جدول أم القرى المضمّن في المنصة (HijrahDate) —
 *    المصدر نفسه الذي تنطق به لوحة الأرقام في التطبيق («1 رمضان 1447
 *    هـ»)، فالتطبيق كله يتكلم هجريًا واحدًا. جداول أم القرى تختلف
 *    بين الإصدارات بيوم في أحيان كثيرة — والمراسي أدناه مثبتة على
 *    جدول المنصة المستخدم في البناء (JDK 17) وتحقيق تبادلي مع تنفيذ
 *    مستقل داخل منطقة تداخلهما.
 *  - المحلل المغلق نفسه: ثلاث مكونات على فاصل واحد من { - / . }،
 *    السنة حيث يقف المكوّن الرباعي، والتحقق الميلادي البروليبتيك.
 *  - المنطقة مغلقة 1300-01-01 .. 1600-12-30 هجري (1882-11-12 ..
 *    2174-11-25 ميلادي) — وما وراءها رفض صادق لا اختراع، والرمي
 *    الخام عند الحواف (حتى IndexOutOfBounds) يُمسَك فيتحول لا شيء.
 *  - اليوم بكلمات الترتيب المشتركة (مصدر الحقيقة الواحد)، والشهر من
 *    كتالوج الأشهر الهجرية المغلق، والسنة عبر DrsNumberWords مجرورة،
 *    و«هجري» بلا تنوين (عرف البيت).
 *  - العمى اللغوي للأرقام: غربية وعربية-هندية وممتدة ومختلطة تقبل.
 *  - نقطة ثابتة: المخرج بلا أرقام ولا فواصل فإعادة التطبيق لا تغير
 *    حرفًا.
 *  - الفحص الشامل: كل يوم في المنطقة المغلقة كاملة (1882-11-12 ..
 *    2174-11-25) يقارن حرفيًا بالتوقع المستقل المبني فوق تحويل
 *    المنصة وجداول الاختبار المستقلة — لا يوم واحد يُختصر.
 */
class DrsPublicV2100Tests : FunSpec({

    val ar = java.util.Locale.forLanguageTag("ar")

    // جداول الاختبار المستقلة — نسخة مرجعية لا تقرأ من المحرك.
    val ordinals = listOf(
        "", "الأول", "الثاني", "الثالث", "الرابع", "الخامس",
        "السادس", "السابع", "الثامن", "التاسع", "العاشر",
        "الحادي عشر", "الثاني عشر", "الثالث عشر", "الرابع عشر", "الخامس عشر",
        "السادس عشر", "السابع عشر", "الثامن عشر", "التاسع عشر",
        "العشرين", "الحادي والعشرين", "الثاني والعشرين", "الثالث والعشرين",
        "الرابع والعشرين", "الخامس والعشرين", "السادس والعشرين",
        "السابع والعشرين", "الثامن والعشرين", "التاسع والعشرين",
        "الثلاثين", "الحادي والثلاثين",
    )
    val hijriMonths = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    fun expectedHijriWords(gregorian: LocalDate): String {
        val h = HijrahDate.from(gregorian)
        val year = h.get(ChronoField.YEAR)
        val month = h.get(ChronoField.MONTH_OF_YEAR)
        val day = h.get(ChronoField.DAY_OF_MONTH)
        val yearWords = DrsNumberWords.arabicWordsOrNull(
            year.toString(), DrsNumberWords.Case.GENITIVE,
        ) ?: error("year words unavailable for $year")
        return "${ordinals[day]} من ${hijriMonths[month - 1]} عام $yearWords هجري"
    }

    test("GREGORIAN_TO_HIJRI registers at -668 in the catalogue") {
        val tool = DrsTextTool.GREGORIAN_TO_HIJRI
        tool.code shouldBe -668
        DrsTextTool.fromCode(-668) shouldBe tool
        DrsTextTool.CODE_RANGE.first shouldBe -673
        (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
        // 61 tools through v2.0.0 + the calendar-conversion tool.
        DrsTextTool.entries.size shouldBe 67
        (tool.isInfoOnly) shouldBe false
        (tool.isEditorOp) shouldBe false
        (tool.isInsertMark) shouldBe false
    }

    test("the tool dispatch speaks the Hijri phrase") {
        DrsTextTools.apply(
            DrsTextTool.GREGORIAN_TO_HIJRI, "2026-02-18", ar,
        ) shouldBe "الأول من رمضان عام ألف وأربعمائة وسبعة وأربعين هجري"
    }

    test("documented anchors speak their Hijri phrases") {
        // مراسٍ موثقة: رمضان 1447 يبدأ 2026-02-18، ومواسم الرمضان
        // الثلاثة، رأس السنة الهجرية 1440 و1421، حواف المنطقة 1300
        // و1600، وأواخر ربيع الأول وذو الحجة — كلها مُثبتة على جدول
        // المنصة ومُحققة تبادليًا مع تنفيذ مستقل حيث تداخل النطاقان.
        DrsHijriWords.gregorianToHijriWordsOrNull("2026-02-18") shouldBe
            "الأول من رمضان عام ألف وأربعمائة وسبعة وأربعين هجري" // 1447/9/1
        DrsHijriWords.gregorianToHijriWordsOrNull("2025-03-01") shouldBe
            "الأول من رمضان عام ألف وأربعمائة وستة وأربعين هجري" // 1446/9/1
        DrsHijriWords.gregorianToHijriWordsOrNull("2024-03-11") shouldBe
            "الأول من رمضان عام ألف وأربعمائة وخمسة وأربعين هجري" // 1445/9/1
        DrsHijriWords.gregorianToHijriWordsOrNull("2018-09-11") shouldBe
            "الأول من محرم عام ألف وأربعمائة وأربعين هجري" // 1440/1/1
        DrsHijriWords.gregorianToHijriWordsOrNull("2000-04-06") shouldBe
            "الأول من محرم عام ألف وأربعمائة وواحد وعشرين هجري" // 1421/1/1
        DrsHijriWords.gregorianToHijriWordsOrNull("2025-09-02") shouldBe
            "العاشر من ربيع الأول عام ألف وأربعمائة وسبعة وأربعين هجري" // 1447/3/10
        DrsHijriWords.gregorianToHijriWordsOrNull("2026-09-03") shouldBe
            "الحادي والعشرين من ربيع الأول عام ألف وأربعمائة وثمانية وأربعين هجري" // 1448/3/21
        DrsHijriWords.gregorianToHijriWordsOrNull("2026-05-27") shouldBe
            "العاشر من ذو الحجة عام ألف وأربعمائة وسبعة وأربعين هجري" // 1447/12/10
        DrsHijriWords.gregorianToHijriWordsOrNull("2030-01-01") shouldBe
            "السادس والعشرين من شعبان عام ألف وأربعمائة وواحد وخمسين هجري" // 1451/8/26
        // حواف المنطقة — أول يوم وآخر يوم في جدول المنصة.
        DrsHijriWords.gregorianToHijriWordsOrNull("1882-11-12") shouldBe
            "الأول من محرم عام ألف وثلاثمائة هجري" // 1300/1/1
        DrsHijriWords.gregorianToHijriWordsOrNull("2174-11-25") shouldBe
            "الثلاثين من ذو الحجة عام ألف وستمائة هجري" // 1600/12/30
    }

    test("the closed separator set and the year-position rule carry over") {
        // الفواصل الثلاثة تقبل والمختلط يرفض — نفس المغلق.
        DrsHijriWords.gregorianToHijriWordsOrNull("2026/02/18") shouldBe
            "الأول من رمضان عام ألف وأربعمائة وسبعة وأربعين هجري"
        DrsHijriWords.gregorianToHijriWordsOrNull("2026.02.18") shouldBe
            "الأول من رمضان عام ألف وأربعمائة وسبعة وأربعين هجري"
        DrsHijriWords.gregorianToHijriWordsOrNull("  2026-02-18  ") shouldBe
            "الأول من رمضان عام ألف وأربعمائة وسبعة وأربعين هجري"
        DrsHijriWords.gregorianToHijriWordsOrNull("18-02-2026") shouldBe
            "الأول من رمضان عام ألف وأربعمائة وسبعة وأربعين هجري"
        DrsHijriWords.gregorianToHijriWordsOrNull("2026-10/03") shouldBe null
        DrsHijriWords.gregorianToHijriWordsOrNull("26-10-03") shouldBe null
        DrsHijriWords.gregorianToHijriWordsOrNull("2026-10-2026") shouldBe null
    }

    test("digits are locale-blind on the three systems, mixed accepted") {
        DrsHijriWords.gregorianToHijriWordsOrNull("٢٠٢٦-٠٢-١٨") shouldBe
            "الأول من رمضان عام ألف وأربعمائة وسبعة وأربعين هجري"
        DrsHijriWords.gregorianToHijriWordsOrNull("۲۰۲۶.۰۲.۱۸") shouldBe
            "الأول من رمضان عام ألف وأربعمائة وسبعة وأربعين هجري"
        DrsHijriWords.gregorianToHijriWordsOrNull("٢٠٢٦-02-١٨") shouldBe
            "الأول من رمضان عام ألف وأربعمائة وسبعة وأربعين هجري"
    }

    test("out of the closed zone is an honest no-op") {
        // اليوم السابق لأول يوم في الجدول والذي بعده لآخر يوم.
        DrsHijriWords.gregorianToHijriWordsOrNull("1882-11-11") shouldBe null
        DrsHijriWords.gregorianToHijriWordsOrNull("2174-11-26") shouldBe null
        // سنوات ميلادية قبل المنطقة وبعدها — بما فيها سنة تقبلها
        // المحلل الميلادي (1447 م) لكنها خارج جدول المنصة.
        DrsHijriWords.gregorianToHijriWordsOrNull("1447/9/1") shouldBe null
        DrsHijriWords.gregorianToHijriWordsOrNull("0001-01-01") shouldBe null
        DrsHijriWords.gregorianToHijriWordsOrNull("9999-12-31") shouldBe null
    }

    test("shapes the grammar does not name come back byte-identical") {
        DrsHijriWords.gregorianToHijriWordsOrNull("") shouldBe null
        DrsHijriWords.gregorianToHijriWordsOrNull("اليوم الجمعة") shouldBe null
        DrsHijriWords.gregorianToHijriWordsOrNull("2026-13-01") shouldBe null
        DrsHijriWords.gregorianToHijriWordsOrNull("2026-02-30") shouldBe null
        DrsHijriWords.gregorianToHijriWordsOrNull("-2026-02-18") shouldBe null
        DrsHijriWords.gregorianToHijriWordsOrNull("2026-02-18 يوم خميس") shouldBe null
    }

    test("the output is a fixed point — no digits, no separator") {
        val out = DrsHijriWords.gregorianToHijriWordsOrNull("2026-02-18")!!
        // المحرك على مخرجه: لا شيء صادق (لا تاريخ فيه) — والأداة
        // عبر apply: تمرير بايتي حرفي.
        DrsHijriWords.gregorianToHijriWordsOrNull(out) shouldBe null
        DrsTextTools.apply(DrsTextTool.GREGORIAN_TO_HIJRI, out, ar) shouldBe out
    }

    test("every day of the closed zone speaks its exact phrase") {
        // الفحص الشامل: المنطقة المغلقة كاملة يومًا يومًا — التوقع
        // يُبنى مستقلًا فوق تحويل المنصة وجداول الاختبار الخاصة،
        // والمقارنة حرفية بلا اختصار.
        var d = LocalDate.of(1882, 11, 12)
        val end = LocalDate.of(2174, 11, 25)
        var count = 0
        while (!d.isAfter(end)) {
            val expected = expectedHijriWords(d)
            val iso = "${d.year}-${d.monthValue}-${d.dayOfMonth}"
            val actual = DrsHijriWords.gregorianToHijriWordsOrNull(iso)
            if (actual != expected) {
                error("zone mismatch at $iso ($d): actual=[$actual] expected=[$expected]")
            }
            count++
            d = d.plusDays(1)
        }
        // 1882-11-12..2174-11-25 = 106,665 يومًا كلها مطابقة.
        count shouldBe 106665
    }

    test("HIJRI_TO_GREGORIAN registers at -669 in the catalogue") {
        val tool = DrsTextTool.HIJRI_TO_GREGORIAN
        tool.code shouldBe -669
        DrsTextTool.fromCode(-669) shouldBe tool
        DrsTextTool.CODE_RANGE.first shouldBe -673
        (tool.code in DrsTextTool.CODE_RANGE) shouldBe true
        // 62 tools after the Gregorian→Hijri tool + the inverse.
        DrsTextTool.entries.size shouldBe 67
        (tool.isInfoOnly) shouldBe false
        (tool.isEditorOp) shouldBe false
        (tool.isInsertMark) shouldBe false
    }

    test("the inverse tool dispatch speaks the Gregorian phrase") {
        DrsTextTools.apply(
            DrsTextTool.HIJRI_TO_GREGORIAN, "1447/9/1", ar,
        ) shouldBe "الثامن عشر من فبراير عام ألفين وستة وعشرين"
    }

    test("documented inverse anchors speak their Gregorian phrases") {
        // مراسٍ موثقة معكوسة: مواسم الرمضان، رأس السنة 1440 و1421،
        // حواف المنطقة 1300 و1600 — كلها مُثبتة على جدول المنصة
        // ومُحققة تبادليًا مع تنفيذ مستقل داخل تداخل النطاقين.
        DrsHijriWords.hijriToGregorianWordsOrNull("1447/9/1") shouldBe
            "الثامن عشر من فبراير عام ألفين وستة وعشرين" // 2026-02-18
        DrsHijriWords.hijriToGregorianWordsOrNull("1446/9/1") shouldBe
            "الأول من مارس عام ألفين وخمسة وعشرين" // 2025-03-01
        DrsHijriWords.hijriToGregorianWordsOrNull("1445/9/1") shouldBe
            "الحادي عشر من مارس عام ألفين وأربعة وعشرين" // 2024-03-11
        DrsHijriWords.hijriToGregorianWordsOrNull("1440/1/1") shouldBe
            "الحادي عشر من سبتمبر عام ألفين وثمانية عشر" // 2018-09-11
        DrsHijriWords.hijriToGregorianWordsOrNull("1421/1/1") shouldBe
            "السادس من أبريل عام ألفين" // 2000-04-06
        DrsHijriWords.hijriToGregorianWordsOrNull("1447/3/10") shouldBe
            "الثاني من سبتمبر عام ألفين وخمسة وعشرين" // 2025-09-02
        DrsHijriWords.hijriToGregorianWordsOrNull("1448/3/21") shouldBe
            "الثالث من سبتمبر عام ألفين وستة وعشرين" // 2026-09-03
        DrsHijriWords.hijriToGregorianWordsOrNull("1447/12/10") shouldBe
            "السابع والعشرين من مايو عام ألفين وستة وعشرين" // 2026-05-27
        // حواف المنطقة — أول يوم وآخر يوم في جدول المنصة.
        DrsHijriWords.hijriToGregorianWordsOrNull("1300/1/1") shouldBe
            "الثاني عشر من نوفمبر عام ألف وثمانمائة واثنين وثمانين" // 1882-11-12
        DrsHijriWords.hijriToGregorianWordsOrNull("1600/12/30") shouldBe
            "الخامس والعشرين من نوفمبر عام ألفين ومائة وأربعة وسبعين" // 2174-11-25
    }

    test("the inverse renders through the SAME formatter as DATE_WORDS") {
        // مصدر الحقيقة الواحد: عبارت الأداتين لنفس اليوم حرفيًا واحدة.
        DrsHijriWords.hijriToGregorianWordsOrNull("1447/9/1") shouldBe
            DrsDateWords.dateWordsOrNull("2026-02-18")
        DrsHijriWords.hijriToGregorianWordsOrNull("1445/9/1") shouldBe
            DrsDateWords.dateWordsOrNull("2024-03-11")
        DrsHijriWords.hijriToGregorianWordsOrNull("1600/12/30") shouldBe
            DrsDateWords.dateWordsOrNull("2174-11-25")
    }

    test("the closed Hijri parser mirrors the Gregorian shape rules") {
        // الفواصل الثلاثة تقبل، والسنة حيثما يقف المكوّن الرباعي.
        DrsHijriWords.hijriToGregorianWordsOrNull("1447-9-1") shouldBe
            "الثامن عشر من فبراير عام ألفين وستة وعشرين"
        DrsHijriWords.hijriToGregorianWordsOrNull("1447.9.1") shouldBe
            "الثامن عشر من فبراير عام ألفين وستة وعشرين"
        DrsHijriWords.hijriToGregorianWordsOrNull("  1447/9/1  ") shouldBe
            "الثامن عشر من فبراير عام ألفين وستة وعشرين"
        DrsHijriWords.hijriToGregorianWordsOrNull("1/9/1447") shouldBe
            "الثامن عشر من فبراير عام ألفين وستة وعشرين"
        DrsHijriWords.hijriToGregorianWordsOrNull("1447/9-1") shouldBe null
        DrsHijriWords.hijriToGregorianWordsOrNull("47/9/1") shouldBe null
        DrsHijriWords.hijriToGregorianWordsOrNull("1447/03/1447") shouldBe null
        DrsHijriWords.hijriToGregorianWordsOrNull("1447/9") shouldBe null
        DrsHijriWords.hijriToGregorianWordsOrNull("1447/9/1/5") shouldBe null
    }

    test("Hijri digits are locale-blind on the three systems, mixed accepted") {
        DrsHijriWords.hijriToGregorianWordsOrNull("١٤٤٧/٩/١") shouldBe
            "الثامن عشر من فبراير عام ألفين وستة وعشرين"
        DrsHijriWords.hijriToGregorianWordsOrNull("۱۴۴۷-۹-۱") shouldBe
            "الثامن عشر من فبراير عام ألفين وستة وعشرين"
        DrsHijriWords.hijriToGregorianWordsOrNull("١٤٤٧/9/۱") shouldBe
            "الثامن عشر من فبراير عام ألفين وستة وعشرين"
    }

    test("out of the closed Hijri zone and wrong month lengths are honest no-ops") {
        // السنة قبل أول سنة في الجدول وبعدها.
        DrsHijriWords.hijriToGregorianWordsOrNull("1299/12/30") shouldBe null
        DrsHijriWords.hijriToGregorianWordsOrNull("1601/1/1") shouldBe null
        // الشهر 13 ليس شهرًا.
        DrsHijriWords.hijriToGregorianWordsOrNull("1447/13/1") shouldBe null
        // صفر 1447 له 29 يومًا — الثلاثون مرفوضة بجدول المنصة نفسه.
        DrsHijriWords.hijriToGregorianWordsOrNull("1447/2/30") shouldBe null
        DrsHijriWords.hijriToGregorianWordsOrNull("1447/2/29") shouldBe
            "الثالث والعشرين من أغسطس عام ألفين وخمسة وعشرين" // 2025-08-23
        // لاحقة «هـ» ليست في القواعد المغلقة — لا نثر بروفي.
        DrsHijriWords.hijriToGregorianWordsOrNull("1447/9/1 هـ") shouldBe null
        DrsHijriWords.hijriToGregorianWordsOrNull("") shouldBe null
        DrsHijriWords.hijriToGregorianWordsOrNull("اليوم عيد الأضحى") shouldBe null
    }

    test("the inverse output is a fixed point too") {
        val out = DrsHijriWords.hijriToGregorianWordsOrNull("1447/9/1")!!
        // المخرج عبارة ميلادية بلا أرقام ولا فواصل — المحرك يعيد null
        // والأداة تمرر بايتيًا.
        DrsHijriWords.hijriToGregorianWordsOrNull(out) shouldBe null
        DrsTextTools.apply(DrsTextTool.HIJRI_TO_GREGORIAN, out, ar) shouldBe out
    }

    test("every Hijri day of the zone returns the DATE_WORDS phrase of its Gregorian day") {
        // الفحص الشامل المعكوس: كل يوم هجري في المنطقة كاملة (1300
        // إلى 1600، كل شهر بكل أيامه بجداول المنصة نفسها) يعطي حرفيًا
        // عبارت DATE_WORDS لليوم الميلادي الموافق — المحرك هو المرآة
        // الحرفية لمصدر الحقيقة، يومًا يومًا.
        var count = 0
        for (year in DrsHijriWords.HIJRI_YEAR_FIRST..DrsHijriWords.HIJRI_YEAR_LAST) {
            for (month in 1..12) {
                val length = HijrahDate.of(year, month, 1).lengthOfMonth()
                for (day in 1..length) {
                    val hijriInput = "$year/$month/$day"
                    val gregorian = LocalDate.from(HijrahDate.of(year, month, day))
                    val expected = DrsDateWords.dateWordsOrNull(
                        "${gregorian.year}-${gregorian.monthValue}-${gregorian.dayOfMonth}",
                    ) ?: error("DATE_WORDS null for $gregorian")
                    val actual = DrsHijriWords.hijriToGregorianWordsOrNull(hijriInput)
                    if (actual != expected) {
                        error("inverse mismatch at $hijriInput ($gregorian): " +
                            "actual=[$actual] expected=[$expected]")
                    }
                    count++
                }
            }
        }
        // 1300-01-01..1600-12-30 = 106,665 يومًا كلها مطابقة تمامًا.
        count shouldBe 106665
    }
})
