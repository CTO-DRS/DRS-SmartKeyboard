/*
 * Copyright (C) 2024-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.media.emoji

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import android.content.Context
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.ime.core.Subtype
import com.drs.smartkeyboard.ime.editor.EditorContent
import com.drs.smartkeyboard.ime.text.key.KeyVariation
import com.drs.smartkeyboard.ime.nlp.EmojiSuggestionCandidate
import com.drs.smartkeyboard.ime.nlp.SuggestionCandidate
import com.drs.smartkeyboard.ime.nlp.SuggestionProvider
import com.drs.smartkeyboard.editorInstance
import com.drs.smartkeyboard.keyboardManager
import com.drs.smartkeyboard.lib.DrsLocale
import org.drs.lib.android.AndroidKeyguardManager
import org.drs.lib.android.systemService
import io.github.reactivecircus.cache4k.Cache

/**
 * Provides emoji suggestions within a text input context.
 *
 * This class handles the following tasks:
 * - Initializes and maintains a list of supported emojis.
 * - Generates and returns emoji suggestions based on user input and preferences.
 *
 * @param context The application context.
 */
class EmojiSuggestionProvider(private val context: Context) : SuggestionProvider {
    override val providerId = "org.drs.nlp.providers.emoji"

    private val prefs by DrsPreferenceStore
    private val lettersRegex = "^[\\p{L}]*$".toRegex()

    private val cachedEmojiMappings = Cache.Builder<DrsLocale, EmojiDataBySkinTone>().build()

    // DRS perf (r0-D): the emoji warm-up below parses the (large) emoji
    // assets. It is now gated on the emoji-suggestion pref AND on the IME
    // window being shown (KeyboardManager's B5 state), and recomputed when
    // either half turns on, so a cold start with the keyboard hidden and a
    // user who never enabled emoji suggestions both skip the parse
    // entirely. suggest() still loads on demand via the cache, so gating
    // the warm-up can only cost first-query latency, never correctness.
    private val gateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val collectorsStarted = AtomicBoolean(false)

    @Volatile
    private var lastPreloadSubtype: Subtype? = null

    override suspend fun create() {
        startGateCollectors()
    }

    override suspend fun preload(subtype: Subtype) {
        lastPreloadSubtype = subtype
        startGateCollectors()
        // DRS perf (r0-D): the gate — disabled pref or hidden window means
        // no warm-up parse.
        if (!prefs.emoji.suggestionEnabled.get()) return
        if (!context.keyboardManager().value.isImeWindowShown()) return
        preloadNow(subtype)
    }

    private suspend fun preloadNow(subtype: Subtype) {
        subtype.locales().forEach { locale ->
            cachedEmojiMappings.get(locale) {
                EmojiData.get(context, locale).bySkinTone
            }
        }
    }

    /** DRS perf (r0-D): idempotent one-shot startup of the two gate collectors. */
    private fun startGateCollectors() {
        if (!collectorsStarted.compareAndSet(false, true)) return
        // DRS perf (r0-D): on-enable collector — turning emoji suggestions
        // on after the fact must warm the mappings for the active subtype.
        gateScope.launch {
            prefs.emoji.suggestionEnabled.asFlow().collect { enabled ->
                if (enabled) recomputePreloadIfWindowShown()
            }
        }
        // DRS perf (r0-D): onImeWindowShown recompute — a preload skipped
        // while the window was hidden is retried the moment it shows.
        gateScope.launch {
            context.keyboardManager().value.imeWindowShown.collect { shown ->
                if (shown) recomputePreloadIfWindowShown()
            }
        }
    }

