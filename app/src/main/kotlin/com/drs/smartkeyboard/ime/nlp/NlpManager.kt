/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.clipboardManager
import com.drs.smartkeyboard.editorInstance
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardItem
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import com.drs.smartkeyboard.ime.core.Subtype
import com.drs.smartkeyboard.ime.editor.EditorContent
import com.drs.smartkeyboard.ime.editor.EditorRange
import com.drs.smartkeyboard.ime.media.emoji.EmojiSuggestionProvider
import com.drs.smartkeyboard.ime.nlp.han.HanShapeBasedLanguageProvider
import com.drs.smartkeyboard.ime.nlp.latin.LatinLanguageProvider
import com.drs.smartkeyboard.ime.text.key.KeyVariation
import com.drs.smartkeyboard.keyboardManager
import com.drs.smartkeyboard.lib.util.NetworkUtils
import com.drs.smartkeyboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.drs.lib.kotlin.guardedByLock
import org.drs.lib.kotlin.collectLatestIn
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.Delegates

private const val BLANK_STR_PATTERN = "^\\s*$"

class NlpManager(context: Context) {
    private val blankStrRegex = Regex(BLANK_STR_PATTERN)

    private val prefs by DrsPreferenceStore
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val clipboardSuggestionProvider = ClipboardSuggestionProvider(context)
    private val emojiSuggestionProvider = EmojiSuggestionProvider(context)
    private val providers = guardedByLock {
        mapOf(
            LatinLanguageProvider.ProviderId to ProviderInstanceWrapper(LatinLanguageProvider(context)),
            HanShapeBasedLanguageProvider.ProviderId to ProviderInstanceWrapper(HanShapeBasedLanguageProvider(context)),
        )
    }
    // lock unnecessary because values constant
    private val providersForceSuggestionOn = mutableMapOf<String, Boolean>()

    // DRS (r0-D perf): the suggestions guard is a plain monitor lock now —
    // every section below is short, pure and holds NO suspension points, so
    // the suspend Mutex (which forced suggestDirectly/clearSuggestions into
    // runBlocking hops on the keystroke path) is unnecessary.
    private val internalSuggestionsLock = Any()
    private var internalSuggestions by Delegates.observable(SystemClock.uptimeMillis() to listOf<SuggestionCandidate>()) { _, _, _ ->
        scope.launch { assembleCandidates() }
    }

    /**
     * DRS (M6): the currently running suggest() batch. Cancelled before every
     * new launch so a superseded keystroke stops burning the Default
     * dispatcher in a long dictionary lookup.
     */
    @Volatile
    private var suggestJob: Job? = null

    /**
     * DRS (M5): composing region (absolute, incl. offset) of the LAST WINNING
     * suggest() batch. Auto-commit is only allowed while the editor is still
     * composing exactly this region; [suggestDirectly] (glide previews) and
     * [clearSuggestions] invalidate it so previews can never auto-commit.
     */
    @Volatile
    private var autoCommitComposingSnapshot: EditorRange = EditorRange.Unspecified

    /**
     * DRS v1.20.0: uptime stamp of the last direct (glide preview) publish. Non-zero
     * means a glide gesture is (or very recently was) publishing word previews through
     * [suggestDirectly], and the candidate row must keep showing them instead of
     * letting the clipboard chip take over mid-gesture.
     */
    @Volatile
    private var directSuggestionStamp: Long = 0L

    private val _activeCandidatesFlow = MutableStateFlow(listOf<SuggestionCandidate>())
    val activeCandidatesFlow = _activeCandidatesFlow.asStateFlow()
    inline var activeCandidates
        get() = activeCandidatesFlow.value
        private set(v) {
            _activeCandidatesFlow.value = v
        }

    val debugOverlaySuggestionsInfos = LruCache<Long, Pair<String, SpellingResult>>(10)
    var debugOverlayVersion = MutableStateFlow(0)

    init {
        clipboardManager.primaryClipFlow.collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.enabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.clipboard.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.emoji.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        subtypeManager.activeSubtypeFlow.collectLatestIn(scope) { subtype ->
            preload(subtype)
        }
    }

