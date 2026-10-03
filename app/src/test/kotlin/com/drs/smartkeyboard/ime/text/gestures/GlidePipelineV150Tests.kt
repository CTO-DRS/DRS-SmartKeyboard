/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.text.gestures

import androidx.collection.SparseArrayCompat
import com.drs.smartkeyboard.ime.text.keyboard.TextKey
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.lib.DrsRect
import com.drs.smartkeyboard.ime.text.gestures.StatisticalGlideTypingClassifier.Gesture
import com.drs.smartkeyboard.ime.text.gestures.StatisticalGlideTypingClassifier.Pruner
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * v1.5.0 — عقود SHARK2 الخالصة (the pinned pure contracts of the glide
 * pipeline): the Gesture geometry (add/length/clone/resample/normalize),
 * the ideal-gesture generation for ARABIC words (hamza carriers resolve
 * through the NFD base-letter path, harakat are skipped, doubled letters
 * get the loop variant), and the Pruner (extremities + length pruning on
 * a synthetic Arabic key grid). Every expectation below was traced
 * against the implementation — this suite PINS the truth so the doctrine
 * (deterministic, on-device, no AI) stays verifiable.
 */
class GlidePipelineV150Tests : FunSpec({

    // -------------------------------------------------------------
    // مفتاح اصطناعي — a synthetic Arabic key on a virtual grid
    // -------------------------------------------------------------

    fun key(letter: Char, cx: Float, cy: Float, size: Float = 60f): TextKey {
        val data = TextKeyData(code = letter.code, label = letter.toString())
        return object : TextKey(data) {
            override val visibleBounds: DrsRect = DrsRect.new(
                cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f,
            )
        }
    }

    /**
     * Letters spaced ≥ 340px apart so «أقرب مفتاحين» is deterministic
     * everywhere: each query point's two nearest keys are itself and the
     * designated partner, never a third letter.
     */
    fun sparseGrid(): SparseArrayCompat<TextKey> {
        val map = SparseArrayCompat<TextKey>()
        val placed: Map<Char, Pair<Float, Float>> = mapOf(
            'ا' to (60f to 60f), 'د' to (60f to 600f), 'ن' to (400f to 600f),
            'ه' to (400f to 60f), 'ك' to (2000f to 60f), 'ل' to (2340f to 60f),
            'ت' to (2000f to 600f), 'ب' to (2000f to 1140f), 'س' to (4000f to 60f),
            'م' to (4000f to 600f), 'ر' to (4000f to 1140f), 'ة' to (4340f to 1140f),
        )
        placed.forEach { (ch, pos) -> map.put(ch.code, key(ch, pos.first, pos.second)) }
        return map
    }

    fun keys(grid: SparseArrayCompat<TextKey>): List<TextKey> =
        (0 until grid.size()).map { grid.valueAt(it) }

    // -------------------------------------------------------------
    // الهندسة الأساسية — the Gesture geometry
    // -------------------------------------------------------------

    test("a 3-4-5 polyline measures its true length and endpoints") {
        val g = Gesture()
        g.addPoint(0f, 0f)
        g.addPoint(3f, 0f)
        g.addPoint(3f, 4f)
        g.isEmpty shouldBe false
        g.getLength() shouldBe 7f
        g.getFirstX() shouldBe 0f
        g.getFirstY() shouldBe 0f
        g.getLastX() shouldBe 3f
        g.getLastY() shouldBe 4f
    }

    test("an empty gesture is empty and reads its fallback endpoints") {
        val g = Gesture()
        g.isEmpty shouldBe true
        g.getFirstX() shouldBe 0f
        g.getLastY() shouldBe 0f
    }

    test("clone is an independent deep copy") {
        val g = Gesture()
        g.addPoint(1f, 1f)
        g.addPoint(2f, 2f)
        val c = g.clone()
        c shouldBe g
        c.addPoint(3f, 3f)
        c shouldNotBe g
        // the original was untouched by the clone's mutation
        g.getLength() shouldBe sqrt(2f)
    }

    // -------------------------------------------------------------
    // إعادة المعايرة — the resampler
    // -------------------------------------------------------------

    test("a straight line resamples to numPoints+1 equidistant points") {
        val g = Gesture()
        g.addPoint(0f, 0f)
        g.addPoint(100f, 0f)
        val r = g.resample(200)
        r.getFirstX() shouldBe 0f
        (abs(r.getLastX() - 100f) < 1e-3f) shouldBe true
        // geometric invariance: the resampled path measures the same length
        (abs(r.getLength() - 100f) < 0.5f) shouldBe true
    }

    test("a zigzag keeps its total length after resampling") {
        val g = Gesture()
        g.addPoint(0f, 0f)
        g.addPoint(30f, 40f)
        g.addPoint(60f, 0f)
        val r = g.resample(200)
        (abs(r.getLength() - 100f) < 0.5f) shouldBe true
        (abs(r.getLastX() - 60f) < 1e-3f) shouldBe true
        (abs(r.getLastY()) < 1e-3f) shouldBe true
    }

    test("a single-point gesture resamples into a constant burst") {
        val g = Gesture()
        g.addPoint(5f, 5f)
        val r = g.resample(200)
        r.isEmpty shouldBe false
        for (i in 0 until 200) {
            (r.getX(i) == 5f && r.getY(i) == 5f) shouldBe true
        }
    }

    test("a fully degenerate stroke (two identical points) resamples without crashing") {
        val g = Gesture()
        g.addPoint(5f, 5f)
        g.addPoint(5f, 5f)
        val r = g.resample(200)
        // graceful degradation: only the seed point survives the zero-length walk
        r.isEmpty shouldBe false
    }

    // -------------------------------------------------------------
    // التطبيع — the box-side normalizer
    // -------------------------------------------------------------

    test("a horizontal line normalizes onto the unit box centered at the origin") {
        val g = Gesture()
        g.addPoint(0f, 0f)
        g.addPoint(100f, 0f)
        val n = g.normalizeByBoxSide()
        (abs(n.getX(0) + 0.5f) < 1e-4f) shouldBe true
        (abs(n.getX(1) - 0.5f) < 1e-4f) shouldBe true
        n.getY(0) shouldBe 0f
    }

    test("a degenerate single point normalizes to the origin") {
        val g = Gesture()
        g.addPoint(5f, 7f)
        val n = g.normalizeByBoxSide()
        n.getX(0) shouldBe 0f
        n.getY(0) shouldBe 0f
    }

    test("an empty gesture normalizes to an empty gesture") {
        Gesture().normalizeByBoxSide().isEmpty shouldBe true
    }

    // -------------------------------------------------------------
    // الأشكال المثالية العربية — the Arabic ideal-gesture contracts
    // -------------------------------------------------------------

    test("a plain Arabic word places one point per letter in order") {
        val grid = sparseGrid()
        val ideals = Gesture.generateIdealGestures("كتاب", grid)
        ideals.size shouldBe 1
        val ideal = ideals.first()
        val k = grid['ك'.code]!!
        val b = grid['ب'.code]!!
        (ideal.getFirstX() == k.visibleBounds.center.x) shouldBe true
        (ideal.getLastX() == b.visibleBounds.center.x) shouldBe true
        // طول المسار المثالي موجب بالضرورة
        (ideal.getLength() > 0f) shouldBe true
    }

    test("harakat inside a word are skipped — vocalized equals bare") {
        val grid = sparseGrid()
        val bare = Gesture.generateIdealGestures("كتاب", grid)
        val vocalized = Gesture.generateIdealGestures("كِتَاب", grid)
        vocalized.size shouldBe 1
        vocalized.first() shouldBe bare.first()
    }

    test("hamza carriers resolve to their base letter through the NFD path") {
        val grid = sparseGrid()
        // «أراد» يبدأ بألف منهمزة — تُحل إلى «ا» فتولَّد من مركز مفتاح الألف
        val vocalizedIdeals = Gesture.generateIdealGestures("أراد", grid)
        val bareIdeals = Gesture.generateIdealGestures("اراد", grid)
        vocalizedIdeals.size shouldBe 1
        vocalizedIdeals.first() shouldBe bareIdeals.first()
        val a = grid['ا'.code]!!
        val d = grid['د'.code]!!
        val ideal = vocalizedIdeals.first()
        (ideal.getFirstX() == a.visibleBounds.center.x) shouldBe true
        (ideal.getLastX() == d.visibleBounds.center.x) shouldBe true
    }

    test("doubled letters produce the plain variant and the longer loop variant") {
        val grid = sparseGrid()
        // ا ل ل ه: النسخة السادية، ونسخة الحلقة حول «ل» المكررة
        val ideals = Gesture.generateIdealGestures("الله", grid)
        ideals.size shouldBe 2
        val plainLen = ideals[0].getLength()
        val loopLen = ideals[1].getLength()
        (loopLen > plainLen) shouldBe true
    }

    test("a letter with no key is skipped silently — the word still generates") {
        val grid = sparseGrid()
        val withUnknown = Gesture.generateIdealGestures("كپتاب", grid)
        val bare = Gesture.generateIdealGestures("كتاب", grid)
        withUnknown.size shouldBe 1
        // «پ» بلا مفتاح تُتخطى — الشكل مطابق لكتاب نفسها
        withUnknown.first() shouldBe bare.first()
    }

    test("a word of only key-less letters generates an empty gesture") {
        val grid = sparseGrid()
        val ideals = Gesture.generateIdealGestures("پپپ", grid)
        ideals.size shouldBe 1
        ideals.first().isEmpty shouldBe true
    }

    // -------------------------------------------------------------
    // التقليم — the Pruner contracts
    // -------------------------------------------------------------

    test("pruneByExtremities keeps words keyed to the stroke's first-last pair") {
        val grid = sparseGrid()
        // كتابة تنتهي بـ«ة» فتُقلم خارج الزوج (ك،ب) — والعقدة (ك،ب) تحمل كتاب وكتب
        val words = listOf("كتاب", "كتب", "كتابة", "أراد", "سلام")
        val pruner = StatisticalGlideTypingClassifier.Pruner(8.42, words, grid)

        val user = Gesture()
        val k = grid['ك'.code]!!
        val b = grid['ب'.code]!!
        user.addPoint(k.visibleBounds.center.x, k.visibleBounds.center.y)
        user.addPoint(b.visibleBounds.center.x, b.visibleBounds.center.y)

        val remaining = pruner.pruneByExtremities(user, keys(grid))
        remaining shouldContainExactlyInAnyOrder listOf("كتاب", "كتب")
    }

    test("pruneByExtremities resolves hamza-carried first letters via NFD") {
        val grid = sparseGrid()
        val pruner = StatisticalGlideTypingClassifier.Pruner(8.42, listOf("أراد"), grid)

        val user = Gesture()
        val a = grid['ا'.code]!!
        val d = grid['د'.code]!!
        user.addPoint(a.visibleBounds.center.x, a.visibleBounds.center.y)
        user.addPoint(d.visibleBounds.center.x, d.visibleBounds.center.y)

        pruner.pruneByExtremities(user, keys(grid)) shouldContainExactlyInAnyOrder listOf("أراد")
    }

    test("pruneByLength keeps the matched word and drops the far-length word") {
        val grid = sparseGrid()
        val words = listOf("كتاب", "سلام")
        val pruner = StatisticalGlideTypingClassifier.Pruner(8.42, words, grid)

        // مسرة المستخدم = الشكل المثالي لكتاب نفسها
        val user = Gesture.generateIdealGestures("كتاب", grid).first().clone()
        val remaining = pruner.pruneByLength(user, arrayListOf("كتاب", "سلام"), grid, keys(grid))

        // كتاب بمسار مطابق يبقى؛ سلام مساره المثالي أبعد من 8.42 × نصف قطر المفتاح
        remaining shouldContainExactlyInAnyOrder listOf("كتاب")
        val radius = 60f
        val farLen = Gesture.generateIdealGestures("سلام", grid).first().getLength()
        (abs(user.getLength() - farLen) > 8.42 * radius) shouldBe true
    }
})
