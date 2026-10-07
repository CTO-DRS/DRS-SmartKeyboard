/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp.latin

import android.content.Context
import android.os.SystemClock
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.appContext
import com.drs.smartkeyboard.externalDictStore
import com.drs.smartkeyboard.ime.core.Subtype
import com.drs.smartkeyboard.ime.dictionary.DictionaryManager
import com.drs.smartkeyboard.ime.dictionary.UserDictionaryEntry
import com.drs.smartkeyboard.ime.editor.EditorContent
import com.drs.smartkeyboard.ime.nlp.AutocorrectDecider
import com.drs.smartkeyboard.ime.nlp.DrsExternalDictMerger
import com.drs.smartkeyboard.ime.nlp.SpellingProvider
import com.drs.smartkeyboard.ime.nlp.SpellingResult
import com.drs.smartkeyboard.ime.nlp.SuggestionCandidate
import com.drs.smartkeyboard.ime.nlp.SuggestionProvider
import com.drs.smartkeyboard.ime.nlp.WordSuggestionCandidate
import com.drs.smartkeyboard.drs.ai.DrsAiPowerManager
import com.drs.smartkeyboard.drs.ai.DrsArabicCorrector
import com.drs.smartkeyboard.drs.ai.DrsArabicMorphology
import com.drs.smartkeyboard.drs.ai.DrsContextRanker
import com.drs.smartkeyboard.drs.ai.DrsLearningEngine
import com.drs.smartkeyboard.drs.ai.DrsMorphologyRanker
import com.drs.smartkeyboard.drs.ai.DrsTrigramChains
import com.drs.smartkeyboard.drs.ai.DrsNextWordPredictor
import com.drs.smartkeyboard.drs.ai.DrsSmartReplies
import com.drs.smartkeyboard.drs.ai.LatinNormBridge
import com.drs.smartkeyboard.lib.devtools.flogDebug
import com.drs.smartkeyboard.lib.devtools.flogError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.drs.lib.android.readText
import org.drs.lib.kotlin.guardedByLock
import java.util.Locale
import java.util.PriorityQueue

private const val CORRECTION_MIN_LENGTH = 3
private const val CORRECTION_MAX_LENGTH = 12
private const val CORRECTION_INDEX_LIMIT = 12000

// DRS personal word learning constants
private const val USER_WORDS_TTL_MS = 15_000L
private const val USER_WORDS_MAX = 4000
private const val LEARNED_INSERT_FREQ_FROM_SUGGESTION = 160
private const val LEARNED_INSERT_FREQ_TYPED = 120

/** DRS v1.20.0: frequency drained from a personally learned word per rejected (reverted) suggestion. */
private const val REVERT_DEMOTE_STEP = 64

/** DRS v1.20.0: characters after which the next word starts a new sentence (Latin + Arabic marks). */
private val SENTENCE_TERMINATORS = charArrayOf('.', '!', '?', '…', '؟', '۔')

/** DRS Phase 2 (task 13): power-tier probe interval. */
private const val POWER_CAPS_TTL_MS = 30_000L

/** DRS Phase 2 (task 9): learning flush cadence (every N learned units). */
private const val LEARNING_FLUSH_EVERY = 16

/**
 * DRS Phase 2 (task 11): extracts the conversation tail — the fragment
 * the smart-reply engine classifies (the CURRENT incoming message, not
 * the whole history).
 *
 * Trailing terminators BELONG to the current message («كيف حالك؟» must
 * classify as the question it is), so they are peeled first; the tail is
 * then everything after the last remaining boundary. Bounded to the last
 * 300 chars to cap the regex cost.
 */
internal fun conversationTail(beforeCursor: String): String {
    var trimmed = beforeCursor.trimEnd()
    if (trimmed.isEmpty()) return ""
    val terminators = charArrayOf('\n', '.', '!', '?', '؟', '…', '۔')
    while (trimmed.isNotEmpty() && trimmed.last() in terminators) {
        trimmed = trimmed.dropLast(1).trimEnd()
    }
    if (trimmed.isEmpty()) return ""
    var cutIdx = -1
    for (t in terminators) {
        val idx = trimmed.lastIndexOf(t)
        if (idx > cutIdx) cutIdx = idx
    }
    val tail = if (cutIdx >= 0 && cutIdx < trimmed.length - 1) {
        trimmed.substring(cutIdx + 1).trim()
    } else {
        trimmed
    }
    return tail.takeLast(300)
}

/** All single-character deletion variants of [word] (deduplicated). */
private fun delete1Variants(word: String): Set<String> {
    if (word.length < 2) return emptySet()
    val out = HashSet<String>(word.length * 2)
    val sb = StringBuilder(word)
    for (i in word.indices) {
        val ch = sb[i]
        sb.deleteCharAt(i)
        out.add(sb.toString())
        sb.insert(i, ch)
    }
    return out
}

/**
 * DRS p6 (A1): true when [c] can appear INSIDE a word — letters of any
 * script, the Arabic harakat (U+064B..U+065F), the superscript (dagger)
 * alef (U+0670) and apostrophes (U+0027 straight, U+2019 typographic).
 * Everything else (spaces, punctuation, digits, symbols) terminates a word.
 *
 * The old prev-word backward scan used Character.isLetter() only, so it
 * stopped at the FIRST haraka — next-word prediction was DEAD after any
 * vocalized Arabic word — and it split "don't" into "t".
 */
internal fun isWordInternalChar(c: Char): Boolean =
    c.isLetter() ||
        c in '\u064B'..'\u065F' || // harakat (fathatan .. sukun)
        c == '\u0670' ||           // superscript alef (also Lm, kept defensive)
        c == '\'' ||               // ASCII apostrophe
        c == '\u2019'              // right single quotation mark

/**
 * DRS p6 (A2): true when [a] and [b] differ by at most ONE edit — a single
 * substitution, insertion, deletion, OR a single transposition of two
 * adjacent characters ("thier" -> "their").
 *
 * The correction index already collected transposition neighbors (its
 * delete-1 keys collide for swapped pairs), but the old filter rejected
 * them with the transposition-unaware check, so "thier" could never reach
 * "their". Top-level + internal so the JVM contract tests can exercise it
 * without a Context.
 */
