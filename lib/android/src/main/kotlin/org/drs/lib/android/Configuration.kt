/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.android

import android.content.res.Configuration

fun Configuration.isOrientationPortrait(): Boolean {
    return this.orientation == Configuration.ORIENTATION_PORTRAIT
}

fun Configuration.isOrientationLandscape(): Boolean {
    return this.orientation == Configuration.ORIENTATION_LANDSCAPE
}
