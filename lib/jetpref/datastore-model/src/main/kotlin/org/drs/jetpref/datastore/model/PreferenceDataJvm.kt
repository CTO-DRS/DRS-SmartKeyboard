/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

private class PreferenceDataImpl<V : Any>(
    override val key: String,
    override val default: V,
    override val type: PreferenceType,
    override val serializer: PreferenceSerializer<V>,
) : PreferenceData<V> {
    override val typedKey = PreferenceModel.TypedKey(type, key)
    private val cachedValue = AtomicReference<V?>(null)
    private var cachedValueFlow = MutableStateFlow(default)
    private val cachedValueWriteGuard = Mutex()
    private var valuePersistHandler: PreferenceData.ValuePersistHandler<V>? = null

    init {
        Validator.validateKey(key)
    }

    override fun get(): V = cachedValue.get() ?: default

    override fun getOrNull(): V? = cachedValue.get()

    override fun asFlow() = cachedValueFlow.asStateFlow()

    override suspend fun set(value: V) = cachedValueWriteGuard.withLock {
        cachedValue.set(value)
        cachedValueFlow.value = value
        valuePersistHandler?.onValueChanged(value) ?: Result.success(Unit)
    }

    override suspend fun reset() = cachedValueWriteGuard.withLock {
        cachedValue.set(null)
        cachedValueFlow.value = default
        valuePersistHandler?.onValueChanged(null) ?: Result.success(Unit)
    }

    override suspend fun init(
        value: V?,
        handler: PreferenceData.ValuePersistHandler<V>,
    ): Unit = cachedValueWriteGuard.withLock {
        cachedValue.set(value)
        cachedValueFlow.value = value ?: default
        valuePersistHandler = handler
    }
}

internal fun <V : Any> preferenceDataOf(
    key: String,
    default: V,
    type: PreferenceType,
    serializer: PreferenceSerializer<V>
): PreferenceData<V> {
    return PreferenceDataImpl(key, default, type, serializer)
}
