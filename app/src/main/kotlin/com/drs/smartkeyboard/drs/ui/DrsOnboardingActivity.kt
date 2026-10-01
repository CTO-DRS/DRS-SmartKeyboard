/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs.ui

import com.drs.smartkeyboard.drs.DrsOnboardingState
import androidx.compose.runtime.LaunchedEffect
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.apptheme.DrsAppTheme
import com.drs.smartkeyboard.drs.DrsProfile
import com.drs.smartkeyboard.drs.DrsProfileManager
import com.drs.smartkeyboard.drs.DrsShortcuts
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystemSpec
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.DrsUserPath
import com.drs.smartkeyboard.ime.input.DrsSoundPlayer
import com.drs.smartkeyboard.ime.input.DrsSoundStyle
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import com.drs.smartkeyboard.lib.util.InputMethodUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.drs.lib.compose.DrsCanvasIcon
import org.drs.lib.compose.ProvideLocalizedResources
import org.drs.lib.compose.stringRes

/**
 * File-level scope for suspend preference writes performed by onboarding
 * steps (theme pair, sound style, feedback toggles). Prefs .set() calls are
 * suspend functions, so they must run inside a coroutine.
 */
private val onboardingPrefScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * A curated day+night theme pair offered during onboarding, rendered with
 * two color swatches so the user can preview the feel before applying.
 */
private data class DrsThemePair(
    val key: String,
    val dayThemeId: String,
    val nightThemeId: String,
    val daySwatch: Color,
    val nightSwatch: Color,
)

private object DrsThemePairs {
    const val EXT_ID = "org.drs.themes.drs"
    val ALL = listOf(
        DrsThemePair(
            key = "desert",
            dayThemeId = "drs_desert_day",
            nightThemeId = "drs_midnight",
            daySwatch = Color(0xFFC2A26B),
            nightSwatch = Color(0xFF1B2440),
        ),
        DrsThemePair(
            key = "ocean",
            dayThemeId = "drs_ocean_day",
            nightThemeId = "drs_amoled",
            daySwatch = Color(0xFF2E86AB),
            nightSwatch = Color(0xFF000000),
        ),
        DrsThemePair(
            key = "emerald",
            dayThemeId = "drs_emerald_day",
            nightThemeId = "drs_neon_night",
            daySwatch = Color(0xFF10B981),
            nightSwatch = Color(0xFF0F172A),
        ),
        DrsThemePair(
            key = "royal",
            dayThemeId = "drs_royal_day",
            nightThemeId = "drs_rose_night",
            daySwatch = Color(0xFF6D28D9),
            nightSwatch = Color(0xFF3B0A2A),
        ),
    )
}

/**
 * DRS onboarding: detects how the user will use the keyboard and prepares a
 * matching experience. Runs once on first launch; the chosen path can be
 * changed at any time afterwards from the DRS Control Center.
 *
 * Steps: welcome -> explanation -> user path -> preferences -> profile
 * summary -> theme pair -> key sounds -> shortcut samples -> typing test ->
 * IME activation -> done (deep link into Control Center).
 */
class DrsOnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Leaving early counts as skipping: never trap the user here.
                // DRS (B2): onboarding-critical write takes the durable path.
                DrsStore.update(immediate = true) { it.copy(onboardingDone = true) }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
        val prefs by DrsPreferenceStore
        val appTheme = prefs.other.settingsTheme.get()
        setContent {
            ProvideLocalizedResources(
                this,
                appName = R.string.app_name,
            ) {
                DrsAppTheme(theme = appTheme) {
                    DrsOnboardingRoot()
                }
            }
        }
    }
}

private enum class OnboardingStep {
    WELCOME, EXPLANATION, PATH, PREFERENCES, PROFILE, THEMES, SOUNDS, SHORTCUTS, TEST, ENABLE, DONE,
}

