/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.ext

import android.content.Context
import android.net.Uri
import android.os.FileObserver
import com.drs.smartkeyboard.appContext
import com.drs.smartkeyboard.ime.keyboard.KeyboardExtension
import com.drs.smartkeyboard.ime.nlp.LanguagePackExtension
import com.drs.smartkeyboard.ime.text.composing.Appender
import com.drs.smartkeyboard.ime.text.composing.Composer
import com.drs.smartkeyboard.ime.text.composing.HangulUnicode
import com.drs.smartkeyboard.ime.text.composing.KanaUnicode
import com.drs.smartkeyboard.ime.text.composing.WithRules
import com.drs.smartkeyboard.ime.theme.ThemeExtension
import com.drs.smartkeyboard.lib.devtools.LogTopic
import com.drs.smartkeyboard.lib.devtools.flogDebug
import com.drs.smartkeyboard.lib.devtools.flogError
import com.drs.smartkeyboard.lib.devtools.flogWarning
import com.drs.smartkeyboard.lib.io.DrsRef
import com.drs.smartkeyboard.lib.io.ZipUtils
import com.drs.smartkeyboard.lib.io.delete
import com.drs.smartkeyboard.lib.io.listDirs
import com.drs.smartkeyboard.lib.io.listFiles
import com.drs.smartkeyboard.lib.io.loadJsonAsset
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.drs.lib.android.FileObserver
import org.drs.lib.kotlin.io.FsFile
import org.drs.lib.kotlin.io.writeJson
import org.drs.lib.kotlin.throwOnFailure

@OptIn(ExperimentalSerializationApi::class)
val ExtensionJsonConfig = Json {
    classDiscriminator = "$"
    encodeDefaults = false
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
    prettyPrintIndent = "  "
    serializersModule = SerializersModule {
        polymorphic(Extension::class) {
            subclass(KeyboardExtension::class, KeyboardExtension.serializer())
            subclass(ThemeExtension::class, ThemeExtension.serializer())
            subclass(LanguagePackExtension::class, LanguagePackExtension.serializer())
        }
        polymorphic(Composer::class) {
            subclass(Appender::class, Appender.serializer())
            subclass(HangulUnicode::class, HangulUnicode.serializer())
            subclass(KanaUnicode::class, KanaUnicode.serializer())
            subclass(WithRules::class, WithRules.serializer())
            defaultDeserializer { Appender.serializer() }
        }
    }
}

class ExtensionManager(context: Context) {
    companion object {
        const val IME_KEYBOARD_PATH = "ime/keyboard"
        const val IME_KEYBOARD3_PATH = "ime/keyboard3"
        const val IME_THEME_PATH = "ime/theme"
        const val IME_LANGUAGEPACK_PATH = "ime/languagepack"

        private const val FILE_OBSERVER_MASK =
            FileObserver.CLOSE_WRITE or FileObserver.DELETE or FileObserver.MOVED_FROM or FileObserver.MOVED_TO
    }

    private val appContext by context.appContext()
    private val defaultScope = CoroutineScope(Dispatchers.Default)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    val keyboardExtensions = ExtensionIndex(KeyboardExtension.serializer(), IME_KEYBOARD_PATH)
    val themes = ExtensionIndex(ThemeExtension.serializer(), IME_THEME_PATH)
    val languagePacks = ExtensionIndex(LanguagePackExtension.serializer(), IME_LANGUAGEPACK_PATH)

    val extensions = combine(
        keyboardExtensions,
        themes,
        languagePacks,
    ) { lists -> lists.flatMap { it } }.stateIn(defaultScope, SharingStarted.Eagerly, emptyList())

    // DRS M0.2 — the state-inclusive combined listing: every extension with
    // its enabled flag. The UI shows disabled entries dimmed and offers
    // enable/rollback for them; the runtime keeps using [extensions].
    val extensionsWithState: StateFlow<List<Pair<Extension, Boolean>>> = combine(
        keyboardExtensions.allWithDisabledState,
        themes.allWithDisabledState,
        languagePacks.allWithDisabledState,
    ) { lists -> lists.flatMap { it } }.stateIn(defaultScope, SharingStarted.Eagerly, emptyList())

    fun init() {
        ioScope.launch {
            keyboardExtensions.init()
            themes.init()
            languagePacks.init()
        }
    }

