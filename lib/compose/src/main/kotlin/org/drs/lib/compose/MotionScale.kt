/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.compose

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * DRS a11y/i18n/ux (r0-I): reduced-motion gate shared by every looping/decorative animation in the
 * app and the IME.
 *
 * Returns `true` when the system-wide animator duration scale
 * ([Settings.Global.ANIMATOR_DURATION_SCALE]) is `0` — i.e. the user (or a battery saver / a11y
 * service) disabled animations. Callers must treat `true` as "never start an infinite transition":
 * render the settled/static state directly (for example a solid pulse-color instead of a pulse
 * animation), so no animator is created at all.
 *
 * The scale is read once synchronously and then kept up to date through a [ContentObserver] on the
 * setting's URI, so toggling "Remove animations" in the system accessibility settings takes effect
 * live without recreating the composition.
 *
 * Known consumers: voice input pulse (KeyboardManager voice bar), editor record blink, and any
 * future gate that must honor the reduced-motion preference.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val animatorScaleUri = remember { Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE) }

    var animatorScale by remember {
        mutableFloatStateOf(
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }

    DisposableEffect(resolver, animatorScaleUri) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                animatorScale = Settings.Global.getFloat(
                    resolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }
        }
        resolver.registerContentObserver(animatorScaleUri, false, observer)
        onDispose {
            resolver.unregisterContentObserver(observer)
        }
    }

    return animatorScale == 0f
}
