/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.drs.smartkeyboard.BuildConfig
import com.drs.smartkeyboard.lib.devtools.flogDebug
import kotlinx.coroutines.delay
import org.drs.lib.android.AndroidSettings
import org.drs.lib.android.AndroidVersion
import org.drs.lib.android.systemServiceOrNull
import org.drs.lib.compose.observeAsState

private const val DELIMITER = ':'
private const val IME_SERVICE_CLASS_NAME = "com.drs.smartkeyboard.DrsImeService"
// DRS (B6) — 500ms foreground polling per screen burned battery; 2s + RESUMED-only (repeatOnLifecycle)
// pauses the loop entirely while the host is not resumed (fixes all 8 screen usages at the source).
private const val TIMED_QUERY_DELAY = 2000L

object InputMethodUtils {
    fun isDrsKeyboardEnabled(context: Context): Boolean {
        return if (AndroidVersion.ATLEAST_API34_U) {
            context.systemServiceOrNull(InputMethodManager::class)
                ?.enabledInputMethodList
                ?.any { it.packageName == BuildConfig.APPLICATION_ID } ?: false
        } else {
            val enabledImeList = AndroidSettings.Secure.getString(
                context, Settings.Secure.ENABLED_INPUT_METHODS
            )
            enabledImeList != null && parseIsDrsKeyboardEnabled(context, enabledImeList)
        }
    }

    fun isDrsKeyboardSelected(context: Context): Boolean {
        return if (AndroidVersion.ATLEAST_API34_U) {
            context.systemServiceOrNull(InputMethodManager::class)
                ?.currentInputMethodInfo
                ?.packageName == BuildConfig.APPLICATION_ID
        } else {
            val selectedIme = AndroidSettings.Secure.getString(
                context, Settings.Secure.DEFAULT_INPUT_METHOD
            )
            selectedIme != null && parseIsDrsKeyboardSelected(context, selectedIme)
        }
    }

    @Composable
    fun observeIsDrsKeyboardEnabled(
        context: Context = LocalContext.current.applicationContext,
        foregroundOnly: Boolean = false,
    ): State<Boolean> {
        return if (AndroidVersion.ATLEAST_API34_U) {
            timedObserveIsDrsKeyboardEnabled()
        } else {
            AndroidSettings.Secure.observeAsState(
                key = Settings.Secure.ENABLED_INPUT_METHODS,
                foregroundOnly = foregroundOnly,
                transform = { parseIsDrsKeyboardEnabled(context, it.toString()) },
            )
        }
    }

    @Composable
    fun observeIsDrsKeyboardSelected(
        context: Context = LocalContext.current.applicationContext,
        foregroundOnly: Boolean = false,
    ): State<Boolean> {
        return if (AndroidVersion.ATLEAST_API34_U) {
            timedObserveIsDrsKeyboardSelected()
        } else {
            AndroidSettings.Secure.observeAsState(
                key = Settings.Secure.DEFAULT_INPUT_METHOD,
                foregroundOnly = foregroundOnly,
                transform = { parseIsDrsKeyboardSelected(context, it.toString()) },
            )
        }
    }

    fun parseIsDrsKeyboardEnabled(context: Context, activeImeIds: String): Boolean {
        flogDebug { activeImeIds }
        return activeImeIds.split(DELIMITER).map { componentStr ->
            ComponentName.unflattenFromString(componentStr)
        }.any { it?.packageName == context.packageName && it.className == IME_SERVICE_CLASS_NAME }
    }

    fun parseIsDrsKeyboardSelected(context: Context, selectedImeId: String): Boolean {
        flogDebug { selectedImeId }
        val component = ComponentName.unflattenFromString(selectedImeId)
        return component?.packageName == context.packageName && component.className == IME_SERVICE_CLASS_NAME
    }

    fun showImeEnablerActivity(context: Context) {
        val intent = Intent()
        intent.action = Settings.ACTION_INPUT_METHOD_SETTINGS
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        context.startActivity(intent)
    }

    fun showImePicker(context: Context): Boolean {
        val imm = context.systemServiceOrNull(InputMethodManager::class)
        return if (imm != null) {
            imm.showInputMethodPicker()
            true
        } else {
            false
        }
    }

    @RequiresApi(api = 34)
    @Composable
    private fun timedObserveIsDrsKeyboardEnabled(): State<Boolean> {
        val state = remember { mutableStateOf(false) }
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            // DRS (B6) — poll only while RESUMED; loop is cancelled on pause, re-armed on resume.
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    state.value = isDrsKeyboardEnabled(context)
                    delay(TIMED_QUERY_DELAY)
                }
            }
        }
        return state
    }

    @RequiresApi(api = 34)
    @Composable
    private fun timedObserveIsDrsKeyboardSelected(): State<Boolean> {
        val state = remember { mutableStateOf(false) }
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            // DRS (B6) — poll only while RESUMED; loop is cancelled on pause, re-armed on resume.
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    state.value = isDrsKeyboardSelected(context)
                    delay(TIMED_QUERY_DELAY)
                }
            }
        }
        return state
    }
}
