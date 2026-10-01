/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.drs.ai.DrsArabicMorphology
import com.drs.smartkeyboard.drs.ai.DrsContextRanker
import com.drs.smartkeyboard.drs.ai.DrsLearningEngine
import com.drs.smartkeyboard.drs.ai.DrsMorphologyRanker
import com.drs.smartkeyboard.drs.ai.DrsNextWordPredictor
import com.drs.smartkeyboard.drs.ai.DrsWritingAssistant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

/**
 * DRS M1.1–M1.6 — the on-device Arabic intelligence contracts: the
 * morphological analyzer (two-pass, documented weights, ة/ت rotation),
 * the morphology ranker (1/1/2 boosts, stable, no root!=word guard), the
 * interpolated next-word predictor (α=0.65, lemma fallback 0.7), the
 * interrogative context ranker (2-token window), the writing assistant
 * (16 full-tail triggers, idempotent spacing), and the lemma-aware
 * personal learning (count/2 reservoir with floor 1).
 */
class DrsM1IntelligenceTests : FunSpec({

    context("M1.1 — DrsArabicMorphology") {
        test("bare 3-letter word: the TWO-PASS analysis never eats the root (كتاب stays whole)") {
            val analysis = DrsArabicMorphology.analyze("كتب")
            analysis.prefixes shouldBe emptyList()
            analysis.core shouldBe "كتب"
            analysis.root shouldBe "كتب" // فعل pattern: the word IS its root
            analysis.lemma shouldBe "كتب"
        }

        test("فاعل vs فعال: the vowel-letter positions are distinct and correct") {
            // كاتب = ف ا ع ل → root ك ت ب (alif at position 1 dropped)
            DrsArabicMorphology.analyze("كاتب").root shouldBe "كتب"
            DrsArabicMorphology.analyze("كاتب").pattern shouldBe "فاعل"
            // كتاب = ف ع ا ل shape (فعّال normalized) → root ك ت ب (seat alif at position 2 dropped)
            DrsArabicMorphology.analyze("كتاب").root shouldBe "كتب"
        }

        test("مكتوب (مفعول): root م dropped, و is the template's vowel letter") {
            val analysis = DrsArabicMorphology.analyze("مكتوب")
            analysis.pattern shouldBe "مفعول"
            analysis.root shouldBe "كتب"
            analysis.lemma shouldBe "مكتوب" // no ة → lemma = core
        }

        test("مدرسة carries the مفعلة weight and strips the ة from the lemma") {
            val analysis = DrsArabicMorphology.analyze("مدرسة")
            analysis.pattern shouldBe "مفعلة"
            analysis.root shouldBe "درس"
            analysis.lemma shouldBe "مدرس"
        }

        test("مدرست: the ة/ت rotation matches the same weight") {
            val analysis = DrsArabicMorphology.analyze("مدرست")
            analysis.pattern shouldBe "مفعلة"
            analysis.root shouldBe "درس"
        }

        test("prefix stripping: الكتاب loses only ال and keeps the stem") {
            val analysis = DrsArabicMorphology.analyze("الكتاب")
            analysis.prefixes shouldBe listOf("ال")
            analysis.core shouldBe "كتاب"
            analysis.suffixes shouldBe emptyList()
        }

        test("the LRU cache returns the SAME analysis instance for repeats") {
            val first = DrsArabicMorphology.analyze("الطالب")
            val second = DrsArabicMorphology.analyze("الطالب")
            (first === second) shouldBe true
        }

        test("non-Arabic and short words degrade to a trivial analysis honestly") {
            val latin = DrsArabicMorphology.analyze("hello")
            latin.root shouldBe null
            latin.lemma shouldBe "hello"
            val tiny = DrsArabicMorphology.analyze("ب")
            tiny.root shouldBe null
        }
    }

    context("M1.2 — DrsMorphologyRanker") {
        test("the candidate that IS the lemma gets the +2 top rank") {
            val ranked = DrsMorphologyRanker.rerank(
                "يكتب",
                listOf("في", "كتب", "على"),
            )
            ranked.first() shouldBe "كتب"
        }

        test("same-root candidates rise above unrelated ones (+1)") {
            val ranked = DrsMorphologyRanker.rerank(
                "الكتاب",
                listOf("جميل", "كاتب", "على"),
            )
            ranked.indexOf("كاتب") shouldBe 0 // shares the root كتب with كتاب
        }

        test("THE KILLER GUARD STAYS DEAD: a 3-letter word still earns root bonuses") {
            // prev كتب has root كتب == word (3 letters) — the old
            // root != word guard would void every bonus here.
            val ranked = DrsMorphologyRanker.rerank(
                "كتب",
                listOf("على", "كاتب"),
            )
            ranked.first() shouldBe "كاتب"
        }

        test("the reorder is STABLE: equal scores keep the frequency order") {
            val ranked = DrsMorphologyRanker.rerank(
                "كتب",
                listOf("أ", "ب", "ج"),
            )
            ranked shouldBe listOf("أ", "ب", "ج")
        }

        test("no previous word → documented no-op") {
            DrsMorphologyRanker.rerank(null, listOf("ب", "أ")) shouldBe listOf("ب", "أ")
        }
    }

    context("M1.3 — DrsNextWordPredictor") {
        test("interpolation: α=0.65 personal dominance on a known word") {
            val personal = listOf("السوق" to 10)
            val static = listOf("السوق" to 10, "البيت" to 10)
            val predictions = DrsNextWordPredictor.predict(personal, static, limit = 5)
            val market = predictions.first { it.word == "السوق" }
            val house = predictions.first { it.word == "البيت" }
            market.score shouldBe (0.65 * 1.0 + 0.35 * 0.5)
            house.score shouldBe (0.35 * 0.5)
            (market.score > house.score) shouldBe true
        }

        test("static-only words still predict on a fresh install") {
            val predictions = DrsNextWordPredictor.predict(
                personal = emptyList(),
                static = listOf("البيت" to 7, "المدرسة" to 3),
                limit = 5,
            )
            predictions.size shouldBe 2
            predictions[0].word shouldBe "البيت"
        }

        test("the lemma fallback applies the 0.7 discount to the discounted rows") {
            val predictions = DrsNextWordPredictor.predict(
                personal = emptyList(),
                static = emptyList(),
                personalFallback = listOf("السوق" to 10),
                staticFallback = emptyList(),
                limit = 3,
            )
            val market = predictions.first { it.word == "السوق" }
            market.score shouldBe (0.7 * 0.65)
            (market.score in 0.0..1.0) shouldBe true
        }

        test("empty rows → empty predictions (no fabricated candidates)") {
            DrsNextWordPredictor.predict(emptyList(), emptyList(), limit = 5) shouldBe emptyList()
        }

        test("limit=0 returns nothing; ties order deterministically") {
            DrsNextWordPredictor.predict(listOf("أ" to 1), listOf("ب" to 1), limit = 0) shouldBe emptyList()
            val tied = DrsNextWordPredictor.predict(listOf("ب" to 5), listOf("ب" to 5), limit = 5)
            tied.size shouldBe 1
        }
    }

    context("M1.4 — DrsContextRanker") {
        test("the window is TWO tokens: the interrogative behind one word is seen") {
            // «ماذا تريد …» — at prediction time the cursor sits after
            // تريد؛ the interrogative is exactly 2 back. A WINDOW=1 scan
            // (regression pin) of the LAST token alone would miss it.
            val tokens = listOf("ماذا", "تريد")
            DrsContextRanker.isInterrogativeContext(tokens) shouldBe true
            DrsContextRanker.WINDOW shouldBe 2
        }

        test("answer openers are promoted stably inside an interrogative context") {
            val candidates = listOf("البيت", "نعم", "في")
            val ranked = DrsContextRanker.rerank(listOf("هل", "جئت"), candidates) { it }
            ranked.first() shouldBe "نعم"
            // stability: the non-openers keep their relative order
            ranked.indexOf("البيت") shouldBe ranked.indexOf("في") - 1
        }

        test("outside an interrogative context the call is a documented no-op") {
            val candidates = listOf("البيت", "نعم", "في")
            DrsContextRanker.rerank(listOf("ذهبت", "الى"), candidates) { it } shouldBe candidates
        }

        test("short context (< window) never detects") {
            DrsContextRanker.isInterrogativeContext(listOf("ماذا")) shouldBe false
        }
    }

    context("M1.5 — DrsWritingAssistant") {
        test("there are exactly SIXTEEN phrase rewrites") {
            DrsWritingAssistant.PHRASES.size shouldBe 16
        }

        test("the tail trigger fires on FULL normalized equality") {
            val out = DrsWritingAssistant.improveTail("وشكرًا، انشاء الله")
            out shouldBe "وشكرًا، إن شاء الله"
        }

        test("a longer tail does NOT fire (containment must not corrupt mid-text)") {
            val before = DrsWritingAssistant.improveTail("انشاء اللهية")
            before shouldBe "انشاء اللهية" // the tail is اللهية، not the trigger
        }

        test("each rule is idempotent: improving twice changes nothing") {
            for ((_, replacement) in DrsWritingAssistant.PHRASES) {
                DrsWritingAssistant.improveTail(replacement) shouldBe DrsWritingAssistant.improveTail(
                    DrsWritingAssistant.improveTail(replacement),
                )
            }
        }

        test("normalizeSpacing is IDEMPOTENT by contract") {
            val samples = listOf("مرحبا   بالعالم  ؟", " سؤال ؟؟؟  ", "نص،مقسم،بفواصل")
            for (sample in samples) {
                val once = DrsWritingAssistant.normalizeSpacing(sample)
                DrsWritingAssistant.normalizeSpacing(once) shouldBe once
            }
        }

        test("normalizeSpacing removes the space before punctuation") {
            DrsWritingAssistant.normalizeSpacing("كيف حالك ؟") shouldBe "كيف حالك؟"
        }
    }

    context("M1.6 — lemma-aware personal learning") {
        test("learnWordWithLemma gives the stem a count/2 reservoir (floor 1)") {
            DrsLearningEngine.clear()
            DrsLearningEngine.learnWordWithLemma("مدرسة", "مدرس")
            DrsLearningEngine.knowsWord("مدرسة") shouldBe true
            DrsLearningEngine.lemmaCount("مدرس") shouldBe 1L // floor: 2/2 = 1

            DrsLearningEngine.learnWordWithLemma("مدرسة", "مدرس")
            DrsLearningEngine.lemmaCount("مدرس") shouldBe 2L // 1 (reservoir) + 2/2
        }

        test("personalProbability: count over total tokens") {
            DrsLearningEngine.clear()
            DrsLearningEngine.learnWordWithLemma("مدرسة", "مدرس")
            DrsLearningEngine.learnWordWithLemma("كتاب", "كتاب")
            val total = 2.0
            DrsLearningEngine.personalProbability("مدرسة") shouldBe (1.0 / total)
            DrsLearningEngine.personalProbability("غير_موجود") shouldBe 0.0
        }

        test("the stem survives the reload cycle with its reservoir intact") {
            val file = File(System.getProperty("java.io.tmpdir"), "drs-m16-lemma-${System.nanoTime()}.json")
            try {
                DrsLearningEngine.clear()
                repeat(4) { DrsLearningEngine.learnWordWithLemma("مدرستي", "مدرس") }
                // word count 4 → bumps 1 (floor) + 1 (2/2) + 1 (3/2) + 2 (4/2) = 5
                DrsLearningEngine.lemmaCount("مدرس") shouldBe 5L
                DrsLearningEngine.persist(file)
                DrsLearningEngine.clear()
                DrsLearningEngine.load(file)
                // both survive the −25% aging; the stem's reservoir keeps
                // the family rankable even if the word ages out later:
                DrsLearningEngine.lemmaCount("مدرس") shouldNotBe 0L
                DrsLearningEngine.knowsWord("مدرستي") shouldBe true
            } finally {
                file.delete()
            }
        }

        test("the lemma table rides the persistence file and old payloads decode clean") {
            val file = File(System.getProperty("java.io.tmpdir"), "drs-m16-persist-${System.nanoTime()}.json")
            try {
                DrsLearningEngine.clear()
                DrsLearningEngine.learnWordWithLemma("الكتاب", "كتاب")
                DrsLearningEngine.persist(file)
                DrsLearningEngine.clear()
                DrsLearningEngine.load(file)
                DrsLearningEngine.lemmaCount("كتاب") shouldNotBe 0L
            } finally {
                file.delete()
            }
        }
    }
})
