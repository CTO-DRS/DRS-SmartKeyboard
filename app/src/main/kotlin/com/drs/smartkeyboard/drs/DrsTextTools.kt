/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import java.text.Collator
import java.util.Locale

/**
 * DRS v1.0.6: pure text transformation engine for the technical user system.
 *
 * Every tool operates on a plain [String] and returns a new [String] - no
 * input-connection or UI dependency here, so all transformations are fully
 * unit-testable. The editor glue lives in
 * [com.drs.smartkeyboard.ime.editor.EditorInstance.performTextTool].
 *
 * Design rules:
 *  - Never throws: callers pass arbitrary user text; every function is
 *    written defensively and returns the input unchanged when it cannot be
 *    transformed.
 *  - Never reorders or drops content except where the tool's purpose IS
 *    removal (empty lines, duplicate lines, diacritics, extra spaces).
 *  - Locale-aware casing through [Locale] so Arabic/Turkish/etc. behave
 *    correctly on the user's device.
 */

/** Horizontal whitespace: spaces, tabs and NBSP (never newlines). */
private val HORIZONTAL_SPACES = Regex("[ \\t\\u00A0\\u2007\\u202F]+")

/** Arabic diacritics (harakat), small quranic marks and superscript alef. */
private val ARABIC_DIACRITICS = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")

/** Arabic tatweel (kashida) character — pure elongation, carries no meaning. */
private const val TATWEEL = '\u0640'

/** First Arabic-Indic digit (٠). Digits 0-9 map to U+0660-U+0669. */
private const val ARABIC_INDIC_ZERO = '\u0660'

/** First EXTENDED Arabic-Indic digit (۰, Persian/Urdu). U+06F0-U+06F9. */
private const val EXTENDED_ARABIC_INDIC_ZERO = '\u06F0'

/** Punctuation that must never keep a space BEFORE it (Latin + Arabic). */
private val SPACE_BEFORE_PUNCT = Regex("[ \\t\\u00A0]+([,;:.!?،؛…؟)\\]\\}»])")

/**
 * Punctuation that should be followed by one space when the next character is
 * a letter. '.' is intentionally excluded for Latin text (domains, files);
 * it is handled separately for Arabic letters only.
 */
private val SPACE_AFTER_PUNCT = Regex("([,;:!?،؛…])(?=[\\p{IsAlphabetic}])")

/** Arabic letter following a '.' (sentence dot in Arabic text). */
private val DOT_BEFORE_ARABIC = Regex("\\.(?=[\\u0600-\\u06FF])")

/** DRS v1.5.0: zero-width / invisible formatting characters that carry no
 *  visible content and break search and copy. U+200C (ZWNJ) is deliberately
 *  NOT here: نصف المسافة is a real Arabic keyboard character with meaning. */
private val ZERO_WIDTH_CHARS = Regex("[\\u200B\\u200D\\u200E\\u200F\\uFEFF\\u2066-\\u2069]")

/** DRS v1.5.0: sentence ender followed by horizontal whitespace — the
 *  boundary where SENTENCE_PER_LINE inserts the line break. The lookahead
 *  guarantees the ender is between sentences (not at the text's end), and
 *  existing newlines are left untouched so the tool is idempotent. */
private val SENTENCE_BOUNDARY = Regex("([.!؟?…])[ \\t\\u00A0]+(?=[^\\s])")

/** DRS v1.6.0: a run of one or more whitespace characters — the word
 *  separator REVERSE_WORDS splits each line on. */
private val WHITESPACE_RUN = Regex("\\s+")

/** DRS v1.7.0: emoji and pictograph blocks plus their joiners and
 *  selectors (VS16, skin-tone modifiers, ZWJ). Letters, digits,
 *  punctuation, whitespace and every script pass through untouched.
 *  NOTE: the supplementary-plane bounds use \x{...} — Java/Kotlin \uXXXX
 *  is exactly four digits, so \u1F000 would silently parse as U+1F00+"0"
 *  and corrupt the whole class (this really broke the first draft). */
private val EMOJI_CHARS = Regex(
    "[\\u2600-\\u27BF\\uFE0F\\u200D\\uD83C-\\uD83E\\uDC00-\\uDFFF\\x{1F000}-\\x{1FAFF}]",
)

/** DRS v1.7.0: list separators recognized by SPLIT_TO_LINES — both the
 *  Latin comma/semicolon and their Arabic counterparts (، ؛). */
private val LIST_SEPARATORS = Regex("[,،;؛]")

/** DRS v1.8.0: the boundary between a digit and a letter (either
 *  direction). \p{N} covers 0-9 and ٠-۹ alike, \p{L} covers Arabic and
 *  Latin letters; lookaround means insertion never consumes characters. */
private val DIGIT_THEN_LETTER = Regex("(?<=\\p{N})(?=\\p{L})")
private val LETTER_THEN_DIGIT = Regex("(?<=\\p{L})(?=\\p{N})")

