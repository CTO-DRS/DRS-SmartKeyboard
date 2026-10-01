/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.drs.smartkeyboard.app.ext

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.drs.smartkeyboard.drs.DrsTrustState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.drsupdater.DrsPackageManager
import com.drs.smartkeyboard.app.drsupdater.DrsUpdateCenter
import com.drs.smartkeyboard.app.drsupdater.PackageStatus
import com.drs.smartkeyboard.app.drsupdater.RemotePackage
import com.drs.smartkeyboard.extensionManager
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.lib.compose.DrsButton
import org.drs.lib.compose.DrsOutlinedBox
import org.drs.lib.compose.DrsTextButton
import org.drs.lib.compose.defaultDrsOutlinedBox
import org.drs.lib.android.showLongToastSync
import kotlinx.coroutines.launch
import org.drs.lib.compose.stringRes

/** Real sort orders of the official catalog. */
enum class PackageSort(val labelRes: Int) {
    DEFAULT(R.string.packages__sort_default),
    NAME(R.string.packages__sort_name),
    SIZE(R.string.packages__sort_size),
    DATE(R.string.packages__sort_date),
}

/**
 * DRS Package Center — browsable catalog of official packages (themes,
 * layouts, language packs) fetched from the repository manifest, with
 * verified downloads (size + SHA-256) funneled into the native extension
 * system. Fully offline-safe: failures render as banners, never crashes.
 */
