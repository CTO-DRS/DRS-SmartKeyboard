/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

/**
 * DRS v2.0.0 — the deterministic Arabic-words-to-number engine
 * (الكلمات إلى عدد) behind the WORDS_TO_NUMBER text tool: the written
 * words of the words family return to their number — «ثلاثة وعشرون»
 * → 23، «مائة ألف» → 100000 — the exact inverse of [DrsNumberWords]
 * on ONE release lexicon, both grammatical cases, never mixed.
 *
 * The mirror of the release grammar, no model, no dictionary beyond
 * the engine's own words, no guesswork:
 *  - The lexicon is CLOSED: exactly the words [DrsNumberWords] emits —
 *    units, عشرة, the bare teen tail عشر, teens, tens and hundreds in
 *    both cases, the three scale families with their singular, dual
 *    (both cases) and plural forms. Anything else is an honest no-op.
 *  - The written case is LOCKED on the first inflected word (اثنان vs
 *    اثنين، عشرون vs عشرين، مائتان vs مائتين، ألفان vs ألفين) and any
 *    later word of the other case refuses the whole field — the mixed
 *    case is a shape the engine never writes.
 *  - The prefixed waw is ATTACHED and REQUIRED at every join: every
 *    segment after the first, the rest after hundreds, and the tens
 *    after a unit all carry their و on the following word («مائة
 *    وواحد وعشرون»). The lexical detector strips it only there —
 *    «واحد» is bare because «احد» is not a lexicon word, while
 *    «وواحد» inside «مائة وواحد وعشرون» is the join. A bare head at a
 *    join position («ألف واحد»، «مليون ألف») is a shape the engine
 *    never writes and is refused.
 *  - The bare teen tail عشر exists only after its teen head (أحد عشر،
 *    اثنا عشر) — never standalone, never joined. The scale word after
 *    its group is bare by the same proof: آلاف for groups 3..10, the
 *    singular for 11..999, and groups 1 and 2 stand as the scale word
 *    itself (ألف، ألفان، ألفين) with no counter — «اثنان ألف» is
 *    refused.
 *  - Segments parse strictly descending: مليار، مليون، ألف، then the
 *    units group — and every word must be consumed. Twelve digits is
 *    the shared ceiling: 999,999,999,999 exactly.
 *  - Fail-closed everywhere: an unknown word, a leftover word, a bare
 *    head at a join, a mixed case, «سالب» with no body, «سالب صفر» —
 *    every shape the grammar does not name comes back byte-identical.
 */
object DrsWordsToNumber {

    /** The oblique hundreds mirror: only the dual inflects in writing
     *  (مائتان → مائتين), the rest stay neutral — the word itself comes
     *  from [DrsNumberWords.HUNDRED_TWO_GEN], never re-spelled here. */
    private val HUNDREDS_GEN: List<String> =
        DrsNumberWords.HUNDREDS.mapIndexed { index, word ->
            if (index == 2) DrsNumberWords.HUNDRED_TWO_GEN else word
        }

    /** The group-word lexicon: word → value (1..900). Includes both
     *  written cases for every family that inflects — the parse locks
     *  on the first inflected word and never mixes. Built directly on
     *  the render tables of [DrsNumberWords] — one source of truth. */
    private val GROUP_WORDS: Map<String, Long> = buildMap {
        for (i in 1..9) {
            put(DrsNumberWords.UNITS[i], i.toLong())
            put(DrsNumberWords.UNITS_GEN[i], i.toLong())
        }
        put("عشرة", 10L)
        for (i in 2..9) {
            put(DrsNumberWords.TENS[i], (i * 10).toLong())
            put(DrsNumberWords.TENS_GEN[i], (i * 10).toLong())
        }
        for (i in 1..9) {
            put(DrsNumberWords.HUNDREDS[i], (i * 100).toLong())
            put(HUNDREDS_GEN[i], (i * 100).toLong())
        }
    }