internal fun isEditDistanceAtMostOneWithTransposition(a: String, b: String): Boolean {
    val la = a.length
    val lb = b.length
    if (kotlin.math.abs(la - lb) > 1) return false
    var i = 0
    var j = 0
    var consumedEdit = false
    while (i < la && j < lb) {
        if (a[i] == b[j]) {
            i++
            j++
            continue
        }
        if (consumedEdit) return false
        // Adjacent transposition counts as THE single edit; whatever
        // follows must match exactly (no second edit after the swap).
        if (i + 1 < la && j + 1 < lb && a[i] == b[j + 1] && a[i + 1] == b[j]) {
            i += 2
            j += 2
            consumedEdit = true
            while (i < la && j < lb) {
                if (a[i] != b[j]) return false
                i++
                j++
            }
            return i == la && j == lb
        }
        consumedEdit = true
        if (la > lb) {
            i++
        } else if (lb > la) {
            j++
        } else {
            i++
            j++
        }
    }
    return true
}

/**
 * DRS language provider delivering real dictionary-based word suggestions, next-word
 * prediction and spelling support.
 *
 * Dictionaries are language-aware assets stored under `ime/dict/`:
 *  - `ar.json`   : 50k-word Arabic frequency dictionary (with smart normalization)
 *  - `fr/de/es/it/pt/tr/ru/fa.json` : DRS v1.18.0 curated high-frequency
 *    dictionaries per language (rank-based score curve) — Latin-script
 *    languages and Persian no longer fall back to ENGLISH suggestions
 *  - `data.json` : generic Latin dictionary (English, also the honest
 *    fallback for languages without a bundled dictionary)
 *  - `ar_bigrams.json` / `en_bigrams.json` : next-word prediction tables built from
 *    real corpus co-occurrence counts (OpenSubtitles). Head words are stored in their
 *    normalized form; the suggested next words keep their original (correct) spelling.
 *
 * Arabic matching is performed on a normalized form (hamza variants unified, ta-marbuta,
 * alif maqsura, diacritics and tatweel stripped), so typing "مدرسه" still surfaces the
 * correct spelling "مدرسة" as a tap-to-commit suggestion. DRS v1.18.0: Latin input is
 * matched through the same pipeline with accent folding, so "eleve" surfaces "élève".
 *
 * When the composing region is empty (right after a space/commit), the provider predicts
 * the NEXT word from the preceding word using the bigram table.
 */
