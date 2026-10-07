/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * الجولة الثامنة v2.2.0 — «البلاطة الذكية».
 *
 * الدفعة A: عقل البلاطات الحتمي — DrsTileSmart.kt.
 *
 * العقود المثبتة هنا:
 *  - التطبيع العربي الواعي (DrsTileText): نزع التشكيل والتطويل
 *    والعلامات الصفرية، طي الهمزات والتاء المربوطة والألف المقصورة
 *    والياء والواو الهمزيتين، توحيد الأرقام الهندية والممتدة على
 *    0-9، الطي إلى الصغير، وحصر المسافات — كل المقارنات فوق مصدر
 *    واحد لا يتعارض معه اثنان.
 *  - البحث (DrsTileSearch): دلالة «و» صارمة — كل كلمة سؤال تنزل على
 *    أحد المفاتيح أو يسقط القالب صادقًا (صفر لا مراعاة)، والتدرج
 *    مطابق-تمام > بادئة > بداية كلمة داخلية > احتواء.
 *  - المستشار السياقي (DrsTileContextAdvisor): أسباب مغلقة فقط،
 *    الأدق يتصدر، السقف أربع، وما لا إشارة فيه يعود فارغًا — الصمت
 *    صدق وليس صفًا حشوًا.
 *  - المفضلة (DrsTileFavorites): ترتيب تثبيت، إزالة بالقيمة، وسقف
 *    الثمانية رفض صادق (Full) لا إسكات أقدم بلاطة — على نمط
 *    capPinned في الشريط الموحد.
 *  - المصدر الواحد: كاشفا EMOJI/ZERO_WIDTH في DrsTextTools أصبحا
 *    internal ويستهلكهما المستشار نفسه — إعادة الإملاء ممنوعة،
 *    وZWNJ (\u200C) يبقى خارج عائلة النفاية لأنه نصف المسافة المشروع.
 *  - الأختام قبل إضافات الدفعة C: 63 أداة والنطاق يبدأ من -669.
 */