/** DRS v1.8.0: every Unicode punctuation mark (category P) — the Latin
 *  set plus the Arabic ، ؛ ؟ and quotes, brackets, dashes and more. */
private val PUNCTUATION_CHARS = Regex("\\p{IsPunctuation}")

enum class DrsTextTool(
    val code: Int,
    /** Info-only tools show a result message instead of modifying text. */
    val isInfoOnly: Boolean = false,
    /** Editor-level tools that need the cursor position, not a transform. */
    val isEditorOp: Boolean = false,
    /** DRS v1.21.0: tools that commit an invisible mark at the cursor. */
    val isInsertMark: Boolean = false,
) {
    // ---- Case transformation (applies to selection, else whole field) ----
    UPPERCASE(-601),
    LOWERCASE(-602),
    TITLE_CASE(-603),
    SENTENCE_CASE(-604),

    // ---- Spacing and lines ----
    TRIM_SPACES(-611),
    TRIM_LINE_EDGES(-612),
    REMOVE_EMPTY_LINES(-613),
    REMOVE_LINE_BREAKS(-614),
    SORT_LINES(-615),
    REMOVE_DUPLICATE_LINES(-616),

    // DRS v1.1.0: line-ordering additions (same pure-transform family).
    NUMBER_LINES(-617),
    REVERSE_LINES(-618),

    // DRS v1.2.0: locale-aware quote wrapping (same pure-transform family).
    WRAP_QUOTES(-619),

    // DRS v1.3.0: collapse runs of blank lines into one (same family).
    COLLAPSE_EMPTY_LINES(-620),

    // DRS v1.4.0: descending line sort (the counterpart of SORT_LINES).
    SORT_LINES_DESC(-624),

    // ---- Arabic-specific ----
    REMOVE_DIACRITICS(-621),

    // DRS v1.3.0: Arabic digit conversions + tatweel removal.
    REMOVE_TATWEEL(-622),
    TO_ARABIC_DIGITS(-605),
    TO_WESTERN_DIGITS(-606),

    // DRS v1.4.0: unifies Arabic letter variants (hamza-carriers,
    // ta-marbuta, alef maqsura) — same letter-normalization family.
    NORMALIZE_ARABIC(-623),

    // DRS v1.5.0: case inversion, Arabic punctuation mapping, zero-width
    // cleanup and tab→space conversion (same pure-transform families).
    TOGGLE_CASE(-607),
    TO_ARABIC_PUNCTUATION(-608),
    REMOVE_ZERO_WIDTH(-609),
    TABS_TO_SPACES(-610),

    // DRS v1.5.0: length sort, word-level dedup, paren wrapping and one
    // sentence per line (same line/quick families).
    SORT_LINES_BY_LENGTH(-625),
    REMOVE_DUPLICATE_WORDS(-626),
    WRAP_PARENS(-627),
    SENTENCE_PER_LINE(-628),

    // DRS v1.6.0: the inverse tab↔space and punctuation mappings, the
    // edge-only blank trim and word-order reversal (same families).
    SPACES_TO_TABS(-629),
    TO_WESTERN_PUNCTUATION(-630),
    TRIM_BLANK_EDGES(-633),
    REVERSE_WORDS(-634),

    // DRS v1.7.0: PDF/web paste repair (presentation forms), list↔lines
    // splitting (Arabic comma aware), hashtag-style space removal, emoji
    // stripping and the URL encode/decode pair (same pure families).
    NORMALIZE_ARABIC_FORMS(-635),
    SPLIT_TO_LINES(-636),
    JOIN_LINES(-637),
    REMOVE_ALL_SPACES(-638),
    STRIP_EMOJI(-639),
    URL_ENCODE(-642),
    URL_DECODE(-643),

    // DRS v1.8.0: mixed-script cleanup — a single space between digits and
    // letters in both directions, and full Unicode punctuation removal
    // (same pure families).
    SEPARATE_DIGIT_LETTERS(-644),
    REMOVE_PUNCTUATION(-645),

    // ---- Punctuation and full clean-up ----
    NORMALIZE_PUNCTUATION(-631),
    CLEAN_TEXT(-632),

    // ---- Info (never modifies text) ----
    COUNT(-641, isInfoOnly = true),

    // ---- Editor-level line operations (cursor-relative) ----
    DELETE_LINE(-651, isEditorOp = true),
    DELETE_TO_LINE_START(-652, isEditorOp = true),
    DELETE_TO_LINE_END(-653, isEditorOp = true),

    // DRS v1.21.0: invisible directional/joining marks committed at the
    // cursor — the tools RTL authors keep reaching for in every other
    // editor («إدراج RLM/LRM/ZWJ/ZWNJ»). Insertion needs no text, works
    // on an empty field, and never replaces anything.
    INSERT_RLM(-654, isEditorOp = true, isInsertMark = true),
    INSERT_LRM(-655, isEditorOp = true, isInsertMark = true),
    INSERT_ZWJ(-656, isEditorOp = true, isInsertMark = true),
    INSERT_ZWNJ(-657, isEditorOp = true, isInsertMark = true),

    // DRS v1.4.0 (public): the deterministic sentence vocalization — the
    // exact counterpart of REMOVE_DIACRITICS. Every Arabic word run is
    // looked up in the local lexicons (the reviewed seed first, then the
    // 3000-word asset); a known word takes its canonical vocalized form,
    // an unknown word stays byte-identical — never an invented mark.
    // Pure, offline, on-device: the doctrine holds.
    TASHKEEL_TEXT(-658),

    // DRS v1.6.0: the deterministic number-to-Arabic-words rendering —
    // 1234 → «ألف ومائتان وأربعة وثلاثون». The WHOLE field must be a
    // clean integer (Western, Arabic-Indic or Extended digits, optional
    // leading minus); anything else returns byte-identical — the tool
    // never rewrites prose it does not understand. See [DrsNumberWords].
    NUMBER_WORDS(-659),

    // DRS v1.7.0: the deterministic financial tafqit — a clean monetary
    // amount becomes its formal check-writing words «فقط … لا غير»
    // (see [DrsTafqit]): closed five-currency catalog, documented
    // counted-noun agreement, feminine subunit counting; anything that
    // is not a clean amount returns byte-identical, never a guess.
    TAFQIT(-660),

    // DRS v1.8.0: the deterministic date-in-words rendering — the trilogy
    // closes (v1.6.0 numbers, v1.7.0 amounts, v1.8.0 dates): a clean
    // three-component date over one consistent separator becomes its
    // formal documentary phrase «الثالث من أكتوبر عام ألفين وستة وعشرين»
    // (see [DrsDateWords]): closed 31-day ordinal table, closed 12-month
    // catalog, the year through [DrsNumberWords] in the genitive; any
    // ambiguous or invalid shape returns byte-identical, never a guess.
    DATE_WORDS(-661),

    // DRS v1.9.0: the deterministic clock-time-in-words rendering — the
    // quartet closes (v1.6.0 numbers, v1.7.0 amounts, v1.8.0 dates,
    // v1.9.0 times): a clean two-component clock time over the single
    // colon separator becomes its formal spoken phrase «الثالثة إلا
    // الربع» (see [DrsTimeWords]): closed 12-entry feminine hour table,
    // the documented quarter/half/إلا branches and feminine counted-noun
    // minutes; any ambiguous or invalid shape returns byte-identical,
    // never a guess.
    TIME_WORDS(-662),

    // DRS v1.10.0: the deterministic fraction-in-words rendering — the
    // first expansion of the fifth round (v1.10.0 fractions, ordinals,
    // weekdays): a clean two-component fraction over one fraction slash
    // becomes its formal written words «ثلاثة أرباع» (see
    // [DrsFractionWords]): the closed Arabic fraction-word zone
    // (denominator 2..10, numerator 1..10), the documented counted-noun
    // agreement (النصف، ثلثان، ثلاثة أثلاث) and no tanwin — house
    // style; any ambiguous or invalid shape returns byte-identical,
    // never a guess.
    FRACTION_WORDS(-663),

    // DRS v1.10.0: the deterministic weekday resolution — a clean date
    // resolves to its weekday name «السبت» (see [DrsDateWords]): the
    // same closed date parser as DATE_WORDS plus the documented
    // Sakamoto congruence (exhaustively verified against the reference
    // calendar for every accepted date 1..9999); any ambiguous or
    // invalid shape returns byte-identical, never a guess.
    WEEKDAY(-664);

    companion object {
        /** Inclusive range covering every tool code, for fast dispatch. */
        val CODE_RANGE = -664..-601

        fun fromCode(code: Int): DrsTextTool? = entries.firstOrNull { it.code == code }
    }
}

