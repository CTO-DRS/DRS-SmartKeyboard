/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.theme

import android.icu.lang.UCharacter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pageview
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import com.drs.smartkeyboard.ime.input.InputKeyEventReceiver
import com.drs.smartkeyboard.ime.input.InputShiftState
import com.drs.smartkeyboard.ime.keyboard.ComputingEvaluator
import com.drs.smartkeyboard.ime.keyboard.DefaultComputingEvaluator
import com.drs.smartkeyboard.ime.keyboard.Key
import com.drs.smartkeyboard.ime.keyboard.KeyData
import com.drs.smartkeyboard.ime.keyboard.Keyboard
import com.drs.smartkeyboard.ime.keyboard.KeyboardMode
import com.drs.smartkeyboard.ime.keyboard.computeImageVector
import com.drs.smartkeyboard.ime.keyboard.computeLabel
import com.drs.smartkeyboard.ime.text.key.KeyCode
import com.drs.smartkeyboard.ime.text.keyboard.TextKeyData
import com.drs.smartkeyboard.ime.theme.DrsImeUi
import com.drs.smartkeyboard.ime.window.ImeWindowMode
import com.drs.smartkeyboard.keyboardManager
import com.drs.smartkeyboard.lib.NATIVE_NULLPTR
import com.drs.smartkeyboard.lib.compose.DrsHyperlinkText
import com.drs.smartkeyboard.lib.util.InputMethodUtils
import org.drs.jetpref.material.ui.JetPrefAlertDialog
import org.drs.jetpref.material.ui.JetPrefDropdown
import org.drs.jetpref.material.ui.JetPrefTextField
import org.drs.jetpref.material.ui.JetPrefTextFieldDefaults
import org.drs.lib.android.showShortToastSync
import org.drs.lib.android.stringRes
import org.drs.lib.compose.DrsChip
import org.drs.lib.compose.DrsIconButton
import org.drs.lib.compose.rememberReducedMotion
import org.drs.lib.compose.drsHorizontalScroll
import org.drs.lib.compose.stringRes
import org.drs.lib.kotlin.curlyFormat
import org.drs.lib.snygg.SnyggAnnotationRule
import org.drs.lib.snygg.SnyggAttributes
import org.drs.lib.snygg.SnyggElementRule
import org.drs.lib.snygg.SnyggRule
import org.drs.lib.snygg.SnyggSelector
import org.drs.lib.snygg.ui.NonNullSaver
import kotlin.reflect.KClass