    fun import(ext: Extension) {
        val workingDir = requireNotNull(ext.workingDir) { "No working dir specified" }
        // DRS v1.28.0 audit fix (Medium): the meta id was never validated at
        // import time — only the interactive editor ran validation. An imported
        // manifest with an id containing '/' or '..' steered the per-extension
        // cache dir (Extension.load builds FsDir(cacheDir, meta.id)) outside
        // its own folder. The same id contract the editor enforces now gates
        // every programmatic import path.
        require(ext.meta.id.matches(ExtensionValidation.META_ID_REGEX)) {
            "Refusing to import extension with invalid id: '${ext.meta.id}'"
        }
        // DRS p7 (E3-4) — stylesheet-path containment at import time: reject absolute
        // paths and any ".." segment so a crafted theme manifest can never steer the
        // theme loader (or the editor save loop) outside the extension's own directory.
        // Every ThemeExtension component is gated; a violation aborts the whole import.
        if (ext is ThemeExtension) {
            for (theme in ext.themes) {
                require(ExtensionValidation.isSafeStylesheetPath(theme.stylesheetPath())) {
                    "Refusing to import theme component with unsafe stylesheet path: '${theme.stylesheetPath()}'"
                }
            }
        }
        val extFileName = ExtensionDefaults.createFlexName(ext.meta.id)
        val relGroupPath = when (ext) {
            is KeyboardExtension -> IME_KEYBOARD_PATH
            is ThemeExtension -> IME_THEME_PATH
            is LanguagePackExtension -> IME_LANGUAGEPACK_PATH
            else -> error("Unknown extension type")
        }
        ext.sourceRef = DrsRef.internal(relGroupPath).subRef(extFileName)
        // DRS M0.2 — archive before every overwrite: the replaced archive
        // moves into archive/<id>/<manifest-version>.flex so re-importing
        // the same id never destroys the only copy of a previous version.
        // Best-effort: a failed archive is logged, the import proceeds.
        ExtensionLifecycle(internalDirOf(ext)).archive(ext.meta.id)
            .onFailure { error ->
                flogWarning(LogTopic.EXT_INDEXING) {
                    "lifecycle: pre-overwrite archive failed for ${ext.meta.id}: $error"
                }
            }
        FsFile(workingDir, ExtensionDefaults.MANIFEST_FILE_NAME).writeJson(ext, ExtensionJsonConfig)
        writeExtension(ext).throwOnFailure()
        ext.unload(appContext)
        ext.workingDir = null
    }

    // -----------------------------------------------------------------------
    // DRS M0.2 — lifecycle surface (disable / enable / rollback)
    // -----------------------------------------------------------------------

    /** Only internal (user-writable) archives may change lifecycle state. */
    fun canChangeLifecycle(ext: Extension): Boolean = ext.sourceRef?.isInternal == true

    fun isDisabled(ext: Extension): Boolean =
        canChangeLifecycle(ext) && lifecycleOf(ext).isDisabled(ext.meta.id)

    fun disable(ext: Extension): Result<Unit> = runCatching {
        check(canChangeLifecycle(ext)) { "Cannot disable an assets-based extension" }
        lifecycleOf(ext).disable(ext.meta.id).getOrThrow()
    }

    fun enable(ext: Extension): Result<Unit> = runCatching {
        check(canChangeLifecycle(ext)) { "Cannot enable an assets-based extension" }
        lifecycleOf(ext).enable(ext.meta.id).getOrThrow()
    }

    /** Newest first. */
    fun archivedVersions(ext: Extension): List<String> =
        if (canChangeLifecycle(ext)) lifecycleOf(ext).archivedVersions(ext.meta.id) else emptyList()

    fun rollback(ext: Extension): Result<String> = runCatching {
        check(canChangeLifecycle(ext)) { "Cannot roll back an assets-based extension" }
        lifecycleOf(ext).rollback(ext.meta.id).getOrThrow()
    }

    private fun internalDirOf(ext: Extension): File = when (ext) {
        is KeyboardExtension -> keyboardExtensions.internalModuleDir
        is ThemeExtension -> themes.internalModuleDir
        is LanguagePackExtension -> languagePacks.internalModuleDir
        else -> error("Unknown extension type")
    }

    private fun lifecycleOf(ext: Extension): ExtensionLifecycle = ExtensionLifecycle(internalDirOf(ext))

    fun export(ext: Extension, uri: Uri) {
        ext.load(appContext).throwOnFailure()
        val workingDir = requireNotNull(ext.workingDir) { "No working dir specified" }
        ZipUtils.zip(appContext, workingDir, uri).throwOnFailure()
        ext.unload(appContext)
    }

    private fun writeExtension(ext: Extension) = runCatching {
        val workingDir = requireNotNull(ext.workingDir) { "No working dir specified" }
        val sourceRef = requireNotNull(ext.sourceRef) { "No source ref specified" }
        ZipUtils.zip(appContext, workingDir, sourceRef).throwOnFailure()
    }

    fun getExtensionById(id: String): Extension? {
        return extensions.value.find { it.meta.id == id }
    }

