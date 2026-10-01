/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.help

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.drs.ui.DrsOnboardingActivity
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.util.InputMethodUtils
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.datastore.ui.PreferenceGroup
import org.drs.lib.compose.stringRes

/**
 * DRS v1.0.5: a plain-language help center for the normal user. Every entry
 * is wired to the real screen it describes — no dead ends: either it opens
 * the relevant settings page or performs the action directly.
 */
@Composable
fun HelpScreen() = DrsScreen {
    title = stringRes(R.string.help__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    // Live keyboard status — same checks the setup wizard uses.
    val isDrsKeyboardEnabled by InputMethodUtils.observeIsDrsKeyboardEnabled(foregroundOnly = true)
    val isDrsKeyboardSelected by InputMethodUtils.observeIsDrsKeyboardSelected(foregroundOnly = true)

    content {
        PreferenceGroup(title = stringRes(R.string.help__status_group)) {
            Preference(
                icon = if (isDrsKeyboardEnabled && isDrsKeyboardSelected) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.ErrorOutline
                },
                title = stringRes(R.string.help__status_title),
                summary = stringRes(
                    when {
                        isDrsKeyboardEnabled && isDrsKeyboardSelected -> R.string.help__status_ready
                        isDrsKeyboardEnabled -> R.string.help__status_enabled_not_selected
                        else -> R.string.help__status_disabled
                    },
                ),
                onClick = { navController.navigate(Routes.Setup.Screen) },
            )
            Preference(
                icon = Icons.Default.WavingHand,
                title = stringRes(R.string.help__rerun_welcome__title),
                summary = stringRes(R.string.help__rerun_welcome__summary),
                onClick = {
                    context.startActivity(Intent(context, DrsOnboardingActivity::class.java))
                },
            )
        }

        PreferenceGroup(title = stringRes(R.string.help__faq_group)) {
            // DRS v1.0.7: the unified «كلاهما» system explanation leads the FAQ.
            Preference(
                icon = Icons.Default.Dashboard,
                title = stringRes(R.string.help__faq_unified__title),
                summary = stringRes(R.string.help__faq_unified__summary),
                onClick = { navController.navigate(Routes.Settings.DrsUnifiedDashboard) },
            )
            Preference(
                icon = Icons.Default.Language,
                title = stringRes(R.string.help__faq_languages__title),
                summary = stringRes(R.string.help__faq_languages__summary),
                onClick = { navController.navigate(Routes.Settings.Localization) },
            )
            Preference(
                icon = Icons.Default.ContentPaste,
                title = stringRes(R.string.help__faq_clipboard__title),
                summary = stringRes(R.string.help__faq_clipboard__summary),
                onClick = { navController.navigate(Routes.Settings.Clipboard) },
            )
            Preference(
                icon = Icons.Default.EmojiEmotions,
                title = stringRes(R.string.help__faq_emoji__title),
                summary = stringRes(R.string.help__faq_emoji__summary),
                onClick = { navController.navigate(Routes.Settings.Media) },
            )
            Preference(
                icon = Icons.AutoMirrored.Filled.Assignment,
                title = stringRes(R.string.help__faq_shortcuts__title),
                summary = stringRes(R.string.help__faq_shortcuts__summary),
                onClick = { navController.navigate(Routes.Settings.DrsShortcuts) },
            )
            Preference(
                icon = Icons.Default.Gesture,
                title = stringRes(R.string.help__faq_gestures__title),
                summary = stringRes(R.string.help__faq_gestures__summary),
                onClick = { navController.navigate(Routes.Settings.Gestures) },
            )
            Preference(
                icon = Icons.Default.Palette,
                title = stringRes(R.string.help__faq_themes__title),
                summary = stringRes(R.string.help__faq_themes__summary),
                onClick = { navController.navigate(Routes.Settings.Theme) },
            )
            Preference(
                icon = Icons.Outlined.Keyboard,
                title = stringRes(R.string.help__faq_feedback__title),
                summary = stringRes(R.string.help__faq_feedback__summary),
                onClick = { navController.navigate(Routes.Settings.InputFeedback) },
            )
            Preference(
                icon = Icons.Default.SettingsBackupRestore,
                title = stringRes(R.string.help__faq_backup__title),
                summary = stringRes(R.string.help__faq_backup__summary),
                onClick = { navController.navigate(Routes.Settings.Backup) },
            )
            Preference(
                icon = Icons.Default.SystemUpdateAlt,
                title = stringRes(R.string.help__faq_update__title),
                summary = stringRes(R.string.help__faq_update__summary),
                onClick = { navController.navigate(Routes.Ext.CheckUpdates) },
            )
        }
    }
}
