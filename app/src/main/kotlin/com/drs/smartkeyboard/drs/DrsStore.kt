/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Local-only store for the DRS adaptive layer (user path, profiles,
 * shortcuts, usage stats). All data stays on-device in a single small JSON
 * file inside noBackupFilesDir; nothing is ever sent to any server.
 *
 * The app is single-process (no android:process attributes in the manifest),
 * so this object is safely shared between the IME service and app activities.
 */
object DrsStore {
    private const val FILE_NAME = "drs_state.json"

    /**
     * DRS (B2): minimum interval between two durable writes of the
     * coalesced [update] path. The DRS state file is tiny, but it used to
     * be rewritten on EVERY keystroke-level award — one atomic write per
     * 15s (plus a trailing flush when the keyboard hides) is plenty.
     */
    private const val MIN_WRITE_INTERVAL_MS = 15_000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // DRS (B2): coalescing bookkeeping, guarded by [coalesceLock]. At most
    // ONE trailing Job exists; every change requested while it is pending
    // is folded into [pendingDeferred] in request order.
    private val coalesceLock = Any()
    @Volatile
    private var lastWriteAtMs = 0L
    private var pendingDeferred: ((DrsState) -> DrsState)? = null
    private var trailingFlushJob: Job? = null

    private val _state = MutableStateFlow(DrsState())
    val state: StateFlow<DrsState> = _state.asStateFlow()

    /** Process-wide guard so the onboarding is only launched once per launch request. */
    @Volatile
    var onboardingLaunchGuard: Boolean = false

    @Volatile
    private var file: File? = null

    /**
     * Loads the persisted state synchronously. The file is tiny (a few KB at
     * most) so the one-time read at process start is negligible.
     */
    fun init(context: Context) {
        if (file != null) return
        synchronized(this) {
            if (file != null) return
            val f = File(context.noBackupFilesDir, FILE_NAME)
            val loaded = if (f.isFile) {
                try {
                    json.decodeFromString<DrsState>(f.readText())
                } catch (_: Throwable) {
                    DrsState()
                }
            } else {
                DrsState()
            }
            // Publish state BEFORE exposing the file, so a racing update() can
            // never write the default state over the loaded one.
            _state.value = loaded
            file = f
        }
    }

    /**
     * Asynchronous state transformation (safe to call from anywhere).
     *
     * DRS (B2): the default path is COALESCED — deferred changes fold into
     * a single pending transform and one trailing Job writes them at most
     * once per [MIN_WRITE_INTERVAL_MS] (the Job fires after the remainder
     * of the interval since the last durable write has elapsed). Pass
     * [immediate] = true for the durable atomic path (economy, backup,
     * onboarding-critical writes); the immediate path also absorbs any
     * pending deferred change so nothing is lost or reordered.
     */
    fun update(immediate: Boolean = false, transform: (DrsState) -> DrsState) {
        if (immediate) {
            scope.launch { updateNow(transform) }
            return
        }
        val now = System.currentTimeMillis()
        synchronized(coalesceLock) {
            val previous = pendingDeferred
            pendingDeferred = if (previous == null) {
                transform
            } else {
                // Preserve request order: the earlier change applies first.
                { state -> transform(previous(state)) }
            }
            if (trailingFlushJob != null) return
            val delayMs = (lastWriteAtMs + MIN_WRITE_INTERVAL_MS - now).coerceAtLeast(0L)
            trailingFlushJob = scope.launch {
                delay(delayMs)
                flushPendingDeferred()
            }
        }
    }

    /** Synchronous (suspend) state transformation with atomic file write. */
    suspend fun updateNow(transform: (DrsState) -> DrsState) {
        mutex.withLock {
            // DRS (B2): a durable write absorbs any pending deferred change
            // so the deferred work is neither lost nor applied out of order.
            val pending = synchronized(coalesceLock) {
                val p = pendingDeferred
                pendingDeferred = null
                trailingFlushJob?.cancel()
                trailingFlushJob = null
                p
            }
            val next = if (pending != null) transform(pending(_state.value)) else transform(_state.value)
            _state.value = next
            write(next)
            lastWriteAtMs = System.currentTimeMillis()
        }
    }

    /**
     * DRS (B2): applies and clears the pending deferred change, if any.
     * Guarded so a racing [updateNow] can never double-apply it.
     */
    private suspend fun flushPendingDeferred() {
        val pending = synchronized(coalesceLock) {
            val p = pendingDeferred
            pendingDeferred = null
            trailingFlushJob = null
            p
        } ?: return
        updateNow(pending)
    }

    /**
     * DRS (B2): requests a flush of any pending deferred change without
     * waiting for the trailing interval. Fire-and-forget; the flush runs
     * on the store scope. Intended to be wired to the IME's
     * onWindowHidden transition so a deferred change never sits
     * unpersisted while the keyboard is away.
     */
    fun flushAsync() {
        scope.launch { flushPendingDeferred() }
    }

    private fun write(state: DrsState) {
        val f = file ?: return
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(state))
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (t: Throwable) {
            // Persisting the adaptive layer must never crash the keyboard.
            // DRS v1.0.6: record the failure in the sanitized event log so
            // diagnostics can show WHY data was not persisted.
            runCatching {
                DrsEventLog.recordError(
                    DrsEventLog.Categories.STORE,
                    DrsEventLog.throwableDetail(t),
                )
            }
        }
    }

    /** Wipes all DRS-layer local data (profiles, shortcuts, stats). */
    suspend fun resetAll() {
        updateNow { DrsState(onboardingDone = true) }
    }

    /**
     * Self-check used by the diagnostics screen: reports whether the store
     * is initialized and its backing directory is writable. Never throws.
     */
    fun storageHealthy(): Boolean {
        return try {
            val f = file ?: return false
            f.parentFile?.canWrite() == true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * DRS v1.6.0: re-decodes the persisted state file to verify it still
     * parses. [init] silently falls back to a default [DrsState] when the
     * file is corrupt — without this check the user's whole DRS layer is
     * wiped by the next write with ZERO signal. States:
     *  - `true`  — the file parses (or there is no file yet: fresh install)
     *  - `false` — the file exists but fails to decode (DATA LOSS pending)
     *  - `null`  — the store is not initialized yet (unknown)
     * Never throws.
     */
    fun stateFileParses(): Boolean? {
        return try {
            val f = file ?: return null
            if (!f.isFile) return true
            json.decodeFromString<DrsState>(f.readText())
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * DRS v1.0.6: real on-disk size of the state file in bytes (0 when the
     * store is not initialized yet). Surfaced in the performance screen -
     * measured, never estimated.
     */
    fun fileSizeBytes(): Long {
        return try {
            file?.takeIf { it.isFile }?.length() ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }
}
