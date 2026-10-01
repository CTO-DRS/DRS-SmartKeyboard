/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.ImeUiMode

/**
 * DRS v1.15.0 — الأنظمة الذكية للوحات الجديدة (لوحة الحركات، لوحة الرموز
 * الذكية، لوحة الحروف الموسعة).
 *
 * This file is the PURE logic core of the three new smart panels: the
 * harakat catalogue and the smart-stacking insert decision, the
 * context-aware symbol suggestions, and the shared "most used" recents
 * engine. Nothing here touches Android UI or Context, so every contract
 * is covered by JVM unit tests exactly like the rest of the DRS layer.
 *
 * الأنظمة الذكية الثلاثة:
 *  1. الدمج الذكي للحركات: حركة جديدة فوق حركة تستبدلها بدل أن تتراكم،
 *     مع استثناء صادق لزوج الشدة (ّ + حركة) لأنه تركيب مشروع.
 *  2. اقتراحات الرموز السياقية: النص قبل المؤشر يحدد صف الرموز المقترحة
 *     (أرقام → ٪ ° ÷، حروف عربية → ـ ، ؛، سياق كود → أقواس …).
 *  3. الأكثر استخدامًا المشترك: عدّادات محلية خالصة لكل لوحة، الأعلى
 *      استخدامًا يطفو أولًا مع كسر تعادل بترتيب الكتالوج.
 */

/** The Arabic harakat (تشكيل) catalogue shared by the smart insert engine. */
object DrsHarakat {
    const val FATHA = '\u064E'          // َ
    const val DAMMA = '\u064F'          // ُ
    const val KASRA = '\u0650'          // ِ
    const val SUKUN = '\u0652'          // ْ
    const val SHADDA = '\u0651'         // ّ
    const val FATHATAN = '\u064B'       // ً
    const val DAMMATAN = '\u064C'       // ٌ
    const val KASRATAN = '\u064D'       // ٍ
    const val SUPERSCRIPT_ALEF = '\u0670' // ٰ
    const val TATWEEL = '\u0640'        // ـ (extension, not a combining mark)

    /** The nine combining marks — a new mark may smartly replace one of these. */
    val MARKS: List<Char> = listOf(
        FATHA, DAMMA, KASRA, SUKUN, SHADDA,
        FATHATAN, DAMMATAN, KASRATAN, SUPERSCRIPT_ALEF,
    )

    private val MARK_SET: Set<Char> = MARKS.toHashSet()

    /** Panel display order: the nine marks then the stretching tatweel. */
    val GRID: List<Char> = MARKS + TATWEEL

    /** True for the nine combining harakat; the tatweel is NOT a mark. */
    fun isCombiningMark(c: Char): Boolean = c in MARK_SET

    /** True for the whole panel grid (marks + tatweel). */
    fun isHarakatTile(c: Char): Boolean = c in MARK_SET || c == TATWEEL

    /** Shadda + haraka pairs that commit two characters in one tap. */
    val COMBOS: List<String> = listOf(
        "$SHADDA$FATHA", // شَّ
        "$SHADDA$DAMMA", // شُّ
        "$SHADDA$KASRA", // شِّ
        "$SHADDA$SUKUN", // شّْ
    )
}

/** How the smart insert engine applies the tapped haraka. */
enum class HarakaInsertMode {
    /** Commit the haraka after the cursor (normal insertion). */
    APPEND,

    /** Delete the single haraka before the cursor, then commit the new one. */
    REPLACE_PREVIOUS,
}

/**
 * The smart stacking engine (الدمج الذكي للحركات). Typing a haraka right
 * after another haraka replaces it instead of stacking two marks that can
 * never render sanely — with two honest exceptions:
 *  - re-tapping the SAME mark is idempotent (replace, not double);
 *  - shadda followed by a vowel mark is a legitimate combo and appends.
 * With [smartReplace] off the engine always appends (classic behavior).
 */
