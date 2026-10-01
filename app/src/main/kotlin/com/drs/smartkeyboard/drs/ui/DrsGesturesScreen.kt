/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import com.drs.smartkeyboard.drs.DrsAdaptationEngine
import com.drs.smartkeyboard.ime.text.gestures.SwipeAction
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.jetpref.datastore.ui.DialogSliderPreference
import org.drs.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import org.drs.jetpref.datastore.ui.ListPreference
import org.drs.jetpref.datastore.ui.PreferenceGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.drs.lib.compose.DrsInfoCard
import org.drs.lib.compose.stringRes

/**
 * Scope used to write gesture presets into the jetpref datastore. All pref
 * setters are suspend functions, so preset application must run inside a
 * coroutine.
 */
private val gesturesPresetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * One curated gesture configuration: a full assignment of all ten gesture
 * slots offered by the keyboard. Built around Arabic-titled cards.
 */
private data class DrsGesturePreset(
    val nameRes: Int,
    val descRes: Int,
    val swipeUp: SwipeAction,
    val swipeDown: SwipeAction,
    val swipeLeft: SwipeAction,
    val swipeRight: SwipeAction,
    val spaceBarSwipeUp: SwipeAction,
    val spaceBarSwipeLeft: SwipeAction,
    val spaceBarSwipeRight: SwipeAction,
    val spaceBarLongPress: SwipeAction,
    val deleteKeySwipeLeft: SwipeAction,
    val deleteKeyLongPress: SwipeAction,
)

private val DRS_PRESET_BALANCED = DrsGesturePreset(
    nameRes = R.string.drs__gestures__preset_balanced,
    descRes = R.string.drs__gestures__preset_balanced_desc,
    swipeUp = SwipeAction.SHIFT,
    swipeDown = SwipeAction.HIDE_KEYBOARD,
    swipeLeft = SwipeAction.SWITCH_TO_NEXT_SUBTYPE,
    swipeRight = SwipeAction.SWITCH_TO_PREV_SUBTYPE,
    spaceBarSwipeUp = SwipeAction.NO_ACTION,
    spaceBarSwipeLeft = SwipeAction.MOVE_CURSOR_LEFT,
    spaceBarSwipeRight = SwipeAction.MOVE_CURSOR_RIGHT,
    spaceBarLongPress = SwipeAction.SHOW_INPUT_METHOD_PICKER,
    deleteKeySwipeLeft = SwipeAction.DELETE_CHARACTERS_PRECISELY,
    deleteKeyLongPress = SwipeAction.DELETE_CHARACTER,
)

private val DRS_PRESET_WRITER = DrsGesturePreset(
    nameRes = R.string.drs__gestures__preset_writer,
    descRes = R.string.drs__gestures__preset_writer_desc,
    swipeUp = SwipeAction.MOVE_CURSOR_UP,
    swipeDown = SwipeAction.MOVE_CURSOR_DOWN,
    swipeLeft = SwipeAction.MOVE_CURSOR_LEFT,
    swipeRight = SwipeAction.MOVE_CURSOR_RIGHT,
    spaceBarSwipeUp = SwipeAction.SHOW_SUBTYPE_PICKER,
    spaceBarSwipeLeft = SwipeAction.MOVE_CURSOR_START_OF_LINE,
    spaceBarSwipeRight = SwipeAction.MOVE_CURSOR_END_OF_LINE,
    spaceBarLongPress = SwipeAction.SHOW_INPUT_METHOD_PICKER,
    deleteKeySwipeLeft = SwipeAction.DELETE_WORD,
    deleteKeyLongPress = SwipeAction.DELETE_WORDS_PRECISELY,
)

private val DRS_PRESET_CODER = DrsGesturePreset(
    nameRes = R.string.drs__gestures__preset_coder,
    descRes = R.string.drs__gestures__preset_coder_desc,
    swipeUp = SwipeAction.MOVE_CURSOR_UP,
    swipeDown = SwipeAction.MOVE_CURSOR_DOWN,
    swipeLeft = SwipeAction.MOVE_CURSOR_LEFT,
    swipeRight = SwipeAction.MOVE_CURSOR_RIGHT,
    spaceBarSwipeUp = SwipeAction.UNDO,
    spaceBarSwipeLeft = SwipeAction.SWITCH_TO_CLIPBOARD_CONTEXT,
    spaceBarSwipeRight = SwipeAction.SWITCH_TO_MEDIA_CONTEXT,
    spaceBarLongPress = SwipeAction.SHOW_INPUT_METHOD_PICKER,
    deleteKeySwipeLeft = SwipeAction.DELETE_WORDS_PRECISELY,
    deleteKeyLongPress = SwipeAction.DELETE_WORD,
)

private val DRS_PRESETS = listOf(DRS_PRESET_BALANCED, DRS_PRESET_WRITER, DRS_PRESET_CODER)

