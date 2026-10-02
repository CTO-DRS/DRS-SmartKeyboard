/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS v1.1.0 — الذكاء العربي للوحة الحركات الكاملة (the smart harakat
 * BOARD intelligence core).
 *
 * The v1.16.0 harakat panel became a FULL keyboard-style board «مشابهة
 * للوحة الحروف» with three on-device, offline, deterministic algorithms:
 *
 *  1. المستشار السياقي ([DrsHarakatAdvisor]) — reads ONLY the text before
 *     the cursor and ranks the harakat that make sense right now: a tanween
 *     fath not sitting on an alef asks for its alef, a fresh shadda asks
 *     for its haraka, the lam of a fresh definite article asks for its
 *     sukun, a mark under the cursor asks to be replaced, and everything
 *     else falls back to the user's most-used harakat. Every rule is a
 *     documented orthography convention — never a guess, never a network
 *     call, never any text stored.
 *
 *  2. مشكِّل الكلمة ([DrsWordTashkeel]) — a hand-built lexicon of the most
 *     frequent Arabic words with their canonical vocalization, matched on
 *     the STRIPPED form so already-diacritized input still matches. An
 *     unknown word returns null: the UI honestly disables the action
 *     instead of inventing diacritics.
 *
 *  3. جراحة الكلمة ([DrsHarakatWordOps]) — the pure helpers that find the
 *     Arabic word ending at the cursor, count its grapheme clusters
 *     (a letter plus all its marks is ONE cluster) and strip marks.
 *
 * Nothing here touches Android UI or Context, so every contract is covered
 * by JVM unit tests exactly like the rest of the DRS logic layer.
 */

/**
 * جراحة الكلمة — the pure word-surgery helpers of the smart harakat board.
 */
object DrsHarakatWordOps {

    /**
     * True for every character that belongs to an Arabic word BODY:
     * Arabic-block letters (the [SymbolSmartSuggestor] definition — letters
     * only, no digits, no marks), the nine combining harakat, and the
     * tatweel that stretches inside a word.
     */
    fun isArabicWordChar(c: Char): Boolean {
        return SymbolSmartSuggestor.isArabicLetter(c) ||
            DrsHarakat.isCombiningMark(c) ||
            c == DrsHarakat.TATWEEL
    }

    /**
     * The Arabic word ending at the cursor: the trailing run of
     * [isArabicWordChar] characters of the text before the cursor. An empty
     * string means «the cursor is not inside an Arabic word».
     */
    fun currentWordBefore(text: String): String {
        val lastWordChar = text.indexOfLast { !isArabicWordChar(it) }
        val start = if (lastWordChar == -1) 0 else lastWordChar + 1
        if (start >= text.length) return ""
        val word = text.substring(start)
        // A cursor sitting right AFTER a space must not return the empty
        // run that precedes it — substring already handles that; but a
        // cursor INSIDE trailing marks only (e.g. after a lone haraka pasted
        // mid-text) still counts as a word fragment, which is correct for
        // the replace op (it would strip/replace just those marks).
        return word
    }

    /**
     * Removes ALL the nine combining harakat and the tatweel from [text].
     * Arabic-Indic digits, letters and punctuation are untouched.
     */
    fun stripDiacritics(text: String): String {
        return text.filter { !DrsHarakat.isCombiningMark(it) && it != DrsHarakat.TATWEEL }
    }

    /** True when [text] contains at least one combining haraka mark. */
    fun containsDiacritics(text: String): Boolean {
        return text.any { DrsHarakat.isCombiningMark(it) }
    }

    /**
     * The number of grapheme clusters a word occupies — for Arabic a letter
     * plus ALL its marks is one cluster, so the count is simply the number
     * of NON-mark characters (a tatweel stretches its own cluster).
     * Deleting the word cluster-by-cluster therefore peels letter + marks
     * together, matching the ICU delete path.
     */
    fun clusterCount(word: String): Int {
        return word.count { !DrsHarakat.isCombiningMark(it) }
    }
}

/**
 * مشكِّل الكلمة — the lexicon-based word vocalizer. The lexicon maps the
 * STRIPPED form of ~90 of the most frequent Arabic words to their canonical
 * vocalization (standard MSA, pause-friendly: final marks only where the
 * orthography fixes them — the sukun of مِنْ، لَمْ، قَدْ and the like).
 *
 * Matching is on the stripped form, so «الكتاب» typed WITH partial marks
 * still resolves. An unknown word returns null — the UI disables the
 * action rather than inventing diacritics (honest by design).
 */
object DrsWordTashkeel {

    /**
     * The vocalization lexicon: stripped word → canonical vocalized form.
     * Pure data, reviewed against standard MSA orthography.
     */
    val LEXICON: Map<String, String> = buildLexicon()

