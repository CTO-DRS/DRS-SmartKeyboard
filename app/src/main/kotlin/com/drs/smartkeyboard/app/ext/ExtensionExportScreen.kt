/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.ext

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.extensionManager
import org.drs.lib.android.showLongToast
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.ext.Extension
import com.drs.smartkeyboard.lib.ext.ExtensionDefaults
import org.drs.lib.android.showLongToastSync

@Composable
fun ExtensionExportScreen(id: String) {
    val context = LocalContext.current
    val extensionManager by context.extensionManager()

    val ext = extensionManager.getExtensionById(id)
    if (ext != null) {
        ExportScreen(ext)
    } else {
        ExtensionNotFoundScreen(id)
    }
}

@Composable
private fun ExportScreen(ext: Extension) = DrsScreen {
    title = ext.meta.title
    scrollable = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val extensionManager by context.extensionManager()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(),
        onResult = { uri ->
            // If uri is null it indicates that the selection activity
            //  was cancelled (mostly by pressing the back button), so
            //  we don't display an error message here.
            if (uri == null) {
                navController.popBackStack()
                return@rememberLauncherForActivityResult
            }
            runCatching { extensionManager.export(ext, uri) }.onSuccess {
                context.showLongToastSync(R.string.ext__export__success)
            }.onFailure { error ->
                context.showLongToastSync(R.string.ext__export__failure, "error_message" to error.localizedMessage)
            }
            navController.popBackStack()
        },
    )

    content {
        exportLauncher.launch(ExtensionDefaults.createFlexName(ext.meta.id))
    }
}
