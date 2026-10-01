/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.drs.DrsProfile
import com.drs.smartkeyboard.drs.DrsProfileManager
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.ime.theme.ThemeManager
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import com.drs.smartkeyboard.themeManager
import org.drs.lib.compose.stringRes

/**
 * Editable snapshot of one profile inside the edit dialog. Kept separate
 * from [DrsProfile] so the dialog can be cancelled without touching the
 * persisted state, and so theme ids can be edited alongside the toggles.
 */
private data class DrsProfileEditorState(
    val id: String,
    val name: String,
    val dayThemeId: String,
    val nightThemeId: String,
    val numberRow: Boolean,
    val techStripEnabled: Boolean,
    val suggestionsEnabled: Boolean,
    val clipboardHistoryEnabled: Boolean,
    val audioFeedbackEnabled: Boolean,
    val hapticFeedbackEnabled: Boolean,
)

private fun DrsProfile.toEditorState(): DrsProfileEditorState = DrsProfileEditorState(
    id = id,
    name = name,
    dayThemeId = dayThemeId,
    nightThemeId = nightThemeId,
    numberRow = numberRow,
    techStripEnabled = techStripEnabled,
    suggestionsEnabled = suggestionsEnabled,
    clipboardHistoryEnabled = clipboardHistoryEnabled,
    audioFeedbackEnabled = audioFeedbackEnabled,
    hapticFeedbackEnabled = hapticFeedbackEnabled,
)

private fun DrsProfileEditorState.toProfile(path: String): DrsProfile = DrsProfile(
    id = id,
    name = name.ifBlank { "DRS" },
    path = path,
    dayThemeId = dayThemeId,
    nightThemeId = nightThemeId,
    numberRow = numberRow,
    techStripEnabled = techStripEnabled,
    suggestionsEnabled = suggestionsEnabled,
    clipboardHistoryEnabled = clipboardHistoryEnabled,
    audioFeedbackEnabled = audioFeedbackEnabled,
    hapticFeedbackEnabled = hapticFeedbackEnabled,
)

/**
 * DRS Profiles: full profile management screen. Users can switch profiles
 * with one tap, clone the active profile, edit name/themes/toggles and
 * delete profiles. Deleting is guarded so at least one profile always
 * remains active, and the first remaining profile is re-applied after a
 * deletion - no user data is ever lost.
 */