object DrsTextTools {

    /**
     * DRS v1.21.0 — the invisible mark each insertion tool commits at the
     * cursor (pure, JVM-testable): RLM (U+200F) and LRM (U+200E) steer the
     * bidi resolution of neutral characters, ZWJ (U+200D) joins letters or
     * emoji into ligatures/sequences, ZWNJ (U+200C) — نصف المسافة — breaks
     * the join. Null for every non-insertion tool.
     */
    fun insertionMarkFor(tool: DrsTextTool): Char? = when (tool) {
        DrsTextTool.INSERT_RLM -> '\u200F'
        DrsTextTool.INSERT_LRM -> '\u200E'
        DrsTextTool.INSERT_ZWJ -> '\u200D'
        DrsTextTool.INSERT_ZWNJ -> '\u200C'
        else -> null
    }

    /**
     * Applies [tool] to [text]. Pure function; never throws.
     */
    fun apply(tool: DrsTextTool, text: String, locale: Locale = Locale.getDefault()): String {
        if (text.isEmpty() && tool != DrsTextTool.COUNT) return text
        return try {
            when (tool) {
                DrsTextTool.UPPERCASE -> text.uppercase(locale)
                DrsTextTool.LOWERCASE -> text.lowercase(locale)
                DrsTextTool.TITLE_CASE -> titleCase(text, locale)
                DrsTextTool.SENTENCE_CASE -> sentenceCase(text, locale)
                DrsTextTool.TRIM_SPACES -> collapseHorizontalSpaces(text).trim(' ', '\t', '\u00A0')
                DrsTextTool.TRIM_LINE_EDGES -> text.lines().joinToString("\n") { line ->
                    line.trim(' ', '\t', '\u00A0')
                }
                DrsTextTool.REMOVE_EMPTY_LINES -> text.lines()
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                DrsTextTool.REMOVE_LINE_BREAKS -> collapseHorizontalSpaces(
                    text.replace(Regex("\\s*\\n+\\s*"), " "),
                )
                DrsTextTool.SORT_LINES -> {
                    val trailingNewline = text.endsWith("\n")
                    val collator = Collator.getInstance(locale)
                    val sorted = text.lines()
                        .let { if (trailingNewline) it.dropLast(1) else it }
                        .sortedWith(compareBy(collator) { it })
                        .joinToString("\n")
                    if (trailingNewline) "$sorted\n" else sorted
                }
                DrsTextTool.REMOVE_DUPLICATE_LINES -> {
                    val seen = HashSet<String>()
                    text.lines()
                        .filter { line ->
                            val key = line.trim(' ', '\t', '\u00A0')
                            seen.add(key)
                        }
                        .joinToString("\n")
                }
                // DRS v1.1.0: prefix every line with its 1-based order, and
                // flip the line order. Both preserve a trailing newline and
                // keep blank lines exactly where they are.
                DrsTextTool.NUMBER_LINES -> {
                    val trailingNewline = text.endsWith("\n")
                    val numbered = text.lines()
                        .let { if (trailingNewline) it.dropLast(1) else it }
                        .mapIndexed { index, line -> "${index + 1}. $line" }
                        .joinToString("\n")
                    if (trailingNewline) "$numbered\n" else numbered
                }
                DrsTextTool.REVERSE_LINES -> {
                    val trailingNewline = text.endsWith("\n")
                    val reversed = text.lines()
                        .let { if (trailingNewline) it.dropLast(1) else it }
                        .asReversed()
                        .joinToString("\n")
                    if (trailingNewline) "$reversed\n" else reversed
                }
                // DRS v1.2.0: surround the text with quotation marks that
                // match the active locale — Arabic guillemets «» for Arabic,
                // straight quotes for everything else.
                DrsTextTool.WRAP_QUOTES -> {
                    val (openQuote, closeQuote) =
                        if (locale.language == "ar") "«" to "»" else "\"" to "\""
                    openQuote + text + closeQuote
                }
                // DRS v1.3.0: turn every run of consecutive blank lines
                // into exactly one blank line. Leading and trailing runs
                // are collapsed to one blank line as well (this tool's
                // purpose IS removal — of the extra blanks only; single
                // blank separators and the trailing newline are kept).
                DrsTextTool.COLLAPSE_EMPTY_LINES -> collapseEmptyLines(text)
                DrsTextTool.REMOVE_DIACRITICS -> text.replace(ARABIC_DIACRITICS, "")
                // DRS v1.4.0 (public): the sentence vocalization — the
                // lexicon-based counterpart of REMOVE_DIACRITICS (see
                // [tashkeelText]): known words vocalized, unknown words
                // byte-identical, everything else verbatim.
                DrsTextTool.TASHKEEL_TEXT -> tashkeelText(text)
                // DRS v1.6.0: the number-to-words rendering — a clean
                // integer field becomes its Arabic words, anything else
                // stays byte-identical (see [DrsNumberWords]).
                DrsTextTool.NUMBER_WORDS -> DrsNumberWords.arabicWordsOrNull(text) ?: text
                // DRS v1.7.0: the financial tafqit — a clean amount speaks
                // its formal check words, anything else stays byte-identical
                // (see [DrsTafqit]).
                DrsTextTool.TAFQIT -> DrsTafqit.tafqitOrNull(text) ?: text
                // DRS v1.8.0: the date-in-words rendering — a clean date
                // speaks its formal documentary phrase, anything else
                // stays byte-identical (see [DrsDateWords]).
                DrsTextTool.DATE_WORDS -> DrsDateWords.dateWordsOrNull(text) ?: text
                // DRS v1.9.0: the clock-time-in-words rendering — a clean
                // clock time speaks its formal spoken phrase, anything
                // else stays byte-identical (see [DrsTimeWords]).
                DrsTextTool.TIME_WORDS -> DrsTimeWords.timeWordsOrNull(text) ?: text
                // DRS v1.10.0: the fraction-in-words rendering — a clean
                // fraction field speaks its Arabic words, prose stays
                // byte-identical (see [DrsFractionWords]).
                DrsTextTool.FRACTION_WORDS -> DrsFractionWords.fractionWordsOrNull(text) ?: text
                // DRS v1.10.0: the weekday resolution — a clean date
                // resolves to its weekday name, prose stays byte-identical
                // (see [DrsDateWords.weekdayOrNull]).
                DrsTextTool.WEEKDAY -> DrsDateWords.weekdayOrNull(text) ?: text
                // DRS v1.3.0: strip tatweel (kashida) elongation and
                // convert digits between Western and Arabic-Indic forms.
                // Both are per-character, lossless-for-everything-else
                // conversions that never touch letters or punctuation.
                DrsTextTool.REMOVE_TATWEEL -> text.replace(TATWEEL.toString(), "")
                DrsTextTool.TO_ARABIC_DIGITS -> mapChars(text) { ch ->
                    when {
                        ch in '0'..'9' ->
                            (ch.code - '0'.code + ARABIC_INDIC_ZERO.code).toChar()
                        // DRS v1.5.0: Persian/Urdu digits (۰-۹) map to the
                        // Arabic-Indic forms as well so ALL eastern digits
                        // normalize to one system.
                        ch.code in EXTENDED_ARABIC_INDIC_ZERO.code..EXTENDED_ARABIC_INDIC_ZERO.code + 9 ->
                            (ch.code - EXTENDED_ARABIC_INDIC_ZERO.code + ARABIC_INDIC_ZERO.code).toChar()
                        else -> ch
                    }
                }
                DrsTextTool.TO_WESTERN_DIGITS -> mapChars(text) { ch ->
                    when (ch.code) {
                        in ARABIC_INDIC_ZERO.code..ARABIC_INDIC_ZERO.code + 9 ->
                            (ch.code - ARABIC_INDIC_ZERO.code + '0'.code).toChar()
                        in EXTENDED_ARABIC_INDIC_ZERO.code..EXTENDED_ARABIC_INDIC_ZERO.code + 9 ->
                            (ch.code - EXTENDED_ARABIC_INDIC_ZERO.code + '0'.code).toChar()
                        else -> ch
                    }
                }
                // DRS v1.4.0: sort lines in DESCENDING order with the same
                // locale collator (deterministic counterpart of SORT_LINES).
                DrsTextTool.SORT_LINES_DESC -> {
                    val trailingNewline = text.endsWith("\n")
                    val collator = Collator.getInstance(locale)
                    val sorted = text.lines()
                        .let { if (trailingNewline) it.dropLast(1) else it }
                        .sortedWith(compareByDescending(collator) { it })
                        .joinToString("\n")
                    if (trailingNewline) "$sorted\n" else sorted
                }
                // DRS v1.4.0: unify Arabic letter variants that carry no
                // meaning once the text is copied elsewhere — per-character,
                // lossless for everything else (see [normalizeArabicLetters]).
                DrsTextTool.NORMALIZE_ARABIC -> normalizeArabicLetters(text)
                // DRS v1.5.0: invert the case of every cased Latin/Cyrillic/
                // Greek letter; Arabic (and every other script without case
                // pairs) passes through unchanged.
                DrsTextTool.TOGGLE_CASE -> mapChars(text) { ch ->
                    val upper = ch.uppercaseChar()
                    val lower = ch.lowercaseChar()
                    when {
                        // Uppercase letter: its lowercase form differs.
                        ch == upper && upper != lower -> lower
                        // Lowercase letter: its uppercase form differs.
                        ch == lower && upper != lower -> upper
                        // Caseless (Arabic, digits, punctuation): untouched.
                        else -> ch
                    }
                }
                // DRS v1.5.0: map the three Latin sentence punctuation marks
                // to their Arabic counterparts — per-character, everything
                // else untouched.
                DrsTextTool.TO_ARABIC_PUNCTUATION -> mapChars(text) { ch ->
                    when (ch) {
                        ',' -> '\u060C' // ،
                        ';' -> '\u061B' // ؛
                        '?' -> '\u061F' // ؟
                        else -> ch
                    }
                }
                // DRS v1.5.0: strip invisible zero-width formatting marks
                // that break search/copy (ZWSP, ZWJ, LRM/RLM, BOM, Unicode
                // isolates). ZWNJ (نصف المسافة) is intentionally kept.
                DrsTextTool.REMOVE_ZERO_WIDTH -> text.replace(ZERO_WIDTH_CHARS, "")
                // DRS v1.5.0: every tab becomes four real spaces so the
                // text is safe in fields that collapse or misrender tabs.
                DrsTextTool.TABS_TO_SPACES -> text.replace("\t", "    ")
                DrsTextTool.NORMALIZE_PUNCTUATION -> normalizePunctuation(text)
                // DRS v1.5.0: stable sort by line length (ties keep the
                // original relative order); trailing newline preserved.
                DrsTextTool.SORT_LINES_BY_LENGTH -> {
                    val trailingNewline = text.endsWith("\n")
                    val sorted = text.lines()
                        .let { if (trailingNewline) it.dropLast(1) else it }
                        .sortedBy { it.length }
                        .joinToString("\n")
                    if (trailingNewline) "$sorted\n" else sorted
                }
                // DRS v1.5.0: remove repeated words across the whole text
                // (first occurrence wins, all whitespace separators kept
                // verbatim) — see [removeDuplicateWords].
                DrsTextTool.REMOVE_DUPLICATE_WORDS -> removeDuplicateWords(text)
                // DRS v1.5.0: surround the text with parentheses.
                DrsTextTool.WRAP_PARENS -> "(" + text + ")"
                // DRS v1.5.0: one sentence per line — break after sentence
                // enders when horizontal whitespace follows and another
                // non-space character comes after it (idempotent, existing
                // newlines untouched).
                DrsTextTool.SENTENCE_PER_LINE -> text.replace(SENTENCE_BOUNDARY, "$1\n")
                // DRS v1.6.0: the inverse of TABS_TO_SPACES — every run of
                // four consecutive spaces becomes one real tab. The regex
                // scans left-to-right, so longer runs split deterministically
                // (8 spaces -> 2 tabs, 5 spaces -> tab + one space).
                DrsTextTool.SPACES_TO_TABS -> text.replace(" ".repeat(4).toRegex(), "\t")
                // DRS v1.6.0: the inverse of TO_ARABIC_PUNCTUATION — the
                // three Arabic sentence marks map back to their Latin
                // counterparts, per-character, everything else untouched.
                DrsTextTool.TO_WESTERN_PUNCTUATION -> mapChars(text) { ch ->
                    when (ch) {
                        '\u060C' -> ',' // ، -> ,
                        '\u061B' -> ';' // ؛ -> ;
                        '\u061F' -> '?' // ؟ -> ?
                        else -> ch
                    }
                }
                // DRS v1.6.0: strips blank lines at the text's EDGES only —
                // blank separators inside the text are kept exactly as they
                // are (distinct from COLLAPSE_EMPTY_LINES, which keeps one
                // blank separator, and REMOVE_EMPTY_LINES, which removes
                // them all). See [trimBlankEdges].
                DrsTextTool.TRIM_BLANK_EDGES -> trimBlankEdges(text)
                // DRS v1.6.0: reverses the word order of every line — the
                // word-level sibling of REVERSE_LINES. Tokens are joined
                // with a single space; blank lines stay blank and a
                // trailing newline survives as-is.
                DrsTextTool.REVERSE_WORDS -> text.lines().joinToString("\n") { line ->
                    line.split(WHITESPACE_RUN).filter { it.isNotEmpty() }
                        .asReversed()
                        .joinToString(" ")
                }
                // DRS v1.7.0: repairs Arabic text copied from PDFs and some
                // websites, where letters arrive as PRESENTATION FORMS
                // (U+FB50–U+FDFF, U+FE70–U+FEFF) that break search and are
                // invisible to NORMALIZE_ARABIC. NFKC folds them back to the
                // base letters (including the لا ligature); everything else
                // (letters, digits, spacing) passes through untouched.
                DrsTextTool.NORMALIZE_ARABIC_FORMS ->
                    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
                // DRS v1.7.0: turns a comma/semicolon list (Latin or Arabic
                // separators) into one item per line — the natural inverse
                // of JOIN_LINES, and a natural pre-step for NUMBER_LINES.
                DrsTextTool.SPLIT_TO_LINES -> text
                    .split(LIST_SEPARATORS)
                    .joinToString("\n") { it.trim(' ', '\t', '\u00A0') }
                // DRS v1.7.0: joins every line into a single list — Arabic
                // comma (،) for Arabic locales, Latin comma elsewhere. The
                // inverse of SPLIT_TO_LINES; blank lines are skipped.
                DrsTextTool.JOIN_LINES -> text.lines()
                    .filter { it.isNotBlank() }
                    .joinToString(if (locale.language == "ar") "، " else ", ") { it.trim(' ', '\t', '\u00A0') }
                // DRS v1.7.0: removes EVERY horizontal space (hashtag style
                // #وسم_عربي) — newlines are kept so multi-line text stays
                // line-per-tag. The aggressive sibling of TRIM_SPACES.
                DrsTextTool.REMOVE_ALL_SPACES -> text.replace(HORIZONTAL_SPACES, "")
                // DRS v1.7.0: strips emoji/pictographs (and their selectors
                // and joiners) so pasted social text no longer pollutes the
                // counting/sorting/search tools downstream.
                DrsTextTool.STRIP_EMOJI -> text.replace(EMOJI_CHARS, "")
                // DRS v1.7.0: percent-encodes the text as a UTF-8 URL
                // component (Arabic links become shareable everywhere).
                // Spaces become %20, not '+', per RFC 3986 paths.
                DrsTextTool.URL_ENCODE -> java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")
                // DRS v1.7.0: decodes percent-encoded text back to UTF-8;
                // malformed sequences return the input untouched so typing
                // is never destroyed (decode must never throw).
                DrsTextTool.URL_DECODE -> try {
                    java.net.URLDecoder.decode(text, "UTF-8")
                } catch (_: Throwable) {
                    text
                }
                // DRS v1.8.0: one space between digits and letters in BOTH
                // directions (12abc -> 12 abc، ٣س -> ٣ س) — the mixed-script
                // cleanup pasted prices and codes need. The lookarounds never
                // consume characters, so punctuation and newlines survive;
                // the inserted space removes the boundary, so idempotent.
                DrsTextTool.SEPARATE_DIGIT_LETTERS -> text
                    .replace(DIGIT_THEN_LETTER, " ")
                    .replace(LETTER_THEN_DIGIT, " ")
                // DRS v1.8.0: removes EVERY Unicode punctuation mark (Latin
                // and Arabic ،؛؟ alike). Letters, digits, spaces, symbols and
                // currency survive — the search-prep sibling of CLEAN_TEXT.
                DrsTextTool.REMOVE_PUNCTUATION -> text.replace(PUNCTUATION_CHARS, "")
                DrsTextTool.CLEAN_TEXT -> normalizePunctuation(
                    collapseHorizontalSpaces(
                        text.lines()
                            .filter { it.isNotBlank() }
                            .joinToString("\n") { it.trim(' ', '\t', '\u00A0') },
                    ),
                ).trim(' ', '\t', '\u00A0')
                // COUNT never transforms; callers use [countInfo] instead.
                DrsTextTool.COUNT -> text
                // Editor ops need the live cursor; handled by EditorInstance.
                // The mark insertions commit at the cursor the same way —
                // nothing to transform here.
                DrsTextTool.DELETE_LINE, DrsTextTool.DELETE_TO_LINE_START,
                DrsTextTool.DELETE_TO_LINE_END,
                DrsTextTool.INSERT_RLM, DrsTextTool.INSERT_LRM,
                DrsTextTool.INSERT_ZWJ, DrsTextTool.INSERT_ZWNJ,
                -> text
            }
        } catch (_: Throwable) {
            text
        }
    }