object HarakatSmartInsert {

    /**
     * DRS v1.17.0: haraka-first backspace — true when the delete key may
     * peel ONE diacritic off the letter before the cursor instead of
     * deleting the whole ICU grapheme cluster (letter + all marks) in a
     * single tap. Repeated taps therefore remove شدة then the haraka then
     * the letter, giving the typist full control over the smart stacking
     * this panel produces. Pure and unit-tested; the editor consults it
     * before its cluster delete path.
     */
    fun shouldStripBeforeDelete(textBeforeCursor: String, enabled: Boolean): Boolean {
        if (!enabled) return false
        val last = textBeforeCursor.lastOrNull() ?: return false
        return DrsHarakat.isCombiningMark(last)
    }

    fun decide(previous: Char?, haraka: Char, smartReplace: Boolean): HarakaInsertMode {
        if (!smartReplace) return HarakaInsertMode.APPEND
        val prev = previous ?: return HarakaInsertMode.APPEND
        if (!DrsHarakat.isCombiningMark(prev)) return HarakaInsertMode.APPEND
        if (prev == haraka) return HarakaInsertMode.REPLACE_PREVIOUS
        if (prev == DrsHarakat.SHADDA && haraka != DrsHarakat.SHADDA) {
            return HarakaInsertMode.APPEND
        }
        return HarakaInsertMode.REPLACE_PREVIOUS
    }
}

/**
 * The context-aware symbol suggestions (اقتراحات الرموز الذكية). Pure:
 * the text before the cursor decides which six symbols lead the smart
 * symbols panel — digits suggest percent/degree/currency, Arabic letters
 * suggest tatweel and Arabic punctuation, math context extends the
 * relation, open brackets suggest their closer, and a code-ish window
 * suggests brackets. Everything is honest, deterministic, offline.
 */
object SymbolSmartSuggestor {

    const val MAX_SUGGESTIONS = 6

    /** The default row when nothing about the context is known. */
    val DEFAULT: List<String> = listOf("@", "#", "%", "&", "*", "«")

    private val ARABIC_INDIC_DIGITS = '٠'..'٩'

    private val CODE_CONTEXT_CHARS = "{}[]<>=;&|".toSet()

    fun suggest(textBeforeCursor: String): List<String> {
        val ch = textBeforeCursor.lastOrNull() ?: return DEFAULT
        return when {
            ch.isDigit() || ch in ARABIC_INDIC_DIGITS -> listOf("%", "°", "÷", "×", "$", "﷼")
            ch == '=' -> listOf("≠", "≈", "≤", "≥", "+", "−")
            ch == '<' -> listOf("≤", ">", "≥", "⇐", "→", "«")
            ch == '>' -> listOf("≥", "<", "≤", "⇒", "←", "»")
            isArabicLetter(ch) -> listOf("ـ", "،", "؛", "؟", "«", "»")
            ch == '(' || ch == '[' || ch == '{' ->
                listOf(closerFor(ch), "(", ")", "[", "]", "{")
            textBeforeCursor.takeLast(8).any { it in CODE_CONTEXT_CHARS } ->
                listOf("{", "}", "(", ")", "<", "=")
            else -> DEFAULT
        }.take(MAX_SUGGESTIONS)
    }

    /** The matching closer for the common openers (identity otherwise). */
    fun closerFor(open: Char): String = when (open) {
        '(' -> ")"
        '[' -> "]"
        '{' -> "}"
        '«' -> "»"
        else -> open.toString()
    }

    /**
     * True for Arabic block letters only — not the Arabic-Indic digits,
     * not the combining harakat, not the tatweel.
     */
    fun isArabicLetter(ch: Char): Boolean {
        if (ch.code !in 0x0600..0x06FF) return false
        if (!Character.isLetter(ch)) return false
        if (ch in ARABIC_INDIC_DIGITS) return false
        if (DrsHarakat.isCombiningMark(ch)) return false
        if (ch == DrsHarakat.TATWEEL) return false
        return true
    }
}

