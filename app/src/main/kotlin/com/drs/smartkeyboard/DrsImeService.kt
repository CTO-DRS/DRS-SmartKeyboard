/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard

import com.drs.smartkeyboard.drs.DrsRuntimeState
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.inputmethodservice.ExtractEditText
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Size
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.drs.smartkeyboard.app.DrsAppActivity
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.ime.ImeUiMode
import com.drs.smartkeyboard.ime.editor.EditorRange
import com.drs.smartkeyboard.ime.editor.DrsEditorInfo
import com.drs.smartkeyboard.ime.input.InputFeedbackController
import com.drs.smartkeyboard.ime.keyboard.isFullscreenInputRequired
import com.drs.smartkeyboard.ime.landscapeinput.ExtractedInputRootView
import com.drs.smartkeyboard.ime.landscapeinput.LandscapeInputUiMode
import com.drs.smartkeyboard.ime.lifecycle.LifecycleInputMethodService
import com.drs.smartkeyboard.ime.nlp.NlpInlineAutofill
import com.drs.smartkeyboard.ime.theme.WallpaperChangeReceiver
import com.drs.smartkeyboard.ime.window.ImeRootView
import com.drs.smartkeyboard.ime.window.ImeWindowController
import com.drs.smartkeyboard.lib.devtools.LogTopic
import com.drs.smartkeyboard.lib.devtools.FlogTopic
import com.drs.smartkeyboard.lib.devtools.flogError
import com.drs.smartkeyboard.lib.devtools.flogInfo
import com.drs.smartkeyboard.lib.devtools.flogWarning
import com.drs.smartkeyboard.lib.util.InputMethodUtils
import com.drs.smartkeyboard.lib.util.debugSummarize
import com.drs.smartkeyboard.lib.util.launchActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import org.drs.lib.android.AndroidInternalR
import org.drs.lib.android.AndroidVersion
import org.drs.lib.android.showShortToastSync
import org.drs.lib.android.systemServiceOrNull
import org.drs.lib.kotlin.collectIn
import org.drs.lib.kotlin.collectLatestIn
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [DrsImeService] class. This is needed as certain actions (request hide, switch to
 * another input method, getting the editor instance / input connection, etc.) can only be performed by an IME
 * service class and no context-bound managers. This reference is exclusively used by the companion helper methods
 * of [DrsImeService], which provide a safe and memory-leak-free way of performing certain actions on the Drs
 * input method service instance.
 */
private var DrsImeServiceReference = WeakReference<DrsImeService?>(null)

/**
 * Core class responsible for linking together all managers and UI composables to provide an IME service. Sets
 * up the window and context to be lifecycle-aware, so LiveData and Jetpack Compose can be used without issues.
 */
