/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.drsupdater

import android.content.Context
import com.drs.smartkeyboard.BuildConfig
import com.drs.smartkeyboard.drs.privacy.DrsPrivacyLock
import com.drs.smartkeyboard.lib.devtools.LogTopic
import com.drs.smartkeyboard.lib.devtools.flogError
import com.drs.smartkeyboard.lib.devtools.flogInfo
import com.drs.smartkeyboard.lib.devtools.flogWarning
import com.drs.smartkeyboard.lib.ext.Extension
import com.drs.smartkeyboard.lib.ext.DrsPackageTrust
import com.drs.smartkeyboard.lib.ext.ExtensionJsonConfig
import com.drs.smartkeyboard.lib.ext.ExtensionManager
import com.drs.smartkeyboard.lib.io.ZipUtils
import java.io.File
import java.io.IOException
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.drs.lib.kotlin.io.FsDir
import org.drs.lib.kotlin.io.FsFile
import org.drs.lib.kotlin.io.readJson

/** Real extension types a remote package may target (matches ExtensionManager paths). */
object PackageTypes {
    const val THEME = "ime/theme"
    const val KEYBOARD = "ime/keyboard"
    const val LANGUAGEPACK = "ime/languagepack"

    val ALL = setOf(THEME, KEYBOARD, LANGUAGEPACK)
}

/** Lifecycle status of a remote package relative to the on-device state. */
enum class PackageStatus {
    /** Known remotely, not installed on device. */
    AVAILABLE,

    /** Installed and identical version to the remote catalog. */
    INSTALLED,

    /** Installed but the remote catalog offers a newer version. */
    UPDATE_AVAILABLE,

    /** Package requires a newer app version than the installed one. */
    INCOMPATIBLE,
}

/** A catalog entry of the official package manifest. */
data class RemotePackage(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val type: String,
    val sizeBytes: Long,
    val sha256: String,
    val url: String,
    val releaseDate: String,
    val author: String,
    val minAppVersion: String,
    /** Real palette colors of the package content, used as an offline preview. */
    val colors: List<String>,
    /** DRS M0.3 — ed25519 signature over the exact .flex file bytes. */
    val signature: String = "",
    /** DRS M0.3 — which pinned release key the signature must verify under. */
    val pubkeyId: String = "",
)

/**
 * DRS Package Center — a real, manifest-driven package distribution layer on
 * top of the app's existing extension (.flex) system.
 *
 * The catalog source of truth is a signed-by-HTTPS manifest committed to the
 * official repository (`packages/manifest.json`); each entry points to a
 * `.flex` release asset published on GitHub Releases. Every install enforces,
 * in order: HTTPS-only fetch → exact byte size → SHA-256 checksum → package
 * id/type whitelist → native extension import (which itself validates the
 * archive structure). Nothing is executed — packages are pure data (themes,
 * layouts, language packs) consumed by the existing engines.
 */
object DrsPackageManager {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    private val json = Json { ignoreUnknownKeys = true }

    // DRS p8 (C-4): dotted-numeric version contract — "<major>[.<minor>
    // [.<patch>[.<build>]]]" with an optional -/+ suffix (e.g. "1.2.3-beta1").
    // Anything else is rejected in [parseManifest] before the value can ever
    // reach packageFile()'s "<id>-<version>.flex" path join.
    // DRS p9 (D-1): suffix chars restricted to [A-Za-z0-9._-] so a crafted "1.0+../../x" can no longer pass this gate and escape cache/packages via packageFile().
    private val PACKAGE_VERSION_REGEX = Regex("""^\d+(\.\d+){0,3}([-+][A-Za-z0-9._-]*)?$""")

    data class InstallProgress(
        val phase: Phase,
        val received: Long,
        val total: Long,
        val pkgId: String? = null,
    ) {
        enum class Phase { IDLE, DOWNLOADING, VERIFYING, INSTALLING, DONE, FAILED }
    }

    private val _progress = MutableStateFlow(InstallProgress(InstallProgress.Phase.IDLE, 0L, 0L))
    val progress: StateFlow<InstallProgress> = _progress.asStateFlow()

    // -------------------------------------------------------------------------
    // Catalog (manifest.json from the official repository)
    // -------------------------------------------------------------------------

    suspend fun fetchPackages(): Result<List<RemotePackage>> = withContext(Dispatchers.IO) {
        // DRS roadmap phase 3: absolute privacy mode refuses catalog fetches.
        if (!DrsPrivacyLock.allows(DrsPrivacyLock.NetworkSurface.PACKAGE_MANIFEST)) {
            return@withContext Result.failure(
                IllegalStateException("absolute privacy mode: package catalog is disabled"),
            )
        }
        runCatching {
            val connection = openConnection(BuildConfig.DRS_PACKAGES_MANIFEST_URL)
                ?: throw IOException("no connection")
            val body = connection.inputStream.use { it.readBytes().decodeToString() }
            connection.disconnect()
            parseManifest(body)
        }
    }

