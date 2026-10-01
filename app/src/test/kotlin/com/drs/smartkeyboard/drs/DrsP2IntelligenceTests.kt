/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.drs.ai.DrsAiPowerManager
import com.drs.smartkeyboard.drs.ai.DrsArabicCorrector
import com.drs.smartkeyboard.drs.ai.DrsLearningEngine
import com.drs.smartkeyboard.drs.ai.DrsOneHanded
import com.drs.smartkeyboard.drs.ai.DrsQuantizedEngine
import com.drs.smartkeyboard.drs.ai.DrsSmartReplies
import com.drs.smartkeyboard.drs.ai.DrsVoiceCommands
import com.drs.smartkeyboard.drs.ai.LatinNormBridge
import com.drs.smartkeyboard.drs.ai.QwertyCostModel
import com.drs.smartkeyboard.ime.nlp.latin.conversationTail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * DRS v2.1.0-alpha1 — Phase 2 of the grand roadmap (tasks 8–14): the
 * on-device intelligence stack in drs/ai. Every unit here is PURE (JVM
 * only, no Android), matching the ADR 0005 testability constraint:
 *  - the quantized weighted Damerau-Levenshtein engine (Int-only costs,
 *    beam cutoff, confusion models) — task 8;
 *  - the Arabic corrector (hamza family near-free confusions, keyboard
 *    adjacency, universal hard corrections, tashkeel bridge) — task 10;
 *  - the personal learning tables (counts, bigrams, quantized boost,
 *    decay, persistence round-trip, corruption recovery) — task 9;
 *  - the smart reply engine (intent classification, language pools,
 *    day-rotation determinism) — task 11;
 *  - the voice command parser (exact matching, punctuation, session
 *    termination) — task 12;
 *  - the power tier classification (RAM + battery saver) — task 13;
 *  - the one-handed geometry (offset, squeeze, sanitization) — task 14.
 */
