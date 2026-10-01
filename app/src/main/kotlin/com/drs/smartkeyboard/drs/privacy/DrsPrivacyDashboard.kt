/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.privacy

import android.content.Context
import java.io.File

/**
 * DRS roadmap phase 3 (privacy & trust, task 4): the privacy dashboard
 * engine — the single place that KNOWS what data the app keeps, where,
 * how big, and how to destroy or export it.
 *
 * The inventory is deliberately an allowlist (like the sync payload):
 * each store is registered here on purpose, with its honest numbers.
 * A new data store that forgets to register itself breaks the
 * documented contract (PRIVACY_POLICY.md points here) — and the store
 * IDs are stable strings so the UI translations and the policy doc can
 * reference them.
 *
 * Store map (phase 3 audit):
 *   - learning        : personalization words+bigrams (noBackupFilesDir)
 *   - crash_reports   : sanitized crash registry (noBackupFilesDir)
 *   - update_downloads: downloaded APK files awaiting install (cache)
 *   - clipboard_media : clipboard image/audio cache (cache)
 *
 * Actions:
 *   - summary()            : everything the dashboard renders.
 *   - deleteAll()          : one tap, actually deletes (returns per-store results).
 *   - exportEncrypted()    : the E2E bundle (learning data is the only
 *     personalization payload) via [DrsSyncBundle] — the passphrase
 *     never leaves the call.
 *
 * Core is pure (injectable store probes) and JVM-tested; the android()
 * factory wires the real files.
 */