private fun applyGesturePreset(preset: DrsGesturePreset) {
    val prefs by DrsPreferenceStore
    gesturesPresetScope.launch {
        prefs.gestures.swipeUp.set(preset.swipeUp)
        prefs.gestures.swipeDown.set(preset.swipeDown)
        prefs.gestures.swipeLeft.set(preset.swipeLeft)
        prefs.gestures.swipeRight.set(preset.swipeRight)
        prefs.gestures.spaceBarSwipeUp.set(preset.spaceBarSwipeUp)
        prefs.gestures.spaceBarSwipeLeft.set(preset.spaceBarSwipeLeft)
        prefs.gestures.spaceBarSwipeRight.set(preset.spaceBarSwipeRight)
        prefs.gestures.spaceBarLongPress.set(preset.spaceBarLongPress)
        prefs.gestures.deleteKeySwipeLeft.set(preset.deleteKeySwipeLeft)
        prefs.gestures.deleteKeyLongPress.set(preset.deleteKeyLongPress)
    }
    DrsAdaptationEngine.recordGestureUse()
}

/**
 * DRS Gestures: Arabic-first configuration for all keyboard gestures.
 * Offers one-tap Arabic presets (balanced / writer / coder) plus the full
 * manual assignment lists with localizable action labels, mirroring the
 * upstream gesture system without replacing it.
 */
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun DrsGesturesScreen() = DrsScreen {
    title = stringRes(R.string.drs__gestures__title)
    navigationIconVisible = true
    previewFieldVisible = true

    var appliedPresetName by remember { mutableStateOf("") }

    content {
        DrsInfoCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.drs__gestures__hint),
        )

        PreferenceGroup(title = stringRes(R.string.drs__gestures__presets_section)) {
                DRS_PRESETS.forEach { preset ->
                    val name = stringRes(preset.nameRes)
                    Card(
                        onClick = {
                            appliedPresetName = name
                            applyGesturePreset(preset)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringRes(preset.descRes),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (appliedPresetName.isNotEmpty()) {
                    Text(
                        text = stringRes(
                            R.string.drs__gestures__preset_applied,
                            "name" to appliedPresetName,
                        ),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
            }

            PreferenceGroup(title = stringRes(R.string.drs__gestures__manual_swipes)) {
                ListPreference(
                    prefs.gestures.swipeUp,
                    title = stringRes(R.string.pref__gestures__swipe_up__label),
                    entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                )
                ListPreference(
                    prefs.gestures.swipeDown,
                    title = stringRes(R.string.pref__gestures__swipe_down__label),
                    entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                )
                ListPreference(
                    prefs.gestures.swipeLeft,
                    title = stringRes(R.string.pref__gestures__swipe_left__label),
                    entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                )
                ListPreference(
                    prefs.gestures.swipeRight,
                    title = stringRes(R.string.pref__gestures__swipe_right__label),
                    entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                )
            }

            PreferenceGroup(title = stringRes(R.string.drs__gestures__manual_space_bar)) {
                ListPreference(
                    prefs.gestures.spaceBarSwipeUp,
                    title = stringRes(R.string.pref__gestures__space_bar_swipe_up__label),
                    entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                )
                ListPreference(
                    prefs.gestures.spaceBarSwipeLeft,
                    title = stringRes(R.string.pref__gestures__space_bar_swipe_left__label),
                    entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                )
                ListPreference(
                    prefs.gestures.spaceBarSwipeRight,
                    title = stringRes(R.string.pref__gestures__space_bar_swipe_right__label),
                    entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                )
                ListPreference(
                    prefs.gestures.spaceBarLongPress,
                    title = stringRes(R.string.pref__gestures__space_bar_long_press__label),
                    entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                )
            }

            PreferenceGroup(title = stringRes(R.string.drs__gestures__manual_delete)) {
                ListPreference(
                    prefs.gestures.deleteKeySwipeLeft,
                    title = stringRes(R.string.pref__gestures__delete_key_swipe_left__label),
                    entries = enumDisplayEntriesOf(SwipeAction::class, "deleteSwipe"),
                )
                ListPreference(
                    prefs.gestures.deleteKeyLongPress,
                    title = stringRes(R.string.pref__gestures__delete_key_long_press__label),
                    entries = enumDisplayEntriesOf(SwipeAction::class, "deleteLongPress"),
                )
            }

            PreferenceGroup(title = stringRes(R.string.drs__gestures__thresholds)) {
                DialogSliderPreference(
                    prefs.gestures.swipeDistanceThreshold,
                    title = stringRes(R.string.pref__gestures__swipe_distance_threshold__label),
                    valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                    min = 12,
                    max = 72,
                    stepIncrement = 1,
                )
                DialogSliderPreference(
                    prefs.gestures.swipeVelocityThreshold,
                    title = stringRes(R.string.pref__gestures__swipe_velocity_threshold__label),
                    valueLabel = { stringRes(R.string.unit__display_pixel_per_seconds__symbol, "v" to it) },
                    min = 400,
                    max = 4000,
                    stepIncrement = 100,
                )
            }
        }
    }
