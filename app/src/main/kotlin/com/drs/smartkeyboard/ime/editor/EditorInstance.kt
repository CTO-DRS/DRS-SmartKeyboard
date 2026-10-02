/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.editor

import com.drs.smartkeyboard.drs.privacy.DrsFieldSecurity
import android.content.ClipDescription
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.drs.smartkeyboard.DrsImeService
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.drs.DrsRuntimeState
import com.drs.smartkeyboard.drs.HarakatSmartInsert
import com.drs.smartkeyboard.drs.DrsTextTool
import com.drs.smartkeyboard.drs.DrsTextTools
import com.drs.smartkeyboard.appContext
import com.drs.smartkeyboard.clipboardManager
import com.drs.smartkeyboard.ime.ImeUiMode
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardFileStorage
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardItem
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import com.drs.smartkeyboard.ime.input.InputShiftState
import com.drs.smartkeyboard.ime.keyboard.IncognitoMode
import com.drs.smartkeyboard.ime.keyboard.KeyboardMode
import com.drs.smartkeyboard.ime.nlp.SuggestionCandidate
import com.drs.smartkeyboard.ime.text.composing.Appender
import com.drs.smartkeyboard.ime.text.composing.Composer
import com.drs.smartkeyboard.ime.text.key.KeyVariation
import com.drs.smartkeyboard.keyboardManager
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import com.drs.smartkeyboard.nlpManager
import com.drs.smartkeyboard.subtypeManager
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.drs.lib.android.showShortToastSync

class EditorInstance(context: Context) : AbstractEditorInstance(context) {
    companion object {
        private const val SPACE = " "
    }

    private val prefs by DrsPreferenceStore
    private val appContext by context.appContext()
    private val clipboardManager by context.clipboardManager()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()
    private val nlpManager by context.nlpManager()

    private val activeState get() = keyboardManager.activeState
    val autoSpace = AutoSpaceState()
    val phantomSpace = PhantomSpaceState()
    val massSelection = MassSelectionState()

    private fun currentInputConnection() = DrsImeService.currentInputConnection()