class DrsV2200Tests : FunSpec({

    val ar = java.util.Locale.forLanguageTag("ar")

    // ------------------------------------------------------------
    // 1) التطبيع العربي الواعي
    // ------------------------------------------------------------
    test("DrsTileText strips harakat, tatweel and zero-width marks") {
        DrsTileText.normalize("كِتَـابٌ") shouldBe "كتاب"
        DrsTileText.normalize("abc\u200Fdef\u200B") shouldBe "abcdef"
        DrsTileText.normalize("\uFEFFنص\u2066x\u2069") shouldBe "نصx"
    }

    test("DrsTileText folds hamza carriers, ta-marbuta, alef maqsura") {
        DrsTileText.normalize("أإآٱ") shouldBe "اااا"
        DrsTileText.normalize("مدرسة") shouldBe "مدرسه"
        DrsTileText.normalize("على") shouldBe "علي"
        DrsTileText.normalize("ؤلئ") shouldBe "ولي"
    }

    test("DrsTileText unifies eastern digits and latin case and spaces") {
        DrsTileText.normalize("٠١٢٣") shouldBe "0123"
        DrsTileText.normalize("۱۲۳") shouldBe "123"
        DrsTileText.normalize("ABC") shouldBe "abc"
        DrsTileText.normalize("  أهلًا\t  بالعالم  ") shouldBe "اهلا بالعالم"
        DrsTileText.normalize("") shouldBe ""
    }

    // ------------------------------------------------------------
    // 2) البحث بدلالة «و» وتدرّج الدرجة
    // ------------------------------------------------------------
    test("DrsTileSearch exact token beats prefix beats word-start beats contains") {
        DrsTileSearch.rank("نزع", listOf("نزع")) shouldBe 16
        DrsTileSearch.rank("نزع", listOf("نزع التشكيل")) shouldBe 8
        // بداية كلمة داخلية: «التشكيل» بلامها التعريف تبدأ الكلمة الثانية.
        DrsTileSearch.rank("التشكيل", listOf("نزع التشكيل")) shouldBe 4
        // احتواء بحت: «شكيل» داخل «التشكيل» بلا بداية كلمة.
        DrsTileSearch.rank("شكيل", listOf("نزع التشكيل")) shouldBe 2
    }

    test("DrsTileSearch is diacritics- and hamza-insensitive") {
        DrsTileSearch.matches("تنسيق", listOf("تَنْسِيق الأسطر")) shouldBe true
        // الهمزة تُطىّ من الجهتين: سؤال «الالف» ينزل على «الألف».
        DrsTileSearch.matches("الالف", listOf("همزات الألف")) shouldBe true
    }

    test("DrsTileSearch requires EVERY query token to land (AND semantics)") {
        DrsTileSearch.rank("ترتيب غيره", listOf("ترتيب الأسطر")) shouldBe 0
        // 8 (بادئة «ترتيب») + 2 («اسطر» داخل «الاسطر» بلا بداية كلمة).
        DrsTileSearch.rank("ترتيب اسطر", listOf("ترتيب الأسطر")) shouldBe 10
    }

    test("DrsTileSearch refuses honestly on empty query or empty keys") {
        DrsTileSearch.rank("", listOf("ترتيب")) shouldBe 0
        DrsTileSearch.rank("ترتيب", emptyList()) shouldBe 0
        DrsTileSearch.rank("   ", listOf("ترتيب")) shouldBe 0
    }

    // ------------------------------------------------------------
    // 3) المستشار السياقي — أسباب مغلقة وسقف أربع
    // ------------------------------------------------------------
    test("Advisor stays silent on empty text and on signal-less latin") {
        DrsTileContextAdvisor.advise("") shouldBe emptyList()
        DrsTileContextAdvisor.advise("hello world") shouldBe emptyList()
    }

    test("Advisor leads with URL_DECODE for percent-escaped URLs") {
        val picks = DrsTileContextAdvisor.advise("https%3A%2F%2Fexample.com")
        picks shouldContain DrsTextTool.URL_DECODE
        picks.first() shouldBe DrsTextTool.URL_DECODE
    }

    test("Advisor strips zero-width and tatweel signals") {
        DrsTileContextAdvisor.advise("نص\u200Fآخر") shouldContain DrsTextTool.REMOVE_ZERO_WIDTH
        DrsTileContextAdvisor.advise("لــغة") shouldContain DrsTextTool.REMOVE_TATWEEL
    }

    test("Advisor offers removal for marked Arabic and vocalization for naked Arabic") {
        val marked = DrsTileContextAdvisor.advise("مُشكَّل")
        marked shouldContain DrsTextTool.REMOVE_DIACRITICS
        marked shouldNotContain DrsTextTool.TASHKEEL_TEXT

        val naked = DrsTileContextAdvisor.advise("كتاب")
        naked shouldContain DrsTextTool.TASHKEEL_TEXT
        naked shouldNotContain DrsTextTool.REMOVE_DIACRITICS
    }

    test("Advisor suggests trim, emoji strip, western unification and word speaking") {
        DrsTileContextAdvisor.advise("سطر  مزدوج") shouldContain DrsTextTool.TRIM_SPACES
        DrsTileContextAdvisor.advise("مرحبا \uD83D\uDE00") shouldContain DrsTextTool.STRIP_EMOJI
        DrsTileContextAdvisor.advise("سجلت ٢٥ نقطة") shouldContain DrsTextTool.TO_WESTERN_DIGITS
        DrsTileContextAdvisor.advise("العدد 5") shouldContain DrsTextTool.NUMBER_WORDS
    }

    test("Advisor caps the picks at four and keeps reason order stable") {
        // نصٌّ يضرب خمسة أسباب دفعة واحدة: ترميز + صفريات + تطويل +
        // حركات + مسافات متراكمة — السقف أربع والأولوية محفوظة.
        val picks = DrsTileContextAdvisor.advise("https%3A نــَص\u200F  مزدوج")
        picks.size shouldBe 4
        picks.first() shouldBe DrsTextTool.URL_DECODE
        picks[1] shouldBe DrsTextTool.REMOVE_ZERO_WIDTH
        picks[2] shouldBe DrsTextTool.REMOVE_TATWEEL
        picks[3] shouldBe DrsTextTool.REMOVE_DIACRITICS
    }

    // ------------------------------------------------------------
    // 4) المفضلة المحدودة — ترتيب تثبيت ورفض صادق
    // ------------------------------------------------------------
    test("Favorites toggle adds in pin order and removes by value") {
        DrsTileFavorites.toggled(listOf(-601, -621), -641) shouldBe
            DrsTileFavorites.ToggleResult.Ok(listOf(-601, -621, -641))
        DrsTileFavorites.toggled(listOf(-601, -621, -641), -621) shouldBe
            DrsTileFavorites.ToggleResult.Ok(listOf(-601, -641))
    }

    test("Favorites cap refuses honestly at eight (no silent eviction)") {
        // نطاق تصاعدي — (-601..-608) في Kotlin فارغ لأن البداية أكبر من النهاية!
        val full = (-608..-601).toList()
        DrsTileFavorites.toggled(full, -609) shouldBe DrsTileFavorites.ToggleResult.Full
        DrsTileFavorites.canFavorite(full) shouldBe false
        DrsTileFavorites.canFavorite(full - -601) shouldBe true
        DrsTileFavorites.MAX_FAVORITES shouldBe 8
    }

    // ------------------------------------------------------------
    // 5) المصدر الواحد لكاشفي الرموز والعلامات الصفرية
    // ------------------------------------------------------------
    test("DrsTextTools mark classes stay the single source of truth") {
        (EMOJI_CHARS.containsMatchIn("\uD83D\uDE00")) shouldBe true
        (EMOJI_CHARS.containsMatchIn("نص عربي")) shouldBe false
        (ZERO_WIDTH_CHARS.containsMatchIn("\u200B")) shouldBe true
        // ZWNJ (نصف المسافة) مشروع ولا ينتمي لعائلة النفاية.
        (ZERO_WIDTH_CHARS.containsMatchIn("\u200C")) shouldBe false
    }

    // ------------------------------------------------------------
    // 6) الأختام بعد إضافات الدفعة C
    // ------------------------------------------------------------
    test("v2.2.0 seals — the catalogue is 67 tools over -673") {
        DrsTextTool.entries.size shouldBe 69
        DrsTextTool.CODE_RANGE.first shouldBe -675
        DrsTextTool.CODE_RANGE.last shouldBe -601
        // round-trip من الكود إلى الأداة ومعهRegistration للأربعة الجدد.
        DrsTextTool.fromCode(-670) shouldBe DrsTextTool.SORT_LINES_NATURAL
        DrsTextTool.fromCode(-671) shouldBe DrsTextTool.SLUGIFY
        DrsTextTool.fromCode(-672) shouldBe DrsTextTool.TO_SNAKE_CASE
        DrsTextTool.fromCode(-673) shouldBe DrsTextTool.TO_CAMEL_CASE
    }

    // ------------------------------------------------------------
    // 7) الأدوات الأربع الجديدة — عقودها المغلقة
    // ------------------------------------------------------------
    test("SORT_LINES_NATURAL reads numbers the way humans do") {
        fun nat(s: String) = DrsTextTools.apply(DrsTextTool.SORT_LINES_NATURAL, s, ar)
        nat("file10\nfile2\nfile1") shouldBe "file1\nfile2\nfile10"
        nat("قائمة 10\nقائمة 2") shouldBe "قائمة 2\nقائمة 10"
        // دقة عشوائية: مفاضلة الطول قبل المعجمية — لا فيض Long أبدًا.
        nat("100000000000000000000\n99999999999999999999") shouldBe
            "99999999999999999999\n100000000000000000000"
        // الصفر القائد لا يخدع المقارنة.
        nat("a007\na8\na10") shouldBe "a007\na8\na10"
        // السطر الجديد الختامي محفوظ كسائر أسرة الفرز.
        nat("b\na\n") shouldBe "a\nb\n"
    }

    test("SLUGIFY builds a link-safe path and refuses an empty one honestly") {
        fun slug(s: String) = DrsTextTools.apply(DrsTextTool.SLUGIFY, s, ar)
        slug("مرحبا بالعالم!") shouldBe "مرحبا-بالعالم"
        slug("كِتَابُ الْحَدِيث") shouldBe "كتاب-الحديث"
        slug("علم_الحاسوب") shouldBe "علم-الحاسوب"
        slug("Drs   Smart--Keyboard!!") shouldBe "drs-smart-keyboard"
        slug("دَرس      مطوَّل") shouldBe "درس-مطول"
        // لا شرطات زائدة في الطرفين، والرموز تسقط.
        slug("--عنوان *** خاص--") shouldBe "عنوان-خاص"
        // ترقيم صافٍ يعود بايتيًا — لا slug فارغ مختلق.
        slug("!!!") shouldBe "!!!"
    }

    test("TO_SNAKE_CASE joins with underscores and no invented case") {
        fun snake(s: String) = DrsTextTools.apply(DrsTextTool.TO_SNAKE_CASE, s, ar)
        snake("my variable name") shouldBe "my_variable_name"
        snake("MyVariableName") shouldBe "my_variable_name"
        snake("my-variable") shouldBe "my_variable"
        snake("getHTTPResponse") shouldBe "get_http_response"
        snake("متجر آب") shouldBe "متجر_آب"
        // ما هو snake أصلًا يبقى بايتيًا.
        snake("my_variable") shouldBe "my_variable"
    }

    test("TO_CAMEL_CASE joins latin tokens and refuses non-ASCII honestly") {
        fun camel(s: String) = DrsTextTools.apply(DrsTextTool.TO_CAMEL_CASE, s, ar)
        camel("my variable name") shouldBe "myVariableName"
        camel("MY VAR") shouldBe "myVar"
        camel("my-variable") shouldBe "myVariable"
        camel("get_http_response") shouldBe "getHttpResponse"
        // الحرف غير اللاتيني يرفض التحويل كله بايتيًا — لا اختراع.
        camel("my متجر") shouldBe "my متجر"
        // camel أصلًا يبقى كما هو.
        camel("myVar") shouldBe "myVar"
    }
})
