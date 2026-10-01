/*
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.app.ext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Outbound
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.drs.DrsProfileManager
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.DrsUserPath
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.jetpref.datastore.ui.PreferenceGroup
import org.drs.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.drs.lib.android.showLongToastSync
import org.drs.lib.compose.stringRes
import androidx.compose.ui.platform.LocalContext

/**
 * DRS Smart Accessories (الملحقات الذكية).
 *
 * Every accessory here is a real, working keyboard module backed by an
 * actual preference - switching a row changes the live keyboard behavior
 * immediately. Each of the three DRS systems owns a complete accessory
 * package (see [com.drs.smartkeyboard.drs.DrsAccessoryPreset]) that can be
 * applied in a single tap, so switching between the normal, technical and
 * hybrid systems always yields a coherent accessory configuration.
 */
@Composable
fun AccessoriesScreen() = DrsScreen {
    title = stringRes(R.string.accessories__title)
    previewFieldVisible = false
    iconSpaceReserved = true

    val context = LocalContext.current
    val prefs by DrsPreferenceStore

    val drsState by DrsStore.state.collectAsState()
    val spec = DrsSystems.specOfName(drsState.userPath)
    val accent = if (isSystemInDarkTheme()) spec.accentNight else spec.accent

    content {
        // ---------------- system accessory package hero ----------------
        AccessorySystemCard(
            systemName = stringRes(pathTitleResOf(spec.path)),
            hint = stringRes(R.string.accessories__system_card_hint),
            accent = accent,
            onApply = {
                applySystemAccessoryPackage(prefs, spec.path)
                context.showLongToastSync(R.string.accessories__preset_applied)
            },
        )

        Text(
            text = stringRes(R.string.accessories__hint),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // ---------------- productivity accessories ----------------
        PreferenceGroup(title = stringRes(R.string.accessories__section_productivity)) {
            SwitchPreference(
                prefs.smartbar.enabled,
                icon = Icons.Default.SmartButton,
                title = stringRes(R.string.pref__smartbar__enabled__label),
                summary = stringRes(R.string.accessories__smartbar_summary),
            )
            SwitchPreference(
                prefs.clipboard.historyEnabled,
                icon = Icons.Default.ContentPaste,
                title = stringRes(R.string.pref__clipboard__enable_clipboard_history__label),
                summary = stringRes(R.string.accessories__clipboard_summary),
            )
            SwitchPreference(
                prefs.clipboard.suggestionEnabled,
                icon = Icons.Default.ContentPaste,
                title = stringRes(R.string.pref__clipboard__suggestion_enabled__label),
                summary = stringRes(R.string.accessories__clipboard_suggestion_summary),
            )
            SwitchPreference(
                prefs.suggestion.enabled,
                icon = Icons.Default.Spellcheck,
                title = stringRes(R.string.pref__suggestion__enabled__label),
                summary = stringRes(R.string.accessories__suggestions_summary),
            )
            SwitchPreference(
                prefs.suggestion.nextWordEnabled,
                icon = Icons.Default.SwapHoriz,
                title = stringRes(R.string.pref__suggestion__next_word_enabled__label),
                summary = stringRes(R.string.accessories__next_word_summary),
            )
        }

        // ---------------- expression accessories ----------------
        PreferenceGroup(title = stringRes(R.string.accessories__section_expression)) {
            SwitchPreference(
                prefs.emoji.suggestionEnabled,
                icon = Icons.Default.SentimentSatisfiedAlt,
                title = stringRes(R.string.prefs__media__emoji_suggestion_enabled),
                summary = stringRes(R.string.accessories__emoji_suggestion_summary),
            )
            SwitchPreference(
                prefs.emoji.historyEnabled,
                icon = Icons.Outlined.Schedule,
                title = stringRes(R.string.prefs__media__emoji_history_enabled),
                summary = stringRes(R.string.accessories__emoji_history_summary),
            )
        }

        // ---------------- typing accessories ----------------
        PreferenceGroup(title = stringRes(R.string.accessories__section_typing)) {
            SwitchPreference(
                prefs.keyboard.numberRow,
                icon = Icons.Default.Numbers,
                title = stringRes(R.string.pref__keyboard__number_row__label),
                summary = stringRes(R.string.accessories__number_row_summary),
            )
            SwitchPreference(
                prefs.glide.enabled,
                icon = Icons.Default.Gesture,
                title = stringRes(R.string.pref__glide__enabled__label),
                summary = stringRes(R.string.accessories__glide_summary),
            )
            SwitchPreference(
                prefs.glide.showTrail,
                icon = Icons.Default.Outbound,
                title = stringRes(R.string.pref__glide__show_trail__label),
                summary = stringRes(R.string.accessories__glide_trail_summary),
            )
            SwitchPreference(
                prefs.keyboard.popupEnabled,
                icon = Icons.Default.AutoAwesome,
                title = stringRes(R.string.pref__keyboard__popup_enabled__label),
                summary = stringRes(R.string.accessories__popups_summary),
            )
        }

        // ---------------- feel & feedback accessories ----------------
        PreferenceGroup(title = stringRes(R.string.accessories__section_feedback)) {
            SwitchPreference(
                prefs.inputFeedback.audioEnabled,
                icon = Icons.Default.MusicNote,
                title = stringRes(R.string.pref__input_feedback__audio_enabled__label),
                summary = stringRes(R.string.accessories__audio_summary),
            )
            SwitchPreference(
                prefs.inputFeedback.hapticEnabled,
                icon = Icons.Default.Vibration,
                title = stringRes(R.string.pref__input_feedback__haptic_enabled__label),
                summary = stringRes(R.string.accessories__haptic_summary),
            )
        }
    }
}

@Composable
private fun pathTitleResOf(path: DrsUserPath): Int = when (path) {
    DrsUserPath.TECHNICAL -> R.string.drs__path_technical_title
    DrsUserPath.HYBRID -> R.string.drs__path_hybrid_title
    DrsUserPath.NORMAL -> R.string.drs__path_normal_title
}

/**
 * Applies the complete accessory package owned by [path]'s system to the
 * live preferences in one step, and keeps the active system profile's tech
 * strip in sync with the system specification.
 */
private val accessoryPresetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun applySystemAccessoryPackage(prefs: com.drs.smartkeyboard.app.DrsPreferenceModel, path: DrsUserPath) {
    val spec = DrsSystems.specOf(path)
    val preset = spec.accessoryPreset
    accessoryPresetScope.launch {
        prefs.smartbar.enabled.set(preset.smartbarEnabled)
        prefs.glide.enabled.set(preset.glideEnabled)
        prefs.glide.showTrail.set(preset.glideTrail)
        prefs.suggestion.enabled.set(spec.suggestionsEnabled)
        prefs.suggestion.nextWordEnabled.set(preset.nextWordEnabled)
        prefs.emoji.suggestionEnabled.set(preset.emojiSuggestions)
        prefs.emoji.historyEnabled.set(preset.emojiHistory)
        prefs.keyboard.numberRow.set(spec.numberRow)
        prefs.keyboard.popupEnabled.set(preset.keyPopups)
        prefs.clipboard.historyEnabled.set(spec.clipboardHistoryEnabled)
        prefs.inputFeedback.audioEnabled.set(spec.audioFeedbackEnabled)
        prefs.inputFeedback.hapticEnabled.set(spec.hapticFeedbackEnabled)
    }
    val state = DrsStore.state.value
    DrsProfileManager.activeProfile(state)?.let { profile ->
        if (profile.path == spec.path.name) {
            DrsProfileManager.applyProfile(profile.copy(techStripEnabled = spec.techStripEnabled))
        }
    }
}

/**
 * Hero card for the system-owned accessory package: carries the system's
 * identity accent and a one-tap apply action.
 */
@Composable
private fun AccessorySystemCard(
    systemName: String,
    hint: String,
    accent: androidx.compose.ui.graphics.Color,
    onApply: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.05f)),
                ),
                RoundedCornerShape(18.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(accent, CircleShape),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringRes(R.string.accessories__system_card_title),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Text(
            text = systemName,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = hint,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            androidx.compose.material3.OutlinedButton(onClick = onApply) {
                Text(
                    text = stringRes(R.string.accessories__apply_preset),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
