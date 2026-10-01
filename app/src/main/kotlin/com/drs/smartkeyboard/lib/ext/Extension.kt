/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

import android.content.Context
import com.drs.smartkeyboard.lib.io.DrsRef
import com.drs.smartkeyboard.lib.io.ZipUtils
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.drs.lib.kotlin.io.FsDir
import org.drs.lib.kotlin.io.FsFile
import org.drs.lib.kotlin.resultErr
import org.drs.lib.kotlin.resultOk
import java.security.MessageDigest

/**
 * DRS v1.26.0: the source fingerprint marker — a SIBLING file of the
 * cache directory (`<ext-id>.drs-fp`), never inside [Extension.workingDir],
 * so extension components never see it and the language-pack database
 * path resolution stays untouched. It stores the content fingerprint of
 * the source the cache was built from.
 */
internal const val FINGERPRINT_MARKER_SUFFIX = ".drs-fp"

/**
 * DRS v1.26.0: the pure cache-reuse decision — JVM-tested. Reuse only
 * when the load was not forced AND both fingerprints are known AND they
 * match: a forced reload must always rebuild, and an unknown fingerprint
 * (exotic source ref, unreadable source, legacy cache without a marker)
 * keeps the conservative rebuild behavior.
 */
internal fun canReuseCache(
    force: Boolean,
    cachedFingerprint: String?,
    sourceFingerprint: String?,
): Boolean = !force && sourceFingerprint != null && cachedFingerprint != null &&
    cachedFingerprint == sourceFingerprint

/** Lowercase hex of a SHA-256 digest — stable across platforms. */
private fun hexOf(digest: ByteArray): String =
    digest.joinToString(separator = "") { "%02x".format(it) }

/**
 * DRS v1.26.0: stable content fingerprint of an (relativePath, size)
 * entry list — JVM-tested. Entries are sorted by path first, so listing
 * order can never change the fingerprint, while any path or size change
 * always does. Used for asset-tree sources (an APK's assets cannot change
 * while installed, but an app UPDATE ships new content behind the same
 * extension id — the fingerprint catches that honestly).
 */
internal fun fingerprintOfEntries(entries: List<Pair<String, Long>>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    entries.sortedBy { it.first }.forEach { (path, size) ->
        digest.update(path.toByteArray(Charsets.UTF_8))
        digest.update(":$size\n".toByteArray(Charsets.UTF_8))
    }
    return "tree:" + hexOf(digest.digest())
}

private const val DIGEST_BUFFER_SIZE = 8192

/**
 * An extension container holding a parsed config, a working directory file
 * object as well as a reference to the original flex file.
 *
 * @property meta The parsed config of this extension.
 * @property workingDir The working directory, used as a cache and as a staging
 *  area for modifications to extension files.
 * @property sourceRef Optional, defines where the original flex file is stored.
 */
@Polymorphic
@Serializable
abstract class Extension {
    @Transient var workingDir: FsDir? = null
    @Transient var sourceRef: DrsRef? = null

    abstract val meta: ExtensionMeta
    abstract val dependencies: List<String>?

    abstract fun serialType(): String

    abstract fun components(): List<ExtensionComponent>

    fun isLoaded() = workingDir != null

    open fun onBeforeLoad(context: Context, cacheDir: FsDir) {
        /* Empty */
    }

    open fun onAfterLoad(context: Context, cacheDir: FsDir) {
        /* Empty */
    }

