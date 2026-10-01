/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp.han

import android.content.Context
import android.database.sqlite.SQLiteException
import android.icu.text.BreakIterator
import com.drs.smartkeyboard.appContext
import com.drs.smartkeyboard.extensionManager
import com.drs.smartkeyboard.ime.core.Subtype
import com.drs.smartkeyboard.ime.editor.EditorContent
import com.drs.smartkeyboard.ime.editor.EditorRange
import com.drs.smartkeyboard.ime.nlp.BreakIteratorGroup
import com.drs.smartkeyboard.ime.nlp.LanguagePackComponent
import com.drs.smartkeyboard.ime.nlp.LanguagePackExtension
import com.drs.smartkeyboard.ime.nlp.SpellingProvider
import com.drs.smartkeyboard.ime.nlp.SpellingResult
import com.drs.smartkeyboard.ime.nlp.SuggestionCandidate
import com.drs.smartkeyboard.ime.nlp.SuggestionProvider
import com.drs.smartkeyboard.ime.nlp.WordSuggestionCandidate
import com.drs.smartkeyboard.lib.devtools.flogDebug
import com.drs.smartkeyboard.lib.devtools.flogError
import com.drs.smartkeyboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HanShapeBasedLanguageProvider(val context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.drs.nlp.providers.han.shape"

        const val DB_PATH = "han.sqlite3";
    }


    private val appContext by context.appContext()

    private val maxFreqBySubType = mutableMapOf<String, Int>();
    private val extensionManager by context.extensionManager()
    private val subtypeManager by context.subtypeManager()
    // DRS v1.24.0: the pack index is a StateFlow (ExtensionIndex) — a
    // plain collector in the provider's scope observes it on any thread,
    // no main-thread handler needed.
    private var packObserversWired = false
    private val allLanguagePacks: List<LanguagePackExtension>
        // Assume other types of extensions do not extend LanguagePackExtension
        get() = extensionManager.languagePacks.value
    private var __connectedActiveLanguagePacks: Set<LanguagePackExtension> = setOf() // DRS v1.24.0 note: the live pack observer below retired the old "can't observe" hack — this snapshot stays as the belt-and-braces guard in suggest()
    private var languagePackItems: Map<String, LanguagePackComponent> = mapOf() // init in refreshLanguagePacks()
    private var keyCode: Map<String, Set<Char>> = mapOf() // init in refreshLanguagePacks()
    private val activeLanguagePacks  // language packs referenced in subtypes
        get() = buildSet {
            val locales = subtypeManager.subtypes.map { it.primaryLocale.localeTag() }.toSet()
            for (languagePack in allLanguagePacks) {
                // DRS v1.24.0 honest audit: the upstream FIXME asked for a
                // language-pack type check, but no Han-specific extension
                // subtype exists in the codebase — every pack reaching this
                // provider ships the Han shape-based tables by asset
                // contract, so the check would be a tautology. The locale
                // filter plus the live observer wired in create() keep the
                // set honest instead.
                if (languagePack.items.any { it.locale.localeTag() in locales }) {
                    add(languagePack)
                }
            }
        }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())  // same as NlpManager's preload()

    override val providerId = ProviderId

    private fun refreshLanguagePacks() {
        scope.launch { create() }
    }

    override suspend fun create() {
        // Here we initialize our provider, set up all things which are not language dependent.
        // Refresh language pack parsing

        // DRS v1.24.0: the upstream FIXME "observeForever only callable on
        // the main thread" is finally resolved — the pack index turned out
        // to be a StateFlow (ExtensionIndex : StateFlow<List<T>>), so a
        // plain collector in the provider's own scope observes it without
        // any thread constraint. Installing or removing a Han language
        // pack now refreshes this provider immediately instead of waiting
        // for the next suggest() to notice the snapshot mismatch (that
        // lazy guard stays as belt-and-braces because it is free).
        if (!packObserversWired) {
            packObserversWired = true
            scope.launch {
                extensionManager.languagePacks.collect {
                    refreshLanguagePacks()
                }
            }
        }

        // build index of available language packs
        languagePackItems = buildMap {
            for (languagePack in allLanguagePacks) {
                // DRS v1.24.0 honest audit: same as the activeLanguagePacks
                // note — no Han-specific extension subtype exists to check
                // against; the asset contract is the type system here.
                for (languagePackItem in languagePack.items) {
                    put(languagePackItem.locale.localeTag(), languagePackItem)
                    // FIXME: how to put this in deserialization?
                    languagePackItem.parent = languagePack
                }
            }
        }.toMap()
        keyCode = buildMap {
            languagePackItems.forEach { (tag, languagePackItem) ->
                put(tag, languagePackItem.hanShapeBasedKeyCode.toSet())
            }
            put("default", "abcdefghijklmnopqrstuvwxyz".toSet())
        }.toMap()

        // Load all actively used language packs.
        val activeLanguagePacks = activeLanguagePacks
        for (activeLanguagePack in activeLanguagePacks) {
            if (!activeLanguagePack.isLoaded()) {
                // populates activeLanguagePack.hanShapeBasedSQLiteDatabase
                // FIXME: every time this is copied over to cache.
                activeLanguagePack.load(context)
            }
        }
        __connectedActiveLanguagePacks = activeLanguagePacks
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        // Here we have the chance to preload dictionaries and prepare a neural network for a specific language.
        // Is kept in sync with the active keyboard subtype of the user, however a new preload does not necessary mean
        // the previous language is not needed anymore (e.g. if the user constantly switches between two subtypes)

        // To read a file from the APK assets the following methods can be used:
        // appContext.assets.open()
        // appContext.assets.reader()
        // appContext.assets.bufferedReader()
        // appContext.assets.readText()
        // To copy an APK file/dir to the file system cache (appContext.cacheDir), the following methods are available:
        // appContext.assets.copy()
        // appContext.assets.copyRecursively()

        // The subtype we get here contains a lot of data, however we are only interested in subtype.primaryLocale and
        // subtype.secondaryLocales.
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
        return when (word.lowercase()) {
            // Use typo for typing errors
            "typo" -> SpellingResult.typo(arrayOf("typo1", "typo2", "typo3"))
            // Use grammar error if the algorithm can detect this. On Android 11 and lower grammar errors are visually
            // marked as typos due to a lack of support
            "gerror" -> SpellingResult.grammarError(arrayOf("grammar1", "grammar2", "grammar3"))
            // Use valid word for valid input
            else -> SpellingResult.validWord()
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        if (__connectedActiveLanguagePacks != activeLanguagePacks) {
            // DRS v1.24.0: belt-and-braces only — the live observer wired
            // in create() refreshes eagerly; this lazy guard costs nothing
            // and covers any observer-delivery gap.
            refreshLanguagePacks()
        }
        if (content.composingText.isEmpty()) {
            return emptyList();
        }
        val (languagePackItem, languagePackExtension) = getLanguagePack(subtype) ?: return emptyList();
        val layout: String = languagePackItem.hanShapeBasedTable
        try {
            val database = languagePackExtension.hanShapeBasedSQLiteDatabase
            // DRS p6 (A13): the cursor is .use{}-closed now — the raw query()
            // cursor used to leak on every early return/exception, exhausting
            // the SQLite statement cache of the pack database over a session.
            val suggestions = database.query(layout, arrayOf("code", "text"), "code LIKE ? || '%'", arrayOf(content.composingText), "", "", "code ASC, weight DESC", "$maxCandidateCount").use { cur ->
                cur.moveToFirst()
                val rowCount = cur.count
                // DRS privacy (S-3): the composing text IS user text — log length only.
                flogDebug { "Query was composing text (len=${content.composingText.length})" }
                buildList {
                    for (n in 0 until rowCount) {
                        val code = cur.getString(0)
                        val word = cur.getString(1)
                        cur.moveToNext()
                        add(WordSuggestionCandidate(
                            text = "$word",
                            secondaryText = code,
                            confidence = 0.5,
                            isEligibleForAutoCommit = n == 0,
                            // We set ourselves as the source provider so we can get notify events for our candidate
                            sourceProvider = this@HanShapeBasedLanguageProvider,
                        ))
                    }
                }
            }
            return suggestions
        } catch (e: IllegalStateException) {
            flogError { "Invalid layout '${layout}' not found" }
            return emptyList()
        } catch (e: SQLiteException) {
            // DRS privacy (S-3): composing text is user text — log length only.
            flogError { "SQLiteException: layout=$layout, composingLen=${content.composingText.length}, error='${e}'" }
            return emptyList()
        }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // We can use flogDebug, flogInfo, flogWarning and flogError for debug logging, which is a wrapper for Logcat
        // DRS privacy (S-3): the candidate text IS user text — log length only.
        flogDebug { "suggestion accepted (textLen=${candidate.text.length})" }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        // DRS privacy (S-3): the candidate text IS user text — log length only.
        flogDebug { "suggestion reverted (textLen=${candidate.text.length})" }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        // DRS privacy (S-3): the candidate text IS user text — log length only.
        flogDebug { "suggestion removal requested (textLen=${candidate.text.length})" }
        return false
    }

    fun getLanguagePack(subtype: Subtype): Pair<LanguagePackComponent, LanguagePackExtension>? {
        val languagePackItem = languagePackItems[subtype.primaryLocale.localeTag()]
        val languagePackExtension = languagePackItem?.parent
        if (languagePackItem == null || languagePackExtension == null) {
            flogError { "Could not read language pack item / extension" }
            return null;
        }
        return Pair(languagePackItem, languagePackExtension)
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return emptyList()
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return 0.0
//        val (languagePackItem, languagePackExtension) = getLanguagePack(subtype) ?: return 0.0;
//        val layout: String = languagePackItem.hanShapeBasedTable
//        try {
//            val database = languagePackExtension.hanShapeBasedSQLiteDatabase
//            val cur = database.query(layout, arrayOf ( "weight" ), "code = ?", arrayOf(word), "", "", "", "");
//            cur.moveToFirst();
//            return try { cur.getDouble(0) } catch (e: Exception) { 0.0 };
//        } catch (e: SQLiteException) {
//            return 0.0;
//        }
    }

    override suspend fun destroy() {
        // Here we have the chance to de-allocate memory and finish our work. However this might never be called if
        // the app process is killed (which will most likely always be the case).
    }

    override suspend fun determineLocalComposing(
        subtype: Subtype,
        textBeforeSelection: CharSequence,
        breakIterators: BreakIteratorGroup,
        localLastCommitPosition: Int
    ): EditorRange {
        return breakIterators.character(subtype.primaryLocale) {
            it.setText(textBeforeSelection.toString())
            val end = it.last()
            var start = end
            var next = it.previous()
            val keyCodeLocale = keyCode[subtype.primaryLocale.localeTag()]?: keyCode["default"]?: emptySet()
            while (next != BreakIterator.DONE && start > localLastCommitPosition) {
                val sub = textBeforeSelection.substring(next, start)
                if (! sub.all { char -> char in keyCodeLocale })
                    break
                start = next
                next = it.previous()
            }
            if (start != end) {
                // DRS privacy (S-3): the composing text IS user text — log length only.
                flogDebug { "Determined $start - $end as composing (len=${end - start})" }
                EditorRange(start, end)
            } else {
                flogDebug { "Determined Unspecified as composing" }
                EditorRange.Unspecified
            }
        }
    }

    override val forcesSuggestionOn
        get() = true
}
