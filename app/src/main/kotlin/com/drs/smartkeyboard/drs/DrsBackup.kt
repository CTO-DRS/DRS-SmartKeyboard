/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.drs.smartkeyboard.R
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.Json
import org.drs.lib.kotlin.tryOrNull

/**
 * DRS v1.0.6: export/import of the DRS layer state (profiles, shortcuts,
 * wallet, usage counters, technical toolbar arrangement) as a single JSON
 * file chosen via the system file picker.
 *
 * Import is strictly validated BEFORE anything is applied:
 *  1. The file must parse as JSON.
 *  2. It must deserialize into [DrsState] (unknown keys ignored).
 *  3. Structural sanity: version >= 1 and userPath must be a known system.
 *
 * A file failing any check is REJECTED with an error message - a corrupt or
 * malicious file can never crash the app or replace the state with garbage.
 * Only the state fields listed in [DrsState] are touched; nothing else in
 * the app is modified by an import.
 */
object DrsBackup {

    /**
     * DRS v1.5.0: the highest state-schema version this build understands.
     * A file written by a NEWER build may carry fields this build drops
     * silently on import (ignoreUnknownKeys) — restoring it here would
     * destroy data on a downgrade-restore, so it is rejected up front.
     */
    const val SUPPORTED_STATE_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
    }

    fun defaultFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "drs-keyboard-state-$stamp.json"
    }

    /** Serializes the current DRS state. Never throws. */
    fun exportJson(): String = try {
        json.encodeToString(DrsStore.state.value)
    } catch (_: Throwable) {
        "{}"
    }

    /** Outcome of a validated import attempt. */
    data class ImportResult(
        val success: Boolean,
        val errorRes: Int? = null,
    )

    /**
     * DRS v1.5.0: structural validation of a parsed state, split out of
     * [importFrom] so it stays pure and JVM-testable. Returns null when
     * the state is acceptable, else the error message resource.
     */
    fun validateParsed(parsed: DrsState): Int? {
        if (parsed.version < 1) {
            return R.string.drs__diagnostics__backup_error_invalid
        }
        // DRS v1.5.0: reject files from a NEWER schema — this build would
        // silently drop the fields it does not know, destroying data.
        if (parsed.version > SUPPORTED_STATE_VERSION) {
            return R.string.drs__diagnostics__backup_error_version_newer
        }
        if (parsed.userPath !in listOf(
                DrsUserPath.NORMAL.name,
                DrsUserPath.TECHNICAL.name,
                DrsUserPath.HYBRID.name,
            ) && parsed.userPath !in listOf("CUSTOM")
        ) {
            return R.string.drs__diagnostics__backup_error_invalid
        }
        return null
    }

    fun importFrom(context: Context, uri: Uri): ImportResult {
        val parsed = parseFrom(context, uri) ?: return ImportResult(
            false,
            R.string.drs__diagnostics__backup_error_invalid,
        )
        return importParsed(parsed)
    }

    /**
     * DRS v1.7.0: reads and parses a backup file WITHOUT applying it —
     * the first half of [importFrom], split out so the UI can show a
     * describe-before-restore preview ([describeBackup]) and only then
     * confirm through [importParsed]. Returns null on read/parse failure
     * (the caller shows the shared invalid-file message).
     *
     * DRS p8 (S-5): the old body buffered the WHOLE stream
     * (`stream.readBytes()`) before consulting [MAX_IMPORT_BYTES] — a
     * multi-GB file picked in the SAF dialog was fully buffered (OOM) before
     * validation ever ran. Now the provider's declared OpenableColumns.SIZE
     * is rejected up front (when the provider reports one), and the stream
     * is read through a bounded buffer that aborts the moment the cap is
     * exceeded — the declared size can lie. The error surface is unchanged:
     * every over-cap/reject path returns null → the shared invalid-file
     * message.
     *
     * DRS p9 (T-6): the bounded 64KiB-chunk read lives in [readBounded]
     * (pure, JVM-testable). The old trailing `raw.length > MAX_IMPORT_BYTES`
     * char-count re-check was DEAD code — the chunk loop already guarantees
     * the byte cap — so it is gone; the byte cap stays the single
     * authority. The declared-size pre-check is untouched.
     */
    fun parseFrom(context: Context, uri: Uri): DrsState? {
        val declaredSize = tryOrNull { queryDeclaredSize(context, uri) }
        if (declaredSize != null && declaredSize > MAX_IMPORT_BYTES) return null
        val raw = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                readBounded(stream, MAX_IMPORT_BYTES)?.toString(Charsets.UTF_8)
            } ?: return null
        } catch (_: Throwable) {
            return null
        }
        return try {
            json.decodeFromString<DrsState>(raw)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * DRS p9 (T-6): reads [stream] through 64KiB chunks and returns the
     * full bytes, or null the moment the next chunk would push the total
     * past [maxBytes] (nothing beyond the cap is ever buffered). Pure and
     * JVM-testable — extracted verbatim from [parseFrom], which used to
     * keep an unreachable char-count re-check after it.
     */
    internal fun readBounded(stream: InputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream(64 * 1024)
        val chunk = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            out.write(chunk, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * DRS p8 (S-5): the content provider's declared size for [uri] — null
     * when the provider does not report one (the bounded read is then the
     * only guard).
     */
    private fun queryDeclaredSize(context: Context, uri: Uri): Long? {
        return context.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
            }
    }

    /**
     * DRS v1.7.0: human-readable counts of what a backup file contains —
     * shown in a confirm dialog BEFORE a restore overwrites live state
     * (the import used to apply immediately: the only destructive
     * unconfirmed action in the app). Pure and JVM-testable.
     */
    data class BackupPreview(
        val version: Int,
        val shortcuts: Int,
        val profiles: Int,
        val walletTotal: Long,
        val daysRecorded: Int,
        val activeDayCount: Int,
    )

    fun describeBackup(parsed: DrsState): BackupPreview = with(DrsDailyStats) {
        BackupPreview(
            version = parsed.version,
            shortcuts = parsed.shortcuts.size,
            profiles = parsed.profiles.size,
            // The wallet tracks three independent system balances — the
            // preview shows their real sum, never a fabricated "points" field.
            walletTotal = parsed.wallet.normal + parsed.wallet.technical + parsed.wallet.hybrid,
            daysRecorded = parsed.dailyStats.size,
            activeDayCount = parsed.dailyStats.values.count { it.hasActivity() },
        )
    }

    /**
     * DRS v1.7.0: applies an ALREADY-PARSED state after [validateParsed] —
     * the second half of [importFrom], so the preview-then-confirm flow
     * runs the exact same validation and update path the direct import
     * always did.
     */
    fun importParsed(parsed: DrsState): ImportResult {
        val validationError = validateParsed(parsed)
        if (validationError != null) {
            return ImportResult(false, validationError)
        }
        return try {
            // Preserve runtime-only flags that must not travel with a backup:
            // the onboarding flow is NOT re-triggered by a restore.
            val restored = parsed.copy(onboardingDone = DrsStore.state.value.onboardingDone)
            // DRS (B2): a restore is economy-critical — durable write, never coalesced.
            DrsStore.update(immediate = true) { restored }
            DrsEventLog.recordInfo(DrsEventLog.Categories.STORE, "state imported (${parsed.shortcuts.size} shortcuts)")
            ImportResult(true)
        } catch (_: Throwable) {
            ImportResult(false, R.string.drs__diagnostics__backup_error_invalid)
        }
    }

    private const val MAX_IMPORT_BYTES = 4 * 1024 * 1024
}
