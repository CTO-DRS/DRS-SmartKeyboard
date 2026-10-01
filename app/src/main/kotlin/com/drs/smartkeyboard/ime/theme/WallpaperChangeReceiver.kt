/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drs.smartkeyboard.lib.devtools.flogDebug
import com.drs.smartkeyboard.themeManager
import kotlinx.coroutines.flow.update

class WallpaperChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        if (context == null) return
        @Suppress("DEPRECATION") // We do not retrieve the wallpaper but only listen to changes
        if (intent.action == Intent.ACTION_WALLPAPER_CHANGED) {
            flogDebug { "Wallpaper changed" }
            val themeManager by context.themeManager()
            themeManager.configurationChangeCounter.update { it + 1 }
        }
    }
}