    internal fun parseManifest(payload: String): List<RemotePackage> {
        val root = json.parseToJsonElement(payload).jsonObject
        val packages = root["packages"]?.jsonArray ?: return emptyList()
        return packages.mapNotNull { element ->
            val pkg = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            fun str(key: String) = pkg[key]?.jsonPrimitive?.content
            // DRS v1.28.0 audit fix (defense-in-depth, L-4): the package id and
            // version are interpolated into local file paths (packageFile =
            // "<id>-<version>.flex"), and the download URL is fetched over
            // HTTPS only. Catalog entries with path-bearing or non-contractual
            // ids, oversized hash fields or non-HTTPS URLs are dropped before
            // they can ever reach the download/install stage.
            val id = str("package_id") ?: return@mapNotNull null
            if (!com.drs.smartkeyboard.lib.ext.ExtensionValidation.META_ID_REGEX.matches(id)) {
                flogWarning(LogTopic.CRASH_UTILITY) { "package manifest: dropping entry with invalid id '$id'" }
                return@mapNotNull null
            }
            val sha256 = str("sha256") ?: return@mapNotNull null
            if (!sha256.matches(Regex("^[0-9a-fA-F]{64}$"))) return@mapNotNull null
            val url = str("download_url") ?: return@mapNotNull null
            if (!url.startsWith("https://")) return@mapNotNull null
            // DRS p8 (C-4): the version lands in packageFile() as
            // "<id>-<version>.flex", so a manifest-supplied version carrying
            // separators ("../x", "/etc/passwd", "a\\b") could traverse out
            // of cache/packages. parseManifest now enforces the same contract
            // as the v1.28.0 L-4 id/sha256/url gates: only dotted-numeric
            // versions pass; anything else drops the catalog entry (the
            // existing failure path), so the manifest is treated as invalid
            // for that package.
            val version = str("version") ?: "0.0.0"
            if (!PACKAGE_VERSION_REGEX.matches(version)) {
                flogWarning(LogTopic.CRASH_UTILITY) { "package manifest: dropping entry with invalid version '$version'" }
                return@mapNotNull null
            }
            // DRS M0.3 — the signature pair is MANDATORY, fail-closed: a
            // catalog entry without BOTH a signature and a pubkey id naming
            // our pinned release key is dropped exactly like an invalid id.
            // Unsigned catalogs are not a legacy mode — they are untrusted.
            val signature = str("signature")
            val pubkeyId = str("pubkey_id")
            if (signature.isNullOrBlank() || pubkeyId != DrsPackageTrust.RELEASE_KEY_ID) {
                flogWarning(LogTopic.CRASH_UTILITY) {
                    "package manifest: dropping UNSIGNED entry '${id}' (missing signature/pubkey_id pair)"
                }
                return@mapNotNull null
            }
            RemotePackage(
                id = id,
                name = str("name") ?: return@mapNotNull null,
                description = str("description") ?: "",
                version = version,
                type = str("type") ?: return@mapNotNull null,
                sizeBytes = str("file_size")?.toLongOrNull() ?: 0L,
                sha256 = sha256,
                url = url,
                releaseDate = str("release_date") ?: "",
                author = str("author") ?: "",
                minAppVersion = str("min_app_version") ?: "0.0.0",
                colors = (pkg["colors"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList()))
                    .mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() },
                signature = signature,
                pubkeyId = pubkeyId,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Status resolution against the installed extension index
    // -------------------------------------------------------------------------



    // -------------------------------------------------------------------------
    // Install / remove (verified download → native extension import)
    // -------------------------------------------------------------------------

    fun installPackage(
        context: Context,
        pkg: RemotePackage,
        extensionManager: ExtensionManager,
        onProgress: (InstallProgress) -> Unit = { _progress.value = it },
    ) {
        val appContext = context.applicationContext
        // DRS roadmap phase 3: absolute privacy mode refuses package downloads.
        if (!DrsPrivacyLock.allows(DrsPrivacyLock.NetworkSurface.PACKAGE_DOWNLOAD)) {
            _progress.value = InstallProgress(InstallProgress.Phase.FAILED, 0L, pkg.sizeBytes, pkg.id)
            return
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            _progress.value = InstallProgress(InstallProgress.Phase.DOWNLOADING, 0L, pkg.sizeBytes, pkg.id)
            try {
                val dstFile = packageFile(appContext, pkg)
                downloadToFile(pkg.url, dstFile, pkg.sizeBytes) { received, total ->
                    _progress.value = InstallProgress(InstallProgress.Phase.DOWNLOADING, received, total, pkg.id)
                }

                _progress.value = InstallProgress(InstallProgress.Phase.VERIFYING, pkg.sizeBytes, pkg.sizeBytes, pkg.id)
                verifyFile(dstFile, pkg)

                _progress.value = InstallProgress(InstallProgress.Phase.INSTALLING, pkg.sizeBytes, pkg.sizeBytes, pkg.id)
                importVerifiedPackage(appContext, dstFile, pkg, extensionManager)

                dstFile.delete()
                _progress.value = InstallProgress(InstallProgress.Phase.DONE, pkg.sizeBytes, pkg.sizeBytes, pkg.id)
            } catch (error: Throwable) {
                flogError(LogTopic.CRASH_UTILITY) { "package install failed for ${pkg.id}: $error" }
                packageFile(appContext, pkg).delete()
                _progress.value = InstallProgress(InstallProgress.Phase.FAILED, 0L, pkg.sizeBytes, pkg.id)
            }
        }
    }

    fun removePackage(pkgId: String, extensionManager: ExtensionManager): Result<Unit> = runCatching {
        val ext = extensionManager.extensions.value.find { it.meta.id == pkgId }
            ?: throw IOException("package not installed")
        extensionManager.delete(ext)
    }

    /**
     * Re-usable verification exposed for the diagnostics screen.
     *
     * DRS M0.3 — the ed25519 signature over the exact file bytes is checked
     * BEFORE the file is ever touched by a decompressor: [importVerifiedPackage]
     * only unzips after this function returned. [signatureVerifier] is the
     * injection seam for tests (default = the pinned release key trust root).
     */
    fun verifyFile(
        file: File,
        pkg: RemotePackage,
        signatureVerifier: (ByteArray, String?, String?) -> Boolean = DrsPackageTrust::verifyPackageSignature,
    ) {
        if (pkg.sizeBytes > 0 && file.length() != pkg.sizeBytes) {
            throw IOException("size mismatch: got ${file.length()}, expected ${pkg.sizeBytes}")
        }
        val actual = DrsUpdateCenter.sha256Of(file)
        if (!actual.equals(pkg.sha256, ignoreCase = true)) {
            throw IOException("SHA-256 mismatch")
        }
        // Signature gate — over the raw file bytes, BEFORE any unzip.
        val bytes = file.readBytes()
        if (!signatureVerifier(bytes, pkg.signature, pkg.pubkeyId)) {
            throw IOException("signature verification failed for ${pkg.id}")
        }
    }

    internal fun importVerifiedPackage(
        context: Context,
        zipFile: File,
        pkg: RemotePackage,
        extensionManager: ExtensionManager,
    ) {
        val staging = FsDir(context.cacheDir, "package_staging_${pkg.id}")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            ZipUtils.unzip(zipFile, staging)
            val manifestFile = FsFile(staging, "extension.json")
            if (!manifestFile.exists()) throw IOException("invalid package: extension.json missing")
            val ext: Extension = manifestFile.readJson(ExtensionJsonConfig)

            // Security gate: the archive payload must IDENTICALLY match the
            // catalog entry — id and type come from our own manifest only.
            if (ext.meta.id != pkg.id) throw IOException("package id mismatch: ${ext.meta.id}")
            val actualType = when (ext) {
                is com.drs.smartkeyboard.ime.theme.ThemeExtension -> PackageTypes.THEME
                is com.drs.smartkeyboard.ime.keyboard.KeyboardExtension -> PackageTypes.KEYBOARD
                is com.drs.smartkeyboard.ime.nlp.LanguagePackExtension -> PackageTypes.LANGUAGEPACK
                else -> throw IOException("unknown package type")
            }
            if (actualType != pkg.type) throw IOException("package type mismatch")
            if (pkg.type !in PackageTypes.ALL) throw IOException("package type not allowed")

            ext.workingDir = staging
            extensionManager.import(ext)
            flogInfo(LogTopic.CRASH_UTILITY) { "package installed: ${pkg.id} v${pkg.version}" }
        } finally {
            staging.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // Download plumbing
    // -------------------------------------------------------------------------

    internal fun packageFile(context: Context, pkg: RemotePackage): File {
        val dir = File(context.cacheDir, "packages").apply { mkdirs() }
        return File(dir, pkg.id.substringAfterLast('.') + "-" + pkg.version + ".flex")
    }

    private fun downloadToFile(
        url: String,
        dst: File,
        expectedSize: Long,
        onBytes: (Long, Long) -> Unit,
    ) {
        val connection = openConnection(url) ?: throw IOException("no connection")
        try {
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            var received = 0L
            connection.inputStream.use { input ->
                dst.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        received += read
                        onBytes(received, total)
                    }
                }
            }
            if (expectedSize > 0 && received != expectedSize) {
                throw IOException("size mismatch: got $received, expected $expectedSize")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): java.net.HttpURLConnection? {
        return runCatching {
            val connection = URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "DRS-Smart-Keyboard/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept", "application/octet-stream, application/json, */*")
            connection
        }.getOrElse { error ->
            flogError(LogTopic.CRASH_UTILITY) { "openConnection failed for $url: $error" }
            null
        }
    }
}