    /**
     * Gets the punctuation rule from the currently active subtype and returns it. Falls back to a default one if the
     * subtype does not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getActivePunctuationRule(): PunctuationRule {
        return getPunctuationRule(subtypeManager.activeSubtype)
    }

    /**
     * Gets the punctuation rule from the given subtype and returns it. Falls back to a default one if the subtype does
     * not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getPunctuationRule(subtype: Subtype): PunctuationRule {
        return keyboardManager.resources.punctuationRules.value[subtype.punctuationRule] ?: PunctuationRule.Fallback
    }

    private suspend fun getSpellingProvider(subtype: Subtype): SpellingProvider {
        return providers.withLock { it[subtype.nlpProviders.spelling] }?.provider as? SpellingProvider
            ?: FallbackNlpProvider
    }

    private suspend fun getSuggestionProvider(subtype: Subtype): SuggestionProvider {
        return providers.withLock { it[subtype.nlpProviders.suggestion] }?.provider as? SuggestionProvider
            ?: FallbackNlpProvider
    }

    fun preload(subtype: Subtype) {
        scope.launch {
            emojiSuggestionProvider.preload(subtype)
            providers.withLock { providers ->
                subtype.nlpProviders.forEach { _, providerId ->
                    providers[providerId]?.let { provider ->
                        provider.createIfNecessary()
                        provider.preload(subtype)
                    }
                }
            }
        }
    }

    /**
     * DRS v2.8.0 «قاموسك من ملفك»: drops the cached dictionary index for [lang]
     * so the next suggestion pass re-loads it merged with the user's currently
     * imported external dictionaries. Called by the settings screen (same
     * process) right after an import or a removal — the change reaches the live
     * keyboard without any restart.
     */
    fun invalidateDictCaches(lang: String) {
        scope.launch {
            val latin = providers.withLock { it[LatinLanguageProvider.ProviderId] }?.provider
            (latin as? LatinLanguageProvider)?.invalidateDict(lang)
        }
    }