class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.drs.nlp.providers.latin"

        private const val MAX_WORD_LENGTH = 32

        private const val ARABIC_LANGUAGE = "ar"

        /** Prefix of the Arabic unicode block used for script detection. */
        private const val ARABIC_SCRIPT_START = '\u0600'
        private const val ARABIC_SCRIPT_END = '\u06FF'
    }

    private val appContext by context.appContext()
    private val prefs by DrsPreferenceStore

    // DRS v1.20.0: DictEntry/DictIndex moved to file top-level as `internal` so the
    // prefix-ranking contracts (findByPrefix / findTopByPrefix) are JVM-testable.

    private val json = Json { ignoreUnknownKeys = true }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val bigramDataSerializer =
        MapSerializer(String.serializer(), MapSerializer(String.serializer(), Int.serializer()))

    /** Language code -> loaded dictionary index. */
    private val dictCache = guardedByLock { mutableMapOf<String, DictIndex>() }

    /** Language code -> next-word table (normalized head -> next words sorted by score desc). */
    private val bigramCache = guardedByLock { mutableMapOf<String, Map<String, List<Pair<String, Int>>>>() }

    /** v2.13.0 — language code -> static trigram-chain table (drained lazily like the bigrams). */
    private val chainsCache = guardedByLock { mutableMapOf<String, DrsTrigramChains.Table>() }

    /** DRS: cached personal user dictionary entries with a short TTL. */
    private class UserDataCacheState {
        var loadedAtElapsed: Long = Long.MIN_VALUE
        var entries: List<UserDictionaryEntry> = emptyList()

        /**
         * DRS (r0-D perf): PRE-NORMALIZED (normWord, entry) pairs of [entries],
         * computed once at cache-load time. The per-keystroke suggest()/spell()
         * paths used to re-run the full normalization pipeline over every
         * personal entry on every call; now they only walk this list.
         */
        var normalizedEntries: List<Pair<String, UserDictionaryEntry>> = emptyList()
    }

    /** Immutable snapshot handed out by [userDataFor] (lists are never mutated). */
    private class UserDataCacheSnapshot(
        val entries: List<UserDictionaryEntry>,
        val normalizedEntries: List<Pair<String, UserDictionaryEntry>>,
    )

    private val userDataCache = guardedByLock { UserDataCacheState() }

    /** DRS: privacy flag of the most recent suggest() call, used to gate learning. */
    @Volatile
    private var lastSuggestWasPrivate = false

    override val providerId = ProviderId

    override suspend fun create() {
        // One-time provider initialization; dictionaries are loaded per language in preload().
        // DRS v2.12.0: ONE normalization truth — the LatinNormBridge default has
        // the same Arabic semantics, but the hard-correction matching now runs
        // through the SAME tested pipeline the dictionary and typed prefix use.
        LatinNormBridge.normalize = LatinWordNormalize::normalize
        // DRS Phase 2 (roadmap task 9): the personal learning tables load
        // once here — lazy from the disk's perspective (a missing file is
        // an empty table) and fully OFF the keystroke hot path.
        withContext(Dispatchers.IO) {
            runCatching { DrsLearningEngine.load(learningFile()) }
        }
    }

    /** DRS Phase 2: personal learning persistence (noBackupFilesDir, capped JSON). */
    private fun learningFile(): java.io.File {
        val dir = appContext.noBackupFilesDir
        return java.io.File(dir, "drs_learning_v1.json")
    }

    /**
     * DRS Phase 2 (roadmap task 13): the power tier cache — re-probed every
     * 30 s so flipping Battery Saver mid-session downgrades (and re-upgrades)
     * the AI stack without any restart. Any probe failure degrades to
     * STANDARD, the historical behavior.
     */
    private var powerCapsCache: Pair<Long, DrsAiPowerManager.Caps>? = null
    private fun powerCaps(): DrsAiPowerManager.Caps {
        val now = SystemClock.elapsedRealtime()
        powerCapsCache?.let { (at, caps) ->
            if (now - at < POWER_CAPS_TTL_MS) return caps
        }
        val caps = runCatching { DrsAiPowerManager.capsFor(appContext) }
            .getOrDefault(DrsAiPowerManager.Caps(
                tier = DrsAiPowerManager.Tier.STANDARD,
                bigramsEnabled = true,
                smartRepliesEnabled = true,
                learningEnabled = true,
                maxCorrectionCost = 12,
                lengthBand = 4,
                vocalizationEnabled = true,
            ))
        powerCapsCache = now to caps
        return caps
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        // Preload the dictionary for the active primary language (and secondaries lazily on demand).
        val languages = listOf(subtype.primaryLocale.language) +
            subtype.secondaryLocales.map { it.language }
        val lang = languages.firstOrNull() ?: return@withContext
        if (dictCache.withLock { it.containsKey(lang) }) return@withContext
        runCatching { loadDict(lang) }
            .onSuccess { index -> dictCache.withLock { it[lang] = index } }
            .onFailure { e -> flogError { "Failed to load dictionary for '$lang': ${e}" } }
    }

    private suspend fun loadDict(lang: String): DictIndex {
        val asset = dictAssetFor(lang)
        val rawData = appContext.assets.readText(asset)
        val data = json.decodeFromString(wordDataSerializer, rawData)
        // DRS v2.8.0 «قاموسك من ملفك»: user-imported external dictionaries for
        // this language extend the bundled one (max frequency wins on dupes).
        // The store lives in the same process — the settings screen drops this
        // cache entry via NlpManager.invalidateDictCaches right after an
        // import/remove, so the next suggest() re-loads with the new words.
        val external = appContext.externalDictStore().value.wordsFor(lang)
        val merged = DrsExternalDictMerger.merge(data, external)
        val entries = merged
            .map { (word, freq) -> DictEntry(normalize(word), word, freq) }
            .sortedBy { it.norm }
        return DictIndex(entries)
    }

    /**
     * DRS v2.8.0: external-dictionary import/remove hook — drops the cached
     * dictionary index for [lang] so the next suggestion pass rebuilds it with
     * the current external words. Cheap, idempotent, lock-guarded.
     */
    suspend fun invalidateDict(lang: String) {
        dictCache.withLock { it.remove(lang) }
    }

    // DRS v1.18.0: bundled per-language dictionaries live in the pure
    // LatinWordNormalize object (tested): every Latin-script language below
    // gets its own frequency dictionary instead of silently receiving
    // ENGLISH suggestions (the old generic data.json fallback); unknown
    // languages still degrade to the English fallback honestly.
    private fun dictAssetFor(lang: String): String = LatinWordNormalize.dictAssetFor(lang)

    private fun bigramAssetFor(lang: String): String? = when (lang) {
        ARABIC_LANGUAGE -> "ime/dict/ar_bigrams.json"
        "en" -> "ime/dict/en_bigrams.json"
        else -> null
    }

    /**
     * Loads the next-word table for the given language lazily. A missing or invalid asset
     * degrades gracefully to an empty table (feature silently off, never throws).
     */
    private suspend fun bigramsFor(subtype: Subtype): Map<String, List<Pair<String, Int>>> {
        val languages = listOf(subtype.primaryLocale.language) +
            subtype.secondaryLocales.map { it.language }
        val lang = languages.firstOrNull() ?: return emptyMap()
        bigramCache.withLock { it[lang] }?.let { return it }
        // DRS v1.18.0: bigram tables exist for Arabic and English only —
        // the other bundled languages degrade to an empty next-word table
        // (prefix suggestions still work) instead of a dishonest lookup in
        // the English table that can never match.
        val asset = bigramAssetFor(lang) ?: return emptyMap()
        val table = runCatching {
            val rawData = appContext.assets.readText(asset)
            val data = json.decodeFromString(bigramDataSerializer, rawData)
            data.mapValues { (_, nexts) ->
                nexts.entries.sortedByDescending { it.value }.map { it.key to it.value }
            }
        }.getOrElse { e ->
            flogError { "Failed to load bigrams for '$lang': ${e}" }
            emptyMap()
        }
        bigramCache.withLock { it[lang] = table }
        return table
    }

    /** v2.13.0 «السياق الأعمق» — the curated chain asset exists for Arabic only today. */
    private fun chainsAssetFor(lang: String): String? = when (lang) {
        ARABIC_LANGUAGE -> "drs/trigram_chains.txt"
        else -> null
    }

    /**
     * v2.13.0 — loads the static trigram-chain table for the given language
     * lazily, mirroring [bigramsFor] exactly: a missing or invalid asset
     * degrades to the shared empty table (the trigram layer is an honest
     * no-op there — never a dishonest lookup in a table that cannot match).
     * The parse uses the SAME normalizer the bigram keys use ([normalize])
     * — one normalization serves both layers, per the chains contract.
     */
    private suspend fun chainsFor(subtype: Subtype): DrsTrigramChains.Table {
        val languages = listOf(subtype.primaryLocale.language) +
            subtype.secondaryLocales.map { it.language }
        val lang = languages.firstOrNull() ?: return DrsTrigramChains.EMPTY
        chainsCache.withLock { it[lang] }?.let { return it }
        val asset = chainsAssetFor(lang) ?: return DrsTrigramChains.EMPTY
        val table = runCatching {
            val lines = appContext.assets.readText(asset).lines()
            DrsTrigramChains.parse(lines, ::normalize).table
        }.getOrElse { e ->
            flogError { "Failed to load trigram chains for '$lang': ${e}" }
            DrsTrigramChains.EMPTY
        }
        chainsCache.withLock { it[lang] = table }
        return table
    }

    private suspend fun dictFor(subtype: Subtype): DictIndex? {
        val languages = listOf(subtype.primaryLocale.language) +
            subtype.secondaryLocales.map { it.language }
        for (lang in languages) {
            dictCache.withLock { it[lang] }?.let { return it }
        }
        val lang = languages.firstOrNull() ?: return null
        return runCatching { loadDict(lang) }
            .getOrNull()
            ?.also { index -> dictCache.withLock { it[lang] = index } }
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        // DRS v1.19.0: REAL typo marking, governed by the pure SpellingDecider.
        // Until v1.18.0 this function unconditionally returned validWord(), so the
        // spell-checker service was a dead channel: fully wired sessions, ~180
        // advertised subtypes, and never a single red underline. The gates are
        // deliberately conservative — only rich dictionaries (ar/en, >=10k entries)
        // judge, plain single-script words only, all-caps acronyms and mid-sentence
        // capitalized proper nouns excluded, and a word is only flagged when a
        // plausible edit-distance-1 correction actually exists. Users can switch
        // the whole channel off via prefs.spelling.typoFlaggingEnabled.
        if (!prefs.spelling.typoFlaggingEnabled.get()) return SpellingResult.validWord()
        val raw = word.trim()
        // DRS p6 (A5): capitalized-fallback when there is NO context. The
        // spell channel often runs with empty precedingWords (no sentence
        // context available); a capitalized word may then be a sentence
        // start OR a proper noun — we cannot tell, so the conservative
        // proper-noun protection applies and the word is never flagged.
        val midSentence = precedingWords.isNotEmpty() || raw.firstOrNull()?.isUpperCase() == true
        if (!SpellingDecider.isSpellableWord(raw, midSentence = midSentence)) {
            return SpellingResult.validWord()
        }
        val dict = dictFor(subtype) ?: return SpellingResult.validWord()
        val norm = normalize(raw)
        val exactInDict = dict.words.containsKey(raw)
        val normInDict = norm.isNotEmpty() && norm != raw &&
            dict.findByPrefix(norm, limit = 1).firstOrNull()?.norm == norm
        if (exactInDict || normInDict) return SpellingResult.validWord()
        // DRS (r0-D perf): walk the PRE-NORMALIZED user words (no per-call
        // normalize() over the whole personal dictionary).
        val inUserWords = userDataFor(subtype).normalizedEntries.any { (normWord, entry) ->
            entry.word == raw || normWord == norm
        }
        if (inUserWords) return SpellingResult.validWord()
        val corrections = dict.corrections(norm, maxSuggestionCount.coerceIn(1, 5))
        if (!SpellingDecider.shouldFlagTypo(
                wordInDict = false,
                wordInUserWords = false,
                dictEntries = dict.entries.size,
                correctionCount = corrections.size,
            )
        ) {
            return SpellingResult.validWord()
        }
        return SpellingResult.typo(corrections.map { it.word }.toTypedArray())
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val caps = powerCaps()
        val raw = content.composingText.toString().trim()
        lastSuggestWasPrivate = isPrivateSession
        if (raw.isEmpty()) {
            // Nothing is being composed (right after a space, punctuation or commit):
            // predict the next word from the word before the cursor. Honors the
            // dedicated next-word toggle so users can keep prefix-only suggestions.
            if (prefs.suggestion.nextWordEnabled.get()) {
                // DRS Phase 2 (task 13): the bigram tables are the first thing
                // a LIGHT tier drops — old devices skip straight past them.
                val nexts = if (caps.bigramsEnabled) {
                    nextWordCandidates(subtype, content, maxCandidateCount)
                } else {
                    emptyList()
                }
                if (nexts.isNotEmpty()) return nexts
            }
            // DRS Phase 2 (task 11): smart replies — when nothing is being
            // composed AND the bigrams had nothing, the tail of the text
            // before the cursor is classified locally into a reply intent
            // (greeting/thanks/confirmation/…). Tapping commits the chip
            // through the ordinary candidate path; no new surface, no
            // network, and a wrong classification just shows chips the
            // user can ignore.
            if (caps.smartRepliesEnabled) {
                val tail = conversationTail(content.textBeforeSelection.toString())
                return DrsSmartReplies.repliesFor(tail, maxCandidateCount.coerceAtMost(3))
                    .map { reply ->
                        WordSuggestionCandidate(
                            text = reply,
                            confidence = 0.55,
                            isEligibleForUserRemoval = false,
                            sourceProvider = this,
                        )
                    }
            }
            return emptyList()
        }
        if (raw.length > MAX_WORD_LENGTH) return emptyList()
        if (!raw[0].isLetter() || raw.any { it.isDigit() }) return emptyList()

        val index = dictFor(subtype) ?: return emptyList()
        val isArabic = raw.any { it in ARABIC_SCRIPT_START..ARABIC_SCRIPT_END }
        // DRS v1.18.0: normalize the typed prefix through the SAME pipeline
        // the dictionary was pre-normalized with (Arabic unification + Latin
        // accent folding), so accent-less input matches accented words.
        val prefix = normalize(raw)
        if (prefix.isEmpty()) return emptyList()

        fun candidates(list: List<DictEntry>) = list.map { entry ->
            WordSuggestionCandidate(
                text = displayCase(entry.word, raw, isArabic, subtype.primaryLocale.base),
                confidence = entry.freq / 255.0,
                sourceProvider = this,
            )
        }

        // Personally learned words matching the typed prefix always come first.
        val userMatches = userWordMatchesFor(subtype, prefix, isArabic, raw, maxCandidateCount)

        // Gather a wider slice next so we can rank the best matches by frequency.
        // DRS v1.20.0: findTopByPrefix ranks the WHOLE matching range by frequency
        // (bounded heap) instead of truncating lexicographically first — productive
        // prefixes no longer lose their most frequent words to an arbitrary cut.
        val dictMatches = index.findTopByPrefix(prefix, limit = maxCandidateCount * 6)
            .filter { it.word != raw }
            .sortedByDescending { it.freq }

        val merged = buildList {
            addAll(userMatches.map { (word, freq) ->
                WordSuggestionCandidate(
                    text = displayCase(word, raw, isArabic, subtype.primaryLocale.base),
                    confidence = freq.coerceAtLeast(150) / 255.0,
                    sourceProvider = this@LatinLanguageProvider,
                )
            })
            addAll(candidates(dictMatches))
        }.distinctBy { it.text }.take(maxCandidateCount)
        // DRS M1.2 — the morphology ranker reorders the Arabic candidate
        // list by the stem family of the word before the cursor: the lemma
        // itself first, its stem family next, same-root words after — all
        // with a stable sort that never scrambles the frequency order.
        // DRS v2.12.0 — both exits pass through the KNOWN-BUT-WRONG
        // composition head (a universal misspelling that lives in the
        // dictionary, e.g. مسئول، لاكن، اللذي، هاذا، شئ — its correction
        // leads the strip, suggestion-only, never auto-committed).
        fun withUniversalHead(list: List<SuggestionCandidate>): List<SuggestionCandidate> {
            if (!isArabic || list.isEmpty()) return list
            val head = runCatching {
                DrsArabicCorrector.composedSuggestionFor(
                    raw, list.map { it.text.toString() },
                ) { normalize(it) }
            }.getOrNull() ?: return list
            return buildList {
                add(
                    WordSuggestionCandidate(
                        text = head,
                        confidence = 0.95,
                        sourceProvider = this@LatinLanguageProvider,
                    ),
                )
                addAll(list.take(maxCandidateCount - 1))
            }
        }
        if (isArabic && merged.size > 1) {
            val prev = lastWordBefore(content.textBeforeSelection)
            if (prev != null) {
                val reordered = DrsMorphologyRanker.rerank(prev, merged.map { it.text.toString() })
                val byText = merged.associateBy { it.text.toString() }
                val reranked = reordered.mapNotNull { byText[it] }
                if (reranked.size == merged.size) return withUniversalHead(reranked)
            }
        }
        if (merged.isNotEmpty()) return withUniversalHead(merged)

        // No word starts with what was typed: offer the nearest known spellings
        // as "did you mean?" candidates, ranked by frequency.
        // DRS Phase 2 (tasks 8+10): the correction branch is now FUSED —
        // universally-wrong Arabic phrases first, then the quantized weighted
        // Damerau-Levenshtein search (confusion-aware costs: the hamza family,
        // ta-marbuta/hah and alef maqsura/yeh are near-free, Arabic/Latin
        // keyboard adjacency is cheap) with per-tier budgets. The legacy
        // delete-1 index stays as the light backstop for very short words.
        val fallback = fusedCorrectionCandidates(
            subtype, index, raw, prefix, isArabic, caps, maxCandidateCount,
        )
        if (fallback.isEmpty()) return fallback
        // DRS v1.17.0: TRUE autocorrect — the FIRST high-frequency correction of a
        // real typo becomes auto-commit eligible, so pressing space silently fixes
        // the word (backspace reverts). The decider is conservative: the typed
        // word matched no prefix at all (genuine typo branch), it is long enough,
        // and only corpus-head frequencies qualify. User-dictionary matches and
        // normal prefix suggestions never auto-commit.
        // DRS p6 (A3): the correction branch additionally rejects NON-word-
        // internal tokens ("e.g.", "user@host", hyphenated fragments) — a
        // silent auto-commit rewrite is only ever allowed for a plain word.
        // DRS Phase 2 (task 10): the auto-commit candidate is reconstructed on
        // the concrete WordSuggestionCandidate (the interface has no copy), the
        // frequency fed to the decider is recovered from the candidate's
        // confidence, and a MULTI-WORD hard correction (e.g. «إن شاء الله»)
        // is NEVER auto-committed — a silent multi-word insert is a privacy/
        // correctness line this keyboard does not cross. Single-word only.
        val first = fallback.first()
        val firstWord = first as? WordSuggestionCandidate
        val firstIsPlainSingleWord = firstWord != null &&
            firstWord.text.toString().none { it == ' ' }
        val recoveredFreq = ((firstWord?.confidence ?: 0.0) * 255.0).toInt()
        return if (firstIsPlainSingleWord && raw.all { isWordInternalChar(it) } &&
            AutocorrectDecider.shouldAutoCommit(
                typedLength = raw.length,
                typedIsPrefixMatch = false,
                correctionFreq = recoveredFreq,
                enabled = prefs.suggestion.autocorrectEnabled.get(),
            )
        ) {
            buildList {
                add(firstWord!!.copy(isEligibleForAutoCommit = true))
                addAll(fallback.drop(1))
            }
        } else {
            fallback
        }
    }

    /**
     * Next-word prediction: extracts the last word before the cursor from the composed
     * content and looks it up in the language's bigram table. Candidates keep their
     * corpus spelling and are ranked by co-occurrence score. Purely static data, so it
     * is safe in every session type.
     */
    private suspend fun nextWordCandidates(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
    ): List<SuggestionCandidate> {
        if (maxCandidateCount <= 0) return emptyList()
        val before = content.textBeforeSelection
        if (before.isBlank()) return emptyList()

        // DRS M1.3 — the shared last-word scan (word-internal aware, see
        // [lastWordBefore]) now feeds BOTH next-word prediction and the
        // morphology ranker, so the two can no longer disagree about what
        // the previous word even is.
        val prev = lastWordBefore(before) ?: return emptyList()

        val isArabic = prev.any { it in ARABIC_SCRIPT_START..ARABIC_SCRIPT_END }
        // DRS p6 (A10): the bigram key is normalized UNCONDITIONALLY (see
        // the original note below — one path serves both scripts).
        val key = normalize(prev)
        if (key.isEmpty()) return emptyList()

        val staticNexts = bigramsFor(subtype)[key] ?: emptyList()
        // DRS M1.3 — the interpolated predictor: the personal bigram row
        // (what THIS user actually types) weighs α=0.65 against the static
        // corpus row, and when the previous word has no rows at all the
        // LEMMA's rows carry the prediction at the 0.7 discount.
        val caps = powerCaps()
        val personalNexts = if (caps.learningEnabled) {
            DrsLearningEngine.personalNext(key, 12)
        } else {
            emptyList()
        }
        val lemmaKey = DrsArabicMorphology.lemmaOf(key)
        val personalFallback = if (lemmaKey != key && caps.learningEnabled) {
            DrsLearningEngine.personalNext(lemmaKey, 12)
        } else {
            emptyList()
        }
        val staticFallback = if (lemmaKey != key) bigramsFor(subtype)[lemmaKey] ?: emptyList() else emptyList()
        // v2.13.0 «السياق الأعمق» — the trigram layer: when TWO words sit
        // behind the cursor, the static chain table and the personal chain
        // table both get asked about (p2, p1). Either row empty = the layer
        // honestly stays silent (the predictor's pinned no-op contract).
        val twoWords = lastTwoWordsBefore(before)
        val staticTrigram: List<Pair<String, Int>>
        val personalTrigram: List<Pair<String, Int>>
        if (twoWords != null && twoWords.first.isNotEmpty()) {
            val triKey = normalize(twoWords.first)
            if (triKey.isNotEmpty() && triKey != key) {
                staticTrigram = chainsFor(subtype).rowFor(triKey, key)
                    .map { it.next to it.strength }
                personalTrigram = if (caps.learningEnabled) {
                    DrsLearningEngine.personalTrigramNext(triKey, key, 12)
                } else {
                    emptyList()
                }
            } else {
                staticTrigram = emptyList()
                personalTrigram = emptyList()
            }
        } else {
            staticTrigram = emptyList()
            personalTrigram = emptyList()
        }
        val predictions = DrsNextWordPredictor.predict(
            personal = personalNexts,
            static = staticNexts,
            personalFallback = personalFallback,
            staticFallback = staticFallback,
            personalTrigram = personalTrigram,
            staticTrigram = staticTrigram,
            limit = maxCandidateCount,
        )
        if (predictions.isEmpty()) return emptyList()

        // DRS M1.4 — inside an interrogative context (window = 2 tokens:
        // «ماذا تريد أن…» keeps the interrogative in sight behind the
        // conjunction) the answer openers are promoted stably.
        val contextTokens = before.split(Regex("\\s+")).filter { it.isNotBlank() }
        val ordered = DrsContextRanker.rerank(contextTokens, predictions) { it.word }

        return ordered.map { prediction ->
            WordSuggestionCandidate(
                text = nextWordDisplayCase(prediction.word, before, isArabic),
                // DRS p6 (A11): confidence is a 0.0..1.0 contract — the
                // predictor's probabilities satisfy it by construction.
                confidence = prediction.score.coerceIn(0.0, 1.0),
                sourceProvider = this,
            )
        }
    }

    /**
     * DRS M1.3 — the last word before the cursor, WORD-INTERNAL aware
     * (harakat and apostrophes count as part of the word, per p6-A1).
     * Shared by next-word prediction and the morphology ranker.
     */
    private fun lastWordBefore(before: CharSequence): String? {
        var end = before.length
        while (end > 0 && !isWordInternalChar(before[end - 1])) end--
        var start = end
        while (start > 0 && isWordInternalChar(before[start - 1])) start--
        if (start == end) return null
        val word = before.substring(start, end)
        if (word.length < 2 || word.length > MAX_WORD_LENGTH || word.any { it.isDigit() }) return null
        return word
    }

    /**
     * v2.13.0 «السياق الأعمق» — the TWO words before the cursor, in order
     * (second-last to last), by the same word-internal scan [lastWordBefore]
     * uses — the trigram layer's context extraction. Either word failing
     * the same word contract (length 2..MAX, no digits) returns null: the
     * caller then runs the pure bigram path, exactly as today.
     */
    private fun lastTwoWordsBefore(before: CharSequence): Pair<String, String>? {
        var end = before.length
        while (end > 0 && !isWordInternalChar(before[end - 1])) end--
        var start = end
        while (start > 0 && isWordInternalChar(before[start - 1])) start--
        if (start == end) return null
        val last = before.substring(start, end)
        if (last.length < 2 || last.length > MAX_WORD_LENGTH || last.any { it.isDigit() }) return null
        // Second scan: the word that ends before [start]'s whitespace gap.
        var end2 = start
        while (end2 > 0 && !isWordInternalChar(before[end2 - 1])) end2--
        var start2 = end2
        while (start2 > 0 && isWordInternalChar(before[start2 - 1])) start2--
        if (start2 == end2) return null
        val second = before.substring(start2, end2)
        if (second.length < 2 || second.length > MAX_WORD_LENGTH || second.any { it.isDigit() }) return null
        return second to last
    }

    /**
     * DRS: personal user dictionary entries for the active primary language, cached for
     * a few seconds to keep per-keystroke cost low. Never throws; on any failure an empty
     * snapshot is returned and personalization silently degrades.
     *
     * DRS (r0-D perf): the snapshot carries PRE-NORMALIZED spellings alongside the
     * raw entries (computed once per TTL refresh, not once per keystroke).
     */
    private suspend fun userDataFor(subtype: Subtype): UserDataCacheSnapshot {
        val locale = subtype.primaryLocale
        val now = SystemClock.elapsedRealtime()
        userDataCache.withLock { it ->
            if (now - it.loadedAtElapsed < USER_WORDS_TTL_MS) {
                return UserDataCacheSnapshot(it.entries, it.normalizedEntries)
            }
        }
        val fresh = runCatching {
            DictionaryManager.default().queryAllUserWords(locale).take(USER_WORDS_MAX)
        }.getOrElse { emptyList() }
        // DRS (r0-D perf): normalize each personal word ONCE at load time —
        // the exact same pipeline the typed prefix goes through.
        val normalized = fresh.map { entry -> normalize(entry.word) to entry }
        userDataCache.withLock { it ->
            it.loadedAtElapsed = now
            it.entries = fresh
            it.normalizedEntries = normalized
        }
        return UserDataCacheSnapshot(fresh, normalized)
    }

    /** DRS: user words whose (normalized) spelling starts with the typed prefix. */
    private suspend fun userWordMatchesFor(
        subtype: Subtype,
        prefix: String,
        isArabic: Boolean,
        raw: String,
        limit: Int,
    ): List<Pair<String, Int>> {
        if (limit <= 0 || prefix.isEmpty()) return emptyList()
        // DRS (r0-D perf): match against the PRE-NORMALIZED spellings from the
        // cache snapshot — the old code re-normalized every personal word on
        // EVERY keystroke. Same pipeline (v1.20.0: Arabic unification + Latin
        // accent folding for BOTH scripts), just precomputed at load time.
        return userDataFor(subtype).normalizedEntries.mapNotNull { (normWord, entry) ->
            val word = entry.word
            if (word == raw || word.length > MAX_WORD_LENGTH || word.any { it.isDigit() }) return@mapNotNull null
            if (normWord.startsWith(prefix)) word to entry.freq else null
        }.sortedByDescending { it.second }.take(limit)
    }

    private suspend fun invalidateUserDataCache() {
        userDataCache.withLock { it -> it.loadedAtElapsed = Long.MIN_VALUE }
    }

    /**
     * DRS p6 (A7): letters plus the Arabic marks (harakat U+064B..U+065F and
     * superscript alef U+0670 — nonspacing marks, NOT letters). A vocalized
     * word is learnable as a whole instead of being rejected because its
     * marks fail the plain isLetter() test.
     */
    private fun isWordLetterOrMark(c: Char): Boolean =
        c.isLetter() || c in '\u064B'..'\u065F' || c == '\u0670'

    /**
     * DRS: shared learning entry point. Writes into the app-private user dictionary only,
     * never in private sessions, and only for plausible words (letters/marks only, sane length).
     */
    private suspend fun learnWord(subtype: Subtype, word: String, insertFreq: Int): Boolean {
        val clean = word.trim()
        if (clean.length !in 2..MAX_WORD_LENGTH) return false
        // DRS p6 (A7): accept letters AND marks (was: letters only, which
        // silently dropped every diacritized word from learning).
        if (!clean.all { isWordLetterOrMark(it) }) return false
        if (clean.any { it.isDigit() }) return false
        val changed = runCatching {
            DictionaryManager.default().insertOrBumpUserWord(clean, subtype.primaryLocale, insertFreq)
        }.getOrElse { false }
        if (changed) invalidateUserDataCache()
        return changed
    }

    /** DRS Phase 2 (task 9): the previous learned word, for personal bigrams. */
    private var lastLearnedWord: String? = null

    /** v2.13.0: the word before the previous one, for personal trigrams. */
    private var secondLastLearnedWord: String? = null

    /** DRS Phase 2 (task 9): learned-unit counter, drives the flush cadence. */
    private var learningCounter = 0

    /**
     * DRS Phase 2 (task 9): one learning unit = a word in the personal
     * frequency table + (when a previous word exists) one personal bigram.
     * The tables are IN-MEMORY FIRST — flushes are batched every
     * [LEARNING_FLUSH_EVERY] units so typing never waits on disk.
     */
    private fun learnPersonal(word: String) {
        val prev = lastLearnedWord
        val secondPrev = secondLastLearnedWord
        // DRS M1.6 — the lemma rides along automatically:
        // learnWordWithLemma gives the stem half of the word's bump (floor
        // 1) so the per-load decay can never kill the stem before its
        // words. For non-Arabic words the analyzer degrades to the word
        // itself as its own lemma — the reservoir just tracks the word.
        DrsLearningEngine.learnWordWithLemma(word, DrsArabicMorphology.lemmaOf(word))
        if (prev != null) DrsLearningEngine.learnBigram(prev, word)
        // v2.13.0 «السياق الأعمق» — the two-word habit rides the same
        // learning unit: p2 → p1 → next, same gates, same caps, same file.
        if (secondPrev != null && prev != null) DrsLearningEngine.learnTrigram(secondPrev, prev, word)
        secondLastLearnedWord = prev
        lastLearnedWord = word
        if (++learningCounter % LEARNING_FLUSH_EVERY == 0) {
            runCatching { DrsLearningEngine.persist(learningFile()) }
        }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // DRS privacy (S-3): the candidate text IS user text — log length only.
        flogDebug { "suggestion accepted (textLen=${candidate.text.length})" }
        // DRS: reinforce personally learned words when a suggestion is accepted.
        if (lastSuggestWasPrivate) return
        if (!prefs.dictionary.learnFromSuggestions.get()) return
        if (candidate !is WordSuggestionCandidate) return
        val word = candidate.text.toString()
        learnWord(subtype, word, LEARNED_INSERT_FREQ_FROM_SUGGESTION)
        // DRS Phase 2 (task 9): the adaptive frequency table — this is what
        // RANKS the user's real vocabulary above corpus noise. LIGHT-tier
        // devices (battery saver / small RAM) pause learning entirely.
        if (powerCaps().learningEnabled) learnPersonal(word)
    }

    override suspend fun learnExternalWord(subtype: Subtype, word: String, isPrivateSession: Boolean) {
        if (isPrivateSession) return
        if (!prefs.dictionary.learnTypedWords.get()) return
        learnWord(subtype, word, LEARNED_INSERT_FREQ_TYPED)
        // DRS Phase 2 (task 9): same adaptive-table path as accepted
        // suggestions — typed words learn too.
        if (powerCaps().learningEnabled) learnPersonal(word)
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        // DRS privacy (S-3): the candidate text IS user text — log length only.
        flogDebug { "suggestion reverted (textLen=${candidate.text.length})" }
        // DRS v1.20.0: a REJECTED suggestion used to keep sitting in the personal
        // dictionary at full strength — reverting an auto-correct left the unwanted
        // word learned forever. Demote it; when its frequency is drained to zero the
        // entry is deleted entirely. Private sessions never learned anything anyway.
        if (lastSuggestWasPrivate) return
        if (candidate !is WordSuggestionCandidate) return
        val word = candidate.text.toString().trim()
        if (word.length !in 2..MAX_WORD_LENGTH) return
        val changed = runCatching {
            DictionaryManager.default().demoteUserWord(word, subtype.primaryLocale, REVERT_DEMOTE_STEP)
        }.getOrElse { false }
        if (changed) invalidateUserDataCache()
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        // DRS privacy (S-3): the candidate text IS user text — log length only.
        flogDebug { "suggestion removal requested (textLen=${candidate.text.length})" }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return dictFor(subtype)?.words?.keys?.toList() ?: emptyList()
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val index = dictFor(subtype) ?: return 0.0
        val freq = index.words[word]
            ?: index.findByPrefix(normalize(word), limit = 1).firstOrNull()
                ?.takeIf { it.norm == normalize(word) }?.freq
            ?: 0
        return freq / 255.0
    }

    override suspend fun destroy() {
        // Here we have the chance to de-allocate memory and finish our work. However this might never be called if
        // the app process is killed (which will most likely always be the case).
        // DRS Phase 2 (task 9): final learning flush — best-effort by design.
        runCatching { DrsLearningEngine.persist(learningFile()) }
    }

    /**
     * DRS Phase 2 (tasks 8+10+9): the fused correction pipeline — now a thin
     * delegate to the PURE DrsFusedCorrection (v2.12.0 extraction): the
     * real-errors benchmark measures the exact same computation the provider
     * runs. The provider keeps the candidate wrapping (display case + source).
     */
    private fun fusedCorrectionCandidates(
        subtype: Subtype,
        index: DictIndex,
        raw: String,
        prefix: String,
        isArabic: Boolean,
        caps: DrsAiPowerManager.Caps,
        maxCandidateCount: Int,
    ): List<SuggestionCandidate> {
        return DrsFusedCorrection.corrections(
            index = index,
            raw = raw,
            isArabic = isArabic,
            maxCost = caps.maxCorrectionCost,
            lengthBand = caps.lengthBand,
            maxCandidateCount = maxCandidateCount,
            normalize = ::normalize,
            learningBoost = { DrsLearningEngine.boostFor(it) },
        ).map { correction ->
            WordSuggestionCandidate(
                text = displayCase(correction.text, raw, isArabic, subtype.primaryLocale.base),
                confidence = correction.confidence,
                sourceProvider = this,
            )
        }
    }

    // DRS v1.18.0: the normalization pipeline lives in the pure
    // LatinWordNormalize object (strips Arabic diacritics/tatweel, unifies
    // hamza carriers, folds Latin accents, lowercases) — one tested path
    // for both the loaded dictionary and the typed prefix.
    private fun normalize(word: String): String = LatinWordNormalize.normalize(word)

    /**
     * Adapts the dictionary spelling to the user's capitalization for Latin scripts.
     * Arabic text is returned unchanged.
     *
     * DRS v1.28.0 audit improvement: ALL-CAPS typing (caps lock / shift-held
     * entries like "HEL") used to come back as "Hel" — only the first letter
     * was ever adapted. The all-caps style is now honored the way Gboard does.
     */
    private fun displayCase(word: String, typed: String, isArabic: Boolean, locale: Locale): String = when {
        isArabic -> word
        typed.length >= 2 && typed.any { it.isLetter() } && typed.all { !it.isLetter() || it.isUpperCase() } ->
            word.uppercase(locale)
        typed.first().isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }
}

