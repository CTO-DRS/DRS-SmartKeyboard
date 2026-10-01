/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.editor

import com.drs.smartkeyboard.drs.DrsHarakat
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.TextUtils
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.drs.smartkeyboard.DrsImeService
import com.drs.smartkeyboard.ime.nlp.BreakIteratorGroup
import com.drs.smartkeyboard.ime.text.composing.Composer
import com.drs.smartkeyboard.keyboardManager
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import com.drs.smartkeyboard.nlpManager
import com.drs.smartkeyboard.subtypeManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.max
import kotlin.math.min

enum class OperationUnit {
    CHARACTERS,
    WORDS;
}

enum class OperationScope {
    BEFORE_CURSOR,
    AFTER_CURSOR;
}

@Suppress("BlockingMethodInNonBlockingContext")
abstract class AbstractEditorInstance(context: Context) {
    companion object {
        private const val NumCharsBeforeCursor: Int = 256
        private const val NumCharsAfterCursor: Int = 128
        private const val NumCharsSafeMarginBeforeCursor: Int = 128
        //private const val NumCharsSafeMarginAfterCursor: Int = 0

        private const val CursorUpdateAll: Int =
            InputConnection.CURSOR_UPDATE_MONITOR or InputConnection.CURSOR_UPDATE_IMMEDIATE
        private const val CursorUpdateNone: Int = 0
    }

    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()
    private val nlpManager by context.nlpManager()
    private val scope = MainScope()
    protected val breakIterators = BreakIteratorGroup()

    private val _activeInfoFlow = MutableStateFlow(DrsEditorInfo.Unspecified)
    val activeInfoFlow = _activeInfoFlow.asStateFlow()
    inline var activeInfo: DrsEditorInfo
        get() = activeInfoFlow.value
        private set(v) {
            _activeInfoFlow.value = v
        }

    private val _activeCursorCapsModeFlow = MutableStateFlow(InputAttributes.CapsMode.NONE)
    val activeCursorCapsModeFlow = _activeCursorCapsModeFlow.asStateFlow()
    inline var activeCursorCapsMode: InputAttributes.CapsMode
        get() = activeCursorCapsModeFlow.value
        private set(v) {
            _activeCursorCapsModeFlow.value = v
        }

    private val _activeContentFlow = MutableStateFlow(EditorContent.Unspecified)
    val activeContentFlow = _activeContentFlow.asStateFlow()
    inline var activeContent: EditorContent
        get() = expectedContent() ?: activeContentFlow.value
        private set(v) {
            _activeContentFlow.value = v
        }
    private val expectedContentQueue = ExpectedContentQueue()
    private val _lastCommitPosition = LastCommitPosition()
    val lastCommitPosition
        get() = LastCommitPosition(_lastCommitPosition)

    fun expectedContent(): EditorContent? {
        // DRS (r0-D perf): the queue is monitor-locked and non-suspending now,
        // so this hot read path no longer needs a runBlocking hop.
        return expectedContentQueue.peekNewestOrNull()
    }

    /**
     * DRS p7 (F4): drops every queued expected-content entry (monitor-locked,
     * non-suspending — same discipline as push/pop). Raw input-connection
     * commits — e.g. the text-tool paths in [EditorInstance] — mutate the
     * editor BEHIND this queue's back; a stale entry whose (selection,
     * composing) coincides with the post-tool selection would otherwise be
     * adopted by the next selection update and serve a PRE-transform content
     * snapshot to the suggestion/shift-state pipeline. Must be called
     * immediately after every successful raw-IC commit, before any deferred
     * host selection update can pop the queue.
     */
    internal fun invalidateExpectedContent() {
        expectedContentQueue.clear()
    }

    private fun currentInputConnection() = DrsImeService.currentInputConnection()

    open fun handleStartInput(editorInfo: DrsEditorInfo) {
        activeInfo = editorInfo
        activeCursorCapsMode = editorInfo.initialCapsMode
        activeContent = EditorContent.Unspecified
        currentInputConnection()?.requestCursorUpdates(CursorUpdateAll)
    }

