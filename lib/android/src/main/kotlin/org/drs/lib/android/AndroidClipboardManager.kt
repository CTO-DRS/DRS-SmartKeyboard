/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.android

typealias AndroidClipboardManager = android.content.ClipboardManager
// TODO: remove this once https://youtrack.jetbrains.com/issue/KT-34281 is fixed
typealias AndroidClipboardManager_OnPrimaryClipChangedListener = android.content.ClipboardManager.OnPrimaryClipChangedListener

fun AndroidClipboardManager.clearPrimaryClipAnyApi() {
    if (AndroidVersion.ATLEAST_API28_P) {
        this.clearPrimaryClip()
    } else {
        this.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
    }
}

fun AndroidClipboardManager.setOrClearPrimaryClip(clip: android.content.ClipData?) {
    if (clip != null) {
        this.setPrimaryClip(clip)
    } else {
        this.clearPrimaryClipAnyApi()
    }
}
