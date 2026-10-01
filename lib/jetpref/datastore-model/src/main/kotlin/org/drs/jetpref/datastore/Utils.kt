/*
 * Copyright (C) 2021-2026 The DRS Smart Keyboard Project
 */

package org.drs.jetpref.datastore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consume

@Throws(CancellationException::class)
internal inline fun <T, R> T.runCatchingCancellationAware(block: T.() -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

internal suspend fun <E> Channel<E>.consumeFirst(): E {
    return consume { return@consume receive() }
}
