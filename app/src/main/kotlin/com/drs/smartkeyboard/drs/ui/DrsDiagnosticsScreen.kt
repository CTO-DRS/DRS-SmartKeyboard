/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.BuildConfig
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.drs.DrsCrashHandler
import com.drs.smartkeyboard.drs.DrsBackup
import com.drs.smartkeyboard.drs.DrsDailyStats
import com.drs.smartkeyboard.drs.DrsEventLog
import com.drs.smartkeyboard.drs.DrsShortcuts
import com.drs.smartkeyboard.drs.DrsEconomy
import com.drs.smartkeyboard.drs.DrsPerformance
import com.drs.smartkeyboard.drs.DrsHybridViewMode
import com.drs.smartkeyboard.drs.DrsProfileManager
import com.drs.smartkeyboard.drs.DrsRewardCatalog
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsState
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.DrsToolView
import com.drs.smartkeyboard.drs.DrsUnified
import com.drs.smartkeyboard.drs.DrsUnifiedTools
import com.drs.smartkeyboard.ime.input.DrsSoundStyle
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import com.drs.smartkeyboard.lib.util.InputMethodUtils
import com.drs.smartkeyboard.appContext
import com.drs.smartkeyboard.themeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.drs.lib.android.showShortToastSync
import org.drs.lib.android.systemVibratorOrNull
import org.drs.lib.compose.stringRes

/**
 * DRS Keyboard Diagnostics: in-app self checks for the IME status, service
 * wiring, layouts, themes, profiles, shortcuts, clipboard state and a
 * sanitized local crash log viewer. Also hosts the privacy "clear local DRS
 * data" action. No typed user content is ever collected here.
 */