@Composable
fun DrsPackageCenterScreen() = DrsScreen {
    title = stringRes(R.string.packages__center__title)
    previewFieldVisible = false

    content {
        val context = LocalContext.current
        val extensionManager by context.extensionManager()
        val extensionIndex by extensionManager.extensions.collectAsState()
        val progress by DrsPackageManager.progress.collectAsState()
        val scope = rememberCoroutineScope()

        var fetchState by remember { mutableStateOf<FetchState>(FetchState.Loading) }
        var searchQuery by remember { mutableStateOf("") }
        var sortMode by remember { mutableStateOf(PackageSort.DEFAULT) }

        fun refresh() {
            fetchState = FetchState.Loading
            scope.launch {
                fetchState = DrsPackageManager.fetchPackages().fold(
                    onSuccess = { FetchState.Ready(it) },
                    onFailure = { FetchState.Failed(it.localizedMessage ?: "") },
                )
            }
        }
        LaunchedEffect(Unit) { refresh() }

        when (val state = fetchState) {
            FetchState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is FetchState.Failed -> {
                DrsOutlinedBox(modifier = Modifier.defaultDrsOutlinedBox()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            modifier = Modifier.size(28.dp),
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringRes(R.string.packages__error_fetch),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.size(8.dp))
                        DrsTextButton(
                            onClick = { refresh() },
                            icon = Icons.Outlined.Refresh,
                            text = stringRes(R.string.packages__retry),
                        )
                    }
                }
            }
            is FetchState.Ready -> {
                val packages = state.packages
                if (packages.isEmpty()) {
                    DrsOutlinedBox(modifier = Modifier.defaultDrsOutlinedBox()) {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            text = stringRes(R.string.packages__empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringRes(R.string.packages__search)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.size(8.dp))

                    val filtered = packages.filter { pkg ->
                        searchQuery.isBlank() ||
                            pkg.name.contains(searchQuery, ignoreCase = true) ||
                            pkg.description.contains(searchQuery, ignoreCase = true) ||
                            pkg.author.contains(searchQuery, ignoreCase = true)
                    }.let { list ->
                        when (sortMode) {
                            PackageSort.DEFAULT -> list
                            PackageSort.NAME -> list.sortedBy { it.name }
                            PackageSort.SIZE -> list.sortedByDescending { it.sizeBytes }
                            PackageSort.DATE -> list.sortedByDescending { it.releaseDate }
                        }
                    }

                    // Real catalog storage summary: count + total download size.
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        text = stringRes(
                            R.string.packages__summary,
                            "count" to packages.size,
                            "size" to formatBytes(packages.sumOf { it.sizeBytes }),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Real sort controls over the fetched catalog.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (sort in PackageSort.entries) {
                            FilterChip(
                                selected = sortMode == sort,
                                onClick = { sortMode = sort },
                                label = { Text(stringRes(sort.labelRes)) },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        DrsTextButton(
                            onClick = { refresh() },
                            icon = Icons.Outlined.Refresh,
                            text = stringRes(R.string.packages__refresh),
                        )
                    }

                    for (pkg in filtered) {
                        PackageCard(
                            pkg = pkg,
                            extensionIndex = extensionIndex,
                            progress = progress.takeIf { it.pkgId == pkg.id && it.phase != DrsPackageManager.InstallProgress.Phase.IDLE },
                            onInstall = { extensionManager.let { DrsPackageManager.installPackage(context, pkg, it) } },
                            onRemove = {
                                val removed = DrsPackageManager.removePackage(pkg.id, extensionManager)
                                context.showLongToastSync(
                                    if (removed.isSuccess) R.string.packages__remove_success else R.string.packages__error_install,
                                )
                            },
                        )
                    }

                    if (filtered.isEmpty()) {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            text = stringRes(R.string.packages__search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private sealed interface FetchState {
    data object Loading : FetchState
    data class Ready(val packages: List<RemotePackage>) : FetchState
    data class Failed(val message: String) : FetchState
}

@Composable
private fun PackageCard(
    pkg: RemotePackage,
    extensionIndex: List<com.drs.smartkeyboard.lib.ext.Extension>,
    progress: DrsPackageManager.InstallProgress?,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    // Status is computed directly against the live extension index (cheap lookup).
    val installedVersion = extensionIndex.find { it.meta.id == pkg.id }?.meta?.version
    val status = when {
        DrsUpdateCenter.compareVersions(current = com.drs.smartkeyboard.BuildConfig.VERSION_NAME, latest = pkg.minAppVersion) < 0 ->
            PackageStatus.INCOMPATIBLE
        installedVersion == null -> PackageStatus.AVAILABLE
        DrsUpdateCenter.compareVersions(current = installedVersion, latest = pkg.version) < 0 ->
            PackageStatus.UPDATE_AVAILABLE
        else -> PackageStatus.INSTALLED
    }

    DrsOutlinedBox(modifier = Modifier.defaultDrsOutlinedBox()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Header: palette swatch + name + type
            Row(verticalAlignment = Alignment.CenterVertically) {
                PaletteSwatch(colors = pkg.colors)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pkg.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = typeLabel(pkg.type) + " · v" + pkg.version,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(status = status)
            }
            if (pkg.description.isNotBlank()) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = pkg.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // DRS M3.2 — the trust badge, computed from the REAL catalog
            // fields (signature + key id) via the same classifier the
            // fail-closed catalog gate uses, so badge and gate never drift.
            Spacer(Modifier.size(4.dp))
            TrustBadge(trust = DrsTrustState.of(pkg.signature, pkg.pubkeyId))
            Spacer(Modifier.size(6.dp))
            Text(
                text = buildString {
                    append(formatBytes(pkg.sizeBytes))
                    if (pkg.releaseDate.isNotBlank()) append("  ·  " + pkg.releaseDate)
                    if (pkg.author.isNotBlank()) append("  ·  " + pkg.author)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Progress while busy
            if (progress != null && progress.phase in setOf(
                    DrsPackageManager.InstallProgress.Phase.DOWNLOADING,
                    DrsPackageManager.InstallProgress.Phase.VERIFYING,
                    DrsPackageManager.InstallProgress.Phase.INSTALLING,
                )
            ) {
                Spacer(Modifier.size(10.dp))
                val fraction = if (progress.total > 0) (progress.received.toFloat() / progress.total).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(4.dp))
                Text(
                    text = when (progress.phase) {
                        DrsPackageManager.InstallProgress.Phase.VERIFYING -> stringRes(R.string.packages__verifying)
                        DrsPackageManager.InstallProgress.Phase.INSTALLING -> stringRes(R.string.packages__installing)
                        else -> "${formatBytes(progress.received)} / ${formatBytes(progress.total)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (progress?.phase == DrsPackageManager.InstallProgress.Phase.FAILED) {
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringRes(R.string.packages__error_install),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (progress?.phase == DrsPackageManager.InstallProgress.Phase.DONE) {
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringRes(R.string.packages__install_success),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when (status) {
                    PackageStatus.AVAILABLE, PackageStatus.UPDATE_AVAILABLE -> {
                        val busy = progress != null && progress.phase in setOf(
                            DrsPackageManager.InstallProgress.Phase.DOWNLOADING,
                            DrsPackageManager.InstallProgress.Phase.VERIFYING,
                            DrsPackageManager.InstallProgress.Phase.INSTALLING,
                        )
                        DrsButton(
                            onClick = onInstall,
                            icon = Icons.Outlined.CloudDownload,
                            text = if (status == PackageStatus.UPDATE_AVAILABLE) {
                                stringRes(R.string.packages__update)
                            } else {
                                stringRes(R.string.packages__install)
                            },
                            enabled = !busy,
                        )
                    }
                    PackageStatus.INSTALLED -> {
                        DrsTextButton(
                            onClick = onRemove,
                            icon = Icons.Outlined.DeleteOutline,
                            text = stringRes(R.string.packages__remove),
                        )
                    }
                    PackageStatus.INCOMPATIBLE -> {}
                }
            }
            if (status == PackageStatus.INCOMPATIBLE) {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringRes(R.string.packages__needs_newer_app, "version" to pkg.minAppVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PaletteSwatch(colors: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp), verticalAlignment = Alignment.CenterVertically) {
        colors.take(4).forEach { hex ->
            val color = runCatching {
                Color(android.graphics.Color.parseColor(hex))
            }.getOrDefault(MaterialTheme.colorScheme.primary)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(color, CircleShape),
            )
        }
    }
}

@Composable
private fun StatusBadge(status: PackageStatus) {
    val (textRes, color) = when (status) {
        PackageStatus.AVAILABLE -> R.string.packages__status_available to MaterialTheme.colorScheme.secondary
        PackageStatus.INSTALLED -> R.string.packages__status_installed to MaterialTheme.colorScheme.primary
        PackageStatus.UPDATE_AVAILABLE -> R.string.packages__status_update to MaterialTheme.colorScheme.tertiary
        PackageStatus.INCOMPATIBLE -> R.string.packages__status_incompatible to MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringRes(textRes),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/** DRS M3.2 — the colored trust badge, from the same classifier as the gate. */
@Composable
private fun TrustBadge(trust: DrsTrustState) {
    val (textRes, color) = when (trust) {
        DrsTrustState.VERIFIED -> R.string.drs__trust__verified to MaterialTheme.colorScheme.primary
        DrsTrustState.UNKNOWN_KEY -> R.string.drs__trust__unknown_key to MaterialTheme.colorScheme.tertiary
        DrsTrustState.UNSIGNED -> R.string.drs__trust__unsigned to MaterialTheme.colorScheme.error
        DrsTrustState.INVALID_SIG -> R.string.drs__trust__invalid_sig to MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = stringRes(textRes),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun typeLabel(type: String): String = when (type) {
    "ime/theme" -> stringRes(R.string.packages__type_theme)
    "ime/keyboard" -> stringRes(R.string.packages__type_keyboard)
    "ime/languagepack" -> stringRes(R.string.packages__type_languagepack)
    else -> type
}
