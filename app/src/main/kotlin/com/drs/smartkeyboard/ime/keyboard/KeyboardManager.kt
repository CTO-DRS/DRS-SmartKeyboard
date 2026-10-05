/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.keyboard

import com.drs.smartkeyboard.drs.DrsRuntimeState
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.icu.lang.UCharacter
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.drs.smartkeyboard.DrsImeService
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.appContext
import com.drs.smartkeyboard.clipboardManager
import com.drs.smartkeyboard.drs.DrsAdaptationEngine
import com.drs.smartkeyboard.drs.DrsContextMode
import com.drs.smartkeyboard.drs.DrsEconomy
import com.drs.smartkeyboard.drs.DrsIntegration
import com.drs.smartkeyboard.drs.DrsPerformance
import com.drs.smartkeyboard.drs.DrsTextTool
import com.drs.smartkeyboard.editorInstance
import com.drs.smartkeyboard.extensionManager
import com.drs.smartkeyboard.ime.ImeUiMode
import com.drs.smartkeyboard.ime.core.DisplayLanguageNamesIn
import com.drs.smartkeyboard.ime.core.Subtype
import com.drs.smartkeyboard.ime.core.SubtypePreset
import com.drs.smartkeyboard.ime.editor.EditorContent
import com.drs.smartkeyboard.ime.editor.DrsEditorInfo
import com.drs.smartkeyboard.ime.editor.ImeOptions
import com.drs.smartkeyboard.ime.editor.InputAttributes
import com.drs.smartkeyboard.ime.editor.OperationUnit
import com.drs.smartkeyboard.ime.input.CapitalizationBehavior
import com.drs.smartkeyboard.ime.input.InputEventDispatcher
import com.drs.smartkeyboard.ime.input.InputKeyEventReceiver
import com.drs.smartkeyboard.ime.input.InputShiftState
import com.drs.smartkeyboard.ime.input.cycleModifierLatch
import com.drs.smartkeyboard.ime.input.fnFunctionKeyCodeOf
import com.drs.smartkeyboard.ime.input.fnSurvivesKey
import com.drs.smartkeyboard.ime.nlp.ClipboardSuggestionCandidate
import com.drs.smartkeyboard.ime.nlp.DrsCorrectionRevert
import com.drs.smartkeyboard.ime.nlp.DrsSmartPunctuation
import com.drs.smartkeyboard.ime.nlp.PunctuationRule
import com.drs.smartkeyboard.ime.nlp.SuggestionCandidate
import com.drs.smartkeyboard.ime.popup.PopupMappingComponent
import com.drs.smartkeyboard.ime.smartbar.quickaction.SmartToolCodes
import com.drs.smartkeyboard.ime.text.composing.Composer
import com.drs.smartkeyboard.ime.text.gestures.SwipeAction
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.key.KeyType
import com.drs.smartkeyboard.ime.text.key.UtilityKeyAction
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyboardCache
import com.drs.smartkeyboard.drs.privacy.DrsPrivacyLock
import com.drs.smartkeyboard.ime.voice.DrsVoiceInputBus
import com.drs.smartkeyboard.ime.voice.DrsVoiceInputController
import com.drs.smartkeyboard.ime.voice.VoiceInputRoute
import com.drs.smartkeyboard.ime.voice.VoicePermissionActivity
import com.drs.smartkeyboard.ime.voice.VoiceRecognizerMode
import com.drs.smartkeyboard.ime.voice.decideVoiceInputRoute
import com.drs.smartkeyboard.lib.devtools.LogTopic
import com.drs.smartkeyboard.lib.devtools.flogError
import com.drs.smartkeyboard.lib.devtools.flogInfo
import com.drs.smartkeyboard.drs.ai.DrsVoiceCommands
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import com.drs.smartkeyboard.lib.titlecase
import com.drs.smartkeyboard.lib.uppercase
import com.drs.smartkeyboard.lib.util.InputMethodUtils
import com.drs.smartkeyboard.nlpManager
import com.drs.smartkeyboard.subtypeManager
import com.drs.smartkeyboard.themeManager
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drs.lib.android.AndroidKeyguardManager
import org.drs.lib.android.AndroidVersion
import org.drs.lib.android.showLongToast
import org.drs.lib.android.showLongToastSync
import org.drs.lib.android.showShortToastSync
import org.drs.lib.android.systemService
import org.drs.lib.kotlin.collectIn
import org.drs.lib.kotlin.collectLatestIn

private val DoubleSpacePeriodMatcher = """([^.!?‽\s]\s)""".toRegex()

class KeyboardManager(context: Context) : InputKeyEventReceiver {
    private val prefs by DrsPreferenceStore
    private val appContext by context.appContext()
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val extensionManager by context.extensionManager()
    private val nlpManager by context.nlpManager()
    private val subtypeManager by context.subtypeManager()
    // DRS v1.0.8: theme cycling from the unified strip needs the theme
    // index + the theme prefs (see handleThemeCycle / ThemeManager.cycleTheme).
    private val themeManager by context.themeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val layoutManager = LayoutManager(context)
    private val keyboardCache = TextKeyboardCache()

    // DRS v1.23.0: the built-in voice dictation controller — created on
    // first mic press (lazy), commits transcripts at the cursor, torn
    // down with the service. See DrsVoiceInput.kt for the full contract.
    // DRS (M1): this used to be a `lazy` singleton on this app-scoped
    // manager — destroy() cancelled the controller's collector scope, so
    // after the first IME service recreation every later mic press hit a
    // dead controller. The instance is now a @Volatile nullable field
    // created double-checked on demand; destroy() nulls it so the next
    // mic press re-creates a fresh, live controller.
    @Volatile
    private var voiceControllerInstance: DrsVoiceInputController? = null

    val voiceController: DrsVoiceInputController
        get() {
            voiceControllerInstance?.let { return it }
            return synchronized(this) {
                voiceControllerInstance ?: DrsVoiceInputController(
                    appContext,
                    onCommit = { text -> editorInstance.commitText(text) },
                    // DRS v1.25.0: read lazily at every start so a settings
                    // change between sessions is honored without recreating
                    // the controller.
                    recognizerMode = { prefs.voice.recognizerMode.get() },
                    // DRS Phase 2 (roadmap task 12): continuous dictation +
                    // spoken edit commands — both read/execute lazily at
                    // every result.
                    continuous = { prefs.voice.continuousDictation.get() },
                    onCommand = { command -> executeVoiceCommand(command) },
                ).also { voiceControllerInstance = it }
            }
        }

    /** DRS v1.23.0: the keyboard window hid — a live session must die. */
    fun stopVoiceInput() {
        // DRS (M1): nullable read — a mere stop must not create a controller.
        voiceControllerInstance?.stop()
    }

    /** DRS v1.23.0: the service is going away — full teardown. */
    fun destroyVoiceInput() {
        // DRS (M1): null the reference so the next mic press re-creates the
        // controller instead of reusing the destroyed one.
        voiceControllerInstance?.destroy()
        voiceControllerInstance = null
    }