class DrsImeService : LifecycleInputMethodService() {
    companion object {
        private val InlineSuggestionUiSmallestSize = Size(0, 0)
        private val InlineSuggestionUiBiggestSize = Size(Int.MAX_VALUE, Int.MAX_VALUE)

        fun currentInputConnection(): InputConnection? {
            return DrsImeServiceReference.get()?.currentInputConnection
        }

        fun inputFeedbackController(): InputFeedbackController? {
            return DrsImeServiceReference.get()?.inputFeedbackController
        }

        /**
         * Hides the IME and launches [DrsAppActivity].
         */
        fun launchSettings() {
            val ims = DrsImeServiceReference.get() ?: return
            ims.requestHideSelf(0)
            ims.launchActivity(DrsAppActivity::class) {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        fun showUi() {
            val ims = DrsImeServiceReference.get() ?: return
            ims.showUi()
        }

        fun hideUi() {
            val ims = DrsImeServiceReference.get() ?: return
            ims.hideUi()
        }

        fun switchToPrevInputMethod(): Boolean {
            val ims = DrsImeServiceReference.get() ?: return false
            return ims.switchToPrevInputMethod()
        }

        fun switchToNextInputMethod(): Boolean {
            val ims = DrsImeServiceReference.get() ?: return false
            return ims.switchToNextInputMethod()
        }

        fun switchToVoiceInputMethod(): Boolean {
            val ims = DrsImeServiceReference.get() ?: return false
            return ims.switchToVoiceInputMethod()
        }

        fun showImePicker(): Boolean {
            val ims = DrsImeServiceReference.get() ?: return false
            return InputMethodUtils.showImePicker(ims)
        }

        fun windowControllerOrNull(): ImeWindowController? {
            val ims = DrsImeServiceReference.get() ?: return null
            return ims.windowController
        }
    }

    fun hideUi() {
        requestHideSelf(0)
    }

    /**
     * Show the Ime UI
     *
     * Note: This function can be replaced with a `requestShowSelf(0)`
     * call once we've set the minApiLevel to 28 (Android 9)
     */
    fun showUi() {
        if (AndroidVersion.ATLEAST_API28_P) {
            requestShowSelf(0)
        } else {
            @Suppress("DEPRECATION")
            systemServiceOrNull(InputMethodManager::class)
                ?.showSoftInputFromInputMethod(currentInputBinding.connectionToken, 0)
        }
    }


    /**
     * Switch to previous input method
     *
     * Note: This function can be replaced with a `switchToPreviousInputMethod()`
     * call once we've set the minApiLevel to 28 (Android 9)
     *
     * @return true if the switch was successful
     */
    fun switchToPrevInputMethod(): Boolean {
        val imm = systemServiceOrNull(InputMethodManager::class)
        try {
            if (AndroidVersion.ATLEAST_API28_P) {
                return switchToPreviousInputMethod()
            } else {
                window.window?.let { window ->
                    @Suppress("DEPRECATION")
                    return imm?.switchToLastInputMethod(window.attributes.token) == true
                }
            }
        } catch (e: Exception) {
            flogError { "Unable to switch to the previous IME" }
            imm?.showInputMethodPicker()
        }
        return false
    }

    /**
     * Switch to next input method
     *
     * Note: This function can be replaced with a `switchToNextInputMethod(false)`
     * call once we've set the minApiLevel to 28 (Android 9)
     *
     * @return true if the switch was successful
     */
    fun switchToNextInputMethod(): Boolean {
        val imm = systemServiceOrNull(InputMethodManager::class)
        try {
            if (AndroidVersion.ATLEAST_API28_P) {
                return switchToNextInputMethod(false)
            } else {
                window.window?.let { window ->
                    @Suppress("DEPRECATION")
                    return imm?.switchToNextInputMethod(window.attributes.token, false) == true
                }
            }
        } catch (e: Exception) {
            flogError { "Unable to switch to the next IME" }
            imm?.showInputMethodPicker()
        }
        return false
    }

    /**
     * Switch to next input method
     *
     * Note: The inner part of this function can be replaced with a
     *
     * `switchInputMethod(el.id, el.getSubtypeAt(i))` call once we've set the minApiLevel to 28 (Android 9)
     *
     * @return true if the switch was successful
     */
    fun switchToVoiceInputMethod(): Boolean {
        val imm = systemServiceOrNull(InputMethodManager::class) ?: return false
        val list: List<InputMethodInfo> = imm.enabledInputMethodList
        for (el in list) {
            for (i in 0 until el.subtypeCount) {
                // Check if the subtype is a voice input method.
                // We need to hardcode 'voice' here because the SUBTYPE_MODE_VOICE constant is private.
                // https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/core/java/android/view/inputmethod/InputMethodManager.java;drc=2b278ab3ac73bb5596327aac1298df85cd94e454;l=309
                if (el.getSubtypeAt(i).mode != "voice") continue
                if (AndroidVersion.ATLEAST_API28_P) {
                    switchInputMethod(el.id, el.getSubtypeAt(i))
                    return true
                } else {
                    window.window?.let { window ->
                        @Suppress("DEPRECATION")
                        imm.setInputMethod(window.attributes.token, el.id)
                        return true
                    }
                }
            }
        }
        showShortToastSync(R.string.ime__voice_ime_not_found)
        return false
    }

    private val prefs by DrsPreferenceStore
    val editorInstance by editorInstance()
    private val keyboardManager by keyboardManager()
    private val nlpManager by nlpManager()
    private val subtypeManager by subtypeManager()
    private val themeManager by themeManager()

    val windowController = ImeWindowController(prefs, lifecycleScope)

    private val activeState get() = keyboardManager.activeState
    val inputFeedbackController by lazy { InputFeedbackController.new(this) }
    private val systemLocalesFlow = MutableStateFlow(LocaleList())
    var resourcesContext by mutableStateOf(this as Context)
        private set

    private val wallpaperChangeReceiver = WallpaperChangeReceiver()

    init {
        setTheme(R.style.DrsImeTheme)
    }

    override fun onCreate() {
        super.onCreate()
        DrsImeServiceReference = WeakReference(this)
        systemLocalesFlow.value = resources.configuration.locales

        WindowCompat.setDecorFitsSystemWindows(window.window!!, false)
        windowController.onConfigurationChanged(resources.configuration)
        windowController.activeWindowConfig.collectLatestIn(lifecycleScope) {
            // DRS (B5): gated — a window-config change while the IME window
            // is hidden defers to the single shown-transition recompute.
            keyboardManager.recomputeEvaluatorsIfWindowShown() // TODO: wacky solution, but works for now
        }

        combine(
            systemLocalesFlow,
            subtypeManager.activeSubtypeFlow,
            prefs.localization.displayKeyboardLabelsInSubtypeLanguage.asFlow(),
        ) { systemLocales, subtype, shouldUseSubtypeLanguage ->
            systemLocales to (if (shouldUseSubtypeLanguage) subtype.primaryLocale else null)
        }.collectIn(lifecycleScope) { (systemLocales, subtypeLocale) ->
            val config = Configuration().apply {
                setToDefaults()
                if (subtypeLocale != null) {
                    setLocale(subtypeLocale.base)
                } else {
                    setLocales(systemLocales)
                }
            }
            resourcesContext = createConfigurationContext(config)
        }

        prefs.physicalKeyboard.showOnScreenKeyboard.asFlow().collectIn(lifecycleScope) {
            updateInputViewShown()
        }

        @Suppress("DEPRECATION") // We do not retrieve the wallpaper but only listen to changes
        registerReceiver(wallpaperChangeReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
    }

    override fun onCreateInputView(): View? {
        super.installViewTreeOwners()
        val content = window.window!!.findViewById<ViewGroup>(android.R.id.content)
        // DRS p6 (E3): onCreateInputView runs again after rotations/theme
        // recreation — remove any previous ImeRootView first or the compose
        // roots stack up inside android.R.id.content (leaked composition +
        // duplicated touch dispatch from two live keyboard roots).
        for (i in content.childCount - 1 downTo 0) {
            if (content.getChildAt(i) is ImeRootView) {
                content.removeViewAt(i)
            }
        }
        content.addView(ImeRootView(this))
        // Disable the default input view placement
        return null
    }

    override fun onCreateCandidatesView(): View? {
        // Disable the default candidates view
        return null
    }

    override fun onCreateExtractTextView(): View {
        super.installViewTreeOwners()
        // Consider adding a fallback to the default extract edit layout if user reports come
        // that this causes a crash, especially if the device manufacturer of the user device
        // is a known one to break AOSP standards...
        val defaultExtractView = super.onCreateExtractTextView()
        if (defaultExtractView == null || defaultExtractView !is ViewGroup) {
            return ExtractedInputRootView(this, null)
        }
        val extractEditText = defaultExtractView.findViewById<ExtractEditText>(android.R.id.inputExtractEditText)
        (extractEditText?.parent as? ViewGroup)?.removeView(extractEditText)
        defaultExtractView.let {
            it.removeAllViews()
            it.addView(ExtractedInputRootView(this, extractEditText))
        }
        return defaultExtractView
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemLocalesFlow.value = newConfig.locales
        windowController.onConfigurationChanged(newConfig)
        themeManager.configurationChangeCounter.update { it + 1 }
    }

    override fun onDestroy() {
        super.onDestroy()
        // DRS (H2): teardown is best-effort — a dead recognizer, a dead
        // receiver or a half-torn-down manager must never crash the
        // service shutdown path.
        // DRS v1.23.0: a live voice session must never outlive the service.
        runCatching { keyboardManager.destroyVoiceInput() }.onFailure {
            flogError(LogTopic.IMS_EVENTS) { "destroyVoiceInput failed: $it" }
        }
        runCatching { unregisterReceiver(wallpaperChangeReceiver) }.onFailure {
            flogError(LogTopic.IMS_EVENTS) { "unregisterReceiver failed: $it" }
        }
        DrsImeServiceReference = WeakReference(null)
    }

    /**
     * DRS (H2): shield for IMS lifecycle callbacks. The framework can
     * deliver callbacks during/after teardown and any manager hit by a
     * dead InputConnection or a half-disposed evaluator must not take the
     * whole IME process down: failures are flog-reported and swallowed.
     * `super.*` invocations stay OUTSIDE the shield on purpose — framework
     * state must remain consistent even when our side fails.
     */
    private inline fun shieldInputCallback(
        topic: FlogTopic,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (t: Throwable) {
            flogError(topic) { "Shielded IMS callback failed: $t" }
        }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        // DRS (H2): shielded; super stays outside the shield.
        shieldInputCallback(LogTopic.IMS_EVENTS) {
            flogInfo { "restarting=$restarting info=${info?.debugSummarize()}" }
            if (info != null) {
                val editorInfo = DrsEditorInfo.wrap(info)
                editorInstance.handleStartInput(editorInfo)
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // DRS (H2): shielded; super stays outside the shield.
        shieldInputCallback(LogTopic.IMS_EVENTS) {
            flogInfo { "restarting=$restarting info=${info?.debugSummarize()}" }
            // DRS p7 (E2-1): a new input view must never inherit a live
            // emoji search — a stale isMediaSearchActive would silently
            // swallow every letter/digit/space/delete into the invisible
            // query until the media tab is reopened. Runs for BOTH
            // restarting modes; safe off-dispatch (only flips state flags
            // + clears the query flow; no-op when inactive).
            keyboardManager.exitMediaSearch()
            // DRS p7 (E2-2): same shield — a dictation session must not
            // survive the field change (the mic would otherwise commit the
            // transcript into the new field, including password/incognito
            // fields the mic-key gate would have refused). stop() is
            // idempotent and a no-op when idle; nullable instance so no
            // controller is created here.
            keyboardManager.stopVoiceInput()
            if (info != null) {
                val editorInfo = DrsEditorInfo.wrap(info)
                activeState.batchEdit {
                    if (activeState.imeUiMode != ImeUiMode.CLIPBOARD || prefs.clipboard.historyHideOnNextTextField.get()) {
                        activeState.imeUiMode = ImeUiMode.TEXT
                    }
                    activeState.isSelectionMode = editorInfo.initialSelection.isSelectionMode
                    editorInstance.handleStartInputView(editorInfo, isRestart = restarting)
                }
            }
        }
    }

    override fun onEvaluateInputViewShown(): Boolean {
        val config = resources.configuration
        return super.onEvaluateInputViewShown()
            || config.keyboard == Configuration.KEYBOARD_NOKEYS
            || prefs.physicalKeyboard.showOnScreenKeyboard.get()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // DRS (H2): shielded (batchEdit inside the shield); super stays
        // outside the shield.
        shieldInputCallback(LogTopic.IMS_EVENTS) {
            flogInfo { "old={start=$oldSelStart,end=$oldSelEnd} new={start=$newSelStart,end=$newSelEnd} composing={start=$candidatesStart,end=$candidatesEnd}" }
            activeState.batchEdit {
                activeState.isSelectionMode = (newSelEnd - newSelStart) != 0
                editorInstance.handleSelectionUpdate(
                    oldSelection = EditorRange.normalized(oldSelStart, oldSelEnd),
                    newSelection = EditorRange.normalized(newSelStart, newSelEnd),
                    composing = EditorRange.normalized(candidatesStart, candidatesEnd),
                )
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // DRS (H2): shielded; super stays outside the shield.
        shieldInputCallback(LogTopic.IMS_EVENTS) {
            flogInfo { "finishing=$finishingInput" }
            editorInstance.handleFinishInputView()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // DRS (H2): shielded; super stays outside the shield.
        shieldInputCallback(LogTopic.IMS_EVENTS) {
            flogInfo { "(no args)" }
            editorInstance.handleFinishInput()
            NlpInlineAutofill.clearInlineSuggestions()
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        // DRS (B5): push the window-shown transition — KeyboardManager
        // unfreezes evaluator recomputes and runs exactly one recompute.
        keyboardManager.onImeWindowShownChanged(true)
        if (windowController.onWindowShown()) {
            flogInfo(LogTopic.IMS_EVENTS)
            inputFeedbackController.updateSystemPrefsState()
        } else {
            flogWarning(LogTopic.IMS_EVENTS) { "Ignoring (is already shown)" }
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        // DRS (B5): push the window-hidden transition — evaluator
        // recomputes freeze while the keyboard is invisible; cache clears
        // stay unconditional.
        keyboardManager.onImeWindowShownChanged(false)
        // DRS (B2): the keyboard went away — flush any deferred DRS store
        // write now so a process death can never eat up to 15s of state.
        com.drs.smartkeyboard.drs.DrsStore.flushAsync()
        if (windowController.onWindowHidden()) {
            flogInfo(LogTopic.IMS_EVENTS)
            // DRS v1.23.0: the keyboard went away — a live dictation
            // session must die with it (never keep the mic hot in the
            // background).
            keyboardManager.stopVoiceInput()
            activeState.batchEdit {
                activeState.imeUiMode = ImeUiMode.TEXT
                activeState.isActionsOverflowVisible = false
                activeState.isActionsEditorVisible = false
                // DRS v1.8.0: never persist a stale tools drawer across hides.
                activeState.isToolsDrawerVisible = false
                // DRS v1.16.0: ...nor a stale strip slot editor.
                DrsRuntimeState.closeStripSlotEditor()
            }
        } else {
            flogWarning(LogTopic.IMS_EVENTS) { "Ignoring (is already hidden)" }
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        val config = resources.configuration
        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            return false
        }
        return when (prefs.keyboard.landscapeInputUiMode.get()) {
            LandscapeInputUiMode.DYNAMICALLY_SHOW -> super.onEvaluateFullscreenMode()
            LandscapeInputUiMode.NEVER_SHOW -> false
            LandscapeInputUiMode.ALWAYS_SHOW -> true
        }
    }

    override fun onUpdateExtractingVisibility(info: EditorInfo?) {
        // DRS p6 (E8): the old code unconditionally re-ran the full
        // handleStartInputView(restart = true) pipeline on every extraction
        // update. Now: only when fullscreen extract is ACTUALLY shown,
        // only with isRestart = false (this is not an input restart), and
        // skipped entirely when the editor info did not change (dedupe).
        if (info != null && isExtractViewShown) {
            val editorInfo = DrsEditorInfo.wrap(info)
            if (editorInstance.activeInfo != editorInfo) {
                editorInstance.handleStartInputView(editorInfo, isRestart = false)
            }
        }
        when (prefs.keyboard.landscapeInputUiMode.get()) {
            LandscapeInputUiMode.DYNAMICALLY_SHOW -> super.onUpdateExtractingVisibility(info)
            LandscapeInputUiMode.NEVER_SHOW -> isExtractViewShown = false
            LandscapeInputUiMode.ALWAYS_SHOW -> isExtractViewShown = true
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (!prefs.smartbar.enabled.get() || !prefs.suggestion.api30InlineSuggestionsEnabled.get()) {
            flogInfo(LogTopic.IMS_EVENTS) {
                "Ignoring inline suggestions request because Smartbar and/or inline suggestions are disabled."
            }
            return null
        }

        flogInfo(LogTopic.IMS_EVENTS) { "Creating inline suggestions request" }
        val stylesBundle = themeManager.createInlineSuggestionUiStyleBundle(this)
        if (stylesBundle == null) {
            flogWarning(LogTopic.IMS_EVENTS) { "Failed to retrieve inline suggestions style bundle" }
            return null
        }
        val spec = InlinePresentationSpec.Builder(
            InlineSuggestionUiSmallestSize,
            InlineSuggestionUiBiggestSize,
        ).run {
            setStyle(stylesBundle)
            build()
        }

        return InlineSuggestionsRequest.Builder(listOf(spec)).run {
            setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
            build()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        val inlineSuggestions = response.inlineSuggestions
        flogInfo(LogTopic.IMS_EVENTS) {
            "Received inline suggestions response with ${inlineSuggestions.size} suggestion(s) provided."
        }
        return NlpInlineAutofill.showInlineSuggestions(this, inlineSuggestions)
    }

    override fun onComputeInsets(outInsets: Insets?) {
        if (outInsets == null) return
        val state = keyboardManager.activeState.snapshot()
        windowController.onComputeInsets(outInsets, state.isFullscreenInputRequired())
    }

    override fun getTextForImeAction(imeOptions: Int): String? {
        return try {
            when (imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_NONE -> null
                EditorInfo.IME_ACTION_GO -> resourcesContext.getString(AndroidInternalR.string.ime_action_go)
                EditorInfo.IME_ACTION_SEARCH -> resourcesContext.getString(AndroidInternalR.string.ime_action_search)
                EditorInfo.IME_ACTION_SEND -> resourcesContext.getString(AndroidInternalR.string.ime_action_send)
                EditorInfo.IME_ACTION_NEXT -> resourcesContext.getString(AndroidInternalR.string.ime_action_next)
                EditorInfo.IME_ACTION_DONE -> resourcesContext.getString(AndroidInternalR.string.ime_action_done)
                EditorInfo.IME_ACTION_PREVIOUS -> resourcesContext.getString(AndroidInternalR.string.ime_action_previous)
                else -> resourcesContext.getString(AndroidInternalR.string.ime_action_default)
            }
        } catch (_: Throwable) {
            super.getTextForImeAction(imeOptions)?.toString()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // DRS (H2): a throwing hardware-key path degrades to "not handled"
        // instead of crashing the IME process.
        val handled = runCatching { keyboardManager.onHardwareKeyDown(keyCode, event) }
            .onFailure { flogError(LogTopic.KEY_EVENTS) { "onHardwareKeyDown failed: $it" } }
            .getOrDefault(false)
        if (handled) return true
        // DRS v2.7.0: volume-key cursor control (opt-in). Only reached when
        // the hardware path declined the key, so physical-keyboard behavior
        // is never shadowed. Same shielded-degrade contract as above.
        val volumeHandled = runCatching { keyboardManager.onVolumeKeyCursor(keyCode) }
            .onFailure { flogError(LogTopic.KEY_EVENTS) { "volume-key cursor failed: $it" } }
            .getOrDefault(false)
        return volumeHandled || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // DRS p6 (E11): the up path is shielded exactly like the down path —
        // an unbalanced key-up after a down-side failure must not crash the
        // IME process.
        val handled = runCatching { keyboardManager.onHardwareKeyUp(keyCode, event) }
            .onFailure { flogError(LogTopic.KEY_EVENTS) { "onHardwareKeyUp failed: $it" } }
            .getOrDefault(false)
        if (handled) return true
        // DRS v2.7.0: a consumed volume DOWN must pair with a consumed UP,
        // otherwise the system half-handles the chord (stray click/beep).
        val volumeHandled = runCatching { keyboardManager.onVolumeKeyCursor(keyCode) }
            .onFailure { flogError(LogTopic.KEY_EVENTS) { "volume-key cursor failed: $it" } }
            .getOrDefault(false)
        return volumeHandled || super.onKeyUp(keyCode, event)
    }
}
