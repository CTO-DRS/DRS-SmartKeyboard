/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState

@Composable
@Deprecated(message = "Use collectAsState for flow like constructs", ReplaceWith("collectAsState()"))
fun <V : Any> PreferenceData<V>.observeAsState(): State<V> {
    return asFlow().collectAsState()
}

@Composable
fun <V : Any> PreferenceData<V>.collectAsState(): State<V> {
    return asFlow().collectAsState()
}

@Composable
@Deprecated(message = "Use collectAsState for flow like constructs", ReplaceWith("collectAsState(initial)"))
fun <V : Any> PreferenceData<V>.observeAsState(initial: V): State<V> {
    return asFlow().collectAsState(initial)
}

@Composable
fun <V : Any> PreferenceData<V>.collectAsState(initial: V): State<V> {
    return asFlow().collectAsState(initial)
}