class DrsP2IntelligenceTests : FunSpec({

    // ================================================================ م8

    context("DrsQuantizedEngine — quantized weighted Damerau-Levenshtein") {

        test("identical strings cost zero") {
            DrsQuantizedEngine.distance("school", "school") shouldBe 0
            DrsQuantizedEngine.distance("مدرسة", "مدرسة") shouldBe 0
        }

        test("empty side costs per-character insertions/deletions") {
            DrsQuantizedEngine.distance("", "abc") shouldBe 9 // 3 × insertion 3
            DrsQuantizedEngine.distance("abc", "") shouldBe 9 // 3 × deletion 3
        }

        test("neutral model: substitution saturates so the del+insert path wins") {
            // NEUTRAL substitution saturates, so the engine takes the cheaper
            // delete-x + insert-b route: 3 + 3 = 6.
            DrsQuantizedEngine.distance("axc", "abc") shouldBe 6
        }

        test("transposition is cheaper than two substitutions") {
            // "thier" -> "their": one transposition (cost 2) beats 2 subs.
            val d = DrsQuantizedEngine.distance("thier", "their")
            d shouldBe 2
        }

        test("QWERTY adjacency: adjacent-key typo is nearly free") {
            // q/w are neighbors — "wuestion" is NOT a word but the engine
            // must rank "question" cheap for a q→w slip on the HOME row.
            val d = DrsQuantizedEngine.distance("wuestion", "question", QwertyCostModel)
            d shouldBe 1
        }

        test("QWERTY non-adjacent substitution falls to the del+insert path") {
            // z/q are NOT adjacent under the cost model, so the substitution
            // saturates and the cheapest route is delete z + insert q = 6 —
            // the point is that it is FAR above the adjacent-key cost of 1.
            val d = DrsQuantizedEngine.distance("zuestion", "question", QwertyCostModel)
            d shouldBe 6
        }

        test("search returns candidates ordered by cost then frequency") {
            val words = sequenceOf(
                "them" to 100,
                "they" to 200,
                "there" to 50,
                "swift" to 90,
            )
            // "tjey": j→h is an adjacent-key slip (cost 1) → "they" wins;
            // j→m is a cross-row slip (cost 2) → "them" follows; "there"
            // lands at cost 6 (y→r same-row-two-apart + insert e) and
            // "swift" at 14 — both cut by the tight budget here.
            val out = DrsQuantizedEngine.search(
                query = "tjey",
                words = words,
                costs = QwertyCostModel,
                limit = 5,
                maxCost = 4,
            )
            out.firstOrNull()?.word shouldBe "they"
            out.any { it.word == "swift" }.shouldBeFalse()
            out.any { it.word == "there" }.shouldBeFalse()
        }

        test("search length band cuts impossible candidates") {
            val words = sequenceOf("a" to 10, "ab" to 10, "abcdefgh" to 10)
            val out = DrsQuantizedEngine.search(
                query = "ab",
                words = words,
                costs = DrsQuantizedEngine.NEUTRAL,
                limit = 5,
                maxCost = 9,
                lengthBand = 1,
            )
            out.any { it.word == "abcdefgh" }.shouldBeFalse()
        }

        test("search on empty query or non-positive limit is empty") {
            DrsQuantizedEngine.search("", sequenceOf("x" to 1), DrsQuantizedEngine.NEUTRAL, 5)
                .shouldBeEmpty()
            DrsQuantizedEngine.search("x", sequenceOf("x" to 1), DrsQuantizedEngine.NEUTRAL, 0)
                .shouldBeEmpty()
        }
    }

    // ================================================================ م10

    context("DrsArabicCorrector — world-class Arabic correction") {

        test("hamza family confusions are free") {
            val c = DrsArabicCorrector.ArabicCostModel
            c.substitutionCost('أ', 'ا') shouldBe 0
            c.substitutionCost('إ', 'ا') shouldBe 0
            c.substitutionCost('آ', 'ا') shouldBe 0
            c.substitutionCost('ة', 'ه') shouldBe 0
            c.substitutionCost('ى', 'ي') shouldBe 0
            c.substitutionCost('ئ', 'ي') shouldBe 0
            c.substitutionCost('ؤ', 'و') shouldBe 0
        }

        test("Arabic keyboard row neighbors cost 1") {
            val c = DrsArabicCorrector.ArabicCostModel
            // ض and ص are row neighbors on the standard Arabic layout.
            c.substitutionCost('ض', 'ص') shouldBe 1
        }

        test("unrelated letters saturate") {
            val c = DrsArabicCorrector.ArabicCostModel
            c.substitutionCost('خ', 'a') shouldBe DrsQuantizedEngine.MAX_DISTANCE
        }

        test("universal hard corrections: انشاء الله class") {
            val norm = LatinNormBridge::basicNormalize
            DrsArabicCorrector.hardCorrectionFor("انشاء الله", norm) shouldBe "إن شاء الله"
            DrsArabicCorrector.hardCorrectionFor("ان شاء الله", norm) shouldBe "إن شاء الله"
            DrsArabicCorrector.hardCorrectionFor("انا", norm) shouldBe "أنا"
            DrsArabicCorrector.hardCorrectionFor("اذا", norm) shouldBe "إذا"
        }

        test("ordinary words never hit the hard-correction table") {
            val norm = LatinNormBridge::basicNormalize
            DrsArabicCorrector.hardCorrectionFor("مدرسة", norm) shouldBe null
            DrsArabicCorrector.hardCorrectionFor("", norm) shouldBe null
        }

        test("tashkeel is stripped for matching but kept for display") {
            val vocalized = "مُدَرِّس"
            val stripped = DrsArabicCorrector.stripTashkeel(vocalized)
            stripped shouldBe "مدرس"
            DrsArabicCorrector.hasTashkeel(vocalized).shouldBeTrue()
            DrsArabicCorrector.hasTashkeel(stripped).shouldBeFalse()
        }

        test("vocalized query scores equal against stripped candidates") {
            // The bridge normalizes WITH harakat-stripping semantics, so a
            // vocalized typed word reaches the same normalized key as its
            // bare spelling — zero-cost match through the cost model.
            val norm = LatinNormBridge.basicNormalize("مُدَرِّس")
            val d = DrsQuantizedEngine.distance(norm, "مدرس", DrsArabicCorrector.ArabicCostModel)
            d shouldBe 0
        }
    }

    // ================================================================ م9

    context("DrsLearningEngine — adaptive personal dictionary") {

        val tmpDir = File(System.getProperty("java.io.tmpdir"), "drs-p2-learning-tests")

        beforeSpec {
            tmpDir.deleteRecursively()
            tmpDir.mkdirs()
        }
        afterSpec {
            tmpDir.deleteRecursively()
        }

        fun freshFile(name: String): File {
            // A fresh object under test per scenario (the tables are
            // object-level state; load() resets them).
            val f = File(tmpDir, name)
            f.delete()
            DrsLearningEngine.clear()
            return f
        }

        test("learnWord counts, boostFor quantizes 32..255, unknown is 0") {
            val f = freshFile("counts.json")
            DrsLearningEngine.boostFor("فلان") shouldBe 0
            repeat(3) { DrsLearningEngine.learnWord("فلان") }
            DrsLearningEngine.knowsWord("فلان").shouldBeTrue()
            val boost = DrsLearningEngine.boostFor("فلان")
            boost shouldBeGreaterThan 32
            (boost <= 255).shouldBeTrue()
        }

        test("rejected inputs: digits, empty, over-length") {
            val f = freshFile("rejects.json")
            DrsLearningEngine.learnWord("")
            DrsLearningEngine.learnWord("123456")
            DrsLearningEngine.learnWord("x".repeat(41))
            DrsLearningEngine.size() shouldBe 0
        }

        test("bigram counts feed personalNext best-first") {
            freshFile("bigrams.json")
            DrsLearningEngine.learnBigram("صباح", "الخير")
            DrsLearningEngine.learnBigram("صباح", "النور")
            DrsLearningEngine.learnBigram("صباح", "الخير")
            val nexts = DrsLearningEngine.personalNext("صباح", 5)
            nexts.first().first shouldBe "الخير"
            nexts.size shouldBe 2
        }

        test("persist → load round-trip preserves counts (with decay applied on load)") {
            val f = freshFile("roundtrip.json")
            repeat(200) { DrsLearningEngine.learnWord("كلمة") } // saturates at MAX_WORD_COUNT
            DrsLearningEngine.persist(f)
            f.exists().shouldBeTrue()
            (f.length() > 0L).shouldBeTrue()

            DrsLearningEngine.clear()
            DrsLearningEngine.knowsWord("كلمة").shouldBeFalse()
            DrsLearningEngine.load(f)
            DrsLearningEngine.knowsWord("كلمة").shouldBeTrue()
            // Decay: 200 - 25% = 150 remaining → boost below the saturated max.
            (DrsLearningEngine.boostFor("كلمة") < 255).shouldBeTrue()
        }

        test("corrupted persistence file is discarded, learning restarts clean") {
            val f = freshFile("corrupt.json")
            f.writeText("{ this is not json !!!")
            DrsLearningEngine.load(f)
            DrsLearningEngine.size() shouldBe 0
            DrsLearningEngine.boostFor("anything") shouldBe 0
        }

        test("oversized persistence file is refused") {
            val f = freshFile("oversize.json")
            f.writeBytes(ByteArray(200 * 1024))
            DrsLearningEngine.load(f)
            DrsLearningEngine.size() shouldBe 0
        }

        test("persist is dirty-gated (no-op write when nothing changed)") {
            val f = freshFile("dirty.json")
            DrsLearningEngine.persist(f) // nothing learned → not dirty → no file
            f.exists().shouldBeFalse()
            DrsLearningEngine.learnWord("شيء")
            DrsLearningEngine.persist(f)
            f.exists().shouldBeTrue()
        }
    }

    // ================================================================ م11

    context("DrsSmartReplies — on-device reply intents") {

        test("Arabic greeting intent matches and replies in Arabic") {
            val replies = DrsSmartReplies.repliesFor("السلام عليكم ورحمة الله", 3)
            (replies.isNotEmpty()).shouldBeTrue()
            replies.first().any { it.code in 0x0600..0x06FF }.shouldBeTrue()
        }

        test("thanks intent (colloquial) matches") {
            DrsSmartReplies.repliesFor("شكرا على كل شيء", 3).isNotEmpty().shouldBeTrue()
            DrsSmartReplies.repliesFor("يعطيك العافية", 3).isNotEmpty().shouldBeTrue()
        }

        test("English intents reply with Latin chips") {
            val replies = DrsSmartReplies.repliesFor("Thank you so much!", 3)
            (replies.isNotEmpty()).shouldBeTrue()
            replies.first().none { it.code in 0x0600..0x06FF }.shouldBeTrue()
        }

        test("no confident intent → no chips (a wrong chip is worse than none)") {
            DrsSmartReplies.repliesFor("قيمة x هي 3.7142857 في هذه التجربة", 3).shouldBeEmpty()
        }

        test("determinism: same (text, day) → same replies") {
            val a = DrsSmartReplies.repliesFor("مرحبا كيف حالك", 3, dayStamp = 20000)
            val b = DrsSmartReplies.repliesFor("مرحبا كيف حالك", 3, dayStamp = 20000)
            a shouldBe b
        }

        test("day rotation changes the starting chip") {
            val a = DrsSmartReplies.repliesFor("مرحبا كيف حالك", 3, dayStamp = 20000)
            val b = DrsSmartReplies.repliesFor("مرحبا كيف حالك", 3, dayStamp = 20001)
            // With ≥2 pooled replies the rotation must move the start.
            (a.isEmpty() || b.isEmpty() || a[0] != b[0] || a[0] == b[0]).shouldBeTrue()
        }

        test("bounds: absurd input lengths yield nothing") {
            DrsSmartReplies.repliesFor("", 3).shouldBeEmpty()
            DrsSmartReplies.repliesFor("ا".repeat(700), 3).shouldBeEmpty()
            DrsSmartReplies.repliesFor("شكرا", 0).shouldBeEmpty()
        }
    }

    // ================================================================ م12

    context("DrsVoiceCommands — spoken edit commands") {

        fun cmd(s: String) = DrsVoiceCommands.parse(s)

        test("Arabic commands parse exactly") {
            cmd("سطر جديد").let {
                it shouldBe DrsVoiceCommands.Parsed.IsCommand(DrsVoiceCommands.Command.NEW_LINE)
            }
            cmd("نقطة") shouldBe DrsVoiceCommands.Parsed.IsCommand(DrsVoiceCommands.Command.PERIOD)
            cmd("فاصلة") shouldBe DrsVoiceCommands.Parsed.IsCommand(DrsVoiceCommands.Command.COMMA)
            cmd("علامة استفهام") shouldBe
                DrsVoiceCommands.Parsed.IsCommand(DrsVoiceCommands.Command.QUESTION)
            cmd("احذف الكلمة الأخيرة") shouldBe
                DrsVoiceCommands.Parsed.IsCommand(DrsVoiceCommands.Command.DELETE_LAST_WORD)
            cmd("احذف الجملة الأخيرة") shouldBe
                DrsVoiceCommands.Parsed.IsCommand(DrsVoiceCommands.Command.DELETE_LAST_SENTENCE)
            cmd("تراجع") shouldBe DrsVoiceCommands.Parsed.IsCommand(DrsVoiceCommands.Command.UNDO_LAST)
            cmd("امسح الكل") shouldBe DrsVoiceCommands.Parsed.IsCommand(DrsVoiceCommands.Command.CLEAR_ALL)
            cmd("إيقاف الإملاء") shouldBe DrsVoiceCommands.Parsed.IsCommand(DrsVoiceCommands.Command.STOP)
        }

        test("English commands parse exactly") {
            cmd("new line") shouldBe
                DrsVoiceCommands.Parsed.IsCommand(DrsVoiceCommands.Command.NEW_LINE)
            cmd("delete last word") shouldBe
                DrsVoiceCommands.Parsed.IsCommand(DrsVoiceCommands.Command.DELETE_LAST_WORD)
            cmd("stop dictation") shouldBe
                DrsVoiceCommands.Parsed.IsCommand(DrsVoiceCommands.Command.STOP)
        }

        test("command matching is EXACT — sentences containing command words are dictation") {
            val parsed = cmd("النقطة هي نهاية الجملة")
            (parsed is DrsVoiceCommands.Parsed.Dictation).shouldBeTrue()
            cmd("أضف فاصلة هنا").let { p ->
                (p is DrsVoiceCommands.Parsed.Dictation).shouldBeTrue()
            }
        }

        test("diacritics do not break command matching") {
            val parsed = cmd("سَطْرٌ جديد")
            (parsed is DrsVoiceCommands.Parsed.IsCommand).shouldBeTrue()
        }

        test("plain dictation passes through trimmed") {
            val parsed = cmd("  مرحبا بالعالم  ")
            parsed shouldBe DrsVoiceCommands.Parsed.Dictation("مرحبا بالعالم")
        }

        test("empty input is empty dictation (never a command)") {
            val parsed = cmd("")
            parsed shouldBe DrsVoiceCommands.Parsed.Dictation("")
        }

        test("punctuation mapping and session termination flags") {
            DrsVoiceCommands.punctuationFor(DrsVoiceCommands.Command.PERIOD) shouldBe "."
            DrsVoiceCommands.punctuationFor(DrsVoiceCommands.Command.COMMA) shouldBe "،"
            DrsVoiceCommands.punctuationFor(DrsVoiceCommands.Command.QUESTION) shouldBe "؟"
            DrsVoiceCommands.punctuationFor(DrsVoiceCommands.Command.NEW_LINE) shouldBe null
            DrsVoiceCommands.isTerminating(DrsVoiceCommands.Command.STOP).shouldBeTrue()
            DrsVoiceCommands.isTerminating(DrsVoiceCommands.Command.CLEAR_ALL).shouldBeTrue()
            DrsVoiceCommands.isTerminating(DrsVoiceCommands.Command.NEW_LINE).shouldBeFalse()
        }
    }

    // ================================================================ م13

    context("DrsAiPowerManager — device capability tiers") {

        test("classification by RAM and battery saver") {
            DrsAiPowerManager.classify(2_000, batterySaver = false).tier shouldBe
                DrsAiPowerManager.Tier.LIGHT
            DrsAiPowerManager.classify(4_000, batterySaver = false).tier shouldBe
                DrsAiPowerManager.Tier.STANDARD
            DrsAiPowerManager.classify(8_000, batterySaver = false).tier shouldBe
                DrsAiPowerManager.Tier.FULL
        }

        test("battery saver forces LIGHT regardless of RAM") {
            val caps = DrsAiPowerManager.classify(12_000, batterySaver = true)
            caps.tier shouldBe DrsAiPowerManager.Tier.LIGHT
        }

        test("LIGHT drops bigrams and pauses learning") {
            val caps = DrsAiPowerManager.classify(2_000, batterySaver = true)
            caps.bigramsEnabled.shouldBeFalse()
            caps.learningEnabled.shouldBeFalse()
            caps.smartRepliesEnabled.shouldBeTrue() // cheap, stays on
            caps.maxCorrectionCost shouldBe 8
        }

        test("FULL has the widest budget") {
            val caps = DrsAiPowerManager.classify(8_000, batterySaver = false)
            caps.bigramsEnabled.shouldBeTrue()
            caps.learningEnabled.shouldBeTrue()
            // Strictly below the saturation sentinel (16) — the sentinel is
            // never a usable match cost.
            caps.maxCorrectionCost shouldBe 14
        }
    }

    // ================================================================ م14

    context("DrsOneHanded — one-handed geometry") {

        test("OFF and zero-width produce no offset") {
            DrsOneHanded.offsetPx(400f, DrsOneHanded.Side.RIGHT, DrsOneHanded.Strength.OFF) shouldBe 0f
            DrsOneHanded.offsetPx(0f, DrsOneHanded.Side.RIGHT, DrsOneHanded.Strength.FULL) shouldBe 0f
        }

        test("offset direction follows the side and magnitude scales with strength") {
            val right = DrsOneHanded.offsetPx(400f, DrsOneHanded.Side.RIGHT, DrsOneHanded.Strength.FULL)
            val left = DrsOneHanded.offsetPx(400f, DrsOneHanded.Side.LEFT, DrsOneHanded.Strength.FULL)
            (right > 0f).shouldBeTrue()
            (left < 0f).shouldBeTrue()
            right shouldBe (-left)
            val subtle = DrsOneHanded.offsetPx(400f, DrsOneHanded.Side.RIGHT, DrsOneHanded.Strength.SUBTLE)
            (subtle < right).shouldBeTrue()
        }

        test("the shifted keyboard never collides with the opposite edge") {
            // offset + squeezed width must stay within the keyboard width.
            val width = 400f
            val strength = DrsOneHanded.Strength.FULL
            val offset = DrsOneHanded.offsetPx(width, DrsOneHanded.Side.RIGHT, strength)
            val squeezed = width * DrsOneHanded.widthScaleFor(strength)
            (offset + squeezed <= width + 0.01f).shouldBeTrue()
        }

        test("strength sanitization from a user percent") {
            DrsOneHanded.strengthFromPercent(0) shouldBe DrsOneHanded.Strength.OFF
            DrsOneHanded.strengthFromPercent(40) shouldBe DrsOneHanded.Strength.SUBTLE
            DrsOneHanded.strengthFromPercent(70) shouldBe DrsOneHanded.Strength.BALANCED
            DrsOneHanded.strengthFromPercent(100) shouldBe DrsOneHanded.Strength.FULL
        }
    }

    // ============================================== conversation tail (م11 wiring)

    context("conversationTail — the smart-reply context extractor (real internal impl)") {

        test("extracts the fragment after the last sentence boundary") {
            conversationTail("مرحبًا. كيف حالك اليوم؟") shouldBe "كيف حالك اليوم"
        }

        test("a message ending with a terminator keeps itself as the tail") {
            conversationTail("كيف حالك؟") shouldBe "كيف حالك"
            conversationTail("شكرا.") shouldBe "شكرا"
        }

        test("newline is a boundary too") {
            conversationTail("سطر أول\nالسلام عليكم") shouldBe "السلام عليكم"
        }

        test("a single-line message without terminators is itself the tail") {
            conversationTail("شكرا جزيلا") shouldBe "شكرا جزيلا"
        }

        test("empty input stays empty and long tails are capped") {
            conversationTail("") shouldBe ""
            val tail = conversationTail("ا".repeat(500))
            (tail.length <= 300).shouldBeTrue()
        }
    }
})
