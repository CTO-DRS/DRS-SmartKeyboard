/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.drs.ai.LatinNormBridge
import com.drs.smartkeyboard.ime.nlp.latin.DictEntry
import com.drs.smartkeyboard.ime.nlp.latin.DictIndex
import com.drs.smartkeyboard.ime.nlp.latin.DrsFusedCorrection
import com.drs.smartkeyboard.ime.nlp.latin.LatinWordNormalize
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * DRS v2.12.0 «تعميق محركات المرحلة 1» — the REAL-ERRORS reference
 * benchmark (مرجعية أخطاء حقيقية) + the deepened-engine contracts.
 *
 * The benchmark measures the correction engine ITSELF — the pure
 * [DrsFusedCorrection] pipeline the provider delegates to — over a curated
 * dataset of REAL Arabic typing errors, against the REAL shipped Arabic
 * dictionary (the 50k-word ar.json asset, loaded as-is). No synthetic
 * mutations: every entry is a documented, universally-accepted
 * wrong-spelling → correct-spelling pair, grouped by error class
 * (hamza seats, ta-marbuta, keyboard adjacency, fused phrases, …).
 *
 * ACCURACY DEFINITION (honest and pinned): a case is CORRECT when the
 * expected spelling appears among the TOP-3 suggestions the engine emits
 * for the typo. The round's target, verbatim from the roadmap:
 * **دقة التصحيح ≥ 85% على مرجعية أخطاء حقيقية**. Multi-word phrase
 * corrections are measured at the hard-correction API (their offering path
 * is the composing region, not the word strip).
 *
 * The dataset deliberately includes the KNOWN-BUT-WRONG class — typos that
 * are dictionary entries in their own right (مسئول، لاكن، اللذي، هاذا،
 * شئ) — and prefixed forms that only the new affix-splitting layer can
 * reach (والمسئول → والمسؤول).
 */