    override fun handleStartInputView(editorInfo: DrsEditorInfo, isRestart: Boolean) {
        if (!prefs.correction.rememberCapsLockState.get()) {
            activeState.inputShiftState = InputShiftState.UNSHIFTED
        }
        activeState.isActionsOverflowVisible = false
        activeState.isActionsEditorVisible = false
        // DRS v1.8.0: a fresh input view never opens behind the tools drawer.
        activeState.isToolsDrawerVisible = false
        // DRS v1.16.0: ...nor behind an open strip slot editor.
        DrsRuntimeState.closeStripSlotEditor()
        super.handleStartInputView(editorInfo, isRestart)
        val keyboardMode = when (editorInfo.inputAttributes.type) {
            InputAttributes.Type.NUMBER -> {
                activeState.keyVariation = KeyVariation.NORMAL
                KeyboardMode.NUMERIC
            }
            InputAttributes.Type.PHONE -> {
                activeState.keyVariation = KeyVariation.NORMAL
                KeyboardMode.PHONE
            }
            // DRS v1.28.0 audit fix (Medium): TYPE_CLASS_DATETIME fields used to
            // fall into the generic `else` branch and open the alphabetic
            // keyboard, trapping the user without any letter escape. Digits are
            // the primary alphabet of date/time inputs (e.g. datetime-local),
            // so they now open the numeric pad like NUMBER fields do.
            InputAttributes.Type.DATETIME -> {
                activeState.keyVariation = KeyVariation.NORMAL
                KeyboardMode.NUMERIC
            }
            InputAttributes.Type.TEXT -> {
                activeState.keyVariation = when (editorInfo.inputAttributes.variation) {
                    InputAttributes.Variation.EMAIL_ADDRESS,
                    InputAttributes.Variation.WEB_EMAIL_ADDRESS,
                    -> {
                        KeyVariation.EMAIL_ADDRESS
                    }
                    InputAttributes.Variation.PASSWORD,
                    InputAttributes.Variation.VISIBLE_PASSWORD,
                    InputAttributes.Variation.WEB_PASSWORD,
                    -> {
                        KeyVariation.PASSWORD
                    }
                    InputAttributes.Variation.URI -> {
                        KeyVariation.URI
                    }
                    else -> {
                        KeyVariation.NORMAL
                    }
                }
                KeyboardMode.CHARACTERS
            }
            else -> {
                activeState.keyVariation = KeyVariation.NORMAL
                KeyboardMode.CHARACTERS
            }
        }
        activeState.keyboardMode = keyboardMode
        activeState.isComposingEnabled = when (keyboardMode) {
            KeyboardMode.NUMERIC,
            KeyboardMode.PHONE,
            KeyboardMode.PHONE2,
            -> false
            else -> activeState.keyVariation != KeyVariation.PASSWORD &&
                prefs.suggestion.enabled.get() &&
                // DRS p6 (E6): the NO_SUGGESTIONS and AUTO_COMPLETE ("field
                // does its own filtering") input-type flags are honored
                // again — they used to be commented out, so fields that
                // explicitly opted out of suggestions still got a composing
                // region, a candidate row and auto-commits.
                !editorInfo.inputAttributes.flagTextAutoComplete &&
                !editorInfo.inputAttributes.flagTextNoSuggestions
        }
        activeState.isIncognitoMode = when (prefs.suggestion.incognitoMode.get()) {
            IncognitoMode.FORCE_OFF -> false
            IncognitoMode.FORCE_ON -> true
            IncognitoMode.DYNAMIC_ON_OFF -> {
                // DRS v1.20.0: password fields force no-learning even when the editor
                // does not advertise IME_FLAG_NO_PERSONALIZED_LEARNING — Gboard/SwiftKey
                // treat every password field as private, and so does the indicator that
                // users rely on to trust the keyboard.
                editorInfo.imeOptions.flagNoPersonalizedLearning ||
                    activeState.keyVariation == KeyVariation.PASSWORD ||
                    prefs.suggestion.forceIncognitoModeFromDynamic.get()
            }
        }
        // DRS M3.6 — field security: the classification runs on the
        // ABSTRACTED facts (mapped here from the InputAttributes boundary —
        // no android.* types reach the pure classifier). The effective level
        // honors the user's FORCE_OFF gate but the password line stays
        // BLOCKED: protection opt-out never re-enables password learning.
        // (Honest note: TYPE_TEXT_VARIATION_OTP does not exist in the real
        // android.jar — no OTP class is invented here.)
        val fieldFacts = DrsFieldSecurity.FieldFacts(
            isPasswordKind = activeState.keyVariation == KeyVariation.PASSWORD,
            isEmailAddress = activeState.keyVariation == KeyVariation.EMAIL_ADDRESS,
            isUri = activeState.keyVariation == KeyVariation.URI,
            isAutoCompleteBlocked =
                editorInfo.inputAttributes.flagTextAutoComplete ||
                editorInfo.inputAttributes.flagTextNoSuggestions,
        )
        val fieldLevel = DrsFieldSecurity.effective(
            classified = DrsFieldSecurity.classify(fieldFacts),
            gate = if (prefs.suggestion.incognitoMode.get() == IncognitoMode.FORCE_OFF) {
                DrsFieldSecurity.UserGate.FEATURE_OFF
            } else {
                DrsFieldSecurity.UserGate.STANDARD
            },
        )
        if (DrsFieldSecurity.suppressesLearning(fieldLevel)) {
            activeState.isIncognitoMode = true
        }
        // DRS: derive the adaptive context mode (normal/chat/numbers/password/...)
        DrsRuntimeState.onInputStarted(editorInfo)
    }

    override fun handleSelectionUpdate(oldSelection: EditorRange, newSelection: EditorRange, composing: EditorRange) {
        autoSpace.setInactiveFromUpdate()
        phantomSpace.setInactiveFromUpdate()
        if (massSelection.isActive) {
            super.handleMassSelectionUpdate(newSelection, composing)
        } else {
            super.handleSelectionUpdate(oldSelection, newSelection, composing)
        }
    }

    override fun determineComposingEnabled(): Boolean {
        return activeState.isComposingEnabled && nlpManager.isSuggestionOn()
    }

    override fun determineComposer(composerName: ExtensionComponentName): Composer {
        return keyboardManager.resources.composers.value[composerName] ?: Appender
    }

    override fun shouldDetermineComposingRegion(editorInfo: DrsEditorInfo): Boolean {
        return super.shouldDetermineComposingRegion(editorInfo) &&
            (phantomSpace.isInactive || phantomSpace.showComposingRegion)
    }