/**
 * DRS v1.20.0: casing for NEXT-WORD predictions. The previous heuristic compared the
 * prediction against the previous word's own case — which is wrong in both directions
 * ("Hello " would capitalize the next word; "Hi. " would not). What actually decides
 * capitalization is whether the word being predicted starts a NEW SENTENCE: that is
 * when the last meaningful character before the cursor is a sentence terminator.
 * Arabic is returned unchanged (no case).
 */
internal fun nextWordDisplayCase(word: String, beforeCursor: String, isArabic: Boolean): String {
    if (isArabic) return word
    val lastMeaningful = beforeCursor.trimEnd().lastOrNull() ?: return word
    return if (lastMeaningful in SENTENCE_TERMINATORS) {
        word.replaceFirstChar { it.uppercaseChar() }
    } else {
        word
    }
}

/**
 * One dictionary entry, pre-normalized at load time for fast prefix lookups.
 * [norm] is the normalized form of [word] used for matching only; suggestions
 * are always returned in their original (correct) spelling.
 */
internal class DictEntry(val norm: String, val word: String, val freq: Int)

/** A language dictionary: entries sorted lexicographically by [DictEntry.norm]. */
internal class DictIndex(val entries: List<DictEntry>) {
    val words: Map<String, Int> by lazy { entries.associate { it.word to it.freq } }

