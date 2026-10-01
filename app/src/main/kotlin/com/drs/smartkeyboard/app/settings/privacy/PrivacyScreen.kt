/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.settings.privacy

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.drs.smartkeyboard.BuildConfig
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.drs.ai.DrsLearningEngine
import com.drs.smartkeyboard.drs.privacy.DrsE2ECrypto
import com.drs.smartkeyboard.drs.privacy.DrsE2ECrypto.DrsE2EError
import com.drs.smartkeyboard.drs.privacy.DrsNetworkSentinel
import com.drs.smartkeyboard.drs.privacy.DrsPrivacyDashboard
import com.drs.smartkeyboard.drs.privacy.DrsPrivacyLock
import com.drs.smartkeyboard.drs.privacy.DrsSyncBundle
import com.drs.smartkeyboard.lib.compose.DrsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.datastore.ui.PreferenceGroup
import org.drs.jetpref.datastore.ui.SwitchPreference
import org.drs.lib.compose.stringRes
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * DRS roadmap phase 3 (privacy & trust, tasks 2+3+4): the privacy
 * dashboard. Three honest sections:
 *  1. the absolute privacy mode switch (closes every network surface),
 *  2. the LIVE network attestation — TrafficStats deltas for our UID,
 *     rendered as numbers the user can verify against the OS — plus
 *     the permission inventory,
 *  3. the data inventory with one-tap E2E export (DRSYNC1 container)
 *     and one-tap real deletion.
 * Nothing here is decorative: every number comes from
 * [DrsNetworkSentinel]/[DrsPrivacyDashboard], not a hardcoded claim.
 */
@Composable
fun PrivacyScreen() = DrsScreen {
    title = stringRes(R.string.privacy__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Live dashboard state, recomputed on entry and after destructive actions.
    var summary by remember { mutableStateOf<DrsPrivacyDashboard.Summary?>(null) }

    // Passphrase carrier between the export dialog and the SAF create launcher.
    var pendingSealedExport by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var passphraseError by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val text = pendingSealedExport
        if (uri != null && text != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(text.toByteArray(Charsets.UTF_8))
                        } != null
                    }.getOrDefault(false)
                }
                Toast.makeText(
                    context,
                    context.getString(
                        if (ok) R.string.privacy__export__done else R.string.privacy__export__failed,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        pendingSealedExport = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            passphrase = ""
            passphraseError = false
            showImportDialog = true
        }
    }

    fun refresh() {
        scope.launch {
            summary = withContext(Dispatchers.IO) {
                buildDashboard(context).summary()
            }
        }
    }

    // --- dialogs -------------------------------------------------------

    if (showExportDialog) {
        PassphraseDialog(
            title = stringRes(R.string.privacy__export__passphrase_title),
            hint = stringRes(R.string.privacy__export__passphrase_hint),
            passphrase = passphrase,
            error = passphraseError,
            errorText = stringRes(R.string.privacy__passphrase_min),
            onPassphrase = { passphrase = it; passphraseError = false },
            onDismiss = { showExportDialog = false },
            onConfirm = {
                if (passphrase.length < MIN_PASSPHRASE) {
                    passphraseError = true
                } else {
                    showExportDialog = false
                    scope.launch {
                        val sealed = withContext(Dispatchers.IO) {
                            runCatching {
                                buildDashboard(context).exportEncrypted(
                                    appVersion = BuildConfig.VERSION_NAME,
                                    exportedAt = System.currentTimeMillis(),
                                    passphrase = passphrase.toCharArray(),
                                )
                            }.getOrNull()
                        }
                        if (sealed == null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.privacy__export__empty),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            pendingSealedExport = sealed
                            val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
                                .withZone(ZoneOffset.UTC)
                                .format(Instant.now())
                            exportLauncher.launch("drs-smart-keyboard-$stamp.drsync")
                        }
                    }
                }
            },
        )
    }

    if (showImportDialog) {
        PassphraseDialog(
            title = stringRes(R.string.privacy__import__passphrase_title),
            hint = stringRes(R.string.privacy__export__passphrase_hint),
            passphrase = passphrase,
            error = passphraseError,
            errorText = stringRes(R.string.privacy__passphrase_min),
            onPassphrase = { passphrase = it; passphraseError = false },
            onDismiss = {
                showImportDialog = false
                pendingImportUri = null
            },
            onConfirm = {
                if (passphrase.length < MIN_PASSPHRASE) {
                    passphraseError = true
                } else {
                    showImportDialog = false
                    val uri = pendingImportUri
                    pendingImportUri = null
                    if (uri != null) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching {
                                    val text = context.contentResolver.openInputStream(uri)
                                        ?.use { it.readBytes().decodeToString() }
                                        ?: throw DrsE2EError.BadEncoding
                                    val payload = DrsSyncBundle.open(text, passphrase.toCharArray())
                                    DrsLearningEngine.importState(
                                        payload.words,
                                        payload.bigrams,
                                        replace = false,
                                    )
                                    true
                                }.getOrDefault(false)
                            }
                            Toast.makeText(
                                context,
                                context.getString(
                                    if (ok) R.string.privacy__import__done
                                    else R.string.privacy__import__failed,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                            refresh()
                        }
                    }
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringRes(R.string.privacy__delete__confirm)) },
            text = { Text(stringRes(R.string.privacy__delete__confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                buildDashboard(context).deleteAll()
                            }
                            refresh()
                        }
                    },
                ) { Text(stringRes(R.string.action__delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringRes(R.string.action__cancel))
                }
            },
        )
    }

    // --- content -------------------------------------------------------

    content {
        val absoluteMode by prefs.privacy.absoluteMode.collectAsState()
        LaunchedEffect(absoluteMode) { refresh() }

        PreferenceGroup(title = stringRes(R.string.privacy__mode__group)) {
            SwitchPreference(
                pref = prefs.privacy.absoluteMode,
                icon = Icons.Default.Lock,
                title = stringRes(R.string.privacy__absolute__label),
                summary = stringRes(R.string.privacy__absolute__summary),
            )
        }

        PreferenceGroup(title = stringRes(R.string.privacy__attestation__title)) {
            val att = summary?.attestation
            Preference(
                icon = Icons.Default.VerifiedUser,
                title = when {
                    att == null -> stringRes(R.string.privacy__attestation__measuring)
                    !att.countersSupported -> stringRes(R.string.privacy__attestation__unsupported)
                    att.zeroTraffic -> stringRes(R.string.privacy__attestation__ok)
                    else -> stringRes(R.string.privacy__attestation__moved)
                        .format(att.rxSinceBaseline, att.txSinceBaseline)
                },
                summary = stringRes(R.string.privacy__attestation__since_session) +
                    when {
                        att == null || !att.countersSupported -> ""
                        att.absoluteMode -> " · " + stringRes(R.string.privacy__attestation__locked_hint)
                        else -> ""
                    },
            )
            Preference(
                icon = Icons.Default.Security,
                title = stringRes(R.string.privacy__permissions__title),
                summary = att?.permissions
                    ?.joinToString(" · ") {
                        it.name.substringAfterLast('.') + if (it.granted) " ✓" else " ✗"
                    }
                    ?: stringRes(R.string.privacy__attestation__measuring),
            )
        }

        PreferenceGroup(title = stringRes(R.string.privacy__data__title)) {
            val s = summary
            if (s != null) {
                for (store in s.stores) {
                    Preference(
                        icon = Icons.Default.Storage,
                        title = storeTitle(store.id),
                        summary = stringRes(R.string.privacy__data__items)
                            .format(formatBytes(store.bytes), store.items),
                    )
                }
            }
            Preference(
                icon = Icons.Default.FileDownload,
                title = stringRes(R.string.privacy__export__label),
                summary = stringRes(R.string.privacy__export__summary),
                onClick = {
                    passphrase = ""
                    passphraseError = false
                    showExportDialog = true
                },
            )
            Preference(
                icon = Icons.Default.FileUpload,
                title = stringRes(R.string.privacy__import__label),
                summary = stringRes(R.string.privacy__import__summary),
                onClick = {
                    importLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                },
            )
            Preference(
                icon = Icons.Default.DeleteForever,
                title = stringRes(R.string.privacy__delete__label),
                summary = stringRes(R.string.privacy__delete__summary),
                onClick = { showDeleteDialog = true },
            )
        }
    }
}