@Composable
fun DrsDiagnosticsScreen() = DrsScreen {
    title = stringRes(R.string.drs__diagnostics__title)
    navigationIconVisible = true
    previewFieldVisible = false

    val context = LocalContext.current
    val appContext by context.appContext()
    val prefs by DrsPreferenceStore

    val isEnabled by InputMethodUtils.observeIsDrsKeyboardEnabled(foregroundOnly = false)
    val isSelected by InputMethodUtils.observeIsDrsKeyboardSelected(foregroundOnly = false)
    val drsState by DrsStore.state.collectAsState()

    var showClearDataDialog by remember { mutableStateOf(false) }
    val crashLog = remember { DrsCrashHandler.readLog() }
    val navController = com.drs.smartkeyboard.app.LocalNavController.current

    val layoutsAvailable = remember {
        try {
            appContext.assets.list("ime/keyboard/org.drs.layouts/layouts/characters").orEmpty().isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }
    val themesAvailable = remember {
        try {
            appContext.assets.list("ime/theme/org.drs.themes/stylesheets").orEmpty().isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }
    val drsThemesAvailable = remember {
        try {
            appContext.assets.list("ime/theme/org.drs.themes.drs/stylesheets").orEmpty().size >= 8
        } catch (_: Throwable) {
            false
        }
    }
    val soundPacksAvailable = remember {
        try {
            DrsSoundStyle.entries.firstNotNullOfOrNull { it.resId }?.let { resId ->
                appContext.resources.openRawResource(resId).use { true }
            } ?: false
        } catch (_: Throwable) {
            false
        }
    }
    // DRS v1.1.0: real free-space probe of the storage volume that holds
    // the DRS state file, clipboard media and the exported backups. A
    // failing probe never raises a false alarm.
    val storageAvailableBytes = remember {
        try {
            android.os.StatFs(appContext.filesDir.path).availableBytes
        } catch (_: Throwable) {
            Long.MAX_VALUE
        }
    }

    // DRS v1.3.0: real battery / power-save state of the device. A power-
    // saving system may throttle background work of the IME (suggestions,
    // adaptation flush) — that is degraded-but-usable, so it is a warning.
    // An unreadable battery level is unknown, never a false alarm.
    val batteryPercent = remember {
        try {
            val bm = appContext.getSystemService(android.content.Context.BATTERY_SERVICE)
                as android.os.BatteryManager
            bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (_: Throwable) {
            -1
        }
    }
    val powerSaveMode = remember {
        try {
            val pm = appContext.getSystemService(android.content.Context.POWER_SERVICE)
                as android.os.PowerManager
            pm.isPowerSaveMode
        } catch (_: Throwable) {
            false
        }
    }

    // DRS v1.3.0: whether the device supports per-step vibration amplitude
    // control. When absent, the configured haptic strength silently falls
    // back to the system default amplitude (the settings key still works,
    // just without fine-grained steps) — degraded, so a warning. Null =
    // unknown (no vibrator / failed probe) and must not false-alarm.
    val amplitudeControlAvailable = remember {
        try {
            appContext.systemVibratorOrNull()?.hasAmplitudeControl()
        } catch (_: Throwable) {
            null
        }
    }

    // DRS v1.3.0: whether the ACTIVE theme loaded cleanly. A load failure
    // means the keyboard falls back to the base theme — degraded, warning.
    val activeThemeLoadFailure = remember {
        try {
            appContext.themeManager().value.activeThemeInfo.value.loadFailure != null
        } catch (_: Throwable) {
            false
        }
    }

    // DRS v1.4.0: real p95 key latency from the in-process performance
    // window. A verdict is meaningful only once enough samples exist —
    // with fewer, the state is unknown and must not false-alarm.
    val typingLatencyHealthy = remember {
        try {
            !DrsPerformance.hasSamples(10) ||
                DrsPerformance.percentileMicros(95) <= LATENCY_PASS_MICROS
        } catch (_: Throwable) {
            true
        }
    }

    // DRS v1.4.0: Java-heap pressure of this process, straight from the
    // runtime. An unreadable maximum (0) passes — degraded-but-usable at
    // worst, so this check warns and never errors.
    val memoryPressureHealthy = remember {
        try {
            val snap = DrsPerformance.memorySnapshot()
            snap.javaHeapMaxBytes <= 0L ||
                snap.javaHeapUsedBytes * 100L / snap.javaHeapMaxBytes < MEMORY_WARN_PERCENT
        } catch (_: Throwable) {
            true
        }
    }

    // DRS v1.5.0: whether the system spell checker points at THIS app.
    // The spell-checker service (DrsSpellCheckerService) only receives
    // queries when it is both enabled and selected system-wide — the same
    // Settings.Secure keys the spell-checker settings screen reads.
    val spellCheckerWired = remember {
        try {
            val enabled = android.provider.Settings.Secure.getString(
                appContext.contentResolver,
                "spell_checker_enabled",
            )
            val selected = android.provider.Settings.Secure.getString(
                appContext.contentResolver,
                "selected_spell_checker",
            )
            enabled != "1" ||
                android.content.ComponentName.unflattenFromString(selected.orEmpty())
                    ?.packageName == appContext.packageName
        } catch (_: Throwable) {
            true
        }
    }

    // DRS v1.5.0: the update notifications preference silently does
    // nothing on Android 13+ when POST_NOTIFICATIONS is denied — the
    // grant state becomes a real diagnostic, not a guess. Older Android
    // versions need no grant, so they pass.
    val notificationsHealthy = remember {
        try {
            if (android.os.Build.VERSION.SDK_INT < 33) {
                true
            } else {
                appContext.checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    !prefs.updates.notifyOnUpdate.get()
            }
        } catch (_: Throwable) {
            true
        }
    }

    // DRS v1.5.0: haptics configuration sanity — when the settings ask for
    // direct vibrator control but the device has no vibrator, every key
    // press silently skips haptic feedback. A failing probe is unknown
    // and must not false-alarm.
    val hapticsHealthy = remember {
        try {
            if (!prefs.inputFeedback.hapticEnabled.get()) {
                true
            } else {
                val wantsDirectVibrator =
                    com.drs.smartkeyboard.ime.input.HapticVibrationMode.USE_VIBRATOR_DIRECTLY ==
                        prefs.inputFeedback.hapticVibrationMode.get()
                !wantsDirectVibrator || appContext.systemVibratorOrNull() != null
            }
        } catch (_: Throwable) {
            true
        }
    }

    // ---------------- DRS v1.0.6: full technical test runner ----------------
    var testResults by remember { mutableStateOf<List<DrsTestResult>?>(null) }
    fun runFullTest() {
        testResults = computeDrsFullTest(
            imeEnabled = isEnabled,
            imeSelected = isSelected,
            prefsLoaded = appContext.preferenceStoreLoaded.value,
            profileReady = drsState.profiles.any { it.path == drsState.userPath },
            tuningMatch = run {
                val active = DrsProfileManager.activeProfile(drsState)
                active?.path == drsState.userPath || active?.path == "CUSTOM"
            },
            layoutsAvailable = layoutsAvailable,
            themesAvailable = themesAvailable,
            drsThemesAvailable = drsThemesAvailable,
            soundPacksAvailable = soundPacksAvailable,
            onboardingDone = drsState.onboardingDone,
            shortcutsUnique = drsState.shortcuts.map { it.shortcut }.distinct().size == drsState.shortcuts.size,
            profileThemesValid = drsState.profiles.all { profile ->
                try {
                    ExtensionComponentName.from(profile.dayThemeId)
                    ExtensionComponentName.from(profile.nightThemeId)
                    true
                } catch (_: Throwable) {
                    false
                }
            },
            storeHealthy = DrsStore.storageHealthy(),
            activeProfileOk = DrsProfileManager.activeProfile(drsState) != null,
            walletHealthy = drsState.wallet.ledger.size <= 24 &&
                DrsEconomy.balanceOf(drsState.wallet, DrsSystems.specOfName(drsState.userPath).path) >= 0,
            eventErrorCount = DrsEventLog.snapshot().count { it.level == DrsEventLog.Level.ERROR },
            unifiedStateConsistent = {
                val validView = DrsHybridViewMode.entries.any { it.name == drsState.hybridViewMode }
                val knownToolIds = DrsUnifiedTools.ALL.map { it.id }.toSet()
                val orderKnown = drsState.unifiedToolOrder.all { it in knownToolIds }
                val viewsValid = drsState.unifiedToolViews.all { (id, view) ->
                    id in knownToolIds && DrsToolView.entries.any { it.name == view }
                }
                val hiddenKnown = drsState.hiddenUnifiedTools.all { it in knownToolIds }
                val pinnedKnown = drsState.pinnedUnifiedTools.all { it in knownToolIds }
                validView && orderKnown && viewsValid && hiddenKnown && pinnedKnown
            }(),
            // DRS v1.0.8: the daily usage buckets must stay bounded, keyed
            // by valid ISO days and non-negative — a corrupted state file
            // degrades to a warning, never a crash.
            dailyStatsSane = DrsDailyStats.isSane(drsState.dailyStats),
            // DRS v1.1.0: real storage headroom + last-backup age.
            storageAvailableBytes = storageAvailableBytes,
            lastBackupAt = drsState.lastBackupAt,
            // DRS v1.3.0: device power state + hardware capability + theme health.
            // A capacity <= 0 means "unknown" (BatteryManager returns 0 or
            // MIN_VALUE when the property is unavailable) — never a false alarm.
            batteryHealthy = batteryPercent <= 0 || (!powerSaveMode && batteryPercent > BATTERY_WARN_PERCENT),
            amplitudeControlAvailable = amplitudeControlAvailable,
            activeThemeLoadFailure = activeThemeLoadFailure,
            // DRS v1.4.0: real latency/memory verdicts from this process.
            typingLatencyHealthy = typingLatencyHealthy,
            memoryPressureHealthy = memoryPressureHealthy,
            // DRS v1.5.0: spell checker wiring + notification grant + haptics
            // configuration sanity.
            spellCheckerWired = spellCheckerWired,
            notificationsHealthy = notificationsHealthy,
            hapticsHealthy = hapticsHealthy,
            // DRS v1.6.0: state-file decode integrity (the silent-wipe
            // detector) + whether a crash log from a previous run exists.
            stateFileParses = DrsStore.stateFileParses(),
            crashLogPresent = crashLog.isNotBlank(),
            // DRS v1.7.0: shortcut template typos — {dat] or {Datee} in an
            // expansion used to commit literally with zero signal. Null
            // (no templates at all) degrades to a pass, never a false alarm.
            shortcutTemplatesValid = DrsShortcuts.templatesValid(drsState),
        )
    }

    content {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrsDiagCard(title = stringRes(R.string.drs__diagnostics__ime_section)) {
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_ime_enabled),
                    passed = isEnabled,
                )
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_ime_selected),
                    passed = isSelected,
                )
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_prefs_loaded),
                    passed = appContext.preferenceStoreLoaded.value,
                )
            }

            // ---------------- full technical test (DRS v1.0.6) ----------------
            DrsDiagCard(title = stringRes(R.string.drs__diagnostics__test_section)) {
                OutlinedButton(onClick = { runFullTest() }) {
                    Text(stringRes(R.string.drs__diagnostics__test_run))
                }
                testResults?.let { results ->
                    val passed = results.count { it.severity == DrsTestSeverity.PASS }
                    val warnings = results.count { it.severity == DrsTestSeverity.WARNING }
                    val errors = results.count { it.severity == DrsTestSeverity.ERROR }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringRes(
                            R.string.drs__diagnostics__test_summary,
                            "passed" to passed.toString(),
                            "warnings" to warnings.toString(),
                            "errors" to errors.toString(),
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            errors > 0 -> MaterialTheme.colorScheme.error
                            warnings > 0 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    results.forEach { result ->
                        TestResultRow(result)
                    }
                }
            }

            // ---------------- active system ----------------
            val activeSystem = DrsSystems.specOfName(drsState.userPath)
            DrsDiagCard(title = stringRes(R.string.drs__diagnostics__system_section)) {
                Text(
                    text = stringRes(pathTitleRes(activeSystem)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringRes(activeSystem.taglineRes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__system_profile_ready),
                    passed = drsState.profiles.any { it.path == drsState.userPath },
                )
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__system_tuning_match),
                    passed = run {
                        val active = DrsProfileManager.activeProfile(drsState)
                        active?.path == drsState.userPath || active?.path == "CUSTOM"
                    },
                )
            }

            DrsDiagCard(title = stringRes(R.string.drs__diagnostics__layouts_section)) {
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_character_layouts),
                    passed = layoutsAvailable,
                )
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_theme_assets),
                    passed = themesAvailable,
                )
            }

            DrsDiagCard(title = stringRes(R.string.drs__diagnostics__extended_section)) {
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_drs_themes),
                    passed = drsThemesAvailable,
                )
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_sound_packs),
                    passed = soundPacksAvailable,
                )
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_onboarding),
                    passed = drsState.onboardingDone,
                )
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_shortcuts_unique),
                    passed = drsState.shortcuts.map { it.shortcut }.distinct().size == drsState.shortcuts.size,
                )
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_profile_themes),
                    passed = drsState.profiles.all { profile ->
                        try {
                            ExtensionComponentName.from(profile.dayThemeId)
                            ExtensionComponentName.from(profile.nightThemeId)
                            true
                        } catch (_: Throwable) {
                            false
                        }
                    },
                )
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_store_file),
                    passed = DrsStore.storageHealthy(),
                )
            }

            DrsDiagCard(title = stringRes(R.string.drs__diagnostics__state_section)) {
                Text(
                    text = stringRes(
                        R.string.drs__diagnostics__profiles_count,
                        "count" to drsState.profiles.size.toString(),
                    ),
                    fontSize = 14.sp,
                )
                Text(
                    text = stringRes(
                        R.string.drs__diagnostics__shortcuts_count,
                        "count" to drsState.shortcuts.size.toString(),
                    ),
                    fontSize = 14.sp,
                )
                Text(
                    text = stringRes(
                        R.string.drs__diagnostics__keypress_count,
                        "count" to drsState.usage.keyPresses.toString(),
                    ),
                    fontSize = 14.sp,
                )
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_active_profile),
                    passed = DrsProfileManager.activeProfile(drsState) != null,
                )
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_clipboard_history),
                    passed = prefs.clipboard.historyEnabled.get(),
                )
                DrsCheckRow(
                    label = stringRes(R.string.drs__diagnostics__check_wallet),
                    passed = drsState.wallet.ledger.size <= 24 &&
                        DrsEconomy.balanceOf(drsState.wallet, DrsSystems.specOfName(drsState.userPath).path) >= 0,
                )
            }

            DrsDiagCard(title = stringRes(R.string.drs__diagnostics__info_section)) {
                Text(
                    text = "DRS Keyboard V" + BuildConfig.VERSION_NAME +
                        " (" + BuildConfig.VERSION_CODE + ")",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringRes(R.string.drs__about__release_tagline),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                DrsRewardCatalog.itemOf(drsState.wallet.equippedTitle)?.let { item ->
                    Text(
                        text = stringRes(
                            R.string.drs__diagnostics__equipped_title,
                            "{title}" to stringRes(item.titleRes),
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                DrsRewardCatalog.itemOf(drsState.wallet.equippedBadge)?.let { item ->
                    Text(
                        text = stringRes(
                            R.string.drs__diagnostics__equipped_badge,
                            "{badge}" to stringRes(item.titleRes),
                        ),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    text = "Android SDK " + Build.VERSION.SDK_INT,
                    fontSize = 14.sp,
                )
            }

            DrsDiagCard(title = stringRes(R.string.drs__diagnostics__crash_section)) {
                if (crashLog.isBlank()) {
                    Text(
                        text = stringRes(R.string.drs__diagnostics__crash_empty),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = crashLog,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { DrsCrashHandler.clearLog() }) {
                        Text(stringRes(R.string.drs__diagnostics__crash_clear))
                    }
                }
            }

            // ---------------- sanitized event log (DRS v1.0.6) ----------------
            DrsDiagCard(title = stringRes(R.string.drs__diagnostics__events_section)) {
                val eventEntries = remember { DrsEventLog.snapshot() }
                if (eventEntries.isEmpty()) {
                    Text(
                        text = stringRes(R.string.drs__diagnostics__events_empty),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // DRS fix (v1.0.9): the fixed height MUST come
                            // before the scroll modifier — the scroll node has
                            // to receive bounded constraints, otherwise it
                            // crashes when measured inside DrsScreen's own
                            // vertical scroll container.
                            .height(180.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        eventEntries.forEach { entry ->
                            Text(
                                text = eventLine(entry),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = when (entry.level) {
                                    DrsEventLog.Level.ERROR -> MaterialTheme.colorScheme.error
                                    DrsEventLog.Level.WARNING -> MaterialTheme.colorScheme.tertiary
                                    DrsEventLog.Level.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                Row {
                    OutlinedButton(onClick = { DrsEventLog.clear() }) {
                        Text(stringRes(R.string.drs__diagnostics__events_clear))
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { navController.navigate(com.drs.smartkeyboard.app.Routes.Settings.DrsPerformance) }) {
                        Text(stringRes(R.string.drs__performance__title))
                    }
                }
                // DRS v1.7.0: the renderReport() renderer existed since v1.0.6
                // but NOTHING ever called it — the report is now actually
                // exportable (same SAF pattern as the stats CSV above).
                val reportPayload = remember { mutableStateOf<String?>(null) }
                val reportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/plain"),
                ) { uri ->
                    val payload = reportPayload.value
                    if (uri != null && payload != null) {
                        val ok = runCatching {
                            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(payload.toByteArray(Charsets.UTF_8))
                            } ?: throw IllegalStateException("output stream null")
                        }.isSuccess
                        appContext.showShortToastSync(
                            if (ok) R.string.drs__diagnostics__report_export_done
                            else R.string.drs__diagnostics__report_export_failed,
                        )
                    }
                    reportPayload.value = null
                }
                OutlinedButton(onClick = {
                    val results = testResults
                    val summary = if (results == null) {
                        "not run"
                    } else {
                        "pass=%d warn=%d error=%d".format(
                            java.util.Locale.US,
                            results.count { it.severity == DrsTestSeverity.PASS },
                            results.count { it.severity == DrsTestSeverity.WARNING },
                            results.count { it.severity == DrsTestSeverity.ERROR },
                        )
                    }
                    reportPayload.value = DrsEventLog.renderReport(
                        BuildConfig.VERSION_NAME,
                        summary,
                    )
                    reportLauncher.launch("drs-diagnostic-report.txt")
                }) {
                    Text(stringRes(R.string.drs__diagnostics__events_export))
                }
                Text(
                    text = stringRes(R.string.drs__diagnostics__events_privacy),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------------- DRS data backup / restore (DRS v1.0.6) --------
            DrsDiagCard(title = stringRes(R.string.drs__diagnostics__backup_section)) {
                Text(
                    text = stringRes(R.string.drs__diagnostics__backup_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // DRS v1.1.0: real "last backup" timestamp from the state.
                Text(
                    text = if (drsState.lastBackupAt > 0L) {
                        val formatted = java.text.DateFormat.getDateTimeInstance(
                            java.text.DateFormat.MEDIUM,
                            java.text.DateFormat.SHORT,
                        ).format(java.util.Date(drsState.lastBackupAt))
                        stringRes(R.string.drs__diagnostics__backup_last, "when" to formatted)
                    } else {
                        stringRes(R.string.drs__diagnostics__backup_never)
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val exportState = remember { mutableStateOf<String?>(null) }
                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json"),
                ) { uri ->
                    val payload = exportState.value
                    if (uri != null && payload != null) {
                        runCatching {
                            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(payload.toByteArray(Charsets.UTF_8))
                            } ?: throw IllegalStateException("output stream null")
                            DrsEventLog.recordInfo(DrsEventLog.Categories.STORE, "backup exported")
                            // DRS v1.1.0: stamp the real export moment so
                            // the backup-age check reflects this artifact.
                            DrsUnified.markBackupExported()
                        }
                    }
                    exportState.value = null
                }
                OutlinedButton(onClick = {
                    exportState.value = DrsBackup.exportJson()
                    exportLauncher.launch(DrsBackup.defaultFileName())
                }) {
                    Text(stringRes(R.string.drs__diagnostics__backup_export))
                }
                var importError by remember { mutableStateOf<String?>(null) }
                // DRS v1.7.0: describe-before-restore — the file is parsed
                // FIRST and a confirm dialog shows what it contains; the
                // import used to overwrite live state with zero preview.
                var pendingImport by remember { mutableStateOf<DrsState?>(null) }
                var pendingParseFailed by remember { mutableStateOf(false) }
                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        val parsed = DrsBackup.parseFrom(appContext, uri)
                        if (parsed == null) {
                            pendingParseFailed = true
                        } else {
                            pendingImport = parsed
                        }
                    }
                }
                OutlinedButton(onClick = { importError = null; pendingParseFailed = false; importLauncher.launch(arrayOf("application/json")) }) {
                    Text(stringRes(R.string.drs__diagnostics__backup_import))
                }
                pendingImport?.let { parsed ->
                    val preview = DrsBackup.describeBackup(parsed)
                    AlertDialog(
                        onDismissRequest = { pendingImport = null },
                        title = { Text(stringRes(R.string.drs__diagnostics__backup_preview_title)) },
                        text = {
                            Column {
                                Text(stringRes(R.string.drs__diagnostics__backup_preview_shortcuts, "count" to preview.shortcuts.toString()))
                                Text(stringRes(R.string.drs__diagnostics__backup_preview_profiles, "count" to preview.profiles.toString()))
                                Text(stringRes(R.string.drs__diagnostics__backup_preview_wallet, "points" to preview.walletTotal.toString()))
                                Text(stringRes(R.string.drs__diagnostics__backup_preview_days, "days" to preview.daysRecorded.toString()))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringRes(R.string.drs__diagnostics__backup_preview_warn),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val result = DrsBackup.importParsed(parsed)
                                importError = result.errorRes?.let { res -> appContext.getString(res) }
                                pendingImport = null
                            }) {
                                Text(stringRes(R.string.drs__diagnostics__backup_preview_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingImport = null }) {
                                Text(stringRes(R.string.drs__diagnostics__backup_preview_cancel))
                            }
                        },
                    )
                }
                if (pendingParseFailed) {
                    Text(
                        text = appContext.getString(R.string.drs__diagnostics__backup_error_invalid),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                importError?.let { message ->
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            DrsDiagCard(title = stringRes(R.string.drs__diagnostics__privacy_section)) {
                Text(
                    text = stringRes(R.string.drs__diagnostics__privacy_note),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { showClearDataDialog = true }) {
                    Text(stringRes(R.string.drs__diagnostics__clear_data))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                title = { Text(stringRes(R.string.drs__diagnostics__clear_data)) },
                text = { Text(stringRes(R.string.drs__diagnostics__clear_data_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        showClearDataDialog = false
                        resetDrsData()
                    }) {
                        Text(stringRes(R.string.action__ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDataDialog = false }) {
                        Text(stringRes(R.string.action__cancel))
                    }
                },
            )
        }
    }
}

private fun resetDrsData() {
    CoroutineScope(Dispatchers.IO).launch {
        DrsStore.resetAll()
    }
}

@Composable
private fun DrsDiagCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer.copy(alpha = 0.6f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // DRS: a small primary accent bar leading the section title —
                // consistent with the group headers across the app.
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .background(colorScheme.primary, RoundedCornerShape(2.dp)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                )
            }
            content()
        }
    }
}

@Composable
private fun DrsCheckRow(label: String, passed: Boolean) {
    Row {
        Text(
            text = if (passed) "✓" else "✗",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (passed) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Spacer(modifier = Modifier.height(0.dp))
        Text(
            text = "  $label",
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 0.dp),
        )
    }
}

// ---------------- DRS v1.0.6: full technical test runner ----------------

private enum class DrsTestSeverity { PASS, WARNING, ERROR }

/** DRS v1.1.0: free-storage thresholds for check #20. */
private const val STORAGE_PASS_BYTES = 200L * 1024 * 1024
private const val STORAGE_ERROR_BYTES = 50L * 1024 * 1024

/** DRS v1.1.0: a backup exported within 30 days keeps check #21 green. */
private const val BACKUP_PASS_MILLIS = 30L * 24 * 60 * 60 * 1000

/** DRS v1.3.0: below this battery percentage (or in power-save mode) the
 * power checks warn that the system may throttle the IME. */
private const val BATTERY_WARN_PERCENT = 20

/** DRS v1.4.0: at or below this p95 key latency (50 ms) the latency check
 * passes; above it the keyboard still works, just measurably slower. */
private const val LATENCY_PASS_MICROS = 50_000L

/** DRS v1.4.0: the memory check warns once the Java heap is at or above
 * this share of its maximum — degraded-but-usable, so a warning at most. */
private const val MEMORY_WARN_PERCENT = 80L

private data class DrsTestResult(
    val labelRes: Int,
    val severity: DrsTestSeverity,
    val hintRes: Int?,
)

@Composable
private fun TestResultRow(result: DrsTestResult) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (result.severity) {
                    DrsTestSeverity.PASS -> "✓"
                    DrsTestSeverity.WARNING -> "!"
                    DrsTestSeverity.ERROR -> "✗"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = when (result.severity) {
                    DrsTestSeverity.PASS -> MaterialTheme.colorScheme.primary
                    DrsTestSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                    DrsTestSeverity.ERROR -> MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = "  " + stringRes(result.labelRes),
                fontSize = 13.sp,
            )
        }
        if (result.severity != DrsTestSeverity.PASS && result.hintRes != null) {
            Text(
                text = stringRes(result.hintRes),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp),
            )
        }
    }
}

/**
 * Severity rules for the full test: every input comes from a REAL check
 * already used elsewhere on this screen. Warnings mean "degraded but
 * usable - user action recommended"; errors mean "a core feature cannot
 * work". Nothing here is simulated.
 */
private fun computeDrsFullTest(
    imeEnabled: Boolean,
    imeSelected: Boolean,
    prefsLoaded: Boolean,
    profileReady: Boolean,
    tuningMatch: Boolean,
    layoutsAvailable: Boolean,
    themesAvailable: Boolean,
    drsThemesAvailable: Boolean,
    soundPacksAvailable: Boolean,
    onboardingDone: Boolean,
    shortcutsUnique: Boolean,
    profileThemesValid: Boolean,
    storeHealthy: Boolean,
    activeProfileOk: Boolean,
    walletHealthy: Boolean,
    eventErrorCount: Int,
    unifiedStateConsistent: Boolean,
    dailyStatsSane: Boolean,
    storageAvailableBytes: Long,
    lastBackupAt: Long,
    batteryHealthy: Boolean,
    amplitudeControlAvailable: Boolean?,
    activeThemeLoadFailure: Boolean,
    typingLatencyHealthy: Boolean,
    memoryPressureHealthy: Boolean,
    spellCheckerWired: Boolean,
    notificationsHealthy: Boolean,
    hapticsHealthy: Boolean,
    // DRS v1.6.0: state-file decode integrity + crash-log presence.
    stateFileParses: Boolean?,
    crashLogPresent: Boolean,
    // DRS v1.7.0: shortcut template validity (null = no templates).
    shortcutTemplatesValid: Boolean?,
): List<DrsTestResult> {
    fun result(pass: Boolean, warn: Boolean, label: Int, hint: Int): DrsTestResult =
        DrsTestResult(
            label,
            when {
                pass -> DrsTestSeverity.PASS
                warn -> DrsTestSeverity.WARNING
                else -> DrsTestSeverity.ERROR
            },
            if (pass) null else hint,
        )
    return listOf(
        result(imeEnabled, warn = true, R.string.drs__diagnostics__check_ime_enabled, R.string.drs__diagnostics__hint_ime),
        result(imeSelected, warn = true, R.string.drs__diagnostics__check_ime_selected, R.string.drs__diagnostics__hint_ime),
        result(prefsLoaded, warn = false, R.string.drs__diagnostics__check_prefs_loaded, R.string.drs__diagnostics__hint_restart),
        result(profileReady, warn = false, R.string.drs__diagnostics__system_profile_ready, R.string.drs__diagnostics__hint_profile),
        result(tuningMatch, warn = true, R.string.drs__diagnostics__system_tuning_match, R.string.drs__diagnostics__hint_tuning),
        result(layoutsAvailable, warn = false, R.string.drs__diagnostics__check_character_layouts, R.string.drs__diagnostics__hint_assets),
        result(themesAvailable, warn = false, R.string.drs__diagnostics__check_theme_assets, R.string.drs__diagnostics__hint_assets),
        result(drsThemesAvailable, warn = true, R.string.drs__diagnostics__check_drs_themes, R.string.drs__diagnostics__hint_assets),
        result(soundPacksAvailable, warn = true, R.string.drs__diagnostics__check_sound_packs, R.string.drs__diagnostics__hint_assets),
        result(onboardingDone, warn = true, R.string.drs__diagnostics__check_onboarding, R.string.drs__diagnostics__hint_onboarding),
        result(shortcutsUnique, warn = true, R.string.drs__diagnostics__check_shortcuts_unique, R.string.drs__diagnostics__hint_shortcuts),
        result(profileThemesValid, warn = false, R.string.drs__diagnostics__check_profile_themes, R.string.drs__diagnostics__hint_profile_themes),
        result(storeHealthy, warn = false, R.string.drs__diagnostics__check_store_file, R.string.drs__diagnostics__hint_store),
        result(activeProfileOk, warn = false, R.string.drs__diagnostics__check_active_profile, R.string.drs__diagnostics__hint_profile),
        result(walletHealthy, warn = true, R.string.drs__diagnostics__check_wallet, R.string.drs__diagnostics__hint_restart),
        result(eventErrorCount == 0, warn = true, R.string.drs__diagnostics__check_events_clean, R.string.drs__diagnostics__hint_events),
        // DRS v1.0.7: unified «كلاهما» layer consistency - unknown tool ids
        // or invalid level names in the customization are dropped by the
        // resolver, so this only ever degrades to a warning.
        result(
            unifiedStateConsistent,
            warn = true,
            R.string.drs__diagnostics__check_unified,
            R.string.drs__diagnostics__hint_unified,
        ),
        // DRS v1.0.8: daily usage statistics buckets sanity.
        result(
            dailyStatsSane,
            warn = true,
            R.string.drs__diagnostics__check_daily_stats,
            R.string.drs__diagnostics__hint_daily_stats,
        ),
        // DRS v1.1.0: real free space of the volume holding the state file.
        result(
            storageAvailableBytes >= STORAGE_PASS_BYTES,
            warn = storageAvailableBytes >= STORAGE_ERROR_BYTES,
            R.string.drs__diagnostics__check_storage,
            R.string.drs__diagnostics__hint_storage,
        ),
        // DRS v1.1.0: age of the last DRS-state backup export. This is
        // always at most a warning (missing protection, not a broken
        // feature): the hint points at the export button on this screen.
        result(
            lastBackupAt > 0L && System.currentTimeMillis() - lastBackupAt <= BACKUP_PASS_MILLIS,
            warn = true,
            R.string.drs__diagnostics__check_backup_age,
            R.string.drs__diagnostics__hint_backup_age,
        ),
        // DRS v1.3.0: battery level / power-save mode. The system may
        // throttle IME background work in power-save or at very low
        // charge — degraded-but-usable, so a warning at most. An unknown
        // battery level (failed read) passes to avoid false alarms.
        result(
            batteryHealthy,
            warn = true,
            R.string.drs__diagnostics__check_battery,
            R.string.drs__diagnostics__hint_battery,
        ),
        // DRS v1.3.0: vibration amplitude control capability. Without it
        // the configured haptic strength silently falls back to the
        // system's default amplitude. Unknown (no vibrator or failed
        // probe) passes so devices without a vibrator are not flagged.
        result(
            amplitudeControlAvailable != false,
            warn = true,
            R.string.drs__diagnostics__check_amplitude,
            R.string.drs__diagnostics__hint_amplitude,
        ),
        // DRS v1.3.0: the active theme loaded cleanly (no fallback to the
        // base theme).
        result(
            !activeThemeLoadFailure,
            warn = true,
            R.string.drs__diagnostics__check_active_theme,
            R.string.drs__diagnostics__hint_active_theme,
        ),
        // DRS v1.4.0: real p95 key latency from the in-process window.
        // Unknown (too few samples) passes so a fresh process never
        // false-alarms; above the threshold it is degraded-but-usable.
        result(
            typingLatencyHealthy,
            warn = true,
            R.string.drs__diagnostics__check_latency,
            R.string.drs__diagnostics__hint_latency,
        ),
        // DRS v1.4.0: Java-heap pressure of this process. An unreadable
        // maximum passes; high pressure means the system may slow the
        // keyboard down — degraded-but-usable, so a warning at most.
        result(
            memoryPressureHealthy,
            warn = true,
            R.string.drs__diagnostics__check_memory,
            R.string.drs__diagnostics__hint_memory,
        ),
        // DRS v1.5.0: the system spell checker must point at this app for
        // DrsSpellCheckerService to receive queries. A spell checker that
        // is off entirely is the user's choice — only the "enabled but
        // someone else selected" case degrades to a warning.
        result(
            spellCheckerWired,
            warn = true,
            R.string.drs__diagnostics__check_spell_checker,
            R.string.drs__diagnostics__hint_spell_checker,
        ),
        // DRS v1.5.0: update notifications need the Android-13+
        // POST_NOTIFICATIONS grant; a denied grant silently silences
        // them, so it warns only when update notifications are wanted.
        result(
            notificationsHealthy,
            warn = true,
            R.string.drs__diagnostics__check_notifications,
            R.string.drs__diagnostics__hint_notifications,
        ),
        // DRS v1.5.0: haptics requested with direct vibrator control but
        // no vibrator present means every key press silently skips the
        // haptic channel — a real misconfiguration, warning only.
        result(
            hapticsHealthy,
            warn = true,
            R.string.drs__diagnostics__check_haptics,
            R.string.drs__diagnostics__hint_haptics,
        ),
        // DRS v1.6.0: the persisted state file must still decode. A
        // corrupt file means the whole DRS layer silently resets to
        // defaults on the next write — a genuine ERROR, not a warning.
        // Unknown (store not initialized) passes to avoid false alarms.
        result(
            stateFileParses != false,
            warn = false,
            R.string.drs__diagnostics__check_state_file_integrity,
            R.string.drs__diagnostics__hint_state_file_integrity,
        ),
        // DRS v1.6.0: a recorded crash log from a previous run. Purely
        // informational — the keyboard recovered, but the user should
        // know (and can review/clear the log on this very screen).
        result(
            !crashLogPresent,
            warn = true,
            R.string.drs__diagnostics__check_crash_log,
            R.string.drs__diagnostics__hint_crash_log,
        ),
        // DRS v1.7.0: shortcut template typos. expandTemplate keeps
        // unknown {variables} literal, so {dat] or {Datee} silently
        // committed garbage on every expansion until now. Null (no
        // templates at all) passes to avoid false alarms.
        result(
            shortcutTemplatesValid != false,
            warn = true,
            R.string.drs__diagnostics__check_shortcut_templates,
            R.string.drs__diagnostics__hint_shortcut_templates,
        ),
    )
}

private fun eventLine(entry: DrsEventLog.Entry): String {
    val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
    return "${timeFormat.format(java.util.Date(entry.timestampMs))} [${entry.level}] ${entry.category}: ${entry.detail}"
}