    /** The teen heads — one..nine in their teen shapes. اثنا (nominative)
     *  and اثني (oblique) are the written pair of twelve. */
    private val TEEN_HEADS: Map<String, Long> = mapOf(
        "أحد" to 1L, "اثنا" to 2L, "اثني" to 2L,
        "ثلاثة" to 3L, "أربعة" to 4L, "خمسة" to 5L,
        "ستة" to 6L, "سبعة" to 7L, "ثمانية" to 8L, "تسعة" to 9L,
    )

    /** The bare teen tail: exists only after its teen head. */
    private const val TEEN_TAIL = "عشر"

    /** The scale-word families, strictly descending. */
    private data class Scale(
        val one: String,
        val twoNom: String,
        val twoGen: String,
        val many: String,
        val multiplier: Long,
    )

    private val SCALES = listOf(
        Scale("مليار", "ملياران", "مليارين", "مليارات", 1_000_000_000L),
        Scale("مليون", "مليونان", "مليونين", "ملايين", 1_000_000L),
        Scale("ألف", "ألفان", "ألفين", "آلاف", 1_000L),
    )

    /** Every scale word, with its role — for the bare scale match. */
    private val SCALE_ROLES: Map<String, Triple<Int, String, Long>> = buildMap {
        SCALES.forEachIndexed { index, s ->
            put(s.one, Triple(index, "one", s.multiplier))
            put(s.twoNom, Triple(index, "two", s.multiplier))
            put(s.twoGen, Triple(index, "two", s.multiplier))
            put(s.many, Triple(index, "many", s.multiplier))
        }
    }

    /** The nominative-only inflected forms — the case lock. */
    private val NOM_FORMS = setOf(
        "اثنان", "اثنا", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون",
        "مائتان", "ألفان", "مليونان", "ملياران",
    )

    /** The oblique-only inflected forms — the case lock. */
    private val GEN_FORMS = setOf(
        "اثنين", "اثني", "عشرين", "ثلاثين", "أربعين", "خمسين", "ستين", "سبعين", "ثمانين", "تسعين",
        "مائتين", "ألفين", "مليونين", "مليارين",
    )

    private fun isKnown(word: String): Boolean =
        word in GROUP_WORDS || word in TEEN_HEADS || word == TEEN_TAIL || word in SCALE_ROLES