private const val MIN_PASSPHRASE = 8

@Composable
private fun storeTitle(id: String): String = when (id) {
    DrsPrivacyDashboard.STORE_LEARNING -> stringResource(R.string.privacy__data__learning)
    DrsPrivacyDashboard.STORE_CRASH -> stringResource(R.string.privacy__data__crash)
    DrsPrivacyDashboard.STORE_UPDATES -> stringResource(R.string.privacy__data__updates)
    DrsPrivacyDashboard.STORE_CLIPBOARD_MEDIA -> stringResource(R.string.privacy__data__clipboard_media)
    else -> id
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%d KB".format(bytes / 1_000)
    else -> "$bytes B"
}

/** Builds the wired dashboard (engine probes + live sentinel attestation). */
private fun buildDashboard(context: Context): DrsPrivacyDashboard {
    val sentinel = DrsNetworkSentinel.android(context)
    return DrsPrivacyDashboard.android(
        context = context,
        attestationProvider = {
            sentinel.attestation(
                absoluteMode = DrsPrivacyLock.isAbsoluteMode(),
                baseline = DrsNetworkSentinel.baseline(),
            )
        },
        learningSnapshot = { DrsLearningEngine.exportState() },
    )
}

@Composable
private fun PassphraseDialog(
    title: String,
    hint: String,
    passphrase: String,
    error: Boolean,
    errorText: String,
    onPassphrase: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = passphrase,
                onValueChange = onPassphrase,
                singleLine = true,
                isError = error,
                label = { Text(hint) },
                supportingText = {
                    if (error) {
                        Text(errorText, color = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringRes(R.string.action__ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.action__cancel)) }
        },
    )
}