private val TransparentTextSelectionColors = TextSelectionColors(
    handleColor = Color.Transparent,
    backgroundColor = Color.Transparent,
)
internal val SnyggEmptyRuleForAdding = SnyggElementRule(elementName = "--select--")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditRuleDialog(
    initRule: SnyggRule,
    level: SnyggLevel,
    onConfirmRule: (oldRule: SnyggRule, newRule: SnyggRule) -> Boolean,
    onDeleteRule: (rule: SnyggRule) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isAddRuleDialog = initRule == SnyggEmptyRuleForAdding
    var showSelectAsError by rememberSaveable { mutableStateOf(false) }
    var showAlreadyExistsError by rememberSaveable { mutableStateOf(false) }

    val possibleRuleTemplates = remember {
        buildList {
            add(SnyggEmptyRuleForAdding)
            add(SnyggAnnotationRule.Font(fontName = ""))
            DrsImeUi.elementNames.forEach { name ->
                add(SnyggElementRule(name))
            }
        }
    }
    val possibleRuleLabels = possibleRuleTemplates.map { rule ->
        val elementName = when (rule) {
            is SnyggElementRule -> rule.elementName
            else -> rule.decl().name
        }
        context.translateElementName(elementName, level) ?: rule
    }
    var elementsSelectedIndex by rememberSaveable {
        val index = possibleRuleTemplates
            .indexOfFirst {  rule ->
                val elementName = when (rule) {
                    is SnyggElementRule -> rule.elementName
                    else -> rule.decl().name
                }
                val initElementName = when (initRule) {
                    is SnyggElementRule -> initRule.elementName
                    else -> initRule.decl().name
                }
                elementName == initElementName
            }
            .coerceIn(possibleRuleTemplates.indices)
        mutableIntStateOf(index)
    }
    var currentRule by rememberSaveable(elementsSelectedIndex, stateSaver = SnyggRule.NonNullSaver) {
        mutableStateOf(
            if (isAddRuleDialog) possibleRuleTemplates[elementsSelectedIndex] else initRule
        )
    }

    JetPrefAlertDialog(
        title = stringRes(
            if (isAddRuleDialog) {
                R.string.settings__theme_editor__add_rule
            } else {
                R.string.settings__theme_editor__edit_rule
            }
        ),
        confirmLabel = stringRes(
            if (isAddRuleDialog) {
                R.string.action__add
            } else {
                R.string.action__apply
            }
        ),
        onConfirm = {
            if (isAddRuleDialog && elementsSelectedIndex == 0) {
                showSelectAsError = true
            } else {
                if (!onConfirmRule(initRule, currentRule)) {
                    showAlreadyExistsError = true
                }
            }
        },
        dismissLabel = stringRes(R.string.action__cancel),
        onDismiss = onDismiss,
        neutralLabel = if (!isAddRuleDialog) {
            stringRes(R.string.action__delete)
        } else {
            null
        },
        neutralColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        onNeutral = { onDeleteRule(initRule) },
    ) {
        Column {
            AnimatedVisibility(visible = showAlreadyExistsError) {
                Text(
                    modifier = Modifier.padding(bottom = 16.dp),
                    text = stringRes(R.string.settings__theme_editor__rule_already_exists),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            DialogProperty(text = stringRes(R.string.settings__theme_editor__rule_name)) {
                JetPrefDropdown(
                    options = possibleRuleLabels,
                    selectedOptionIndex = elementsSelectedIndex,
                    onSelectOption = { elementsSelectedIndex = it },
                    enabled = isAddRuleDialog,
                    isError = showSelectAsError && elementsSelectedIndex == 0,
                )
            }

            (currentRule as? SnyggAnnotationRule.Font)?.apply {
                DialogProperty(text = stringRes(R.string.snygg__rule_annotation__font_name)) {
                    JetPrefTextField(
                        modifier = Modifier,
                        value = fontName,
                        onValueChange = {
                            currentRule = copy(fontName = it)
                        },
                        singleLine = true,
                    )
                }
            }

            // TODO: Move to toplevel @Composable function
            (currentRule as? SnyggElementRule)?.apply {
                if (elementName == SnyggEmptyRuleForAdding.elementName) {
                    return@apply
                }
                fun updateCurrentRule(newSelector: SnyggSelector) {
                    currentRule = if (selector == newSelector) {
                        copy(selector = SnyggSelector.NONE)
                    } else {
                        copy(selector = newSelector)
                    }
                }
                DialogProperty(text = stringRes(R.string.settings__theme_editor__rule_selectors)) {
                    Row(
                        modifier = Modifier.drsHorizontalScroll(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // TODO: avoid code duplication
                        DrsChip(
                            onClick = { updateCurrentRule(SnyggSelector.PRESSED) },
                            text = when (level) {
                                SnyggLevel.DEVELOPER -> SnyggSelector.PRESSED.id
                                else -> stringRes(R.string.snygg__rule_selector__pressed)
                            },
                            selected = selector == SnyggSelector.PRESSED,
                        )
                        DrsChip(
                            onClick = { updateCurrentRule(SnyggSelector.FOCUS) },
                            text = when (level) {
                                SnyggLevel.DEVELOPER -> SnyggSelector.FOCUS.id
                                else -> stringRes(R.string.snygg__rule_selector__focus)
                            },
                            selected = selector == SnyggSelector.FOCUS,
                        )
                        DrsChip(
                            onClick = { updateCurrentRule(SnyggSelector.HOVER) },
                            text = when (level) {
                                SnyggLevel.DEVELOPER -> SnyggSelector.HOVER.id
                                else -> stringRes(R.string.snygg__rule_selector__hover)
                            },
                            selected = selector == SnyggSelector.HOVER,
                        )
                        DrsChip(
                            onClick = { updateCurrentRule(SnyggSelector.DISABLED) },
                            text = when (level) {
                                SnyggLevel.DEVELOPER -> SnyggSelector.DISABLED.id
                                else -> stringRes(R.string.snygg__rule_selector__disabled)
                            },
                            selected = selector == SnyggSelector.DISABLED,
                        )
                    }
                }

                val codes = remember(currentRule) {
                    attributes[DrsImeUi.Attr.Code] ?: emptyList()
                }
                var editCodeDialogValue by rememberSaveable { mutableStateOf<String?>(null) }
                val initCodeValue = editCodeDialogValue
                if (initCodeValue != null) {
                    EditCodeValueDialog(
                        codeValue = initCodeValue,
                        checkExisting = { codes.contains(it) },
                        onAdd = {
                            currentRule = copy(
                                attributes = attributes.including(DrsImeUi.Attr.Code to it)
                            )
                        },
                        onDelete = {
                            currentRule = copy(
                                attributes = attributes.excluding(DrsImeUi.Attr.Code to it)
                            )
                        },
                        onDismiss = { editCodeDialogValue = null },
                    )
                }
                DialogProperty(
                    text = stringRes(R.string.settings__theme_editor__rule_codes),
                    trailingIconTitle = {
                        // DRS a11y/i18n/ux (r0-I) FIX 3 — add-code button labeled.
                        DrsIconButton(
                            onClick = { editCodeDialogValue = KeyCode.UNSPECIFIED.toString() },
                            modifier = Modifier.offset(x = 12.dp),
                            icon = Icons.Default.Add,
                            contentDescription = stringRes(R.string.action__add),
                        )
                    },
                ) {
                    if (codes.isEmpty()) {
                        Text(
                            text = stringRes(R.string.settings__theme_editor__no_codes_defined),
                            fontStyle = FontStyle.Italic,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (code in codes) {
                            DrsChip(
                                onClick = { editCodeDialogValue = code },
                                text = code,
                                selected = editCodeDialogValue == code,
                                shape = MaterialTheme.shapes.medium,
                            )
                        }
                    }
                }

                EnumLikeAttributeBox(
                    text = stringRes(R.string.settings__theme_editor__rule_modes),
                    enumClass = KeyboardMode::class,
                    attribute = DrsImeUi.Attr.Mode,
                    attributes = attributes,
                    setAttributes = { currentRule = copy(attributes = it) },
                    level = level,
                )

                EnumLikeAttributeBox(
                    text = stringRes(R.string.settings__theme_editor__rule_shift_states),
                    enumClass = InputShiftState::class,
                    attribute = DrsImeUi.Attr.ShiftState,
                    attributes = attributes,
                    setAttributes = { currentRule = copy(attributes = it) },
                    level = level,
                )

                EnumLikeAttributeBox(
                    text = stringRes(R.string.settings__theme_editor__rule_window_mode),
                    enumClass = ImeWindowMode::class,
                    attribute = DrsImeUi.Attr.WindowMode,
                    attributes = attributes,
                    setAttributes = { currentRule = copy(attributes = it) },
                    level = level,
                )

                // DRS v1.27.0: the listening-state attribute — a single
                // boolean chip, because the plain "smartbar" rule already
                // covers every non-listening moment and a "false" value
                // would be indistinguishable from no attribute at all.
                BooleanAttributeBox(
                    text = stringRes(R.string.settings__theme_editor__rule_voice),
                    attribute = DrsImeUi.Attr.Voice,
                    value = true,
                    label = stringRes(R.string.settings__theme_editor__rule_voice_value),
                    attributes = attributes,
                    setAttributes = { currentRule = copy(attributes = it) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BooleanAttributeBox(
    text: String,
    attribute: String,
    value: Any,
    label: String,
    attributes: SnyggAttributes,
    setAttributes: (SnyggAttributes) -> Unit,
) {
    val included = remember(attributes, attribute, value) {
        attributes[attribute]?.contains(value.toString()) == true
    }
    DialogProperty(
        text = text,
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrsChip(
                onClick = {
                    setAttributes(
                        if (included) {
                            attributes.excluding(attribute to value)
                        } else {
                            attributes.including(attribute to value)
                        },
                    )
                },
                text = label,
                selected = included,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCodeValueDialog(
    codeValue: String,
    checkExisting: (String) -> Boolean,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    var inputCodeString by rememberSaveable(codeValue) {
        val str = if (codeValue == KeyCode.UNSPECIFIED.toString()) "" else codeValue
        mutableStateOf(str)
    }
    val textKeyData = remember(inputCodeString) {
        inputCodeString.toIntOrNull()?.let { code ->
            TextKeyData.getCodeInfoAsTextKeyData(code)
        }
    }
    var showKeyCodesHelp by rememberSaveable(codeValue) { mutableStateOf(false) }
    var showError by rememberSaveable(codeValue) { mutableStateOf(false) }
    var errorId by rememberSaveable(codeValue) { mutableIntStateOf(NATIVE_NULLPTR.toInt()) }

    val focusRequester = remember { FocusRequester() }
    val isDrsKeyboardEnabled by InputMethodUtils.observeIsDrsKeyboardEnabled(foregroundOnly = true)
    val isDrsKeyboardSelected by InputMethodUtils.observeIsDrsKeyboardSelected(foregroundOnly = true)

    var isRecordingKey by remember { mutableStateOf(false) }
    var lastRecordingToast by remember { mutableStateOf<Toast?>(null) }
    // DRS a11y/i18n/ux (r0-I) FIX 3 — the recording blink is gated on reduced motion:
    // when the system animator scale is 0 the infinite transition is NEVER created
    // (rememberInfiniteTransition is only called in the animated branch); a static
    // error color marks the recording state instead.
    val reducedMotion = rememberReducedMotion()
    val recordingKeyColor = if (isRecordingKey) {
        if (reducedMotion) {
            MaterialTheme.colorScheme.error
        } else {
            rememberInfiniteTransition().animateColor(
                initialValue = LocalContentColor.current,
                targetValue = MaterialTheme.colorScheme.error,
                animationSpec = infiniteRepeatable(
                    tween(750),
                    repeatMode = RepeatMode.Reverse,
                ),
            ).value
        }
    } else {
        LocalContentColor.current
    }

    fun requestStartRecording() {
        if (isRecordingKey) {
            isRecordingKey = false
            return
        }
        if (!isDrsKeyboardEnabled || !isDrsKeyboardSelected) {
            lastRecordingToast?.cancel()
            lastRecordingToast = context.showShortToastSync(
                R.string.settings__theme_editor__code_recording_requires_default_ime_drs,
                "app_name" to context.stringRes(R.string.drs_app_name),
            )
            InputMethodUtils.showImePicker(context)
            return
        }
        showError = false
        isRecordingKey = true
    }

    if (isRecordingKey) {
        DisposableEffect(Unit) {
            val receiver = object : InputKeyEventReceiver {
                override fun onInputKeyDown(data: KeyData) = Unit
                override fun onInputKeyUp(data: KeyData) {
                    inputCodeString = data.code.toString()
                    isRecordingKey = false
                }

                override fun onInputKeyRepeat(data: KeyData) = Unit
                override fun onInputKeyCancel(data: KeyData) = Unit
            }
            val defaultReceiver = keyboardManager.inputEventDispatcher.keyEventReceiver
            keyboardManager.inputEventDispatcher.keyEventReceiver = receiver
            lastRecordingToast?.cancel()
            lastRecordingToast = context.showShortToastSync(R.string.settings__theme_editor__code_recording_started)
            focusRequester.requestFocus()
            onDispose {
                keyboardManager.inputEventDispatcher.keyEventReceiver = defaultReceiver
                lastRecordingToast?.cancel()
                lastRecordingToast = context.showShortToastSync(R.string.settings__theme_editor__code_recording_stopped)
            }
        }
    }

    JetPrefAlertDialog(
        title = stringRes(
            if (codeValue == KeyCode.UNSPECIFIED.toString()) {
                R.string.settings__theme_editor__add_code
            } else {
                R.string.settings__theme_editor__edit_code
            }
        ),
        confirmLabel = stringRes(
            if (codeValue == KeyCode.UNSPECIFIED.toString()) {
                R.string.action__add
            } else {
                R.string.action__apply
            }
        ),
        onConfirm = {
            val code = inputCodeString.trim().toIntOrNull(radix = 10)
            when {
                code == null || (code !in KeyCode.Spec.CHARACTERS && code !in KeyCode.Spec.INTERNAL) -> {
                    errorId = R.string.settings__theme_editor__code_invalid
                    showError = true
                }

                code.toString() == codeValue -> {
                    onDismiss()
                }

                checkExisting(code.toString()) -> {
                    errorId = R.string.settings__theme_editor__code_already_exists
                    showError = true
                }

                else -> {
                    if (codeValue != KeyCode.UNSPECIFIED.toString()) {
                        onDelete(codeValue)
                    }
                    onAdd(code.toString())
                    onDismiss()
                }
            }
        },
        dismissLabel = stringRes(R.string.action__cancel),
        onDismiss = onDismiss,
        neutralLabel = if (codeValue != KeyCode.UNSPECIFIED.toString()) {
            stringRes(R.string.action__delete)
        } else {
            null
        },
        neutralColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        onNeutral = {
            onDelete(codeValue)
            onDismiss()
        },
        trailingIconTitle = {
            // DRS a11y/i18n/ux (r0-I) FIX 3 — help toggle labeled.
            DrsIconButton(
                onClick = { showKeyCodesHelp = !showKeyCodesHelp },
                modifier = Modifier.offset(x = 12.dp),
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = stringRes(R.string.action__help),
            )
        },
    ) {
        Column {
            AnimatedVisibility(visible = showKeyCodesHelp) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(text = stringRes(R.string.settings__theme_editor__code_recording_help_text))
                    Text(text = stringRes(R.string.settings__theme_editor__code_help_text))
                    // DRS a11y/i18n/ux (r0-I) FIX 3 — hardcoded link labels → strings
                    // (the URL resource keys themselves are untouched).
                    DrsHyperlinkText(
                        text = stringRes(R.string.codes_link_unicode_table),
                        url = stringRes(R.string.drs__character_key_codes_url),
                    )
                    DrsHyperlinkText(
                        text = stringRes(R.string.codes_link_github),
                        url = stringRes(R.string.drs__internal_key_codes_url),
                    )
                }
            }
            TextKeyDataPreviewBox(
                modifier = Modifier.padding(bottom = 8.dp),
                textKeyData = textKeyData,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val textSelectionColors = if (isRecordingKey) {
                    TransparentTextSelectionColors
                } else {
                    LocalTextSelectionColors.current
                }
                CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
                    JetPrefTextField(
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .weight(1f),
                        value = inputCodeString,
                        onValueChange = { v ->
                            inputCodeString = v
                            showError = false
                        },
                        placeholderText = when {
                            isRecordingKey -> {
                                stringRes(R.string.settings__theme_editor__code_recording_placeholder)
                            }
                            inputCodeString.isEmpty() -> {
                                stringRes(R.string.settings__theme_editor__code_placeholder)
                            }
                            else -> {
                                null
                            }
                        },
                        isError = showError,
                        singleLine = true,
                        appearance = JetPrefTextFieldDefaults.filled(
                            colors = if (isRecordingKey) {
                                TextFieldDefaults.colors(
                                    focusedTextColor = Color.Transparent,
                                    cursorColor = Color.Transparent,
                                )
                            } else {
                                TextFieldDefaults.colors()
                            }
                        ),
                        trailingIcon = {
                            // DRS a11y/i18n/ux (r0-I) FIX 3 — record-key button labeled.
                            DrsIconButton(
                                onClick = { requestStartRecording() },
                                icon = Icons.Default.Pageview,
                                iconColor = recordingKeyColor,
                                contentDescription = stringRes(R.string.code_record),
                            )
                        }
                    )
                }
            }
            AnimatedVisibility(visible = showError) {
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = stringRes(errorId).curlyFormat(
                        "c_min" to KeyCode.Spec.CHARACTERS_MIN,
                        "c_max" to KeyCode.Spec.CHARACTERS_MAX,
                        "i_min" to KeyCode.Spec.INTERNAL_MIN,
                        "i_max" to KeyCode.Spec.INTERNAL_MAX,
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TextKeyDataPreviewBox(
    textKeyData: TextKeyData?,
    modifier: Modifier = Modifier,
) {
    val data = textKeyData ?: TextKeyData.UNSPECIFIED

    val context = LocalContext.current
    val evaluator = remember(context) {
        object : ComputingEvaluator by DefaultComputingEvaluator {
            override val keyboard = object : Keyboard() {
                override val mode = KeyboardMode.NUMERIC_ADVANCED
                override fun getKeyForPos(pointerX: Float, pointerY: Float) = error("not implemented")
                override fun keys() = error("not implemented")
                override fun layout(
                    keyboardWidth: Float, keyboardHeight: Float, desiredKey: Key,
                    extendTouchBoundariesDownwards: Boolean, splitGapWidth: Float,
                ) = error("not implemented")
            }

            override fun context() = context
        }
    }

    val label = remember(data) { evaluator.computeLabel(data) }
    val icon = remember(data) { evaluator.computeImageVector(data) }
    val displayName = remember(data) {
        if (data.code > 0) {
            UCharacter.getName(data.code) ?: UCharacter.getExtendedName(data.code)
        } else {
            data.label
        }
    }

    Row(modifier = modifier) {
        Box(
            modifier = Modifier
                .padding(end = 16.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.medium,
                )
                // DRS a11y/i18n/ux (r0-I) FIX 3 — heightIn(min) so large font scales
                // grow the chip instead of clipping the glyph.
                .heightIn(min = 36.dp)
                .widthIn(min = 36.dp)
                .align(Alignment.CenterVertically),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(
                    modifier = Modifier.requiredSize(24.dp),
                    imageVector = icon,
                    contentDescription = null,
                )
            } else if (label != null) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = displayName)
            Text(text = data.type.toString())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <V : Any> EnumLikeAttributeBox(
    text: String,
    enumClass: KClass<V>,
    attribute: String,
    attributes: SnyggAttributes,
    setAttributes: (SnyggAttributes) -> Unit,
    level: SnyggLevel,
) {
    val allEntries = enumDisplayEntriesOf(enumClass)
    val (alreadyAddedEntries, notYetAddedEntries) = remember(attributes, attribute) {
        allEntries.partition { entry ->
            attributes[attribute]?.contains(entry.key.toString()) == true
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }

    DialogProperty(
        text = text,
        trailingIconTitle = {
            // DRS a11y/i18n/ux (r0-I) FIX 3 — add-attribute button labeled.
            DrsIconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.offset(x = 12.dp),
                icon = Icons.Default.Add,
                contentDescription = stringRes(R.string.action__add),
            )
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (entry in alreadyAddedEntries) {
                DrsChip(
                    onClick = {
                        setAttributes(attributes.excluding(attribute to entry.key.toString()))
                    },
                    text = entry.label,
                )
            }
            if (alreadyAddedEntries.isEmpty()) {
                Text(
                    text = stringRes(R.string.settings__theme_editor__no_codes_defined),
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }

    if (showAddDialog) {
        JetPrefAlertDialog(
            title = stringRes(R.string.action__add),
            dismissLabel = stringRes(R.string.action__cancel),
            onDismiss = { showAddDialog = false },
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (entry in notYetAddedEntries) {
                    DrsChip(
                        onClick = {
                            setAttributes(attributes.including(attribute to entry.key.toString()))
                            showAddDialog = false
                        },
                        text = when (level) {
                            SnyggLevel.DEVELOPER -> entry.key.toString()
                            else -> entry.label
                        },
                    )
                }
            }
            if (notYetAddedEntries.isEmpty()) {
                Text(
                    text = stringRes(R.string.settings__theme_editor__no_enum_value_to_add_anymore),
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}