    open fun handleStartInputView(editorInfo: DrsEditorInfo, isRestart: Boolean) {
        if (isRestart) {
            reset() // Just to make sure our state is correct after a restart
        }
        val ic = currentInputConnection()
        activeInfo = editorInfo
        var selection = editorInfo.initialSelection
        if (ic == null || selection.isNotValid || editorInfo.isRawInputEditor) {
            activeCursorCapsMode = InputAttributes.CapsMode.NONE
            activeContent = EditorContent.Unspecified
            keyboardManager.reevaluateInputShiftState()
            return
        }

        // Get text (ignore initial text of EditorInfo because some apps like to provide an old or invalid state)
        val textBeforeSelection = ic.getTextBeforeCursor(NumCharsBeforeCursor, 0) ?: ""
        val textAfterSelection = ic.getTextAfterCursor(NumCharsAfterCursor, 0) ?: ""
        val selectedText = ic.getSelectedText(0) ?: ""

        // Adjust initial selection issues as some apps like to do everything but provide the correct initial selection
        if (selection.length != selectedText.length) {
            selection = EditorRange(textBeforeSelection.length, textBeforeSelection.length + selectedText.length)
        } else if (selection.start > 0 || selection.end > 0) {
            if (textBeforeSelection.isEmpty() && textAfterSelection.isEmpty() && selectedText.isEmpty()) {
                selection = EditorRange(0, 0)
            }
        }

        scope.launch {
            // DRS p6 (E2): fence the async application on session identity —
            // an input restart or connection swap that happened while this
            // block sat queued must not apply a stale content cache or
            // composing region into the NEW field (cross-field cache
            // poisoning). Both checks are cheap reference comparisons.
            if (activeInfo !== editorInfo || currentInputConnection() !== ic) return@launch
            val content = generateContent(
                editorInfo,
                selection,
                textBeforeSelection,
                textAfterSelection,
                selectedText,
            )
            activeCursorCapsMode = content.cursorCapsMode()
            activeContent = content
            keyboardManager.reevaluateInputShiftState()
            ic.setComposingRegion(content.composing)
        }
    }

    protected fun handleMassSelectionUpdate(newSelection: EditorRange, composing: EditorRange) {
        activeCursorCapsMode = InputAttributes.CapsMode.NONE
        activeContent = EditorContent.selectionOnly(newSelection)
        if (composing.isValid) {
            currentInputConnection()?.setComposingRegion(EditorRange.Unspecified)
        }
        _lastCommitPosition.handleUpdateSelection(newSelection)
    }

    open fun handleSelectionUpdate(oldSelection: EditorRange, newSelection: EditorRange, composing: EditorRange) {
        val ic = currentInputConnection()
        val editorInfo = activeInfo
        if (ic == null || newSelection.isNotValid || editorInfo.isRawInputEditor) {
            activeCursorCapsMode = InputAttributes.CapsMode.NONE
            activeContent = EditorContent.Unspecified
            keyboardManager.reevaluateInputShiftState()
            return
        }

        _lastCommitPosition.handleUpdateSelection(newSelection)
        // DRS (r0-D perf): popUntilOrNull is monitor-locked and non-suspending
        // now — no runBlocking hop inside onUpdateSelection.
        val expected = expectedContentQueue.popUntilOrNull {
            it.selection == newSelection && it.composing == composing &&
                it.textBeforeSelection.length >= NumCharsSafeMarginBeforeCursor.coerceAtMost(it.selection.start)
        }
        if (expected != null) {
            activeCursorCapsMode = expected.cursorCapsMode()
            activeContent = expected
            keyboardManager.reevaluateInputShiftState()
            return
        }

        // Get Text
        val textBeforeSelection =
            if (newSelection.start > 0) ic.getTextBeforeCursor(NumCharsBeforeCursor, 0) ?: "" else ""
        val textAfterSelection = ic.getTextAfterCursor(NumCharsAfterCursor, 0) ?: ""
        val selectedText = if (newSelection.isSelectionMode) ic.getSelectedText(0) ?: "" else ""

        scope.launch {
            // DRS p6 (E2): same session-identity fence as handleStartInputView —
            // never apply a stale content cache / composing region when the
            // editor info or the input connection changed since this block was
            // queued (stale composing on the old field was possible).
            if (activeInfo !== editorInfo || currentInputConnection() !== ic) return@launch
            val content = generateContent(
                editorInfo,
                newSelection,
                textBeforeSelection,
                textAfterSelection,
                selectedText,
            )
            activeCursorCapsMode = content.cursorCapsMode()
            activeContent = content
            keyboardManager.reevaluateInputShiftState()
            if (content.composing != composing) {
                ic.setComposingRegion(content.composing)
            }
        }
    }