    /**
     * Real character/word/line counts for the [DrsTextTool.COUNT] info tool.
     * Words = whitespace-separated tokens; lines = number of lines.
     */
    fun countInfo(text: String): Triple<Int, Int, Int> {
        val chars = text.length
        val words = text.split(Regex("\\s+")).count { it.isNotEmpty() }
        val lines = when {
            text.isEmpty() -> 0
            else -> text.lines().size
        }
        return Triple(chars, words, lines)
    }

    /** Uppercases the first letter of every word (rest of the word kept). */
    private fun titleCase(text: String, locale: Locale): String {
        val sb = StringBuilder(text.length)
        var atWordStart = true
        for (ch in text) {
            if (ch.isLetterOrDigit()) {
                if (atWordStart) {
                    sb.append(ch.uppercase(locale))
                    atWordStart = false
                } else {
                    sb.append(ch)
                }
            } else {
                sb.append(ch)
                if (ch != '-' && ch != '\'' && ch != '\u2019' && ch != '\u00A0') {
                    // Word boundary: anything that is not a word character and
                    // not an intra-word separator (hyphen, apostrophe, NBSP).
                    atWordStart = true
                }
            }
        }
        return sb.toString()
    }

    /**
     * Lowercases the text, then uppercases the first letter after sentence
     * boundaries ('.', '!', '?', '؟', '…', newline) and at the very start.
     */
    private fun sentenceCase(text: String, locale: Locale): String {
        val lower = text.lowercase(locale)
        val sb = StringBuilder(lower.length)
        var capitalizeNext = true
        for (ch in lower) {
            if (capitalizeNext && ch.isLetter()) {
                sb.append(ch.uppercase(locale))
                capitalizeNext = false
            } else {
                sb.append(ch)
                if (ch in ".!?؟…" || ch == '\n') {
                    capitalizeNext = true
                }
            }
        }
        return sb.toString()
    }