    /**
     * DRS Phase 2 (roadmap task 8): norm -> entry lookup for the quantized
     * correction engine — the weighted search returns NORMALIZED keys, the
     * provider needs the original (correct) spelling back. Built lazily.
     *
     * DRS v2.12.0 — CANONICAL tiebreak instead of last-wins: when several
     * raw spellings share one norm (the dictionary carries a misspelling
     * beside the correct form), the entry whose spelling ends in ة wins
     * over a ه-ending twin, then the higher frequency wins. The suggestion
     * the user sees must be the orthographically canonical form, decided by
     * a deterministic rule — never by JSON order.
     */
    val byNorm: Map<String, DictEntry> by lazy {
        val map = HashMap<String, DictEntry>(entries.size * 2)
        for (entry in entries) {
            val current = map[entry.norm]
            if (current == null || canonicalPrefers(entry, current)) map[entry.norm] = entry
        }
        map
    }

    private fun canonicalPrefers(a: DictEntry, b: DictEntry): Boolean {
        val aTa = a.word.endsWith('ة')
        val bTa = b.word.endsWith('ة')
        if (aTa != bTa) return aTa
        return a.freq > b.freq
    }

    /**
     * Delete-1 neighborhood index for "did you mean?" corrections, built lazily
     * on first use and bounded to the [CORRECTION_INDEX_LIMIT] most frequent words
     * to keep memory usage predictable.
     */
    val correctionIndex: Map<String, List<Int>> by lazy {
        val cutoff = entries.asSequence()
            .map { it.freq }
            .sortedDescending()
            .drop(CORRECTION_INDEX_LIMIT - 1)
            .firstOrNull() ?: 0
        val map = HashMap<String, MutableList<Int>>()
        fun addKey(key: String, index: Int) {
            map.getOrPut(key) { ArrayList(2) }.add(index)
        }
        for (i in entries.indices) {
            val entry = entries[i]
            if (entry.freq < cutoff || entry.norm.length !in CORRECTION_MIN_LENGTH..CORRECTION_MAX_LENGTH) continue
            addKey(entry.norm, i) // catches user typed an extra char (delete1(s) == w)
            for (t in delete1Variants(entry.norm)) {
                addKey(t, i) // catches substitutions and user dropped a char
            }
        }
        map
    }