class DrsPrivacyDashboard(
    private val stores: List<StoreProbe>,
    private val attestationProvider: () -> DrsNetworkSentinel.Attestation,
    private val learningProvider: () -> Pair<Map<String, Long>, Map<String, Map<String, Long>>>? = { null },
) {

    /** One known data store and how to measure/destroy it. */
    data class StoreProbe(
        val id: String,
        val location: String,
        val measure: () -> StoreUsage,
        val destroy: () -> Boolean,
    )

    data class StoreUsage(val bytes: Long, val items: Long, val lastModified: Long)

    data class StoreInfo(
        val id: String,
        val location: String,
        val bytes: Long,
        val items: Long,
        val lastModified: Long,
    )

    data class Summary(
        val stores: List<StoreInfo>,
        val totalBytes: Long,
        val totalItems: Long,
        val attestation: DrsNetworkSentinel.Attestation,
    )

    /** Per-store deletion result — honest reporting beats a boolean. */
    data class DeleteReport(val id: String, val deleted: Boolean)

    fun summary(): Summary {
        val infos = stores.map { probe ->
            val usage = runCatching(probe.measure).getOrElse { StoreUsage(0, 0, 0) }
            StoreInfo(probe.id, probe.location, usage.bytes, usage.items, usage.lastModified)
        }
        return Summary(
            stores = infos,
            totalBytes = infos.sumOf { it.bytes },
            totalItems = infos.sumOf { it.items },
            attestation = attestationProvider(),
        )
    }

    /** Deletes every registered store; a failing store never blocks the others. */
    fun deleteAll(): List<DeleteReport> = stores.map { probe ->
        DeleteReport(probe.id, runCatching(probe.destroy).getOrElse { false })
    }

    /**
     * Builds the E2E sync payload from the learning store and seals it.
     * Returns null when the learning provider has nothing (or is not
     * wired) — the UI then says "nothing to export" honestly.
     */
    fun exportEncrypted(
        appVersion: String,
        exportedAt: Long,
        passphrase: CharArray,
        iterations: Int = DrsE2ECrypto.DEFAULT_ITERATIONS,
    ): String? = exportEncrypted(
        learning = learningProvider(),
        appVersion = appVersion,
        exportedAt = exportedAt,
        passphrase = passphrase,
        iterations = iterations,
    )

    fun exportEncrypted(
        learning: Pair<Map<String, Long>, Map<String, Map<String, Long>>>?,
        appVersion: String,
        exportedAt: Long,
        passphrase: CharArray,
        iterations: Int = DrsE2ECrypto.DEFAULT_ITERATIONS,
    ): String? {
        if (learning == null) return null
        val (words, bigrams) = learning
        if (words.isEmpty() && bigrams.isEmpty()) return null
        val payload = DrsSyncBundle.Payload(
            app = appVersion,
            exportedAt = exportedAt,
            words = words,
            bigrams = bigrams,
        )
        return DrsSyncBundle.seal(payload, passphrase, iterations)
    }

    companion object {

        /** Stable store IDs (referenced by PRIVACY_POLICY.md and translations). */
        const val STORE_LEARNING = "learning"
        const val STORE_CRASH = "crash_reports"
        const val STORE_UPDATES = "update_downloads"
        const val STORE_CLIPBOARD_MEDIA = "clipboard_media"

        /** Recursive dir usage — items = file count. */
        fun dirUsage(dir: File?): StoreUsage {
            if (dir == null || !dir.exists()) return StoreUsage(0, 0, 0)
            var bytes = 0L
            var items = 0L
            var latest = 0L
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                bytes += f.length()
                items += 1
                latest = maxOf(latest, f.lastModified())
            }
            return StoreUsage(bytes, items, latest)
        }

        fun fileUsage(file: File?): StoreUsage {
            if (file == null || !file.exists()) return StoreUsage(0, 0, 0)
            return StoreUsage(file.length(), 1, file.lastModified())
        }

        /** Deletes a file or a directory tree; true when nothing remains. */
        fun destroyPath(file: File?): Boolean {
            if (file == null || !file.exists()) return true
            return if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }

        /**
         * Android factory: wires the four registered stores to their real
         * on-disk locations. The learning/crash engines are consulted
         * through their public APIs so the dashboard never reads their
         * internals; in-memory state is cleared by the same actions.
         */
        fun android(
            context: Context,
            attestationProvider: () -> DrsNetworkSentinel.Attestation,
            learningSnapshot: () -> Pair<Map<String, Long>, Map<String, Map<String, Long>>>?,
        ): DrsPrivacyDashboard {
            val appContext = context.applicationContext
            val learningFile = File(appContext.noBackupFilesDir, "drs_learning_v1.json")
            val clipboardMediaDir = File(
                appContext.noBackupFilesDir,
                com.drs.smartkeyboard.ime.clipboard.provider.ClipboardFileStorage.CLIPBOARD_FILES_PATH,
            )
            return DrsPrivacyDashboard(
                stores = listOf(
                    StoreProbe(
                        id = STORE_LEARNING,
                        location = "noBackupFilesDir/drs_learning_v1.json",
                        measure = { fileUsage(learningFile) },
                        destroy = {
                            com.drs.smartkeyboard.drs.ai.DrsLearningEngine.clear(learningFile)
                            true
                        },
                    ),
                    StoreProbe(
                        id = STORE_CRASH,
                        location = "noBackupFilesDir/drs_crash_reports.json",
                        measure = {
                            val (bytes, entries) = com.drs.smartkeyboard.drs.DrsCrashReports.get()
                                ?.diskUsage() ?: Pair(0L, 0L)
                            StoreUsage(bytes, entries, 0L)
                        },
                        destroy = {
                            com.drs.smartkeyboard.drs.DrsCrashReports.get()?.clear()
                            true
                        },
                    ),
                    StoreProbe(
                        id = STORE_UPDATES,
                        location = "cacheDir/drs_updates/",
                        measure = { dirUsage(com.drs.smartkeyboard.app.drsupdater.DrsUpdateCenter.downloadsDir(appContext)) },
                        destroy = { destroyPath(com.drs.smartkeyboard.app.drsupdater.DrsUpdateCenter.downloadsDir(appContext)) },
                    ),
                    StoreProbe(
                        id = STORE_CLIPBOARD_MEDIA,
                        location = "noBackupFilesDir/clipboard_media/",
                        measure = { dirUsage(clipboardMediaDir) },
                        destroy = { destroyPath(clipboardMediaDir) },
                    ),
                ),
                attestationProvider = attestationProvider,
                // The export path reads the live learning snapshot through
                // the same provider the sync bundle uses.
                learningProvider = learningSnapshot,
            )
        }
    }
}
