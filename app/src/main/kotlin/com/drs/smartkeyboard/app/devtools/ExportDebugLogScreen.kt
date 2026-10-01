/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.devtools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.clipboardManager
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.devtools.Devtools
import org.drs.lib.android.showShortToastSync
import org.drs.lib.compose.DrsButton
import org.drs.lib.compose.drsHorizontalScroll
import org.drs.lib.compose.drsScrollbar
import org.drs.lib.compose.stringRes

// TODO: This screen is just a quick thrown-together thing and needs further enhancing in the UI
@Composable
fun ExportDebugLogScreen() = DrsScreen {
    title = stringRes(R.string.devtools__debuglog__title)
    scrollable = false

    val prefs by DrsPreferenceStore
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboardManager by context.clipboardManager()

    var debugLog by remember { mutableStateOf<List<String>?>(null) }
    var formattedDebugLog by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(Unit) {
        debugLog = Devtools.generateDebugLog(context, prefs, includeLogcat = true).lines()
        formattedDebugLog = Devtools.generateDebugLogForGithub(context, prefs, includeLogcat = true).lines()
    }

    bottomBar {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DrsButton(
                onClick = {
                    clipboardManager.addNewPlaintext(debugLog!!.joinToString("\n"))
                    context.showShortToastSync(resources.getString(R.string.devtools__debuglog__copied_to_clipboard))
                },
                modifier = Modifier,
                text = stringRes(R.string.devtools__debuglog__copy_log),
                enabled = debugLog != null,
            )
            DrsButton(
                onClick = {
                    clipboardManager.addNewPlaintext(formattedDebugLog!!.joinToString("\n"))
                    context.showShortToastSync(resources.getString(R.string.devtools__debuglog__copied_to_clipboard))
                },
                text = stringRes(R.string.devtools__debuglog__copy_for_github),
                enabled = debugLog != null,
            )
        }
    }

    content {
        // Forcing LTR because text displayed is a debug log
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val lazyListState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .drsScrollbar(lazyListState, isVertical = true)
                    .drsHorizontalScroll(),
                state = lazyListState,
            ) {
                val log = debugLog
                if (log == null) {
                    item {
                        Text(stringRes(R.string.devtools__debuglog__loading))
                    }
                } else {
                    items(log) { logLine ->
                        Text(
                            text = logLine,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}
