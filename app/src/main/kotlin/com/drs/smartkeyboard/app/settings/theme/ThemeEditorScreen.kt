/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.apptheme.Shapes
import com.drs.smartkeyboard.app.ext.ExtensionComponentView
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.ime.theme.ThemeExtensionComponent
import com.drs.smartkeyboard.ime.theme.ThemeExtensionComponentEditor
import com.drs.smartkeyboard.ime.theme.ThemeExtensionEditor
import com.drs.smartkeyboard.ime.theme.ThemeManager
import com.drs.smartkeyboard.ime.theme.extPreviewTheme
import com.drs.smartkeyboard.lib.cache.CacheManager
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.compose.PreviewKeyboardField
import com.drs.smartkeyboard.lib.compose.Validation
import com.drs.smartkeyboard.lib.compose.rememberPreviewFieldController
import com.drs.smartkeyboard.lib.ext.ExtensionValidation
import com.drs.smartkeyboard.lib.rememberValidationResult
import com.drs.smartkeyboard.themeManager
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.jetpref.material.ui.JetPrefAlertDialog
import org.drs.jetpref.material.ui.JetPrefDropdown
import org.drs.jetpref.material.ui.JetPrefListItem
import org.drs.jetpref.material.ui.JetPrefTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drs.lib.android.showLongToastSync
import org.drs.lib.color.MaterialYouFlagsSaver
import org.drs.lib.compose.DrsIconButton
import org.drs.lib.compose.DrsOutlinedBox
import org.drs.lib.compose.defaultDrsOutlinedBox
import org.drs.lib.compose.drsVerticalScroll
import org.drs.lib.compose.rippleClickable
import org.drs.lib.android.stringRes
import org.drs.lib.compose.stringRes
import org.drs.lib.kotlin.io.subFile
import org.drs.lib.snygg.SnyggAnnotationRule
import org.drs.lib.snygg.SnyggElementRule
import org.drs.lib.snygg.SnyggJsonConfiguration
import org.drs.lib.snygg.SnyggMultiplePropertySetsEditor
import org.drs.lib.snygg.SnyggRule
import org.drs.lib.snygg.SnyggSelector
import org.drs.lib.snygg.SnyggSinglePropertySetEditor
import org.drs.lib.snygg.SnyggSpec
import org.drs.lib.snygg.SnyggSpecDecl
import org.drs.lib.snygg.SnyggStylesheet
import org.drs.lib.snygg.SnyggStylesheetEditor
import org.drs.lib.snygg.ui.Saver

internal val PrettyPrintConfig = SnyggJsonConfiguration.of(
    prettyPrint = true,
    prettyPrintIndent = "  ",
)

private val LenientConfig = SnyggJsonConfiguration.of(
    ignoreMissingSchema = true,
    ignoreInvalidSchema = true,
    ignoreUnsupportedSchema = true,
    ignoreInvalidRules = true,
    ignoreInvalidProperties = true,
    ignoreInvalidValues = true,
)