@Composable
fun DrsProfilesScreen() = DrsScreen {
    title = stringRes(R.string.drs__profiles__title)
    navigationIconVisible = true
    previewFieldVisible = true
    // The content below provides its own verticalScroll Column. DrsScreen
    // wraps the content in drsVerticalScroll() by default, so leaving
    // scrollable = true here would nest two vertical scroll containers and
    // crash with "Vertically scrollable component was measured with an
    // infinity maximum height constraints" (IllegalStateException) as soon
    // as the screen is opened. All upstream screens with self-scrolling
    // content follow the same scrollable = false convention.
    scrollable = false

    val context = LocalContext.current
    val themeManager by context.themeManager()

    val drsState by DrsStore.state.collectAsState()
    val activeProfile = DrsProfileManager.activeProfile(drsState)

    var editingProfile by remember { mutableStateOf<DrsProfileEditorState?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteCandidateId by remember { mutableStateOf<String?>(null) }
    var guardMessage by remember { mutableStateOf(false) }

    content {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---------------- summary + clone ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringRes(
                            R.string.drs__profiles__count,
                            "count" to drsState.profiles.size.toString(),
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringRes(R.string.drs__profiles__hint),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showCreateDialog = true }, enabled = activeProfile != null) {
                            Text(stringRes(R.string.drs__profiles__clone))
                        }
                    }
                }
            }

            if (guardMessage) {
                Text(
                    text = stringRes(R.string.drs__profiles__last_guard),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // ---------------- profile list ----------------
            if (drsState.profiles.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringRes(R.string.drs__profiles__empty),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            drsState.profiles.forEach { profile ->
                val isActive = profile.id == drsState.activeProfileId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isActive) 2.dp else 1.dp,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = RoundedCornerShape(12.dp),
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile.name,
                                modifier = Modifier.weight(1f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            // System identity badge: which of the three
                            // systems this profile belongs to.
                            DrsSystems.specOfProfile(profile)?.let { spec ->
                                val accent = if (isSystemInDarkTheme()) spec.accentNight else spec.accent
                                Text(
                                    text = stringRes(pathTitleRes(spec)),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                    modifier = Modifier
                                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            if (isActive) {
                                Text(
                                    text = stringRes(R.string.drs__profiles__active_badge),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            text = stringRes(
                                R.string.drs__profiles__card_summary,
                                "day" to themeLabel(themeManager, profile.dayThemeId),
                                "night" to themeLabel(themeManager, profile.nightThemeId),
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!isActive) {
                                TextButton(onClick = { DrsProfileManager.applyProfile(profile) }) {
                                    Text(stringRes(R.string.drs__profiles__activate))
                                }
                            }
                            TextButton(onClick = { editingProfile = profile.toEditorState() }) {
                                Text(stringRes(R.string.drs__profiles__edit))
                            }
                            TextButton(onClick = { deleteCandidateId = profile.id }) {
                                Text(stringRes(R.string.drs__profiles__delete))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // ---------------- create (clone) dialog ----------------
        if (showCreateDialog) {
            DrsCloneProfileDialog(
                baseName = activeProfile?.name ?: "DRS",
                onCreate = { name ->
                    showCreateDialog = false
                    val base = activeProfile
                    if (base != null) {
                        DrsProfileManager.createAndApply(DrsProfileManager.customProfileFrom(base, name))
                    }
                },
                onDismiss = { showCreateDialog = false },
            )
        }

        // ---------------- edit dialog ----------------
        editingProfile?.let { editor ->
            DrsEditProfileDialog(
                initial = editor,
                themeManager = themeManager,
                onSave = { saved ->
                    editingProfile = null
                    val path = drsState.profiles.firstOrNull { it.id == saved.id }?.path ?: "CUSTOM"
                    DrsProfileManager.applyProfile(saved.toProfile(path))
                },
                onDismiss = { editingProfile = null },
            )
        }

        // ---------------- delete confirm dialog ----------------
        deleteCandidateId?.let { candidateId ->
            AlertDialog(
                onDismissRequest = { deleteCandidateId = null },
                title = { Text(stringRes(R.string.drs__profiles__delete)) },
                text = { Text(stringRes(R.string.drs__profiles__delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        deleteCandidateId = null
                        if (drsState.profiles.size <= 1) {
                            guardMessage = true
                        } else {
                            guardMessage = false
                            val remaining = drsState.profiles.filter { it.id != candidateId }
                            DrsStore.update { state ->
                                state.copy(
                                    profiles = remaining,
                                    activeProfileId = if (state.activeProfileId == candidateId) {
                                        remaining.firstOrNull()?.id ?: ""
                                    } else {
                                        state.activeProfileId
                                    },
                                )
                            }
                            // Re-apply the first remaining profile so live
                            // preferences always match an existing profile.
                            remaining.firstOrNull()?.let { DrsProfileManager.applyProfile(it) }
                        }
                    }) {
                        Text(stringRes(R.string.action__ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteCandidateId = null }) {
                        Text(stringRes(R.string.action__cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun themeLabel(themeManager: ThemeManager, themeId: String): String {
    val name = remember(themeId) {
        runCatching { ExtensionComponentName.from(themeId) }.getOrNull()
    } ?: return stringRes(R.string.drs__profiles__theme_none)
    val configs by themeManager.indexedThemeConfigs.collectAsState()
    return configs.first[name]?.label ?: name.toString()
}

@Composable
private fun DrsCloneProfileDialog(baseName: String, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.drs__profiles__clone)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringRes(
                        R.string.drs__profiles__clone_hint,
                        "base" to baseName,
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringRes(R.string.action__ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.action__cancel))
            }
        },
    )
}

@Composable
private fun DrsEditProfileDialog(
    initial: DrsProfileEditorState,
    themeManager: ThemeManager,
    onSave: (DrsProfileEditorState) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var dayThemeId by remember { mutableStateOf(initial.dayThemeId) }
    var nightThemeId by remember { mutableStateOf(initial.nightThemeId) }
    var numberRow by remember { mutableStateOf(initial.numberRow) }
    var techStripEnabled by remember { mutableStateOf(initial.techStripEnabled) }
    var suggestionsEnabled by remember { mutableStateOf(initial.suggestionsEnabled) }
    var clipboardHistoryEnabled by remember { mutableStateOf(initial.clipboardHistoryEnabled) }
    var audioFeedbackEnabled by remember { mutableStateOf(initial.audioFeedbackEnabled) }
    var hapticFeedbackEnabled by remember { mutableStateOf(initial.hapticFeedbackEnabled) }
    var pickingDay by remember { mutableStateOf(false) }
    var pickingNight by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.drs__profiles__edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringRes(R.string.drs__profiles__name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ThemePickRow(
                    label = stringRes(R.string.drs__profiles__day_theme),
                    value = themeLabel(themeManager, dayThemeId),
                    onClick = { pickingDay = true },
                )
                ThemePickRow(
                    label = stringRes(R.string.drs__profiles__night_theme),
                    value = themeLabel(themeManager, nightThemeId),
                    onClick = { pickingNight = true },
                )
                EditorToggle(
                    label = stringRes(R.string.drs__profiles__toggle_number_row),
                    checked = numberRow,
                    onChange = { numberRow = it },
                )
                EditorToggle(
                    label = stringRes(R.string.drs__profiles__toggle_tech_strip),
                    checked = techStripEnabled,
                    onChange = { techStripEnabled = it },
                )
                EditorToggle(
                    label = stringRes(R.string.drs__profiles__toggle_suggestions),
                    checked = suggestionsEnabled,
                    onChange = { suggestionsEnabled = it },
                )
                EditorToggle(
                    label = stringRes(R.string.drs__profiles__toggle_clipboard),
                    checked = clipboardHistoryEnabled,
                    onChange = { clipboardHistoryEnabled = it },
                )
                EditorToggle(
                    label = stringRes(R.string.drs__profiles__toggle_audio),
                    checked = audioFeedbackEnabled,
                    onChange = { audioFeedbackEnabled = it },
                )
                EditorToggle(
                    label = stringRes(R.string.drs__profiles__toggle_haptic),
                    checked = hapticFeedbackEnabled,
                    onChange = { hapticFeedbackEnabled = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        name = name,
                        dayThemeId = dayThemeId,
                        nightThemeId = nightThemeId,
                        numberRow = numberRow,
                        techStripEnabled = techStripEnabled,
                        suggestionsEnabled = suggestionsEnabled,
                        clipboardHistoryEnabled = clipboardHistoryEnabled,
                        audioFeedbackEnabled = audioFeedbackEnabled,
                        hapticFeedbackEnabled = hapticFeedbackEnabled,
                    ),
                )
            }) {
                Text(stringRes(R.string.drs__profiles__save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.action__cancel))
            }
        },
    )

    if (pickingDay) {
        DrsThemePickDialog(
            themeManager = themeManager,
            onlyNight = false,
            current = dayThemeId,
            onSelect = {
                dayThemeId = it
                pickingDay = false
            },
            onDismiss = { pickingDay = false },
        )
    }
    if (pickingNight) {
        DrsThemePickDialog(
            themeManager = themeManager,
            onlyNight = true,
            current = nightThemeId,
            onSelect = {
                nightThemeId = it
                pickingNight = false
            },
            onDismiss = { pickingNight = false },
        )
    }
}

@Composable
private fun ThemePickRow(label: String, value: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
            )
            Text(
                text = value,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EditorToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DrsThemePickDialog(
    themeManager: ThemeManager,
    onlyNight: Boolean,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val configs by themeManager.indexedThemeConfigs.collectAsState()
    val candidates = remember(configs, onlyNight) {
        configs.first.entries
            .filter { (_, component) -> component.isNightTheme == onlyNight }
            .map { (name, component) -> name.toString() to component.label }
            .sortedBy { it.second.lowercase() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringRes(
                    if (onlyNight) R.string.drs__profiles__night_theme else R.string.drs__profiles__day_theme,
                ),
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (candidates.isEmpty()) {
                    Text(stringRes(R.string.drs__profiles__theme_none), fontSize = 13.sp)
                }
                candidates.forEach { (id, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = id == current,
                            onClick = { onSelect(id) },
                        )
                        TextButton(onClick = { onSelect(id) }) {
                            Text(label, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.action__cancel))
            }
        },
    )
}
