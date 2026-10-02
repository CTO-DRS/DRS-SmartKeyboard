/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * الإصدار العام v1.2.0 — قاموس التشكيل الموسع (the 3000-word vocalization
 * lexicon): the strict file contract, the «البذرة تفوز» resolution and
 * the real asset audit (3000 entries, zero rejections, the nine
 * canonical marks only). Pure JVM — the asset is read straight from the
 * source tree.
 */
class DrsPublicV120LexiconTests : FunSpec({

    // -------------------------------------------------------------
    // المحلل العاقد — the strict contract parser
    // -------------------------------------------------------------

    test("a valid entry parses into stripped → vocalized") {
        val result = DrsTashkeelLexicon.parse(listOf("كتاب\tكِتَاب"))
        result.rejected shouldBe 0
        result.entries["كتاب"] shouldBe "كِتَاب"
    }

    test("comments and blank lines are skipped, not rejected") {
        val result = DrsTashkeelLexicon.parse(
            listOf(
                "# a comment",
                "",
                "   ",
                "كتاب\tكِتَاب",
            ),
        )
        result.rejected shouldBe 0
        result.entries.size shouldBe 1
    }

    test("a line without a tab is rejected") {
        DrsTashkeelLexicon.parse(listOf("كتاب كِتَاب")).rejected shouldBe 1
        DrsTashkeelLexicon.parse(listOf("كتاب\t")).rejected shouldBe 1
        DrsTashkeelLexicon.parse(listOf("\tكِتَاب")).rejected shouldBe 1
    }

    test("an entry whose strip does not reproduce the key is rejected") {
        DrsTashkeelLexicon.parse(listOf("كتب\tكِتَاب")).rejected shouldBe 1
    }

    test("a vocalized form without any mark is rejected as pointless") {
        DrsTashkeelLexicon.parse(listOf("كتاب\tكتاب")).rejected shouldBe 1
    }

    test("a combining mark outside the nine canonical marks is rejected") {
        // U+0653 ARABIC MADDAH ABOVE — a real combining mark that is NOT
        // one of the nine canonical harakat.
        DrsTashkeelLexicon.parse(listOf("مدرسة\tمَدْرَسَ\u0653ة")).rejected shouldBe 1
    }

    test("a repeated key keeps its first form — البذرة تفوز") {
        val result = DrsTashkeelLexicon.parse(
            listOf(
                "علم\tعِلْم",
                "علم\tعَلَمَ",
            ),
        )
        result.entries["علم"] shouldBe "عِلْم"
        result.rejected shouldBe 1
    }

    test("the tatweel is a stretch, never a mark — it survives stripping") {
        // كِتـَاب carries a tatweel between ت and ا; stripping removes the
        // marks AND the tatweel and must reproduce the key exactly.
        val result = DrsTashkeelLexicon.parse(listOf("كتاب\tكِتـَاب"))
        result.rejected shouldBe 0
        result.entries["كتاب"] shouldBe "كِتـَاب"
    }

    // -------------------------------------------------------------
    // تدقيق الأصل الحقيقي — the real asset audit
    // -------------------------------------------------------------

    val assetLines: List<String> by lazy {
        val candidates = listOf(
            File("src/main/assets/drs/tashkeel_lexicon.txt"),
            File("app/src/main/assets/drs/tashkeel_lexicon.txt"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("tashkeel_lexicon.txt not found in ${candidates.map { it.absolutePath }}")
        file.readLines()
    }

    test("the shipped asset carries exactly 3000 entries with zero rejections") {
        val result = DrsTashkeelLexicon.parse(assetLines)
        result.entries.size shouldBe 3000
        result.rejected shouldBe 0
    }

    test("every shipped entry satisfies the full contract") {
        val parsed = DrsTashkeelLexicon.parse(assetLines).entries
        val marks = DrsHarakat.MARKS.toHashSet()
        parsed.forEach { (key, vocalized) ->
            DrsHarakatWordOps.stripDiacritics(vocalized) shouldBe key
            vocalized.any { it in marks } shouldBe true
            key shouldBe key.trim()
        }
    }

    test("the shipped lexicon spot-checks keep their canonical forms") {
        val parsed = DrsTashkeelLexicon.parse(assetLines).entries
        parsed["يونس"] shouldBe "يُونُسَ"
        parsed["يونيو"] shouldBe "يُونِيُو"
    }

    // -------------------------------------------------------------
    // البذرة تفوز + التركيب الذري — seed-wins and the atomic install
    // -------------------------------------------------------------

    test("the seed lexicon serves before any asset is installed") {
        // The seed of DrsWordTashkeel answers the function words even
        // with an empty installed map.
        DrsWordTashkeel.vocalize("من") shouldBe "مِنْ"
        DrsWordTashkeel.size shouldBe 107
    }

    test("vocalize consults the installed asset after the seed") {
        val before = DrsTashkeelLexicon.isLoaded
        try {
            DrsTashkeelLexicon.install(mapOf("مدرسة" to "مَدْرَسَة"))
            DrsTashkeelLexicon.isLoaded shouldBe true
            DrsWordTashkeel.vocalize("مدرسة") shouldBe "مَدْرَسَة"
            DrsWordTashkeel.extendedSize shouldBe 1
        } finally {
            // Restore the pre-test state honestly.
            DrsTashkeelLexicon.install(emptyMap())
            DrsTashkeelLexicon.isLoaded shouldBe before
        }
    }

    test("the seed wins over a conflicting installed form") {
        try {
            DrsTashkeelLexicon.install(mapOf("من" to "مَنْ"))
            // The seed form is canonical and must win.
            DrsWordTashkeel.vocalize("من") shouldBe "مِنْ"
        } finally {
            DrsTashkeelLexicon.install(emptyMap())
        }
    }

    test("an unknown word returns null — the UI stays honest") {
        DrsTashkeelLexicon.install(emptyMap())
        DrsWordTashkeel.vocalize("كلمة-مجهولة-تماما") shouldBe null
        DrsTashkeelLexicon.vocalize("كلمة-مجهولة-تماما") shouldBe null
    }
})
