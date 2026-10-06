/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.ime.editor.DrsEditorInfo
import com.drs.smartkeyboard.ime.editor.InputAttributes
import com.drs.smartkeyboard.ime.smartbar.DrsFieldKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Derives the [DrsContextMode] from the focused editor (EditorInfo) every time
 * input starts. The mode drives adaptive UI (e.g. hiding the technical strip
 * in password fields, preferring numbers in numeric fields).
 *
 * Detection is intentionally conservative and fully on-device.
 */
object DrsRuntimeState {

    private val _contextMode = MutableStateFlow(DrsContextMode.NORMAL)
    val contextMode: StateFlow<DrsContextMode> = _contextMode.asStateFlow()

    /**
     * DRS v1.2.0: the raw number-field class of the focused editor —
     * the input the smart numbers panel (لوحة الأرقام الذكية) detects
     * its context from. Set at EVERY input start BEFORE the
     * context-modes pref gate: the raw EditorInfo truth is not a user
     * preference, the panel reads it regardless of the adaptive-UI
     * switch (like every usage counter, it is counted but never
     * recorded when disabled).
     */
    private val _numberFieldClass = MutableStateFlow(DrsNumberFieldClass.GENERAL)
    val numberFieldClass: StateFlow<DrsNumberFieldClass> = _numberFieldClass.asStateFlow()

    /**
     * DRS v2.11.0 «المُرتّب السياقي الصادق»: the fine-grained field kind
     * the smartbar's contextual ranker reads. Set at EVERY input start
     * BEFORE the context-modes pref gate — the declared EditorInfo truth
     * is not a user preference (same covenant as [numberFieldClass]):
     * the coarse [DrsContextMode] keeps its stable stats contract while
     * this classification stays lossless (URI fields are URI, email
     * fields are EMAIL, filter bars are SEARCH_FILTER — nothing folded
     * away). Attributes only, never field content.
     */
    private val _fieldKind = MutableStateFlow(DrsFieldKind.GENERAL)
    val fieldKind: StateFlow<DrsFieldKind> = _fieldKind.asStateFlow()

    /**
     * DRS v1.16.0: the open slot editor of the fixed tasks bar — null
     * when closed, otherwise the index of the slot being changed
     * («إمكانية تغيير المهام»). Runtime-only state like the drawer flag;
     * the panel itself reads [DrsStore] for everything persisted.
     */
    private val _stripSlotEditor = MutableStateFlow<Int?>(null)
    val stripSlotEditor: StateFlow<Int?> = _stripSlotEditor.asStateFlow()

    /** Opens the slot editor for bar slot [index]. */
    fun openStripSlotEditor(index: Int) {
        _stripSlotEditor.value = index
    }

    /** Closes the slot editor (no-op when already closed). */
    fun closeStripSlotEditor() {
        _stripSlotEditor.value = null
    }

    /** Packages whose names hint at technical/coding usage. */
    private val codingPackageHints = listOf(
        "termux", "code", "coder", "ide", "terminal", "kotlin", "studio",
        "github", "gitlab", "git.", "vim", "emacs", "nano", "editor",
    )

    /** Called from EditorInstance.handleStartInputView for every new input. */
    fun onInputStarted(editorInfo: DrsEditorInfo) {
        // DRS v1.2.0: the raw number-field class lands FIRST — before
        // the pref gate returns early — so the smart numbers panel sees
        // every field, not just the adaptive-UI-enabled ones.
        _numberFieldClass.value = detectNumberFieldClass(editorInfo)
        // DRS v2.11.0: the fine field kind lands beside it, also before
        // the gate — the honest ranker reads declared attributes, not
        // adaptive-UI switches.
        _fieldKind.value = DrsFieldKind.kindOf(
            editorInfo.inputAttributes,
            isCodingPackage(editorInfo.packageName),
        )
        val state = DrsStore.state.value
        if (!state.contextModesEnabled) {
            _contextMode.value = DrsContextMode.NORMAL
            return
        }
        _contextMode.value = detect(editorInfo, state.userPath)
        // DRS v1.7.0: the detected mode was discarded before — it only
        // drove adaptive UI. Now the (anonymous) START of every input in
        // each mode is counted so the stats screen shows how typing time
        // splits across contexts (password/numbers/coding/...).
        DrsAdaptationEngine.recordContextStart(_contextMode.value.name)
    }

    private fun detect(editorInfo: DrsEditorInfo, userPath: String): DrsContextMode {
        val variation = editorInfo.inputAttributes.variation
        val type = editorInfo.inputAttributes.type
        return when {
            variation == InputAttributes.Variation.PASSWORD ||
                variation == InputAttributes.Variation.VISIBLE_PASSWORD ||
                variation == InputAttributes.Variation.WEB_PASSWORD ->
                DrsContextMode.PASSWORD

            type == InputAttributes.Type.NUMBER ||
                type == InputAttributes.Type.PHONE ||
                type == InputAttributes.Type.DATETIME ->
                DrsContextMode.NUMBERS

            variation == InputAttributes.Variation.URI ->
                DrsContextMode.SEARCH

            variation == InputAttributes.Variation.EMAIL_ADDRESS ||
                variation == InputAttributes.Variation.WEB_EMAIL_ADDRESS ->
                DrsContextMode.WRITING

            isCodingPackage(editorInfo.packageName) ->
                DrsContextMode.CODING

            userPath == DrsUserPath.TECHNICAL.name ->
                DrsContextMode.TECHNICAL

            else -> DrsContextMode.NORMAL
        }
    }

    private fun isCodingPackage(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        val name = packageName.lowercase()
        return codingPackageHints.any { name.contains(it) }
    }

    /**
     * DRS v1.2.0: the raw field class of the number panel — OTP (a
     * number+password field), PHONE, DATE, MONEY (a decimal field),
     * MATH (a plain number field) or GENERAL. Pure and honest: no
     * guessing beyond what the editor actually declares.
     */
    private fun detectNumberFieldClass(editorInfo: DrsEditorInfo): DrsNumberFieldClass {
        val attributes = editorInfo.inputAttributes
        return when {
            attributes.type == InputAttributes.Type.NUMBER &&
                attributes.variation == InputAttributes.Variation.PASSWORD ->
                DrsNumberFieldClass.OTP

            attributes.type == InputAttributes.Type.PHONE ->
                DrsNumberFieldClass.PHONE

            attributes.type == InputAttributes.Type.DATETIME ->
                DrsNumberFieldClass.DATE

            attributes.type == InputAttributes.Type.NUMBER && attributes.flagNumberDecimal ->
                DrsNumberFieldClass.MONEY

            attributes.type == InputAttributes.Type.NUMBER ->
                DrsNumberFieldClass.MATH

            else -> DrsNumberFieldClass.GENERAL
        }
    }

}