    /**
     * Sets the selection of the input editor to the specified [start] and [end] values. This method does nothing if
     * the input connection is not valid or if the input editor is raw.
     *
     * @param start The start of the selection (inclusive). May be any value ranging from -1 to positive infinity.
     * @param end The end of the selection (exclusive). May be any value ranging from -1 to positive infinity.
     *
     * @return True on success or if the selection is already at specified position, false otherwise.
     */
    fun setSelection(start: Int, end: Int): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val selection = EditorRange.normalized(start, end)
        return super.setSelection(selection)
    }

    private fun shouldInsertAutoSpaceBefore(text: String): Boolean {
        if (!prefs.correction.autoSpacePunctuation.get() || text.isEmpty()) return false
        if (activeInfo.isRawInputEditor) return false
        if (activeState.keyVariation != KeyVariation.NORMAL) return false

        val punctuationRule = nlpManager.getActivePunctuationRule()
        val textBefore = activeContent.getTextBeforeCursor(1)
        return textBefore.isNotEmpty() && !textBefore.last().isWhitespace() &&
            punctuationRule.symbolsFollowingAutoSpace.contains(text.first())
    }

    private fun shouldInsertAutoSpaceAfter(text: String): Boolean {
        if (!prefs.correction.autoSpacePunctuation.get() || text.isEmpty()) return false
        if (activeInfo.isRawInputEditor) return false
        if (activeState.keyVariation != KeyVariation.NORMAL) return false

        val punctuationRule = nlpManager.getActivePunctuationRule()
        val content = activeContent
        val textBefore = content.getTextBeforeCursor(3).let { textBefore ->
            if (autoSpace.isActive && textBefore.isNotEmpty() && textBefore.last() == ' ') {
                textBefore.dropLast(1)
            } else {
                textBefore
            }
        }
        return textBefore.isNotEmpty() && !textBefore.last().isWhitespace() &&
            content.currentWordText.all { !it.isDigit() } &&
            punctuationRule.symbolsPrecedingAutoSpace.contains(text.first())
    }

    override fun commitChar(char: String): Boolean {
        val isInsertAutoSpaceBeforeChar = shouldInsertAutoSpaceBefore(char)
        val isInsertAutoSpaceAfterChar = shouldInsertAutoSpaceAfter(char)
        val isDeletePreviousSpace = isInsertAutoSpaceAfterChar && autoSpace.isActive
        if (isInsertAutoSpaceAfterChar) {
            autoSpace.setActive()
        } else {
            autoSpace.setInactive()
        }
        val isPhantomSpaceActive = phantomSpace.determine(char)
        phantomSpace.setInactive()
        return super.commitChar(
            char = char,
            deletePreviousSpace = isDeletePreviousSpace,
            insertSpaceBeforeChar = isInsertAutoSpaceBeforeChar || isPhantomSpaceActive,
            insertSpaceAfterChar = isInsertAutoSpaceAfterChar,
        )
    }

    /**
     * Commits the given [text] to this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * This method overwrites any selected text and replaces it with given [text]. If there is no
     * text selected (selection is in cursor mode), then this method will insert the [text] after
     * the cursor, then set the cursor position to the first character after the inserted text.
     *
     * @param text The text to commit.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    override fun commitText(text: String): Boolean {
        val isPhantomSpaceActive = phantomSpace.determine(text)
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (isPhantomSpaceActive) {
            super.commitText("$SPACE$text")
        } else {
            super.commitText(text)
        }
    }

    /**
     * Completes the given [candidate] in the current composing region. Does nothing if the current
     * input editor is not rich or if the input connection is invalid.
     *
     * Current phantom space state is respected and a space char will be inserted accordingly.
     * Phantom space will be activated if the text is committed.
     *
     * @param candidate The candidate to complete in this editor.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun commitCompletion(candidate: SuggestionCandidate): Boolean {
        val text = candidate.text.toString()
        if (text.isEmpty() || activeInfo.isRawInputEditor) return false
        val content = activeContent
        return if (content.composing.isValid) {
            phantomSpace.setActive(showComposingRegion = false, candidate = candidate)
            super.finalizeComposingText(text)
        } else {
            val isPhantomSpaceActive = phantomSpace.determine(text)
            phantomSpace.setActive(showComposingRegion = false, candidate = candidate)
            return if (isPhantomSpaceActive) {
                super.commitText("$SPACE$text")
            } else {
                super.commitText(text)
            }.also {
                // handled in finalizeComposingText if content.composing.isValid
                updateLastCommitPosition()
            }
        }
    }

    /**
     * Commit a word generated by a gesture.
     *
     * Ignores the current phantom space state and will insert a space depending on the character
     * before selection start. Phantom space will be activated if the text is committed.
     *
     * @param text The text to commit in this editor.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun commitGesture(text: String): Boolean {
        if (text.isEmpty() || activeInfo.isRawInputEditor) return false
        val isPhantomSpaceActive = phantomSpace.determine(text, forceActive = true)
        phantomSpace.setActive(showComposingRegion = true)
        return if (isPhantomSpaceActive) {
            super.commitText("$SPACE$text")
        } else {
            super.commitText(text)
        }.also {
            updateLastCommitPosition()
        }
    }

    /**
     * Commits the given [ClipboardItem]. If the clip data is text (incl. HTML), it delegates to [commitText].
     * If the item has a content URI (and the EditText supports it), the item is committed as rich data.
     * This allows for committing (e.g) images.
     *
     * @param item The ClipboardItem to commit
     *
     * @return True on success, false if something went wrong.
     */
    fun commitClipboardItem(item: ClipboardItem?): Boolean {
        if (item == null) return false
        val mimeTypes = item.mimeTypes
        return when (item.type) {
            ItemType.TEXT -> {
                commitText(item.text.toString()).also {
                    updateLastCommitPosition()
                }
            }
            ItemType.IMAGE, ItemType.VIDEO -> {
                item.uri ?: return false
                val id = ContentUris.parseId(item.uri)
                val file = ClipboardFileStorage.getFileForId(appContext, id)
                if (!file.exists()) return false
                val inputContentInfo = InputContentInfoCompat(
                    item.uri,
                    ClipDescription("clipboard media file", mimeTypes.toTypedArray()),
                    null,
                )
                val ic = currentInputConnection() ?: return false
                ic.finishComposingText()
                val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                InputConnectionCompat.commitContent(ic, activeInfo.base, inputContentInfo, flags, null)
            }
        }.also {
            if (prefs.clipboard.historyHideOnPaste.get()) {
                keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
            }
        }
    }

    /**
     * Executes a backward delete on this editor's text. If a text selection is active, all
     * characters inside this selection will be removed, else only the left-most character from
     * the cursor's position.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun deleteBackwards(unit: OperationUnit): Boolean {
        val content = activeContent
        if (unit == OperationUnit.CHARACTERS) {
            if (phantomSpace.isActive && content.currentWord.isValid && prefs.glide.immediateBackspaceDeletesWord.get()) {
                return deleteBackwards(OperationUnit.WORDS)
            }
        }
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (content.selection.isSelectionMode) {
            commitText("")
        } else if (unit == OperationUnit.CHARACTERS &&
            HarakatSmartInsert.shouldStripBeforeDelete(
                textBeforeCursor = content.textBeforeSelection.toString(),
                enabled = prefs.keyboard.backspaceStripsHarakat.get(),
            )
        ) {
            // DRS v1.17.0: haraka-first backspace — peel ONE diacritic off
            // the letter instead of deleting the whole grapheme cluster
            // (the helper lives in AbstractEditorInstance beside the
            // delete path it mirrors).
            runBlocking { deleteSingleDiacriticBeforeCursor() }
        } else runBlocking {
            deleteAroundCursor(unit, OperationScope.BEFORE_CURSOR, n = 1)
        }
    }

    /**
     * Executes a backward delete on this editor's text. If a text selection is active, all
     * characters inside this selection will be removed, else only the left-most character from
     * the cursor's position.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun deleteForwards(unit: OperationUnit): Boolean {
        val content = activeContent
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (content.selection.isSelectionMode) {
            commitText("")
        } else runBlocking {
            deleteAroundCursor(unit, OperationScope.AFTER_CURSOR, n = 1)
        }
    }

    fun setSelectionSurrounding(n: Int, unit: OperationUnit, scope: OperationScope): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val content = activeContent
        val selection = content.selection
        val safeEditorBounds = content.safeEditorBounds
        if (selection.isNotValid) return false
        when (scope) {
            OperationScope.BEFORE_CURSOR -> {
                if (n <= 0) {
                    return setSelection(selection.end, selection.end)
                }
                // DRS (H3): out-of-bounds no-op guard — a stale/lagging local
                // selection can point past the cached snapshot; the raw
                // substring below used to throw IndexOutOfBoundsException
                // straight into the key dispatch. No-op instead.
                val localEnd = content.localSelection.end
                if (localEnd < 0 || localEnd > content.text.length) return false
                val textToAnalyze = content.text.substring(0, localEnd)
                val length = runBlocking {
                    when (unit) {
                        OperationUnit.CHARACTERS -> breakIterators.measureLastUChars(textToAnalyze, n)
                        OperationUnit.WORDS -> breakIterators.measureLastUWords(textToAnalyze, n)
                    }
                }
                return setSelection((selection.end - length).coerceAtLeast(safeEditorBounds.start), selection.end)
            }
            OperationScope.AFTER_CURSOR -> {
                if (n <= 0) {
                    return setSelection(selection.start, selection.start)
                }
                // DRS (H3): out-of-bounds no-op guard (see BEFORE_CURSOR).
                val localStart = content.localSelection.start
                if (localStart < 0 || localStart > content.text.length) return false
                val textToAnalyze = content.text.substring(localStart)
                val length = runBlocking {
                    when (unit) {
                        OperationUnit.CHARACTERS -> breakIterators.measureUChars(textToAnalyze, n)
                        OperationUnit.WORDS -> breakIterators.measureUWords(textToAnalyze, n)
                    }
                }
                return setSelection(selection.start, (selection.start + length).coerceAtMost(safeEditorBounds.end))
            }
        }
    }

    /**
     * Performs a cut command on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardCut(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        // DRS p6 (E12) re-application: the LIVE selection is authoritative —
        // a cached selection can be stale after programmatic edits, and
        // cutting/copying stale text silently corrupts the clipboard.
        val text = currentInputConnection()?.getSelectedText(0)?.takeIf { it.isNotBlank() }
            ?: activeContent.selectedText.takeIf { it.isNotBlank() }
        if (text != null) {
            clipboardManager.addNewPlaintext(text.toString())
        } else {
            // DRS v1.28.0 audit fix (Medium): with no selection the old code
            // showed the "cut failed" toast and then STILL ran
            // deleteBackwards(), silently removing one character before the
            // cursor — one-character data loss from a plain cut tap. A failed
            // cut must not mutate the field at all.
            appContext.showShortToastSync(R.string.editor__cut_failed)
            return false
        }
        return deleteBackwards(OperationUnit.CHARACTERS)
    }

    /**
     * Performs a copy command on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardCopy(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        // DRS p6 (E12) re-application: the LIVE selection is authoritative —
        // a cached selection can be stale after programmatic edits, and
        // cutting/copying stale text silently corrupts the clipboard.
        val text = currentInputConnection()?.getSelectedText(0)?.takeIf { it.isNotBlank() }
            ?: activeContent.selectedText.takeIf { it.isNotBlank() }
        if (text != null) {
            clipboardManager.addNewPlaintext(text.toString())
        } else {
            appContext.showShortToastSync(R.string.editor__copy_failed)
        }
        val activeSelection = activeContent.selection
        return setSelection(activeSelection.end, activeSelection.end)
    }

    /**
     * Performs a paste command on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardPaste(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return commitClipboardItem(clipboardManager.primaryClip).also { result ->
            if (!result) {
                // DRS v1.19.0: the failure toast is localized now (was a
                // hardcoded English literal in every locale).
                appContext.showShortToastSync(R.string.clipboard__paste_failed)
            }
        }
    }

    /**
     * Performs a select all on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardSelectAll(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val ic = currentInputConnection() ?: return false
        ic.finishComposingText()
        return if (activeInfo.isRawInputEditor) {
            sendDownUpKeyEvent(KeyEvent.KEYCODE_A, meta(ctrl = true))
        } else {
            ic.performContextMenuAction(android.R.id.selectAll)
        }
    }

    /**
     * DRS v1.0.5: shares the selected text through the system share sheet.
     * When nothing is selected, the entire field content is shared instead.
     * Nothing is stored or transmitted by the keyboard itself — the user
     * explicitly picks the receiving app in the system chooser.
     *
     * @return True when a share intent was dispatched, false when there was
     *         no text to share or the input connection was unavailable.
     */
    fun performClipboardShare(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val selected = activeContent.selectedText.ifBlank { currentInputConnection()?.getSelectedText(0) }
        val text = (selected ?: activeContent.text.ifBlank { null })
            ?.toString()
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) {
            appContext.showShortToastSync(R.string.share__no_text)
            return false
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, appContext.getString(R.string.share__chooser_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            appContext.startActivity(chooser)
            true
        }.getOrElse { false }
    }

    /**
     * DRS v1.0.6: applies a technical text tool (see [DrsTextTool]) to the
     * current selection, or to the whole field when nothing is selected.
     * Runs inside a single input-connection batch edit so the host app sees
     * one atomic change and its own undo stack stays usable. Password fields
     * and incognito mode are refused - the tool surface must never be used to
     * bulk-rewrite credentials.
     *
     * @return True when the tool was applied (or, for the info tool, the
     *         counts were shown), false otherwise.
     */
    fun performTextTool(tool: DrsTextTool): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        if (activeInfo.isRawInputEditor || activeState.isIncognitoMode ||
            activeState.keyVariation == KeyVariation.PASSWORD
        ) {
            return false
        }
        val ic = currentInputConnection() ?: return false
        ic.beginBatchEdit()
        try {
            ic.finishComposingText()
            val extracted = try {
                ic.getExtractedText(ExtractedTextRequest(), 0)
            } catch (_: Throwable) {
                null
            }
            if (extracted?.text != null) {
                val full = extracted.text.toString()
                val start = extracted.selectionStart.coerceIn(0, full.length)
                val end = extracted.selectionEnd.coerceIn(0, full.length)
                return applyTextToolToRange(ic, tool, full, start, end)
            }
            // Fallback: the content window maintained by the IME itself.
            val content = activeContent
            val full = content.text.toString()
            val start = content.selection.start.coerceIn(0, full.length)
            val end = content.selection.end.coerceIn(0, full.length)
            return applyTextToolToRange(ic, tool, full, start, end)
        } catch (t: Throwable) {
            appContext.showShortToastSync(R.string.drs__text_tools__failed)
            runCatching {
                com.drs.smartkeyboard.drs.DrsEventLog.recordError(
                    com.drs.smartkeyboard.drs.DrsEventLog.Categories.TEXT_TOOLS,
                    com.drs.smartkeyboard.drs.DrsEventLog.throwableDetail(t),
                )
            }
            return false
        } finally {
            try {
                ic.endBatchEdit()
            } catch (_: Throwable) {
            }
        }
    }

    private fun applyTextToolToRange(
        ic: InputConnection,
        tool: DrsTextTool,
        full: String,
        selectionStart: Int,
        selectionEnd: Int,
    ): Boolean {
        // DRS (H3): clamp the tool range to the actual text bounds — stale
        // extracted-text or cached-selection data used to reach the raw
        // substring/commit path unchecked and throw deep inside a batch edit.
        val from = minOf(selectionStart, selectionEnd).coerceIn(0, full.length)
        val to = maxOf(selectionStart, selectionEnd).coerceIn(0, full.length)
        val hasSelection = from != to
        if (tool.isInsertMark) {
            // DRS v1.21.0: the invisible directional/joining marks commit
            // straight at the cursor — no text needed, nothing replaced.
            val mark = DrsTextTools.insertionMarkFor(tool) ?: return false
            val ok = ic.commitText(mark.toString(), 1)
            if (!ok) {
                appContext.showShortToastSync(R.string.drs__text_tools__failed)
            } else {
                // DRS p7 (F4): raw-IC commit succeeded — the expected-content
                // queue must not serve a pre-commit snapshot afterwards.
                onRawTextToolCommit()
            }
            return ok
        }
        if (tool.isEditorOp) {
            return performEditorLineOp(ic, tool, full, from, to)
        }
        val target = if (hasSelection) full.substring(from, to) else full
        if (tool.isInfoOnly) {
            val (chars, words, lines) = DrsTextTools.countInfo(target)
            appContext.showShortToastSync(
                R.string.drs__text_tools__count_result,
                "chars" to chars,
                "words" to words,
                "lines" to lines,
            )
            return true
        }
        if (target.isEmpty()) {
            appContext.showShortToastSync(R.string.drs__text_tools__no_text)
            return false
        }
        val transformed = DrsTextTools.apply(tool, target, Locale.getDefault())
        if (transformed == target) {
            appContext.showShortToastSync(R.string.drs__text_tools__no_change)
            return false
        }
        val ok = if (hasSelection) {
            ic.setSelection(from, to) && ic.commitText(transformed, 1)
        } else {
            ic.setSelection(0, full.length) && ic.commitText(transformed, 1)
        }
        if (!ok) {
            appContext.showShortToastSync(R.string.drs__text_tools__failed)
        } else {
            // DRS p7 (F4): raw-IC commit succeeded — see onRawTextToolCommit.
            onRawTextToolCommit()
        }
        return ok
    }

    /**
     * DRS p7 (F4): called after each SUCCESSFUL raw input-connection commit
     * performed by the text-tool paths (mark insert, transform, line op).
     * Those commits bypass the expected-content queue, so a stale queue entry
     * whose (selection, composing) coincides with the post-tool selection
     * would be adopted by the next selection update and serve a pre-transform
     * content snapshot to the suggestion/shift-state pipeline. Invalidate the
     * queue and reevaluate the shift state immediately — ordered AFTER the
     * commitText and BEFORE any deferred host selection update can pop the
     * queue (runs inside performTextTool's batch; host selection updates are
     * deferred until its endBatchEdit).
     */
    private fun onRawTextToolCommit() {
        invalidateExpectedContent()
        keyboardManager.reevaluateInputShiftState()
    }

    /**
     * DRS v1.1.0: لوحة الحركات الذكية — replaces the Arabic word ending at
     * the cursor (the trailing [wordLength] characters before the cursor,
     * marks included) with [transformed], in one atomic batch edit the host
     * app sees as a single change. Used by the smart harakat board's word
     * actions (تشكيل الكلمة / نزع التشكيل) whose decision comes from the
     * pure [com.drs.smartkeyboard.drs.DrsWordTashkeel] layer. Raw input
     * editors, incognito mode and password fields are refused — the word
     * surface must never rewrite credentials.
     *
     * @return True when the word was replaced, false otherwise (no cursor
     *         word, refused context, or input-connection error).
     */
    fun replaceWordBeforeCursor(transformed: String, wordLength: Int): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        if (transformed.isEmpty() || wordLength <= 0) return false
        if (activeInfo.isRawInputEditor || activeState.isIncognitoMode ||
            activeState.keyVariation == KeyVariation.PASSWORD
        ) {
            return false
        }
        val ic = currentInputConnection() ?: return false
        ic.beginBatchEdit()
        try {
            ic.finishComposingText()
            val extracted = try {
                ic.getExtractedText(ExtractedTextRequest(), 0)
            } catch (_: Throwable) {
                null
            }
            val full: String
            val cursor: Int
            if (extracted?.text != null) {
                full = extracted.text.toString()
                cursor = extracted.selectionEnd.coerceIn(0, full.length)
            } else {
                // Fallback: the content window maintained by the IME itself.
                val content = activeContent
                full = content.text.toString()
                cursor = content.selection.end.coerceIn(0, full.length)
            }
            val to = cursor
            val from = (to - wordLength).coerceIn(0, to)
            if (from >= to) return false
            val ok = ic.setSelection(from, to) && ic.commitText(transformed, 1)
            if (!ok) {
                appContext.showShortToastSync(R.string.drs__text_tools__failed)
            } else {
                // Raw-IC commit succeeded — same queue-invalidation contract
                // as the text tools (DRS p7 (F4)).
                onRawTextToolCommit()
            }
            return ok
        } finally {
            try {
                ic.endBatchEdit()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Cursor-relative line operations: delete the whole (selected) line(s),
     * or delete from the cursor to the start/end of the line. Implemented on
     * the real input connection so behavior matches the actual field content.
     */
    private fun performEditorLineOp(
        ic: InputConnection,
        tool: DrsTextTool,
        full: String,
        from: Int,
        to: Int,
    ): Boolean {
        if (full.isEmpty()) {
            appContext.showShortToastSync(R.string.drs__text_tools__no_text)
            return false
        }
        val lineStart = full.lastIndexOf('\n', (from - 1).coerceAtLeast(0)).let {
            if (it == -1 || from == 0) 0 else it + 1
        }
        val lineEnd = full.indexOf('\n', to).let { if (it == -1) full.length else it }
        val rangeStart: Int
        val rangeEnd: Int
        when (tool) {
            DrsTextTool.DELETE_LINE -> {
                // Include the trailing newline so no empty line is left over.
                rangeStart = lineStart
                rangeEnd = if (lineEnd < full.length) lineEnd + 1 else lineEnd
            }
            DrsTextTool.DELETE_TO_LINE_START -> {
                rangeStart = lineStart
                rangeEnd = to
            }
            DrsTextTool.DELETE_TO_LINE_END -> {
                rangeStart = from
                rangeEnd = lineEnd
            }
            else -> return false
        }
        if (rangeStart >= rangeEnd) {
            appContext.showShortToastSync(R.string.drs__text_tools__no_change)
            return false
        }
        val ok = ic.setSelection(rangeStart, rangeEnd) && ic.commitText("", 1)
        if (!ok) {
            appContext.showShortToastSync(R.string.drs__text_tools__failed)
        } else {
            // DRS p7 (F4): raw-IC commit succeeded — see onRawTextToolCommit.
            onRawTextToolCommit()
        }
        return ok
    }

    /**
     * Performs an enter key press on the current input editor.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performEnter(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (activeInfo.isRawInputEditor) {
            sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER)
        } else {
            commitText("\n")
        }
    }

    fun tryPerformEnterCommitRaw(): Boolean {
        return if (subtypeManager.activeSubtype.primaryLocale.language.startsWith("zh") && activeContent.composing.length > 0) {
            finalizeComposingText(activeContent.composingText)
        } else {
            false
        }
    }

    /**
     * Performs a given [action] on the current input editor.
     *
     * @param action The action to be performed on this editor instance.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performEnterAction(action: ImeOptions.Action): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val ic = currentInputConnection() ?: return false
        return ic.performEditorAction(action.toInt())
    }

    /**
     * Undoes the last action.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performUndo(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return sendDownUpKeyEvent(KeyEvent.KEYCODE_Z, meta(ctrl = true))
    }

    /**
     * Redoes the last Undo action.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performRedo(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return sendDownUpKeyEvent(KeyEvent.KEYCODE_Z, meta(ctrl = true, shift = true))
    }

    override fun reset() {
        super.reset()
        autoSpace.setInactive()
        phantomSpace.setInactive()
        massSelection.reset()
    }

    private fun PhantomSpaceState.determine(text: String, forceActive: Boolean = false): Boolean {
         val content = activeContent
         val selection = content.selection
         if (!(isActive || forceActive) || selection.isNotValid || selection.start <= 0 || text.isEmpty()) return false
         val textBefore = content.getTextBeforeCursor(1)
         val punctuationRule = nlpManager.getActivePunctuationRule()
         if (!subtypeManager.activeSubtype.primaryLocale.supportsAutoSpace) return false;
         return textBefore.isNotEmpty() &&
             (punctuationRule.symbolsPrecedingPhantomSpace.contains(textBefore[textBefore.length - 1]) ||
                 textBefore[textBefore.length - 1].isLetterOrDigit()) &&
             (punctuationRule.symbolsFollowingPhantomSpace.contains(text[0]) || text[0].isLetterOrDigit())
    }

    class AutoSpaceState {
        companion object {
            private const val F_IS_ACTIVE = 0x1
            private const val F_STAY_ACTIVE_NEXT_UPDATE = 0x4
        }

        private val state = AtomicInteger(0)

        val isActive: Boolean
            get() = state.get() and F_IS_ACTIVE != 0

        val isInactive: Boolean
            get() = !isActive

        fun setActive(stayActiveNextUpdate: Boolean = true) {
            state.set(F_IS_ACTIVE or (if (stayActiveNextUpdate) F_STAY_ACTIVE_NEXT_UPDATE else 0))
        }

        fun setInactive() {
            state.set(0)
        }

        fun setInactiveFromUpdate() {
            state.updateAndGet { state ->
                if ((state and F_STAY_ACTIVE_NEXT_UPDATE) != 0) (state and F_STAY_ACTIVE_NEXT_UPDATE.inv()) else 0
            }
        }
    }

    class PhantomSpaceState {
        companion object {
            private const val F_IS_ACTIVE = 0x1
            private const val F_SHOW_COMPOSING_REGION = 0x2
            private const val F_STAY_ACTIVE_NEXT_UPDATE = 0x4
        }

        private val state = AtomicInteger(0)
        var candidateForRevert: SuggestionCandidate? = null
            private set

        val isActive: Boolean
            get() = state.get() and F_IS_ACTIVE != 0

        val isInactive: Boolean
            get() = !isActive

        val showComposingRegion: Boolean
            get() = state.get() and F_SHOW_COMPOSING_REGION != 0

        fun setActive(
            showComposingRegion: Boolean,
            stayActiveNextUpdate: Boolean = true,
            candidate: SuggestionCandidate? = null,
        ) {
            state.set(
                F_IS_ACTIVE
                    or (if (showComposingRegion) F_SHOW_COMPOSING_REGION else 0)
                    or (if (stayActiveNextUpdate) F_STAY_ACTIVE_NEXT_UPDATE else 0)
            )
            candidateForRevert = candidate
        }

        fun setInactive() {
            state.set(0)
            candidateForRevert = null
        }

        fun setInactiveFromUpdate() {
            val prevStateValue = state.getAndUpdate { state ->
                if ((state and F_STAY_ACTIVE_NEXT_UPDATE) != 0) (state and F_STAY_ACTIVE_NEXT_UPDATE.inv()) else 0
            }
            if ((prevStateValue and F_STAY_ACTIVE_NEXT_UPDATE) == 0) {
                candidateForRevert = null
            }
        }
    }

    inner class MassSelectionState {
        private val state = AtomicInteger(0)

        val isActive: Boolean
            get() = state.get() > 0

        val isInactive: Boolean
            get() = !isActive

        fun begin() {
            state.incrementAndGet()
        }

        fun end() {
            if (state.decrementAndGet() == 0) {
                // We need to emulate a selection update to update the content if mass selection has ended
                handleSelectionUpdate(EditorRange.Unspecified, activeContent.selection, EditorRange.Unspecified)
            }
        }

        fun reset() {
            state.set(0)
        }
    }
}