    private fun collapseHorizontalSpaces(text: String): String =
        text.replace(HORIZONTAL_SPACES, " ")

    /**
     * DRS v1.5.0: removes repeated whitespace-delimited words across the
     * whole text — the word-level sibling of REMOVE_DUPLICATE_LINES. A
     * manual scanner with a one-run lookbehind keeps the output clean:
     * when a word is dropped, the whitespace run immediately before it is
     * dropped with it (no dangling trailing spaces), while separators
     * around kept words survive verbatim. The first occurrence of each
     * word wins and comparison is exact (case-sensitive) so names and
     * acronyms are never merged away.
     */
    private fun removeDuplicateWords(text: String): String {
        val seen = HashSet<String>()
        val sb = StringBuilder(text.length)
        val pending = StringBuilder()
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch.isWhitespace()) {
                pending.append(ch)
                index++
                continue
            }
            val wordStart = index
            while (index < text.length && !text[index].isWhitespace()) index++
            val word = text.substring(wordStart, index)
            if (seen.add(word)) {
                sb.append(pending)
                sb.append(word)
            }
            pending.clear()
        }
        // Whitespace after the final word is kept as-is.
        sb.append(pending)
        return sb.toString()
    }

    /** DRS v1.3.0: maps every character through [transform] (pure, per-char). */
    private inline fun mapChars(text: String, transform: (Char) -> Char): String {
        var changed = false
        for (ch in text) {
            if (transform(ch) != ch) {
                changed = true
                break
            }
        }
        if (!changed) return text
        val sb = StringBuilder(text.length)
        for (ch in text) sb.append(transform(ch))
        return sb.toString()
    }

    /**
     * DRS v1.6.0: removes blank lines at the START and END of the text
     * only. Blank lines between content are kept exactly as they are —
     * the edge-only sibling of COLLAPSE_EMPTY_LINES and
     * REMOVE_EMPTY_LINES. A trailing newline survives as-is when any
     * non-blank line remains; a text of only blank lines becomes "".
     */
    private fun trimBlankEdges(text: String): String {
        val trailingNewline = text.endsWith("\n")
        val lines = text.lines().let { if (trailingNewline) it.dropLast(1) else it }
        val first = lines.indexOfFirst { it.isNotBlank() }
        if (first == -1) return ""
        val last = lines.indexOfLast { it.isNotBlank() }
        val kept = lines.subList(first, last + 1).joinToString("\n")
        return if (trailingNewline) "$kept\n" else kept
    }

    /**
     * DRS v1.3.0: collapses every run of consecutive blank lines into a
     * single blank line. The line's own content (spacing included) is kept
     * for non-blank lines, and a trailing newline survives as-is.
     */
    private fun collapseEmptyLines(text: String): String {
        val trailingNewline = text.endsWith("\n")
        val lines = text.lines().let { if (trailingNewline) it.dropLast(1) else it }
        val out = StringBuilder(text.length)
        var previousWasBlank = false
        var first = true
        for (line in lines) {
            val blank = line.isBlank()
            if (blank && previousWasBlank) continue
            if (!first) out.append('\n')
            out.append(line)
            previousWasBlank = blank
            first = false
        }
        if (trailingNewline) out.append('\n')
        return out.toString()
    }

    /**
     * DRS v1.4.0 (public) — المُشكِّل الحتمي للنص الكامل (the deterministic
     * whole-text vocalizer). Scans [text] once: every maximal run of
     * Arabic word characters (letters, the nine harakat, tatweel — see
     * [DrsHarakatWordOps.isArabicWordChar]) is a word token; each token
     * is looked up via [DrsWordTashkeel.vocalize] (seed lexicon first,
     * then the installed 3000-word asset — both matched on the STRIPPED
     * form, so partially-marked words still resolve to the canonical
     * form). A known token is replaced by its canonical vocalization; an
     * unknown token is emitted BYTE-IDENTICAL (the honest rule: the tool
     * never invents diacritics, exactly like the word-level action).
     * Every other character — spaces, newlines, punctuation, digits,
     * Latin — passes through verbatim, so punctuation and line structure
     * are preserved exactly. Idempotent by construction: applying the
     * tool to its own output re-derives the same canonical forms.
     */
    private fun tashkeelText(text: String): String {
        return buildString(text.length + 64) {
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (DrsHarakatWordOps.isArabicWordChar(c)) {
                    var j = i + 1
                    while (j < text.length && DrsHarakatWordOps.isArabicWordChar(text[j])) j++
                    val word = text.substring(i, j)
                    append(DrsWordTashkeel.vocalize(word) ?: word)
                    i = j
                } else {
                    append(c)
                    i++
                }
            }
        }
    }

    /**
     * DRS v1.4.0: unifies Arabic letter variants commonly interchanged in
     * search and copy contexts: hamza-carried alefs (أ إ آ ٱ) become a bare
     * alef (ا), ta-marbuta (ة) becomes ha (ه) and alef maqsura (ى) becomes
     * ya (ي). Pure per-character mapping — diacritics, digits, punctuation
     * and every non-Arabic character pass through untouched.
     */
    private fun normalizeArabicLetters(text: String): String = mapChars(text) { ch ->
        when (ch) {
            '\u0623', '\u0625', '\u0622', '\u0671' -> '\u0627' // أ إ آ ٱ -> ا
            '\u0629' -> '\u0647'                              // ة -> ه
            '\u0649' -> '\u064A'                              // ى -> ي
            else -> ch
        }
    }

    /**
     * Fixes spacing around punctuation in both directions:
     *  - removes spaces before punctuation and closing brackets,
     *  - guarantees exactly one space after sentence punctuation when the
     *    next character is a letter ('.' only for Arabic letters so URLs,
     *    file names and decimals are never broken).
     */
    private fun normalizePunctuation(text: String): String {
        var out = text.replace(SPACE_BEFORE_PUNCT, "$1")
        out = out.replace(SPACE_AFTER_PUNCT, "$1 ")
        out = out.replace(DOT_BEFORE_ARABIC, ". ")
        return out
    }
}