class DrsV21200RealErrorsBenchmark : FunSpec({

    // ------------------------------------------------------------------
    // The real dictionary — loaded exactly like the provider loads it
    // ------------------------------------------------------------------

    LatinNormBridge.normalize = LatinWordNormalize::normalize

    val assetFile = listOf(
        File("src/main/assets/ime/dict/ar.json"),
        File("app/src/main/assets/ime/dict/ar.json"),
    ).firstOrNull { it.isFile } ?: error("ar.json asset not found — run from the module or repo root")

    val dict: Map<String, Int> = Json { ignoreUnknownKeys = true }
        .decodeFromString(MapSerializer(String.serializer(), Int.serializer()), assetFile.readText())

    val index = DictIndex(
        dict.map { (word, freq) -> DictEntry(LatinWordNormalize.normalize(word), word, freq) }
            .sortedBy { it.norm },
    )

    fun normalize(word: String): String = LatinWordNormalize.normalize(word)

    /** The engine the provider itself uses — never a test-side copy. */
    fun correctionsFor(raw: String): List<String> =
        DrsFusedCorrection.corrections(
            index = index,
            raw = raw,
            isArabic = true,
            maxCost = 12,   // STANDARD tier budget
            lengthBand = 4,
            maxCandidateCount = 5,
            normalize = ::normalize,
        ).map { it.text }

    // ------------------------------------------------------------------
    // The real-errors reference dataset (مرجعية أخطاء حقيقية)
    // typo → expected, category, isPhrase (multi-word correction API)
    // ------------------------------------------------------------------

    data class Case(val typo: String, val expected: String, val category: String, val phrase: Boolean = false)

    val cases = listOf(
        // همزات القطع — omitted opening hamza (the classicclass)
        Case("اذا", "إذا", "همزات القطع"), Case("انا", "أنا", "همزات القطع"),
        Case("اذن", "إذن", "همزات القطع"), Case("الان", "الآن", "همزات القطع"),
        Case("اكثر", "أكثر", "همزات القطع"), Case("اقل", "أقل", "همزات القطع"),
        Case("اولئك", "أولئك", "همزات القطع"), Case("الا", "إلا", "همزات القطع"),
        Case("انت", "أنت", "همزات القطع"), Case("اهم", "أهم", "همزات القطع"),
        Case("افضل", "أفضل", "همزات القطع"), Case("الي", "إلى", "همزات القطع"),
        Case("اننا", "إننا", "همزات القطع"),
        Case("الانسان", "الإنسان", "همزات القطع"), Case("انسان", "إنسان", "همزات القطع"),
        // الهمزة المتوسطة — the seated-hamza class (مسئول carries a dict entry!)
        Case("مسئول", "مسؤول", "همزات وسطى"), Case("مسئولية", "مسؤولية", "همزات وسطى"),
        Case("مسئلة", "مسألة", "همزات وسطى"), Case("شئ", "شيء", "همزات وسطى"),
        Case("شئون", "شؤون", "همزات وسطى"),
        // العبارات المركّبة — fused praise phrases (composing-region API)
        Case("انشاء الله", "إن شاء الله", "عبارات مركبة", phrase = true),
        Case("ان شاء الله", "إن شاء الله", "عبارات مركبة", phrase = true),
        Case("انشاءالله", "إن شاء الله", "عبارات مركبة", phrase = true),
        Case("ماشاء الله", "ما شاء الله", "عبارات مركبة", phrase = true),
        Case("ماشاءالله", "ما شاء الله", "عبارات مركبة", phrase = true),
        Case("الحمدلله", "الحمد لله", "عبارات مركبة", phrase = true),
        Case("بأذن الله", "بإذن الله", "عبارات مركبة", phrase = true),
        Case("انشالله", "إن شاء الله", "عبارات مركبة", phrase = true),
        Case("الله اكبر", "اللهُ أكبر", "عبارات مركبة", phrase = true),
        // التاء المربوطة — the ta-marbuta class (norm-collapse + quantized)
        Case("مدرسه", "مدرسة", "تاء مربوطة"), Case("جامعه", "جامعة", "تاء مربوطة"),
        Case("سياره", "سيارة", "تاء مربوطة"), Case("حديقه", "حديقة", "تاء مربوطة"),
        Case("شركه", "شركة", "تاء مربوطة"), Case("وزاره", "وزارة", "تاء مربوطة"),
        Case("درجه", "درجة", "تاء مربوطة"), Case("نقطه", "نقطة", "تاء مربوطة"),
        Case("شخصيه", "شخصية", "تاء مربوطة"), Case("صفحه", "صفحة", "تاء مربوطة"),
        Case("الجمله", "الجملة", "تاء مربوطة"), Case("الحيات", "الحياة", "تاء مربوطة"),
        Case("الخاصه", "الخاصة", "تاء مربوطة"), Case("الجمعه", "الجمعة", "تاء مربوطة"),
        Case("الصلاه", "الصلاة", "تاء مربوطة"),
        // الألف المقصورة
        Case("مستشفي", "مستشفى", "ألف مقصورة"),
        // الكلمات المعجمية الخاطئة — typos that ARE dict entries (composed path)
        Case("لاكن", "لكن", "معجمية خاطئة"), Case("اللذي", "الذي", "معجمية خاطئة"),
        Case("هاذا", "هذا", "معجمية خاطئة"),
        // السوابق الصرفية — only the NEW affix-splitting layer reaches these
        Case("والمسئول", "والمسؤول", "سوابق صرفية"),
        Case("بالمسئول", "بالمسؤول", "سوابق صرفية"),
        Case("فالمسئول", "فالمسؤول", "سوابق صرفية"),
        Case("واللذي", "والذي", "سوابق صرفية"),
        Case("والجامعه", "والجامعة", "سوابق صرفية"),
        // جوار لوحة المفاتيح — real fat-finger slips on the Arabic layout
        Case("شرزة", "شركة", "جوار لوحة المفاتيح"), Case("درخة", "درجة", "جوار لوحة المفاتيح"),
        Case("صفجة", "صفحة", "جوار لوحة المفاتيح"), Case("يوط", "يوم", "جوار لوحة المفاتيح"),
        // الحروف الزائدة — doubled-letter typos
        Case("اللله", "الله", "حروف زائدة"), Case("جمميل", "جميل", "حروف زائدة"),
        Case("الكتابب", "الكتاب", "حروف زائدة"), Case("مشكوور", "مشكور", "حروف زائدة"),
        // الصوتيات — identical-sounding letters (ت/ط د/ض ذ/ظ ض/ظ class)
        Case("السحيح", "الصحيح", "صوتيات"), Case("حفض", "حفظ", "صوتيات"),
    )

    // ------------------------------------------------------------------
    // THE BENCHMARK — دقة التصحيح على مرجعية أخطاء حقيقية ≥ 85%
    // ------------------------------------------------------------------

    test("دقة التصحيح على المرجعية الحقيقية ≥ 85% (أوائل 3 مرشحين)") {
        var ok = 0
        val failures = mutableListOf<String>()
        val byCategory = linkedMapOf<String, Pair<Int, Int>>() // cat to (ok, total)

        for (case in cases) {
            val (hit, top3) = if (case.phrase) {
                val got = runCatching { com.drs.smartkeyboard.drs.ai.DrsArabicCorrector.hardCorrectionFor(case.typo, ::normalize) }.getOrNull()
                (got == case.expected) to (got ?: "—")
            } else {
                val texts = runCatching { correctionsFor(case.typo) }.getOrDefault(emptyList())
                (case.expected in texts.take(3)) to texts.take(3).toString()
            }
            val (cOk, cTotal) = byCategory[case.category] ?: (0 to 0)
            byCategory[case.category] = (cOk + if (hit) 1 else 0) to (cTotal + 1)
            if (hit) {
                ok++
            } else {
                failures.add("  [${case.category}] ${case.typo} ← ${case.expected} | أعلى 3: $top3")
            }
        }

        val rate = ok.toDouble() / cases.size
        println("═══ مرجعية الأخطاء الحقيقية v2.12.0 ═══")
        byCategory.forEach { (cat, pair) ->
            val (cOk, cTotal) = pair
            println("  $cat: $cOk/$cTotal")
        }
        println("  الإجمالي: $ok/${cases.size} = ${"%.1f".format(rate * 100)}%")
        if (failures.isNotEmpty()) {
            println("  الإخفاقات:")
            failures.forEach(::println)
        }

        // THE ROUND'S TARGET, PINNED: ≥ 85% top-3 on the real-errors reference.
        (rate >= 0.85) shouldBe true
    }

    test("المرجعية حتمية — تشغيلان متتاليان ينتجان الترتيب نفسه") {
        val first = correctionsFor("والمسئول") + correctionsFor("مسئول") + correctionsFor("مدرسه")
        val second = correctionsFor("والمسئول") + correctionsFor("مسئول") + correctionsFor("مدرسه")
        first shouldBe second
    }

    // ------------------------------------------------------------------
    // Deepened-engine contracts
    // ------------------------------------------------------------------

    test("التقسيم الصرفي: السوابق الموثقة فقط، والجذع لا يقل عن 3 أحرف") {
        val variants = DrsFusedCorrection.affixVariants(normalize("والمسئول"))
        variants.first() shouldBe ("" to normalize("والمسئول"))
        variants.drop(1).map { it.first } shouldBe listOf("وال", "و")
        // short words never split
        DrsFusedCorrection.affixVariants(normalize("بكل")).size shouldBe 1
        // capped variants
        DrsFusedCorrection.affixVariants(normalize("والبالLonglong")).size.let { it <= 4 } shouldBe true
    }

    test("الطبقة الصرفية: والمسئول يصل والمسؤول عبر الجذع") {
        val texts = correctionsFor("والمسئول")
        texts.first() shouldBe "والمسؤول"
    }

    test("نموذج التكلفة: الصوتيات المتطابقة تكلفة 2 والجوار الصفي 2-بعيد تكلفة 2") {
        val c = com.drs.smartkeyboard.drs.ai.DrsArabicCorrector.ArabicCostModel
        c.substitutionCost('ت', 'ط') shouldBe 2
        c.substitutionCost('د', 'ض') shouldBe 2
        c.substitutionCost('ذ', 'ظ') shouldBe 2
        c.substitutionCost('ض', 'ظ') shouldBe 2
        c.substitutionCost('ض', 'ث') shouldBe 2 // same row, two apart
        c.substitutionCost('ض', 'ص') shouldBe 1 // still the adjacency anchor
    }

    test("الرأس المركب: كلمة خاطئة معجمية يصححها حتى لو اكتملت في القاموس") {
        val corrector = com.drs.smartkeyboard.drs.ai.DrsArabicCorrector
        // مسئول is a dict entry: the composed path still yields مسؤول
        corrector.composedSuggestionFor("مسئول", listOf("مسئولية", "مسئولون"), ::normalize) shouldBe "مسؤول"
        // already on the strip → honest no-op
        corrector.composedSuggestionFor("مسئول", listOf("مسؤول"), ::normalize) shouldBe null
        // correct words never hit the table
        corrector.composedSuggestionFor("مسؤول", emptyList(), ::normalize) shouldBe null
        corrector.composedSuggestionFor("", emptyList(), ::normalize) shouldBe null
    }

    test("كسر التعادل القانوني في byNorm: ة تتفوق على ه ثم الأعلى ترددًا") {
        val normH = normalize("مدرسه") // both spellings collapse to this
        val taLowFreq = DictEntry(normH, "مدرسة", 50)
        val hahHighFreq = DictEntry(normH, "مدرسه", 200)
        DictIndex(listOf(hahHighFreq, taLowFreq)).byNorm[normH]!!.word shouldBe "مدرسة"

        val normY = normalize("علي") // no ة/ه pair — frequency decides
        val lowY = DictEntry(normY, "علي", 10)
        val highY = DictEntry(normY, "علي", 90)
        DictIndex(listOf(lowY, highY)).byNorm[normY]!!.freq shouldBe 90
    }
})
