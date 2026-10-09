/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

@file:OptIn(ExperimentalJetPrefDatastoreUi::class, ExperimentalMaterial3Api::class)

package com.drs.smartkeyboard.drs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import com.drs.smartkeyboard.drs.DrsShortcuts
import com.drs.smartkeyboard.ime.text.gestures.SwipeAction
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.lib.compose.stringRes
import org.drs.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import org.drs.jetpref.datastore.ui.ListPreference
import org.drs.jetpref.datastore.ui.PreferenceGroup

/**
 * DRS v1.0.7 — الكتابة والأدوات الموحدة (Unified Writing & Tools).
 *
 * One screen over the whole writing pipeline of the unified system:
 *  - الكتابة الأساسية: what the engine already does for everyone.
 *  - التحرير المتقدم: undo/redo, selection, word deletion - available as
 *    unified-strip tools and as real gesture assignments edited here.
 *  - القوالب والاختصارات: the real template variables the expansion
 *    engine supports, with the per-shortcut availability scope.
 *
 * Every control edits a real preference or navigates to the matching
 * manager; nothing is decorative.
 */
@Composable
fun DrsUnifiedWritingScreen() = DrsScreen {
    title = stringRes(R.string.drs__unified__writing_title)
    navigationIconVisible = true
    previewFieldVisible = true

    val navController = LocalNavController.current
    val prefs by DrsPreferenceStore

    content {
        // ---------------- explanatory cards ----------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringRes(R.string.drs__unified__writing_basic_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringRes(R.string.drs__unified__writing_basic_body),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringRes(R.string.drs__unified__writing_advanced_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringRes(R.string.drs__unified__writing_advanced_body),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { navController.navigate(Routes.Settings.DrsUnifiedTools) }) {
                    Text(stringRes(R.string.drs__unified__dashboard_manage_tools))
                }
                TextButton(onClick = { navController.navigate(Routes.Settings.DrsGestures) }) {
                    Text(stringRes(R.string.drs__gestures__title))
                }
            }
        }

        // ---------------- real gesture assignments for writing ----------------
        PreferenceGroup(title = stringRes(R.string.drs__unified__writing_gestures_group)) {
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
        }

        // ---------------- templates & shortcuts ----------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringRes(R.string.drs__unified__writing_templates_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringRes(R.string.drs__unified__writing_templates_body),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                DrsShortcuts.TEMPLATE_VARIABLES.forEach { (variable, sample) ->
                    Text(
                        text = "$variable → ${sample.ifBlank { "—" }}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                TextButton(onClick = { navController.navigate(Routes.Settings.DrsShortcuts) }) {
                    Text(stringRes(R.string.drs__shortcuts__title))
                }
                // DRS v2.21.0: نصوصي المحفوظة — the manager of the
                // sixth smart panel, a sibling of the shortcuts system.
                Text(
                    text = stringRes(R.string.drs__unified__writing_mytexts_body),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { navController.navigate(Routes.Settings.DrsMyTexts) }) {
                    Text(stringRes(R.string.drs__mytexts__title))
                }
            }
        }
    }
}
