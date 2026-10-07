/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.ai

/**
 * DRS v1.6.0 — the deterministic sun/moon letter classification
 * (حروف الشمسية والقمرية) behind the definite article ال.
 *
 * The linguistic law this encodes: the lam of the definite article is
 * SILENT before one of the fourteen sun letters (the letter doubles —
 * الشَّمس is pronounced ash-shams), and PRONOUNCED before anything else
 * (القمر al-qamar — moon letters, and the hamza carriers likewise: الأمل
 * is pronounced al-amal). This is what a tashkeel renderer, a
 * reading-assistant surface and a pronunciation hint all need — and it
 * is pure table logic: no guessing, no model, no data leaves the device.
 *
 * Classification is EXHAUSTIVE over the 28 base letters and DISJOINT
 * (a base letter is sun or moon, never both, never neither) — the tests
 * pin both properties across the whole alphabet. The ta-marbuta ة and
 * the bare hamza ء are NOT classified: they cannot follow the lam of a
 * definite article in standard orthography, and the engine refuses to
 * invent a class for them. APIs expect RAW (unvocalized) tokens; a
 * token already carrying marks passes through the vocalizer untouched.
 */
object DrsArabicLetters {

    /** The fourteen sun letters (الحروف الشمسية) — lam assimilates into them. */
    val SUN: Set<Char> = setOf(
        'ت', 'ث', 'د', 'ذ', 'ر', 'ز', 'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ل', 'ن',
    )

    /** The fourteen moon letters (الحروف القمرية) — lam stays pronounced. */
    val MOON: Set<Char> = setOf(
        'ا', 'ب', 'ج', 'ح', 'خ', 'ع', 'غ', 'ف', 'ق', 'ك', 'م', 'ه', 'و', 'ي',
    )

    /** The lam of the definite article. */
    private const val LAM = 'ل'

    /** The bare alif that opens the definite article. */
    private const val ALIF = 'ا'

    /** The shadda (U+0651) the sun letter of a definite-article token demands. */
    private const val SHADDA = '\u0651'

    /** Hamza-carrying alif forms that still OPEN a definite-article token. */
    private val ALIF_OPENERS = setOf('ا', 'أ', 'إ', 'آ')

    /** Harakat / superscript-alef / tatweel — a marked letter is already vocalized. */
    private fun isMark(c: Char): Boolean =
        c in '\u064B'..'\u065F' || c == '\u0670' || c == '\u0640'

    /** How the definite article's lam behaves in [word]. */
    enum class LamAssimilation {
        /** ال before a sun letter: the lam is silent and the sun letter doubles (الشمس). */
        ASSIMILATED,

        /**
         * ال before a non-sun letter: the lam stays pronounced — the moon
         * letters (القمر) and the hamza carriers alike (الأمل).
         */
        DISTINCT,

        /** Not a definite-article token (no ال opener) — honest no-op. */
        NOT_DEFINITE,
    }

    /** True when [c] is one of the fourteen sun letters. */
    fun isSun(c: Char): Boolean = c in SUN

    /** True when [c] is one of the fourteen moon letters. */
    fun isMoon(c: Char): Boolean = c in MOON

    /**
     * Classifies the definite article's behavior in [word]. The token must
     * open with alif (bare or hamza-carrying) followed by lam and carry at
     * least one more letter; anything else — including the bare article ال
     * itself — is honestly [LamAssimilation.NOT_DEFINITE] because there is
     * no letter after the article to classify. The article's law is
     * binary: SUN assimilates, everything else stays pronounced.
     */
    fun lamAssimilation(word: String): LamAssimilation {
        if (word.length < 3) return LamAssimilation.NOT_DEFINITE
        if (word[0] !in ALIF_OPENERS || word[1] != LAM) return LamAssimilation.NOT_DEFINITE
        return if (word[2] in SUN) LamAssimilation.ASSIMILATED else LamAssimilation.DISTINCT
    }

    /**
     * The vocalized form of a definite-article token. The SPELLING of the
     * letters never changes — assimilation is a pronunciation fact — what
     * changes is only the marks a vocalizer would place:
     *
     *  - sun follower: Sukun on the lam, Shadda on the sun letter (الْشَّمس)
     *  - anything else: Sukun on the lam (الْقمر, الْأمل)
     *
     * DRS v2.17.0 (honest correction): the moon branch previously emitted
     * a FATHA on the lam (الَقمر) — a form no standard orthography writes.
     * The lam of the definite article is SAKINAH in both branches (that is
     * exactly what the live definite-lam advisor rule has always offered);
     * the two branches differ only in the shadda the sun letter demands.
     * The old pins were corrected alongside, and the CHANGELOG carries the
     * confession.
     *
     * Words that are not definite-article tokens — and tokens ALREADY
     * carrying a mark right after the article — pass through
     * byte-identical: the doctrine of never inventing a second layer of
     * marks holds here too.
     */
    fun vocalizedArticle(word: String): String {
        if (word.length < 3) return word
        if (word[0] != ALIF || word[1] != LAM) return word
        val third = word[2]
        if (isMark(third)) return word
        return if (third in SUN) {
            "$ALIF$LAM\u0652$third\u0651" + word.substring(3)
        } else {
            "$ALIF$LAM\u0652" + word.substring(2)
        }
    }

    /**
     * DRS v2.17.0 — the partial-style assimilation mark for a definite-
     * article sun token, the form the whole-text vocalizer applies to
     * tokens its lexicons do not know.
     *
     * The design decision behind this SEPARATE function: the seed and
     * asset lexicons vocalize their known sun words in the PARTIAL style
     * (النَّاس، الَّذِي — shadda on the sun letter, lam left bare) and the
     * vocalizer must not speak two styles in one text. So the law layer
     * adds exactly what the law demands and nothing the style debate
     * owns: the sun letter's shadda. The lam's sukun stays the advisor's
     * explicit suggestion ([DrsHarakatAdvisor] definite-lam rule) — a
     * user choice, never auto-applied.
     *
     * Returns the token with U+0651 inserted after the sun letter when —
     * and only when — [word] is a definite-article token whose third
     * letter is one of the fourteen sun letters and the token carries NO
     * mark anywhere (a partially-marked word is not the engine's to
     * touch). Null otherwise: not an article token, a moon follower,
     * already marked, or too short. Idempotent by construction — the
     * output carries a mark, so a second call returns null.
     */
    fun shaddaForm(word: String): String? {
        if (lamAssimilation(word) != LamAssimilation.ASSIMILATED) return null
        if (word.any { isMark(it) }) return null
        return word.substring(0, 3) + SHADDA + word.substring(3)
    }
}