    /**
     * The number behind [text] when it is a clean release-lexicon
     * composition the engine understands, or null when it is an honest
     * no-op. The whole field must parse — every word consumed.
     */
    fun wordsToNumberOrNull(text: String): Long? {
        val tokens = text.trim().split(Regex("\\s+"))
        if (tokens.isEmpty() || tokens[0].isEmpty()) return null
        var lockedCase = -1 // -1 open, 0 nominative, 1 oblique

        fun checkCase(word: String): Boolean {
            val isNom = word in NOM_FORMS
            val isGen = word in GEN_FORMS
            if (!isNom && !isGen) return true
            val c = if (isNom) 0 else 1
            if (lockedCase == -1) lockedCase = c
            return lockedCase == c
        }

        var negative = false
        var i = 0
        if (tokens[0] == "سالب") {
            negative = true
            i = 1
            if (tokens.size == 1) return null
        }
        // Zero stands alone — and never takes the minus.
        if (i == tokens.size - 1 && tokens[i] == "صفر") {
            return if (negative) null else 0L
        }

        /** Exact lexicon hit only — bare positions: token 0, the teen
         *  tail, the scale word after its group. */
        fun resolveExact(pos: Int): String? =
            tokens[pos].takeIf { isKnown(it) }

        /** The attached-و hit only — join positions: every segment head
         *  after the first, the rest after hundreds, the tens after a
         *  unit. The lexical detector: «واحد» is bare («احد» is not a
         *  lexicon word), «وواحد» is the join. */
        fun resolveJoined(pos: Int): String? {
            val tok = tokens[pos]
            if (tok.length > 1 && tok[0] == 'و') {
                val rest = tok.substring(1)
                if (isKnown(rest)) return rest
            }
            return null
        }

        fun resolveHead(pos: Int, joined: Boolean): String? =
            if (joined) resolveJoined(pos) else resolveExact(pos)

        /**
         * The rest of a group after its head word is resolved:
         * عشرة، the teen pair, a unit (with its joined tens), or a tens
         * word. Returns the value to add and the next position, or null
         * when [word] opens no rest shape.
         */
        fun rest(word: String, nextPos: Int): Pair<Long, Int>? {
            if (word == "عشرة") {
                if (!checkCase(word)) return null
                return 10L to nextPos
            }
            TEEN_HEADS[word]?.let { head ->
                if (nextPos < tokens.size && resolveExact(nextPos) == TEEN_TAIL) {
                    if (!checkCase(word)) return null
                    return 10L + head to nextPos + 1
                }
            }
            val u = GROUP_WORDS[word]
            if (u != null && u in 1..9) {
                if (!checkCase(word)) return null
                if (nextPos < tokens.size) {
                    val joined = resolveJoined(nextPos) ?: return u to nextPos
                    val t = GROUP_WORDS[joined]
                    if (t != null && t in 20..99 && t % 10 == 0L) {
                        if (!checkCase(joined)) return null
                        return u + t to nextPos + 1
                    }
                }
                return u to nextPos
            }
            if (u != null && u in 20..99 && u % 10 == 0L) {
                if (!checkCase(word)) return null
                return u to nextPos
            }
            return null
        }

        /**
         * A group of words worth 1..999. [headJoined] pins the documented
         * join: after the first segment the group head MUST carry the
         * attached waw («ومائة ألف»، «ألف وواحد») — a bare head there is
         * a shape the engine never writes.
         */
        fun group(pos: Int, headJoined: Boolean): Pair<Long, Int>? {
            if (pos >= tokens.size) return null
            val head = resolveHead(pos, headJoined) ?: return null
            var v = 0L
            var next = pos + 1
            val hundreds = GROUP_WORDS[head]
            if (hundreds != null && hundreds >= 100L) {
                if (!checkCase(head)) return null
                v = hundreds
                // The rest after hundreds MUST arrive joined; a bare next
                // token may still be the bare scale word — the group just
                // ends as its hundreds and the caller matches the scale.
                if (next < tokens.size) {
                    val joined = resolveJoined(next) ?: return v to next
                    val added = rest(joined, next + 1) ?: return null
                    v += added.first
                    next = added.second
                }
            } else {
                val added = rest(head, next) ?: return null
                v = added.first
                next = added.second
            }
            return if (v > 0) v to next else null
        }

        var total = 0L
        var consumedAny = false
        SCALES.forEachIndexed { scaleIndex, scale ->
            // Standalone singular (group IS the scale word: ألف) or dual
            // (ألفان، ألفين) — joined when it follows a segment.
            if (i < tokens.size) {
                val w = resolveHead(i, consumedAny)
                if (w == null) {
                    if (consumedAny) return null
                } else if (w == scale.one) {
                    total += scale.multiplier
                    i += 1
                    consumedAny = true
                    return@forEachIndexed
                } else if (w == scale.twoNom || w == scale.twoGen) {
                    if (!checkCase(w)) return null
                    total += 2 * scale.multiplier
                    i += 1
                    consumedAny = true
                    return@forEachIndexed
                }
            }
            // The counted group: 3..10 take the plural (آلاف), 11..999 the
            // singular (ألف) — the scale word BARE, never joined; groups
            // 1 and 2 must stand as the scale word itself.
            if (i < tokens.size) {
                val save = i
                val parsed = group(i, headJoined = consumedAny)
                if (parsed != null && parsed.second < tokens.size) {
                    val (g, next) = parsed
                    val role = SCALE_ROLES[tokens[next]]
                    if (role != null && role.first == scaleIndex) {
                        if (g in 3..10 && role.second == "many") {
                            total += g * scale.multiplier
                            i = next + 1
                            consumedAny = true
                            return@forEachIndexed
                        }
                        if (g >= 11 && role.second == "one") {
                            total += g * scale.multiplier
                            i = next + 1
                            consumedAny = true
                            return@forEachIndexed
                        }
                    }
                }
                i = save
            }
        }
        // The units group closes the field — joined when it follows a segment.
        if (i < tokens.size) {
            val parsed = group(i, headJoined = consumedAny) ?: return null
            total += parsed.first
            i = parsed.second
        }
        if (i != tokens.size) return null
        if (total == 0L) return null
        return if (negative) -total else total
    }
}