    open fun handleFinishInputView() {
        reset()
    }

    open fun handleFinishInput() {
        reset()
        // DRS (M3): finish any active composing region BEFORE closing the
        // cursor-update channel. Hosts disconnected mid-composition used to
        // keep rendering the composing underline forever because the IME
        // never finalized it. Null-safe apply: the connection may already be
        // gone, in which case there is nothing left to finalize.
        currentInputConnection()?.apply {
            finishComposingText()
            requestCursorUpdates(CursorUpdateNone)
        }
    }

    protected open fun reset() {
        activeInfo = DrsEditorInfo.Unspecified
        activeCursorCapsMode = InputAttributes.CapsMode.NONE
        activeContent = EditorContent.Unspecified
        // DRS (r0-D perf): queue clear is monitor-locked and non-suspending now.
        expectedContentQueue.clear()
        _lastCommitPosition.reset()
    }

    private suspend fun generateContent(
        editorInfo: DrsEditorInfo,
        selection: EditorRange,
        textBeforeSelection: CharSequence,
        textAfterSelection: CharSequence,
        selectedText: CharSequence,
    ): EditorContent {
        // Calculate offset and local selection
        val offset = selection.start - textBeforeSelection.length
        val localSelection = EditorRange(
            start = textBeforeSelection.length,
            end = textBeforeSelection.length + selectedText.length,
        )

        // Check consistency and exit if necessary
        if (offset < 0 || selection.translatedBy(-offset) != localSelection) {
            return EditorContent.Unspecified
        }

        // Determine local composing word range, if any
        val localCurrentWord =
            if (shouldDetermineComposingRegion(editorInfo) && localSelection.isCursorMode && textBeforeSelection.isNotEmpty()) {
                determineLocalComposing(textBeforeSelection, _lastCommitPosition.pos - offset)
            } else {
                EditorRange.Unspecified
            }
        val localComposing = if (determineComposingEnabled()) localCurrentWord else EditorRange.Unspecified

        // Build and publish text and content
        val text = buildString {
            append(textBeforeSelection)
            append(selectedText)
            append(textAfterSelection)
        }
        return EditorContent(text, offset, localSelection, localComposing, localCurrentWord)
    }

    private suspend fun EditorContent.generateCopy(
        editorInfo: DrsEditorInfo = activeInfo,
        selection: EditorRange = this.selection,
        textBeforeSelection: CharSequence = this.textBeforeSelection,
        textAfterSelection: CharSequence = this.textAfterSelection,
        selectedText: CharSequence = this.selectedText,
    ): EditorContent {
        return generateContent(
            editorInfo, selection, textBeforeSelection, textAfterSelection, selectedText
        )
    }

    private fun EditorContent.cursorCapsMode(): InputAttributes.CapsMode {
        return when {
            localSelection.isNotValid -> InputAttributes.CapsMode.NONE
            else -> {
                InputAttributes.CapsMode.fromFlags(
                    TextUtils.getCapsMode(text, localSelection.start, activeInfo.inputAttributes.raw)
                )
            }
        }
    }

    abstract fun determineComposingEnabled(): Boolean

    abstract fun determineComposer(composerName: ExtensionComponentName): Composer

