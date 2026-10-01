/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.android

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

private const val SETTINGS_ACTIVITY_NAME = "com.drs.smartkeyboard.SettingsLauncherAlias"

fun Context.hideAppIcon() {
    val pkg: PackageManager = this.packageManager
    pkg.setComponentEnabledSetting(
        ComponentName(this, SETTINGS_ACTIVITY_NAME),
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP
    )
}

fun Context.showAppIcon() {
    val pkg: PackageManager = this.packageManager
    pkg.setComponentEnabledSetting(
        ComponentName(this, SETTINGS_ACTIVITY_NAME),
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP
    )
}
