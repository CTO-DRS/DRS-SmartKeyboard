/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.ext

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.settings.advanced.RadioListItem
import com.drs.smartkeyboard.app.settings.theme.DialogProperty
import com.drs.smartkeyboard.app.settings.theme.PrettyPrintConfig
import com.drs.smartkeyboard.app.settings.theme.ThemeEditorScreen
import com.drs.smartkeyboard.cacheManager
import com.drs.smartkeyboard.extensionManager
import com.drs.smartkeyboard.ime.keyboard.KeyboardExtension
import com.drs.smartkeyboard.ime.keyboard.KeyboardExtensionEditor
import com.drs.smartkeyboard.ime.keyboard.LayoutArrangementComponent
import com.drs.smartkeyboard.ime.keyboard.LayoutType
import com.drs.smartkeyboard.ime.nlp.LanguagePackExtension
import com.drs.smartkeyboard.ime.theme.ThemeExtension
import com.drs.smartkeyboard.ime.theme.ThemeExtensionComponent
import com.drs.smartkeyboard.ime.theme.ThemeExtensionComponentEditor
import com.drs.smartkeyboard.ime.theme.ThemeExtensionComponentImpl
import com.drs.smartkeyboard.ime.theme.ThemeExtensionEditor
import com.drs.smartkeyboard.lib.ValidationResult
import com.drs.smartkeyboard.lib.cache.CacheManager
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.compose.DrsUnsavedChangesDialog
import com.drs.smartkeyboard.lib.compose.Validation
import com.drs.smartkeyboard.lib.ext.Extension
import com.drs.smartkeyboard.lib.ext.ExtensionComponent
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import com.drs.smartkeyboard.lib.ext.ExtensionDefaults
import com.drs.smartkeyboard.lib.ext.ExtensionEditor
import com.drs.smartkeyboard.lib.ext.ExtensionJsonConfig
import com.drs.smartkeyboard.lib.ext.ExtensionMaintainer
import com.drs.smartkeyboard.lib.ext.ExtensionManager
import com.drs.smartkeyboard.lib.ext.ExtensionMeta
import com.drs.smartkeyboard.lib.ext.ExtensionValidation
import com.drs.smartkeyboard.lib.ext.validate
import com.drs.smartkeyboard.lib.io.DrsRef
import com.drs.smartkeyboard.lib.io.ZipUtils
import com.drs.smartkeyboard.lib.io.delete
import com.drs.smartkeyboard.lib.rememberValidationResult
import com.drs.smartkeyboard.themeManager
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.material.ui.JetPrefAlertDialog
import org.drs.jetpref.material.ui.JetPrefTextField
import java.io.File
import java.util.*
import org.drs.lib.compose.DrsButtonBar
import org.drs.lib.compose.DrsIconButton
import org.drs.lib.compose.DrsInfoCard
import org.drs.lib.compose.DrsOutlinedBox
import org.drs.lib.compose.defaultDrsOutlinedBox
import org.drs.lib.compose.stringRes
import org.drs.lib.android.showLongToastSync
import org.drs.lib.android.stringRes
import org.drs.lib.kotlin.io.FsDir
import org.drs.lib.kotlin.io.FsFile
import org.drs.lib.kotlin.io.deleteContentsRecursively
import org.drs.lib.kotlin.io.subDir
import org.drs.lib.kotlin.io.subFile
import org.drs.lib.kotlin.io.writeJson
import kotlin.reflect.KClass

private val TextFieldVerticalPadding = 8.dp
private val MetaDataContentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)

private const val AnimationDuration = 300

private val ActionScreenEnterTransition = fadeIn(tween(AnimationDuration))
private val ActionScreenExitTransition = fadeOut(tween(AnimationDuration))

sealed class EditorAction {
    object ManageMetaData : EditorAction()

    object ManageDependencies : EditorAction()

    object ManageFiles : EditorAction()

    data class CreateComponent<T : ExtensionComponent>(val type: KClass<T>) : EditorAction()

    data class ManageComponent(val editor: ExtensionComponent) : EditorAction()

    /** DRS M0.1 — open the visual keyboard layout arrangement editor. */
    data class ManageKeyboardArrangement(
        val typeId: String,
        val component: LayoutArrangementComponent,
    ) : EditorAction()
}