    /**
     * Collects up to [limit] dictionary entries within edit distance 1 of [norm]
     * (single substitution, insertion or deletion), ranked by frequency.
     */
    fun corrections(norm: String, limit: Int): List<DictEntry> {
        if (norm.length < CORRECTION_MIN_LENGTH) return emptyList()
        val candidateIdx = HashSet<Int>()
        correctionIndex[norm]?.let { candidateIdx.addAll(it) }
        for (t in delete1Variants(norm)) {
            correctionIndex[t]?.let { candidateIdx.addAll(it) }
        }
        return candidateIdx.asSequence()
            .map { entries[it] }
            // DRS p6 (A2): transposition-aware filter — "thier" now reaches
            // "their" (the index already collected swapped-pair candidates,
            // the old transposition-unaware filter rejected them).
            .filter { it.norm != norm && isEditDistanceAtMostOneWithTransposition(it.norm, norm) }
            .sortedByDescending { it.freq }
            .take(limit)
            .toList()
    }

    /**
     * Collects up to [limit] entries whose normalized form starts with [prefix],
     * using a binary search over the sorted entries (O(log n + k)).
     */
    fun findByPrefix(prefix: String, limit: Int): List<DictEntry> {
        if (prefix.isEmpty()) return emptyList()
        var lo = 0
        var hi = entries.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (entries[mid].norm < prefix) lo = mid + 1 else hi = mid
        }
        val out = ArrayList<DictEntry>(limit)
        for (i in lo until entries.size) {
            val entry = entries[i]
            if (!entry.norm.startsWith(prefix)) break
            out.add(entry)
            if (out.size >= limit) break
        }
        return out
    }

    /**
     * DRS v1.20.0: collects the [limit] MOST FREQUENT entries whose normalized form
     * starts with [prefix], scanning the ENTIRE matching range instead of the first
     * [limit] lexicographic hits. The old truncation silently dropped frequent words
     * beyond the cutoff for productive prefixes (like "ال" or "al") — the ranking
     * happened after the cut, so the cut decided the winners. A bounded min-heap
     * keeps this O(k · log limit) over the k matching entries.
     */
    fun findTopByPrefix(prefix: String, limit: Int): List<DictEntry> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        var lo = 0
        var hi = entries.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (entries[mid].norm < prefix) lo = mid + 1 else hi = mid
        }
        val heap = PriorityQueue<DictEntry>(limit.coerceAtLeast(1), compareBy { it.freq })
        for (i in lo until entries.size) {
            val entry = entries[i]
            if (!entry.norm.startsWith(prefix)) break
            if (heap.size < limit) {
                heap.add(entry)
            } else if (entry.freq > heap.peek().freq) {
                heap.poll()
                heap.add(entry)
            }
        }
        val out = heap.sortedByDescending { it.freq }
        return out
    }
}