    private fun buildLexicon(): Map<String, String> = mapOf(
        // ---- حروف الجر والموصولات والأدوات ----
        "من" to "مِنْ",
        "عن" to "عَنْ",
        "في" to "فِي",
        "إلى" to "إِلَى",
        "على" to "عَلَى",
        "مع" to "مَعَ",
        "حتى" to "حَتَّى",
        "إذا" to "إِذَا",
        "قد" to "قَدْ",
        "لقد" to "لَقَدْ",
        "لم" to "لَمْ",
        "لن" to "لَنْ",
        "لما" to "لَمَّا",
        "سوف" to "سَوْفَ",
        "ثم" to "ثُمَّ",
        "أو" to "أَوْ",
        "أم" to "أَمْ",
        "بل" to "بَلْ",
        "كي" to "كَيْ",
        "لكي" to "لِكَيْ",
        "هل" to "هَلْ",
        "يا" to "يَا",
        "نعم" to "نَعَمْ",
        "كما" to "كَمَا",
        "كلما" to "كُلَّمَا",
        "حيث" to "حَيْثُ",
        "حين" to "حِينَ",
        "عندما" to "عَنْدَمَا",
        "منذ" to "مُنْذُ",
        "لا" to "لَا",
        "ما" to "مَا",
        // ---- الضمائر وأسماء الإشارة والموصولات ----
        "هو" to "هُوَ",
        "هي" to "هِيَ",
        "هم" to "هُمْ",
        "أنا" to "أَنَا",
        "نحن" to "نَحْنُ",
        "أنت" to "أَنْتَ",
        "أنتم" to "أَنْتُمْ",
        "هذا" to "هَٰذَا",
        "هذه" to "هَٰذِهِ",
        "ذلك" to "ذَٰلِكَ",
        "تلك" to "تِلْكَ",
        "الذي" to "الَّذِي",
        "التي" to "الَّتِي",
        "الذين" to "الَّذِينَ",
        "اللاتي" to "اللَّاتِي",
        "كل" to "كُلّ",
        "بعض" to "بَعْض",
        "جميع" to "جَمِيع",
        "نفس" to "نَفْس",
        "ذات" to "ذَات",
        "مثل" to "مِثْل",
        // ---- الظروف والمكان والزمان ----
        "بين" to "بَيْنَ",
        "بعد" to "بَعْدَ",
        "قبل" to "قَبْلَ",
        "فوق" to "فَوْقَ",
        "تحت" to "تَحْتَ",
        "أمام" to "أَمَامَ",
        "خلف" to "خَلْفَ",
        "حول" to "حَوْلَ",
        "لدى" to "لَدَى",
        "عند" to "عِنْدَ",
        "دون" to "دُونَ",
        "خلال" to "خِلَالَ",
        "نحو" to "نَحْوَ",
        "سوى" to "سِوَى",
        "الآن" to "الْآن",
        "اليوم" to "الْيَوْم",
        // ---- الأفعال الشائعة ----
        "كان" to "كَانَ",
        "كانت" to "كَانَتْ",
        "كانوا" to "كَانُوا",
        "يكون" to "يَكُونُ",
        "تكون" to "تَكُونُ",
        "قال" to "قَالَ",
        "قالت" to "قَالَتْ",
        "يقول" to "يَقُولُ",
        "ليس" to "لَيْسَ",
        "ليست" to "لَيْسَتْ",
        "صار" to "صَارَ",
        "أصبح" to "أَصْبَحَ",
        "يجب" to "يَجِبُ",
        "يمكن" to "يُمْكِنُ",
        "عمل" to "عَمِلَ",
        "صنع" to "صَنَعَ",
        "أراد" to "أَرَادَ",
        "نظر" to "نَظَرَ",
        "ذهب" to "ذَهَبَ",
        "جاء" to "جَاءَ",
        "بدأ" to "بَدَأَ",
        // ---- عائلة إنَّ وأخواتها ----
        "إن" to "إِنَّ",
        "إنه" to "إِنَّهُ",
        "إنها" to "إِنَّهَا",
        "لأن" to "لِأَنَّ",
        "لكن" to "لَٰكِنَّ",
        "وأن" to "وَأَنَّ",
        "بأنه" to "بِأَنَّهُ",
        "لأنه" to "لِأَنَّهُ",
        // ---- أسماء شائعة (بلا حركة أخيرة) ----
        "الله" to "اللَّه",
        "يوم" to "يَوْم",
        "سنة" to "سَنَة",
        "عام" to "عَام",
        "الناس" to "النَّاس",
        "شيء" to "شَيْء",
        "رسول" to "رَسُول",
        "نبي" to "نَبِيّ",
        "كتاب" to "كِتَاب",
        "علم" to "عِلْم",
    )

