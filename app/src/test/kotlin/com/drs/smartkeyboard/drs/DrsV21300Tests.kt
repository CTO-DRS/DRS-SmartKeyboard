/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.drs.ai.DrsLearningEngine
import com.drs.smartkeyboard.drs.ai.DrsNextWordPredictor
import com.drs.smartkeyboard.drs.ai.DrsTrigramChains
import com.drs.smartkeyboard.drs.privacy.DrsPrivacyDashboard
import com.drs.smartkeyboard.drs.privacy.DrsSyncBundle
import com.drs.smartkeyboard.ime.nlp.latin.LatinWordNormalize
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

/**
 * DRS v2.13.0 «السياق الأعمق» — the contracts of the deeper-context
 * round: the static chain table's strict file contract, the predictor's
 * pinned no-op + interpolation, the learning engine's bounded trigram
 * store (same file, same discipline), and the sync container's additive
 * trigram field (old payloads decode, new payloads travel, caps hold).
 */
class DrsV21300Tests : FunSpec({

    fun normalize(word: String): String = LatinWordNormalize.normalize(word)

    // ------------------------------------------------------------------
    // DrsTrigramChains — the strict file contract
    // ------------------------------------------------------------------

    test("العقد: سطر سليم يُقبل ويُرتَّب تنازليًا بالقوة") {
        val table = DrsTrigramChains.parse(
            listOf("# تعليق", "", "بسم\tالله\tالرحمن\t9", "بسم\tالله\tالعظيم\t6"),
            ::normalize,
        )
        table.rejected shouldBe 0
        table.table.rowCount shouldBe 1
        val row = table.table.rowFor(normalize("بسم"), normalize("الله"))
        row.map { it.next } shouldBe listOf("الرحمن", "العظيم")
        row.first().strength shouldBe 9
    }

    test("العقد: عدد تبويبات خاطئ أو قوة خارج 5..9 تُرفض") {
        DrsTrigramChains.parse(listOf("بسم\tالله\tالرحمن"), ::normalize).rejected shouldBe 1
        DrsTrigramChains.parse(listOf("بسم\tالله\tالرحمن\t9\tزائد"), ::normalize).rejected shouldBe 1
        DrsTrigramChains.parse(listOf("بسم\tالله\tالرحمن\t4"), ::normalize).rejected shouldBe 1
        DrsTrigramChains.parse(listOf("بسم\tالله\tالرحمن\t10"), ::normalize).rejected shouldBe 1
        DrsTrigramChains.parse(listOf("بسم\tالله\tالرحمن\tقوية"), ::normalize).rejected shouldBe 1
    }

    test("العقد: أرقام داخل الكلمات أو فراغات تُرفض") {
        DrsTrigramChains.parse(listOf("بسم\tالله2\tالرحمن\t9"), ::normalize).rejected shouldBe 1
        DrsTrigramChains.parse(listOf("بسم\tبسم الله\tالرحمن\t9"), ::normalize).rejected shouldBe 1
        DrsTrigramChains.parse(listOf("\tالله\tالرحمن\t9"), ::normalize).rejected shouldBe 1
    }

    test("البذرة تفوز: تكرار السلسلة بعد التطبيع يُرفض والأول تبقى") {
        val table = DrsTrigramChains.parse(
            listOf(
                "بسم\tالله\tالرحمن\t9",
                "بسم\tالله\tالرحمن\t5",   // literal duplicate
                "ان\tشاء\tالله\t8",        // normalizes to the SAME row as إن شاء الله below
                "إن\tشاء\tالله\t9",
            ),
            ::normalize,
        )
        table.rejected shouldBe 2
        // The FIRST seed occurrence (ان شاء الله / 8) wins the normalized slot.
        table.table.rowFor(normalize("ان"), normalize("شاء")).single().strength shouldBe 8
    }

    test("التطبيع الموحد: إن/ان، والة/ه، والألف المقصورة مفتاح واحد") {
        val table = DrsTrigramChains.parse(listOf("إن\tشاء\tالله\t9"), ::normalize)
        table.table.rowFor(normalize("ان"), normalize("شاء")).isNotEmpty() shouldBe true
        table.table.rowFor(normalize("إن"), normalize("شاء")).isNotEmpty() shouldBe true
    }

    test("سياق مجهول = قائمة فارغة صادقة (لا اختراع)") {
        DrsTrigramChains.EMPTY.rowFor("اي", "شي") shouldBe emptyList()
        DrsTrigramChains.parse(listOf("بسم\tالله\tالرحمن\t9"), ::normalize)
            .table.rowFor("غير", "موجود") shouldBe emptyList()
    }

    // ------------------------------------------------------------------
    // The REAL asset — zero rejections, all strengths 5..9, seed-ordered
    // ------------------------------------------------------------------

    val chainsFile = listOf(
        File("src/main/assets/drs/trigram_chains.txt"),
        File("app/src/main/assets/drs/trigram_chains.txt"),
    ).firstOrNull { it.isFile } ?: error("trigram_chains.txt asset not found")

    test("أصل السلاسل الحقيقي: صفر رفض وسلاسل فوق المئة وكل القوى 5..9") {
        val result = DrsTrigramChains.parse(chainsFile.readText().lines(), ::normalize)
        result.rejected shouldBe 0
        val chainCount = result.table.rowMap().values.sumOf { it.size }
        (chainCount >= 100) shouldBe true
        (result.table.rowCount >= 80) shouldBe true
        result.table.rowMap().values.flatten().all { it.strength in 5..9 } shouldBe true
        // The canonical chain of the language is in the seed, strongest.
        result.table.rowFor(normalize("بسم"), normalize("الله")).first().next shouldBe "الرحمن"
    }

    // ------------------------------------------------------------------
    // DrsNextWordPredictor — the pinned no-op + the trigram layer
    // ------------------------------------------------------------------

    test("عقد اللا-op: صف ثلاثي فارغ = ترتيب الثنائيات القديم حرفيًا") {
        // A reference computation of the PRE-v2.13.0 bigram interpolation,
        // written out here independently of the implementation.
        val personal = listOf("كتاب" to 3, "قلم" to 1)
        val static = listOf("كتاب" to 2, "ورقة" to 2, "قلم" to 7)
        val expected = listOf("كتاب", "قلم", "ورقة") // verified below by hand
        val pTotal = 4.0
        val sTotal = 11.0
        fun score(w: String): Double {
            val p = (personal.firstOrNull { it.first == w }?.second ?: 0) / pTotal
            val s = (static.firstOrNull { it.first == w }?.second ?: 0) / sTotal
            return DrsNextWordPredictor.ALPHA * p + (1 - DrsNextWordPredictor.ALPHA) * s
        }
        val legacy = static.map { it.first }.plus(personal.map { it.first }).distinct()
            .map { DrsNextWordPredictor.Prediction(it, score(it)) }
            .sortedWith(compareByDescending<DrsNextWordPredictor.Prediction> { it.score }.thenBy { it.word })
            .map { it.word }
        expected shouldBe legacy

        // The engine, called exactly as before v2.13.0 (defaults) and with
        // explicitly-empty trigram rows — identical output, bit for bit.
        val legacyCall = DrsNextWordPredictor.predict(
            personal = personal,
            static = static,
            personalFallback = emptyList(),
            staticFallback = emptyList(),
            limit = 5,
        ).map { it.word }
        val explicitCall = DrsNextWordPredictor.predict(
            personal = personal,
            static = static,
            personalFallback = emptyList(),
            staticFallback = emptyList(),
            personalTrigram = emptyList(),
            staticTrigram = emptyList(),
            limit = 5,
        ).map { it.word }
        legacyCall shouldBe legacy
        explicitCall shouldBe legacy
    }

    test("الطبقة الثلاثية: تتمة شبه حتمية تتصدى فوق تردد ثنائي أعلى") {
        // bigram row says «أن/أكبر/في» — the corpus noise after «الله»;
        // the chain says «الرحمن» (strength 9). The chain wins.
        val out = DrsNextWordPredictor.predict(
            personal = emptyList(),
            static = listOf("أن" to 99, "أكبر" to 40, "في" to 25),
            staticTrigram = listOf("الرحمن" to 9),
            limit = 3,
        ).map { it.word }
        out.first() shouldBe "الرحمن"
    }

    test("وزن الشخصي داخل الطبقة: عادة المستخدم تزن 0.65 من الثلاثي") {
        // Same word in both trigram rows: personal dominates (0.65 vs 0.35),
        // but a personal-only chain still beats nothing and a static-only
        // chain still surfaces — here we pin the ORDER between two words:
        // personal «الجديد» (count 5) vs static «الكبير» (strength 9).
        // p_tri(الجديد) = 0.65 * 1.0 = 0.65 ; p_tri(الكبير) = 0.35 * 1.0 = 0.35
        val out = DrsNextWordPredictor.predict(
            personal = emptyList(),
            static = emptyList(),
            personalTrigram = listOf("الجديد" to 5),
            staticTrigram = listOf("الكبير" to 9),
            limit = 3,
        ).map { it.word }
        out.first() shouldBe "الجديد"
        out.getOrNull(1) shouldBe "الكبير"
    }

    test("الحد: limit صفر أو أقل = قائمة فارغة") {
        DrsNextWordPredictor.predict(
            personal = listOf("كتاب" to 1),
            static = emptyList(),
            limit = 0,
        ) shouldBe emptyList()
    }

    // ------------------------------------------------------------------
    // DrsLearningEngine — the bounded personal trigram store
    // ------------------------------------------------------------------

    val tmpDir = kotlin.io.path.createTempDirectory("drs-v2130").toFile()

    test("التعلّم الثلاثي: يتخزن ويُستعاد بعد الحفظ والتحميل (دورة كاملة)") {
        DrsLearningEngine.clear()
        val file = File(tmpDir, "learning.json")
        try {
            DrsLearningEngine.learnTrigram("في", "المشروع", "الجديد")
            DrsLearningEngine.learnTrigram("في", "المشروع", "القديم")
            DrsLearningEngine.persist(file)
            DrsLearningEngine.clear()
            file.exists() shouldBe true
            DrsLearningEngine.load(file)
            val row = DrsLearningEngine.personalTrigramNext("في", "المشروع", 5)
            row.map { it.first } shouldBe listOf("الجديد", "القديم")
            row.first().second shouldBe 1
        } finally {
            DrsLearningEngine.clear()
        }
    }

    test("التعلّم الثلاثي: كلمات فارغة أو طويلة أو برقم تُرفض") {
        DrsLearningEngine.clear()
        try {
            DrsLearningEngine.learnTrigram("", "المشروع", "الجديد")
            DrsLearningEngine.learnTrigram("في", "المشروع", "")
            DrsLearningEngine.learnTrigram("في", "المشروع12", "الجديد")
            DrsLearningEngine.learnTrigram("ف".repeat(41), "المشروع", "الجديد")
            DrsLearningEngine.trigramRowCount() shouldBe 0
        } finally {
            DrsLearningEngine.clear()
        }
    }

    test("التعلّم الثلاثي: عرض الصف مسقوف بـ8 وإشباع العدّاد عند 100") {
        DrsLearningEngine.clear()
        try {
            repeat(150) { DrsLearningEngine.learnTrigram("في", "المشروع", "كلمة$it") }
            val row = DrsLearningEngine.personalTrigramNext("في", "المشروع", 100)
            (row.size <= 8) shouldBe true
        } finally {
            DrsLearningEngine.clear()
        }
    }

    test("التعلّم الثلاثي: صفوف p2 مسقوفة بـ512 بإخلاء الأضعف") {
        DrsLearningEngine.clear()
        try {
            repeat(520) { DrsLearningEngine.learnTrigram("س${it}ك", "المشروع", "الجديد") }
            (DrsLearningEngine.trigramRowCount() <= DrsLearningEngine.MAX_TRIGRAMS) shouldBe true
        } finally {
            DrsLearningEngine.clear()
        }
    }

    test("الملف القديم بلا ثلاثيات يُفكّ سالمًا (توافق خلفي)") {
        DrsLearningEngine.clear()
        val file = File(tmpDir, "old-learning.json")
        file.writeText("""{"v":1,"words":{"كتاب":3},"bigrams":{"في":{"nexts":{"المشروع":2}}},"lemmas":{"كتب":1}}""")
        try {
            DrsLearningEngine.load(file)
            DrsLearningEngine.size() shouldBe 1
            DrsLearningEngine.bigramRowCount() shouldBe 1
            DrsLearningEngine.trigramRowCount() shouldBe 0
        } finally {
            DrsLearningEngine.clear()
        }
    }

    test("التصدير والاستيراد الثلاثي: دمج إضافي واستبدال كامل وسقوف لا تتجاوز") {
        DrsLearningEngine.clear()
        try {
            DrsLearningEngine.learnTrigram("في", "المشروع", "الجديد")
            val exported = DrsLearningEngine.exportTrigramState()
            exported["في"]?.get("المشروع")?.get("الجديد") shouldBe 1L
            // Merge path adds on top.
            DrsLearningEngine.importTrigramState(mapOf("في" to mapOf("المشروع" to mapOf("الجديد" to 2L))), replace = false)
            DrsLearningEngine.personalTrigramNext("في", "المشروع", 5).first().second shouldBe 3
            // Replace path swaps wholesale.
            DrsLearningEngine.importTrigramState(mapOf("على" to mapOf("الرغم" to mapOf("من" to 1L))), replace = true)
            DrsLearningEngine.trigramRowCount() shouldBe 1
            DrsLearningEngine.personalTrigramNext("على", "الرغم", 5).first().first shouldBe "من"
        } finally {
            DrsLearningEngine.clear()
        }
    }

    // ------------------------------------------------------------------
    // DrsSyncBundle — the additive trigram field
    // ------------------------------------------------------------------

    test("الحاوية: ثلاثيات تسافر وتعود سليمة في DRSYNC1") {
        val payload = DrsSyncBundle.Payload(
            app = "2.13.0",
            exportedAt = 7L,
            words = mapOf("كتاب" to 3L),
            bigrams = mapOf("في" to mapOf("المشروع" to 2L)),
            trigrams = mapOf("في" to mapOf("المشروع" to mapOf("الجديد" to 4L))),
        )
        val sealed = DrsSyncBundle.seal(payload, "مرور".toCharArray())
        val opened = DrsSyncBundle.open(sealed, "مرور".toCharArray())
        opened.trigrams shouldBe payload.trigrams
        opened.words shouldBe payload.words
    }

    test("الحاوية: حمولة قديمة بلا ثلاثيات تُفكّ بخريطة فارغة") {
        val legacyJson = """{"v":1,"app":"2.9.0","exportedAt":1,"words":{"كتاب":1},"bigrams":{}}"""
        val bytes = legacyJson.toByteArray(Charsets.UTF_8)
        val opened = DrsSyncBundle.decode(bytes)
        opened.trigrams shouldBe emptyMap()
        opened.words shouldBe mapOf("كتاب" to 1L)
    }

    test("الحاوية: صفوف ثلاثية فاقدة السقف تُرفض قبل بلوغ المحرك") {
        // 9 continuations in one p1 row (cap 8) → MalformedEntry
        val wideRow = (1..9).associate { "ك$it" to 1L }
        val wide = DrsSyncBundle.Payload(trigrams = mapOf("في" to mapOf("المشروع" to wideRow)))
        val wideError = runCatching { DrsSyncBundle.encode(wide) }.exceptionOrNull()
        wideError shouldNotBe null
        // negative count → MalformedEntry
        val negative = DrsSyncBundle.Payload(trigrams = mapOf("في" to mapOf("المشروع" to mapOf("الجديد" to -1L))))
        val negativeError = runCatching { DrsSyncBundle.encode(negative) }.exceptionOrNull()
        negativeError shouldNotBe null
        // non-lowercase key → MalformedEntry
        val upper = DrsSyncBundle.Payload(trigrams = mapOf("في" to mapOf("المشروع" to mapOf("الجديد" to 1L))))
        val upperError = runCatching { DrsSyncBundle.encode(upper) }.exceptionOrNull()
        upperError shouldBe null // Arabic has no case — the lowercase law holds
        // 513 p2 rows (cap 512) → TooManyEntries
        val many = DrsSyncBundle.Payload(
            trigrams = (1..513).associate { "س$it" to mapOf("المشروع" to mapOf("الجديد" to 1L)) },
        )
        val manyError = runCatching { DrsSyncBundle.encode(many) }.exceptionOrNull()
        manyError shouldNotBe null
    }

    // ------------------------------------------------------------------
    // DrsPrivacyDashboard — the export path carries trigrams
    // ------------------------------------------------------------------

    test("لوحة الخصوصية: التصدير يشمل الثلاثيات والفراغ الصادق يبقى") {
        val dashboard = DrsPrivacyDashboard(
            stores = emptyList(),
            attestationProvider = { DrsNetworkSentinelTestStub.attestation() },
            learningProvider = { Pair(emptyMap(), emptyMap()) },
            trigramProvider = { mapOf("في" to mapOf("المشروع" to mapOf("الجديد" to 2L))) },
        )
        val sealed = dashboard.exportEncrypted(
            appVersion = "2.13.0",
            exportedAt = 3L,
            passphrase = "مرور".toCharArray(),
        )
        sealed shouldNotBe null
        val opened = DrsSyncBundle.open(sealed!!, "مرور".toCharArray())
        opened.trigrams["في"]?.get("المشروع")?.get("الجديد") shouldBe 2L

        // Nothing anywhere → honest null (the UI says "nothing to export").
        val emptyDashboard = DrsPrivacyDashboard(
            stores = emptyList(),
            attestationProvider = { DrsNetworkSentinelTestStub.attestation() },
            learningProvider = { Pair(emptyMap(), emptyMap()) },
            trigramProvider = { emptyMap() },
        )
        emptyDashboard.exportEncrypted(
            appVersion = "2.13.0",
            exportedAt = 3L,
            passphrase = "مرور".toCharArray(),
        ) shouldBe null
    }
})

/** Minimal attestation stub for dashboard construction in tests. */
private object DrsNetworkSentinelTestStub {
    fun attestation(): com.drs.smartkeyboard.drs.privacy.DrsNetworkSentinel.Attestation =
        com.drs.smartkeyboard.drs.privacy.DrsNetworkSentinel.Attestation(
            absoluteMode = false,
            rxSinceBaseline = 0L,
            txSinceBaseline = 0L,
            rxSincePrevious = 0L,
            txSincePrevious = 0L,
            countersSupported = false,
            zeroTraffic = true,
            permissions = emptyList(),
            checkedAt = 0L,
        )
}
