/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.android

import android.content.Context
import android.database.ContentObserver
import android.os.Handler

fun interface OnSystemSettingsChangedListener {
    fun onChanged()
}

class SystemSettingsObserver(
    context: Context,
    private val listener: OnSystemSettingsChangedListener,
) : ContentObserver(Handler(context.mainLooper)) {

    override fun deliverSelfNotifications(): Boolean {
        return true
    }

    override fun onChange(selfChange: Boolean) {
        listener.onChanged()
    }
}