    protected open fun shouldDetermineComposingRegion(editorInfo: DrsEditorInfo): Boolean {
        return editorInfo.isRichInputEditor && !editorInfo.inputAttributes.flagTextNoSuggestions
    }

    private suspend fun determineLocalComposing(
        textBeforeSelection: CharSequence, localLastCommitPosition: Int
    ): EditorRange {
        return nlpManager.determineLocalComposing(textBeforeSelection, breakIterators, localLastCommitPosition)
    }

    private fun InputConnection.setComposingRegion(composing: EditorRange) {
        if (composing.isValid) {
            this.setComposingRegion(composing.start, composing.end)
        } else {
            this.finishComposingText()
        }
    }

    protected fun setSelection(selection: EditorRange): Boolean {
        if (activeInfo.isRawInputEditor) return false
        val content = activeContent
        if (content.selection == selection) return true
        val ic = currentInputConnection() ?: return false
        // DRS v1.28.0 audit fix: batch edits opened on the hot paths are now
        // exception-safe. A DeadObjectException or editor crash mid-batch used
        // to leak the beginBatchEdit() reference and leave the host editor in
        // batch mode (deferred updates, broken undo) until the input restarted.
        ic.beginBatchEdit()
        try {
            runBlocking {
                val newContent = content
                    .copy(localSelection = selection.translatedBy(-content.offset))
                    .generateCopy(selection = selection)
                expectedContentQueue.push(newContent)
                ic.setSelection(selection.start, selection.end)
                ic.setComposingRegion(newContent.composing)
            }
        } finally {
            ic.endBatchEdit()
        }
        return true
    }

    open fun commitChar(char: String): Boolean {
        return commitChar(
            char = char,
            deletePreviousSpace = false,
            insertSpaceBeforeChar = false,
            insertSpaceAfterChar = false,
        )
    }

    /**
     * DRS (r0-D re-application): true when [s] is exactly one plain
     * single-code-unit grapheme cluster — one non-surrogate code point that
     * is not a Grapheme_Extend mark (Mn/Me/Mc per UAX-29 GB9) and not ZWJ.
     * Pure and allocation-free; lets [commitChar] skip the ICU break
     * iterator on the common letter/digit/punctuation keystroke.
     */
    private fun endsWithPlainSingleCharGrapheme(s: String): Boolean {
        if (s.length != 1) return false
        val c = s[0]
        if (Character.isSurrogate(c)) return false
        if (c == '\u200D') return false
        val t = Character.getType(c).toByte()
        return when (t) {
            Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> false
            else -> true
        }
    }

    protected fun commitChar(
        char: String,
        deletePreviousSpace: Boolean,
        insertSpaceBeforeChar: Boolean,
        insertSpaceAfterChar: Boolean,
    ): Boolean {
        val content = activeContent
        val selection = content.selection
        // DRS (r0-D re-application): the per-keystroke isSingleChar check must
        // not block the main thread on the ICU break iterator. A single
        // non-surrogate code point that is not a Grapheme_Extend mark
        // (UAX-29 GB9: Mn/Me/Mc, plus ZWJ) always forms one plain grapheme —
        // ICU is only consulted for surrogate pairs and exotic cases.
        val isSingleChar = endsWithPlainSingleCharGrapheme(char) || runBlocking {
            breakIterators.measureUChars(char, 1, subtypeManager.activeSubtype.primaryLocale)
        } == char.length
        if (!isSingleChar || selection.isNotValid || selection.isSelectionMode || activeInfo.isRawInputEditor) {
            return commitTextInternal(char)
        }
        val ic = currentInputConnection() ?: return false
        val composer = determineComposer(subtypeManager.activeSubtype.composer)
        val previous = content.textBeforeSelection.takeLast(composer.toRead.coerceAtLeast(if (deletePreviousSpace) 1 else 0))
        val (tempRm, tempText) = composer.getActions(previous, char)
        val rm = if (deletePreviousSpace && previous.isNotEmpty() && previous.last() == ' ') tempRm + 1 else tempRm
        val finalText = buildString(tempText.length + 2) {
            if (insertSpaceBeforeChar) append(' ')
            append(tempText)
            if (insertSpaceAfterChar) append(' ')
        }
        if (rm <= 0) {
            commitTextInternal(finalText)
        } else runBlocking {
            // DRS v1.28.0 audit fix: exception-safe batch edit (see setSelection).
            ic.beginBatchEdit()
            try {
                val newSelection = EditorRange.cursor(selection.start - rm + finalText.length)
                val newContent = content.generateCopy(
                    selection = newSelection,
                    textBeforeSelection = buildString {
                        append(content.textBeforeSelection.dropLast(rm))
                        append(finalText)
                    },
                    selectedText = "",
                )
                expectedContentQueue.push(newContent)
                // Utilize composing region to replace previous chars without using delete. This avoids flickering in the
                // target editor and improves the UX
                ic.setComposingRegion(content.selection.start - rm, content.selection.start)
                ic.setComposingText(finalText, 1)
                // Now set the proper composing region we expect
                ic.setComposingRegion(newContent.composing)
            } finally {
                ic.endBatchEdit()
            }
        }
        return true
    }

