/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.setup

import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsAppActivity
import com.drs.smartkeyboard.app.DrsPreferenceModel
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.compose.DrsScreenScope
import com.drs.smartkeyboard.lib.util.InputMethodUtils
import com.drs.smartkeyboard.lib.util.launchActivity
import com.drs.smartkeyboard.lib.util.launchUrl
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.jetpref.datastore.ui.PreferenceUiScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.drs.lib.android.AndroidVersion
import org.drs.lib.compose.DrsBulletSpacer
import org.drs.lib.compose.DrsStep
import org.drs.lib.compose.DrsStepLayout
import org.drs.lib.compose.DrsStepState
import org.drs.lib.compose.stringRes

@Composable
fun SetupScreen() = DrsScreen {
    title = stringRes(R.string.setup__title)
    navigationIconVisible = false
    scrollable = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    val prefs by DrsPreferenceStore
    val scope = rememberCoroutineScope()

    val isDrsKeyboardEnabled by InputMethodUtils.observeIsDrsKeyboardEnabled(foregroundOnly = true)
    val isDrsKeyboardSelected by InputMethodUtils.observeIsDrsKeyboardSelected(foregroundOnly = true)
    val hasNotificationPermission by prefs.internal.notificationPermissionState.collectAsState()

    val requestNotification =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            scope.launch {
                if (isGranted) {
                    prefs.internal.notificationPermissionState.set(NotificationPermissionState.GRANTED)
                } else {
                    prefs.internal.notificationPermissionState.set(NotificationPermissionState.DENIED)
                }
            }
        }

    content(
        isDrsKeyboardEnabled,
        isDrsKeyboardSelected,
        context,
        navController,
        requestNotification,
        hasNotificationPermission,
        scope,
    )
}

@Composable
private fun DrsScreenScope.content(
    isDrsKeyboardEnabled: Boolean,
    isDrsKeyboardSelected: Boolean,
    context: Context,
    navController: NavController,
    requestNotification: ManagedActivityResultLauncher<String, Boolean>,
    hasNotificationPermission: NotificationPermissionState,
    scope: CoroutineScope,
) {

    val stepState = rememberSaveable(saver = DrsStepState.Saver) {
        val initStep = when {
            !isDrsKeyboardEnabled -> Steps.EnableIme.id
            !isDrsKeyboardSelected -> Steps.SelectIme.id
            hasNotificationPermission == NotificationPermissionState.NOT_SET && AndroidVersion.ATLEAST_API33_T -> Steps.SelectNotification.id
            else -> Steps.FinishUp.id
        }
        DrsStepState.new(init = initStep)
    }

    content {
        // DRS (B7) — the 200ms poll LaunchedEffect was deleted (battery). This single effect
        // relaunches on every observed change (isDrsKeyboardEnabled / isDrsKeyboardSelected /
        // hasNotificationPermission), so it now does BOTH jobs with fresh values:
        // 1st the "return from the system IME enabler as soon as it gets selected" auto-continue
        // (fresh enable query; EnableIme auto step + manual==-1 guard preserved), then the
        // derived auto-step advance.
        LaunchedEffect(isDrsKeyboardEnabled, isDrsKeyboardSelected, hasNotificationPermission) {
            val isEnabled = InputMethodUtils.isDrsKeyboardEnabled(context)
            if (stepState.getCurrentAuto().value == Steps.EnableIme.id &&
                stepState.getCurrentManual().value == -1 &&
                !isDrsKeyboardEnabled &&
                !isDrsKeyboardSelected &&
                hasNotificationPermission == NotificationPermissionState.NOT_SET &&
                isEnabled
            ) {
                context.launchActivity(DrsAppActivity::class) {
                    it.flags = (Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
            stepState.setCurrentAuto(
                when {
                    !isDrsKeyboardEnabled -> Steps.EnableIme.id
                    !isDrsKeyboardSelected -> Steps.SelectIme.id
                    hasNotificationPermission == NotificationPermissionState.NOT_SET && AndroidVersion.ATLEAST_API33_T -> Steps.SelectNotification.id
                    else -> Steps.FinishUp.id
                }
            )
        }
        DrsStepLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            stepState = stepState,
            header = {
                StepText(stringRes(R.string.setup__intro_message))
                Spacer(modifier = Modifier.height(16.dp))
            },
            steps = steps(
                context, navController, requestNotification, scope
            ),
            footer = {
                footer(context)
            },
        )
    }
}

@Composable
private fun footer(context: Context) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val privacyPolicyUrl = stringRes(R.string.drs__privacy_policy_url)
        TextButton(onClick = { context.launchUrl(privacyPolicyUrl) }) {
            Text(text = stringRes(R.string.setup__footer__privacy_policy))
        }
        DrsBulletSpacer()
        val repositoryUrl = stringRes(R.string.drs__repo_url)
        TextButton(onClick = { context.launchUrl(repositoryUrl) }) {
            Text(text = stringRes(R.string.setup__footer__repository))
        }
    }
}

@Composable
private fun PreferenceUiScope<DrsPreferenceModel>.steps(
    context: Context,
    navController: NavController,
    requestNotification: ManagedActivityResultLauncher<String, Boolean>,
    scope: CoroutineScope,
): List<DrsStep> {

    return listOfNotNull(
        DrsStep(
            id = Steps.EnableIme.id,
            title = stringRes(R.string.setup__enable_ime__title),
        ) {
            StepText(stringRes(R.string.setup__enable_ime__description))
            StepButton(label = stringRes(R.string.setup__enable_ime__open_settings_btn)) {
                InputMethodUtils.showImeEnablerActivity(context)
            }
        },
        DrsStep(
            id = Steps.SelectIme.id,
            title = stringRes(R.string.setup__select_ime__title),
        ) {
            StepText(stringRes(R.string.setup__select_ime__description))
            StepButton(label = stringRes(R.string.setup__select_ime__switch_keyboard_btn)) {
                InputMethodUtils.showImePicker(context)
            }
        },
        if (AndroidVersion.ATLEAST_API33_T) {
            DrsStep(
                id = Steps.SelectNotification.id,
                title = stringRes(R.string.setup__grant_notification_permission__title),
            ) {
                StepText(stringRes(R.string.setup__grant_notification_permission__description))
                StepButton(stringRes(R.string.setup__grant_notification_permission__btn)) {
                    requestNotification.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else null,
        DrsStep(
            id = Steps.FinishUp.id,
            title = stringRes(R.string.setup__finish_up__title),
        ) {
            StepText(stringRes(R.string.setup__finish_up__description_p1))
            StepText(stringRes(R.string.setup__finish_up__description_p2))
            StepButton(label = stringRes(R.string.setup__finish_up__finish_btn)) {
                scope.launch { this@steps.prefs.internal.isImeSetUp.set(true) }
                navController.navigate(Routes.Settings.Home) {
                    popUpTo(Routes.Setup.Screen) {
                        inclusive = true
                    }
                }
            }
        }
    )
}

private sealed class Steps(val id: Int) {
    data object EnableIme : Steps(id = 1)
    data object SelectIme : Steps(id = 2)
    data object SelectNotification : Steps(id = 3)
    data object FinishUp : Steps(id = 4)
}