    fun canDelete(ext: Extension): Boolean {
        return ext.sourceRef?.isInternal == true
    }

    fun delete(ext: Extension) {
        check(canDelete(ext)) { "Cannot delete extension!" }
        ext.unload(appContext)
        ext.sourceRef!!.delete(appContext)
    }

    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    inner class ExtensionIndex<T : Extension>(
        private val serializer: KSerializer<T>,
        modulePath: String,
        private val flow: MutableStateFlow<List<T>> = MutableStateFlow(emptyList()),
    ) : StateFlow<List<T>> by flow {
        private val assetsModuleRef = DrsRef.assets(modulePath)
        private val internalModuleRef = DrsRef.internal(modulePath)
        var internalModuleDir = internalModuleRef.absoluteFile(appContext)

        private var staticExtensions = listOf<T>()
        private var fileObserver: FileObserver? = null
        private val initGuard = Mutex()
        private val refreshGuard = Mutex()

        // DRS M0.2 — the FULL index including disabled archives (flag), while
        // the main flow carries enabled entries only.
        private val allFlow = MutableStateFlow(emptyList<Pair<T, Boolean>>())
        val allWithDisabledState: StateFlow<List<Pair<T, Boolean>>> = allFlow

        suspend fun init() {
            initGuard.withLock {
                // Update internal module dir to actual path and make directory if not exists
                internalModuleDir = internalModuleRef.absoluteFile(appContext)
                internalModuleDir.mkdirs()

                // Refresh index to new state
                refreshGuard.withLock {
                    staticExtensions = indexAssetsModule()
                    refresh()
                }

                // Stop watching on old file observer if one exists and start new observer on new path
                fileObserver?.stopWatching()
                fileObserver = FileObserver(internalModuleDir, FILE_OBSERVER_MASK) { event, path ->
                    flogDebug(LogTopic.EXT_INDEXING) { "FileObserver.onEvent { event=$event path=$path }" }
                    if (path == null) return@FileObserver
                    ioScope.launch {
                        refreshGuard.withLock {
                            refresh()
                        }
                    }
                }.also { it.startWatching() }
            }
        }

        private fun refresh() {
            val staticWithState = staticExtensions.map { it to true }
            val internalWithState = indexInternalModule()
            allFlow.value = staticWithState + internalWithState
            flow.value = (staticWithState + internalWithState)
                .filter { it.second }
                .map { it.first }
        }

        private fun indexAssetsModule(): List<T> {
            val list = mutableListOf<T>()
            assetsModuleRef.listDirs(appContext).fold(
                onSuccess = { extRefs ->
                    for (extRef in extRefs) {
                        val fileRef = extRef.subRef(ExtensionDefaults.MANIFEST_FILE_NAME)
                        fileRef.loadJsonAsset(appContext, serializer, ExtensionJsonConfig).fold(
                            onSuccess = { ext ->
                                ext.sourceRef = extRef
                                list.add(ext)
                            },
                            onFailure = { error ->
                                flogError { error.toString() }
                            },
                        )
                    }
                },
                onFailure = { error ->
                    flogError { error.toString() }
                },
            )
            return list.toList()
        }

        private fun indexInternalModule(): List<Pair<T, Boolean>> {
            val list = mutableListOf<Pair<T, Boolean>>()
            internalModuleRef.listFiles(appContext).fold(
                onSuccess = { extRefs ->
                    for (extRef in extRefs) {
                        val fileRef = extRef.absoluteFile(appContext)
                        if (!fileRef.name.endsWith("." + ExtensionDefaults.FILE_EXTENSION)) {
                            continue
                        }
                        // DRS M0.2 — a disabled archive stays OUT of the live
                        // index (it must not load or resolve), but stays visible
                        // through allWithDisabledState with enabled=false.
                        val extId = fileRef.name.removeSuffix("." + ExtensionDefaults.FILE_EXTENSION)
                        val disabled = ExtensionLifecycle(internalModuleDir).isDisabled(extId)
                        ZipUtils.readFileFromArchive(appContext, extRef, ExtensionDefaults.MANIFEST_FILE_NAME).fold(
                            onSuccess = { metaStr ->
                                loadJsonAsset(metaStr, serializer, ExtensionJsonConfig).fold(
                                    onSuccess = { ext ->
                                        ext.sourceRef = extRef
                                        list.add(ext to !disabled)
                                    },
                                    onFailure = { error ->
                                        flogError { error.toString() }
                                    },
                                )
                            },
                            onFailure = { error ->
                                flogError { error.toString() }
                            },
                        )
                    }
                },
                onFailure = { error ->
                    flogError { error.toString() }
                },
            )
            return list.toList()
        }
    }
}