    open fun commitText(text: String): Boolean = commitTextInternal(text)

    private fun commitTextInternal(text: String): Boolean {
        val ic = currentInputConnection() ?: return false
        val content = activeContent
        val selection = content.selection
        // DRS v1.28.0 audit fix: exception-safe batch edit (see setSelection).
        ic.beginBatchEdit()
        try {
            ic.finishComposingText()
            if (activeInfo.isRawInputEditor) {
                ic.commitText(text, 1)
            } else runBlocking {
                val newSelection = EditorRange.cursor(selection.start + text.length)
                val newContent = content.generateCopy(
                    selection = newSelection,
                    textBeforeSelection = buildString {
                        append(content.textBeforeSelection)
                        append(text)
                    },
                    selectedText = "",
                )
                expectedContentQueue.push(newContent)
                ic.commitText(text, 1)
                ic.setComposingRegion(newContent.composing)
            }
        } finally {
            ic.endBatchEdit()
        }
        return true
    }

    open fun finalizeComposingText(text: String): Boolean {
        val ic = currentInputConnection() ?: return false
        val content = activeContent
        val composing = content.composing
        // DRS v1.28.0 audit fix (HIGH): this early return used to run AFTER
        // beginBatchEdit() below had already been issued, leaking the batch
        // reference and leaving the host editor stuck in batch mode (broken
        // undo, deferred UI updates) until the input connection was reset.
        // Triggered via EditorInstance.tryPerformEnterCommitRaw whenever the
        // cached composing state disagreed with the real editor state. The
        // guard now runs before the batch opens, and the batch is
        // exception-safe via try/finally.
        if (activeInfo.isRawInputEditor || composing.isNotValid) {
            return false
        }
        ic.beginBatchEdit()
        try {
            runBlocking {
                val newSelection = EditorRange.cursor(composing.end + (text.length - content.composingText.length))
                val newContent = content.generateCopy(
                    selection = newSelection,
                    textBeforeSelection = buildString {
                        append(content.textBeforeSelection.removeSuffix(content.composingText))
                        append(text)
                    },
                    selectedText = "",
                )
                expectedContentQueue.push(newContent)
                ic.setComposingText(text, 1)
                ic.finishComposingText()
                _lastCommitPosition.handleCommit(newContent.selection)
            }
        } finally {
            ic.endBatchEdit()
        }
        return true
    }

