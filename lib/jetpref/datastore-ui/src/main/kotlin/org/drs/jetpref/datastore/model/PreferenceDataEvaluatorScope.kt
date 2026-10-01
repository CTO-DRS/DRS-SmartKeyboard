/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore.model

import androidx.compose.runtime.Composable

typealias PreferenceDataEvaluator = @Composable PreferenceDataEvaluatorScope.() -> Boolean

object PreferenceDataEvaluatorScope {
    @Composable
    infix fun <V : Any> PreferenceData<V>.isEqualTo(other: PreferenceData<V>): Boolean {
        val pref1 = this.collectAsState()
        val pref2 = other.collectAsState()
        return pref1.value == pref2.value
    }

    @Composable
    infix fun <V : Any> PreferenceData<V>.isEqualTo(other: V): Boolean {
        val pref = this.collectAsState()
        return pref.value == other
    }

    @Composable
    infix fun <V : Any> V.isEqualTo(other: PreferenceData<V>): Boolean {
        val pref = other.collectAsState()
        return this == pref.value
    }

    @Composable
    infix fun <V : Any> PreferenceData<V>.isNotEqualTo(other: PreferenceData<V>): Boolean {
        return !(this isEqualTo other)
    }

    @Composable
    infix fun <V : Any> PreferenceData<V>.isNotEqualTo(other: V): Boolean {
        return !(this isEqualTo other)
    }

    @Composable
    infix fun <V : Any> V.isNotEqualTo(other: PreferenceData<V>): Boolean {
        return !(this isEqualTo other)
    }

    @Composable
    fun PreferenceData<Boolean>.isTrue(): Boolean {
        return this isEqualTo true
    }

    @Composable
    fun PreferenceData<Boolean>.isFalse(): Boolean {
        return this isEqualTo false
    }
}