    /**
     * Spell wrapper helper which calls the spelling provider and returns the result. Coroutine management must be done
     * by the source spell checker service.
     */
    suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
    ): SpellingResult {
        return getSpellingProvider(subtype).spell(
            subtype = subtype,
            word = word,
            precedingWords = precedingWords,
            followingWords = followingWords,
            maxSuggestionCount = maxSuggestionCount,
            allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
        )
    }

    suspend fun determineLocalComposing(
        textBeforeSelection: CharSequence, breakIterators: BreakIteratorGroup, localLastCommitPosition: Int
    ): EditorRange {
        return getSuggestionProvider(subtypeManager.activeSubtype).determineLocalComposing(
            subtypeManager.activeSubtype, textBeforeSelection, breakIterators, localLastCommitPosition
        )
    }

    fun providerForcesSuggestionOn(subtype: Subtype): Boolean {
        // Using a cache because I have no idea how fast the runBlocking is
        return providersForceSuggestionOn.getOrPut(subtype.nlpProviders.suggestion) {
            runBlocking {
                getSuggestionProvider(subtype).forcesSuggestionOn
            }
        }
    }

    fun isSuggestionOn(): Boolean =
        prefs.suggestion.enabled.get()
            || prefs.emoji.suggestionEnabled.get()
            || providerForcesSuggestionOn(subtypeManager.activeSubtype)

    fun suggest(subtype: Subtype, content: EditorContent) {
        val reqTime = SystemClock.uptimeMillis()
        // DRS (M6): cancel any in-flight suggest batch before launching the
        // new one — a superseded keystroke must not keep the Default
        // dispatcher busy with a dictionary lookup nobody consumes anymore.
        // The reqTime guard below stays as the correctness backstop for
        // batches that already passed the cancellation point.
        suggestJob?.cancel()
        suggestJob = scope.launch {
            val emojiSuggestions = when {
                prefs.emoji.suggestionEnabled.get() -> {
                    emojiSuggestionProvider.suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = prefs.emoji.suggestionCandidateMaxCount.get(),
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }
                else -> emptyList()
            }
            val suggestions = when {
                emojiSuggestions.isNotEmpty() && prefs.emoji.suggestionType.get().prefix.isNotEmpty() -> {
                    emptyList()
                }
                else -> {
                    val base = getSuggestionProvider(subtype).suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = 8,
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                    // DRS v2.7.0: multilingual typing (HeliBoard heritage,
                    // opt-in). The dictionaries of up to two OTHER enabled
                    // subtypes that share the current subtype's suggestion
                    // provider AND speak a different language also feed the
                    // row, so mixed sentences like «مرحبا hello» never force
                    // a manual language switch. Same on-device providers,
                    // same content, same privacy flags — nothing new leaves
                    // the keyboard. Cross-provider subtypes (e.g. Latin ↔
                    // Han) and same-language duplicates are excluded; extras
                    // interleave round-robin behind the active language's
                    // candidates.
                    if (prefs.suggestion.multilingualTyping.get()) {
                        val extras = subtypeManager.subtypes
                            .filter { other ->
                                other != subtype &&
                                    other.nlpProviders.suggestion == subtype.nlpProviders.suggestion &&
                                    other.primaryLocale.languageTag() != subtype.primaryLocale.languageTag()
                            }
                            .distinctBy { it.primaryLocale.languageTag() }
                            .take(DrsMultilingualMerge.MAX_EXTRA_SUBTYPES)
                            .map { extraSubtype ->
                                getSuggestionProvider(extraSubtype).suggest(
                                    subtype = extraSubtype,
                                    content = content,
                                    maxCandidateCount = 8,
                                    allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                                    isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                                )
                            }
                        DrsMultilingualMerge.interleave(
                            base = base,
                            extras = extras,
                            dedupKey = { it.text.toString().lowercase() },
                        )
                    } else {
                        base
                    }
                }
            }
            synchronized(internalSuggestionsLock) {
                // DRS p6 (A8): tie-break is <= now — batches carry an uptime-ms
                // stamp only, so two batches in the SAME millisecond used to
                // keep the older one and silently drop the newer input.
                if (internalSuggestions.first <= reqTime) {
                    // DRS v1.20.0: regular (typed) suggestions supersede any pending
                    // glide preview — the gesture is over once real typing resumes.
                    directSuggestionStamp = 0L
                    // DRS (M5): remember the composing region this WINNING batch
                    // was computed for — auto-commit stays valid only while the
                    // editor still composes exactly this region.
                    autoCommitComposingSnapshot = content.composing
                    internalSuggestions = reqTime to buildList {
                        addAll(emojiSuggestions)
                        addAll(suggestions)
                    }
                }
            }
        }
    }

    /**
     * DRS: learn a word the user committed manually (typed + space, not picked from the
     * suggestion row). Skipped entirely in private sessions.
     */
    fun learnExternalWord(subtype: Subtype, word: String) {
        // DRS p6 (E6): fields that disabled composing (numeric/phone modes,
        // NO_SUGGESTIONS / AUTO_COMPLETE input-type flags) and password
        // variations must never leak typed tokens into the personal
        // dictionary either — "drop suggestions" implies "drop learning".
        val state = keyboardManager.activeState
        if (state.isIncognitoMode) return
        if (!state.isComposingEnabled) return
        if (state.keyVariation == KeyVariation.PASSWORD) return
        scope.launch {
            getSuggestionProvider(subtype).learnExternalWord(subtype, word, isPrivateSession = false)
        }
    }

    fun suggestDirectly(suggestions: List<SuggestionCandidate>) {
        val reqTime = SystemClock.uptimeMillis()
        // DRS v1.20.0: stamp the direct publish so assembleCandidates keeps the live
        // glide word preview instead of silently swapping in the clipboard chip.
        directSuggestionStamp = reqTime
        // DRS (M5): a glide preview publish must never become an auto-commit —
        // invalidate the snapshot so getAutoCommitCandidate refuses until the
        // next real (typed) suggest() batch wins.
        autoCommitComposingSnapshot = EditorRange.Unspecified
        // DRS (r0-D perf): monitor-locked, no suspend -> no runBlocking hop.
        synchronized(internalSuggestionsLock) {
            internalSuggestions = reqTime to suggestions
        }
    }

    fun clearSuggestions() {
        val reqTime = SystemClock.uptimeMillis()
        // DRS (M5): no candidate row -> nothing may auto-commit either.
        autoCommitComposingSnapshot = EditorRange.Unspecified
        // DRS (r0-D perf): monitor-locked, no suspend -> no runBlocking hop.
        synchronized(internalSuggestionsLock) {
            internalSuggestions = reqTime to emptyList()
        }
    }

    fun getAutoCommitCandidate(): SuggestionCandidate? {
        // DRS p6 (A3): the auto-commit flush is refused for every context that
        // must never silently rewrite text — composing disabled (numeric/phone
        // modes and NO_SUGGESTIONS/AUTO_COMPLETE fields via E6), password
        // variations and private (incognito) sessions. Gating the single
        // lookup API covers all KeyboardManager flush call sites.
        val state = keyboardManager.activeState
        if (!state.isComposingEnabled) return null
        if (state.keyVariation == KeyVariation.PASSWORD) return null
        if (state.isIncognitoMode) return null
        // DRS (M5): the candidate is only eligible while the editor is STILL
        // composing exactly the region the winning suggest() batch was
        // computed for. Valid + non-empty + matching — otherwise the batch is
        // stale (field changed, composing finished, glide preview published)
        // and an auto-commit would rewrite the wrong span.
        val composing = editorInstance.activeContent.composing
        if (!composing.isValid || composing.length <= 0 || composing != autoCommitComposingSnapshot) {
            return null
        }
        return activeCandidates.firstOrNull { it.isEligibleForAutoCommit }
    }

    fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        return runBlocking { candidate.sourceProvider?.removeSuggestion(subtype, candidate) == true }.also { result ->
            if (result) {
                scope.launch {
                    // Need to re-trigger the suggestions algorithm
                    if (candidate is ClipboardSuggestionCandidate) {
                        assembleCandidates()
                    } else {
                        suggest(subtypeManager.activeSubtype, editorInstance.activeContent)
                    }
                }
            }
        }
    }

    fun getListOfWords(subtype: Subtype): List<String> {
        return runBlocking { getSuggestionProvider(subtype).getListOfWords(subtype) }
    }

    fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return runBlocking { getSuggestionProvider(subtype).getFrequencyForWord(subtype, word) }
    }

    private suspend fun assembleCandidates() {
        // DRS (r0-D perf): suspend (no runBlocking wrapper) — callers already
        // run in coroutines; the internal-suggestions read below is a short
        // monitor-locked section with no suspension inside.
        val candidates = when {
            isSuggestionOn() -> {
                val clipboardCandidates = clipboardSuggestionProvider.suggest(
                    subtype = Subtype.DEFAULT,
                    content = editorInstance.activeContent,
                    maxCandidateCount = 8,
                    allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                    isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                )
                val internal = buildList {
                    synchronized(internalSuggestionsLock) {
                        addAll(internalSuggestions.second)
                    }
                }
                // DRS v1.20.0: during a glide the word preview IS the candidate row.
                // It used to lose to the clipboard chip whenever any clip was recent
                // (the most common state right after a copy), leaving the user with
                // no visual feedback for the gesture in progress.
                if (glidePreviewBeatsClipboard(internal.size, clipboardCandidates.size, directSuggestionStamp)) {
                    internal
                } else {
                    clipboardCandidates.ifEmpty { internal }
                }
            }
            else -> emptyList()
        }
        activeCandidates = candidates
        autoExpandCollapseSmartbarActions(candidates, NlpInlineAutofill.suggestions.value)
    }

    fun autoExpandCollapseSmartbarActions(list1: List<*>?, list2: List<*>?) {
        if (!prefs.smartbar.enabled.get()) {// || !prefs.smartbar.sharedActionsAutoExpandCollapse.get()) {
            return
        }
        // TODO: this is a mess and needs to be cleaned up in v0.5 with the NLP development
        /*if (keyboardManager.inputEventDispatcher.isRepeatableCodeLastDown()
            && !keyboardManager.inputEventDispatcher.isPressed(KeyCode.DELETE)
            && !keyboardManager.inputEventDispatcher.isPressed(KeyCode.FORWARD_DELETE)
            || keyboardManager.activeState.isActionsOverflowVisible
        ) {
            return // We do not auto switch if a repeatable action key was last pressed or if the actions overflow
                   // menu is visible to prevent annoying UI changes
        }*/
        val isSelection = editorInstance.activeContent.selection.isSelectionMode
        val isExpanded = list1.isNullOrEmpty() && list2.isNullOrEmpty() || isSelection
        scope.launch {
            // DRS (r0-D perf): smartbar pref writes are VALUE-GUARDED via
            // [isSmartbarPrefValueUnchanged] — this runs for every suggestion
            // batch, and unconditional DataStore writes (disk I/O + listener
            // broadcast) were a per-keystroke cost even when nothing changed.
            if (!isSmartbarPrefValueUnchanged(prefs.smartbar.sharedActionsExpandWithAnimation.get(), false)) {
                prefs.smartbar.sharedActionsExpandWithAnimation.set(false)
            }
            if (!isSmartbarPrefValueUnchanged(prefs.smartbar.sharedActionsExpanded.get(), isExpanded)) {
                prefs.smartbar.sharedActionsExpanded.set(isExpanded)
            }
        }
    }

    /**
     * DRS (r0-D perf): value-guard helper for smartbar preference writes.
     * DataStore writes hit disk and broadcast to every collector while the
     * smartbar expansion flags are re-evaluated on every suggestion batch
     * (and from KeyboardManager paths) — a write may only be issued when the
     * value actually changed. Returns true when [currentValue] already equals
     * [newValue], i.e. the write MUST be skipped.
     *
     * Lives here (public) so [autoExpandCollapseSmartbarActions] and
     * KeyboardManager's smartbar writes share one identical guard.
     */
    fun isSmartbarPrefValueUnchanged(currentValue: Boolean, newValue: Boolean): Boolean =
        currentValue == newValue

    fun addToDebugOverlay(word: String, info: SpellingResult) {
        debugOverlaySuggestionsInfos.put(System.currentTimeMillis(), word to info)
        debugOverlayVersion.update { it + 1 }
    }

    fun clearDebugOverlay() {
        debugOverlaySuggestionsInfos.evictAll()
        debugOverlayVersion.update { it + 1 }
    }

    private class ProviderInstanceWrapper(val provider: NlpProvider) {
        private var isInstanceAlive = AtomicBoolean(false)

        suspend fun createIfNecessary() {
            if (!isInstanceAlive.getAndSet(true)) provider.create()
        }

        suspend fun preload(subtype: Subtype) {
            provider.preload(subtype)
        }

        suspend fun destroyIfNecessary() {
            // DRS v1.19.0 fix: the flag was getAndSet(true) — destroy() ran on
            // EVERY call (not only when alive) and left the flag true forever,
            // so a later createIfNecessary() could never re-create the provider.
            // Now: destroy exactly when alive, and mark the instance dead.
            if (isInstanceAlive.getAndSet(false)) provider.destroy()
        }
    }

    inner class ClipboardSuggestionProvider internal constructor(private val context: Context) : SuggestionProvider {
        private var lastClipboardItemId: Long = -1

        override val providerId = "org.drs.nlp.providers.clipboard"

        override suspend fun create() {
            // Do nothing
        }

        override suspend fun preload(subtype: Subtype) {
            // Do nothing
        }

        override suspend fun suggest(
            subtype: Subtype,
            content: EditorContent,
            maxCandidateCount: Int,
            allowPossiblyOffensive: Boolean,
            isPrivateSession: Boolean,
        ): List<SuggestionCandidate> {
            // Check if enabled
            if (!prefs.clipboard.suggestionEnabled.get()) return emptyList()

            val currentItem = validateClipboardItem(clipboardManager.primaryClip, lastClipboardItemId, content.text)
                ?: return emptyList()

            return buildList {
                val now = System.currentTimeMillis()
                if ((now - currentItem.creationTimestampMs) < prefs.clipboard.suggestionTimeout.get() * 1000) {
                    add(ClipboardSuggestionCandidate(currentItem, sourceProvider = this@ClipboardSuggestionProvider, context = context))
                    if (currentItem.isSensitive) {
                        return@buildList
                    }
                    if (currentItem.type == ItemType.TEXT) {
                        val text = currentItem.stringRepresentation()
                        val matches = buildList {
                            addAll(NetworkUtils.getEmailAddresses(text))
                            addAll(NetworkUtils.getUrls(text))
                            addAll(NetworkUtils.getPhoneNumbers(text))
                        }
                        matches.forEachIndexed { i, match ->
                            val isUniqueMatch = matches.subList(0, i).all { prevMatch ->
                                prevMatch.value != match.value && prevMatch.range.intersect(match.range).isEmpty()
                            }
                            if (match.value != text && isUniqueMatch) {
                                add(ClipboardSuggestionCandidate(
                                    clipboardItem = currentItem.copy(
                                        // DRS v1.26.0: the old inline
                                        // strip (and its TODO) moved into
                                        // NetworkUtils.normalizePhoneNumberMatch
                                        // — a pure, JVM-tested step that
                                        // also unwraps the truncated
                                        // leading-paren case.
                                        text = NetworkUtils.normalizePhoneNumberMatch(match.value),
                                    ),
                                    sourceProvider = this@ClipboardSuggestionProvider,
                                    context = context,
                                ))
                            }
                        }
                    }
                }
            }
        }

        override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
            }
        }

        override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
            // Do nothing
        }

        override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
                return true
            }
            return false
        }

        override suspend fun getListOfWords(subtype: Subtype): List<String> {
            return emptyList()
        }

        override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
            return 0.0
        }

        override suspend fun destroy() {
            // Do nothing
        }

        private fun validateClipboardItem(currentItem: ClipboardItem?, lastItemId: Long, contentText: String) =
            currentItem?.takeIf {
                // Check if already used
                it.id != lastItemId
                    // Check if content is empty
                    && contentText.isBlank()
                    // Check if clipboard content has any valid characters
                    && !currentItem.text.isNullOrBlank()
                    && !blankStrRegex.matches(currentItem.text)
            }
    }
}

/**
 * DRS v1.20.0: pure precedence decision — a live glide preview (direct publish,
 * stamped non-zero) with actual content beats the clipboard chip even when a recent
 * clip exists. Top-level so the JVM contract tests can exercise it without a Context.
 */
internal fun glidePreviewBeatsClipboard(internalCount: Int, clipboardCount: Int, directStamp: Long): Boolean =
    internalCount > 0 && clipboardCount > 0 && directStamp > 0L