private enum class StylesheetLoadingStrategy {
    TRY_LOAD_OR_ASK_ON_CONFLICT, // default state
    TRY_LOAD_OR_EMPTY, // user chose to not auto-fix errors
    TRY_LOAD_OR_PARSE_LENIENT; // user chose to auto-fix errors
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeEditorScreen(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    editor: ThemeExtensionComponentEditor,
) = DrsScreen {
    title = stringRes(R.string.ext__editor__edit_component__title_theme)
    scrollable = false

    val prefs by DrsPreferenceStore
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val themeManager by context.themeManager()

    val scope = rememberCoroutineScope()
    val previewFieldController = rememberPreviewFieldController().also { it.isVisible = true }

    // DRS: the theme editor hosts the preview pill inline in its own bottom bar
    // (next to its "add rule" FAB), so the activity-level draggable overlay
    // must stand down while this screen is current.
    DisposableEffect(previewFieldController) {
        previewFieldController.usesInlinePill = true
        onDispose { previewFieldController.usesInlinePill = false }
    }

    var stylesheetLoadingStrategy by rememberSaveable {
        mutableStateOf(StylesheetLoadingStrategy.TRY_LOAD_OR_ASK_ON_CONFLICT)
    }
    var stylesheetEditorFailure by remember { mutableStateOf<Throwable?>(null) }
    val stylesheetEditor = remember(stylesheetLoadingStrategy) {
        editor.stylesheetEditor ?: run {
            stylesheetEditorFailure = null
            val stylesheetPath = editor.stylesheetPath()
            editor.stylesheetPathOnLoad = stylesheetPath
            val stylesheetFile = workspace.extDir.subFile(stylesheetPath)
            val stylesheetEditor = if (stylesheetFile.exists()) {
                try {
                    val stylesheetJson = stylesheetFile.readText()
                    val config = when (stylesheetLoadingStrategy) {
                        StylesheetLoadingStrategy.TRY_LOAD_OR_PARSE_LENIENT -> LenientConfig
                        else -> PrettyPrintConfig
                    }
                    SnyggStylesheet.fromJson(stylesheetJson, config).getOrThrow().edit(CustomRuleComparator)
                } catch (error: Throwable) {
                    stylesheetEditorFailure = when (stylesheetLoadingStrategy) {
                        StylesheetLoadingStrategy.TRY_LOAD_OR_ASK_ON_CONFLICT -> error
                        else -> null
                    }
                    SnyggStylesheetEditor(SnyggStylesheet.SCHEMA_V2, comparator = CustomRuleComparator)
                }
            } else {
                SnyggStylesheetEditor(SnyggStylesheet.SCHEMA_V2, comparator = CustomRuleComparator)
            }
            stylesheetEditor.rules.putIfAbsent(SnyggAnnotationRule.Defines, SnyggSinglePropertySetEditor())
            stylesheetEditor
        }.also { editor.stylesheetEditor = it }
    }

    val definedVariables = remember(stylesheetEditor.rules, workspace.version) {
        stylesheetEditor.rules.firstNotNullOfOrNull { (rule, propertySet) ->
            if (rule is SnyggAnnotationRule.Defines && propertySet is SnyggSinglePropertySetEditor) {
                propertySet.properties
            } else {
                null
            }
        } ?: emptyMap()
    }

    val fontNames = remember(stylesheetEditor.rules, workspace.version) {
        stylesheetEditor.rules.mapNotNull { (rule, _) ->
            if (rule is SnyggAnnotationRule.Font) {
                rule.fontName
            } else {
                null
            }
        }
    }

    val snyggLevel by prefs.theme.editorLevel.collectAsState()
    val colorRepresentation by prefs.theme.editorColorRepresentation.collectAsState()
    val displayKbdAfterDialogs by prefs.theme.editorDisplayKbdAfterDialogs.collectAsState()
    var oldFocusState by remember { mutableStateOf(false) }
    var snyggRuleToEdit by rememberSaveable(stateSaver = SnyggRule.Saver) { mutableStateOf(null) }
    var snyggPropertyToEdit by remember { mutableStateOf<PropertyInfo?>(null) }
    var snyggPropertySetForEditing = remember<SnyggSinglePropertySetEditor?> { null }
    var showEditComponentMetaDialog by rememberSaveable { mutableStateOf(false) }
    var showFineTuneDialog by rememberSaveable { mutableStateOf(false) }

    fun handleBackPress() {
        workspace.currentAction = null
    }

    navigationIcon {
        // DRS a11y/i18n/ux (r0-I) FIX 2 — close (discard editor) button labeled.
        DrsIconButton(
            onClick = { handleBackPress() },
            icon = Icons.Default.Close,
            contentDescription = stringRes(R.string.action__discard),
        )
    }

    actions {
        // DRS a11y/i18n/ux (r0-I) FIX 2 — fine-tune entry labeled. Reuses the existing
        // settings__theme_editor__fine_tune__title key (the lost fine_tune__title key).
        DrsIconButton(
            onClick = { showFineTuneDialog = true },
            icon = Icons.Default.Tune,
            contentDescription = stringRes(R.string.settings__theme_editor__fine_tune__title),
        )
    }

    floatingActionButton {
        ExtendedFloatingActionButton(
            icon = { Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
            ) },
            text = { Text(
                text = stringRes(R.string.settings__theme_editor__add_rule),
            ) },
            onClick = { snyggRuleToEdit = SnyggEmptyRuleForAdding },
        )
    }

    bottomBar {
        PreviewKeyboardField(previewFieldController)
    }

