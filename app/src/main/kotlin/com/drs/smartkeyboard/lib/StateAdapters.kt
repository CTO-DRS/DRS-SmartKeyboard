/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import org.drs.jetpref.datastore.model.PreferenceData
import kotlinx.coroutines.flow.map

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
inline fun <V : Any, R : Any> PreferenceData<V>.observeAsTransformingState(
    crossinline transform: @DisallowComposableCalls (V) -> R,
): State<R> {
    return asFlow().let { flow ->
        flow.map { transform(it) }.collectAsState(transform(flow.value))
    }
}