    /**
     * The canonical vocalization of [word], or null when the word is not in
     * the lexicon. Matching happens on the stripped form, so input typed
     * with (partial or full) marks still resolves to the canonical form.
     */
    fun vocalize(word: String): String? {
        val stripped = DrsHarakatWordOps.stripDiacritics(word)
        if (stripped.isEmpty()) return null
        return LEXICON[stripped]
    }

    /** True when [word] (stripped) is in the lexicon. */
    fun isKnown(word: String): Boolean = vocalize(word) != null

    /** The lexicon size — surfaced in tests and diagnostics. */
    val size: Int get() = LEXICON.size
}

/**
 * المستشار السياقي — the context-aware harakat advisor of the smart board.
 *
 * Given the text before the cursor (and the user's most-used harakat), it
 * returns an ordered list of [Pick]s — each a commit string (a haraka, or
 * the alef that completes a tanween, or the whole definite article) plus
 * the orthography reason behind it. Empty output means «no contextual rule
 * fires» and the UI falls back to the most-used chips. Pure, offline,
 * deterministic; no text ever leaves the device or gets stored.
 */
object DrsHarakatAdvisor {

    /** Why a pick was recommended — the UI maps these to localized labels. */
    enum class AdviceReason {
        /** Fallback ranking from the user's own most-used harakat. */
        MRU,

        /** A fresh shadda wants its vowel: شدة + فتحة first. */
        AFTER_SHADDA,

        /** The cursor is right after «ال» — the lam takes a sukun. */
        DEFINITE_LAM,

        /** A tanween fath is not sitting on an alef — offer to complete it. */
        TANWEEN_ALEF,

        /** A mark sits right before the cursor — a new mark replaces it. */
        OVER_MARK,
    }

    /** One contextual recommendation: what to commit and why. */
    data class Pick(val commit: String, val reason: AdviceReason)

    /**
     * The ordered contextual picks for the current cursor position. At most
     * [MAX_PICKS] picks are returned; an empty list means no rule fired.
     */
    fun advise(textBeforeCursor: String, mru: List<Char> = emptyList()): List<Pick> {
        val last = textBeforeCursor.lastOrNull() ?: return emptyList()

        // R1 — تنوين الفتح بلا ألف: «كتابً» wants its alef (كتابًا).
        if (last == DrsHarakat.FATHATAN) {
            val prev = textBeforeCursor.getOrNull(textBeforeCursor.length - 2)
            if (prev != null && SymbolSmartSuggestor.isArabicLetter(prev) &&
                !DrsHarakat.isAlefLetter(prev)
            ) {
                return listOf(Pick("اً", AdviceReason.TANWEEN_ALEF))
            }
            return emptyList()
        }

        // R2 — شدة طازجة تطلب حركتها: شدة + فتحة أولًا.
        if (last == DrsHarakat.SHADDA) {
            return listOf(Pick(DrsHarakat.FATHA.toString(), AdviceReason.AFTER_SHADDA))
        }

        // R3 — لام التعريف: «ال» على حدّها تطلب سكون اللام.
        if (textBeforeCursor.endsWith("ال")) {
            val beforeAl = textBeforeCursor.dropLast(2).lastOrNull()
            if (beforeAl == null || !SymbolSmartSuggestor.isArabicLetter(beforeAl)) {
                return listOf(Pick(DrsHarakat.SUKUN.toString(), AdviceReason.DEFINITE_LAM))
            }
        }

        // R4 — حركة تحت المؤشر: الحركة الجديدة تستبدلها (الدمج الذكي).
        if (DrsHarakat.isCombiningMark(last)) {
            val top = mru.firstOrNull() ?: DrsHarakat.FATHA
            return listOf(Pick(top.toString(), AdviceReason.OVER_MARK))
        }

        // R5 — لا قاعدة سياقية: أعد قائمة فارغة وسيقود «الأكثر استخدامًا».
        return emptyList()
    }

    /**
     * The most-used harakat of this user as [Pick]s (reason [AdviceReason.MRU]),
     * capped at [MAX_PICKS] — the smart strip's fallback row.
     */
    fun mruPicks(mru: List<Char>, n: Int = MAX_PICKS): List<Pick> {
        return mru.take(n.coerceAtLeast(0)).map { Pick(it.toString(), AdviceReason.MRU) }
    }

    /** The maximum number of contextual picks the strip shows. */
    const val MAX_PICKS = 3

    /**
     * The honest note for the cursor state — shown as an info chip:
     * an alef (or alef variant) before the cursor can never carry a haraka,
     * so the board says so instead of pretending the insert is useful.
     */
    fun alefNoteFor(textBeforeCursor: String): Boolean {
        val last = textBeforeCursor.lastOrNull() ?: return false
        return DrsHarakat.isAlefLetter(last)
    }
}
