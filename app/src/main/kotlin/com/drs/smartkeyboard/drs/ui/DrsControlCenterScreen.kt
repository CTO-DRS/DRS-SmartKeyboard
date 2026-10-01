/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.drs.DrsAdaptationEngine
import com.drs.smartkeyboard.drs.DrsCurrencies
import com.drs.smartkeyboard.drs.DrsEconomy
import com.drs.smartkeyboard.drs.DrsProfile
import com.drs.smartkeyboard.drs.DrsProfileManager
import com.drs.smartkeyboard.drs.DrsRewardCatalog
import com.drs.smartkeyboard.drs.DrsShortcuts
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystemSpec
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.DrsUserPath
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.util.InputMethodUtils
import org.drs.lib.compose.stringRes

/**
 * DRS Control Center: the central dashboard of the adaptive keyboard.
 *
 * The three DRS systems (normal / technical / hybrid) are first-class
 * products here: the current system is presented as a hero card carrying
 * its own visual identity, all three systems can be switched directly
 * from the dashboard (each keeps its own dedicated profile and tuning),
 * and the active system's curated feature list is always visible.
 * Changing the system re-applies its own keyboard configuration without
 * losing any user data.
 */
@Composable
fun DrsControlCenterScreen() = DrsScreen {
    title = stringRes(R.string.drs__control_center__title)
    navigationIconVisible = true
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current

    val drsState by DrsStore.state.collectAsState()
    val isEnabled by InputMethodUtils.observeIsDrsKeyboardEnabled(foregroundOnly = true)
    val isSelected by InputMethodUtils.observeIsDrsKeyboardSelected(foregroundOnly = true)

    // Provision each system's own dedicated profile (idempotent) so every
    // system always owns a ready-to-activate configuration.
    LaunchedEffect(Unit) { DrsProfileManager.ensureSystemProfiles() }

    var showProfileDialog by remember { mutableStateOf(false) }
    var showCreateProfileDialog by remember { mutableStateOf(false) }

    val activeProfile = DrsProfileManager.activeProfile(drsState)
    val activeSpec = DrsSystems.specOfName(drsState.userPath)
    val suggestions = remember(drsState) { DrsAdaptationEngine.computeSuggestions(drsState) }

    content {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---------------- current system hero ----------------
            DrsSystemHeroCard(spec = activeSpec)

            // ---------------- the three systems switcher ----------------
            DrsSectionCard(title = stringRes(R.string.drs__control_center__systems_section)) {
                Text(
                    text = stringRes(R.string.drs__control_center__systems_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DrsSystems.ALL.forEach { spec ->
                    DrsSystemSwitchCard(
                        spec = spec,
                        selected = spec.path.name == drsState.userPath,
                        onClick = {
                            if (spec.path.name != drsState.userPath) {
                                DrsAdaptationEngine.setUserPath(spec.path)
                            }
                        },
                    )
                }
            }

            // ---------------- active system curated features ----------------
            DrsSystemFeaturesCard(spec = activeSpec)

            // ---------------- profiles ----------------
            DrsSectionCard(title = stringRes(R.string.drs__control_center__profile_section)) {
                Text(
                    text = activeProfile?.name ?: stringRes(R.string.drs__control_center__no_profile),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringRes(
                        R.string.drs__control_center__profile_count,
                        "count" to drsState.profiles.size.toString(),
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showProfileDialog = true }) {
                        Text(stringRes(R.string.drs__control_center__switch_profile))
                    }
                    OutlinedButton(onClick = { showCreateProfileDialog = true }) {
                        Text(stringRes(R.string.drs__control_center__create_profile))
                    }
                }
            }

            // ---------------- ime status ----------------
            DrsSectionCard(title = stringRes(R.string.drs__control_center__ime_status)) {
                // DRS v1.28.0 audit fix (UI consistency): the ✓/✗ glyphs used to
                // live inside the translated strings; the state is now carried
                // by a themed vector icon next to the clean text.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringRes(
                            if (isEnabled) R.string.drs__control_center__ime_enabled
                            else R.string.drs__control_center__ime_not_enabled,
                        ),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringRes(
                            if (isSelected) R.string.drs__control_center__ime_selected
                            else R.string.drs__control_center__ime_not_selected,
                        ),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (!isEnabled) {
                    OutlinedButton(onClick = { InputMethodUtils.showImeEnablerActivity(context) }) {
                        Text(stringRes(R.string.drs__control_center__enable_ime))
                    }
                } else if (!isSelected) {
                    OutlinedButton(onClick = { InputMethodUtils.showImePicker(context) }) {
                        Text(stringRes(R.string.drs__control_center__pick_ime))
                    }
                }
            }

            // ---------------- quick toggles ----------------
            DrsSectionCard(title = stringRes(R.string.drs__control_center__quick_settings)) {
                DrsToggleRow(
                    title = stringRes(R.string.drs__control_center__tech_strip),
                    checked = activeProfile?.techStripEnabled ?: false,
                ) { checked ->
                    activeProfile?.let { DrsProfileManager.applyProfile(it.copy(techStripEnabled = checked)) }
                }
                DrsToggleRow(
                    title = stringRes(R.string.drs__control_center__context_modes),
                    checked = drsState.contextModesEnabled,
                ) { checked ->
                    DrsStore.update { it.copy(contextModesEnabled = checked) }
                }
                DrsToggleRow(
                    title = stringRes(R.string.drs__control_center__shortcuts),
                    checked = drsState.shortcutsEnabled,
                ) { checked ->
                    DrsShortcuts.setEnabled(checked)
                }
                DrsToggleRow(
                    title = stringRes(R.string.drs__control_center__adaptation),
                    checked = drsState.adaptationEnabled,
                ) { checked ->
                    DrsStore.update { it.copy(adaptationEnabled = checked) }
                }
            }

            // ---------------- adaptation suggestions ----------------
            if (suggestions.isNotEmpty()) {
                DrsSectionCard(title = stringRes(R.string.drs__control_center__suggestions)) {
                    Text(
                        text = stringRes(R.string.drs__control_center__suggestions_hint),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    suggestions.forEach { suggestion ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = suggestionLabel(suggestion.id) + " (" + suggestion.metric + ")",
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp,
                            )
                            TextButton(onClick = { DrsAdaptationEngine.accept(suggestion) }) {
                                Text(stringRes(R.string.drs__control_center__suggestion_apply))
                            }
                            TextButton(onClick = { DrsAdaptationEngine.dismiss(suggestion.id) }) {
                                Text(stringRes(R.string.drs__control_center__suggestion_dismiss))
                            }
                        }
                    }
                }
            }

            // ---------------- links to existing sections ----------------
            DrsSectionCard(title = stringRes(R.string.drs__control_center__sections)) {
                // DRS v1.0.7: the unified «كلاهما» screens lead the list.
                Text(
                    text = stringRes(R.string.drs__unified__section_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DrsLinkRow(stringRes(R.string.drs__unified__dashboard_title)) {
                    navController.navigate(Routes.Settings.DrsUnifiedDashboard)
                }
                DrsLinkRow(stringRes(R.string.drs__unified__tools_title)) {
                    navController.navigate(Routes.Settings.DrsUnifiedTools)
                }
                DrsLinkRow(stringRes(R.string.drs__unified__basics_title)) {
                    navController.navigate(Routes.Settings.DrsUnifiedBasics)
                }
                DrsLinkRow(stringRes(R.string.drs__unified__writing_title)) {
                    navController.navigate(Routes.Settings.DrsUnifiedWriting)
                }
                DrsLinkRow(stringRes(R.string.drs__unified__system_title)) {
                    navController.navigate(Routes.Settings.DrsUnifiedSystem)
                }
                DrsLinkRow(stringRes(R.string.drs__profiles__title)) { navController.navigate(Routes.Settings.DrsProfiles) }
                DrsLinkRow(stringRes(R.string.drs__gestures__title)) { navController.navigate(Routes.Settings.DrsGestures) }
                DrsLinkRow(stringRes(R.string.settings__theme__title)) { navController.navigate(Routes.Settings.Theme) }
                DrsLinkRow(stringRes(R.string.settings__keyboard__title)) { navController.navigate(Routes.Settings.Keyboard) }
                DrsLinkRow(stringRes(R.string.settings__smartbar__title)) { navController.navigate(Routes.Settings.Smartbar) }
                DrsLinkRow(stringRes(R.string.settings__typing__title)) { navController.navigate(Routes.Settings.Typing) }
                DrsLinkRow(stringRes(R.string.settings__gestures__title)) { navController.navigate(Routes.Settings.Gestures) }
                DrsLinkRow(stringRes(R.string.drs__shortcuts__title)) { navController.navigate(Routes.Settings.DrsShortcuts) }
                // DRS v1.0.6: technical toolbar key editor.
                DrsLinkRow(stringRes(R.string.drs__tech_toolbar__title)) { navController.navigate(Routes.Settings.DrsTechToolbar) }
                DrsLinkRow(stringRes(R.string.settings__clipboard__title)) { navController.navigate(Routes.Settings.Clipboard) }
                DrsLinkRow(stringRes(R.string.settings__localization__title)) { navController.navigate(Routes.Settings.Localization) }
                DrsLinkRow(stringRes(R.string.settings__other__title)) { navController.navigate(Routes.Settings.Other) }
            }

            // ---------------- diagnostics ----------------
            DrsSectionCard(title = stringRes(R.string.drs__diagnostics__title)) {
                Text(
                    text = stringRes(R.string.drs__diagnostics__entry_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { navController.navigate(Routes.Settings.DrsDiagnostics) }) {
                    Text(stringRes(R.string.drs__diagnostics__open))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (showProfileDialog) {
            DrsProfileDialog(
                profiles = drsState.profiles,
                activeId = drsState.activeProfileId,
                onSelect = { profile ->
                    showProfileDialog = false
                    DrsProfileManager.applyProfile(profile)
                },
                onDismiss = { showProfileDialog = false },
            )
        }
        if (showCreateProfileDialog) {
            DrsCreateProfileDialog(
                baseProfile = activeProfile,
                onCreate = { name ->
                    showCreateProfileDialog = false
                    val base = activeProfile ?: DrsProfileManager.defaultProfileFor(DrsUserPath.NORMAL)
                    DrsProfileManager.createAndApply(DrsProfileManager.customProfileFrom(base, name))
                },
                onDismiss = { showCreateProfileDialog = false },
            )
        }
    }
}

/** Resolves the system accent for the current app appearance. */
@Composable
private fun DrsSystemSpec.accentFor(): Color {
    return if (isSystemInDarkTheme()) accentNight else accent
}

/**
 * Hero card presenting the currently active system with its own visual
 * identity: an accent gradient surface, an accent ring badge and the
 * system slogan.
 */
@Composable
private fun DrsSystemHeroCard(spec: DrsSystemSpec) {
    val accent = spec.accentFor()
    val navController = LocalNavController.current
    val drsState by DrsStore.state.collectAsState()
    val wallet = drsState.wallet
    val currency = DrsCurrencies.of(spec.path)
    val balance = DrsEconomy.balanceOf(wallet, spec.path)
    val equippedTitle = wallet.equippedTitle?.let { DrsRewardCatalog.itemOf(it) }
    val cardStyle = wallet.equippedCard?.let { DrsRewardCatalog.itemOf(it) }

    // Card decoration driven by the equipped reward card style.
    // DRS a11y/i18n/ux (r0-I): when the system has animations disabled the
    // infinite shimmer transition is never created — the card falls back to
    // a static mid-alpha instead.
    val reducedMotion = org.drs.lib.compose.rememberReducedMotion()
    val shimmerAlpha = if (reducedMotion) {
        0.85f
    } else {
        val shimmer = rememberInfiniteTransition(label = "cardShimmer")
        val shimmerState = shimmer.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "cardShimmerAlpha",
        )
        shimmerState.value
    }
    val borderColor = when (cardStyle?.id) {
        "card_harmony" -> Color(0xFF2DD4BF)
        "card_stream" -> Color(0xFF8B93F8)
        "card_fusion" -> Color(0xFFFBBF24)
        else -> accent
    }
    val borderAlpha = when (cardStyle?.id) {
        "card_glow", "card_shimmer" -> if (cardStyle?.id == "card_shimmer") shimmerAlpha else 0.85f
        "card_harmony", "card_stream", "card_fusion" -> 0.7f
        else -> 0.45f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (cardStyle != null) 8.dp else 0.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = borderColor.copy(alpha = 0.5f),
                spotColor = borderColor.copy(alpha = 0.5f),
            )
            .border(2.dp * borderAlpha, borderColor, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.05f)),
                    ),
                )
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(accent, CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringRes(R.string.drs__control_center__current_system),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Text(
                text = stringRes(pathTitleRes(spec)),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringRes(spec.taglineRes),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            equippedTitle?.let { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = borderColor,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringRes(item.titleRes),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = borderColor,
                    )
                }
            }
            // Wallet balance chip -> rewards store
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { navController.navigate(Routes.Settings.DrsRewards) }
                    .background(borderColor.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = currency.icon,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = balance.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = borderColor,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringRes(currency.nameRes),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** @return the localized display title of a system. */
@Composable
fun pathTitleRes(spec: DrsSystemSpec): Int {
    return when (spec.path) {
        DrsUserPath.TECHNICAL -> R.string.drs__path_technical_title
        DrsUserPath.HYBRID -> R.string.drs__path_hybrid_title
        DrsUserPath.NORMAL -> R.string.drs__path_normal_title
    }
}

/**
 * One selectable system card: accent identity dot, name, slogan and a
 * clear active state. Tapping a card activates that system together with
 * its own dedicated profile.
 */
@Composable
private fun DrsSystemSwitchCard(spec: DrsSystemSpec, selected: Boolean, onClick: () -> Unit) {
    val accent = spec.accentFor()
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp),
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(accent.copy(alpha = if (selected) 1f else 0.55f), CircleShape),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringRes(pathTitleRes(spec)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringRes(spec.taglineRes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Text(
                    text = stringRes(R.string.drs__control_center__system_active),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
        }
    }
}

/**
 * The active system's curated feature list - a compact, always visible
 * checklist of what this system was designed to deliver.
 */
@Composable
private fun DrsSystemFeaturesCard(spec: DrsSystemSpec) {
    val accent = spec.accentFor()
    DrsSectionCard(title = stringRes(R.string.drs__control_center__system_features)) {
        listOf(spec.feature1Res, spec.feature2Res, spec.feature3Res, spec.feature4Res).forEach { featureRes ->
            Row(verticalAlignment = Alignment.Top) {
                // DRS v1.28.0 audit fix (UI consistency): the "✓" Text glyph is
                // a font-dependent render — replaced by a themed vector icon.
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringRes(featureRes),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DrsSectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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
                // DRS: primary accent bar leading the section title — the
                // same section identity used in diagnostics and home.
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
private fun DrsToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = title, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DrsLinkRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 14.sp)
    }
}

@Composable
private fun DrsProfileDialog(
    profiles: List<DrsProfile>,
    activeId: String,
    onSelect: (DrsProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.drs__control_center__switch_profile)) },
        text = {
            Column {
                if (profiles.isEmpty()) {
                    Text(stringRes(R.string.drs__control_center__no_profile))
                }
                profiles.forEach { profile ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = profile.id == activeId,
                            onClick = { onSelect(profile) },
                        )
                        TextButton(onClick = { onSelect(profile) }) {
                            Text(profile.name, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.action__cancel))
            }
        },
    )
}

@Composable
private fun DrsCreateProfileDialog(
    baseProfile: DrsProfile?,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.drs__control_center__create_profile)) },
        text = {
            Column {
                Text(
                    text = stringRes(
                        R.string.drs__control_center__create_profile_hint,
                        "base" to (baseProfile?.name ?: "-"),
                    ),
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringRes(R.string.action__ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.action__cancel))
            }
        },
    )
}

@Composable
private fun suggestionLabel(id: String): String {
    return when (id) {
        com.drs.smartkeyboard.drs.DrsSuggestionIds.NUMBER_ROW ->
            stringRes(R.string.drs__suggestion__number_row)
        com.drs.smartkeyboard.drs.DrsSuggestionIds.TECH_STRIP ->
            stringRes(R.string.drs__suggestion__tech_strip)
        com.drs.smartkeyboard.drs.DrsSuggestionIds.CLIPBOARD_HISTORY ->
            stringRes(R.string.drs__suggestion__clipboard_history)
        com.drs.smartkeyboard.drs.DrsSuggestionIds.TECHNICAL_PATH ->
            stringRes(R.string.drs__suggestion__technical_path)
        else -> stringRes(R.string.drs__suggestion__shortcuts)
    }
}