    /**
     * DRS Phase 2 (roadmap task 12): the spoken EDIT-command executor —
     * the keyboard-side half of the voice command pipeline. Runs on the
     * main thread (the recognizer's result path); every command maps to
     * an existing editor operation, so no new editing semantics are
     * invented here:
     *  - punctuation/newline → ordinary commitText;
     *  - DELETE_LAST_WORD / DELETE_LAST_SENTENCE → a computed KEYCODE_DEL
     *    burst (count derived from the live editor content — trailing
     *    spaces included, hard-capped);
     *  - UNDO_LAST → the existing performUndo;
     *  - STOP / CLEAR_ALL → STOP is handled by the controller itself
     *    (session terminates); CLEAR_ALL selects-all-then-deletes via a
     *    KEYCODE_MOVE_END + select-all emulation — kept to a bounded
     *    KEYCODE burst, never a blind destructive shortcut.
     */
    private fun executeVoiceCommand(command: DrsVoiceCommands.Command) {
        flogInfo { "executing voice command=$command" }
        when (command) {
            DrsVoiceCommands.Command.NEW_LINE -> editorInstance.commitText("\n")
            DrsVoiceCommands.Command.PERIOD -> editorInstance.commitText(".")
            DrsVoiceCommands.Command.COMMA -> editorInstance.commitText("\u060C")
            DrsVoiceCommands.Command.QUESTION -> editorInstance.commitText("\u061F")
            DrsVoiceCommands.Command.EXCLAMATION -> editorInstance.commitText("!")
            DrsVoiceCommands.Command.DELETE_LAST_WORD -> {
                val count = trailingCharsForDelete(wordUnit = true)
                if (count > 0) editorInstance.sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL, count = count)
            }
            DrsVoiceCommands.Command.DELETE_LAST_SENTENCE -> {
                val count = trailingCharsForDelete(wordUnit = false)
                if (count > 0) editorInstance.sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL, count = count)
            }
            DrsVoiceCommands.Command.UNDO_LAST -> editorInstance.performUndo()
            DrsVoiceCommands.Command.CLEAR_ALL -> {
                // Select all + delete through the editor's own selection
                // machinery (bounded, reversible via UNDO) — never a raw
                // destructive shortcut.
                val content = editorInstance.activeContentFlow.value
                val len = content.text.length
                if (len > 0) {
                    editorInstance.setSelection(0, len)
                    editorInstance.commitText("")
                }
            }
            DrsVoiceCommands.Command.STOP -> Unit // the controller terminated the session itself
        }
    }

    /**
     * DRS Phase 2 (task 12): computes how many characters sit before the
     * cursor for a DELETE_LAST_WORD (trailing whitespace + last word) or
     * DELETE_LAST_SENTENCE (back to — and including — the last sentence
     * terminator) burst, from the live editor content. Hard-capped so a
     * pathological field can never trigger an unbounded delete.
     */
    private fun trailingCharsForDelete(wordUnit: Boolean): Int {
        val content = editorInstance.activeContentFlow.value
        val text = content.textBeforeSelection.toString()
        if (text.isEmpty()) return 0
        var end = text.length
        val hardCap = 120
        if (wordUnit) {
            while (end > 0 && text[end - 1].isWhitespace()) end--
            var start = end
            while (start > 0 && !text[start - 1].isWhitespace()) start--
            return (end - start).coerceIn(1, hardCap)
        }
        // Sentence unit: strip trailing whitespace, then stop AFTER the
        // last sentence terminator (inclusive) if one exists within reach.
        while (end > 0 && text[end - 1].isWhitespace()) end--
        val terminators = charArrayOf('.', '!', '?', '؟', '\n', '…', '۔')
        var idx = end - 1
        while (idx >= 0 && text[idx] !in terminators) idx--
        return if (idx >= 0) {
            (end - idx).coerceIn(1, hardCap)
        } else {
            end.coerceAtMost(hardCap)
        }
    }

    val resources = KeyboardManagerResources()
    val activeState = ObservableKeyboardState.new()
    var smartbarVisibleDynamicActionsCount by mutableIntStateOf(0)
    private var lastToastReference = WeakReference<Toast>(null)

    // DRS p7 (E2-8): armed while executeSwipeAction dispatches a synthetic
    // SHIFT via sendDownUp; makes handleShiftDown skip the double-tap
    // caps-lock arming for swipe-shifts. @Volatile: armed on the caller's
    // thread, read synchronously inside the dispatch on the same thread —
    // the annotation documents the cross-branch visibility contract.
    @Volatile
    private var isSyntheticSwipeShiftDispatch = false

    // DRS v2.9.0: the armed «honest revert» opportunity — an auto-committed
    // correction whose first backspace restores the typed word. One-shot,
    // self-verifying against the editor text at fire time (DrsCorrectionRevert),
    // and disarmed by every other key-up so it can never reach unrelated text.
    private var correctionRevertPending: DrsCorrectionRevert.Pending? = null

    private val activeEvaluatorGuard = Mutex(locked = false)
    private var activeEvaluatorVersion = AtomicInteger(0)
    val activeEvaluator: StateFlow<ComputingEvaluator>
        field = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)
    val activeSmartbarEvaluator: StateFlow<ComputingEvaluator>
        field = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)
    val lastCharactersEvaluator: StateFlow<ComputingEvaluator>
        field = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)

    val inputEventDispatcher = InputEventDispatcher.new(
        repeatableKeyCodes = intArrayOf(
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_WORD_LEFT,
            KeyCode.MOVE_WORD_RIGHT,
            KeyCode.DELETE,
            KeyCode.FORWARD_DELETE,
            KeyCode.UNDO,
            KeyCode.REDO,
        )
    ).also { it.keyEventReceiver = this }

    /**
     * DRS v1.0.5: live query of the emoji search. While
     * [KeyboardState.isMediaSearchActive] is true, character/delete/space
     * key events are routed into this flow instead of the host editor —
     * the standard way IME-internal search fields receive input.
     */
    val mediaSearchQuery = MutableStateFlow("")

    /** Leaves emoji search mode and clears the query. */
    fun exitMediaSearch() {
        activeState.isMediaSearchActive = false
        mediaSearchQuery.value = ""
    }

    /**
     * DRS p6 (E10): hardware SPACE/ENTER inside emoji search submit the
     * query — capture mode ends but the query itself is preserved (unlike
     * [exitMediaSearch]), so the filtered results stay visible.
     */
    fun submitMediaSearch() {
        activeState.isMediaSearchActive = false
    }

    // DRS (B5): whether the IME window is currently shown, pushed by
    // DrsImeService.onWindowShown / DrsImeService.onWindowHidden on both
    // transitions. While the window is hidden the keyboard is invisible,
    // so evaluator recomputes caused by pref/state/subtype churn are
    // deferred; the shown transition triggers exactly one recompute.
    private val imeWindowShownFlow = MutableStateFlow(false)
    val imeWindowShown: StateFlow<Boolean> = imeWindowShownFlow.asStateFlow()

    fun isImeWindowShown(): Boolean = imeWindowShownFlow.value

    fun onImeWindowShownChanged(shown: Boolean) {
        if (imeWindowShownFlow.value == shown) return
        imeWindowShownFlow.value = shown
        if (shown) {
            // DRS (B5): exactly one recompute on the shown transition —
            // covers everything the gated collectors deferred while hidden.
            updateActiveEvaluators()
        }
    }

    /**
     * DRS (B5): gated recompute entry for the pref/state collectors —
     * a no-op while the IME window is hidden. Cache clears stay
     * unconditional (call them next to this, outside the gate).
     */
    fun recomputeEvaluatorsIfWindowShown() {
        if (imeWindowShownFlow.value) {
            updateActiveEvaluators()
        }
    }

    init {
        scope.launch(Dispatchers.Main.immediate) {
            resources.anyChangedVersion.collectIn(scope) {
                // DRS (B5): the cache clear stays unconditional; only the
                // evaluator recompute is frozen while the window is hidden.
                keyboardCache.clear()
                recomputeEvaluatorsIfWindowShown()
            }
            prefs.keyboard.numberRow.asFlow().collectLatestIn(scope) {
                // DRS (B5): cache clear unconditional, recompute gated.
                keyboardCache.clear(KeyboardMode.CHARACTERS)
                recomputeEvaluatorsIfWindowShown()
            }
            prefs.keyboard.hintedNumberRowEnabled.asFlow().collectLatestIn(scope) {
                recomputeEvaluatorsIfWindowShown()
            }
            prefs.keyboard.hintedSymbolsEnabled.asFlow().collectLatestIn(scope) {
                recomputeEvaluatorsIfWindowShown()
            }
            prefs.keyboard.utilityKeyEnabled.asFlow().collectLatestIn(scope) {
                recomputeEvaluatorsIfWindowShown()
            }
            prefs.keyboard.utilityKeyAction.asFlow().collectLatestIn(scope) {
                recomputeEvaluatorsIfWindowShown()
            }
            activeState.collectLatestIn(scope) {
                recomputeEvaluatorsIfWindowShown()
            }
            subtypeManager.subtypesFlow.collectLatestIn(scope) {
                recomputeEvaluatorsIfWindowShown()
            }
            subtypeManager.activeSubtypeFlow.collectLatestIn(scope) {
                reevaluateInputShiftState()
                recomputeEvaluatorsIfWindowShown() // DRS (B5): gated recompute
                editorInstance.refreshComposing()
                resetSuggestions(editorInstance.activeContent)
            }
            clipboardManager.primaryClipFlow.collectLatestIn(scope) {
                recomputeEvaluatorsIfWindowShown()
            }
            editorInstance.activeContentFlow.collectIn(scope) { content ->
                resetSuggestions(content)
            }
            prefs.devtools.enabled.asFlow().collectLatestIn(scope) {
                reevaluateDebugFlags()
            }
            prefs.devtools.showDragAndDropHelpers.asFlow().collectLatestIn(scope) {
                reevaluateDebugFlags()
            }
        }
    }

    fun updateActiveEvaluators(action: () -> Unit = { }) = scope.launch {
        activeEvaluatorGuard.withLock {
            action()
            val editorInfo = editorInstance.activeInfo
            val state = activeState.snapshot()
            val subtype = subtypeManager.activeSubtype
            val mode = state.keyboardMode
            // We need to reset the snapshot input shift state for non-character layouts, because the shift mechanic
            // only makes sense for the character layouts.
            if (mode != KeyboardMode.CHARACTERS) {
                state.inputShiftState = InputShiftState.UNSHIFTED
            }
            val computedKeyboard = keyboardCache.getOrElseAsync(mode, subtype) {
                layoutManager.computeKeyboardAsync(
                    keyboardMode = mode,
                    subtype = subtype,
                ).await()
            }
            val computingEvaluator = ComputingEvaluatorImpl(
                version = activeEvaluatorVersion.getAndAdd(1),
                keyboard = computedKeyboard,
                editorInfo = editorInfo,
                state = state,
                subtype = subtype,
            )
            for (key in computedKeyboard.keys()) {
                key.compute(computingEvaluator)
                key.computeLabelsAndDrawables(computingEvaluator)
            }
            activeEvaluator.value = computingEvaluator
            activeSmartbarEvaluator.value = computingEvaluator.asSmartbarQuickActionsEvaluator()
            if (computedKeyboard.mode == KeyboardMode.CHARACTERS) {
                lastCharactersEvaluator.value = computingEvaluator
            }
        }
    }

    fun reevaluateInputShiftState() {
        if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
            val shift = prefs.correction.autoCapitalization.get()
                && subtypeManager.activeSubtype.primaryLocale.supportsCapitalization
                && editorInstance.activeCursorCapsMode != InputAttributes.CapsMode.NONE
            activeState.inputShiftState = when {
                shift -> InputShiftState.SHIFTED_AUTOMATIC
                else -> InputShiftState.UNSHIFTED
            }
        }
    }

    fun resetSuggestions(content: EditorContent) {
        // DRS p6 (E6) re-application: OR->AND — isSuggestionOn alone must
        // not bypass isComposingEnabled; the composing-disabled gate is real.
        if (!(activeState.isComposingEnabled && nlpManager.isSuggestionOn())) {
            nlpManager.clearSuggestions()
            return
        }
        nlpManager.suggest(subtypeManager.activeSubtype, content)
    }

    /**
     * @return If the language switch should be shown.
     */
    fun shouldShowLanguageSwitch(): Boolean {
        return subtypeManager.subtypes.size > 1
    }

    fun executeSwipeAction(swipeAction: SwipeAction) {
        DrsAdaptationEngine.recordGestureUse()
        val keyData = when (swipeAction) {
            SwipeAction.CYCLE_TO_PREVIOUS_KEYBOARD_MODE -> when (activeState.keyboardMode) {
                KeyboardMode.CHARACTERS -> TextKeyData.VIEW_NUMERIC_ADVANCED
                KeyboardMode.NUMERIC_ADVANCED -> TextKeyData.VIEW_SYMBOLS2
                KeyboardMode.SYMBOLS2 -> TextKeyData.VIEW_SYMBOLS
                else -> TextKeyData.VIEW_CHARACTERS
            }
            SwipeAction.CYCLE_TO_NEXT_KEYBOARD_MODE -> when (activeState.keyboardMode) {
                KeyboardMode.CHARACTERS -> TextKeyData.VIEW_SYMBOLS
                KeyboardMode.SYMBOLS -> TextKeyData.VIEW_SYMBOLS2
                KeyboardMode.SYMBOLS2 -> TextKeyData.VIEW_NUMERIC_ADVANCED
                else -> TextKeyData.VIEW_CHARACTERS
            }
            SwipeAction.DELETE_WORD -> TextKeyData.DELETE_WORD
            SwipeAction.HIDE_KEYBOARD -> TextKeyData.IME_HIDE_UI
            SwipeAction.INSERT_SPACE -> TextKeyData.SPACE
            SwipeAction.MOVE_CURSOR_DOWN -> TextKeyData.ARROW_DOWN
            SwipeAction.MOVE_CURSOR_UP -> TextKeyData.ARROW_UP
            SwipeAction.MOVE_CURSOR_LEFT -> TextKeyData.ARROW_LEFT
            SwipeAction.MOVE_CURSOR_RIGHT -> TextKeyData.ARROW_RIGHT
            SwipeAction.MOVE_CURSOR_START_OF_LINE -> TextKeyData.MOVE_START_OF_LINE
            SwipeAction.MOVE_CURSOR_END_OF_LINE -> TextKeyData.MOVE_END_OF_LINE
            SwipeAction.MOVE_CURSOR_START_OF_PAGE -> TextKeyData.MOVE_START_OF_PAGE
            SwipeAction.MOVE_CURSOR_END_OF_PAGE -> TextKeyData.MOVE_END_OF_PAGE
            SwipeAction.SHIFT -> TextKeyData.SHIFT
            SwipeAction.REDO -> TextKeyData.REDO
            SwipeAction.UNDO -> TextKeyData.UNDO
            SwipeAction.SHOW_INPUT_METHOD_PICKER -> TextKeyData.SYSTEM_INPUT_METHOD_PICKER
            SwipeAction.SHOW_SUBTYPE_PICKER -> TextKeyData.SHOW_SUBTYPE_PICKER
            SwipeAction.SWITCH_TO_CLIPBOARD_CONTEXT -> TextKeyData.IME_UI_MODE_CLIPBOARD
            SwipeAction.SWITCH_TO_MEDIA_CONTEXT -> TextKeyData.IME_UI_MODE_MEDIA
            SwipeAction.SWITCH_TO_PREV_SUBTYPE -> TextKeyData.IME_PREV_SUBTYPE
            SwipeAction.SWITCH_TO_NEXT_SUBTYPE -> TextKeyData.IME_NEXT_SUBTYPE
            SwipeAction.SWITCH_TO_PREV_KEYBOARD -> TextKeyData.SYSTEM_PREV_INPUT_METHOD
            SwipeAction.TOGGLE_SMARTBAR_VISIBILITY -> TextKeyData.TOGGLE_SMARTBAR_VISIBILITY
            SwipeAction.TOGGLE_COMPACT_LAYOUT -> TextKeyData.TOGGLE_COMPACT_LAYOUT
            else -> null
        }
        if (keyData != null) {
            // DRS p7 (E2-8): a swipe-shift is a SYNTHETIC SHIFT dispatch —
            // arm the flag around sendDownUp so handleShiftDown treats it
            // as a plain toggle and never arms/consults the 300ms
            // double-tap caps-lock timer. try/finally: down→handleShiftDown
            // →up is synchronous on one thread, so the flag can never leak
            // past this call.
            if (keyData.code == KeyCode.SHIFT) {
                isSyntheticSwipeShiftDispatch = true
                try {
                    inputEventDispatcher.sendDownUp(keyData)
                } finally {
                    isSyntheticSwipeShiftDispatch = false
                }
            } else {
                inputEventDispatcher.sendDownUp(keyData)
            }
        }
    }

    /**
     * DRS v2.7.0: volume-key cursor control (AOSP/OpenBoard heritage).
     * When the user opted in, VOLUME_UP/DOWN move the cursor one line
     * up/down through the same synthetic-dispatch path the board's swipe
     * language uses — same key data, same feedback pipeline, zero new
     * dialect. Returns false (event untouched) when the pref is off or
     * the key is not one of the two volume keys.
     */
    fun onVolumeKeyCursor(keyCode: Int): Boolean {
        if (!prefs.keyboard.volumeKeyCursor.get()) return false
        val action = DrsVolumeCursor.actionFor(keyCode) ?: return false
        val keyData = when (action) {
            SwipeAction.MOVE_CURSOR_UP -> TextKeyData.ARROW_UP
            SwipeAction.MOVE_CURSOR_DOWN -> TextKeyData.ARROW_DOWN
            else -> return false
        }
        inputEventDispatcher.sendDownUp(keyData)
        return true
    }

    fun commitCandidate(candidate: SuggestionCandidate, isAutoCommit: Boolean = false) {
        // DRS v1.6.0: every committed suggestion-row entry — tapped
        // candidates AND auto-committed completions — flows through this
        // single path, so this is the one real accept counter.
        DrsAdaptationEngine.recordSuggestionAccept()
        // DRS v2.9.0: a manual pick from the suggestion row is a deliberate
        // choice — it is never reverted, so it disarms any pending
        // auto-commit revert. Auto-commits (isAutoCommit = true) arm at
        // their own call sites, where the typed word is still observable
        // before the silent rewrite erases it.
        if (!isAutoCommit) {
            correctionRevertPending = null
        }
        scope.launch {
            candidate.sourceProvider?.notifySuggestionAccepted(subtypeManager.activeSubtype, candidate)
        }
        when (candidate) {
            is ClipboardSuggestionCandidate -> editorInstance.commitClipboardItem(candidate.clipboardItem)
            else -> editorInstance.commitCompletion(candidate)
        }
    }

    fun commitGesture(word: String) {
        editorInstance.commitGesture(fixCase(word))
    }

    /**
     * Changes a word to the current case.
     * eg if [KeyboardState.isUppercase] is true, abc -> ABC
     *    if [caps]     is true, abc -> Abc
     *    otherwise            , abc -> abc
     */
    fun fixCase(word: String): String {
        return when(activeState.inputShiftState) {
            InputShiftState.CAPS_LOCK -> {
                word.uppercase(subtypeManager.activeSubtype.primaryLocale)
            }
            InputShiftState.SHIFTED_MANUAL, InputShiftState.SHIFTED_AUTOMATIC -> {
                word.titlecase(subtypeManager.activeSubtype.primaryLocale)
            }
            else -> word
        }
    }

    /**
     * Handles [KeyCode] arrow and move events, behaves differently depending on text selection.
     */
    fun handleArrow(code: Int, count: Int = 1) = editorInstance.apply {
        val isShiftPressed = activeState.isManualSelectionMode || inputEventDispatcher.isPressed(KeyCode.SHIFT)
        // DRS v1.22.0: the revived modifier latches ride along — an armed
        // CTRL turns an arrow into word movement, an armed ALT into
        // line/page jumps (the same meta semantics physical keyboards
        // deliver; sendDownUpKeyEvent already carries META_*_ON). LOCKED
        // latches persist, LATCHED ones are consumed by the caller.
        val ctrlArmed = activeState.inputCtrlState.isArmed
        val altArmed = activeState.inputAltState.isArmed
        val content = activeContent
        val selection = content.selection
        when (code) {
            KeyCode.ARROW_LEFT -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, meta(ctrl = ctrlArmed, alt = altArmed, shift = isShiftPressed), count)
            }
            KeyCode.ARROW_RIGHT -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, meta(ctrl = ctrlArmed, alt = altArmed, shift = isShiftPressed), count)
            }
            KeyCode.ARROW_UP -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_UP, meta(ctrl = ctrlArmed, alt = altArmed, shift = isShiftPressed), count)
            }
            KeyCode.ARROW_DOWN -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, meta(ctrl = ctrlArmed, alt = altArmed, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_START_OF_PAGE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_UP, meta(alt = true, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_END_OF_PAGE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, meta(alt = true, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_START_OF_LINE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, meta(alt = true, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_END_OF_LINE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, meta(alt = true, shift = isShiftPressed), count)
            }
            // DRS v1.0.5: word-by-word cursor jumps (Ctrl+DPAD) — works with
            // the same editors that support Ctrl+Arrow word movement, and
            // respects manual selection mode for word-wise selection.
            KeyCode.MOVE_WORD_LEFT -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, meta(ctrl = true, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_WORD_RIGHT -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, meta(ctrl = true, shift = isShiftPressed), count)
            }
        }
    }

    /**
     * Handles a [KeyCode.CLIPBOARD_SELECT] event.
     */
    private fun handleClipboardSelect() {
        val activeSelection = editorInstance.activeContent.selection
        activeState.isManualSelectionMode = if (activeSelection.isSelectionMode) {
            if (activeState.isManualSelectionMode && activeState.isManualSelectionModeStart) {
                editorInstance.setSelection(activeSelection.start, activeSelection.start)
            } else {
                editorInstance.setSelection(activeSelection.end, activeSelection.end)
            }
            false
        } else {
            !activeState.isManualSelectionMode
        }
    }

    private fun revertPreviouslyAcceptedCandidate() {
        editorInstance.phantomSpace.candidateForRevert?.let { candidateForRevert ->
            candidateForRevert.sourceProvider?.let { sourceProvider ->
                scope.launch {
                    sourceProvider.notifySuggestionReverted(
                        subtype = subtypeManager.activeSubtype,
                        candidate = candidateForRevert,
                    )
                }
            }
        }
    }

    /**
     * Handles a [KeyCode.DELETE] event.
     */
    private fun handleBackwardDelete(unit: OperationUnit) {
        // DRS v2.9.0: the honest revert is one-shot — the DELETE key consumes
        // the armed opportunity on every path (applied, refused, redirected).
        val revertPending = correctionRevertPending
        correctionRevertPending = null
        if (inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
            return handleForwardDelete(unit)
        }
        if (revertPending != null && unit == OperationUnit.CHARACTERS &&
            prefs.correction.undoAutoCorrectOnBackspace.get()
        ) {
            val content = editorInstance.activeContent
            val selection = content.selection
            if (!selection.isSelectionMode) {
                // Self-verification before any rewrite: the editor text must
                // still end with exactly the committed word (+ bounded
                // non-word tail). A stale arm never reaches unrelated text.
                val plan = DrsCorrectionRevert.plan(revertPending, content.textBeforeSelection.toString())
                if (plan != null) {
                    editorInstance.setSelection(plan.replaceStart, plan.replaceEndExclusive)
                    editorInstance.commitText(revertPending.typed)
                    // The text is restored AND the provider must unlearn —
                    // the same revert notification the plain path sends.
                    revertPreviouslyAcceptedCandidate()
                    return
                }
            }
        }
        activeState.batchEdit {
            it.isManualSelectionMode = false
            it.isManualSelectionModeStart = false
            it.isManualSelectionModeEnd = false
        }
        revertPreviouslyAcceptedCandidate()
        editorInstance.deleteBackwards(unit)
    }

    /**
     * Handles a [KeyCode.FORWARD_DELETE] event.
     */
    private fun handleForwardDelete(unit: OperationUnit) {
        activeState.batchEdit {
            it.isManualSelectionMode = false
            it.isManualSelectionModeStart = false
            it.isManualSelectionModeEnd = false
        }
        revertPreviouslyAcceptedCandidate()
        editorInstance.deleteForwards(unit)
    }

    /**
     * Handles a [KeyCode.ENTER] event.
     */
    private fun handleEnter() {
        val info = editorInstance.activeInfo
        val isShiftPressed = inputEventDispatcher.isPressed(KeyCode.SHIFT)
        if (editorInstance.tryPerformEnterCommitRaw()) {
            return
        }
        if (info.imeOptions.flagNoEnterAction || info.inputAttributes.flagTextMultiLine && isShiftPressed) {
            editorInstance.performEnter()
        } else {
            when (val action = info.imeOptions.action) {
                ImeOptions.Action.DONE,
                ImeOptions.Action.GO,
                ImeOptions.Action.NEXT,
                ImeOptions.Action.PREVIOUS,
                ImeOptions.Action.SEARCH,
                ImeOptions.Action.SEND,
                // DRS p6 (E9): an UNSPECIFIED action reaches the host editor
                // as performEditorAction(IME_ACTION_UNSPECIFIED) so the HOST
                // decides what to do — a raw newline is only correct for an
                // explicit IME_ACTION_NONE field.
                ImeOptions.Action.UNSPECIFIED -> editorInstance.performEnterAction(action)
                ImeOptions.Action.NONE -> editorInstance.performEnter()
            }
        }
    }

    /**
     * Handles a [KeyCode.LANGUAGE_SWITCH] event. Also handles if the language switch should cycle
     * DRS Smart Keyboard internal or system-wide.
     */
    private fun handleLanguageSwitch() {
        when (prefs.keyboard.utilityKeyAction.get()) {
            UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS,
            UtilityKeyAction.SWITCH_LANGUAGE -> subtypeManager.switchToNextSubtype()
            else -> DrsImeService.switchToNextInputMethod()
        }
    }

    /**
     * DRS v1.0.8: handles a [KeyCode.INSERT_DATE_TIME] event by committing
     * the current date & time formatted with the ACTIVE SUBTYPE's locale
     * (system locale as fallback). The text goes through the normal
     * commitText path, so undo/composing/clipboard integrations all apply.
     */
    private fun handleInsertDateTime() {
        val locale = try {
            subtypeManager.activeSubtype.primaryLocale.base
        } catch (_: Throwable) {
            java.util.Locale.getDefault()
        }
        val formatted = try {
            java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT,
                locale,
            ).format(java.util.Date())
        } catch (_: Throwable) {
            return
        }
        editorInstance.commitText(formatted)
    }

    /**
     * Handles a [KeyCode.SHIFT] down event.
     */
    private fun handleShiftDown(data: KeyData) {
        val prefs = prefs.keyboard.capitalizationBehavior
        when (prefs.get()) {
            CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP -> {
                // DRS p7 (E2-8): a synthetic swipe-shift dispatch skips the
                // consecutive-down check — the 300ms double-tap window would
                // otherwise latch CAPS_LOCK for swipe→swipe and
                // tap-SHIFT→swipe-SHIFT sequences, which are plain toggles.
                if (!isSyntheticSwipeShiftDispatch && inputEventDispatcher.isConsecutiveDown(data)) {
                    activeState.inputShiftState = InputShiftState.CAPS_LOCK
                } else {
                    if (activeState.inputShiftState == InputShiftState.UNSHIFTED) {
                        activeState.inputShiftState = InputShiftState.SHIFTED_MANUAL
                    } else {
                        activeState.inputShiftState = InputShiftState.UNSHIFTED
                    }
                }
            }
            CapitalizationBehavior.CAPSLOCK_BY_CYCLE -> {
                activeState.inputShiftState = when (activeState.inputShiftState) {
                    InputShiftState.UNSHIFTED -> InputShiftState.SHIFTED_MANUAL
                    InputShiftState.SHIFTED_MANUAL -> InputShiftState.CAPS_LOCK
                    InputShiftState.SHIFTED_AUTOMATIC -> InputShiftState.UNSHIFTED
                    InputShiftState.CAPS_LOCK -> InputShiftState.UNSHIFTED
                }
            }
        }
    }

    /**
     * Handles a [KeyCode.SHIFT] up event.
     */
    private fun handleShiftUp(data: KeyData) {
        if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isAnyPressed() &&
            !inputEventDispatcher.isUninterruptedEventSequence(data)) {
            activeState.inputShiftState = InputShiftState.UNSHIFTED
        }
    }

    /**
     * Handles a [KeyCode.CAPS_LOCK] event.
     */
    private fun handleCapsLock() {
        activeState.inputShiftState = InputShiftState.CAPS_LOCK
    }

    /**
     * Handles a [KeyCode.SHIFT] cancel event.
     */
    private fun handleShiftCancel() {
        activeState.inputShiftState = InputShiftState.UNSHIFTED
    }

    /**
     * Handles a hardware [KeyEvent.KEYCODE_SPACE] event. Same as [handleSpace],
     * but skips handling changing to characters keyboard and double space periods.
     */
    fun handleHardwareKeyboardSpace() {
        // DRS v1.28.0 audit fix (Low, dedup): this tail duplicated
        // handleSpace's auto-commit + learning + space-commit block verbatim
        // (including its copied TODO). Both paths now share one helper so
        // behavior can never drift apart again.
        commitSpaceWithAutoCommitAndLearning()
    }

    /**
     * DRS v1.28.0: the shared auto-correct + word-learning + space-commit
     * tail used by both the soft space key and the hardware space key.
     */
    private fun commitSpaceWithAutoCommitAndLearning() {
        val candidate = nlpManager.getAutoCommitCandidate()
        // DRS v2.9.0: the space path is where the silent rewrite happens —
        // capture what the user typed BEFORE the commit erases it, then arm
        // the honest revert (the arm refuses honestly when nothing was
        // actually corrected: committed == typed, blanks, oversized words).
        if (candidate != null) {
            val typed = editorInstance.activeContent.currentWordText
            commitCandidate(candidate, isAutoCommit = true)
            correctionRevertPending = DrsCorrectionRevert.arm(
                typed = typed,
                committed = candidate.text.toString(),
            )
        }
        // DRS: learn manually typed words (not picked from the suggestion row and not
        // auto-committed) into the personal user dictionary. Guarded like all DRS
        // learning features: no composing-disabled, password or incognito contexts.
        if (candidate == null && activeState.isComposingEnabled &&
            activeState.keyVariation != com.drs.smartkeyboard.ime.text.key.KeyVariation.PASSWORD &&
            !activeState.isIncognitoMode
        ) {
            val typed = editorInstance.activeContent.currentWordText
            if (typed.length in 2..32 && typed.all { it.isLetter() }) {
                nlpManager.learnExternalWord(subtypeManager.activeSubtype, typed)
            }
        }
        // TODO: this is whether we commit space after selecting candidate. Should be determined by SuggestionProvider
        if (!subtypeManager.activeSubtype.primaryLocale.supportsAutoSpace &&
                candidate != null) { /* Do nothing */ } else {
            editorInstance.commitText(KeyCode.SPACE.toChar().toString())
        }

    }

    /**
     * Handles a [KeyCode.SPACE] event. Also handles the auto-correction of two space taps if
     * enabled by the user.
     */
    private fun handleSpace(data: KeyData) {
        // DRS: expand a registered shortcut (abbreviation + space -> full text,
        // with template variables like {date}/{hijri}/{clipboard}) before any
        // other space handling. Never runs for password/incognito.
        if (DrsIntegration.handleSpaceShortcut(editorInstance, activeState, clipboardManager.primaryClip?.stringRepresentation())) {
            return
        }
        if (prefs.keyboard.spaceBarSwitchesToCharacters.get()) {
            when (activeState.keyboardMode) {
                KeyboardMode.NUMERIC_ADVANCED,
                KeyboardMode.SYMBOLS,
                KeyboardMode.SYMBOLS2 -> {
                    activeState.keyboardMode = KeyboardMode.CHARACTERS
                }
                else -> { /* Do nothing */ }
            }
        }
        if (prefs.correction.doubleSpacePeriod.get() &&
            // DRS p6 (E4): double-space-period is a TEXT-typing affordance —
            // in numeric/phone/datetime fields (or on non-character keyboard
            // pages) the inserted ". " corrupts the value. The soft path now
            // matches the hardware path's field-type contract.
            activeState.keyboardMode == KeyboardMode.CHARACTERS &&
            editorInstance.activeInfo.inputAttributes.type == InputAttributes.Type.TEXT
        ) {
            if (inputEventDispatcher.isConsecutiveUp(data)) {
                val text = editorInstance.run { activeContent.getTextBeforeCursor(2) }
                if (text.length == 2 && DoubleSpacePeriodMatcher.matches(text)) {
                    editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
                    editorInstance.commitText(". ")
                    return
                }
            }
        }
        // DRS v2.7.0: smart punctuation — a whitespace- (or text-start)
        // preceded double hyphen is rewritten to a real em dash as the
        // following space commits. Same TEXT/CHARACTERS guards as the
        // double-space affordance above; runs of three or more dashes and
        // dashes glued inside a token are left untouched (see
        // DrsSmartPunctuation). The space itself still commits below, so
        // "word --␣" becomes "word —␣" in one keystroke.
        if (prefs.correction.smartPunctuation.get() &&
            activeState.keyboardMode == KeyboardMode.CHARACTERS &&
            editorInstance.activeInfo.inputAttributes.type == InputAttributes.Type.TEXT
        ) {
            // Three chars, not two: the matcher must see the character
            // BEFORE the dashes to reject "a--" (glued) and "---" (a drawn
            // line). A shorter return simply means the text starts there.
            val before = editorInstance.run { activeContent.getTextBeforeCursor(3) }
            if (DrsSmartPunctuation.findEmDashTail(before) == DrsSmartPunctuation.MATCH_LENGTH) {
                editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
                editorInstance.commitText(DrsSmartPunctuation.EM_DASH)
            }
        }
        // DRS v1.28.0 audit fix (Low, dedup): shared tail, see
        // commitSpaceWithAutoCommitAndLearning().
        commitSpaceWithAutoCommitAndLearning()
    }

    /**
     * Handles a [KeyCode.TOGGLE_INCOGNITO_MODE] event.
     */
    private suspend fun handleToggleIncognitoMode() {
        prefs.suggestion.forceIncognitoModeFromDynamic.set(!prefs.suggestion.forceIncognitoModeFromDynamic.get())
        val newState = !activeState.isIncognitoMode
        activeState.isIncognitoMode = newState
        lastToastReference.get()?.cancel()
        lastToastReference = WeakReference(
            if (newState) {
                appContext.showLongToast(
                    R.string.incognito_mode__toast_after_enabled,
                    "app_name" to appContext.getString(R.string.drs_app_name),
                )
            } else {
                appContext.showLongToast(
                    R.string.incognito_mode__toast_after_disabled,
                    "app_name" to appContext.getString(R.string.drs_app_name),
                )
            }
        )
    }

    /**
     * DRS v1.23.0: handles a [KeyCode.VOICE_INPUT] press — «الميكروفون
     * يستيقظ». The pure [decideVoiceInputRoute] owns the truth: password/
     * incognito contexts are refused with an honest toast, a ROM without
     * any recognition service keeps the legacy external voice-IME switch,
     * a first press launches the translucent permission trampoline, and an
     * armed session starts the platform recognizer in the active subtype's
     * language.
     */
    private fun handleVoiceInput() {
        val permissionGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        // DRS v1.28.0 audit fix (Low, privacy hardening): the password signal
        // now includes the resolved KeyVariation (the same primary signal the
        // clipboard guard uses). Previously sensitivity leaned on
        // DrsRuntimeState.contextMode, which is force-normalized to NORMAL
        // when the user disables context modes — combined with
        // IncognitoMode.FORCE_OFF that could let the mic open inside a
        // password field. KeyVariation.PASSWORD is immune to that pref.
        val isSensitive = activeState.isIncognitoMode ||
            activeState.keyVariation == com.drs.smartkeyboard.ime.text.key.KeyVariation.PASSWORD ||
            DrsRuntimeState.contextMode.value == DrsContextMode.PASSWORD
        // DRS v1.25.0: the recognizer mode gate is real — the pref the
        // typing settings screen drives is consulted on every press, so
        // a strict on-device demand a ROM cannot honor answers with an
        // honest toast instead of silently falling back to the cloud.
        val onDeviceAvailable = AndroidVersion.ATLEAST_API31_S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        val route = decideVoiceInputRoute(
            recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(appContext),
            permissionGranted = permissionGranted,
            isSensitive = isSensitive,
            userEnabled = prefs.voice.enabled.get(),
            // DRS roadmap phase 3: the absolute privacy mode coerces the
            // requested recognizer to ON_DEVICE_ONLY — a cloud recognizer
            // is refused while a ROM-honored on-device one keeps working
            // (no socket in our process, matching the sentinel contract).
            recognizerMode = DrsPrivacyLock.effectiveRecognizer(
                requested = prefs.voice.recognizerMode.get(),
                absoluteMode = prefs.privacy.absoluteMode.get(),
            ),
            onDeviceAvailable = onDeviceAvailable,
        )
        when (route) {
            VoiceInputRoute.DISABLED_SENSITIVE ->
                appContext.showShortToastSync(R.string.voice__disabled_sensitive)
            VoiceInputRoute.DISABLED_BY_SETTING ->
                appContext.showShortToastSync(R.string.voice__disabled_by_setting)
            VoiceInputRoute.ON_DEVICE_UNAVAILABLE ->
                appContext.showShortToastSync(R.string.voice__on_device_unavailable)
            VoiceInputRoute.FALLBACK_EXTERNAL -> DrsImeService.switchToVoiceInputMethod()
            VoiceInputRoute.REQUEST_PERMISSION -> {
                DrsVoiceInputBus.reset()
                appContext.startActivity(
                    VoicePermissionActivity.createIntent(appContext, activeVoiceLanguageTag()),
                )
            }
            VoiceInputRoute.START_INTERNAL -> voiceController.start(activeVoiceLanguageTag())
        }
    }

    /** DRS v1.23.0: the recognizer follows the active subtype's language. */
    private fun activeVoiceLanguageTag(): String =
        subtypeManager.activeSubtypeFlow.value.primaryLocale.base.toLanguageTag()

    /**
     * Handles a [KeyCode.TOGGLE_AUTOCORRECT] event.
     *
     * DRS v1.3.0: REAL toggle — flips an engine pref the settings screen
     * and the profile switch drive too, so the change takes effect on the
     * very next keystroke, with a toast confirming the new state.
     * DRS v1.17.0: flips suggestion__autocorrect_enabled — whether space
     * may silently fix a typo — while the suggestion row stays available.
     */
    private suspend fun handleToggleAutocorrect() {
        // DRS v1.17.0: the toggle now flips the REAL autocorrect behavior
        // (whether a space can silently fix a typo) instead of the old
        // misnomer of hiding the whole suggestion row. The row itself stays
        // available; only auto-commit stops happening when off.
        prefs.suggestion.autocorrectEnabled.set(!prefs.suggestion.autocorrectEnabled.get())
        val newState = prefs.suggestion.autocorrectEnabled.get()
        lastToastReference.get()?.cancel()
        lastToastReference = WeakReference(
            if (newState) {
                appContext.showLongToast(R.string.drs__autocorrect__toast_enabled)
            } else {
                appContext.showLongToast(R.string.drs__autocorrect__toast_disabled)
            }
        )
    }

    /**
     * Handles a [KeyCode.KANA_SWITCHER] event
     */
    private fun handleKanaSwitch() {
        activeState.batchEdit {
            it.isKanaKata = !it.isKanaKata
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_HIRA] event
     */
    private fun handleKanaHira() {
        activeState.batchEdit {
            it.isKanaKata = false
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_KATA] event
     */
    private fun handleKanaKata() {
        activeState.batchEdit {
            it.isKanaKata = true
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_HALF_KATA] event
     */
    private fun handleKanaHalfKata() {
        activeState.batchEdit {
            it.isKanaKata = true
            it.isCharHalfWidth = true
        }
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthSwitch() {
        activeState.isCharHalfWidth = !activeState.isCharHalfWidth
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthFull() {
        activeState.isCharHalfWidth = false
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthHalf() {
        activeState.isCharHalfWidth = true
    }

    override fun onInputKeyDown(data: KeyData) {
        val windowController = DrsImeService.windowControllerOrNull()
        windowController?.editor?.disableIfNoGestureInProgress()
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> {
                editorInstance.massSelection.begin()
            }
            KeyCode.SHIFT -> handleShiftDown(data)
        }
    }

    override fun onInputKeyUp(data: KeyData) {
        // DRS v1.0.6: measure the REAL wall-clock time the key handler takes
        // per press (µs) into a bounded in-memory window. Pure RAM sampling,
        // lock-free, allocation-free - it never affects typing behavior.
        val startNs = System.nanoTime()
        try {
            onInputKeyUpBody(data)
        } finally {
            DrsPerformance.recordKeyLatency((System.nanoTime() - startNs) / 1000L)
        }
    }

    private fun onInputKeyUpBody(data: KeyData) = activeState.batchEdit {
        val windowController = DrsImeService.windowControllerOrNull() ?: return@batchEdit
        // DRS v2.9.0: the honest revert is a ONE-BACKSPACE affordance — every
        // other key-up (typing, space, arrows, navigation, suggestions)
        // disarms a stale opportunity before it can fire on unrelated text.
        // KeyCode.DELETE is exempt: it is the key that consumes the revert
        // inside handleBackwardDelete (one-shot there, on every path).
        if (data.code != KeyCode.DELETE) {
            correctionRevertPending = null
        }
        // DRS v1.22.0: snapshot the armed modifier latches BEFORE any
        // branch can consume them — the arrow and delete branches below
        // read these, the CTRL/ALT branches cycle them.
        // DRS v1.23.0: the FN latch joins the snapshot — armed FN turns
        // the digit keys into real F1–F10 key events (see the commit path
        // at the bottom of this function).
        val ctrlArmed = activeState.inputCtrlState.isArmed
        val altArmed = activeState.inputAltState.isArmed
        val fnArmed = activeState.inputFnState.isArmed
        DrsAdaptationEngine.recordKey(data.code)
        DrsEconomy.recordKeyEarn(data.code)
        // DRS v1.0.5: anonymous count of smart-tool usage (which tool button
        // was pressed, never what was typed) for the most-used tools surface.
        if (data.code in SmartToolCodes) {
            DrsAdaptationEngine.recordToolUse(data.code)
        }
        // DRS v1.0.5: while the emoji search field is active, route typing
        // into the search query instead of the host editor. Emoji taps and
        // navigation keys still fall through to the normal handling.
        // DRS p7 (E2-1): the capture gate additionally requires
        // imeUiMode == MEDIA (belt-and-braces) — a stale isMediaSearchActive
        // flag from a previous field must never silently swallow typing in
        // TEXT mode; onStartInputView now resets both, this guards the gap.
        if (activeState.isMediaSearchActive && activeState.imeUiMode == ImeUiMode.MEDIA) {
            when (data.code) {
                KeyCode.DELETE -> {
                    mediaSearchQuery.update { it.dropLast(1) }
                    return@batchEdit
                }
                KeyCode.FORWARD_DELETE -> {
                    mediaSearchQuery.update { it.drop(1) }
                    return@batchEdit
                }
                KeyCode.SPACE -> {
                    mediaSearchQuery.update { it + " " }
                    return@batchEdit
                }
                else -> if (data.type == KeyType.CHARACTER || data.type == KeyType.NUMERIC) {
                    val text = data.asString(isForDisplay = false)
                    val first = text.firstOrNull()
                    if (first != null && first.isLetterOrDigit()) {
                        mediaSearchQuery.update { it + text }
                        return@batchEdit
                    }
                    // Non-searchable characters (emoji, punctuation...) keep
                    // their normal behavior — e.g. tapping a result emoji.
                }
            }
        }
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE,
            KeyCode.MOVE_WORD_LEFT,
            KeyCode.MOVE_WORD_RIGHT -> {
                editorInstance.massSelection.end()
                handleArrow(data.code)
            }
            KeyCode.CAPS_LOCK -> handleCapsLock()
            // DRS v1.22.0: the revived CTRL/ALT — «المفاتيح الميتة تنبض».
            // A tap cycles OFF → LATCHED → LOCKED → OFF; the *_LOCK codes
            // jump straight to LOCKED. They used to fall into the
            // unknown-key branch (drawn, dead, logged as an error).
            KeyCode.CTRL, KeyCode.CTRL_LOCK -> {
                activeState.inputCtrlState = cycleModifierLatch(
                    activeState.inputCtrlState,
                    lock = data.code == KeyCode.CTRL_LOCK,
                )
            }
            KeyCode.ALT, KeyCode.ALT_LOCK -> {
                activeState.inputAltState = cycleModifierLatch(
                    activeState.inputAltState,
                    lock = data.code == KeyCode.ALT_LOCK,
                )
            }
            // DRS v1.23.0: the last dead modifier of the family revives —
            // FN latches exactly like CTRL/ALT (the same v1.22.0 contract:
            // tap = one-shot, tap again = lock, third tap = release), and
            // while armed it transforms the digit keys into F1–F10
            // hardware events for terminals, remote desktop and console
            // emulators. Until this round FN/FN_LOCK fell into the
            // unknown-key branch (drawn if a layout declares them, dead
            // on press, logged as an error).
            KeyCode.FN, KeyCode.FN_LOCK -> {
                activeState.inputFnState = cycleModifierLatch(
                    activeState.inputFnState,
                    lock = data.code == KeyCode.FN_LOCK,
                )
            }
            KeyCode.CHAR_WIDTH_SWITCHER -> handleCharWidthSwitch()
            KeyCode.CHAR_WIDTH_FULL -> handleCharWidthFull()
            KeyCode.CHAR_WIDTH_HALF -> handleCharWidthHalf()
            KeyCode.CLIPBOARD_CUT -> {
                editorInstance.performClipboardCut()
                // DRS v1.7.0: cut is a clipboard feature use — credit it the
                // same way paste is credited (economy + adaptation signal).
                DrsAdaptationEngine.recordClipboardUse()
            }
            KeyCode.CLIPBOARD_COPY -> {
                editorInstance.performClipboardCopy()
                // DRS v1.7.0: copy-heavy users were invisible to the
                // clipboard counters and the history suggestion — credit it.
                DrsAdaptationEngine.recordClipboardUse()
            }
            KeyCode.CLIPBOARD_PASTE -> {
                editorInstance.performClipboardPaste()
                // DRS v1.0.5: clipboard use is now actually credited (was
                // dead code before) so points and adaptation suggestions work.
                DrsAdaptationEngine.recordClipboardUse()
            }
            KeyCode.CLIPBOARD_SHARE -> editorInstance.performClipboardShare()
            KeyCode.CLIPBOARD_SELECT -> handleClipboardSelect()
            KeyCode.CLIPBOARD_SELECT_ALL -> editorInstance.performClipboardSelectAll()
            KeyCode.CLIPBOARD_CLEAR_HISTORY -> clipboardManager.clearHistory()
            KeyCode.CLIPBOARD_CLEAR_FULL_HISTORY -> clipboardManager.clearFullHistory()
            KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> {
                if (prefs.clipboard.clearPrimaryClipAffectsHistoryIfUnpinned.get()) {
                    clipboardManager.primaryClip?.let { clipboardManager.deleteClip(it, onlyIfUnpinned = true) }
                }
                clipboardManager.updatePrimaryClip(null)
                appContext.showShortToastSync(R.string.clipboard__cleared_primary_clip)
            }
            // DRS v1.7.0: pin/unpin the ACTIVE clipboard entry through the
            // exact same real path the clipboard panel long-press popup
            // uses (pinned entries survive clearHistory by contract).
            KeyCode.CLIPBOARD_PIN_ACTIVE -> {
                clipboardManager.primaryClip?.let { clip ->
                    if (clip.isPinned) {
                        clipboardManager.unpinClip(clip)
                        appContext.showShortToastSync(R.string.clipboard__unpinned_active)
                    } else if (!clipboardManager.pinClip(clip)) {
                        // DRS v1.22.0: the honest pin cap toast — the
                        // same refusal the clipboard panel popup shows.
                        appContext.showShortToastSync(R.string.clipboard__pin_cap_toast)
                    } else {
                        appContext.showShortToastSync(R.string.clipboard__pinned_active)
                    }
                }
            }
            KeyCode.TOGGLE_FLOATING_WINDOW -> windowController.actions.toggleFloatingWindow()
            KeyCode.TOGGLE_COMPACT_LAYOUT -> windowController.actions.toggleCompactLayout()
            KeyCode.COMPACT_LAYOUT_TO_LEFT -> windowController.actions.compactLayoutToLeft()
            KeyCode.COMPACT_LAYOUT_TO_RIGHT -> windowController.actions.compactLayoutToRight()
            KeyCode.TOGGLE_RESIZE_MODE -> windowController.editor.toggleEnabled()
            // DRS v1.5.0: the tech-toolbar Tab and Esc keys used to fall
            // into the unknown-key branch (silent no-op). Tab now commits a
            // real tab character and Escape hides the keyboard UI — the
            // same path IME_HIDE_UI uses.
            KeyCode.TAB -> editorInstance.commitText("\t")
            KeyCode.ESCAPE -> DrsImeService.hideUi()
            KeyCode.DELETE -> handleBackwardDelete(
                // DRS v1.22.0: an armed CTRL upgrades the delete to word
                // granularity — the same contract a physical keyboard has.
                if (ctrlArmed) OperationUnit.WORDS else OperationUnit.CHARACTERS,
            )
            KeyCode.DELETE_WORD -> handleBackwardDelete(OperationUnit.WORDS)
            KeyCode.ENTER -> handleEnter()
            KeyCode.FORWARD_DELETE -> handleForwardDelete(
                // DRS v1.22.0: armed CTRL deletes forward by words too.
                if (ctrlArmed) OperationUnit.WORDS else OperationUnit.CHARACTERS,
            )
            KeyCode.FORWARD_DELETE_WORD -> handleForwardDelete(OperationUnit.WORDS)
            KeyCode.IME_SHOW_UI -> DrsImeService.showUi()
            KeyCode.IME_HIDE_UI -> DrsImeService.hideUi()
            KeyCode.IME_PREV_SUBTYPE -> subtypeManager.switchToPrevSubtype()
            KeyCode.IME_NEXT_SUBTYPE -> subtypeManager.switchToNextSubtype()
            KeyCode.IME_UI_MODE_TEXT -> {
                exitMediaSearch()
                activeState.imeUiMode = ImeUiMode.TEXT
                DrsRuntimeState.closeStripSlotEditor()
            }
            KeyCode.IME_UI_MODE_MEDIA -> {
                exitMediaSearch()
                activeState.imeUiMode = ImeUiMode.MEDIA
                DrsRuntimeState.closeStripSlotEditor()
            }
            KeyCode.IME_UI_MODE_CLIPBOARD -> {
                exitMediaSearch()
                activeState.imeUiMode = ImeUiMode.CLIPBOARD
                DrsRuntimeState.closeStripSlotEditor()
            }
            KeyCode.IME_UI_MODE_TEXT_TOOLS -> {
                exitMediaSearch()
                activeState.imeUiMode = ImeUiMode.TEXT_TOOLS
                DrsRuntimeState.closeStripSlotEditor()
            }
            // DRS v1.15.0: the three smart panels (الحركات/الرموز/الحروف)
            // open through the same path as every other UI mode.
            KeyCode.IME_UI_MODE_DIACRITICS -> {
                exitMediaSearch()
                activeState.imeUiMode = ImeUiMode.DIACRITICS
                DrsRuntimeState.closeStripSlotEditor()
            }
            KeyCode.IME_UI_MODE_SMART_SYMBOLS -> {
                exitMediaSearch()
                activeState.imeUiMode = ImeUiMode.SMART_SYMBOLS
                DrsRuntimeState.closeStripSlotEditor()
            }
            KeyCode.IME_UI_MODE_ARABIC_LETTERS -> {
                exitMediaSearch()
                activeState.imeUiMode = ImeUiMode.ARABIC_LETTERS
                DrsRuntimeState.closeStripSlotEditor()
            }
            // DRS v1.2.0: the fourth smart panel (لوحة الأرقام الذكية)
            // opens through the same path as every other UI mode.
            KeyCode.IME_UI_MODE_SMART_NUMBER -> {
                exitMediaSearch()
                activeState.imeUiMode = ImeUiMode.SMART_NUMBER
                DrsRuntimeState.closeStripSlotEditor()
            }
            // DRS v1.3.0: the fifth smart panel (لوحة الحافظة الذكية)
            // opens through the same path as every other UI mode.
            KeyCode.IME_UI_MODE_SMART_CLIPBOARD -> {
                exitMediaSearch()
                activeState.imeUiMode = ImeUiMode.SMART_CLIPBOARD
                DrsRuntimeState.closeStripSlotEditor()
            }
            in DrsTextTool.CODE_RANGE -> {
                DrsTextTool.fromCode(data.code)?.let { editorInstance.performTextTool(it) }
            }
            // DRS v1.23.0: the mic key finally dictates — the pure route
            // decision sends it to the built-in recognizer, the permission
            // trampoline, or the legacy external voice-IME switch.
            KeyCode.VOICE_INPUT -> handleVoiceInput()
            KeyCode.KANA_SWITCHER -> handleKanaSwitch()
            KeyCode.KANA_HIRA -> handleKanaHira()
            KeyCode.KANA_KATA -> handleKanaKata()
            KeyCode.KANA_HALF_KATA -> handleKanaHalfKata()
            KeyCode.LANGUAGE_SWITCH -> handleLanguageSwitch()
            KeyCode.REDO -> editorInstance.performRedo()
            KeyCode.SETTINGS -> DrsImeService.launchSettings()
            // DRS v1.0.8: unified-strip tools (real engine actions).
            KeyCode.THEME_CYCLE -> scope.launch { themeManager.cycleTheme() }
            KeyCode.INSERT_DATE_TIME -> handleInsertDateTime()
            // DRS v1.1.0: unified-strip tool — flips the number-row pref;
            // the existing pref collector clears the layout cache and the
            // LayoutManager recomputes the character layout, so the row
            // appears/disappears on the very next frame.
            KeyCode.TOGGLE_NUMBER_ROW -> scope.launch {
                prefs.keyboard.numberRow.set(!prefs.keyboard.numberRow.get())
            }
            // DRS v1.19.0: the split keyboard (v1.18.0) becomes one-tap
            // actionable — SPLIT_LAYOUT activates the split halves, MERGE_LAYOUT
            // returns to the classic full layout. Same engine pref the settings
            // screen drives, so the change lands on the very next frame, with a
            // status toast like every other real toggle.
            KeyCode.SPLIT_LAYOUT -> scope.launch {
                prefs.keyboard.splitMode.set(SplitMode.ALWAYS)
                appContext.showShortToastSync(R.string.keyboard__split_layout_toast_split)
            }
            KeyCode.MERGE_LAYOUT -> scope.launch {
                prefs.keyboard.splitMode.set(SplitMode.NEVER)
                appContext.showShortToastSync(R.string.keyboard__split_layout_toast_merge)
            }
            KeyCode.SHIFT -> handleShiftUp(data)
            KeyCode.SPACE -> handleSpace(data)
            KeyCode.SYSTEM_INPUT_METHOD_PICKER -> InputMethodUtils.showImePicker(appContext)
            KeyCode.SHOW_SUBTYPE_PICKER -> {
                activeState.isSubtypeSelectionVisible = true
            }
            // DRS v1.7.0: IME_SUBTYPE_PICKER had predefined key data since
            // v1.0.x but NO handler — pressing it silently logged "unknown
            // key". It is the same action as SHOW_SUBTYPE_PICKER.
            KeyCode.IME_SUBTYPE_PICKER -> {
                activeState.isSubtypeSelectionVisible = true
            }
            KeyCode.SYSTEM_PREV_INPUT_METHOD -> DrsImeService.switchToPrevInputMethod()
            KeyCode.SYSTEM_NEXT_INPUT_METHOD -> DrsImeService.switchToNextInputMethod()
            KeyCode.TOGGLE_SMARTBAR_VISIBILITY -> scope.launch {
                // DRS perf (r0-D): value-guard the pref write — writing an
                // unchanged value still wakes every downstream pref collector
                // and forces a needless evaluator cycle. (The shared guard
                // helper for the per-keystroke smartbar writes lives in
                // NlpManager and is applied by the r1-B pass; this call site
                // is guarded inline with the same semantics because the
                // helper's signature is not recoverable from the worklog.)
                prefs.smartbar.enabled.let {
                    val current = it.get()
                    val newValue = !current
                    if (newValue != current) {
                        it.set(newValue)
                    }
                }
            }
            KeyCode.TOGGLE_ACTIONS_OVERFLOW -> {
                // DRS v1.8.0: the overflow panel and the tools drawer share
                // the same keyboard area — opening one closes the other.
                activeState.isToolsDrawerVisible = false
                activeState.isActionsOverflowVisible = !activeState.isActionsOverflowVisible
            }
            KeyCode.TOGGLE_ACTIONS_EDITOR -> {
                activeState.isToolsDrawerVisible = false
                activeState.isActionsEditorVisible = !activeState.isActionsEditorVisible
            }
            KeyCode.TOGGLE_INCOGNITO_MODE -> scope.launch { handleToggleIncognitoMode() }
            KeyCode.TOGGLE_AUTOCORRECT -> scope.launch { handleToggleAutocorrect() }
            KeyCode.UNDO -> editorInstance.performUndo()
            KeyCode.VIEW_CHARACTERS -> activeState.keyboardMode = KeyboardMode.CHARACTERS
            KeyCode.VIEW_NUMERIC -> activeState.keyboardMode = KeyboardMode.NUMERIC
            KeyCode.VIEW_NUMERIC_ADVANCED -> activeState.keyboardMode = KeyboardMode.NUMERIC_ADVANCED
            KeyCode.VIEW_PHONE -> activeState.keyboardMode = KeyboardMode.PHONE
            KeyCode.VIEW_PHONE2 -> activeState.keyboardMode = KeyboardMode.PHONE2
            KeyCode.VIEW_SYMBOLS -> activeState.keyboardMode = KeyboardMode.SYMBOLS
            KeyCode.VIEW_SYMBOLS2 -> activeState.keyboardMode = KeyboardMode.SYMBOLS2
            // DRS v1.20.0: transient placeholder tiles (the quick-actions editor paints
            // empty slots with NOOP/DRAG_MARKER while rearranging) previously fell through
            // to the "unknown key" error path and fired a pointless empty commit. They are
            // layout markers, never actionable keys — swallow them silently.
            KeyCode.NOOP,
            KeyCode.DRAG_MARKER,
            -> Unit
            else -> {
                if (activeState.imeUiMode == ImeUiMode.MEDIA) {
                    nlpManager.getAutoCommitCandidate()?.let { commitCandidate(it) }
                    editorInstance.commitText(data.asString(isForDisplay = false))
                    return@batchEdit
                }
                // DRS v1.23.0: armed FN turns the digit keys ('0'..'9',
                // ASCII 48..57) into real F1–F10 hardware events — the
                // exact contract a physical Fn row delivers in terminals,
                // remote-desktop clients and console emulators. Anything
                // else (letters, punctuation) keeps its normal commit and
                // merely consumes the latch below, mirroring the honest
                // no-fake-emulation CTRL/ALT behavior.
                // DRS v1.24.0: the row reaches its natural end — '-'(45)
                // and '='(61) (CHARACTER-typed on the numeric/symbols/
                // symbols2 pages) become F11/F12, and page hops keep the
                // latch alive so those keys are actually reachable.
                val fnFunctionKey = if (fnArmed &&
                    (data.type == KeyType.NUMERIC || data.code == '-'.code || data.code == '='.code)
                ) {
                    fnFunctionKeyCodeOf(data.code)
                } else {
                    null
                }
                if (fnFunctionKey != null) {
                    editorInstance.sendDownUpKeyEvent(fnFunctionKey)
                } else when (activeState.keyboardMode) {
                    KeyboardMode.NUMERIC,
                    KeyboardMode.NUMERIC_ADVANCED,
                    KeyboardMode.PHONE,
                    KeyboardMode.PHONE2 -> when (data.type) {
                        KeyType.CHARACTER,
                        KeyType.NUMERIC -> {
                            val text = data.asString(isForDisplay = false)
                            editorInstance.commitText(text)
                        }
                        else -> when (data.code) {
                            KeyCode.PHONE_PAUSE,
                            KeyCode.PHONE_WAIT -> {
                                val text = data.asString(isForDisplay = false)
                                editorInstance.commitText(text)
                            }
                        }
                    }
                    else -> when (data.type) {
                        KeyType.CHARACTER, KeyType.NUMERIC ->{
                            val text = data.asString(isForDisplay = false)
                            // DRS (C2): data.asString() can be empty for
                            // exotic/placeholder keys — codePointAt on an
                            // empty string throws IndexOutOfBounds and took
                            // the whole key dispatch down. The auto-commit
                            // check is skipped for empty text; commitChar
                            // stays unconditional (it owns empty-input
                            // semantics itself).
                            if (text.isNotEmpty() && !UCharacter.isUAlphabetic(UCharacter.codePointAt(text, 0))) {
                                // DRS v2.9.0: interword punctuation auto-commits too
                                // («helo,» → «hello,») — arm the same honest revert,
                                // capturing the typed word before the silent rewrite.
                                val autoCandidate = nlpManager.getAutoCommitCandidate()
                                if (autoCandidate != null) {
                                    val typed = editorInstance.activeContent.currentWordText
                                    commitCandidate(autoCandidate, isAutoCommit = true)
                                    correctionRevertPending = DrsCorrectionRevert.arm(
                                        typed = typed,
                                        committed = autoCandidate.text.toString(),
                                    )
                                }
                            }
                            editorInstance.commitChar(text)
                        }
                        else -> {
                            flogError(LogTopic.KEY_EVENTS) { "Received unknown key: $data" }
                        }
                    }
                }
                if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                    activeState.inputShiftState = InputShiftState.UNSHIFTED
                }
            }
        }
        // DRS v1.22.0: a consuming key releases a one-shot modifier latch —
        // LOCKED latches persist until the key is tapped again, and the
        // modifier keys never consume themselves (the branch that cycles
        // them is reached before this cleanup). `consumed()` is
        // idempotent, so branches that already consumed stay honest.
        when (data.code) {
            KeyCode.CTRL, KeyCode.CTRL_LOCK,
            KeyCode.ALT, KeyCode.ALT_LOCK,
            KeyCode.FN, KeyCode.FN_LOCK,
            KeyCode.SHIFT, KeyCode.CAPS_LOCK,
            -> Unit
            else -> {
                activeState.inputCtrlState = activeState.inputCtrlState.consumed()
                activeState.inputAltState = activeState.inputAltState.consumed()
                // DRS v1.23.0: the FN latch releases after one consuming
                // press exactly like CTRL/ALT — LOCKED persists.
                // DRS v1.24.0: page/mode switches (VIEW_*/IME_UI_MODE_*)
                // are NOT consuming keys for FN — F11 lives behind '-' on
                // the numeric/symbols pages and F12 behind '=' on symbols2,
                // so the armed latch must survive the hop or the extended
                // Fn row would be unreachable. The pure [fnSurvivesKey]
                // owns the set.
                if (!fnSurvivesKey(data.code)) {
                    activeState.inputFnState = activeState.inputFnState.consumed()
                }
            }
        }
    }

    override fun onInputKeyCancel(data: KeyData) {
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE,
            KeyCode.MOVE_WORD_LEFT,
            KeyCode.MOVE_WORD_RIGHT -> {
                editorInstance.massSelection.end()
            }
            KeyCode.SHIFT -> handleShiftCancel()
        }
    }

    override fun onInputKeyRepeat(data: KeyData) {
        DrsImeService.inputFeedbackController()?.keyRepeatedAction(data)
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE,
            KeyCode.MOVE_WORD_LEFT,
            KeyCode.MOVE_WORD_RIGHT -> handleArrow(data.code)
            else -> onInputKeyUp(data)
        }
    }

    private fun reevaluateDebugFlags() {
        val devtoolsEnabled = prefs.devtools.enabled.get()
        activeState.batchEdit {
            activeState.debugShowDragAndDropHelpers = devtoolsEnabled && prefs.devtools.showDragAndDropHelpers.get()
        }
    }

    fun onHardwareKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // DRS p6 (E10): while emoji search is active, hardware SPACE/ENTER
        // submit the search instead of committing a space/newline into the
        // host field behind the invisible capture.
        if (activeState.isMediaSearchActive &&
            (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            submitMediaSearch()
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                // DRS p6 (E7): a held physical key auto-repeats — consume
                // the repeats so a held SPACE/ENTER cannot spam commits.
                if ((event?.repeatCount ?: 0) > 0) return true
                handleHardwareKeyboardSpace()
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                // DRS p6 (E7): same repeat guard — no duplicate SEND actions
                // from a held ENTER.
                if ((event?.repeatCount ?: 0) > 0) return true
                handleEnter()
                return true
            }
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                inputEventDispatcher.sendDown(TextKeyData.SHIFT)
                return true
            }
            else -> return false
        }
    }

    fun onHardwareKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                inputEventDispatcher.sendUp(TextKeyData.SHIFT)
                return true
            }
            else -> return false
        }
    }

    inner class KeyboardManagerResources {
        val composers = MutableStateFlow<Map<ExtensionComponentName, Composer>>(emptyMap())
        val currencySets = MutableStateFlow<Map<ExtensionComponentName, CurrencySet>>(emptyMap())
        val layouts = MutableStateFlow<Map<LayoutType, Map<ExtensionComponentName, LayoutArrangementComponent>>>(emptyMap())
        val popupMappings = MutableStateFlow<Map<ExtensionComponentName, PopupMappingComponent>>(emptyMap())
        val punctuationRules = MutableStateFlow<Map<ExtensionComponentName, PunctuationRule>>(emptyMap())
        val subtypePresets = MutableStateFlow<List<SubtypePreset>>(emptyList())

        val anyChangedVersion = MutableStateFlow(0)

        init {
            extensionManager.keyboardExtensions.collectIn(scope) { keyboardExtensions ->
                parseKeyboardExtensions(keyboardExtensions)
            }
        }

        private fun parseKeyboardExtensions(keyboardExtensions: List<KeyboardExtension>) {
            val localComposers = mutableMapOf<ExtensionComponentName, Composer>()
            val localCurrencySets = mutableMapOf<ExtensionComponentName, CurrencySet>()
            val localLayouts = mutableMapOf<LayoutType, MutableMap<ExtensionComponentName, LayoutArrangementComponent>>()
            val localPopupMappings = mutableMapOf<ExtensionComponentName, PopupMappingComponent>()
            val localPunctuationRules = mutableMapOf<ExtensionComponentName, PunctuationRule>()
            val localSubtypePresets = mutableListOf<SubtypePreset>()
            for (layoutType in LayoutType.entries) {
                localLayouts[layoutType] = mutableMapOf()
            }
            for (keyboardExtension in keyboardExtensions) {
                keyboardExtension.composers.forEach { composer ->
                    localComposers[ExtensionComponentName(keyboardExtension.meta.id, composer.id)] = composer
                }
                keyboardExtension.currencySets.forEach { currencySet ->
                    localCurrencySets[ExtensionComponentName(keyboardExtension.meta.id, currencySet.id)] = currencySet
                }
                keyboardExtension.layouts.forEach { (type, layoutComponents) ->
                    for (layoutComponent in layoutComponents) {
                        localLayouts[LayoutType.entries.first { it.id == type }]!![ExtensionComponentName(keyboardExtension.meta.id, layoutComponent.id)] = layoutComponent
                    }
                }
                keyboardExtension.popupMappings.forEach { popupMapping ->
                    localPopupMappings[ExtensionComponentName(keyboardExtension.meta.id, popupMapping.id)] = popupMapping
                }
                keyboardExtension.punctuationRules.forEach { punctuationRule ->
                    localPunctuationRules[ExtensionComponentName(keyboardExtension.meta.id, punctuationRule.id)] = punctuationRule
                }
                localSubtypePresets.addAll(keyboardExtension.subtypePresets)
            }
            localSubtypePresets.sortBy { it.locale.displayName() }
            for (languageCode in listOf("en-CA", "en-AU", "en-UK", "en-US")) {
                val index: Int = localSubtypePresets.indexOfFirst { it.locale.languageTag() == languageCode }
                if (index > 0) {
                    localSubtypePresets.add(0, localSubtypePresets.removeAt(index))
                }
            }
            subtypePresets.value = localSubtypePresets
            composers.value = localComposers
            currencySets.value = localCurrencySets
            layouts.value = localLayouts
            popupMappings.value = localPopupMappings
            punctuationRules.value = localPunctuationRules
            anyChangedVersion.update { it + 1 }
        }
    }

    private inner class ComputingEvaluatorImpl(
        override val version: Int,
        override val keyboard: Keyboard,
        override val editorInfo: DrsEditorInfo,
        override val state: KeyboardState,
        override val subtype: Subtype,
    ) : ComputingEvaluator {

        override fun context(): Context = appContext

        val androidKeyguardManager = context().systemService(AndroidKeyguardManager::class)

        override fun displayLanguageNamesIn(): DisplayLanguageNamesIn {
            return prefs.localization.displayLanguageNamesIn.get()
        }

        override fun evaluateEnabled(data: KeyData): Boolean {
            return when (data.code) {
                KeyCode.CLIPBOARD_COPY,
                KeyCode.CLIPBOARD_CUT -> {
                    state.isSelectionMode && editorInfo.isRichInputEditor
                }
                KeyCode.CLIPBOARD_PASTE -> {
                    !androidKeyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }
                        && clipboardManager.canBePasted(clipboardManager.primaryClip)
                }
                KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> {
                    clipboardManager.canBePasted(clipboardManager.primaryClip)
                }
                KeyCode.CLIPBOARD_SELECT_ALL -> {
                    editorInfo.isRichInputEditor
                }
                KeyCode.TOGGLE_INCOGNITO_MODE -> when (prefs.suggestion.incognitoMode.get()) {
                    IncognitoMode.FORCE_OFF, IncognitoMode.FORCE_ON -> false
                    IncognitoMode.DYNAMIC_ON_OFF -> !editorInfo.imeOptions.flagNoPersonalizedLearning
                }
                KeyCode.LANGUAGE_SWITCH -> {
                    subtypeManager.subtypes.size > 1
                }
                // DRS v2.2.2 «الشريطان الصادقان سياقيًا»: مع اكتساح المهام
                // الموحدة على evaluator سياقي، بقي ImeNextSubtype/ImePrevSubtype
                // بلا بوابة — مع نوع فرعي وحيد ينتهيان إلى لا شيء صامت
                // (switchToNext/PrevSubtype يدوران في المكان). نفس شرط
                // LANGUAGE_SWITCH حرفيًا: مصدر حقيقة واحد للثلاثة.
                KeyCode.IME_NEXT_SUBTYPE,
                KeyCode.IME_PREV_SUBTYPE -> {
                    subtypeManager.subtypes.size > 1
                }
                else -> true
            }
        }

        override fun evaluateVisible(data: KeyData): Boolean {
            return when (data.code) {
                KeyCode.IME_UI_MODE_TEXT,
                KeyCode.IME_UI_MODE_MEDIA -> {
                    val tempUtilityKeyAction = when {
                        prefs.keyboard.utilityKeyEnabled.get() -> prefs.keyboard.utilityKeyAction.get()
                        else -> UtilityKeyAction.DISABLED
                    }
                    when (tempUtilityKeyAction) {
                        UtilityKeyAction.DISABLED,
                        UtilityKeyAction.SWITCH_LANGUAGE,
                        UtilityKeyAction.SWITCH_KEYBOARD_APP -> false
                        UtilityKeyAction.SWITCH_TO_EMOJIS -> true
                        UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS -> !shouldShowLanguageSwitch()
                    }
                }
                KeyCode.LANGUAGE_SWITCH -> {
                    val tempUtilityKeyAction = when {
                        prefs.keyboard.utilityKeyEnabled.get() -> prefs.keyboard.utilityKeyAction.get()
                        else -> UtilityKeyAction.DISABLED
                    }
                    when (tempUtilityKeyAction) {
                        UtilityKeyAction.DISABLED,
                        UtilityKeyAction.SWITCH_TO_EMOJIS -> false
                        UtilityKeyAction.SWITCH_LANGUAGE,
                        UtilityKeyAction.SWITCH_KEYBOARD_APP -> true
                        UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS -> shouldShowLanguageSwitch()
                    }
                }
                else -> true
            }
        }

        override fun isSlot(data: KeyData): Boolean {
            return CurrencySet.isCurrencySlot(data.code)
        }

        override fun slotData(data: KeyData): KeyData? {
            return subtypeManager.getCurrencySet(subtype).getSlot(data.code)
        }

        fun asSmartbarQuickActionsEvaluator(): ComputingEvaluatorImpl {
            return ComputingEvaluatorImpl(
                version = version,
                keyboard = SmartbarQuickActionsKeyboard,
                editorInfo = editorInfo,
                state = state,
                subtype = Subtype.DEFAULT,
            )
        }
    }
}
