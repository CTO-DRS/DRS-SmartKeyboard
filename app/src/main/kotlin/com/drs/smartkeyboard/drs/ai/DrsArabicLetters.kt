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

    /**
     * DRS v2.19.0 — the glued-particle contract (العقد الخاص المعلن في
     * v2.17.0): the COLLAPSED preposition-article form لِلْ — ل + ال written
     * as لل with the alif dropped by orthographic law (ل + الشمس = للشمس).
     *
     * This is the ONE glued shape the law can fire on at token level with
     * zero dictionary knowledge, because every Arabic token that opens
     * with لل + letter IS the preposition + the article (للناس، للشمس،
     * للدار) — the collapse leaves no other reading. The sun/moon law
     * belongs to the article's lam wherever it stands, glued or bare, so
     * a sun follower after لل takes the same partial-style shadda the
     * bare-article layer applies: للناس ← للنَّاس.
     *
     * The OTHER glued shapes — [particle + ال + sun] like بالش فالش كالش
     * والش — stay silent BY DOCUMENTED PROOF, not by omission: real
     * non-article words share the exact same token shape (والد/والدين is
     * simultaneously و + الدِّين and the noun والدِين; بالطو is a single
     * loanword) — deciding between them needs dictionary knowledge, and
     * dictionary knowledge is the guessing territory the creed forbids.
     * The DRS v2.17.0 deferral «حتى يأتي قرارها بعقد خاص» is hereby
     * resolved: revived where determinism holds (لل), reasoned silence
     * where it cannot (the rest), and the pins in DrsV21900Tests guard
     * both verdicts.
     *
     * Returns the token with U+0651 inserted after the follower when —
     * and only when — [word] opens with the double-lam collapse, its
     * third letter is one of the fourteen sun letters, and the token
     * carries NO mark anywhere. Null otherwise: not a لل token, a moon
     * follower or hamza carrier, already marked, or too short. Idempotent
     * by construction — the output carries a mark, so a second call
     * returns null.
     */
    fun gluedShaddaForm(word: String): String? {
        if (word.length < 3) return null
        if (word[0] != LAM || word[1] != LAM) return null
        val follower = word[2]
        if (isMark(follower) || follower !in SUN) return null
        if (word.any { isMark(it) }) return null
        return word.substring(0, 3) + SHADDA + word.substring(3)
    }

    /**
     * DRS v2.20.0 — the majesty certification (شهادة الجلالة): the CLOSED
     * hand-pinned family of tokens whose ONLY reading is لفظ الجلالة —
     * the name الله preceded by a single functional letter (و العطف،
     * ب والف والك الجر، ف العطف، ت القسم) or the bare jar ل itself:
     *
     *     لله والله بالله فالله كالله تالله ولله وبالله فلله
     *
     * The lam of the majesty name is doubled by the SAME assimilation
     * law the bare article speaks (الله = al + lāh, the lam is a sun
     * letter assimilating into itself) — the bare layer (v2.17.0)
     * already owns الله and اللهم, and the seed lexicon owns لله
     * والله بالله with fuller hand-reviewed harakat («البذرة تفوز»).
     * What nobody owned is this closed prefixed family — six members
     * (تالله فالله كالله ولله وبالله فلله) had no certification at all.
     *
     * WHY this shape is law and not the guessing territory R27 proved
     * silent: the ambiguous shape [حرف + ال + شمسية] has an OPEN stem
     * slot — والد/والدين (والدِين = و + الدِّين) and بالطو share its
     * exact token shape, so separating readings needs dictionary
     * knowledge. The majesty family has NO open slot: its tail is the
     * FIXED لله, and each of the nine members is an exact enumerated
     * string with provably a single reading — واللسان (و + اللسان)
     * does NOT share والله's shape because the stem له is fixed. This
     * is a table like the fourteen sun letters: closed, hand-pinned,
     * each member justified — never a pattern rule over an open class.
     * Every member R27 pinned silent (والد، بالش، بالطو) stays silent —
     * none of them ends in لله.
     *
     * Returns the token with U+0651 inserted after the SECOND lam of
     * the majesty tail (والله ← واللَّه) when — and only when — [word]
     * is exactly one of the nine members and carries NO mark anywhere
     * (a partially-marked token is not the engine's to touch — the
     * same honest rule the bare and glued layers speak). Null
     * otherwise: not a family member, already marked, or too short.
     * Idempotent by construction — the output carries a mark, so a
     * second call returns null.
     */
    fun majestyShaddaForm(word: String): String? {
        if (word.length < 3) return null
        if (word !in MAJESTY) return null
        if (word.any { isMark(it) }) return null
        // every member ends in the fixed tail له — inserting before the
        // final ha lands the shadda exactly after the SECOND lam, the
        // same position the seed lexicon writes (اللَّه = ا ل ل ّ ه):
        return word.substring(0, word.length - 1) + SHADDA + word.substring(word.length - 1)
    }

    /**
     * The nine majesty tokens (لفظ الجلالة with its functional
     * prefixes) — the closed hand-pinned family of
     * [majestyShaddaForm]. EXACT strings, never a pattern: adding a
     * member is a documented legal decision, not a shape match.
     */
    private val MAJESTY: Set<String> = setOf(
        "لله", "والله", "بالله", "فالله", "كالله", "تالله", "ولله", "وبالله", "فلله",
    )
}
