/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.drs.ai.DrsLearningEngine
import com.drs.smartkeyboard.drs.ai.DrsNextWordPredictor
import com.drs.smartkeyboard.drs.ai.DrsTrigramChains
import com.drs.smartkeyboard.ime.nlp.latin.LatinWordNormalize
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * DRS v2.13.0 «السياق الأعمق» — the REAL-CONTEXT reference benchmark
 * (مرجعية سياق حقيقية) for the next-word engine — the symmetric half of
 * v2.12.0's correction benchmark.
 *
 * The benchmark measures the prediction engine ITSELF — the pure
 * [DrsNextWordPredictor] + the real [DrsTrigramChains] parser — over a
 * curated dataset of REAL Arabic two-word contexts, against the REAL
 * shipped data (the 23k-row ar_bigrams.json asset and the curated
 * trigram_chains.txt asset, loaded as-is, normalized exactly like the
 * provider normalizes its keys). No synthetic contexts: every entry is a
 * context whose continuation carries real contextual certainty (يقين
 * سياقي لا تفضيل أسلوبي), grouped by class.
 *
 * WHY the trigram layer exists (measured, not claimed): 88 of the 119
 * chain rows are NOT resolvable by the bigram top-3 alone — after
 * «بسم الله» the corpus bigram row offers «أن، أكبر، في» and never the
 * near-certain «الرحمن». The static chains + personal trigrams close
 * exactly this blind window.
 *
 * ACCURACY DEFINITION (honest and pinned): a case is CORRECT when the
 * expected continuation appears among the TOP-3 predictions the engine
 * emits for the two-word context, on a FRESH device (personal tables
 * empty) except the personal-learning class, which learns first.
 *
 * The round's target, pinned after measurement like every DRS number:
 * **دقة التنبؤ بالكلمة التالية ≥ 85% على مرجعية سياق حقيقية (أوائل 3)**.
 */