    /**
     * DRS v1.17.0: deletes exactly ONE diacritic codepoint before the
     * cursor — the haraka-first backspace. Mirrors the rich-editor path
     * of [deleteAroundCursor] with a fixed single-codepoint length; raw
     * editors and empty text fall back to the full cluster delete.
     */
    protected suspend fun deleteSingleDiacriticBeforeCursor(): Boolean {
        val ic = currentInputConnection()
            ?: return deleteAroundCursor(OperationUnit.CHARACTERS, OperationScope.BEFORE_CURSOR, n = 1)
        val content = activeContent
        val scopeText = content.textBeforeSelection
        if (scopeText.isNullOrEmpty()) {
            return deleteAroundCursor(OperationUnit.CHARACTERS, OperationScope.BEFORE_CURSOR, n = 1)
        }
        // DRS p6 (E14) re-application: the cached content can lag the live
        // field. Re-check the LIVE last code point before blindly deleting
        // one unit: it must still be a combining mark and not a lone
        // surrogate half, or the safe cluster-delete path runs instead (no
        // surrogate bisection, no stale-position corruption).
        val liveBefore = runCatching { ic.getTextBeforeCursor(2, 0)?.toString() }.getOrNull()
        val liveLast = liveBefore?.lastOrNull()
        if (liveLast == null || Character.isSurrogate(liveLast) || !DrsHarakat.isCombiningMark(liveLast)) {
            return deleteAroundCursor(OperationUnit.CHARACTERS, OperationScope.BEFORE_CURSOR, n = 1)
        }
        val newContent = content.generateCopy(
            selection = content.selection.translatedBy(-1),
            textBeforeSelection = scopeText.dropLast(1),
        )
        expectedContentQueue.push(newContent)
        ic.beginBatchEdit()
        try {
            ic.finishComposingText()
            ic.deleteSurroundingText(1, 0)
            ic.setComposingRegion(newContent.composing)
        } finally {
            try {
                ic.endBatchEdit()
            } catch (_: Throwable) {
            }
        }
        return true
    }

    protected suspend fun deleteAroundCursor(unit: OperationUnit, scope: OperationScope, n: Int = 0): Boolean {
        val ic = currentInputConnection()
        if (ic == null || n < 1) return false
        val content = activeContent
        // Cannot perform below check due to editors which lie about their correct selection
        //if (content.selection.isValid && content.selection.start == 0) return true
        val scopeText = when (scope) {
            OperationScope.BEFORE_CURSOR -> content.textBeforeSelection
            OperationScope.AFTER_CURSOR -> content.textAfterSelection
        }
        return (if (activeInfo.isRawInputEditor || scopeText.isEmpty()) {
            // If editor is rich and text before selection is empty we seem to have an invalid state here, so we fall
            // back to emulating a hardware backspace/forward delete.
            val keyEventCode = when (scope) {
                OperationScope.BEFORE_CURSOR -> KeyEvent.KEYCODE_DEL
                OperationScope.AFTER_CURSOR -> KeyEvent.KEYCODE_FORWARD_DEL
            }
            val metaState = when (unit) {
                OperationUnit.CHARACTERS -> meta()
                OperationUnit.WORDS -> meta(ctrl = true)
            }
            sendDownUpKeyEvent(keyEventCode, metaState, count = n)
        } else {
            val locale = subtypeManager.activeSubtype.primaryLocale
            when (scope) {
                OperationScope.BEFORE_CURSOR -> {
                    val length = when (unit) {
                        OperationUnit.CHARACTERS -> breakIterators.measureLastUChars(scopeText, n, locale)
                        OperationUnit.WORDS -> breakIterators.measureLastUWords(scopeText, n, locale)
                    }
                    val selection = content.selection
                    val newSelection = selection.translatedBy(-length)
                    val newContent = content.generateCopy(
                        selection = newSelection,
                        textBeforeSelection = scopeText.dropLast(length),
                    )
                    expectedContentQueue.push(newContent)
                    // DRS v1.28.0 audit fix: exception-safe batch edit (see setSelection).
                    ic.beginBatchEdit()
                    try {
                        ic.finishComposingText()
                        ic.deleteSurroundingText(length, 0)
                        ic.setComposingRegion(newContent.composing)
                    } finally {
                        ic.endBatchEdit()
                    }
                }
                OperationScope.AFTER_CURSOR -> {
                    val length = when (unit) {
                        OperationUnit.CHARACTERS -> breakIterators.measureUChars(scopeText, n, locale)
                        OperationUnit.WORDS -> breakIterators.measureUWords(scopeText, n, locale)
                    }
                    val selection = content.selection
                    val newSelection = selection.translatedBy(length)
                    val newContent = content.generateCopy(
                        selection = newSelection,
                        textAfterSelection = scopeText.drop(length),
                    )
                    expectedContentQueue.push(newContent)
                    // DRS v1.28.0 audit fix: exception-safe batch edit (see setSelection).
                    ic.beginBatchEdit()
                    try {
                        ic.finishComposingText()
                        ic.deleteSurroundingText(0, length)
                        ic.setComposingRegion(newContent.composing)
                    } finally {
                        ic.endBatchEdit()
                    }
                }
            }
            true
        }).also {
            deleteMoveLastCommitPosition()
        }
    }