    /** DRS perf (r0-D): the warm-up, re-evaluated against the gate. */
    private suspend fun recomputePreloadIfWindowShown() {
        val subtype = lastPreloadSubtype ?: return
        if (!prefs.emoji.suggestionEnabled.get()) return
        if (!context.keyboardManager().value.isImeWindowShown()) return
        preloadNow(subtype)
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean
    ): List<SuggestionCandidate> {
        val preferredSkinTone = prefs.emoji.preferredSkinTone.get()
        val showName = prefs.emoji.suggestionCandidateShowName.get()
        val query = validateInputQuery(content.composingText) ?: return emptyList()
        val emojis = cachedEmojiMappings.get(subtype.primaryLocale)?.get(preferredSkinTone) ?: emptyList()
        val candidates = withContext(Dispatchers.Default) {
            emojis.parallelStream()
                .map { emoji ->
                    val nameWeight = emoji.name.containsWeighted(query, ignoreCase = true)
                    val keywordWeight = emoji.keywords
                        .any { it.contains(query, ignoreCase = true) }
                        .let { if (it) 1.0 else 0.0 }
                    emoji to (nameWeight * 0.7 + keywordWeight * 0.3)
                }
                .sorted { (_, a), (_, b) -> b.compareTo(a) }
                // DRS v1.20.0: the zero-weight cut used to run AFTER the limit, so when
                // some of the top-N scored 0 the caller silently got fewer than N emoji
                // even though real matches existed further down. Filter first, then cut.
                .filter { (_, a) -> a > 0 }
                .limit(maxCandidateCount.toLong())
                .map { (emoji, _) ->
                    EmojiSuggestionCandidate(
                        emoji = emoji,
                        showName = showName,
                        sourceProvider = this@EmojiSuggestionProvider,
                    )
                }
                .collect(Collectors.toList())
        }
        return candidates
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        val updateHistory = prefs.emoji.suggestionUpdateHistory.get()
        if (!updateHistory || candidate !is EmojiSuggestionCandidate) {
            return
        }
        // DRS v2.14.0: قبول الاقتراح التِقَاطٌ أيضًا — يمرّ بالبوابة نفسها.
        // عقلية learnExternalWord في NlpManager: التخفي وعطْل التكوين
        // وتنويعات كلمات المرور «أفلِ الاقتراحات فأفلِ التعلم». المحرر الخام
        // يُقرأ من معلومات المحرر الحية، والقفل الشاشي من مدير القفل —
        // التماثل الكامل مع مسار لوحة الإيموجي.
        val km = context.keyboardManager().value
        val state = km.activeState.value
        EmojiHistoryHelper.markEmojiUsed(
            prefs, candidate.emoji,
            DrsEmojiHistoryGate.RecordContext(
                isIncognitoMode = state.isIncognitoMode,
                isPasswordVariation = state.keyVariation == KeyVariation.PASSWORD,
                isRawInputEditor = context.editorInstance().value.activeInfo.isRawInputEditor,
                isComposingEnabled = state.isComposingEnabled,
                isDeviceLocked = context.systemService(AndroidKeyguardManager::class)
                    .let { it.isDeviceLocked || it.isKeyguardLocked },
            ),
        )
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        // No-op
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate) = false

    override suspend fun getListOfWords(subtype: Subtype) = emptyList<String>()

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String) = 0.0

    override suspend fun destroy() {
        cachedEmojiMappings.invalidateAll()
    }

    /**
     * Validates the user input query for emoji suggestions.
     */
    private fun validateInputQuery(composingText: CharSequence): String? {
        val prefix = prefs.emoji.suggestionType.get().prefix
        val queryMinLength = prefs.emoji.suggestionQueryMinLength.get() + prefix.length
        if (prefix.isNotEmpty() && !composingText.startsWith(prefix)) {
            return null
        }
        if (composingText.length < queryMinLength) {
            return null
        }
        val emojiPartialName = composingText.substring(prefix.length)
        if (!lettersRegex.matches(emojiPartialName)) {
            return null
        }
        return emojiPartialName
    }
}

private fun String.containsWeighted(other: String, ignoreCase: Boolean = false): Double = let { str ->
    if (str.contains(other, ignoreCase = ignoreCase)) {
        other.length.toDouble() / str.length.toDouble()
    } else {
        0.0
    }
}
