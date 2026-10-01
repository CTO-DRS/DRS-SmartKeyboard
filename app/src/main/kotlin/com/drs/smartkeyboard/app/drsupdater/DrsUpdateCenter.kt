/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.drsupdater

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.drs.smartkeyboard.BuildConfig
import com.drs.smartkeyboard.drs.privacy.DrsPrivacyLock
import com.drs.smartkeyboard.lib.devtools.LogTopic
import com.drs.smartkeyboard.lib.devtools.flogError
import com.drs.smartkeyboard.lib.devtools.flogInfo
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * How often the app checks GitHub for a newer official release.
 *
 * MANUAL is the honest default of an opt-in system: no network request is
 * ever made unless the user opens the update center or opts into a schedule.
 */
enum class UpdateCheckMode {
    OFF,
    DAILY,
    WEEKLY,
    MANUAL,
}

/** Metadata of the latest official GitHub release (app update). */
data class ReleaseInfo(
    /** Raw tag as published, e.g. "v1.0.0". */
    val tag: String,
    /** Tag without the leading "v", e.g. "1.0.0". */
    val version: String,
    /** Human release title. */
    val releaseName: String,
    /** Release notes body (Markdown as authored in the release). */
    val body: String?,
    /** ISO-8601 publish timestamp of the release. */
    val publishedAt: String,
    /** APK asset file name, e.g. "DRS-Smart-Keyboard-v1.0.0.apk". */
    val assetName: String,
    /** Browser download URL of the APK asset. */
    val assetUrl: String,
    /** APK asset size in bytes. */
    val assetSize: Long,
    /** Browser download URL of SHA256SUMS.txt, when published. */
    val checksumsUrl: String?,
)

/**
 * DRS Update Center — real in-app app-update machinery backed by GitHub
 * Releases only (no scraping, no third-party stores):
 *
 * 1. [checkForUpdates] queries the official `releases/latest` API endpoint.
 * 2. [startDownload] streams the official APK asset with byte-accurate
 *    progress and HTTP Range based pause/resume.
 * 3. [verifyDownload] enforces SHA-256 (from the release's SHA256SUMS.txt)
 *    AND the expected file size before an install is ever offered.
 * 4. [installApk] hands the verified file to the platform package installer
 *    through the standard FileProvider + VIEW intent flow.
 *
 * The updater never crashes the app offline: every network failure lands in
 * a typed error state that the UI renders as a friendly banner.
 */
object DrsUpdateCenter {
    private const val TAG = "DrsUpdateCenter"

    private const val NOTIFICATION_CHANNEL_ID = "drs_update_center"
    private const val NOTIFICATION_ID_UPDATE = 0xD250001

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _checkState = MutableStateFlow<CheckState>(CheckState.Idle)
    val checkState: StateFlow<CheckState> = _checkState.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null