/**
 * DRS v1.16.0 — لوحة الحركات كلوحة مفاتيح كاملة (the harakat KEYBOARD
 * panel). The user asked for a panel «مشابه تمامًا للوحة الحروف أو
 * الأرقام» — exactly like the letters/numbers panels — so the catalogue
 * is arranged as a real keyboard: four rows of four wide keys each (the
 * numeric panel's anatomy), rendered through the very same themed key
 * element the real keys use. Pure data + pure labels so the arrangement
 * and its display are pinned by unit tests.
 */
sealed interface DrsKeyboardHarakatKey {

    /** One combining haraka, inserted through the smart engine. */
    data class Haraka(val char: Char) : DrsKeyboardHarakatKey

    /** The shadda+haraka combo, committed as two characters. */
    data class Combo(val text: String) : DrsKeyboardHarakatKey

    /** The tatweel (stretch) — not a combining mark. */
    object Tatweel : DrsKeyboardHarakatKey

    /** The real delete key (hold-to-repeat in the UI layer). */
    object Delete : DrsKeyboardHarakatKey

    /** The real space key. */
    object Space : DrsKeyboardHarakatKey
}

object DrsKeyboardHarakat {

    /** The dotted circle base the combining marks render on. */
    const val DOTTED_CIRCLE = '◌' // U+25CC

    /**
     * The full keyboard arrangement — four rows x four keys, the exact
     * anatomy of the numbers panel:
     *  1. التنوينات + الألف الخنجرية
     *  2. الحركات الأساسية
     *  3. الشدة والتطويل + مفتاحا الحذف والمسافة
     *  4. التشكيل المزدوج (شدة + حركة)
     */
    val ROWS: List<List<DrsKeyboardHarakatKey>> = listOf(
        listOf(
            DrsKeyboardHarakatKey.Haraka(DrsHarakat.FATHATAN),
            DrsKeyboardHarakatKey.Haraka(DrsHarakat.DAMMATAN),
            DrsKeyboardHarakatKey.Haraka(DrsHarakat.KASRATAN),
            DrsKeyboardHarakatKey.Haraka(DrsHarakat.SUPERSCRIPT_ALEF),
        ),
        listOf(
            DrsKeyboardHarakatKey.Haraka(DrsHarakat.FATHA),
            DrsKeyboardHarakatKey.Haraka(DrsHarakat.DAMMA),
            DrsKeyboardHarakatKey.Haraka(DrsHarakat.KASRA),
            DrsKeyboardHarakatKey.Haraka(DrsHarakat.SUKUN),
        ),
        listOf(
            DrsKeyboardHarakatKey.Haraka(DrsHarakat.SHADDA),
            DrsKeyboardHarakatKey.Tatweel,
            DrsKeyboardHarakatKey.Delete,
            DrsKeyboardHarakatKey.Space,
        ),
        listOf(
            DrsKeyboardHarakatKey.Combo("${DrsHarakat.SHADDA}${DrsHarakat.FATHA}"),
            DrsKeyboardHarakatKey.Combo("${DrsHarakat.SHADDA}${DrsHarakat.DAMMA}"),
            DrsKeyboardHarakatKey.Combo("${DrsHarakat.SHADDA}${DrsHarakat.KASRA}"),
            DrsKeyboardHarakatKey.Combo("${DrsHarakat.SHADDA}${DrsHarakat.SUKUN}"),
        ),
    )

    /** All the harakat keys of the arrangement (no delete/space). */
    val HARAKAT_KEYS: List<DrsKeyboardHarakatKey> = ROWS.flatten()
        .filter { it !is DrsKeyboardHarakatKey.Delete && it !is DrsKeyboardHarakatKey.Space }

