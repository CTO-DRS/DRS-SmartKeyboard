/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import java.util.Locale

/**
 * DRS v2.2.0 — الجولة الثامنة «البلاطة الذكية»: عقل البلاطات الحتمي.
 *
 * This file is the PURE logic core of the tiles' intelligence — the same
 * doctrine as [DrsPanelSmart] beside it: nothing here touches Android UI
 * or Context, so every contract is covered by JVM unit tests exactly like
 * the rest of the DRS layer. The UI layer (لوحة الأدوات / محرر خانة
 * الشريط) owns the persistence and the composition; this file owns the
 * thinking:
 *
 *  1. [DrsTileText] — التطبيع العربي الواعي: نزع التشكيل والتطويل
 *     والعلامات الصفرية، طي الهمزات والتاء المربوطة والألف المقصورة،
 *     توحيد الأرقام الهندية، وحصر المسافات — مصدر واحد تحدث به كل
 *     المقارنات تحت هذه الوحدة.
 *  2. [DrsTileSearch] — بحث البلاطات: كل كلمة في السؤال يجب أن تنزل
 *     على أحد المفاتيح (دلالة «و») والرصيد يجمع أعلى درجة لكل كلمة —
 *     بادئة ثم بداية كلمة داخلية ثم احتواء، وعدم واحدة تُسقط الكل صادقًا.
 *  3. [DrsTileContextAdvisor] — التوصيات السياقية: النص قبل المؤشر
 *     وحده يرشّح الأدوات المعقولة (تشكيل للعربية العارية، نزع للتشكيل،
 *     فكّ الترميز لعناوين %XX…) بأسباب مغلقة لا تخمين، وما لا يُعرف
 *     عنه شيء يعود فارغًا — الصمت هو الصدق هنا.
 *  4. [DrsTileFavorites] — المفضلة المحدودة: ثمانية سقفًا صريحًا على
 *     نمط capPinned في الشريط الموحد، وامتلاؤه رفض صادق لا إسكات
 *     أقدم بلاطة.
 *
 * الأرقام والعدّادات فقط تُخزَّن خارجًا (أكواد البلاطات لا نصوص) —
 * والعقيدة سليمة: بلا AI ولا سحابة ولا تخمين.
 */

/** The tiles' shared Arabic-aware normalization (مصدر حقيقة واحد). */
object DrsTileText {

    /** The nine combining harakat + the superscript alef + the tatweel. */
    private val DIACRITICS = Regex("[\u064B-\u0652\u0670\u0640]")

    /** Directional/joining invisible marks — never meaningful in a query. */
    private val ZERO_WIDTH =
        Regex("[\u200B-\u200F\u202A-\u202E\u2066-\u2069\uFEFF]")

    /** Arabic-Indic and Extended (Persian/Urdu) digits fold to 0-9. */
    private val EASTERN_DIGITS = Regex("[\u0660-\u0669\u06F0-\u06F9]")

    /** The hamza-carrier family folds to the bare alef. */
    private val ALEF_FAMILY = "أإآٱ"

    /** One normalization pass — deterministic on every input. */
    fun normalize(text: String): String {
        if (text.isEmpty()) return text
        var s = ZERO_WIDTH.replace(text, "")
        s = DIACRITICS.replace(s, "")
        s = EASTERN_DIGITS.replace(s) { m ->
            val c = m.value[0].code
            val arabicIndic = c - 0x0660
            val extended = c - 0x06F0
            (if (arabicIndic in 0..9) arabicIndic else extended).toString()
        }
        for (ch in ALEF_FAMILY) s = s.replace(ch, 'ا')
        s = s.replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
            .lowercase(Locale.ROOT)
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /** The normalized whitespace-separated tokens of [text]. */
    fun tokens(text: String): List<String> =
        normalize(text).split(' ').filter { it.isNotEmpty() }

    /** True when [text] carries any Arabic block letter. */
    fun hasArabicLetter(text: String): Boolean =
        text.any { SymbolSmartSuggestor.isArabicLetter(it) }
}

/**
 * بحث البلاطات — the pure tile search. A query matches a tile only when
 * EVERY query token lands on one of the tile's keys (دلالة «و»)؛ the
 * score sums the best landing per token so «نزع تشكيل» ranks the strip
 * tool above anything that merely contains the word تشكيل. Zero is the
 * honest «no match» — never a loose guess.
 */
object DrsTileSearch {

    private const val SCORE_EXACT = 16
    private const val SCORE_PREFIX = 8
    private const val SCORE_WORD_START = 4
    private const val SCORE_CONTAINS = 2

    /**
     * The deterministic score of [query] over [keys] (each key a free
     * label/description/synonym string): 0 = no match, higher = closer.
     */
    fun rank(query: String, keys: List<String>): Int {
        val qTokens = DrsTileText.tokens(query)
        if (qTokens.isEmpty()) return 0
        val nKeys = keys.map { DrsTileText.normalize(it) }
            .filter { it.isNotEmpty() }
        if (nKeys.isEmpty()) return 0
        var total = 0
        for (qt in qTokens) {
            var best = 0
            for (k in nKeys) {
                val score = when {
                    k == qt -> SCORE_EXACT
                    k.startsWith(qt) -> SCORE_PREFIX
                    k.contains(" $qt") -> SCORE_WORD_START
                    k.contains(qt) -> SCORE_CONTAINS
                    else -> 0
                }
                if (score > best) best = score
            }
            // AND semantics: one unmatched token drops the whole query —
            // the honest refusal, not a fuzzy shrug.
            if (best == 0) return 0
            total += best
        }
        return total
    }