    fun refreshComposing() {
        val content = activeContent
        val ic = currentInputConnection()
        if (activeInfo.isRawInputEditor || ic == null) return
        runBlocking {
            val newContent = content.generateCopy()
            if (newContent.composing != content.composing) {
                expectedContentQueue.push(newContent)
                ic.setComposingRegion(newContent.composing)
            }
        }
    }

    /**
     * Gets [n] characters before the cursor's current position. The resulting string may be any
     * length ranging from 0 to n.
     *
     * @param n The number of characters to get before the cursor. Must be greater than 0 or this
     *  method will fail. This number indicates the number of Unicode chars, so the returned string
     *  length may be greater than n, due to Java char encoding.
     *
     * @return [n] or less characters before the cursor.
     */
    fun EditorContent.getTextBeforeCursor(n: Int): String {
        if (n < 1 || text.isEmpty()) return ""
        return runBlocking {
            val text = textBeforeSelection
            val length = breakIterators.measureLastUChars(text, n, subtypeManager.activeSubtype.primaryLocale)
            text.takeLast(length)
        }
    }

    /**
     * Gets [n] characters after the cursor's current position. The resulting string may be any
     * length ranging from 0 to n.
     *
     * @param n The number of characters to get after the cursor. Must be greater than 0 or this
     *  method will fail. This number indicates the number of Unicode chars, so the returned string
     *  length may be greater than n, due to Java char encoding.
     *
     * @return [n] or less characters after the cursor.
     */
    fun EditorContent.getTextAfterCursor(n: Int): String {
        if (n < 1 || text.isEmpty()) return ""
        return runBlocking {
            val text = textAfterSelection
            val length = breakIterators.measureUChars(text, n, subtypeManager.activeSubtype.primaryLocale)
            text.take(length)
        }
    }