    /**
     * The display label of a key: combining marks render on the dotted
     * circle (◌َ) so a lone mark is visible exactly like real Arabic
     * keyboards show it; the tatweel, space and delete keys have their
     * own honest glyphs.
     */
    fun label(key: DrsKeyboardHarakatKey): String = when (key) {
        is DrsKeyboardHarakatKey.Haraka -> "$DOTTED_CIRCLE${key.char}"
        is DrsKeyboardHarakatKey.Combo -> "$DOTTED_CIRCLE${key.text}"
        DrsKeyboardHarakatKey.Tatweel -> "${DrsHarakat.TATWEEL}"
        DrsKeyboardHarakatKey.Delete -> "\u232B"
        DrsKeyboardHarakatKey.Space -> "\u2423"
    }
}

/**
 * DRS v1.16.0 — «أعد ترتيب وتطوير جميع الوحات بنظام مرتب وذكي» — the
 * smart panel ordering. The switcher chips of the three smart panels
 * reorder themselves from the LOCAL panel-open counters: the current
 * panel always leads (stable anchor), the rest follow by usage with
 * ties broken by catalogue order. Zero text ever recorded — only which
 * PANEL was opened, local only.
 */
object DrsPanelOrder {

    /** The persisted namespace of the panel-open counters. */
    const val USAGE_NAMESPACE = "panels"

    /**
     * The ordered switcher: [current] first (always, even if never
     * used), then the remaining panels by their usage counts (most
     * used first, ties break by [catalogue] order). A panel missing
     * from the usage map counts as zero. Pure and deterministic.
     */
    fun smartSwitcher(
        current: ImeUiMode,
        usage: Map<String, Int>,
        catalogue: List<ImeUiMode>,
    ): List<ImeUiMode> {
        if (catalogue.size <= 1) return catalogue
        val index = catalogue.withIndex().associate { (i, mode) -> mode to i }
        val rest = catalogue.filter { it != current }
            .sortedWith(
                compareByDescending<ImeUiMode> { usage[it.name] ?: 0 }
                    .thenBy { index[it]!! },
            )
        return listOf(current) + rest
    }

    /** Records one panel open into the local counters (pure input). */
    fun recordOpen(counts: Map<String, Int>, mode: ImeUiMode): Map<String, Int> {
        return PanelUsageTracker.record(counts, mode.name)
    }
}

/**
 * The shared «الأكثر استخدامًا» recents engine for the new panels. Pure:
 * callers own the persisted Map<String, Int> (tile key -> use count) and
 * this object only grows it, trims it and reads the top row back. Counts
 * only — never text, never timestamps; the keys are the panel tiles
 * themselves.
 */
object PanelUsageTracker {

    const val MAX_RECENTS = 6
    private const val MAX_ENTRIES = 64

    /** Records one use of [key], keeping the map bounded. */
    fun record(counts: Map<String, Int>, key: String): Map<String, Int> {
        val grown = counts + (key to ((counts[key] ?: 0) + 1))
        if (grown.size <= MAX_ENTRIES) return grown
        // Evict the least-used (ties break towards the catalogue tail —
        // i.e. the largest catalogue index) so hot tiles always survive.
        val evictable = grown.entries
            .sortedWith(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { it.key },
            )
            .take(grown.size - MAX_ENTRIES)
            .map { it.key }
            .toHashSet()
        return grown.filterKeys { it !in evictable }
    }

    /**
     * The recents row: used tiles only, most-used first, ties broken by
     * catalogue order so the row is stable, capped at [n].
     */
    fun topRecents(counts: Map<String, Int>, catalogueOrder: List<String>, n: Int = MAX_RECENTS): List<String> {
        if (n <= 0) return emptyList()
        val index = catalogueOrder.withIndex().associate { (i, id) -> id to i }
        return counts.asSequence()
            .filter { (key, count) -> count > 0 && index.containsKey(key) }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { index[it.key]!! },
            )
            .take(n)
            .map { it.key }
            .toList()
    }
}
