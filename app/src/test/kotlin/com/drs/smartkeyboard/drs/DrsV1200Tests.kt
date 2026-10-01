/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.dictionary.DictionaryManager
import com.drs.smartkeyboard.ime.nlp.glidePreviewBeatsClipboard
import com.drs.smartkeyboard.ime.nlp.latin.DictEntry
import com.drs.smartkeyboard.ime.nlp.latin.DictIndex
import com.drs.smartkeyboard.ime.nlp.latin.nextWordDisplayCase
import com.drs.smartkeyboard.ime.text.gestures.GlideTypingGesture
import com.drs.smartkeyboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * DRS v1.20.0: the precision-and-mastery round — the tri-front audit found a
 * trapped numeric pad with no way back to letters, a phone pad with duplicated
 * comma/pause keys, glide typing doing 50k-entry dictionary work on the main
 * thread, the glide preview silently replaced by the clipboard chip, an
 * unbounded gesture-trail buffer, a mis-keyed length-pruner cache for
 * double-letter words, lexicographic truncation bias in the prefix ranking,
 * next-word casing that ignored sentence boundaries, learned words that could
 * never be unlearned, and invisible global dictionary entries. Every pure
 * contract introduced by this round is pinned here.
 */
class DrsV1200Tests : FunSpec({

    // -------------------------------------------------------------
    // DictIndex.findTopByPrefix — frequency ranking over the FULL range
    // -------------------------------------------------------------
    fun sampleIndex(): DictIndex {
        // 60 low-freq "ba…" words lexicographically first, then one VERY frequent
        // "bz" word lexicographically last. The old lexicographic truncation at
        // limit=5 lost "bz" entirely; findTopByPrefix must surface it.
        val entries = mutableListOf<DictEntry>()
        repeat(60) { i ->
            val w = "ba" + i.toString().padStart(2, '0')
            entries.add(DictEntry(norm = w, word = w, freq = 10 + i % 5))
        }
        entries.add(DictEntry(norm = "bz", word = "bz", freq = 255))
        entries.add(DictEntry(norm = "cat", word = "cat", freq = 100))
        return DictIndex(entries.sortedBy { it.norm })
    }

    test("findTopByPrefix ranks the whole matching range by frequency, not lexicographic position") {
        val index = sampleIndex()
        val top = index.findTopByPrefix("b", limit = 5)
        top.first().word shouldBe "bz" // the most frequent word wins regardless of position
        top shouldHaveSize 5
    }

    test("findTopByPrefix result is ordered by descending frequency") {
        val index = sampleIndex()
        val top = index.findTopByPrefix("b", limit = 10)
        top.map { it.freq } shouldBe top.map { it.freq }.sortedDescending()
    }

    test("findTopByPrefix respects the limit and the prefix window") {
        val index = sampleIndex()
        index.findTopByPrefix("b", limit = 3) shouldHaveSize 3
        index.findTopByPrefix("cat", limit = 5).map { it.word } shouldBe listOf("cat")
        index.findTopByPrefix("zz", limit = 5) shouldHaveSize 0
    }

    test("findTopByPrefix honest edge cases: empty prefix, non-positive limit") {
        val index = sampleIndex()
        index.findTopByPrefix("", limit = 5) shouldHaveSize 0
        index.findTopByPrefix("b", limit = 0) shouldHaveSize 0
        index.findTopByPrefix("b", limit = -3) shouldHaveSize 0
    }

    test("findByPrefix keeps its original lexicographic contract (exact membership checks)") {
        val index = sampleIndex()
        val head = index.findByPrefix("b", limit = 3)
        head.map { it.word } shouldBe listOf("ba00", "ba01", "ba02") // first lexicographic hits
        // spell() relies on this: the single result for a full normalized form is exact
        index.findByPrefix("bz", limit = 1).firstOrNull().shouldNotBeNull()
        index.findByPrefix("bz", limit = 1).first().norm shouldBe "bz"
    }

    // -------------------------------------------------------------
    // nextWordDisplayCase — sentence-aware casing for predictions
    // -------------------------------------------------------------
    test("next-word after a mid-sentence word stays lowercase") {
        nextWordDisplayCase("world", "Hello ", isArabic = false) shouldBe "world"
        nextWordDisplayCase("world", "hello", isArabic = false) shouldBe "world"
    }

    test("next-word after a sentence terminator is capitalized") {
        nextWordDisplayCase("how", "Hi. ", isArabic = false) shouldBe "How"
        nextWordDisplayCase("really", "What! ", isArabic = false) shouldBe "Really"
        nextWordDisplayCase("and", "then… ", isArabic = false) shouldBe "And"
        nextWordDisplayCase("you", "How are ? ", isArabic = false) shouldBe "You"
    }

    test("next-word casing honors Arabic terminators and leaves Arabic words untouched") {
        nextWordDisplayCase("و", "مرحبا. ", isArabic = true) shouldBe "و"
        nextWordDisplayCase("something", "سؤال؟ ", isArabic = false) shouldBe "Something"
        nextWordDisplayCase("something", "نهاية۔ ", isArabic = false) shouldBe "Something"
    }

    test("next-word casing defensive: blank context returns the dictionary spelling") {
        nextWordDisplayCase("Word", "", isArabic = false) shouldBe "Word"
        nextWordDisplayCase("Word", "   ", isArabic = false) shouldBe "Word"
    }

    // -------------------------------------------------------------
    // GlideTypingGesture.Detector.shouldStartGesture — sliding window decision
    // -------------------------------------------------------------
    val keySizeDp = 40f

    test("a fast, long-enough movement inside the window starts a glide") {
        GlideTypingGesture.Detector.shouldStartGesture(
            distDp = 45f, windowMs = 100L, keySizeDp = keySizeDp, initialKeyCode = null,
        ).shouldBeTrue()
    }

    test("short travel or slow velocity never starts a glide") {
        // below the key-size travel gate
        GlideTypingGesture.Detector.shouldStartGesture(
            distDp = 30f, windowMs = 100L, keySizeDp = keySizeDp, initialKeyCode = null,
        ).shouldBeFalse()
        // below the velocity gate (0.10 dp/ms)
        GlideTypingGesture.Detector.shouldStartGesture(
            distDp = 300f, windowMs = 4000L, keySizeDp = keySizeDp, initialKeyCode = null,
        ).shouldBeFalse()
    }

    test("zero-duration window is clamped, not divided by zero") {
        GlideTypingGesture.Detector.shouldStartGesture(
            distDp = 50f, windowMs = 0L, keySizeDp = keySizeDp, initialKeyCode = null,
        ).shouldBeTrue() // dist/1 dp per ms — fast burst counts
    }

    test("swipe gesture keys are excluded from glide even at high velocity") {
        for (code in intArrayOf(KeyCode.DELETE, KeyCode.SHIFT, KeyCode.SPACE, KeyCode.CJK_SPACE)) {
            GlideTypingGesture.Detector.shouldStartGesture(
                distDp = 400f, windowMs = 50L, keySizeDp = keySizeDp, initialKeyCode = code,
            ).shouldBeFalse()
        }
    }

    // -------------------------------------------------------------
    // glidePreviewBeatsClipboard — the live preview keeps the row
    // -------------------------------------------------------------
    test("glide preview beats the clipboard chip only for a live direct publish") {
        glidePreviewBeatsClipboard(internalCount = 3, clipboardCount = 2, directStamp = 1234L).shouldBeTrue()
        // no glide publish (no stamp) → the clipboard chip keeps its normal precedence
        glidePreviewBeatsClipboard(internalCount = 3, clipboardCount = 2, directStamp = 0L).shouldBeFalse()
        // nothing to preview → nothing to protect
        glidePreviewBeatsClipboard(internalCount = 0, clipboardCount = 2, directStamp = 1234L).shouldBeFalse()
        // no clipboard chip → no conflict, the ifEmpty fallback shows the preview anyway
        glidePreviewBeatsClipboard(internalCount = 3, clipboardCount = 0, directStamp = 1234L).shouldBeFalse()
    }

    // -------------------------------------------------------------
    // DictionaryManager.demoteTarget — unlearning on rejected suggestions
    // -------------------------------------------------------------
    test("revert demotion drains the learned frequency by the step") {
        DictionaryManager.demoteTarget(freq = 120, by = 64) shouldBe 56
        DictionaryManager.demoteTarget(freq = 160, by = 64) shouldBe 96
        DictionaryManager.demoteTarget(freq = 65, by = 64) shouldBe 1
    }

    test("a fully drained entry must be deleted, never kept at zero or negative") {
        DictionaryManager.demoteTarget(freq = 64, by = 64).shouldBeNull()
        DictionaryManager.demoteTarget(freq = 30, by = 64).shouldBeNull()
        DictionaryManager.demoteTarget(freq = 0, by = 64).shouldBeNull()
    }

    // -------------------------------------------------------------
    // Layout JSON contracts — the trapped numeric pad and the phone pad
    // -------------------------------------------------------------
    fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            if (File(dir, "app/src/main/assets/ime").exists()) return dir
            dir = dir.parentFile ?: File("/")
        }
        error("repo root with app/src/main/assets not found from ${System.getProperty("user.dir")}")
    }

    fun layoutFile(rel: String) = File(repoRoot(), "app/src/main/assets/ime/keyboard/org.drs.layouts/layouts/$rel")

    class KeyRef(val code: Int?, val type: String?, val popupMainCode: Int?) {
        fun isExitKey(vararg exitCodes: Int): Boolean =
            code != null && type == "system_gui" && code in exitCodes
    }

    fun parseLayout(file: File): List<List<KeyRef>> {
        val root = Json.parseToJsonElement(file.readText()).jsonArray
        return root.map { row ->
            row.jsonArray.map { el ->
                val obj = el.jsonObject
                val code = obj["code"]?.jsonPrimitive?.int
                val type = obj["type"]?.jsonPrimitive?.content
                val popupMain = obj["popup"]?.jsonObject?.get("main")?.jsonObject
                val popupMainCode = popupMain?.get("code")?.jsonPrimitive?.int
                KeyRef(code, type, popupMainCode)
            }
        }
    }

    test("the numeric pads offer a way back to the letters (view_characters exit)") {
        for (file in listOf("numeric/western_arabic.json", "numeric/western_arabic_pc.json")) {
            val layout = parseLayout(layoutFile(file))
            val allKeys = layout.flatten()
            allKeys.any { it.isExitKey(KeyCode.VIEW_CHARACTERS) }.shouldBeTrue()
        }
    }

    test("the phone pad exposes pause/comma and wait through popups") {
        val layout = parseLayout(layoutFile("phone/telpad.json"))
        val allKeys = layout.flatten()
        allKeys.any { it.popupMainCode == KeyCode.PHONE_PAUSE }.shouldBeTrue() // comma
        allKeys.any { it.popupMainCode == KeyCode.PHONE_WAIT }.shouldBeTrue()  // wait ( ;)
    }

    test("the phone2 pad has no duplicated comma/pause key (the old twin code-44 keys)") {
        val layout = parseLayout(layoutFile("phone2/telpad.json"))
        val allKeys = layout.flatten()
        // pause (system_gui 44) and the plain comma (44) both insert ","; exactly one
        // code-44 key remains, and wait (59) appears exactly once (no new duplication)
        allKeys.count { it.code == KeyCode.PHONE_PAUSE } shouldBe 1
        allKeys.count { it.code == KeyCode.PHONE_WAIT } shouldBe 1
    }

    test("every numeric/phone layout still parses as a sane row grid") {
        for (file in listOf(
            "numeric/western_arabic.json",
            "numeric/western_arabic_pc.json",
            "phone/telpad.json",
            "phone2/telpad.json",
        )) {
            val layout = parseLayout(layoutFile(file))
            (layout.size >= 3).shouldBeTrue()
            layout.forEach { row -> (row.isNotEmpty()).shouldBeTrue() }
        }
    }
})