@Composable
fun ExtensionEditScreen(id: String, createSerialType: String?) {
    val context = LocalContext.current
    val cacheManager by context.cacheManager()
    val extensionManager by context.extensionManager()

    @Suppress("unchecked_cast")
    fun <W : CacheManager.ExtEditorWorkspace<T>, T : ExtensionEditor> getOrCreateWorkspace(
        uuid: String,
        container: CacheManager.WorkspacesContainer<W>,
        ext: Extension,
    ): W {
        val workspace = container.getWorkspaceByUuid(uuid)
        return workspace ?: container.new(uuid).also { newWorkspace ->
            val sourceRef = ext.sourceRef
            if (createSerialType == null) {
                checkNotNull(sourceRef) { "Extension source ref must not be null" }
                ZipUtils.unzip(context, sourceRef, newWorkspace.extDir)
            }
            newWorkspace.ext = ext
            newWorkspace.editor = ext.edit() as? T
        }
    }

    val ext = extensionManager.getExtensionById(id) ?: remember {
        val meta = ExtensionMeta(
            id = ExtensionDefaults.createLocalId("themes", System.currentTimeMillis().toString()),
            version = "0.0.0",
            title = "My themes",
            maintainers = listOf(ExtensionMaintainer(name = "Local")),
            license = "(none specified)",
        )
        when (createSerialType) {
            ThemeExtension.SERIAL_TYPE -> ThemeExtension(meta, null, emptyList())
            else -> null
        }
    }
    if (ext != null) {
        val uuid = rememberSaveable { UUID.randomUUID().toString() }
        val cacheWorkspace = remember {
            runCatching {
                when (ext) {
                    is ThemeExtension -> {
                        getOrCreateWorkspace(uuid, cacheManager.themeExtEditor, ext)
                    }
                    is KeyboardExtension -> {
                        // DRS M0.1 — keyboard extensions are editable through
                        // their own workspace container (no theme-dir mixing).
                        getOrCreateWorkspace(uuid, cacheManager.keyExtEditor, ext)
                    }
                    else -> null
                }
            }
        }
        cacheWorkspace.onSuccess { workspace ->
            if (workspace?.editor != null) {
                ExtensionEditScreenSheetSwitcher(workspace, isCreateExt = createSerialType != null)
            } else {
                ExtensionNotFoundScreen(id = id)
            }
        }.onFailure { error ->
            Text(text = remember(error) { error.stackTraceToString() })
        }
    } else {
        ExtensionNotFoundScreen(id)
    }
}

