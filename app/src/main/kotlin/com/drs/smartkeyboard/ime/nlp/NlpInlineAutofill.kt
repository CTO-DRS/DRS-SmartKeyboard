/*
 * Copyright (C) 2024-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.nlp

import android.content.Context
import android.os.Build
import android.util.Size
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionInfo
import android.widget.inline.InlineContentView
import androidx.annotation.RequiresApi
import com.drs.smartkeyboard.lib.devtools.flogInfo
import com.drs.smartkeyboard.lib.devtools.flogWarning
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NlpInlineAutofillSuggestion(
    val info: InlineSuggestionInfo,
    val view: InlineContentView?,
)

object NlpInlineAutofill {
    private val currentSequenceId = AtomicInteger(0)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val setterGuard = Mutex()

    val suggestions: StateFlow<List<NlpInlineAutofillSuggestion>>
        field = MutableStateFlow(emptyList())

    var suggestionsChipHeightPx: Int = 0

    @RequiresApi(Build.VERSION_CODES.R)
    fun showInlineSuggestions(context: Context, rawSuggestions: List<InlineSuggestion>): Boolean {
        val sequenceId = generateSequenceId()

        if (rawSuggestions.isEmpty()) {
            clearInlineSuggestions(sequenceId)
            return false
        }

        scope.launch {
            val size = Size(ViewGroup.LayoutParams.WRAP_CONTENT, suggestionsChipHeightPx)
            val latch = CountDownLatch(rawSuggestions.size)
            val suggestionsArray = Array<NlpInlineAutofillSuggestion?>(rawSuggestions.size) { null }

            flogInfo { "showInlineSuggestions: [${sequenceId}] start inflating suggestions" }
            for ((index, rawSuggestion) in rawSuggestions.withIndex()) {
                rawSuggestion.inflate(context, size, context.mainExecutor) { view ->
                    suggestionsArray[index] = NlpInlineAutofillSuggestion(rawSuggestion.info, view)
                    latch.countDown()
                }
            }

            if (!latch.await(2_000, TimeUnit.MILLISECONDS)) {
                flogWarning { "showInlineSuggestions: [${sequenceId}] timed out while waiting for all " +
                    "suggestions to inflate" }
                return@launch
            }

            val inflatedSuggestions = suggestionsArray.filterNotNull().sortedByDescending { it.info.isPinned }
            // DRS v1.28.0 audit fix (Low-Medium): lock()/unlock() pairs with no
            // try/finally — an exception between them (inflated view touch,
            // flow set) permanently wedged ALL future inline-suggestion
            // updates for the session. Mutex.withLock guarantees release.
            setterGuard.withLock {
                flogInfo { "showInlineSuggestions: [${sequenceId}] successfully inflated " +
                    "${inflatedSuggestions.count { it.view != null }} out of ${inflatedSuggestions.size} suggestions" }
                if (currentSequenceId.get() == sequenceId) {
                    flogInfo { "showInlineSuggestions: [${sequenceId}] setting suggestions" }
                    suggestions.value = inflatedSuggestions
                } else {
                    flogWarning { "showInlineSuggestions: [${sequenceId}] seqId != current, skip setting suggestions" }
                }
            }
        }

        return true
    }

    fun clearInlineSuggestions() {
        // Increment sequence id to invalidate eventual pending suggestions
        clearInlineSuggestions(generateSequenceId())
    }

    private fun clearInlineSuggestions(sequenceId: Int) {
        scope.launch {
            // DRS v1.28.0 audit fix: exception-safe lock, see above.
            setterGuard.withLock {
                flogInfo { "clearInlineSuggestions: [${sequenceId}] clearing suggestions" }
                suggestions.value = emptyList()
            }
        }
    }

    private fun generateSequenceId(): Int {
        return currentSequenceId.incrementAndGet()
    }
}