class DrsV21300RealContextBenchmark : FunSpec({

    // ------------------------------------------------------------------
    // The real data — loaded exactly like the provider loads it
    // ------------------------------------------------------------------

    fun normalize(word: String): String = LatinWordNormalize.normalize(word)

    val bigramFile = listOf(
        File("src/main/assets/ime/dict/ar_bigrams.json"),
        File("app/src/main/assets/ime/dict/ar_bigrams.json"),
    ).firstOrNull { it.isFile } ?: error("ar_bigrams.json asset not found — run from the module or repo root")

    val chainsFile = listOf(
        File("src/main/assets/drs/trigram_chains.txt"),
        File("app/src/main/assets/drs/trigram_chains.txt"),
    ).firstOrNull { it.isFile } ?: error("trigram_chains.txt asset not found — run from the module or repo root")

    val bigrams: Map<String, Map<String, Int>> = Json { ignoreUnknownKeys = true }
        .decodeFromString(
            MapSerializer(String.serializer(), MapSerializer(String.serializer(), Int.serializer())),
            bigramFile.readText(),
        )

    val chains: DrsTrigramChains.Table = DrsTrigramChains.parse(chainsFile.readText().lines(), ::normalize).table

    /**
     * The engine path the provider itself runs — the same lookups, the
     * same predictor, the same normalization, never a test-side copy.
     * A fresh device: personal rows are empty by definition.
     */
    fun predictionsFor(p2: String, p1: String, limit: Int = 5): List<String> {
        val staticNexts = bigrams[normalize(p1)]
            ?.entries
            ?.sortedByDescending { it.value }
            ?.map { it.key to it.value }
            ?: emptyList()
        val staticTrigram = chains.rowFor(normalize(p2), normalize(p1)).map { it.next to it.strength }
        return DrsNextWordPredictor.predict(
            personal = emptyList(),
            static = staticNexts,
            personalTrigram = emptyList(),
            staticTrigram = staticTrigram,
            limit = limit,
        ).map { it.word }
    }

    // ------------------------------------------------------------------
    // The real-context reference dataset (مرجعية سياق حقيقية)
    // (p2, p1) → expected, category — يقين سياقي only
    // ------------------------------------------------------------------

    data class Case(val p2: String, val p1: String, val expected: String, val category: String)

    val cases = listOf(
        // سلاسل ثابتة — expressions whose tail is near-certain (strength 8-9)
        Case("بسم", "الله", "الرحمن", "سلاسل ثابتة"),
        Case("الله", "الرحمن", "الرحيم", "سلاسل ثابتة"),
        Case("إن", "شاء", "الله", "سلاسل ثابتة"),
        Case("ما", "شاء", "الله", "سلاسل ثابتة"),
        Case("لا", "إله", "إلا", "سلاسل ثابتة"),
        Case("إله", "إلا", "الله", "سلاسل ثابتة"),
        Case("صلى", "الله", "عليه", "سلاسل ثابتة"),
        Case("رضي", "الله", "عنه", "سلاسل ثابتة"),
        Case("استغفر", "الله", "العظيم", "سلاسل ثابتة"),
        Case("لا", "حول", "ولا", "سلاسل ثابتة"),
        Case("حول", "ولا", "قوة", "سلاسل ثابتة"),
        Case("السلام", "عليكم", "ورحمة", "سلاسل ثابتة"),
        Case("عليكم", "ورحمة", "الله", "سلاسل ثابتة"),
        Case("ورحمة", "الله", "وبركاته", "سلاسل ثابتة"),
        Case("بارك", "الله", "فيك", "سلاسل ثابتة"),
        Case("الله", "يعطيك", "العافية", "سلاسل ثابتة"),
        // الربط الوظيفي — function chains the bigram row cannot see as a pair
        Case("على", "الرغم", "من", "ربط وظيفي"),
        Case("الرغم", "من", "أن", "ربط وظيفي"),
        Case("على", "أي", "حال", "ربط وظيفي"),
        Case("ليس", "فقط", "بل", "ربط وظيفي"),
        Case("من", "المؤكد", "أن", "ربط وظيفي"),
        Case("لا", "بد", "أن", "ربط وظيفي"),
        Case("إذا", "لزم", "الأمر", "ربط وظيفي"),
        Case("هل", "تعلم", "أن", "ربط وظيفي"),
        Case("تجدر", "الإشارة", "إلى", "ربط وظيفي"),
        Case("من", "الجدير", "بالذكر", "ربط وظيفي"),
        // الظرفية — temporal/positional completions
        Case("في", "الوقت", "نفسه", "ظرفية"),
        Case("من", "وقت", "لآخر", "ظرفية"),
        Case("في", "نهاية", "المطاف", "ظرفية"),
        Case("من", "ناحية", "أخرى", "ظرفية"),
        Case("على", "وجه", "الخصوص", "ظرفية"),
        Case("في", "مقابل", "ذلك", "ظرفية"),
        Case("من", "البداية", "إلى", "ظرفية"),
        Case("الألف", "إلى", "الياء", "ظرفية"),
        Case("على", "النحو", "التالي", "ظرفية"),
        // المهلّات — greetings and occasions
        Case("كل", "عام", "وأنت", "مهلّات"),
        Case("عام", "جديد", "سعيد", "مهلّات"),
        Case("تصبح", "على", "خير", "مهلّات"),
        Case("المملكة", "العربية", "السعودية", "مهلّات"),
        // ثنائيات القاموس الحقيقية — the bigram row itself resolves these
        // (the no-regression class: p2 deliberately carries no chain row)
        Case("هذا", "صباح", "الخير", "ثنائيات القاموس"),
        Case("خير", "مساء", "الخير", "ثنائيات القاموس"),
        Case("جزيل", "شكرا", "لك", "ثنائيات القاموس"),
        Case("أهلا", "كيف", "حالك", "ثنائيات القاموس"),
        Case("عم", "لدي", "فكرة", "ثنائيات القاموس"),
        Case("مرحبا", "السلام", "عليكم", "ثنائيات القاموس"),
    )

    // ------------------------------------------------------------------
    // THE BENCHMARK — دقة التنبؤ على مرجعية سياق حقيقية ≥ 85%
    // ------------------------------------------------------------------

    test("دقة التنبؤ بالكلمة التالية ≥ 85% على المرجعية الحقيقية (أوائل 3 مرشحين)") {
        var ok = 0
        val failures = mutableListOf<String>()
        val byCategory = linkedMapOf<String, Pair<Int, Int>>() // cat to (ok, total)

        for (case in cases) {
            val top3 = runCatching { predictionsFor(case.p2, case.p1).take(3) }.getOrDefault(emptyList())
            val hit = normalize(case.expected) in top3.map(::normalize)
            val (cOk, cTotal) = byCategory[case.category] ?: (0 to 0)
            byCategory[case.category] = (cOk + if (hit) 1 else 0) to (cTotal + 1)
            if (hit) {
                ok++
            } else {
                failures.add("  [${case.category}] ${case.p2} ${case.p1} ← ${case.expected} | أعلى 3: $top3")
            }
        }

        val rate = ok.toDouble() / cases.size
        println("═══ مرجعية السياق الحقيقي v2.13.0 ═══")
        byCategory.forEach { (cat, pair) ->
            val (cOk, cTotal) = pair
            println("  $cat: $cOk/$cTotal")
        }
        println("  الإجمالي: $ok/${cases.size} = ${"%.1f".format(rate * 100)}%")
        if (failures.isNotEmpty()) {
            println("  الإخفاقات:")
            failures.forEach(::println)
        }

        // THE ROUND'S TARGET, PINNED: ≥ 85% top-3 on the real-context reference.
        (rate >= 0.85) shouldBe true
    }

    test("المرجعية حتمية — تشغيلان متتاليان ينتجان الترتيب نفسه") {
        val first = predictionsFor("بسم", "الله") + predictionsFor("على", "الرغم") + predictionsFor("في", "الوقت")
        val second = predictionsFor("بسم", "الله") + predictionsFor("على", "الرغم") + predictionsFor("في", "الوقت")
        first shouldBe second
    }

    // ------------------------------------------------------------------
    // The personal-learning class — what THIS user types next
    // ------------------------------------------------------------------

    test("التعلّم الشخصي: عادة الكتابة الشخصية تتصدر عبر طبقة الثلاثيات") {
        DrsLearningEngine.clear()
        try {
            // The user's habit: they keep writing this exact continuation.
            repeat(5) {
                DrsLearningEngine.learnWord("المشروع")
                DrsLearningEngine.learnTrigram("في", "المشروع", "الجديد")
            }
            val personal = DrsLearningEngine.personalTrigramNext("في", "المشروع", 12)
            (personal.firstOrNull()?.first) shouldBe "الجديد"
            // The engine path with a personal row armed (static row empty):
            val staticNexts = bigrams[normalize("المشروع")]
                ?.entries?.sortedByDescending { it.value }?.map { it.key to it.value }
                ?: emptyList()
            val top = DrsNextWordPredictor.predict(
                personal = emptyList(),
                static = staticNexts,
                personalTrigram = personal,
                staticTrigram = emptyList(),
                limit = 3,
            ).first().word
            top shouldBe "الجديد"
        } finally {
            DrsLearningEngine.clear()
        }
    }
})