@Composable
private fun DrsOnboardingRoot() {
    val context = LocalContext.current
    // DRS M2.6 — the live resume: the last saved step comes from the
    // durable state (collected, not a stale snapshot), and every step
    // change persists through DrsOnboardingState so leaving mid-flow
    // resumes EXACTLY there — a completed onboarding never re-enters.
    val durableState by DrsStore.state.collectAsState()
    var step by remember {
        mutableIntStateOf(
            DrsOnboardingState.resumeStep(
                onboardingDone = durableState.onboardingDone,
                savedStep = durableState.onboardingStep,
                totalSteps = OnboardingStep.entries.size,
            ),
        )
    }
    var selectedPath by remember { mutableStateOf<DrsUserPath?>(null) }
    var numberRow by remember { mutableStateOf(false) }
    var wantSuggestions by remember { mutableStateOf(true) }
    var feedbackEnabled by remember { mutableStateOf(true) }
    var profileCreated by remember { mutableStateOf(false) }
    var selectedPair by remember { mutableStateOf<DrsThemePair?>(null) }
    var selectedSound by remember { mutableStateOf(DrsSoundStyle.SYSTEM) }
    var shortcutsAdded by remember { mutableStateOf(false) }

    val steps = OnboardingStep.entries
    val currentStep = steps[step.coerceIn(0, steps.lastIndex)]
    // DRS M2.6 — every step change persists immediately (durable path via
    // DrsOnboardingState.persistStep), so a kill or an early exit resumes
    // at the exact step, not the flow's start.
    LaunchedEffect(step) {
        DrsOnboardingState.persistStep(step, steps.size)
    }

    fun completeOnboarding() {
        // DRS (B2): onboarding-critical write takes the durable path — the
        // activity may be finished right after this.
        DrsStore.update(immediate = true) { it.copy(onboardingDone = true) }
        (context as? android.app.Activity)?.finish()
    }

    fun openControlCenter() {
        // DRS (B2): onboarding-critical write takes the durable path — the
        // activity may be finished right after this.
        DrsStore.update(immediate = true) { it.copy(onboardingDone = true) }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("ui://drs-keyboard/settings/drs/control-center"))
            intent.setPackage(context.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Throwable) {
            // Deep link failed for any reason - falling back to plain finish
            // is always safe; the user can open the Control Center manually.
        }
        (context as? android.app.Activity)?.finish()
    }

    fun applyPathAndProfile(path: DrsUserPath) {
        selectedPath = path
        // Activating a system provisions and applies its own dedicated
        // profile and stores the system as the user path in one step.
        DrsProfileManager.applySystem(path)
        profileCreated = true
    }

    fun applyThemePair(pair: DrsThemePair) {
        selectedPair = pair
        val prefs by DrsPreferenceStore
        onboardingPrefScope.launch {
            runCatching {
                prefs.theme.dayThemeId.set(ExtensionComponentName(DrsThemePairs.EXT_ID, pair.dayThemeId))
                prefs.theme.nightThemeId.set(ExtensionComponentName(DrsThemePairs.EXT_ID, pair.nightThemeId))
            }
        }
        // Keep the active profile in sync so the Profiles screen reflects
        // what the user picked here.
        updateActiveProfile {
            it.copy(dayThemeId = "${DrsThemePairs.EXT_ID}:${pair.dayThemeId}", nightThemeId = "${DrsThemePairs.EXT_ID}:${pair.nightThemeId}")
        }
    }

    fun applySoundStyle(style: DrsSoundStyle) {
        selectedSound = style
        val prefs by DrsPreferenceStore
        onboardingPrefScope.launch {
            runCatching { prefs.inputFeedback.drsSoundStyle.set(style) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ),
            )
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LinearProgressIndicator(
            progress = { (step + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth(),
        )

        when (currentStep) {
            OnboardingStep.WELCOME -> {
                DrsLogoHeader()
                Text(
                    text = stringRes(R.string.drs__onboarding__welcome_title),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringRes(R.string.drs__onboarding__welcome_body),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = { step += 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.action__continue))
                }
                OutlinedButton(onClick = { completeOnboarding() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.drs__onboarding__skip))
                }
            }

            OnboardingStep.EXPLANATION -> {
                Text(
                    text = stringRes(R.string.drs__onboarding__explanation_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ExplanationRow("1", stringRes(R.string.drs__onboarding__explanation_item_1))
                ExplanationRow("2", stringRes(R.string.drs__onboarding__explanation_item_2))
                ExplanationRow("3", stringRes(R.string.drs__onboarding__explanation_item_3))
                Text(
                    text = stringRes(R.string.drs__onboarding__how_it_works),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = { step += 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.action__continue))
                }
            }

            OnboardingStep.PATH -> {
                Text(
                    text = stringRes(R.string.drs__onboarding__path_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringRes(R.string.drs__onboarding__path_hint),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DrsSystems.ALL.forEach { spec ->
                    OnboardingSystemCard(
                        spec = spec,
                        selected = selectedPath == spec.path,
                        onClick = { applyPathAndProfile(spec.path) },
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { step += 1 },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = profileCreated,
                ) {
                    Text(stringRes(R.string.action__continue))
                }
            }

            OnboardingStep.PREFERENCES -> {
                Text(
                    text = stringRes(R.string.drs__onboarding__prefs_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                PrefToggleRow(
                    title = stringRes(R.string.drs__onboarding__prefs_number_row),
                    checked = numberRow,
                    onChange = { checked ->
                        numberRow = checked
                        updateActiveProfile { it.copy(numberRow = checked) }
                    },
                )
                PrefToggleRow(
                    title = stringRes(R.string.drs__onboarding__prefs_suggestions),
                    checked = wantSuggestions,
                    onChange = { checked ->
                        wantSuggestions = checked
                        updateActiveProfile { it.copy(suggestionsEnabled = checked) }
                    },
                )
                PrefToggleRow(
                    title = stringRes(R.string.drs__onboarding__prefs_feedback),
                    checked = feedbackEnabled,
                    onChange = { checked ->
                        feedbackEnabled = checked
                        updateActiveProfile {
                            it.copy(audioFeedbackEnabled = checked, hapticFeedbackEnabled = checked)
                        }
                    },
                )
                Text(
                    text = stringRes(R.string.drs__onboarding__profile_created),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = { step += 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.action__continue))
                }
            }

            OnboardingStep.PROFILE -> {
                Text(
                    text = stringRes(R.string.drs__onboarding__profile_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val drsStateLive by DrsStore.state.collectAsState()
                val active = DrsProfileManager.activeProfile(drsStateLive)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = active?.name ?: "-",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = stringRes(R.string.drs__onboarding__profile_body),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = { step += 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.action__continue))
                }
            }

            OnboardingStep.THEMES -> {
                Text(
                    text = stringRes(R.string.drs__onboarding__themes_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringRes(R.string.drs__onboarding__themes_hint),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DrsThemePairs.ALL.forEach { pair ->
                    ThemePairCard(
                        pair = pair,
                        label = stringRes(themePairLabel(pair.key)),
                        selected = selectedPair == pair,
                        onClick = { applyThemePair(pair) },
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = { step += 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.action__continue))
                }
            }

            OnboardingStep.SOUNDS -> {
                Text(
                    text = stringRes(R.string.drs__onboarding__sounds_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringRes(R.string.drs__onboarding__sounds_hint),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SoundStyleRow(
                    selected = selectedSound,
                    onSelect = { applySoundStyle(it) },
                )
                OutlinedButton(
                    onClick = {
                        selectedSound.resId?.let { DrsSoundPlayer.play(it, 1f) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringRes(R.string.drs__onboarding__sound_test))
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = { step += 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.action__continue))
                }
            }

            OnboardingStep.SHORTCUTS -> {
                Text(
                    text = stringRes(R.string.drs__onboarding__shortcuts_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringRes(R.string.drs__onboarding__shortcuts_hint),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val signatureSample = stringRes(R.string.drs__onboarding__shortcuts_signature_sample)
                ShortcutSampleRow(shortcut = "تاريخ", expansion = "{date}")
                ShortcutSampleRow(shortcut = "توقيع", expansion = signatureSample)
                if (!shortcutsAdded) {
                    OutlinedButton(
                        onClick = {
                            DrsShortcuts.add("تاريخ", "{date}", false)
                            DrsShortcuts.add("توقيع", signatureSample, false)
                            shortcutsAdded = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(R.string.drs__onboarding__shortcuts_add_samples))
                    }
                } else {
                    Text(
                        text = stringRes(R.string.drs__onboarding__shortcuts_added),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = { step += 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.action__continue))
                }
            }

            OnboardingStep.TEST -> {
                Text(
                    text = stringRes(R.string.drs__onboarding__test_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringRes(R.string.drs__onboarding__test_body),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var testText by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text(stringRes(R.string.drs__onboarding__test_hint)) },
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = { step += 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.action__continue))
                }
            }

            OnboardingStep.ENABLE -> {
                Text(
                    text = stringRes(R.string.drs__onboarding__enable_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val isEnabled by InputMethodUtils.observeIsDrsKeyboardEnabled(foregroundOnly = false)
                val isSelected by InputMethodUtils.observeIsDrsKeyboardSelected(foregroundOnly = false)
                StatusRow(text = stringRes(R.string.drs__onboarding__enable_status_enabled), done = isEnabled)
                StatusRow(text = stringRes(R.string.drs__onboarding__enable_status_selected), done = isSelected)
                if (!isEnabled) {
                    Button(
                        onClick = { InputMethodUtils.showImeEnablerActivity(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(R.string.drs__onboarding__enable_open_settings))
                    }
                } else if (!isSelected) {
                    Button(
                        onClick = { InputMethodUtils.showImePicker(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(R.string.drs__onboarding__enable_pick_keyboard))
                    }
                }
                Text(
                    text = stringRes(R.string.drs__onboarding__final_test_hint),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var finalText by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = finalText,
                    onValueChange = { finalText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    placeholder = { Text(stringRes(R.string.drs__onboarding__test_hint)) },
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = { step += 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.action__continue))
                }
            }

            OnboardingStep.DONE -> {
                DrsLogoHeader()
                Text(
                    text = stringRes(R.string.drs__onboarding__done_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringRes(R.string.drs__onboarding__done_body),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = { openControlCenter() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.drs__onboarding__done_open_control_center))
                }
                OutlinedButton(onClick = { completeOnboarding() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.drs__onboarding__done_close))
                }
            }
        }
    }
}

private fun updateActiveProfile(transform: (DrsProfile) -> DrsProfile) {
    val state = DrsStore.state.value
    val profile = DrsProfileManager.activeProfile(state) ?: return
    DrsProfileManager.applyProfile(transform(profile))
}

private fun themePairLabel(key: String): Int = when (key) {
    "desert" -> R.string.drs__theme__pair_desert
    "ocean" -> R.string.drs__theme__pair_ocean
    "emerald" -> R.string.drs__theme__pair_emerald
    else -> R.string.drs__theme__pair_royal
}

@Composable
private fun DrsLogoHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DrsCanvasIcon(
            iconId = R.mipmap.drs_app_icon,
            modifier = Modifier.size(72.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringRes(R.string.drs__onboarding__brand),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringRes(R.string.drs__onboarding__tagline),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExplanationRow(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ThemePairCard(pair: DrsThemePair, label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(12.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(pair.daySwatch, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(pair.nightSwatch, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SoundStyleRow(selected: DrsSoundStyle, onSelect: (DrsSoundStyle) -> Unit) {
    val styles = remember {
        listOf(
            DrsSoundStyle.SYSTEM,
            DrsSoundStyle.CLASSIC,
            DrsSoundStyle.MODERN,
            DrsSoundStyle.SOFT,
            DrsSoundStyle.CLICK,
            DrsSoundStyle.TYPER,
            DrsSoundStyle.POP,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        styles.chunked(2).forEach { rowStyles ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowStyles.forEach { style ->
                    FilterChip(
                        selected = selected == style,
                        onClick = { onSelect(style) },
                        label = {
                            Text(
                                text = stringRes(soundStyleLabel(style)),
                                fontSize = 13.sp,
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun soundStyleLabel(style: DrsSoundStyle): Int = when (style) {
    DrsSoundStyle.SYSTEM -> R.string.drs__sound__system
    DrsSoundStyle.CLASSIC -> R.string.drs__sound__classic
    DrsSoundStyle.MODERN -> R.string.drs__sound__modern
    DrsSoundStyle.SOFT -> R.string.drs__sound__soft
    DrsSoundStyle.CLICK -> R.string.drs__sound__click
    DrsSoundStyle.TYPER -> R.string.drs__sound__typer
    DrsSoundStyle.POP -> R.string.drs__sound__pop
}

@Composable
private fun ShortcutSampleRow(shortcut: String, expansion: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shortcut,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = "→", fontSize = 15.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = expansion,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One of the three DRS systems, presented during onboarding with its own
 * visual identity (accent color) and its curated feature list, so the
 * user sees exactly what each system delivers before choosing it.
 */
@Composable
private fun OnboardingSystemCard(spec: DrsSystemSpec, selected: Boolean, onClick: () -> Unit) {
    val accent = if (isSystemInDarkTheme()) spec.accentNight else spec.accent
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    accent
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(14.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                accent.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(accent, CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringRes(pathTitleRes(spec)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringRes(spec.bodyRes),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            listOf(spec.feature1Res, spec.feature2Res, spec.feature3Res, spec.feature4Res).forEach { featureRes ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "✓",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringRes(featureRes),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrefToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun StatusRow(text: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (done) "✓" else "○",
            color = if (done) {
                Color(0xFF0E9488)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
