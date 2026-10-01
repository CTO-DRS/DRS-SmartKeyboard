/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

@file:Suppress("NOTHING_TO_INLINE")

package org.drs.lib.kotlin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

typealias DeferredResult<T> = Deferred<Result<T>>

inline fun <T> CoroutineScope.runCatchingAsync(
    crossinline block: suspend CoroutineScope.() -> T,
): DeferredResult<T> {
    return this.async {
        runCatching { block() }
    }
}

inline fun resultOk(): Result<Unit> {
    return Result.success(Unit)
}

inline fun <T> resultOk(value: T): Result<T> {
    return Result.success(value)
}

inline fun <T> resultErr(error: Throwable): Result<T> {
    return Result.failure(error)
}

inline fun <T> resultErrStr(error: String): Result<T> {
    return Result.failure(Exception(error))
}

inline fun Result<*>.throwOnFailure() {
    getOrThrow()
}
