/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.android

import android.annotation.SuppressLint
import android.content.res.Resources

/**
 * Helper class for retrieving `com.android.internal.R.*` resources.
 *
 * Usage of this ids should always be done within a try..catch block, as there may be devices which have completely
 * modified system resources or something has changed in a newer Android version.
 */
object AndroidInternalR {
    @SuppressLint("DiscouragedApi")
    @Suppress("ClassName")
    object string {
        val ime_action_go by lazy {
            Resources.getSystem().getIdentifier("ime_action_go", "string", "android")
        }
        val ime_action_search by lazy {
            Resources.getSystem().getIdentifier("ime_action_search", "string", "android")
        }
        val ime_action_send by lazy {
            Resources.getSystem().getIdentifier("ime_action_send", "string", "android")
        }
        val ime_action_next by lazy {
            Resources.getSystem().getIdentifier("ime_action_next", "string", "android")
        }
        val ime_action_done by lazy {
            Resources.getSystem().getIdentifier("ime_action_done", "string", "android")
        }
        val ime_action_previous by lazy {
            Resources.getSystem().getIdentifier("ime_action_previous", "string", "android")
        }
        val ime_action_default by lazy {
            Resources.getSystem().getIdentifier("ime_action_default", "string", "android")
        }
    }

    @SuppressLint("DiscouragedApi")
    @Suppress("ClassName")
    object drawable {
        val ic_qs_one_handed_mode by lazy {
            Resources.getSystem().getIdentifier("ic_qs_one_handed_mode", "drawable", "android")
        }
    }
}
