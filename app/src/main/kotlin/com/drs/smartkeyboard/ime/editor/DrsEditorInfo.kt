/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.editor

import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.emoji2.text.EmojiCompat

class DrsEditorInfo private constructor(val base: EditorInfo) {
    val inputAttributes = InputAttributes.wrap(base.inputType)

    val imeOptions = ImeOptions.wrap(base.imeOptions)

    val isRichInputEditor: Boolean
        get() = inputAttributes.type != InputAttributes.Type.NULL

    val isRawInputEditor: Boolean
        get() = !isRichInputEditor

    val packageName: String?
        get() = base.packageName

    val initialSelection: EditorRange
        get() = if (base.initialSelStart >= 0 && base.initialSelEnd >= 0) {
            EditorRange.normalized(base.initialSelStart, base.initialSelEnd)
        } else {
            EditorRange.Unspecified
        }

    val initialCapsMode: InputAttributes.CapsMode
        get() = InputAttributes.CapsMode.fromFlags(base.initialCapsMode)

    fun getInitialTextBeforeCursor(n: Int): CharSequence? {
        if (n < 1) return null
        return EditorInfoCompat.getInitialTextBeforeCursor(base, n, 0)
    }

    fun getInitialTextAfterCursor(n: Int): CharSequence? {
        if (n < 1) return null
        return EditorInfoCompat.getInitialTextAfterCursor(base, n, 0)
    }

    fun getInitialSelectedText(): CharSequence? {
        return EditorInfoCompat.getInitialSelectedText(base, 0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DrsEditorInfo

        if (inputAttributes.raw != other.inputAttributes.raw) return false
        if (imeOptions.raw != other.imeOptions.raw) return false
        if (packageName != other.packageName) return false
        if (initialSelection != other.initialSelection) return false
        if (initialCapsMode != other.initialCapsMode) return false
        if (extractedActionLabel != other.extractedActionLabel) return false
        if (extractedActionId != other.extractedActionId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = inputAttributes.raw.hashCode()
        result = 31 * result + imeOptions.raw.hashCode()
        result = 31 * result + (packageName?.hashCode() ?: 0)
        result = 31 * result + initialSelection.hashCode()
        result = 31 * result + initialCapsMode.hashCode()
        result = 31 * result + (extractedActionLabel?.hashCode() ?: 0)
        result = 31 * result + extractedActionId
        return result
    }

    val contentMimeTypes: Array<String>
        get() = EditorInfoCompat.getContentMimeTypes(base)

    val extractedActionLabel: String?
        get() = base.actionLabel?.toString()

    val extractedActionId: Int
        get() = base.actionId

    val emojiCompatMetadataVersion: Int
        get() = base.extras?.getInt(EmojiCompat.EDITOR_INFO_METAVERSION_KEY, 0) ?: 0

    val emojiCompatReplaceAll: Boolean
        get() = base.extras?.getBoolean(EmojiCompat.EDITOR_INFO_REPLACE_ALL_KEY, false) ?: false

    companion object {
        val Unspecified = DrsEditorInfo(EditorInfo())

        fun wrap(editorInfo: EditorInfo) = DrsEditorInfo(editorInfo)
    }
}