@Composable
private fun ExtensionEditScreenSheetSwitcher(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    isCreateExt: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        EditScreen(workspace, isCreateExt)
        AnimatedVisibility(
            visible = workspace.currentAction != null,
            enter = ActionScreenEnterTransition,
            exit = ActionScreenExitTransition,
        ) {
            when (val action = workspace.currentAction) {
                is EditorAction.ManageMetaData -> {
                    ManageMetaDataScreen(workspace, isCreateExt)
                }
                is EditorAction.ManageDependencies -> {
                    ManageDependenciesScreen(workspace)
                }
                is EditorAction.ManageFiles -> {
                    ExtensionEditFilesScreen(workspace)
                }
                is EditorAction.CreateComponent<*> -> {
                    CreateComponentScreen(workspace, action.type)
                }
                is EditorAction.ManageComponent -> when (action.editor) {
                    is ThemeExtensionComponentEditor -> {
                        ThemeEditorScreen(workspace, action.editor)
                    }
                    else -> {
                        // Render nothing
                    }
                }
                is EditorAction.ManageKeyboardArrangement -> {
                    KeyboardLayoutEditorScreen(workspace, action.typeId, action.component)
                }
                else -> {
                    // Render nothing
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun EditScreen(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    isCreateExt: Boolean,
) = DrsScreen {
    title = stringRes(if (isCreateExt) {
        when (workspace.ext) {
            is KeyboardExtension -> R.string.ext__editor__title_create_keyboard
            is ThemeExtension -> R.string.ext__editor__title_create_theme
            else -> R.string.ext__editor__title_create_any
        }
    } else {
        when (workspace.ext) {
            is KeyboardExtension -> R.string.ext__editor__title_edit_keyboard
            is ThemeExtension -> R.string.ext__editor__title_edit_theme
            else -> R.string.ext__editor__title_edit_any
        }
    })

    val context = LocalContext.current
    val navController = LocalNavController.current

    val extEditor = workspace.editor ?: return@DrsScreen
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var showInvalidMetadataDialog by remember { mutableStateOf(false) }

    fun handleBackPress() {
        if (workspace.isModified) {
            showUnsavedChangesDialog = true
        } else {
            workspace.close()
            navController.popBackStack()
        }
    }

    fun handleSave() {
        if (!extEditor.meta.validate()) {
            showUnsavedChangesDialog = false
            showInvalidMetadataDialog = true
            return
        }
        // DRS p7 (E3-6) — saving after editing meta.id used to copy the new-id manifest
        // into the OLD id's archive (index file name ≠ meta.id; the old id silently
        // vanished; renaming onto an installed id yielded two archives claiming one id).
        // Detect the rename up front and refuse genuine collisions via the existing
        // invalid-metadata dialog.
        val oldExt = workspace.ext
        val idChanged = !isCreateExt && oldExt != null && oldExt.meta.id != extEditor.meta.id
        if (idChanged && context.extensionManager().value.getExtensionById(extEditor.meta.id) != null) {
            showUnsavedChangesDialog = false
            showInvalidMetadataDialog = true
            return
        }
        val manifest = extEditor.build()
        workspace.saverDir.deleteContentsRecursively()
        val manifestFile = workspace.saverDir.subFile(ExtensionDefaults.MANIFEST_FILE_NAME)
        manifestFile.writeJson(manifest, ExtensionJsonConfig)
        when (extEditor) {
            is ThemeExtensionEditor -> {
                // TODO: this is hacky
                val fonts = workspace.extDir.subDir("fonts")
                if (fonts.exists()) {
                    fonts.copyRecursively(workspace.saverDir.subDir("fonts"), overwrite = true)
                }
                val images = workspace.extDir.subDir("images")
                if (images.exists()) {
                    images.copyRecursively(workspace.saverDir.subDir("images"), overwrite = true)
                }
                for (theme in extEditor.themes) {
                    // DRS p7 (E3-4) — stylesheet targets resolved through canonical
                    // containment: a crafted/typo'd stylesheetPath (e.g. "../../files/
                    // drs_state.json") used to write stylesheet JSON anywhere in the app
                    // sandbox. Violation → file__error_invalid_name toast + abort.
                    val stylesheetFile = containedSubFile(workspace.saverDir, theme.stylesheetPath())
                    if (stylesheetFile == null) {
                        context.showLongToastSync(context.stringRes(R.string.file__error_invalid_name))
                        return
                    }
                    stylesheetFile.parentFile?.mkdirs()
                    val stylesheetEditor = theme.stylesheetEditor
                    if (stylesheetEditor != null) {
                        runCatching {
                            val stylesheet = stylesheetEditor.build().toJson(PrettyPrintConfig).getOrThrow()
                            stylesheetFile.writeText(stylesheet)
                        }.onFailure {
                            // TODO: better error handling
                            context.showLongToastSync(it.message.toString())
                            return
                        }
                    } else {
                        val unmodifiedStylesheetFile = containedSubFile(workspace.extDir, theme.stylesheetPath())
                        if (unmodifiedStylesheetFile == null) {
                            context.showLongToastSync(context.stringRes(R.string.file__error_invalid_name))
                            return
                        }
                        if (unmodifiedStylesheetFile.exists()) {
                            unmodifiedStylesheetFile.copyTo(stylesheetFile, overwrite = true)
                        }
                    }
                }
            }
            is KeyboardExtensionEditor -> {
                // DRS M0.1 — save = stage the WHOLE source package (manifest,
                // layouts, every other file) into saverDir, then overwrite the
                // visually edited arrangement files through the E3-4 containment
                // resolver so a crafted arrangementFile can never steer the write
                // outside the package. The manifest was already written above via
                // extEditor.build(); the common code below zips + publishes.
                workspace.extDir.copyRecursively(workspace.saverDir, overwrite = true)
                for (entry in extEditor.editedArrangements.entries) {
                    val key = entry.key
                    val arrangementEditor = entry.value
                    val typeId = key.substringBefore(':')
                    val arrangementId = key.substringAfter(':')
                    val type = LayoutType.entries.firstOrNull { it.id == typeId } ?: continue
                    val component = extEditor.layouts[typeId]?.firstOrNull { it.id == arrangementId } ?: continue
                    val arrangementFile = containedSubFile(workspace.saverDir, component.arrangementFile(type))
                    if (arrangementFile == null) {
                        context.showLongToastSync(context.stringRes(R.string.file__error_invalid_name))
                        return
                    }
                    arrangementFile.parentFile?.mkdirs()
                    runCatching {
                        val json = ExtensionJsonConfig.encodeToString(
                            kotlinx.serialization.json.JsonElement.serializer(),
                            arrangementEditor.toJson(),
                        )
                        arrangementFile.writeText(json)
                    }.onFailure {
                        context.showLongToastSync(it.message.toString())
                        return
                    }
                }
            }
            else -> { }
        }
        val flexArchiveName = ExtensionDefaults.createFlexName(extEditor.meta.id)
        val flexArchiveFile = workspace.dir.subFile(flexArchiveName)
        ZipUtils.zip(workspace.saverDir, flexArchiveFile)
        // DRS p7 (E3-6) — publish to the archive name matching the (possibly NEW) id:
        // create flow → the theme module dir (unchanged); id-rename flow → the old
        // extension's module dir + createFlexName(newId), NOT the old sourceRef;
        // same-id edit flow → the old sourceRef (byte-for-byte the old behavior).
        val targetRef = when {
            isCreateExt -> DrsRef.internal(ExtensionManager.IME_THEME_PATH).subRef(flexArchiveName)
            idChanged -> DrsRef.internal(modulePathOf(oldExt!!)).subRef(flexArchiveName)
            else -> oldExt!!.sourceRef!!
        }
        val targetFile = targetRef.absoluteFile(context)
        // DRS p7 (E3-2) — atomic publish (tmp + rename, logged fallback) instead of
        // copyTo(overwrite = true): the installed .flex is the only on-disk copy and
        // must never be briefly missing/truncated mid-publish.
        ZipUtils.publishAtomic(flexArchiveFile, targetFile)
        if (idChanged) {
            // DRS p7 (E3-6) — delete the OLD internal archive only AFTER the new one is
            // safely published; assets-based refs are never deleted (canDelete contract).
            val oldSourceRef = oldExt!!.sourceRef
            if (oldSourceRef?.isInternal == true) {
                oldSourceRef.delete(context)
            }
        }
        workspace.close()
        navController.popBackStack()
    }

    navigationIcon {
        // DRS a11y/i18n/ux (r0-I) FIX 7 — app-bar back button labeled.
        DrsIconButton(
            onClick = { handleBackPress() },
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringRes(R.string.action__navigate_back),
        )
    }

    bottomBar {
        DrsButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(text = stringRes(R.string.action__cancel)) {
                handleBackPress()
            }
            ButtonBarButton(text = stringRes(R.string.action__save)) {
                handleSave()
            }
        }
    }

    content {
        BackHandler {
            handleBackPress()
        }

        DrsOutlinedBox(
            modifier = Modifier.defaultDrsOutlinedBox(),
        ) {
            Preference(
                onClick = { workspace.currentAction = EditorAction.ManageMetaData },
                icon = Icons.Default.Code,
                title = stringRes(R.string.ext__editor__metadata__title),
            )
            Preference(
                onClick = { workspace.currentAction = EditorAction.ManageDependencies },
                icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                title = stringRes(R.string.ext__editor__dependencies__title),
            )
            Preference(
                onClick = { workspace.currentAction = EditorAction.ManageFiles },
                icon = ImageVector.vectorResource(R.drawable.ic_file_blank),
                title = stringRes(R.string.ext__editor__files__title),
            )
        }

        when (extEditor) {
            is ThemeExtensionEditor -> {
                ExtensionComponentListView(
                    title = stringRes(R.string.ext__meta__components_theme),
                    components = extEditor.themes,
                    onCreateBtnClick = {
                        workspace.currentAction = EditorAction.CreateComponent(ThemeExtensionComponent::class)
                    },
                ) { component ->
                    ExtensionComponentView(
                        modifier = Modifier.defaultDrsOutlinedBox(),
                        meta = extEditor.meta,
                        component = component,
                        onDeleteBtnClick = { workspace.update { extEditor.themes.remove(component) } },
                        onEditBtnClick = { workspace.currentAction = EditorAction.ManageComponent(component) },
                    )
                }
            }
            is KeyboardExtensionEditor -> {
                // DRS M0.1 — the layout index of the keyboard extension; each
                // arrangement opens in the visual grid editor.
                KeyboardExtensionLayoutsList(workspace, extEditor)
            }
            else -> {
                // Render nothing
            }
        }

        if (showUnsavedChangesDialog) {
            DrsUnsavedChangesDialog(
                onSave = {
                    handleSave()
                },
                onDiscard = {
                    navController.popBackStack()
                    showUnsavedChangesDialog = false
                },
                onDismiss = {
                    showUnsavedChangesDialog = false
                },
            )
        }

        if (showInvalidMetadataDialog) {
            JetPrefAlertDialog(
                title = stringRes(R.string.ext__editor__metadata__title_invalid),
                confirmLabel = stringRes(R.string.action__ok),
                onConfirm = {
                    showInvalidMetadataDialog = false
                },
                onDismiss = {
                    showInvalidMetadataDialog = false
                },
                content = {
                    Text(text = stringRes(R.string.ext__editor__metadata__message_invalid))
                },
            )
        }
    }
}

@Composable
private fun ManageMetaDataScreen(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    isCreateExt: Boolean,
) = DrsScreen {
    title = stringRes(R.string.ext__editor__metadata__title)

    val meta = workspace.editor?.meta ?: return@DrsScreen
    var showValidationErrors by rememberSaveable { mutableStateOf(false) }

    var id by rememberSaveable { mutableStateOf(meta.id) }
    val idValidation = rememberValidationResult(ExtensionValidation.MetaId, id)
    var version by rememberSaveable { mutableStateOf(meta.version) }
    val versionValidation = rememberValidationResult(ExtensionValidation.MetaVersion, version)
    var title by rememberSaveable { mutableStateOf(meta.title) }
    val titleValidation = rememberValidationResult(ExtensionValidation.MetaTitle, title)
    var description by rememberSaveable { mutableStateOf(meta.description ?: "") }
    var keywords by rememberSaveable { mutableStateOf(meta.keywords?.joinToString("\n") ?: "") }
    var homepage by rememberSaveable { mutableStateOf(meta.homepage ?: "") }
    var issueTracker by rememberSaveable { mutableStateOf(meta.issueTracker ?: "") }
    var maintainers by rememberSaveable { mutableStateOf(meta.maintainers.joinToString("\n")) }
    val maintainersValidation = rememberValidationResult(ExtensionValidation.MetaMaintainers, maintainers)
    var license by rememberSaveable { mutableStateOf(meta.license) }
    val licenseValidation = rememberValidationResult(ExtensionValidation.MetaLicense, license)

    fun handleBackPress() {
        workspace.currentAction = null
    }

    fun handleApply() {
        val invalid = idValidation.isInvalid() ||
            versionValidation.isInvalid() ||
            titleValidation.isInvalid() ||
            maintainersValidation.isInvalid() ||
            licenseValidation.isInvalid()
        if (invalid) {
            showValidationErrors = true
        } else {
            workspace.update {
                workspace.editor?.meta = ExtensionMeta(
                    id = id.trim(),
                    version = version.trim(),
                    title = title.trim(),
                    description = description.trim().takeIf { it.isNotBlank() },
                    keywords = keywords.lines().map { it.trim() }.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() },
                    homepage = homepage.trim().takeIf { it.isNotBlank() },
                    issueTracker = issueTracker.trim().takeIf { it.isNotBlank() },
                    maintainers = maintainers.lines().map { it.trim() }.filter { it.isNotBlank() }
                        .map { ExtensionMaintainer.fromOrTakeRaw(it) },
                    license = license.trim(),
                )
            }
            workspace.currentAction = null
        }
    }

    navigationIcon {
        // DRS a11y/i18n/ux (r0-I) FIX 7 — sub-editor close button labeled as "done".
        DrsIconButton(
            onClick = { handleBackPress() },
            icon = Icons.Default.Close,
            contentDescription = stringRes(R.string.action__done),
        )
    }

    bottomBar {
        DrsButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(text = stringRes(R.string.action__cancel)) {
                handleBackPress()
            }
            ButtonBarButton(text = stringRes(R.string.action__apply)) {
                handleApply()
            }
        }
    }

    content {
        BackHandler {
            handleBackPress()
        }

        Column(modifier = Modifier.padding(MetaDataContentPadding)) {
            EditorSheetTextField(
                enabled = isCreateExt,
                isRequired = true,
                value = id,
                onValueChange = { id = it },
                label = stringRes(R.string.ext__meta__id),
                showValidationError = showValidationErrors,
                validationResult = idValidation,
            )
            EditorSheetTextField(
                isRequired = true,
                value = version,
                onValueChange = { version = it },
                label = stringRes(R.string.ext__meta__version),
                showValidationError = showValidationErrors,
                validationResult = versionValidation,
            )
            EditorSheetTextField(
                isRequired = true,
                value = title,
                onValueChange = { title = it },
                label = stringRes(R.string.ext__meta__title),
                showValidationError = showValidationErrors,
                validationResult = titleValidation,
            )
            EditorSheetTextField(
                value = description,
                onValueChange = { description = it },
                label = stringRes(R.string.ext__meta__description),
            )
            EditorSheetTextField(
                value = keywords,
                onValueChange = { keywords = it },
                label = stringRes(R.string.ext__meta__keywords),
                singleLine = false,
            )
            EditorSheetTextField(
                value = homepage,
                onValueChange = { homepage = it },
                label = stringRes(R.string.ext__meta__homepage),
            )
            EditorSheetTextField(
                value = issueTracker,
                onValueChange = { issueTracker = it },
                label = stringRes(R.string.ext__meta__issue_tracker),
            )
            EditorSheetTextField(
                isRequired = true,
                value = maintainers,
                onValueChange = { maintainers = it },
                label = stringRes(R.string.ext__meta__maintainers),
                singleLine = false,
                showValidationError = showValidationErrors,
                validationResult = maintainersValidation,
            )
            EditorSheetTextField(
                isRequired = true,
                value = license,
                onValueChange = { license = it },
                label = stringRes(R.string.ext__meta__license),
                showValidationError = showValidationErrors,
                validationResult = licenseValidation,
            )
        }
    }
}

@Composable
private fun ManageDependenciesScreen(workspace: CacheManager.ExtEditorWorkspace<*>) = DrsScreen {
    title = stringRes(R.string.ext__editor__dependencies__title)

    val dependencyList = workspace.editor?.dependencies ?: return@DrsScreen

    fun handleBackPress() {
        workspace.currentAction = null
    }

    navigationIcon {
        // DRS a11y/i18n/ux (r0-I) FIX 7 — sub-editor close button labeled as "done".
        DrsIconButton(
            onClick = { handleBackPress() },
            icon = Icons.Default.Close,
            contentDescription = stringRes(R.string.action__done),
        )
    }

    content {
        BackHandler {
            handleBackPress()
        }

        DrsInfoCard(
            modifier = Modifier.padding(all = 8.dp),
            text = """
                Dependencies are currently not implemented, but are already somewhat
                integrated as a placeholder for the future.
                """.trimIndent().replace('\n', ' '),
        )
        if (dependencyList.isEmpty()) {
            Text(text = "no deps found")
        } else {
            for (dependency in dependencyList) {
                Text(text = dependency)
            }
        }
    }
}

private enum class CreateFrom {
    EMPTY,
    EXISTING;
}

@Composable
private fun <T : ExtensionComponent> CreateComponentScreen(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    type: KClass<T>,
) = DrsScreen {
    title = stringRes(when (type) {
        ThemeExtensionComponent::class -> R.string.ext__editor__create_component__title_theme
        else -> R.string.ext__editor__create_component__title
    })

    val context = LocalContext.current
    val extensionManager by context.extensionManager()
    val themeManager by context.themeManager()

    var createFrom by rememberSaveable { mutableStateOf(CreateFrom.EXISTING) }
    val extId = workspace.editor?.meta?.id ?: "null"
    val components = remember<Map<ExtensionComponentName, ExtensionComponent>> {
        when (val editor = workspace.editor) {
            is ThemeExtensionEditor -> buildMap {
                for (theme in editor.themes) {
                    put(ExtensionComponentName(extId, theme.id), theme)
                }
                for ((componentName, theme) in themeManager.indexedThemeConfigs.value.first) {
                    if (componentName.extensionId != extId) {
                        put(componentName, theme)
                    }
                }
            }
            else -> {
                emptyMap()
            }
        }
    }
    var selectedComponentName by rememberSaveable(stateSaver = ExtensionComponentName.Saver) {
        mutableStateOf(null)
    }
    var showValidationErrors by rememberSaveable { mutableStateOf(false) }

    var newId by rememberSaveable { mutableStateOf("") }
    val newIdValidation = rememberValidationResult(ExtensionValidation.ComponentId, newId)
    var newLabel by rememberSaveable { mutableStateOf("") }
    val newLabelValidation = rememberValidationResult(ExtensionValidation.ComponentLabel, newLabel)
    var newAuthors by rememberSaveable { mutableStateOf("") }
    val newAuthorsValidation = rememberValidationResult(ExtensionValidation.ComponentAuthors, newAuthors)

    fun handleBackPress() {
        workspace.currentAction = null
    }

    fun handleCreate() {
        val invalid = createFrom == CreateFrom.EMPTY && (newIdValidation.isInvalid() ||
            newLabelValidation.isInvalid() || newAuthorsValidation.isInvalid())
        if (invalid) {
            showValidationErrors = true
        } else {
            when (val editor = workspace.editor) {
                is ThemeExtensionEditor -> {
                    when (createFrom) {
                        CreateFrom.EMPTY -> {
                            if (editor.themes.any { it.id == newId.trim() }) {
                                // DRS a11y/i18n/ux (r0-I) FIX 7 — literal → localized string.
                                context.showLongToastSync(context.stringRes(R.string.theme__error_id_exists))
                            } else {
                                val componentEditor = ThemeExtensionComponentEditor(
                                    id = newId.trim(),
                                    label = newLabel.trim(),
                                    authors = newAuthors.lines().map { it.trim() }.filter { it.isNotBlank() },
                                )
                                editor.themes.add(componentEditor)
                                workspace.currentAction = null
                            }
                        }
                        CreateFrom.EXISTING -> {
                            val componentName = selectedComponentName ?: return
                            val componentId = if (editor.themes.any { it.id == componentName.componentId }) {
                                var suffix = 1
                                var tempId: String
                                do {
                                    tempId = "${componentName.componentId}_${suffix++}"
                                } while (editor.themes.any { it.id == tempId })
                                tempId
                            } else {
                                componentName.componentId
                            }
                            if (componentName.extensionId == extId) {
                                val component = editor.themes.find { it.id == componentName.componentId } ?: return
                                val componentEditor = component.let { c ->
                                    ThemeExtensionComponentEditor(
                                        componentId, c.label, c.authors, c.isNightTheme, stylesheetPath = "",
                                    ).also { it.stylesheetEditor = c.stylesheetEditor }
                                }
                                if (componentEditor.stylesheetEditor != null) {
                                    val stylesheetFile = workspace.extDir.subFile(componentEditor.stylesheetPath())
                                    stylesheetFile.parentFile?.mkdirs()
                                    val stylesheet = componentEditor.stylesheetEditor!!.build().toJson(PrettyPrintConfig).getOrThrow()
                                    stylesheetFile.writeText(stylesheet)
                                    componentEditor.stylesheetEditor = null
                                } else {
                                    val srcStylesheetFile = workspace.extDir.subFile(component.stylesheetPath())
                                    val dstStylesheetFile = workspace.extDir.subFile(componentEditor.stylesheetPath())
                                    dstStylesheetFile.parentFile?.mkdirs()
                                    srcStylesheetFile.copyTo(dstStylesheetFile, overwrite = true)
                                }
                                editor.themes.add(componentEditor)
                            } else {
                                val component = themeManager.indexedThemeConfigs.value.first.get(componentName) ?: return
                                val componentEditor = (component as? ThemeExtensionComponentImpl)?.edit() ?: return
                                componentEditor.id = componentId
                                componentEditor.stylesheetPath = ""
                                val externalExt = extensionManager.getExtensionById(componentName.extensionId) ?: return
                                val stylesheetJson = ZipUtils.readFileFromArchive(
                                    context, externalExt.sourceRef!!, component.stylesheetPath()
                                ).getOrNull() ?: return
                                val dstStylesheetFile = workspace.extDir.subFile(componentEditor.stylesheetPath())
                                dstStylesheetFile.parentFile?.mkdirs()
                                dstStylesheetFile.writeText(stylesheetJson)
                                editor.themes.add(componentEditor)
                            }
                            workspace.currentAction = null
                        }
                    }
                }
            }
        }
    }

    fun hasSufficientInfoForCreating(): Boolean {
        return when (createFrom) {
            CreateFrom.EMPTY -> newId.isNotBlank() && newLabel.isNotBlank() && newAuthors.isNotBlank()
            CreateFrom.EXISTING -> components.containsKey(selectedComponentName)
        }
    }

    navigationIcon {
        // DRS a11y/i18n/ux (r0-I) FIX 7 — sub-editor close button labeled as "done".
        DrsIconButton(
            onClick = { handleBackPress() },
            icon = Icons.Default.Close,
            contentDescription = stringRes(R.string.action__done),
        )
    }

    bottomBar {
        DrsButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(text = stringRes(R.string.action__cancel)) {
                handleBackPress()
            }
            ButtonBarButton(
                text = stringRes(R.string.action__create),
                enabled = hasSufficientInfoForCreating(),
            ) {
                handleCreate()
            }
        }
    }

    content {
        BackHandler {
            handleBackPress()
        }

        DrsOutlinedBox(
            modifier = Modifier.defaultDrsOutlinedBox(),
        ) {
            RadioListItem(
                onClick = { createFrom = CreateFrom.EXISTING },
                selected = createFrom == CreateFrom.EXISTING,
                text = stringRes(R.string.ext__editor__create_component__from_existing),
            )
            RadioListItem(
                onClick = { createFrom = CreateFrom.EMPTY },
                selected = createFrom == CreateFrom.EMPTY,
                text = stringRes(R.string.ext__editor__create_component__from_empty),
            )
        }

        if (createFrom == CreateFrom.EXISTING) {
            DrsOutlinedBox(
                modifier = Modifier.defaultDrsOutlinedBox(),
            ) {
                for ((componentName, component) in components) {
                    RadioListItem(
                        onClick = { selectedComponentName = componentName },
                        selected = selectedComponentName == componentName,
                        text = component.label,
                        secondaryText = componentName.toString(),
                    )
                }
            }
        } else if (createFrom == CreateFrom.EMPTY) {
            DrsInfoCard(
                modifier = Modifier.defaultDrsOutlinedBox(),
                text = stringRes(R.string.ext__editor__create_component__from_empty_warning),
            )
            DialogProperty(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringRes(R.string.ext__meta__id),
            ) {
                JetPrefTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newId,
                    onValueChange = { newId = it },
                    singleLine = true,
                )
                Validation(showValidationErrors, newIdValidation)
            }
            DialogProperty(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringRes(R.string.ext__meta__label),
            ) {
                JetPrefTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    singleLine = true,
                )
                Validation(showValidationErrors, newLabelValidation)

            }
            DialogProperty(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringRes(R.string.ext__meta__authors),
            ) {
                JetPrefTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newAuthors,
                    onValueChange = { newAuthors = it },
                )
                Validation(showValidationErrors, newAuthorsValidation)
            }
        }
    }
}

