/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package org.drs.lib.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
@NonRestartableComposable
fun DisposableLifecycleEffect(
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    onResume: () -> Unit,
    onPause: () -> Unit,
) {
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onResume()
                Lifecycle.Event.ON_PAUSE -> onPause()
                else -> { }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}
