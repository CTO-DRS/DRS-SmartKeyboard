/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.drs.smartkeyboard.app.ext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.drsupdater.DrsUpdateCenter
import com.drs.smartkeyboard.app.drsupdater.ReleaseInfo
import com.drs.smartkeyboard.app.drsupdater.UpdateCheckMode
import com.drs.smartkeyboard.app.enumDisplayEntriesOf
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.lib.compose.DrsButton
import org.drs.lib.compose.DrsOutlinedBox
import org.drs.lib.compose.DrsTextButton
import org.drs.lib.compose.defaultDrsOutlinedBox
import kotlinx.coroutines.launch
import org.drs.jetpref.datastore.ui.ListPreference
import org.drs.jetpref.datastore.ui.Preference
import org.drs.jetpref.datastore.ui.SwitchPreference
import org.drs.lib.compose.stringRes
import java.util.Locale

/**
 * DRS Update Center — the real in-app update experience: version status,
 * release metadata, resumable download with live progress, mandatory
 * SHA-256 verification and the official install hand-off. Offline states
 * are first-class: nothing here can crash the app without connectivity.
 */
@Composable
fun DrsUpdateCenterScreen() = DrsScreen {
    title = stringRes(R.string.updates__center__title)
    previewFieldVisible = false

    content {
        val context = LocalContext.current
        val prefs by com.drs.smartkeyboard.app.DrsPreferenceStore
        val checkState by DrsUpdateCenter.checkState.collectAsState()
        val downloadState by DrsUpdateCenter.downloadState.collectAsState()
        val scope = rememberCoroutineScope()

        // Version status card ------------------------------------------------
        DrsOutlinedBox(modifier = Modifier.defaultDrsOutlinedBox()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringRes(R.string.updates__current_version),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "DRS Smart Keyboard v" + DrsUpdateCenter.currentVersion(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                when (val state = checkState) {
                    is DrsUpdateCenter.CheckState.Checking -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    }
                    is DrsUpdateCenter.CheckState.UpToDate -> {
                        IconChip(
                            icon = { Icon(modifier=Modifier.size(18.dp), imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            text = stringRes(R.string.updates__up_to_date),
                        )
                    }
                    is DrsUpdateCenter.CheckState.Available -> {
                        // DRS v1.0.2: name the exact upgrade target so the user
                        // always knows what they are about to download.
                        IconChip(
                            icon = { Icon(modifier=Modifier.size(18.dp), imageVector = Icons.Filled.SystemUpdateAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            text = stringRes(R.string.updates__available_short) + " · v" + state.info.version,
                        )
                    }
                    is DrsUpdateCenter.CheckState.Failed -> {
                        IconChip(
                            icon = { Icon(modifier=Modifier.size(18.dp), imageVector = Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            text = stringRes(R.string.updates__error_check),
                        )
                    }
                    DrsUpdateCenter.CheckState.Idle -> {}
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
                DrsTextButton(
                    onClick = { scope.launch { DrsUpdateCenter.checkForUpdates() } },
                    icon = Icons.Outlined.Refresh,
                    text = stringRes(R.string.updates__check_now),
                    enabled = checkState !is DrsUpdateCenter.CheckState.Checking,
                )
            }
        }

        // Release info card ---------------------------------------------------
        val availableInfo = (checkState as? DrsUpdateCenter.CheckState.Available)?.info
        if (availableInfo != null) {
            ReleaseInfoCard(info = availableInfo, downloadState = downloadState)
        }

        // Download progress card ----------------------------------------------
        when (val dl = downloadState) {
            is DrsUpdateCenter.DownloadState.Downloading -> {
                DownloadProgressCard(
                    title = stringRes(R.string.updates__downloading),
                    received = dl.received,
                    total = dl.total,
                    speedBps = dl.bytesPerSecond,
                    onPause = { DrsUpdateCenter.pauseDownload() },
                    onCancel = { DrsUpdateCenter.cancelDownload(context, availableInfo!!) },
                )
            }
            is DrsUpdateCenter.DownloadState.Paused -> {
                DownloadProgressCard(
                    title = stringRes(R.string.updates__paused),
                    received = dl.received,
                    total = dl.total,
                    speedBps = 0L,
                    onResume = { availableInfo?.let { DrsUpdateCenter.resumeDownload(context, it) } },
                    onCancel = { DrsUpdateCenter.cancelDownload(context, availableInfo!!) },
                )
            }
            is DrsUpdateCenter.DownloadState.Verifying -> {
                DrsOutlinedBox(modifier = Modifier.defaultDrsOutlinedBox()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 3.dp)
                        Text(stringRes(R.string.updates__verifying), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is DrsUpdateCenter.DownloadState.Ready -> {
                ReadyCard(file = dl.file, sha256 = dl.sha256)
            }
            is DrsUpdateCenter.DownloadState.Failed -> {
                DrsOutlinedBox(modifier = Modifier.defaultDrsOutlinedBox()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            stringRes(R.string.updates__error_download) + "\n" + (dl.message ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                availableInfo?.let { info ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                        DrsTextButton(
                            onClick = { DrsUpdateCenter.startDownload(context, info) },
                            icon = Icons.Outlined.CloudDownload,
                            text = stringRes(R.string.updates__retry),
                        )
                    }
                }
            }
            DrsUpdateCenter.DownloadState.Idle -> {}
        }

        // Auto-check settings --------------------------------------------------
        DrsOutlinedBox(modifier = Modifier.defaultDrsOutlinedBox()) {
            Text(
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp),
                text = stringRes(R.string.updates__auto_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                modifier = Modifier.padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 6.dp),
                text = stringRes(R.string.updates__privacy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ListPreference(
                prefs.updates.checkMode,
                icon = Icons.Filled.SystemUpdateAlt,
                title = stringRes(R.string.updates__check_mode),
                entries = enumDisplayEntriesOf(UpdateCheckMode::class),
            )
            SwitchPreference(
                prefs.updates.notifyOnUpdate,
                icon = Icons.Filled.SystemUpdateAlt,
                title = stringRes(R.string.updates__notify),
            )
        }

        // DRS v1.0.2: a compact walkthrough card — every step of the update
        // flow in plain language, so a stalled install (wrong system dialog,
        // missing install-permission, interrupted download) is never a
        // dead end for the user.
        DrsOutlinedBox(modifier = Modifier.defaultDrsOutlinedBox()) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringRes(R.string.updates__guide__title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 12.dp),
                text = stringRes(R.string.updates__guide__body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReleaseInfoCard(info: ReleaseInfo, downloadState: DrsUpdateCenter.DownloadState) {
    val context = LocalContext.current
    DrsOutlinedBox(modifier = Modifier.defaultDrsOutlinedBox()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = info.releaseName.ifBlank { info.tag },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(6.dp))
            InfoLine(
                label = stringRes(R.string.updates__latest_version),
                value = "v" + info.version,
            )
            InfoLine(
                label = stringRes(R.string.updates__size),
                value = formatBytes(info.assetSize),
            )
            if (info.publishedAt.isNotBlank()) {
                InfoLine(
                    label = stringRes(R.string.updates__release_date),
                    value = info.publishedAt.substringBefore('T'),
                )
            }
            if (!info.body.isNullOrBlank()) {
                Spacer(Modifier.size(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = info.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            val busy = downloadState is DrsUpdateCenter.DownloadState.Downloading ||
                downloadState is DrsUpdateCenter.DownloadState.Verifying
            DrsButton(
                onClick = { DrsUpdateCenter.startDownload(context, info) },
                icon = Icons.Outlined.FileDownload,
                text = stringRes(R.string.updates__download),
                enabled = !busy,
            )
        }
    }
}

@Composable
private fun DownloadProgressCard(
    title: String,
    received: Long,
    total: Long,
    speedBps: Long,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    DrsOutlinedBox(modifier = Modifier.defaultDrsOutlinedBox()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(8.dp))
            val fraction = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = buildString {
                    append("${formatBytes(received)} / ${formatBytes(total)}")
                    if (speedBps > 0) append("  ·  ${formatBytes(speedBps)}/s")
                    append("  ·  ${(fraction * 100).toInt()}%")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onPause != null) {
                    DrsTextButton(
                        onClick = onPause,
                        icon = Icons.Filled.Pause,
                        text = stringRes(R.string.updates__pause),
                    )
                }
                if (onResume != null) {
                    DrsTextButton(
                        onClick = onResume,
                        icon = Icons.Filled.PlayArrow,
                        text = stringRes(R.string.updates__resume),
                    )
                }
                if (onCancel != null) {
                    DrsTextButton(
                        onClick = onCancel,
                        text = stringRes(R.string.updates__cancel),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyCard(file: java.io.File, sha256: String) {
    val context = LocalContext.current
    val canInstall = DrsUpdateCenter.canRequestInstall(context)
    DrsOutlinedBox(modifier = Modifier.defaultDrsOutlinedBox()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(stringRes(R.string.updates__verified), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "SHA-256 · ${sha256.take(16)}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            if (canInstall) {
                DrsButton(
                    onClick = { DrsUpdateCenter.installApk(context, file) },
                    icon = Icons.Filled.SystemUpdateAlt,
                    text = stringRes(R.string.updates__install),
                )
            } else {
                Text(
                    text = stringRes(R.string.updates__install_permission_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.size(6.dp))
                DrsButton(
                    onClick = { DrsUpdateCenter.openInstallPermissionSettings(context) },
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    text = stringRes(R.string.updates__grant_permission),
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun IconChip(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
