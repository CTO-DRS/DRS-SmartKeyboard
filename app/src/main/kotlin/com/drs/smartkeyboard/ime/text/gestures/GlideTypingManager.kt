/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.text.gestures

import android.content.Context
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.ime.nlp.WordSuggestionCandidate
import com.drs.smartkeyboard.ime.text.keyboard.TextKey
import com.drs.smartkeyboard.keyboardManager
import com.drs.smartkeyboard.lib.devtools.LogTopic
import com.drs.smartkeyboard.lib.devtools.flogError
import com.drs.smartkeyboard.nlpManager
import com.drs.smartkeyboard.subtypeManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Handles the [GlideTypingClassifier]. Basically responsible for linking [GlideTypingGesture.Detector]
 * with [GlideTypingClassifier].
 */
class GlideTypingManager(context: Context) : GlideTypingGesture.Listener {
    companion object {
        private const val MAX_SUGGESTION_COUNT = 8
    }

    private val prefs by DrsPreferenceStore
    private val keyboardManager by context.keyboardManager()
    private val nlpManager by context.nlpManager()
    private val subtypeManager by context.subtypeManager()

    // DRS p7 (E2-3): the glide scope now carries a CoroutineExceptionHandler
    // as a last-resort backstop — an uncaught classifier crash (torn layout
    // read, OOM in a 50k prune, …) previously propagated out of the scope
    // and killed the whole IME process.
    private val scope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            flogError(LogTopic.GLIDE) { "Uncaught glide coroutine failure: $throwable" }
        }
    )
    private var glideTypingClassifier = StatisticalGlideTypingClassifier(context, scope)
    private var lastTime = System.currentTimeMillis()

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        // DRS p7 (E2-5): atomically snapshot-and-clear the gesture SYNCHRONOUSLY
        // (under the classifier's gestureLock) and classify the immutable
        // snapshot in the async block. The old flow cleared the classifier
        // only in the post-commit Main callback — a fast continuous glider
        // starting the next word inside that window had the first points of
        // the new gesture wiped by the late clear() → truncated gesture →
        // wrong/no suggestion. An empty gesture skips the async launch
        // entirely (commit outcome unchanged: nothing was classified).
        val gestureSnapshot = glideTypingClassifier.snapshotAndClearGesture() ?: return
        updateSuggestionsAsync(MAX_SUGGESTION_COUNT, true, gestureSnapshot) { }
    }

    override fun onGlideCancelled() {
        glideTypingClassifier.clear()
    }

    override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
        val normalized = GlideTypingGesture.Detector.Position(point.x, point.y)

        this.glideTypingClassifier.addGesturePoint(normalized)

        val time = System.currentTimeMillis()
        if (prefs.glide.showPreview.get() && time - lastTime > prefs.glide.previewRefreshDelay.get()) {
            // Live preview path: no snapshot — getSuggestions() snapshots the
            // live gesture itself (DRS p7 (E2-5)).
            updateSuggestionsAsync(1, false, null) {}
            lastTime = time
        }
    }

    /**
     * Change the layout of the internal gesture classifier
     */
    fun setLayout(keys: List<TextKey>) {
        if (keys.isNotEmpty()) {
            glideTypingClassifier.setLayout(keys, subtypeManager.activeSubtype)
        }
    }

    /**
     * Asks gesture classifier for suggestions and then passes that on to the smartbar.
     * Also commits the most confident suggestion if [commit] is set. All happens on an async executor.
     * NB: only fetches [MAX_SUGGESTION_COUNT] suggestions.
     *
     * DRS p7 (E2-5): when [gestureSnapshot] is non-null the classification
     * runs against that immutable capture via
     * [StatisticalGlideTypingClassifier.getSuggestionsFromSnapshot] (same LRU
     * keying); when null the classifier snapshots the live gesture itself
     * (preview path).
     *
     * @param callback Called when this function completes. Takes a boolean, which indicates if suggestions
     * were successfully set.
     */
    private fun updateSuggestionsAsync(
        maxSuggestionsToShow: Int,
        commit: Boolean,
        gestureSnapshot: StatisticalGlideTypingClassifier.Gesture?,
        callback: (Boolean) -> Unit,
    ) {
        if (!glideTypingClassifier.ready) {
            callback.invoke(false)
            return
        }

        scope.launch(Dispatchers.Default) {
            val suggestions = if (gestureSnapshot != null) {
                glideTypingClassifier.getSuggestionsFromSnapshot(MAX_SUGGESTION_COUNT, gestureSnapshot)
            } else {
                glideTypingClassifier.getSuggestions(MAX_SUGGESTION_COUNT, true)
            }

            withContext(Dispatchers.Main) {
                val suggestionList = buildList {
                    suggestions.subList(
                        1.coerceAtMost(min(commit.compareTo(false), suggestions.size)),
                        maxSuggestionsToShow.coerceAtMost(suggestions.size)
                    ).map { keyboardManager.fixCase(it) }.forEach {
                        add(WordSuggestionCandidate(it, confidence = 1.0))
                    }
                }

                nlpManager.suggestDirectly(suggestionList)
                if (commit && suggestions.isNotEmpty()) {
                    keyboardManager.commitGesture(suggestions.first())
                }
                callback.invoke(true)
            }
        }
    }
}
