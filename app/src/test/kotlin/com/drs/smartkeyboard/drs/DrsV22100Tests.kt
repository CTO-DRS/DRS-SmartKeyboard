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
 * DRS v2.21.0 «نصوصي المحفوظة» — عقود محرك النصوص المحفوظة النقي:
 * التحقق الحتمي (فارغ/أطوال/حد 100)، الرفض فشل-مغلق بلا إخلاء صامت
 * (المحتوى المنسّق لا يُدَمَّر خلف ظهر المستخدم)، إزالة التكرار الصادقة
 * (الثلاثية نفسها تنعش لا تُوَلِّد توأمًا)، الترتيب الحتمي (المثبت أولًا
 * ثم الأحدث بكسر تعادل المعرّف)، البحث الحرفي بلا تطبيع يخفي، القوالب
 * المشتركة مع محرك الاختصارات وتوسّعها عند الإدراج لا عند التخزين،
 * وعقد فك ترميز الحالة القديمة (حقول نصوصي الغائبة تفك إلى افتراضاتها).
 */
class DrsV22100Tests : FunSpec({

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun stateWith(
        texts: List<DrsMyText> = emptyList(),
        nextId: Long = (texts.maxOfOrNull { it.id } ?: 0L) + 1,
    ): DrsState = DrsState(myTexts = texts, nextMyTextId = nextId)

    fun entry(
        id: Long,
        text: String,
        label: String = "",
        category: String = "",
        pinned: Boolean = false,
        updatedAtMs: Long = 0,
    ) = DrsMyText(
        id = id,
        text = text,
        label = label,
        category = category,
        pinned = pinned,
        createdAtMs = updatedAtMs,
        updatedAtMs = updatedAtMs,
    )

    // ------------------------------------------------------------------
    // التحقق الحتمي
    // ------------------------------------------------------------------

    test("العقد: النص الفارغ (أو الفراغات) يُرفض باسمه") {
        DrsMyTexts.validate("", "", "") shouldBe DrsMyTexts.Error.EMPTY_TEXT
        DrsMyTexts.validate("   ", "", "") shouldBe DrsMyTexts.Error.EMPTY_TEXT
        DrsMyTexts.validate("\n\t", "", "") shouldBe DrsMyTexts.Error.EMPTY_TEXT
    }

    test("العقد: الحدود الثلاثة نص/تسمية/فئة حرفية بلا تسامح") {
        DrsMyTexts.validate("a".repeat(501), "", "") shouldBe DrsMyTexts.Error.TEXT_TOO_LONG
        DrsMyTexts.validate("a".repeat(500), "", "") shouldBe null
        DrsMyTexts.validate("نص", "l".repeat(41), "") shouldBe DrsMyTexts.Error.LABEL_TOO_LONG
        DrsMyTexts.validate("نص", "l".repeat(40), "") shouldBe null
        DrsMyTexts.validate("نص", "", "c".repeat(25)) shouldBe DrsMyTexts.Error.CATEGORY_TOO_LONG
        DrsMyTexts.validate("نص", "", "c".repeat(24)) shouldBe null
    }

    // ------------------------------------------------------------------
    // الإضافة والحد الأقصى وإزالة التكرار الصادقة
    // ------------------------------------------------------------------

    test("العقد: الإضافة تسلّم المعرّف التسلسلي وترفع المُوزِّع وتقصّ الفراغات") {
        val r = DrsMyTexts.addAt(stateWith(), "  تحية صباحية  ", " تحية ", " ردود ", 1000)
        r.shouldBeInstanceOf<DrsMyTexts.Result.Accepted>()
        r.state.myTexts.size shouldBe 1
        r.state.myTexts.first().id shouldBe 1L
        r.state.myTexts.first().text shouldBe "تحية صباحية"
        r.state.myTexts.first().label shouldBe "تحية"
        r.state.myTexts.first().category shouldBe "ردود"
        r.state.nextMyTextId shouldBe 2L
        r.state.myTexts.first().createdAtMs shouldBe 1000L
    }

    test("العقد: الثلاثية نفسها تنعش الموجود ولا تُوَلِّد توأمًا ولا تستهلك معرّفًا") {
        val first = DrsMyTexts.addAt(stateWith(), "نص", "ل", "ف", 1000)
            .shouldBeInstanceOf<DrsMyTexts.Result.Accepted>().state
        val again = DrsMyTexts.addAt(first, "نص", "ل", "ف", 2000)
            .shouldBeInstanceOf<DrsMyTexts.Result.Accepted>().state
        withClue("بلا توأم") { again.myTexts.size shouldBe 1 }
        withClue("المعرّف يبقى معرّف الأصل") { again.myTexts.first().id shouldBe 1L }
        withClue("الانتعاش يرفع updatedAt") { again.myTexts.first().updatedAtMs shouldBe 2000L }
        withClue("المُوزِّع لم يُستهلك") { again.nextMyTextId shouldBe 2L }
        // الثلاثية نفسها بعد اقتطاع الفراغات — لا توأم أيضًا
        val third = DrsMyTexts.addAt(again, " نص ", " ل ", " ف ", 2000)
            .shouldBeInstanceOf<DrsMyTexts.Result.Accepted>().state
        third.myTexts.size shouldBe 1
    }

    test("العقد: حد 100 يُرفض فشل-مغلق — المحتوى المنسّق لا يُخَلَّى صامتًا") {
        var state = stateWith()
        repeat(DrsMyTexts.MAX_ITEMS) { i ->
            state = DrsMyTexts.addAt(state, "نص رقم $i", "", "", i.toLong())
                .shouldBeInstanceOf<DrsMyTexts.Result.Accepted>().state
        }
        state.myTexts.size shouldBe DrsMyTexts.MAX_ITEMS
        val overflow = DrsMyTexts.addAt(state, "واحد زائد", "", "", 9999)
        overflow.shouldBeInstanceOf<DrsMyTexts.Result.Rejected>()
        overflow.error shouldBe DrsMyTexts.Error.FULL
        withClue("الحالة لم تُمسّ") { state.myTexts.size shouldBe DrsMyTexts.MAX_ITEMS }
    }

    // ------------------------------------------------------------------
    // التعديل والحذف والتثبيت
    // ------------------------------------------------------------------

    test("العقد: التعديل يحفظ المعرف ويرفع الزمن ويقص الفراغات") {
        val state = stateWith(texts = listOf(entry(1, "قديم", updatedAtMs = 100)))
        val r = DrsMyTexts.editAt(state, 1, "جديد", " ل ", " ف ", 5000)
        r.shouldBeInstanceOf<DrsMyTexts.Result.Accepted>()
        val edited = r.state.myTexts.single()
        edited.id shouldBe 1L
        edited.text shouldBe "جديد"
        edited.label shouldBe "ل"
        edited.category shouldBe "ف"
        edited.updatedAtMs shouldBe 5000L
    }

    test("العقد: التعديل والحذف والتثبيت على معرف غائب = لا-عمل صادق") {
        val state = stateWith(texts = listOf(entry(1, "نص")))
        DrsMyTexts.editAt(state, 99, "جديد", "", "", 1)
            .shouldBeInstanceOf<DrsMyTexts.Result.Accepted>().state shouldBe state
        DrsMyTexts.removeAt(state, 99) shouldBe state
        DrsMyTexts.togglePinAt(state, 99) shouldBe state
    }

    test("العقد: التثبيت يقلب الراية ولا يعيد ترتيب التخزين") {
        val state = stateWith(texts = listOf(entry(1, "أ"), entry(2, "ب")))
        val pinned = DrsMyTexts.togglePinAt(state, 1)
        pinned.myTexts[0].id shouldBe 1L
        pinned.myTexts[0].pinned shouldBe true
        pinned.myTexts[1].pinned shouldBe false
        val unpinned = DrsMyTexts.togglePinAt(pinned, 1)
        unpinned.myTexts[0].pinned shouldBe false
    }

    // ------------------------------------------------------------------
    // الترتيب الحتمي
    // ------------------------------------------------------------------

    test("العقد: الترتيب المثبت أولًا ثم الأحدث ثم كسر التعادل بالمعرف الأعلى") {
        val texts = listOf(
            entry(3, "حديث", updatedAtMs = 300),
            entry(1, "مثبت قديم", pinned = true, updatedAtMs = 100),
            entry(2, "قديم", updatedAtMs = 200),
            entry(4, "مثبت حديث", pinned = true, updatedAtMs = 400),
            entry(5, "تعادل زمني", updatedAtMs = 200),
        )
        val ordered = DrsMyTexts.order(texts)
        ordered.map { it.id } shouldBe listOf(4L, 1L, 3L, 5L, 2L)
        // الحتمية: نفس الدخل ينتج نفس الترتيب حرفيًا
        DrsMyTexts.order(texts).map { it.id } shouldBe ordered.map { it.id }
    }

    // ------------------------------------------------------------------
    // البحث والفئات
    // ------------------------------------------------------------------

    test("العقد: البحث يطابق النص والتسمية والفئة بحساسية حالة ROOT دون المساس بالعربية") {
        val texts = listOf(
            entry(1, "عنوان المنزل", label = "بيت", category = "عناوين"),
            entry(2, "Best Regards", label = "", category = "ردود"),
            entry(3, "IBAN صافي", label = "حسابي", category = ""),
        )
        DrsMyTexts.search(texts, "المنزل").map { it.id } shouldBe listOf(1L)
        DrsMyTexts.search(texts, "BEST").map { it.id } shouldBe listOf(2L)
        DrsMyTexts.search(texts, "حسابي").map { it.id } shouldBe listOf(3L)
        DrsMyTexts.search(texts, "ردود").map { it.id } shouldBe listOf(2L)
        withClue("الاستعلام الفارغ والفراغات يختاران كل شيء") {
            DrsMyTexts.search(texts, "") shouldBe texts
            DrsMyTexts.search(texts, "   ") shouldBe texts
        }
        withClue("بلا تطبيع يخفي — ما حفظه المستخدم هو ما يطابق") {
            DrsMyTexts.search(texts, "عنوان المنزل").map { it.id } shouldBe listOf(1L)
            DrsMyTexts.search(texts, "عنوانالمنزل") shouldBe emptyList()
        }
    }

    test("العقد: الفئات منفردة مرتبة بلا الفراغات") {
        val texts = listOf(
            entry(1, "أ", category = "ردود"),
            entry(2, "ب", category = "عناوين"),
            entry(3, "ج", category = "ردود"),
            entry(4, "د", category = ""),
            entry(5, "هـ", category = "   "),
        )
        DrsMyTexts.categoriesOf(texts) shouldBe listOf("ردود", "عناوين")
    }

    // ------------------------------------------------------------------
    // المعاينة الصادقة
    // ------------------------------------------------------------------

    test("العقد: التسمية تغلب المعاينة، وأول سطر يُمثّل النص، والقص بصفارة حقيقية") {
        withClue("التسمية أولاها") {
            DrsMyTexts.previewLabel("نص طويل جدا", "تسمية") shouldBe "تسمية"
        }
        withClue("أول سطر فقط") {
            DrsMyTexts.previewLabel("السطر الأول\nالسطر الثاني", "") shouldBe "السطر الأول"
        }
        withClue("قص 24 بصفارة") {
            val long = "خ".repeat(30)
            DrsMyTexts.previewLabel(long, "") shouldBe "خ".repeat(24) + "…"
        }
        withClue("عند الحد تمامًا لا صفارة") {
            DrsMyTexts.previewLabel("خ".repeat(24), "") shouldBe "خ".repeat(24)
        }
        withClue("فراغات تُقص") {
            DrsMyTexts.previewLabel("   نص   ", "   ") shouldBe "نص"
        }
    }

    // ------------------------------------------------------------------
    // القوالب المشتركة مع محرك الاختصارات — توسّع عند الإدراج
    // ------------------------------------------------------------------

    test("العقد: القوالب تفوَّض لمحرك الاختصارات حرفيًا") {
        withClue("بلا قالب = المرور الصامت") {
            DrsMyTexts.expand("نص ثابت") shouldBe "نص ثابت"
        }
        withClue("{newline} يتوسع سطرًا جديدًا") {
            DrsMyTexts.expand("أ{nline}ب".replace("nline", "newline")) shouldBe "أ\nب"
        }
        withClue("{clipboard} يتغذى من الحافظة وقت الإدراج") {
            DrsMyTexts.expand("قبل {clipboard} بعد", "اللصاقة") shouldBe "قبل اللصاقة بعد"
            DrsMyTexts.expand("قبل {clipboard} بعد", null) shouldBe "قبل  بعد"
        }
        withClue("المتغير المجهول يبقى حرفيًا — النص لا يُفقَد أبدًا") {
            DrsMyTexts.expand("يا {mystery}") shouldBe "يا {mystery}"
        }
        DrsMyTexts.isTemplate("سلام {date}") shouldBe true
        DrsMyTexts.isTemplate("سلام") shouldBe false
    }

    test("العقد: {cursor} يُستخرج بإزاحة صحيحة ويبقى سليمًا بلا وسم") {
        DrsMyTexts.hasCursorMarker("عزيزي {cursor} تحية") shouldBe true
        val (clean, offset) = DrsMyTexts.extractCursorMarker("عزيزي {cursor} تحية")
        clean shouldBe "عزيزي  تحية"
        offset shouldBe "عزيزي ".length
        DrsMyTexts.hasCursorMarker("بلا وسم") shouldBe false
        DrsMyTexts.extractCursorMarker("بلا وسم") shouldBe ("بلا وسم" to -1)
    }

    // ------------------------------------------------------------------
    // عقد فك ترميز الحالة (الترقية من الحالات القديمة)
    // ------------------------------------------------------------------

    test("العقد: حالة قديمة بلا حقول نصوصي تفك إلى افتراضات سليمة") {
        val legacy = """{"version":1,"shortcuts":[]}"""
        val state = json.decodeFromString<DrsState>(legacy)
        state.myTexts shouldBe emptyList()
        state.nextMyTextId shouldBe 1L
        state.myTextsEnabled shouldBe true
        state.usage.myTextsUses shouldBe 0L
        state.dailyStats.isEmpty() shouldBe true
    }

    test("العقد: الترميز ذهابًا وعودة يحفظ نصوصي حرفيًا") {
        val state = DrsState(
            myTexts = listOf(
                entry(7, "عنوان\nبسطرين", label = "بيت", category = "عناوين", pinned = true, updatedAtMs = 42),
            ),
            nextMyTextId = 8L,
        )
        val encoded = json.encodeToString(DrsState.serializer(), state)
        val decoded = json.decodeFromString<DrsState>(encoded)
        decoded shouldBe state
        decoded.myTexts.single().pinned shouldBe true
        decoded.myTexts.single().text shouldBe "عنوان\nبسطرين"
    }
})