@Composable
private fun EditorSheetTextField(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isRequired: Boolean = false,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    showValidationError: Boolean = false,
    validationResult: ValidationResult? = null,
) {
    Column(modifier = Modifier.padding(vertical = TextFieldVerticalPadding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = TextFieldVerticalPadding),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
            if (isRequired) {
                Text(
                    modifier = Modifier.padding(start = 2.dp),
                    text = "*",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        JetPrefTextField(
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
        )
        Validation(showValidationError, validationResult)
    }
}

/**
 * DRS p7 (E3-4) — canonical containment for the editor's stylesheet write target and
 * unmodified-copy source: the resolved file must stay inside [root]. A crafted or
 * typo'd stylesheetPath (e.g. "../drs_state.json" or "/files/…") returns null so the
 * caller aborts with the file__error_invalid_name toast instead of writing/reading
 * outside the extension workspace. Legit relative subpaths ("stylesheets/x.json",
 * "sub/dir/x.json") pass unchanged.
 * DRS p9 (T-3): internal since then so the JVM regression suite can pin the
 * containment contract directly.
 */
internal fun containedSubFile(root: FsDir, relPath: String): FsFile? {
    val rootCanonical = root.canonicalPath
    val candidate = root.subFile(relPath).canonicalFile
    return if (candidate.path == rootCanonical ||
        candidate.path.startsWith(rootCanonical + File.separator)
    ) {
        candidate
    } else {
        null
    }
}

/**
 * DRS p7 (E3-6) — the internal module dir an [Extension] of the given runtime type
 * publishes into (mirrors ExtensionManager.import's type dispatch). Used when an
 * extension id rename must publish under the NEW id's archive name in the SAME
 * module directory as the old extension.
 */
private fun modulePathOf(ext: Extension): String = when (ext) {
    is KeyboardExtension -> ExtensionManager.IME_KEYBOARD_PATH
    is LanguagePackExtension -> ExtensionManager.IME_LANGUAGEPACK_PATH
    is ThemeExtension -> ExtensionManager.IME_THEME_PATH
    else -> ExtensionManager.IME_THEME_PATH
}