    /**
     * Constructs a meta state integer flag which can be used for setting the `metaState` field when sending a KeyEvent
     * to the input connection. If this method is called without a meta modifier set to true, the default value `0` is
     * returned.
     *
     * @param ctrl Set to true to enable the CTRL meta modifier. Defaults to false.
     * @param alt Set to true to enable the ALT meta modifier. Defaults to false.
     * @param shift Set to true to enable the SHIFT meta modifier. Defaults to false.
     *
     * @return An integer containing all meta flags passed and formatted for use in a [KeyEvent].
     */
    fun meta(
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
    ): Int {
        var metaState = 0
        if (ctrl) {
            metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
        if (alt) {
            metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        if (shift) {
            metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }
        return metaState
    }

    private fun InputConnection.sendDownKeyEvent(eventTime: Long, keyEventCode: Int, metaState: Int, repeat: Int = 0): Boolean {
        return this.sendKeyEvent(
            KeyEvent(
                eventTime,
                eventTime,
                KeyEvent.ACTION_DOWN,
                keyEventCode,
                repeat,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD,
            )
        )
    }

    private fun InputConnection.sendUpKeyEvent(eventTime: Long, keyEventCode: Int, metaState: Int): Boolean {
        return this.sendKeyEvent(
            KeyEvent(
                eventTime,
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD,
            )
        )
    }

    /**
     * Same as [InputMethodService.sendDownUpKeyEvents] but also allows to set meta state.
     *
     * @param keyEventCode The key code to send, use a key code defined in Android's [KeyEvent].
     * @param metaState Flags indicating which meta keys are currently pressed.
     * @param count How often the key is pressed while the meta keys passed are down. Must be greater than or equal to
     *  `1`, else this method will immediately return false.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun sendDownUpKeyEvent(keyEventCode: Int, metaState: Int = meta(), count: Int = 1): Boolean {
        if (count < 1) return false
        val ic = currentInputConnection() ?: return false
        // DRS p6 (E5): the last unprotected beginBatchEdit() — an exception
        // thrown mid-dispatch (DeadObjectException, host crash) used to leak
        // the batch reference and leave the host editor stuck in batch mode
        // (deferred updates, broken undo) until the input restarted.
        // Exception-safe like every other batch site now.
        ic.beginBatchEdit()
        try {
            val eventTime = SystemClock.uptimeMillis()
            if (metaState and KeyEvent.META_CTRL_ON != 0) {
                ic.sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT, 0)
            }
            if (metaState and KeyEvent.META_ALT_ON != 0) {
                ic.sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT, 0)
            }
            if (metaState and KeyEvent.META_SHIFT_ON != 0) {
                ic.sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT, 0)
            }
            for (n in 0 until count) {
                ic.sendDownKeyEvent(eventTime, keyEventCode, metaState, n)
            }
            ic.sendUpKeyEvent(eventTime, keyEventCode, metaState)
            if (metaState and KeyEvent.META_SHIFT_ON != 0) {
                ic.sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT, 0)
            }
            if (metaState and KeyEvent.META_ALT_ON != 0) {
                ic.sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT, 0)
            }
            if (metaState and KeyEvent.META_CTRL_ON != 0) {
                ic.sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT, 0)
            }
        } finally {
            ic.endBatchEdit()
        }
        return true
    }

    /**
     * DRS (r0-D perf): monitor-locked expected-content queue with strictly
     * NON-suspending operations. The old guardedByLock/Mutex version forced
     * every caller (keystroke-path commits, selection updates, resets) into
     * runBlocking; the sections here hold no suspension points, so a plain
     * monitor lock is both correct and hop-free for all callers.
     *
     * Invariant: the [popUntilOrNull] predicate must stay pure (no
     * suspension, no lock acquisition) while the monitor is held.
     */
    private class ExpectedContentQueue {
        private val list = mutableListOf<EditorContent>()

        fun popUntilOrNull(predicate: (EditorContent) -> Boolean): EditorContent? {
            synchronized(this) {
                while (list.isNotEmpty()) {
                    val item = list.removeAt(0)
                    if (predicate(item)) return item
                }
                return null
            }
        }

        fun push(item: EditorContent) {
            synchronized(this) {
                list.add(item)
            }
        }

        fun peekNewestOrNull(): EditorContent? {
            synchronized(this) {
                return list.lastOrNull()
            }
        }

        fun clear() {
            synchronized(this) {
                list.clear()
            }
        }
    }

    fun updateLastCommitPosition() = _lastCommitPosition.handleCommit(activeContent.selection)
    fun deleteMoveLastCommitPosition() = _lastCommitPosition.handleDelete(activeContent.selection)

    /**
     * Class for handling history of last commit position.
     */
    data class LastCommitPosition(var pos: Int = -1) {

        constructor(other: LastCommitPosition): this(other.pos)

        fun reset() {
            pos = -1
        }

        fun handleCommit(selection: EditorRange) {
            if (selection.isValid) {
                pos = max(selection.start, selection.end)
            } else {
                reset()
            }
        }

        fun handleUpdateSelection(selection: EditorRange) {
            val start = min(selection.start, selection.end)
            if (start < pos) {
                reset()
            }
        }

        fun handleDelete(selection: EditorRange) {
            val start = min(selection.start, selection.end)
            if (start < pos) {
                pos = start
            }
        }
    }
}