    sealed interface CheckState {
        data object Idle : CheckState
        data object Checking : CheckState
        data class UpToDate(val currentVersion: String, val latestVersion: String) : CheckState
        data class Available(val info: ReleaseInfo) : CheckState
        data class Failed(val message: String) : CheckState
    }

    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val received: Long, val total: Long, val bytesPerSecond: Long) : DownloadState
        data class Paused(val received: Long, val total: Long) : DownloadState
        data object Verifying : DownloadState
        data class Ready(val file: File, val sha256: String) : DownloadState
        data class Failed(val message: String) : DownloadState
    }

    fun currentVersion(): String = BuildConfig.VERSION_NAME

    // -------------------------------------------------------------------------
    // Version comparison (numeric-dot aware: 1.10.0 > 1.9.0)
    // -------------------------------------------------------------------------

    fun compareVersions(current: String, latest: String): Int {
        val cur = current.substringBefore('-').split('.').map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val lat = latest.substringBefore('-').split('.').map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(cur.size, lat.size)) {
            val c = cur.getOrElse(i) { 0 }
            val l = lat.getOrElse(i) { 0 }
            if (c != l) return c.compareTo(l)
        }
        return 0
    }


    fun isNewer(latest: String, current: String = currentVersion()): Boolean =
        compareVersions(current = current, latest = latest) > 0

    // -------------------------------------------------------------------------
    // Release check (GitHub releases/latest)
    // -------------------------------------------------------------------------

    suspend fun checkForUpdates(force: Boolean = true): CheckState {
        // DRS roadmap phase 3 (privacy & trust): the absolute privacy mode
        // refuses the check outright — the gate lives here (single choke
        // point, not at the UI call sites) so every current and future
        // caller inherits it.
        if (!DrsPrivacyLock.allows(DrsPrivacyLock.NetworkSurface.UPDATE_CHECK)) {
            val state = CheckState.Failed("absolute privacy mode: update checks are disabled")
            _checkState.value = state
            return state
        }
        _checkState.value = CheckState.Checking
        val state = withContext(Dispatchers.IO) {
            fetchLatestRelease().fold(
                onSuccess = { info ->
                    if (isNewer(latest = info.version)) {
                        CheckState.Available(info)
                    } else {
                        CheckState.UpToDate(currentVersion(), info.version)
                    }
                },
                onFailure = { CheckState.Failed(it.localizedMessage ?: "unknown error") },
            )
        }
        _checkState.value = state
        return state
    }

    suspend fun fetchLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(BuildConfig.DRS_RELEASES_API) ?: throw IOException("no connection")
            val body = connection.inputStream.use { it.readBytes().decodeToString() }
            connection.disconnect()
            parseRelease(body) ?: throw IOException("release payload missing an APK asset")
        }
    }

    internal fun parseRelease(payload: String): ReleaseInfo? {
        val root = json.parseToJsonElement(payload).jsonObject
        val tag = root["tag_name"]?.jsonPrimitive?.content ?: return null
        val assets = root["assets"]?.jsonArray ?: return null
        var apk: Pair<String, String>? = null
        var apkSize = 0L
        var sums: String? = null
        for (assetElement in assets) {
            val asset = assetElement.jsonObject
            val name = asset["name"]?.jsonPrimitive?.content ?: continue
            val url = asset["browser_download_url"]?.jsonPrimitive?.content ?: continue
            when {
                name.endsWith(".apk", ignoreCase = true) && apk == null -> {
                    apk = name to url
                    apkSize = asset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                }
                name.equals("SHA256SUMS.txt", ignoreCase = true) -> sums = url
            }
        }
        val (assetName, assetUrl) = apk ?: return null
        return ReleaseInfo(
            tag = tag,
            version = tag.removePrefix("v"),
            releaseName = root["name"]?.jsonPrimitive?.content ?: tag,
            body = root["body"]?.jsonPrimitive?.content,
            publishedAt = root["published_at"]?.jsonPrimitive?.content ?: "",
            assetName = assetName,
            assetUrl = assetUrl,
            assetSize = apkSize,
            checksumsUrl = sums,
        )
    }

    // -------------------------------------------------------------------------
    // Download (HTTP Range resume + byte-accurate progress)
    // -------------------------------------------------------------------------

    internal fun downloadsDir(context: Context): File =
        File(context.cacheDir, "downloads").apply { mkdirs() }

    /**
     * DRS p8 (C-5): `assetName` arrives from the GitHub releases API
     * response and used to be joined into File(downloadsDir(context),
     * assetName) unvalidated — a crafted payload could walk out of
     * cache/downloads ("../"). This mirrors CacheManager.sanitizeImportFileName
     * (the v1.28.0 HIGH fix; that helper lives in a file outside this task's
     * edit set, so the 3-line sanitize is reimplemented here instead of
     * reused): reduce to a single path segment (strip everything up to the
     * last '/' and '\\', trim, refuse dot segments). Instead of CacheManager's
     * "import.bin" fallback, a SHA-256-derived name is used so distinct
     * malformed names never collide. The install itself stays fail-closed
     * regardless: verifyDownload() looks the checksum up by the RAW asset
     * name, so any sanitized/fallback name can never pass the SHA256SUMS
     * gate.
     * DRS p9 (T-1): internal since then so the JVM regression suite can
     * pin the basename reduction and the SHA-derived fallback directly.
     */
    internal fun sanitizeAssetFileName(rawName: String): String {
        val name = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
        if (name.isNotEmpty() && name != "." && name != "..") return name
        val digest = MessageDigest.getInstance("SHA-256").digest(rawName.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16) + ".apk"
    }

    internal fun targetFile(context: Context, assetName: String): File =
        File(downloadsDir(context), sanitizeAssetFileName(assetName))

    internal fun partialFile(context: Context, assetName: String): File =
        File(downloadsDir(context), sanitizeAssetFileName(assetName) + ".part")

    fun startDownload(context: Context, info: ReleaseInfo) {
        if (downloadJob?.isActive == true) return
        // DRS roadmap phase 3: absolute privacy mode refuses APK downloads.
        if (!DrsPrivacyLock.allows(DrsPrivacyLock.NetworkSurface.UPDATE_DOWNLOAD)) {
            _downloadState.value = DownloadState.Failed("absolute privacy mode: downloads are disabled")
            return
        }
        val appContext = context.applicationContext
        downloadJob = scope.launch {
            runCatching {
                downloadApk(appContext, info)
            }.onFailure { error ->
                if (isActive) {
                    flogError(LogTopic.CRASH_UTILITY) { "APK download failed: $error" }
                    _downloadState.value = DownloadState.Failed(error.localizedMessage ?: "download failed")
                }
            }
        }
    }

    /** Pause = cancel the streaming loop but keep the partial file for resume. */
    fun pauseDownload() {
        val state = _downloadState.value
        downloadJob?.cancel()
        if (state is DownloadState.Downloading) {
            _downloadState.value = DownloadState.Paused(state.received, state.total)
        }
    }

    /** Cancel = stop streaming AND delete any partial or finished artifact. */
    fun cancelDownload(context: Context, info: ReleaseInfo) {
        downloadJob?.cancel()
        downloadJob = null
        val appContext = context.applicationContext
        scope.launch {
            withContext(Dispatchers.IO) {
                partialFile(appContext, info.assetName).let { if (it.exists()) it.delete() }
                targetFile(appContext, info.assetName).let { if (it.exists()) it.delete() }
            }
        }
        _downloadState.value = DownloadState.Idle
    }

    fun resumeDownload(context: Context, info: ReleaseInfo) {
        startDownload(context, info)
    }

    private suspend fun downloadApk(context: Context, info: ReleaseInfo): Unit = withContext(Dispatchers.IO) {
        val dst = targetFile(context, info.assetName)
        val part = partialFile(context, info.assetName)
        val alreadyDownloaded = if (part.exists()) part.length() else 0L

        val connection = openConnection(info.assetUrl) ?: throw IOException("no connection")
        connection.instanceFollowRedirects = true
        if (alreadyDownloaded > 0L) {
            connection.setRequestProperty("Range", "bytes=$alreadyDownloaded-")
        }
        val responseCode = connection.responseCode
        val resuming = responseCode == HttpURLConnection.HTTP_PARTIAL
        if (responseCode != HttpURLConnection.HTTP_OK && !resuming) {
            connection.disconnect()
            throw IOException("HTTP $responseCode while downloading")
        }
        val total = if (resuming) {
            alreadyDownloaded + (connection.contentLengthLong.takeIf { it > 0 } ?: 0L)
        } else {
            connection.contentLengthLong.takeIf { it > 0 } ?: info.assetSize
        }
        if (total <= 0L) {
            connection.disconnect()
            throw IOException("unknown content length; refusing unverifiable download")
        }

        _downloadState.value = DownloadState.Downloading(if (resuming) alreadyDownloaded else 0L, total, 0L)

        var received = if (resuming) alreadyDownloaded else 0L
        var lastSampleTime = System.currentTimeMillis()
        var lastSampleBytes = received
        var speedEma = 0L

        connection.inputStream.use { input ->
            java.io.FileOutputStream(part, resuming).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (isActive) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    received += read
                    val now = System.currentTimeMillis()
                    if (now - lastSampleTime >= 400) {
                        val instantBps = ((received - lastSampleBytes) * 1000L) / (now - lastSampleTime).coerceAtLeast(1)
                        speedEma = if (speedEma == 0L) instantBps else (speedEma * 3 + instantBps) / 4
                        lastSampleTime = now
                        lastSampleBytes = received
                        _downloadState.value = DownloadState.Downloading(received, total, speedEma)
                    }
                }
                output.flush()
            }
        }
        connection.disconnect()

        if (received >= total) {
            if (!part.renameTo(dst)) {
                part.copyTo(dst, overwrite = true)
                part.delete()
            }
            verifyDownload(context, info)
        } else {
            _downloadState.value = DownloadState.Paused(received, total)
        }
    }

    // -------------------------------------------------------------------------
    // Verification (SHA-256 from SHA256SUMS.txt + exact size)
    // -------------------------------------------------------------------------

    suspend fun verifyDownload(context: Context, info: ReleaseInfo): DownloadState.Ready = withContext(Dispatchers.IO) {
        _downloadState.value = DownloadState.Verifying
        val dst = targetFile(context, info.assetName)
        if (!dst.exists()) throw IOException("downloaded file is missing")

        // Size gate first (cheap, catches truncated files even offline).
        if (info.assetSize > 0 && dst.length() != info.assetSize) {
            dst.delete()
            throw IOException("size mismatch: got ${dst.length()}, expected ${info.assetSize}")
        }

        // Checksum gate: refuse to ever install an unverified APK.
        val sumsUrl = info.checksumsUrl ?: throw IOException("release has no SHA256SUMS.txt; refusing to install")
        val expected = fetchChecksums(sumsUrl)[info.assetName.lowercase()]
            ?: throw IOException("checksum for ${info.assetName} not listed")
        val actual = sha256Of(dst)
        if (!actual.equals(expected, ignoreCase = true)) {
            dst.delete()
            throw IOException("SHA-256 mismatch")
        }
        flogInfo(LogTopic.CRASH_UTILITY) { "APK verified: $actual" }
        val ready = DownloadState.Ready(dst, actual)
        _downloadState.value = ready
        ready
    }

    /**
     * DRS v1.28.0 audit fix (HIGH): the old parser built the map as
     * hash -> filename (standard `sha256sum` output is "<hash>  <name>"),
     * but verifyDownload() looks entries up by FILENAME — so every lookup
     * missed and the load-bearing integrity gate failed closed forever
     * with "checksum ... not listed". The parser now detects the line
     * layout (hash-first or filename-first), strips the binary-mode '*'
     * prefix, always returns filename -> hash, and keys are lowercase so
     * the lookup is case-stable. The hash value itself remains the only
     * verification authority.
     */
    suspend fun fetchChecksums(url: String): Map<String, String> = withContext(Dispatchers.IO) {
        val connection = openConnection(url) ?: throw IOException("no connection")
        val body = connection.inputStream.use { it.readBytes().decodeToString() }
        connection.disconnect()
        parseChecksumLines(body)
    }

    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")

    /**
     * DRS v1.28.0 audit fix: the pure checksum-line parser, extracted from
     * [fetchChecksums] so the load-bearing integrity gate is JVM-testable.
     */
    internal fun parseChecksumLines(body: String): Map<String, String> {
        return body.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val parts = trimmed.split(Regex("\\s+"), limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val first = parts[0]
                val second = parts[1].removePrefix("*")
                when {
                    HEX64.matches(first) -> second.lowercase() to first.lowercase()
                    HEX64.matches(second) -> first.removePrefix("*").lowercase() to second.lowercase()
                    else -> null
                }
            }
            .toMap()
    }

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // -------------------------------------------------------------------------
    // Install (official platform flow: FileProvider + package-archive VIEW)
    // -------------------------------------------------------------------------

    fun canRequestInstall(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun installApk(context: Context, file: File): Result<Unit> = runCatching {
        if (!canRequestInstall(context)) throw SecurityException("install-unknown-apps permission not granted")
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.provider.file",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }

    // -------------------------------------------------------------------------
    // Notification (opt-in, only when auto-check is enabled)
    // -------------------------------------------------------------------------

    fun postUpdateNotification(context: Context, info: ReleaseInfo) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(com.drs.smartkeyboard.R.string.updates__notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val deepLink = Intent()
            .setClassName(context, "com.drs.smartkeyboard.app.DrsAppActivity")
            .setData(Uri.parse("ui://drs-keyboard/ext/check-updates"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context,
            0,
            deepLink,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.getString(com.drs.smartkeyboard.R.string.updates__available_long, info.version)
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(context)
        }
        notificationManager.notify(
            NOTIFICATION_ID_UPDATE,
            notificationBuilder.run {
                setContentTitle(context.getString(com.drs.smartkeyboard.R.string.updates__available_short))
                setContentText(text)
                setStyle(Notification.BigTextStyle().bigText(text))
                setSmallIcon(android.R.drawable.stat_sys_download_done)
                setContentIntent(pending)
                setAutoCancel(true)
                build()
            },
        )
    }

    // -------------------------------------------------------------------------
    // Auto check scheduler (respects UpdateCheckMode + last-check timestamp)
    // -------------------------------------------------------------------------

    fun maybeAutoCheck(
        context: Context,
        mode: UpdateCheckMode,
        notifyEnabled: Boolean,
        lastCheckTimestamp: Long,
        onChecked: suspend (Long) -> Unit,
    ) {
        if (mode == UpdateCheckMode.OFF || mode == UpdateCheckMode.MANUAL) return
        val interval = when (mode) {
            UpdateCheckMode.DAILY -> 24L * 60 * 60 * 1000
            UpdateCheckMode.WEEKLY -> 7L * 24 * 60 * 60 * 1000
            else -> return
        }
        val now = System.currentTimeMillis()
        if (lastCheckTimestamp > 0 && now - lastCheckTimestamp < interval) return
        val appContext = context.applicationContext
        scope.launch {
            flogInfo(LogTopic.CRASH_UTILITY) { "auto update check (mode=$mode)" }
            val state = checkForUpdates()
            onChecked(System.currentTimeMillis())
            if (state is CheckState.Available && notifyEnabled) {
                postUpdateNotification(appContext, state.info)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Low-level HTTP
    // -------------------------------------------------------------------------

    private fun openConnection(
        url: String,
        connectTimeout: Int = CONNECT_TIMEOUT_MS,
        readTimeout: Int = READ_TIMEOUT_MS,
    ): HttpURLConnection? {
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = connectTimeout
            connection.readTimeout = readTimeout
            connection.setRequestProperty("User-Agent", "DRS-Smart-Keyboard/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept", "application/vnd.github+json, */*")
            connection
        }.getOrElse { error ->
            flogError(LogTopic.CRASH_UTILITY) { "openConnection failed for $url: $error" }
            null
        }
    }

    /** Small helper the auto-check uses to pace retries without blocking callers. */
}
