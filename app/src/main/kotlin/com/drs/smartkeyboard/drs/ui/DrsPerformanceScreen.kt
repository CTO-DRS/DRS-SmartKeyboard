/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.appContext
import com.drs.smartkeyboard.clipboardManager
import com.drs.smartkeyboard.drs.DrsPerformance
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.lib.compose.DrsScreen
import kotlinx.coroutines.delay
import org.drs.lib.compose.stringRes

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

/**
 * DRS v1.0.6: real-time performance monitor. Every figure here is MEASURED
 * from the running process - no estimates, no placeholders:
 *  - Engine response: actual key-handler durations sampled per press.
 *  - Memory: live Java and native heap usage.
 *  - Storage: the actual size of the DRS state file on disk.
 *  - Counters: the anonymous usage counters the DRS layer already keeps.
 *
 * Latency samples live in RAM only and reset when the keyboard process
 * restarts; the screen says so explicitly.
 */
@Composable
fun DrsPerformanceScreen() = DrsScreen {
    title = stringRes(R.string.drs__performance__title)
    navigationIconVisible = true
    previewFieldVisible = false

    val context = LocalContext.current
    val appContext by context.appContext()
    val clipboardManager by appContext.clipboardManager()
    val drsState by DrsStore.state.collectAsState()

    // Live refresh while the screen is visible - the numbers genuinely move.
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            tick++
            delay(2_000)
        }
    }
    val memory = remember(tick) { DrsPerformance.memorySnapshot() }
    val storeBytes = remember(tick) { DrsStore.fileSizeBytes() }
    val clipboardCount = remember(tick) { clipboardManager.historyFlow.value.all.size }

    content {
        // DRS fix (v1.0.9): DrsScreen already scrolls — a nested verticalScroll
        // here was measured with infinite height constraints and crashed the
        // screen on open.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------------- engine response ----------------
            PerfCard(title = stringRes(R.string.drs__performance__latency_section)) {
                val sampleCount = DrsPerformance.sampleCount()
                if (sampleCount == 0L) {
                    Text(
                        text = stringRes(R.string.drs__performance__latency_empty),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PerfRow(
                        label = stringRes(R.string.drs__performance__latency_avg),
                        value = "%.2f ms".format(DrsPerformance.averageMicros() / 1000.0),
                    )
                    PerfRow(
                        label = stringRes(R.string.drs__performance__latency_p95),
                        value = "%.2f ms".format(DrsPerformance.percentileMicros(95) / 1000.0),
                    )
                    PerfRow(
                        label = stringRes(R.string.drs__performance__latency_max),
                        value = "%.2f ms".format(DrsPerformance.maxMicros() / 1000.0),
                    )
                    PerfRow(
                        label = stringRes(R.string.drs__performance__latency_samples),
                        value = sampleCount.toString(),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringRes(R.string.drs__performance__latency_hint),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------------- memory ----------------
            PerfCard(title = stringRes(R.string.drs__performance__memory_section)) {
                PerfRow(
                    label = stringRes(R.string.drs__performance__memory_java_used),
                    value = formatBytes(memory.javaHeapUsedBytes),
                )
                PerfRow(
                    label = stringRes(R.string.drs__performance__memory_java_max),
                    value = formatBytes(memory.javaHeapMaxBytes),
                )
                PerfRow(
                    label = stringRes(R.string.drs__performance__memory_native),
                    value = formatBytes(memory.nativeHeapUsedBytes),
                )
            }

            // ---------------- storage ----------------
            PerfCard(title = stringRes(R.string.drs__performance__storage_section)) {
                PerfRow(
                    label = stringRes(R.string.drs__performance__storage_drs_file),
                    value = formatBytes(storeBytes),
                )
                PerfRow(
                    label = stringRes(R.string.drs__performance__storage_clipboard_items),
                    value = clipboardCount.toString(),
                )
            }

            // ---------------- usage counters ----------------
            PerfCard(title = stringRes(R.string.drs__performance__usage_section)) {
                val usage = drsState.usage
                PerfRow(
                    label = stringRes(R.string.drs__performance__usage_keys),
                    value = usage.keyPresses.toString(),
                )
                PerfRow(
                    label = stringRes(R.string.drs__performance__usage_tools),
                    value = usage.techToolUses.toString(),
                )
                PerfRow(
                    label = stringRes(R.string.drs__performance__usage_clipboard),
                    value = usage.clipboardUses.toString(),
                )
                PerfRow(
                    label = stringRes(R.string.drs__performance__usage_shortcuts),
                    value = usage.shortcutUses.toString(),
                )
                PerfRow(
                    label = stringRes(R.string.drs__performance__usage_gestures),
                    value = usage.gestureUses.toString(),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PerfCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun PerfRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