    content {
        stylesheetEditorFailure?.let { failure ->
            JetPrefAlertDialog(
                title = stringRes(R.string.settings__theme_editor__stylesheet_error_title),
                confirmLabel = stringRes(R.string.action__yes),
                onConfirm = {
                    editor.stylesheetEditor = null
                    stylesheetLoadingStrategy = StylesheetLoadingStrategy.TRY_LOAD_OR_PARSE_LENIENT
                },
                dismissLabel = stringRes(R.string.action__no),
                onDismiss = {
                    editor.stylesheetEditor = null
                    stylesheetLoadingStrategy = StylesheetLoadingStrategy.TRY_LOAD_OR_EMPTY
                },
            ) {
                Column {
                    Text(
                        modifier = Modifier.padding(bottom = 8.dp),
                        text = failure.message.toString(),
                        fontStyle = FontStyle.Italic,
                    )
                    Text(
                        text = stringRes(R.string.settings__theme_editor__stylesheet_error_description),
                    )
                }
            }
        }

        BackHandler {
            handleBackPress()
        }

        val isImeVisible = WindowInsets.isImeVisible
        LaunchedEffect(showEditComponentMetaDialog, showFineTuneDialog, snyggRuleToEdit, snyggPropertyToEdit) {
            val visible = showEditComponentMetaDialog || showFineTuneDialog ||
                snyggRuleToEdit != null || snyggPropertyToEdit != null
            if (visible) {
                oldFocusState = isImeVisible
                focusManager.clearFocus()
            } else {
                delay(250)
                when (displayKbdAfterDialogs) {
                    DisplayKbdAfterDialogs.ALWAYS -> {
                        previewFieldController.expandAndFocus()
                    }
                    DisplayKbdAfterDialogs.NEVER -> {
                        // Do nothing
                    }
                    DisplayKbdAfterDialogs.REMEMBER -> {
                        if (oldFocusState) {
                            previewFieldController.expandAndFocus()
                        }
                    }
                }
            }
        }

        DisposableEffect(workspace.version) {
            themeManager.previewThemeInfo.value = ThemeManager.ThemeInfo.DEFAULT.copy(
                name = extPreviewTheme(System.currentTimeMillis().toString()),
                config = editor.build(),
                stylesheet = stylesheetEditor.build(),
                loadedDir = workspace.extDir,
            )
            onDispose {
                themeManager.previewThemeInfo.value = null
            }
        }

        // TODO: (priority = low)
        //  Drs scrollbar does not like lazy lists with non-constant item heights.
        //  Consider building a custom scrollbar tailored for this list specifically.
        val lazyListState = rememberLazyListState()
        LazyColumn(
            //modifier = Modifier.drsScrollbar(lazyListState, isVertical = true),
            state = lazyListState,
        ) {
            item {
                Column {
                    ExtensionComponentView(
                        modifier = Modifier.defaultDrsOutlinedBox(),
                        meta = workspace.editor!!.meta,
                        component = editor,
                        onEditBtnClick = { showEditComponentMetaDialog = true },
                    )
                    if (stylesheetEditor.rules.isEmpty() ||
                        (stylesheetEditor.rules.size == 1 && stylesheetEditor.rules.all { (rule, _) -> rule == SnyggAnnotationRule.Defines })
                    ) {
                        Text(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                            text = stringRes(R.string.settings__theme_editor__no_rules_defined),
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }

            items(stylesheetEditor.rules.toList()) { (rule, propertySet) -> key(rule) {
                val propertySetSpec = SnyggSpec.propertySetSpecOf(rule)
                val isVariablesRule = rule == SnyggAnnotationRule.Defines
                DrsOutlinedBox(
                    modifier = Modifier
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SnyggRuleRow(
                            rule = rule,
                            level = snyggLevel,
                            showEditBtn = !isVariablesRule,
                            onEditRuleBtnClick = {
                                snyggRuleToEdit = rule
                            },
                            onAddPropertyBtnClick = {
                                when(propertySet) {
                                    is SnyggMultiplePropertySetsEditor -> {
                                        workspace.update {
                                            propertySet.sets.add(SnyggSinglePropertySetEditor())
                                        }
                                    }
                                    is SnyggSinglePropertySetEditor -> {
                                        snyggPropertySetForEditing = propertySet
                                        snyggPropertyToEdit = SnyggEmptyPropertyInfoForAdding.copy(
                                            rule = rule,
                                        )
                                    }
                                }
                            },
                        )
                        if (isVariablesRule) {
                            Text(
                                modifier = Modifier.padding(bottom = 8.dp, start = 16.dp, end = 16.dp),
                                text = stringRes(R.string.snygg__rule_annotation__defines_description),
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                            )
                        }

                        @Composable
                        fun SinglePropertySetEditor(
                            propertySet: SnyggSinglePropertySetEditor,
                        ) {
                            for ((propertyName, propertySpec) in propertySetSpec?.properties.orEmpty()) {
                                if (propertySpec.required && !propertySet.properties.containsKey(propertyName)) {
                                    DrsOutlinedBox(title = "Errors", modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                                        Text(
                                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                                            text = "Required property '$propertyName' does not exist",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                            for ((propertyName, propertyValue) in propertySet.properties) {
                                if (true /*propertySpec != null && propertySpec.level <= snyggLevel*/ || isVariablesRule) {
                                    JetPrefListItem(
                                        modifier = Modifier.rippleClickable {
                                            snyggPropertySetForEditing = propertySet
                                            snyggPropertyToEdit = PropertyInfo(rule, propertyName, propertyValue)
                                        },
                                        text = context.translatePropertyName(propertyName, snyggLevel),
                                        secondaryText = context.translatePropertyValue(propertyValue, snyggLevel, colorRepresentation),
                                        singleLineSecondaryText = true,
                                        trailing = { SnyggValueIcon(propertyValue, definedVariables) },
                                    )
                                }
                            }
                        }

                        when (propertySet) {
                            is SnyggSinglePropertySetEditor -> {
                                SinglePropertySetEditor(propertySet)
                            }
                            is SnyggMultiplePropertySetsEditor -> {
                                val sets = propertySet.sets
                                sets.forEachIndexed { propertySetIndex, propertySet ->
                                    key(propertySet.uuid) {
                                        DrsOutlinedBox(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                                            Row {
                                                Text(
                                                    text = stringRes(
                                                        R.string.ext__editor__source_set,
                                                        "index" to (propertySetIndex + 1),
                                                    ),
                                                    Modifier
                                                        .padding(start = 16.dp)
                                                        .align(Alignment.CenterVertically))
                                                Spacer(Modifier.weight(1f))
                                                DrsIconButton(
                                                    onClick = {
                                                        workspace.update {
                                                            if (propertySetIndex > 0) {
                                                                val set = sets.removeAt(propertySetIndex)
                                                                sets.add(propertySetIndex - 1, set)
                                                            }
                                                        }
                                                    },
                                                    icon = Icons.Default.KeyboardArrowUp,
                                                    iconColor = MaterialTheme.colorScheme.primary,
                                                    iconModifier = Modifier.size(ButtonDefaults.IconSize),
                                                    enabled = propertySetIndex > 0,
                                                    // DRS a11y/i18n/ux (r0-I) FIX 2
                                                    contentDescription = stringRes(R.string.quick_action__arrow_up),
                                                )
                                                DrsIconButton(
                                                    onClick = {
                                                        workspace.update {
                                                            if (propertySetIndex + 1 < sets.size) {
                                                                val set = sets.removeAt(propertySetIndex)
                                                                sets.add(propertySetIndex + 1, set)
                                                            }
                                                        }
                                                    },
                                                    icon = Icons.Default.KeyboardArrowDown,
                                                    iconColor = MaterialTheme.colorScheme.primary,
                                                    iconModifier = Modifier.size(ButtonDefaults.IconSize),
                                                    enabled = propertySetIndex + 1 < sets.size,
                                                    // DRS a11y/i18n/ux (r0-I) FIX 2
                                                    contentDescription = stringRes(R.string.quick_action__arrow_down),
                                                )
                                                DrsIconButton(
                                                    onClick = {
                                                        workspace.update {
                                                            sets.removeAt(propertySetIndex)
                                                        }
                                                    },
                                                    icon = Icons.Default.Delete,
                                                    iconColor = MaterialTheme.colorScheme.primary,
                                                    iconModifier = Modifier.size(ButtonDefaults.IconSize),
                                                    // DRS a11y/i18n/ux (r0-I) FIX 2
                                                    contentDescription = stringRes(R.string.action__delete),
                                                )
                                                DrsIconButton(
                                                    onClick = {
                                                        snyggPropertySetForEditing = propertySet
                                                        snyggPropertyToEdit = SnyggEmptyPropertyInfoForAdding.copy(
                                                            rule = rule,
                                                        )
                                                    },
                                                    icon = Icons.Default.Add,
                                                    iconColor = MaterialTheme.colorScheme.primary,
                                                    iconModifier = Modifier.size(ButtonDefaults.IconSize),
                                                    // DRS a11y/i18n/ux (r0-I) FIX 2
                                                    contentDescription = stringRes(R.string.action__add),
                                                )
                                            }
                                            SinglePropertySetEditor(propertySet)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        if (showEditComponentMetaDialog) {
            ComponentMetaEditorDialog(
                workspace = workspace,
                editor = editor,
                onConfirm = { showEditComponentMetaDialog = false },
                onDismiss = { showEditComponentMetaDialog = false },
            )
        }

        if (showFineTuneDialog) {
            FineTuneDialog(onDismiss = { showFineTuneDialog = false })
        }

        val ruleToEdit = snyggRuleToEdit
        if (ruleToEdit != null) {
            EditRuleDialog(
                initRule = ruleToEdit,
                level = snyggLevel,
                onConfirmRule = { oldRule, newRule ->
                    val rules = stylesheetEditor.rules
                    when {
                        oldRule == newRule -> {
                            snyggRuleToEdit = null
                            true
                        }
                        rules.contains(newRule) -> {
                            false
                        }
                        else -> workspace.update {
                            val set = rules.remove(oldRule)
                            when {
                                set != null -> {
                                    rules[newRule] = set
                                    snyggRuleToEdit = null
                                    scope.launch {
                                        lazyListState.animateScrollToItem(index = rules.keys.indexOf(newRule))
                                    }
                                    true
                                }
                                oldRule == SnyggEmptyRuleForAdding -> {
                                    when (SnyggSpec.propertySetSpecOf(newRule)!!.type) {
                                        SnyggSpecDecl.PropertySet.Type.SINGLE_SET -> {
                                            rules[newRule] = SnyggSinglePropertySetEditor()
                                        }
                                        SnyggSpecDecl.PropertySet.Type.MULTIPLE_SETS -> {
                                            rules[newRule] = SnyggMultiplePropertySetsEditor()
                                        }
                                    }
                                    snyggRuleToEdit = null
                                    scope.launch {
                                        lazyListState.animateScrollToItem(index = rules.keys.indexOf(newRule))
                                    }
                                    true
                                }
                                else -> {
                                    false
                                }
                            }
                        }
                    }
                },
                onDeleteRule = { rule ->
                    workspace.update {
                        stylesheetEditor.rules.remove(rule)
                    }
                    snyggRuleToEdit = null
                },
                onDismiss = { snyggRuleToEdit = null },
            )
        }

        val propertyToEdit = snyggPropertyToEdit
        if (propertyToEdit != null) {
            EditPropertyDialog(
                initProperty = propertyToEdit,
                level = snyggLevel,
                colorRepresentation = colorRepresentation,
                definedVariables = definedVariables,
                fontNames = fontNames,
                workspace = workspace,
                onConfirmNewValue = { name, value ->
                    val properties = snyggPropertySetForEditing?.properties ?: return@EditPropertyDialog false
                    if (propertyToEdit == SnyggEmptyPropertyInfoForAdding && properties.containsKey(name)) {
                        return@EditPropertyDialog false
                    }
                    workspace.update {
                        properties[name] = value
                    }
                    snyggPropertyToEdit = null
                    true
                },
                onDelete = {
                    workspace.update {
                        snyggPropertySetForEditing?.properties?.remove(propertyToEdit.name)
                    }
                    snyggPropertyToEdit = null
                },
                onDismiss = { snyggPropertyToEdit = null },
            )
        }
    }
}

@Composable
private fun ComponentMetaEditorDialog(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    editor: ThemeExtensionComponentEditor,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showValidationErrors by rememberSaveable { mutableStateOf(false) }

    var id by rememberSaveable { mutableStateOf(editor.id) }
    val idValidation = rememberValidationResult(ExtensionValidation.ComponentId, id)
    var label by rememberSaveable { mutableStateOf(editor.label) }
    val labelValidation = rememberValidationResult(ExtensionValidation.ComponentLabel, label)
    var authors by rememberSaveable { mutableStateOf(editor.authors.joinToString("\n")) }
    val authorsValidation = rememberValidationResult(ExtensionValidation.ComponentAuthors, authors)
    var isNightTheme by rememberSaveable { mutableStateOf(editor.isNightTheme) }
    var materialYouFlags by rememberSaveable(stateSaver = MaterialYouFlagsSaver) { mutableStateOf(editor.materialYouFlags) }
    var stylesheetPath by rememberSaveable { mutableStateOf(editor.stylesheetPath) }
    val stylesheetPathValidation = rememberValidationResult(ExtensionValidation.ThemeComponentStylesheetPath, stylesheetPath)

    JetPrefAlertDialog(
        title = stringRes(R.string.ext__editor__metadata__title),
        confirmLabel = stringRes(R.string.action__apply),
        onConfirm = {
            val allFieldsValid = idValidation.isValid() &&
                labelValidation.isValid() &&
                authorsValidation.isValid() &&
                stylesheetPathValidation.isValid()
            if (!allFieldsValid) {
                showValidationErrors = true
            } else if (id != editor.id && (workspace.editor as? ThemeExtensionEditor)?.themes?.find { it.id == id.trim() } != null) {
                // DRS a11y/i18n/ux (r0-I) FIX 2 — literal → localized string.
                context.showLongToastSync(context.stringRes(R.string.theme__error_id_exists))
            } else {
                workspace.update {
                    editor.id = id.trim()
                    editor.label = label.trim()
                    editor.authors = authors.lines().map { it.trim() }.filter { it.isNotBlank() }
                    editor.isNightTheme = isNightTheme
                    editor.stylesheetPath = stylesheetPath.trim()
                    editor.materialYouFlags = materialYouFlags
                }
                onConfirm()
            }
        },
        dismissLabel = stringRes(R.string.action__cancel),
        onDismiss = onDismiss,
        contentPadding = PaddingValues(horizontal = 8.dp),
        scrollModifier = Modifier.drsVerticalScroll(),
    ) {
        Column {
            DialogProperty(text = stringRes(R.string.ext__meta__id)) {
                JetPrefTextField(
                    value = id,
                    onValueChange = { id = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                )
                Validation(showValidationErrors, idValidation)
            }
            DialogProperty(text = stringRes(R.string.ext__meta__label)) {
                JetPrefTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                )
                Validation(showValidationErrors, labelValidation)
            }
            DialogProperty(text = stringRes(R.string.ext__meta__authors)) {
                JetPrefTextField(
                    value = authors,
                    onValueChange = { authors = it },
                )
                Validation(showValidationErrors, authorsValidation)
            }
            JetPrefListItem(
                modifier = Modifier.toggleable(isNightTheme) { isNightTheme = it },
                text = stringRes(R.string.settings__theme_editor__component_meta_is_night_theme),
                trailing = {
                    Switch(checked = isNightTheme, onCheckedChange = null)
                },
                colors = ListItemDefaults.colors(containerColor = AlertDialogDefaults.containerColor)
            )
            DialogProperty(text = stringRes(R.string.settings__theme_editor__component_meta_stylesheet_path)) {
                JetPrefTextField(
                    value = stylesheetPath,
                    onValueChange = { stylesheetPath = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                    placeholderText = if (stylesheetPath.isEmpty()) {
                        ThemeExtensionComponent.defaultStylesheetPath(id.trim())
                    } else {
                        null
                    },
                )
                Validation(showValidationErrors, stylesheetPathValidation)
            }

            DialogProperty(text = stringRes(R.string.settings__theme_editor__component_meta_material_you__title)) {
                JetPrefDropdown(
                    modifier = Modifier.padding(bottom = 8.dp),
                    labelText = stringRes(R.string.settings__theme_editor__component_meta_material_you__palette_style),
                    optionsLabelProvider = {
                        it.name
                    },
                    options = PaletteStyle.entries,
                    onSelectOption = {
                        materialYouFlags = materialYouFlags.copy(paletteStyle = PaletteStyle.entries[it])
                    },
                    selectedOptionIndex = materialYouFlags.paletteStyle.ordinal
                )
                JetPrefDropdown(
                    modifier = Modifier.padding(bottom = 8.dp),
                    labelText = stringRes(R.string.settings__theme_editor__component_meta_material_you__color_contrast),
                    optionsLabelProvider = {
                        it.name
                    },
                    options = Contrast.entries,
                    onSelectOption = {
                        materialYouFlags = materialYouFlags.copy(contrastLevel = Contrast.entries[it])
                    },
                    selectedOptionIndex = materialYouFlags.contrastLevel.ordinal
                )
                JetPrefDropdown(
                    modifier = Modifier.padding(bottom = 8.dp),
                    labelText = stringRes(R.string.settings__theme_editor__component_meta_material_you__spec_version),
                    optionsLabelProvider = {
                        it.name
                    },
                    options = ColorSpec.SpecVersion.entries,
                    onSelectOption = {
                        materialYouFlags =
                            materialYouFlags.copy(specVersion = ColorSpec.SpecVersion.entries[it])
                    },
                    selectedOptionIndex = materialYouFlags.specVersion.ordinal
                )
            }
        }
    }
}

@Composable
private fun SnyggRuleRow(
    rule: SnyggRule,
    level: SnyggLevel,
    showEditBtn: Boolean,
    onEditRuleBtnClick: () -> Unit,
    onAddPropertyBtnClick: () -> Unit,
) {
    val context = LocalContext.current

    @Composable
    fun Selector(text: String) {
        Text(
            modifier = Modifier
                .padding(end = 8.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, shape = Shapes.small),
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    @Composable
    fun AttributesList(text: String, list: String) {
        Text(
            text = "$text = $list",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = 0.56f),
            fontFamily = FontFamily.Monospace,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp, horizontal = 10.dp),
        ) {
            if (rule is SnyggElementRule) {
                Text(
                    text = context.translateElementName(rule, level),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (rule.selector == SnyggSelector.PRESSED) {
                        Selector(
                            text = when (level) {
                                SnyggLevel.DEVELOPER -> SnyggSelector.PRESSED.id
                                else -> stringRes(R.string.snygg__rule_selector__pressed)
                            }
                        )
                    }
                    if (rule.selector == SnyggSelector.FOCUS) {
                        Selector(
                            text = when (level) {
                                SnyggLevel.DEVELOPER -> SnyggSelector.FOCUS.id
                                else -> stringRes(R.string.snygg__rule_selector__focus)
                            }
                        )
                    }
                    if (rule.selector == SnyggSelector.HOVER) {
                        Selector(
                            text = when (level) {
                                SnyggLevel.DEVELOPER -> SnyggSelector.HOVER.id
                                else -> stringRes(R.string.snygg__rule_selector__hover)
                            }
                        )
                    }
                    if (rule.selector == SnyggSelector.DISABLED) {
                        Selector(
                            text = when (level) {
                                SnyggLevel.DEVELOPER -> SnyggSelector.DISABLED.id
                                else -> stringRes(R.string.snygg__rule_selector__disabled)
                            }
                        )
                    }
                }
                for ((attrKey, attrValue) in rule.attributes) {
                    AttributesList(text = attrKey, list = attrValue.toString())
                }
            } else {
                Text(text = rule.toString())
            }
        }
        if (showEditBtn) {
            // DRS a11y/i18n/ux (r0-I) FIX 2 — edit rule button labeled.
            DrsIconButton(
                onClick = onEditRuleBtnClick,
                icon = Icons.Default.Edit,
                iconColor = MaterialTheme.colorScheme.primary,
                iconModifier = Modifier.size(ButtonDefaults.IconSize),
                contentDescription = stringRes(R.string.action__edit),
            )
        }
        // DRS a11y/i18n/ux (r0-I) FIX 2 — add property button labeled.
        DrsIconButton(
            onClick = onAddPropertyBtnClick,
            icon = Icons.Default.Add,
            iconColor = MaterialTheme.colorScheme.secondary,
            iconModifier = Modifier.size(ButtonDefaults.IconSize),
            contentDescription = stringRes(R.string.action__add),
        )
    }
}

@Composable
internal fun DialogProperty(
    text: String,
    modifier: Modifier = Modifier,
    trailingIconTitle: @Composable () -> Unit = { },
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                text = text,
                style = MaterialTheme.typography.titleMedium,
            )
            trailingIconTitle()
        }
        content()
    }
}

private object CustomRuleComparator : Comparator<SnyggRule> {
    @Suppress("IfThenToElvis")
    override fun compare(a: SnyggRule, b: SnyggRule): Int {
        return if (a !is SnyggElementRule || b !is SnyggElementRule || a.elementName == b.elementName) {
            a.compareTo(b)
        } else {
            val aOrdinal = DrsImeUi.elementNamesToOrdinals[a.elementName]
            val bOrdinal = DrsImeUi.elementNamesToOrdinals[b.elementName]
            if (aOrdinal == null && bOrdinal == null) {
                a.elementName.compareTo(b.elementName)
            } else if (bOrdinal == null) {
                -1
            } else if (aOrdinal == null) {
                1
            } else {
                aOrdinal.compareTo(bOrdinal)
            }
        }
    }
}