    /** True when [query] matches at all. */
    fun matches(query: String, keys: List<String>): Boolean =
        rank(query, keys) > 0
}

/**
 * التوصيات السياقية للبلاطات — the context advisor. The text before the
 * cursor (a bounded window the UI provides) decides which text tools
 * deserve the top advisory row, by CLOSED reasons only: an encoded URL
 * suggests its decoder, marks suggest their remover, naked Arabic
 * suggests the vocalizer, runs of spaces suggest the trimmer. Every
 * suggested tool keeps its own honest no-op (the closed grammar refuses
 * what it does not understand) — advice costs the user nothing, and a
 * context with no signal returns EMPTY, never a filler row.
 */
object DrsTileContextAdvisor {

    /** The advisory row cap — four picks keep the row one glance tall. */
    const val MAX_ADVICE = 4

    private val MULTI_SPACE = Regex("[ \t\u00A0]{2,}")
    private val PERCENT_ESCAPE = Regex("%[0-9A-Fa-f]{2}")

    /**
     * The ranked advisory picks for [beforeCursor] — the bounded text
     * window before the caret. Order = reason specificity (a decoder
     * before its encoder, removal before addition), duplicates collapsed
     * by the set, output capped at [MAX_ADVICE].
     */
    fun advise(beforeCursor: String): List<DrsTextTool> {
        val t = beforeCursor
        if (t.isEmpty()) return emptyList()
        val picks = LinkedHashSet<DrsTextTool>()

        val hasArabic = DrsTileText.hasArabicLetter(t)
        val hasHaraka = t.any { DrsHarakat.isCombiningMark(it) }
        val hasTatweel = t.contains(DrsHarakat.TATWEEL)
        val hasZeroWidth = ZERO_WIDTH_CHARS.containsMatchIn(t)
        val hasEmoji = EMOJI_CHARS.containsMatchIn(t)
        val hasWesternDigit = t.any { it in '0'..'9' }
        val hasEasternDigit = t.any { it in '\u0660'..'\u0669' || it in '\u06F0'..'\u06F9' }
        val lastMeaningful = t.trimEnd().lastOrNull()

        // 1) عناوين مرمّزة (%XX) تفكّ ترميزها أولًا — السبب الأدق يتصدر.
        if (PERCENT_ESCAPE.containsMatchIn(t)) picks += DrsTextTool.URL_DECODE
        // 2) العلامات الصفرية والتطويل نفاية لصق لا تصلح فقرة — انزعها.
        if (hasZeroWidth) picks += DrsTextTool.REMOVE_ZERO_WIDTH
        if (hasTatweel) picks += DrsTextTool.REMOVE_TATWEEL
        // 3) نص مُشكَّل يرشّح نازع التشكيل، وعربية عارية ترشّح المشكِّل —
        //    ولا يُرشَّح المشكِّل فوق نص فيه حركات أصلًا.
        if (hasHaraka) {
            picks += DrsTextTool.REMOVE_DIACRITICS
        } else if (hasArabic) {
            picks += DrsTextTool.TASHKEEL_TEXT
        }
        // 4) مسافات متراكمة ورموز اجتماعية واقتباس لاتيني في سياق عربي.
        if (MULTI_SPACE.containsMatchIn(t)) picks += DrsTextTool.TRIM_SPACES
        if (hasEmoji) picks += DrsTextTool.STRIP_EMOJI
        if (hasArabic && t.any { it == ',' || it == '?' || it == ';' }) {
            picks += DrsTextTool.TO_ARABIC_PUNCTUATION
        }
        // 5) الأرقام تنطق أو تتوحّد: رقم قبل المؤشر ينطق كلمات، والأرقام
        //    الشرقية وحدها ترشّح توحيدها غربيًا.
        if (lastMeaningful?.isDigit() == true) picks += DrsTextTool.NUMBER_WORDS
        if (hasEasternDigit) picks += DrsTextTool.TO_WESTERN_DIGITS

        return picks.take(MAX_ADVICE).toList()
    }
}

/**
 * المفضلة المحدودة — the bounded favorites of the tools' tiles. The list
 * keeps PIN ORDER (the order the user added), removal is by value, and
 * the [MAX_FAVORITES] cap is an HONEST refusal ([ToggleResult.Full])
 * — the user is told «امتلأت المفضلة» instead of a silent eviction of
 * an older tile, mirroring the unified strip's capPinned doctrine.
 * Pure — persistence belongs to the UI store beside the panel.
 */
object DrsTileFavorites {

    /** The explicit favorites cap — eight is one glance's worth. */
    const val MAX_FAVORITES = 8

    /** The outcome of one toggle: a new list, or an honest refusal. */
    sealed interface ToggleResult {

        /** The favorites list after the toggle (add or remove). */
        data class Ok(val favorites: List<Int>) : ToggleResult

        /** The cap refused the add — the caller tells the user honestly. */
        object Full : ToggleResult
    }

    /**
     * One toggle of [code] over [current]: present → removed (order of
     * the rest preserved), absent → appended unless the list is full.
     */
    fun toggled(
        current: List<Int>,
        code: Int,
        max: Int = MAX_FAVORITES,
    ): ToggleResult {
        return if (code in current) {
            ToggleResult.Ok(current - code)
        } else if (current.size >= max) {
            ToggleResult.Full
        } else {
            ToggleResult.Ok(current + code)
        }
    }

    /** True when one more tile may be pinned. */
    fun canFavorite(current: List<Int>, max: Int = MAX_FAVORITES): Boolean =
        current.size < max
}