    fun load(context: Context, force: Boolean = false): Result<Unit> {
        val cacheDir = FsDir(context.cacheDir, meta.id)
        // DRS v1.26.0: «الكاش الصادق» — the cache is reused when it was
        // built from the very same source content, instead of the old
        // unconditional delete+unzip on every single load (the TODO asked
        // for exactly this). An unknown fingerprint keeps the conservative
        // rebuild, so a forced reload or a legacy cache never lies.
        val fingerprint = sourceRef?.let { sourceFingerprint(context, it) }
        if (cacheDir.exists()) {
            val cached = if (force) null else cachedFingerprintOf(cacheDir)
            if (canReuseCache(force, cached, fingerprint)) {
                workingDir = cacheDir
                // onBeforeLoad + unzip already ran when this cache was
                // first built; onAfterLoad re-attaches runtime handles to
                // the cached files (e.g. the language-pack database) and
                // its implementations are re-entrant by contract.
                onAfterLoad(context, cacheDir)
                return resultOk()
            }
            cacheDir.deleteRecursively()
        }
        cacheDir.mkdirs()
        val sourceRef = sourceRef ?: return resultOk()
        onBeforeLoad(context, cacheDir)
        ZipUtils.unzip(context, sourceRef, cacheDir).onFailure { return resultErr(it) }
        if (fingerprint != null) {
            runCatching { markerFile(cacheDir).writeText(fingerprint) }
        }
        workingDir = cacheDir
        onAfterLoad(context, cacheDir)
        return resultOk()
    }

    open fun onBeforeUnload(context: Context, cacheDir: FsDir) {
        /* Empty */
    }

    open fun onAfterUnload(context: Context, cacheDir: FsDir) {
        /* Empty */
    }

    fun unload(context: Context) {
        val cacheDir = workingDir ?: FsDir(context.cacheDir, meta.id)
        if (cacheDir.exists()) {
            onBeforeUnload(context, cacheDir)
            cacheDir.deleteRecursively()
            workingDir = null
            onAfterUnload(context, cacheDir)
        }
        // DRS v1.26.0: keep the cache root tidy — a stale marker next to a
        // deleted cache is harmless (reuse requires the cache itself), but
        // deleting it keeps the directory honest.
        runCatching { markerFile(cacheDir).delete() }
    }

    /**
     * DRS v1.26.0: the fingerprint marker of this extension — a sibling
     * file of [cacheDir] (see [FINGERPRINT_MARKER_SUFFIX]).
     */
    private fun markerFile(cacheDir: FsDir): FsFile {
        val root = cacheDir.parentFile ?: FsFile(cacheDir.absolutePath)
        return FsFile(root, meta.id + FINGERPRINT_MARKER_SUFFIX)
    }

    /** Reads the stored fingerprint of an existing cache, if any. */
    private fun cachedFingerprintOf(cacheDir: FsDir): String? =
        runCatching {
            val marker = markerFile(cacheDir)
            val text = marker.takeIf { it.isFile }?.readText()
            text?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()

    /**
     * DRS v1.26.0: computes the content fingerprint of the source this
     * extension would be extracted from. Asset-tree sources fingerprint
     * as sorted (path, size) entries; flex archives fingerprint as the
     * SHA-256 of their bytes. `null` for any source that cannot be
     * measured — the caller keeps the conservative rebuild behavior.
     */
    private fun sourceFingerprint(context: Context, sourceRef: DrsRef): String? =
        runCatching {
            when {
                sourceRef.isAssets -> {
                    val root = sourceRef.relativePath.removeSuffix("/")
                    val entries = mutableListOf<Pair<String, Long>>()
                    fun walk(path: String) {
                        val children = context.assets.list(path).orEmpty()
                        if (children.isEmpty()) {
                            val size = context.assets.open(path).use { it.available().toLong() }
                            entries.add(path to size)
                        } else {
                            children.forEach { child ->
                                walk(if (path.isEmpty()) child else "$path/$child")
                            }
                        }
                    }
                    walk(root)
                    fingerprintOfEntries(entries)
                }
                sourceRef.isCache || sourceRef.isInternal -> {
                    val file = FsFile(sourceRef.absolutePath(context))
                    if (!file.exists() || !file.isFile) return@runCatching null
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input ->
                        val buffer = ByteArray(DIGEST_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            digest.update(buffer, 0, read)
                        }
                    }
                    "bytes:" + hexOf(digest.digest())
                }
                else -> null
            }
        }.getOrNull()


    abstract fun edit(): ExtensionEditor
}

interface ExtensionEditor {
    var meta: ExtensionMeta
    val dependencies: MutableList<String>

    fun build(): Extension
}
