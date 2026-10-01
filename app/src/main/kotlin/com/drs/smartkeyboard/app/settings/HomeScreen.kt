/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.drs.smartkeyboard.app.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.drs.smartkeyboard.BuildConfig
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.AppTheme
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.util.InputMethodUtils
import org.drs.jetpref.datastore.model.PreferenceModel
import org.drs.jetpref.datastore.model.collectAsState
import androidx.compose.runtime.collectAsState
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.ui.pathTitleRes
import org.drs.jetpref.datastore.ui.PreferenceUiContent
import org.drs.jetpref.datastore.ui.PreferenceUiScope
import org.drs.lib.compose.DrsErrorCard
import org.drs.lib.compose.DrsWarningCard
import org.drs.lib.compose.autoMirrorForRtl
import org.drs.lib.compose.stringRes

/*
 * DRS smart greeting: a time-aware welcome line that changes with the device
 * clock — morning, afternoon, evening or night — so the app always feels
 * alive and personal every time it is opened.
 */
private data class DrsSmartGreeting(val labelRes: Int, val icon: ImageVector)

/**
 * DRS (B8) — ONE shared 60-second ticker that drives both the time-aware
 * greeting and the daily-rotating hero tagline (was two independent
 * produceState loops doing the same delay work). Callers remember() their
 * derived value off the tick so an app left open across an hour/day boundary
 * refreshes itself automatically.
 */
@Composable
private fun rememberDrsHeroMinuteTick(): State<Long> = produceState(0L) {
    while (true) {
        delay(60_000L)
        value = System.currentTimeMillis()
    }
}

@Composable
private fun rememberDrsSmartGreeting(): DrsSmartGreeting {
    // Tick once per minute so the greeting follows the device clock live:
    // leaving the app open across an hour boundary updates it automatically.
    val minuteTick = rememberDrsHeroMinuteTick()
    return remember(minuteTick.value) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> DrsSmartGreeting(R.string.drs__home__greeting_morning, Icons.Default.WbSunny)
            in 12..16 -> DrsSmartGreeting(R.string.drs__home__greeting_afternoon, Icons.Default.LightMode)
            in 17..20 -> DrsSmartGreeting(R.string.drs__home__greeting_evening, Icons.Default.WbTwilight)
            else -> DrsSmartGreeting(R.string.drs__home__greeting_night, Icons.Default.DarkMode)
        }
    }
}

/**
 * DRS v1.0.3: rotating hero subtitle — seven curated, product-truthful
 * taglines picked deterministically by day-of-year, so the welcome header
 * feels alive day after day without ever flickering randomly mid-session.
 */
private val drsHeroSubtitleRes = listOf(
    R.string.drs__home__hero_subtitle,
    R.string.drs__home__hero_subtitle_2,
    R.string.drs__home__hero_subtitle_3,
    R.string.drs__home__hero_subtitle_4,
    R.string.drs__home__hero_subtitle_5,
    R.string.drs__home__hero_subtitle_6,
    R.string.drs__home__hero_subtitle_7,
)

@Composable
private fun rememberDrsHeroSubtitleRes(): Int {
    val dayTick = rememberDrsHeroMinuteTick()
    return remember(dayTick.value) {
        val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        drsHeroSubtitleRes[dayOfYear % drsHeroSubtitleRes.size]
    }
}

/**
 * DRS profile avatar: the DRS app icon inside a gradient ring with a status
 * dot — tapping it opens the user's profiles screen.
 */
@Composable
private fun DrsProfileAvatar(onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                onClickLabel = stringRes(R.string.drs__home__avatar_desc),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Animated gradient ring (primary -> tertiary -> primary).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(
                            colorScheme.primary,
                            colorScheme.tertiary,
                            colorScheme.primary.copy(alpha = 0.55f),
                            colorScheme.primary,
                        ),
                    ),
                ),
        )
        // Inner surface disc holding the DRS app icon.
        Box(
            modifier = Modifier
                .padding(2.dp)
                .fillMaxSize()
                .clip(CircleShape)
                .background(colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.drs_app_icon_foreground),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(7.dp),
            )
        }
        // Status dot.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(12.dp)
                .clip(CircleShape)
                .background(colorScheme.tertiary)
                .border(2.dp, colorScheme.surface, CircleShape),
        )
    }
}

/**
 * DRS smart search pill: a glassy, rounded search affordance. Tapping it
 * opens the smart search popup (Arabic-aware live settings search).
 */
@Composable
private fun DrsHomeSearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            // DRS (B8) — heightIn(min) instead of fixed height: large user font scales
            // grow the pill instead of clipping the hint text.
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(colorScheme.surface.copy(alpha = 0.78f))
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(23.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringRes(R.string.drs__home__search_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(17.dp),
        )
    }
}

/**
 * DRS theme toggle button: a glassy circular button that opens a popup menu
 * with three display modes — automatic (follows the system), night and day.
 * The choice persists in the settingsTheme preference, exactly
 * like choosing it from settings. The sun/moon icon crossfades with a
 * springy scale and a spinning motion, and a soft halo glows behind the
 * icon — moonlit in dark mode, sunlit in day mode.
 */
@Composable
private fun DrsThemeToggleButton(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val prefs by DrsPreferenceStore
    val theme by prefs.other.settingsTheme.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val isDark = theme == AppTheme.DARK
        || theme == AppTheme.AMOLED_DARK
        || ((theme == AppTheme.AUTO || theme == AppTheme.AUTO_AMOLED) && systemDark)
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isDark) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "drsThemeToggleRotation",
    )

    Box(
        modifier = modifier
            .size(46.dp)
            .shadow(
                elevation = if (isDark) 6.dp else 2.dp,
                shape = CircleShape,
                clip = false,
            )
            .clip(CircleShape)
            .background(colorScheme.surface.copy(alpha = 0.78f))
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f), CircleShape)
            .clickable(
                onClickLabel = stringRes(R.string.drs__home__theme_toggle_desc),
                onClick = { showMenu = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Soft halo behind the icon — moonlit glow in dark mode, sunlit in day.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val haloColor = if (isDark) colorScheme.tertiary else colorScheme.primary
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(haloColor.copy(alpha = 0.30f), Color.Transparent),
                        ),
                        radius = size.minDimension * 0.5f,
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                    )
                },
        )
        AnimatedContent(
            targetState = isDark,
            transitionSpec = {
                (scaleIn(initialScale = 0.3f) + fadeIn())
                    .togetherWith(scaleOut(targetScale = 0.3f) + fadeOut())
            },
            label = "drsThemeToggleIcon",
        ) { dark ->
            Icon(
                imageVector = if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = null,
                tint = if (dark) colorScheme.tertiary else colorScheme.primary,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
        // Popup menu: three-way display mode picker — تلقائي / ليلي / نهاري
        // with a check mark on the currently active mode.
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            Text(
                text = stringRes(R.string.drs__home__theme_menu_title),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            DropdownMenuItem(
                text = { Text(stringRes(R.string.drs__home__theme_menu_auto)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.BrightnessAuto,
                        contentDescription = null,
                        tint = colorScheme.primary,
                    )
                },
                trailingIcon = {
                    if (theme == AppTheme.AUTO || theme == AppTheme.AUTO_AMOLED) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colorScheme.primary,
                        )
                    }
                },
                onClick = {
                    showMenu = false
                    scope.launch { prefs.other.settingsTheme.set(AppTheme.AUTO) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringRes(R.string.drs__home__theme_menu_dark)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = colorScheme.tertiary,
                    )
                },
                trailingIcon = {
                    if (theme == AppTheme.DARK || theme == AppTheme.AMOLED_DARK) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colorScheme.primary,
                        )
                    }
                },
                onClick = {
                    showMenu = false
                    scope.launch { prefs.other.settingsTheme.set(AppTheme.DARK) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringRes(R.string.drs__home__theme_menu_light)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = null,
                        tint = colorScheme.primary,
                    )
                },
                trailingIcon = {
                    if (theme == AppTheme.LIGHT) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colorScheme.primary,
                        )
                    }
                },
                onClick = {
                    showMenu = false
                    scope.launch { prefs.other.settingsTheme.set(AppTheme.LIGHT) }
                },
            )
        }
    }
}

/**
 * DRS staggered entrance: wraps content in a box that slides up and fades
 * in with a springy motion, delayed by its index — the home content lands
 * in a cascade right after the hero bar, giving the whole screen a living,
 * modern feel.
 */
@Composable
private fun DrsStaggerIn(index: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * 55).toLong())
        progress.animateTo(1f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 260f))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            translationY = (1f - progress.value) * 40f
            alpha = (progress.value * 1.35f).coerceAtMost(1f)
        },
    ) {
        content()
    }
}

/**
 * DRS modern home tile: replaces the classic plain settings row —
 * a tinted rounded icon container with a per-tile accent color, a bold
 * title with a soft summary line, and a directional chevron that
 * automatically mirrors for RTL locales.
 */
@Composable
private fun DrsHomeTile(
    icon: ImageVector,
    accent: Color,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier
                .size(20.dp)
                // DRS v1.28.0 audit fix (High, RTL): AutoMirrored artwork does
                // NOT self-mirror at render time — the codebase convention
                // (DrsScreen back arrow) pairs it with autoMirrorForRtl().
                // Without it every home-tile chevron pointed backward in the
                // default Arabic locale.
                .autoMirrorForRtl(),
        )
    }
}

/** Hairline divider between modern tiles, aligned with the text edge. */
@Composable
private fun DrsTileDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp, end = 14.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}

/**
 * DRS quick action tile: a glassy rounded square in the 2x2 quick
 * actions grid — accent icon container plus a bold label, springing the
 * user straight into a frequently used destination.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.DrsQuickActionTile(
    icon: ImageVector,
    accent: Color,
    label: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(colorScheme.surface.copy(alpha = 0.92f))
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * DRS all-set status card: when the keyboard is enabled and
 * selected, a calm glassy card with a soft tertiary gradient confirms
 * everything is ready — positive feedback instead of the old silence.
 */
@Composable
private fun DrsHomeStatusCard() {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            colorScheme.tertiary.copy(alpha = 0.16f),
                            colorScheme.primary.copy(alpha = 0.05f),
                        ),
                    ),
                )
            }
            .border(1.dp, colorScheme.tertiary.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colorScheme.tertiary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colorScheme.onTertiary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringRes(R.string.drs__home__status_ready_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )
            Text(
                text = stringRes(R.string.drs__home__status_ready_desc),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * DRS home system card: presents the currently active system (normal /
 * technical / hybrid) with its own accent identity, and opens the Control
 * Center on tap — switching systems is always one tap away from home.
 */
@Composable
private fun DrsHomeSystemCard(onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val drsState by DrsStore.state.collectAsState()
    val spec = DrsSystems.specOfName(drsState.userPath)
    val accent = if (isSystemInDarkTheme()) spec.accentNight else spec.accent
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.05f)),
                    ),
                )
            }
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            // DRS v1.28.0 audit fix (UI consistency): the "★" Text glyph with a
            // hardcoded Color.White became unreadable on light accents and
            // rendered differently per platform font. A themed vector icon
            // keeps contrast under control via onPrimary.
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringRes(pathTitleRes(spec)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )
            Text(
                text = stringRes(spec.taglineRes),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringRes(R.string.drs__home__system_change),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}

/**
 * DRS quick actions grid: a 2x2 dashboard of glassy tiles under
 * the hero bar — one tap to the most-used destinations, exactly like the
 * control panels of modern flagship apps.
 */
@Composable
private fun DrsHomeQuickActions(
    onControlCenter: () -> Unit,
    onTheme: () -> Unit,
    onKeyboard: () -> Unit,
    onSmartbar: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(
            text = stringRes(R.string.drs__home__quick_actions_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            DrsQuickActionTile(
                icon = Icons.Default.Dashboard,
                accent = colorScheme.primary,
                label = stringRes(R.string.drs__control_center__title),
                onClick = onControlCenter,
            )
            Spacer(modifier = Modifier.width(10.dp))
            DrsQuickActionTile(
                icon = Icons.Outlined.Palette,
                accent = colorScheme.tertiary,
                label = stringRes(R.string.settings__theme__title),
                onClick = onTheme,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DrsQuickActionTile(
                icon = Icons.Outlined.Keyboard,
                accent = colorScheme.secondary,
                label = stringRes(R.string.settings__keyboard__title),
                onClick = onKeyboard,
            )
            Spacer(modifier = Modifier.width(10.dp))
            DrsQuickActionTile(
                icon = Icons.Default.SmartButton,
                accent = colorScheme.primary,
                label = stringRes(R.string.settings__smartbar__title),
                onClick = onSmartbar,
            )
        }
    }
}

/**
 * The height the welcome block condenses to before the hero header fully
 * slides away — shared between the collapse layout and the heightOffsetLimit
 * computation so both stay in exact lockstep.
 */
private val WelcomeCompactHeight = 26.dp

/**
 * DRS hero welcome header (Home screen): a modern, world-class
 * welcome area with a pinned control row (profile avatar + smart search
 * pill) and a COLLAPSING welcome block — the big title shrinks and fades
 * away as the user scrolls while a compact small title fades in, exactly
 * like the large-title pattern of the world's best apps. The gradient
 * extends edge-to-edge behind the status bar with soft radial glows and
 * rounded bottom corners; a dynamic hairline divider fades in only while
 * the content scrolls.
 *
 * DRS scroll-away: the whole header is NOT pinned. It glides off the top
 * of the screen 1:1 with the user's upward drag while its visible height
 * (and therefore the content area below) shrinks in lockstep — no gaps,
 * no clipping. The welcome block condenses during the early part of the
 * slide, and the bar gently dissolves over the last stretch. Any downward
 * drag springs it right back, and a fling settles it smartly via the
 * enter-always scroll behavior.
 */
@Composable
private fun DrsHomeHeroHeader(scrollBehavior: TopAppBarScrollBehavior) {
    val colorScheme = MaterialTheme.colorScheme
    val navController = LocalNavController.current
    val greeting = rememberDrsSmartGreeting()
    // DRS v1.0.3: daily-rotating tagline — refreshed once per minute-tick so
    // an app left open across midnight rolls to the next tagline by itself.
    val heroSubtitleRes = rememberDrsHeroSubtitleRes()
    val collapse = scrollBehavior.state.collapsedFraction
    var showSearch by remember { mutableStateOf(false) }

    // Measured scroll-away geometry: the bar's natural (expanded) height and
    // the welcome block's collapsible portion define the condensed height the
    // bar settles at just before vanishing. That value becomes the app bar's
    // heightOffsetLimit, so the slide tracks the finger 1:1 and lands exactly
    // at zero when fully collapsed.
    var fullBarHeight by remember { mutableStateOf(0) }
    var welcomeBlockHeight by remember { mutableStateOf(0) }
    val compactWelcomePx = with(LocalDensity.current) { WelcomeCompactHeight.toPx() }
    SideEffect {
        if (fullBarHeight > 0 && welcomeBlockHeight > 0) {
            val collapsible = (welcomeBlockHeight - compactWelcomePx).coerceAtLeast(0f)
            val condensed = (fullBarHeight - collapsible).coerceAtLeast(0f)
            val limit = -condensed
            if (scrollBehavior.state.heightOffsetLimit != limit) {
                scrollBehavior.state.heightOffsetLimit = limit
            }
        }
    }

    // Springy entrance: the whole bar slides down from above with a fade —
    // the home screen always opens with the bar gracefully landing in place.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, animationSpec = spring(dampingRatio = 0.82f, stiffness = 320f))
    }
    val appeared = entrance.value

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Scroll-away collapse: the visible height shrinks 1:1 with the
            // scroll offset while the bar slides up, its bottom edge staying
            // glued to the content below — no gaps, no clipping artifacts.
            // (onSizeChanged sits inside the collapse layout, so it always
            // reports the bar's natural, un-collapsed height.)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val offset = scrollBehavior.state.heightOffset
                val visible = (placeable.height + offset).roundToInt().coerceAtLeast(0)
                layout(constraints.maxWidth, visible) {
                    placeable.place(0, offset.roundToInt())
                }
            }
            .onSizeChanged { size ->
                if (size.height != fullBarHeight) fullBarHeight = size.height
            }
            // Gentle dissolve over the last stretch of the collapse — the
            // bar melts away instead of merely being pushed off the edge.
            .graphicsLayer {
                alpha = lerp(1f, 0f, ((collapse - 0.72f) / 0.28f).coerceIn(0f, 1f))
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = -(1f - appeared) * 64f
                    alpha = (appeared * 1.4f).coerceAtMost(1f)
                }
                // The bar lifts off the content as it condenses on scroll.
                .shadow(
                    elevation = 10.dp * collapse,
                    shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
                    clip = false,
                )
                .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                .drawBehind {
                    // Base vertical gradient — the same color identity as the
                    // floating preview pill, so the app feels like one product.
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to colorScheme.primary.copy(alpha = lerp(0.20f, 0.32f, collapse)),
                            1f to colorScheme.primary.copy(alpha = 0.05f),
                        ),
                    )
                    // Soft tertiary glow at the top-end for depth — it gently
                    // contracts as the bar condenses, keeping the depth alive.
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(colorScheme.tertiary.copy(alpha = 0.22f), Color.Transparent),
                        ),
                        radius = size.width * (0.55f - 0.18f * collapse),
                        center = Offset(size.width * 0.95f, size.height * 0.08f),
                    )
                    // Subtle primary glow near the bottom-start corner.
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(colorScheme.primary.copy(alpha = 0.16f), Color.Transparent),
                        ),
                        radius = size.width * 0.42f,
                        center = Offset(size.width * 0.04f, size.height * 1.05f),
                    )
                }
                .statusBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = lerp(10f, 6f, collapse).dp,
                    bottom = lerp(16f, 10f, collapse).dp,
                ),
        ) {
            // Pinned control row: profile avatar + smart search pill + theme
            // toggle — the whole row breathes with the scroll (gentle scale).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer {
                    val s = lerp(1f, 0.94f, collapse)
                    scaleX = s
                    scaleY = s
                },
            ) {
                DrsProfileAvatar(onClick = { navController.navigate(Routes.Settings.DrsProfiles) })
                Spacer(modifier = Modifier.width(10.dp))
                DrsHomeSearchPill(
                    onClick = { showSearch = true },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(10.dp))
                DrsThemeToggleButton()
            }
            Spacer(modifier = Modifier.height(14.dp))

            // Collapsing welcome block: measures its full height, then lerps
            // the visible height down to a compact bar as the user scrolls.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(maxHeight = Constraints.Infinity),
                        )
                        val full = placeable.height.toFloat()
                        val compact = WelcomeCompactHeight.toPx()
                        val target = if (collapse <= 0.001f) {
                            full
                        } else {
                            lerp(full, compact, collapse)
                        }
                        layout(constraints.maxWidth, target.roundToInt()) {
                            placeable.place(0, 0)
                        }
                    },
            ) {
                // Big welcome content: shrinks slightly and fades out.
                // onSizeChanged feeds its natural (expanded) height into the
                // scroll-away geometry computation above.
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            val shrink = 1f - 0.16f * collapse
                            scaleX = shrink
                            scaleY = shrink
                            alpha = (1f - 1.35f * collapse).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .onSizeChanged { size ->
                            if (size.height != welcomeBlockHeight) welcomeBlockHeight = size.height
                        },
                ) {
                    Text(
                        text = stringRes(R.string.settings__home__title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Smart greeting merged elegantly into the tagline line.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = greeting.icon,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringRes(
                                R.string.drs__home__hero_tagline,
                                "greeting" to stringRes(greeting.labelRes),
                                "subtitle" to stringRes(heroSubtitleRes),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Compact small title, fades in near full collapse.
                Text(
                    text = stringRes(R.string.settings__home__title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            alpha = ((collapse - 0.55f) / 0.45f).coerceIn(0f, 1f)
                        },
                )
            }
        }
        // Dynamic hairline divider — visible only while scrolling.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colorScheme.outlineVariant.copy(alpha = (collapse * 0.5f).coerceIn(0f, 0.5f))),
        )
    }
    if (showSearch) {
        DrsSmartSearchDialog(
            onDismiss = { showSearch = false },
            onNavigate = { route ->
                showSearch = false
                navController.navigate(route)
            },
        )
    }
}

@Composable
private fun DrsGroupHeader(text: String, icon: ImageVector) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Compact tinted squircle badge instead of the old full-width pill.
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(10.dp))
        // Trailing hairline — the modern section-header signature.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colorScheme.outlineVariant.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun <T : PreferenceModel> PreferenceUiScope<T>.DrsGroupCard(
    icon: ImageVector,
    title: String,
    content: PreferenceUiContent<T>,
) {
    Column {
        DrsGroupHeader(text = title, icon = icon)
        Card(
            modifier = Modifier
                .padding(start = 10.dp, end = 10.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Column {
                val groupScope = PreferenceUiScope(prefs = this@DrsGroupCard.prefs, columnScope = this@Column)
                content(groupScope)
            }
        }
    }
}

/**
 * DRS version footer: a calm, centered identity block that closes the home
 * screen — the app name, the exact build version straight from BuildConfig,
 * and the v1.0 release tagline in the accent color. Tapping it opens the
 * About screen, keeping the release identity one tap away from the hub.
 */
@Composable
private fun DrsHomeVersionFooter(onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringRes(R.string.drs_app_name),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariant,
        )
        Text(
            text = "v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
            fontSize = 11.sp,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = stringRes(R.string.drs__about__release_tagline),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
fun HomeScreen() = DrsScreen {
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    previewFieldVisible = true
    // DRS: smart hero welcome header replaces the plain title bar above the
    // setup warning card — modern gradient + time-aware greeting.
    topBar { scrollBehavior -> DrsHomeHeroHeader(scrollBehavior) }

    val navController = LocalNavController.current
    val context = LocalContext.current

    content {
        val isDrsKeyboardEnabled by InputMethodUtils.observeIsDrsKeyboardEnabled(foregroundOnly = true)
        val isDrsKeyboardSelected by InputMethodUtils.observeIsDrsKeyboardSelected(foregroundOnly = true)
        if (!isDrsKeyboardEnabled) {
            DrsErrorCard(
                modifier = Modifier.padding(8.dp),
                showIcon = false,
                text = stringRes(R.string.settings__home__ime_not_enabled),
                onClick = { InputMethodUtils.showImeEnablerActivity(context) },
            )
        } else if (!isDrsKeyboardSelected) {
            DrsWarningCard(
                modifier = Modifier.padding(8.dp),
                showIcon = false,
                text = stringRes(R.string.settings__home__ime_not_selected),
                onClick = { InputMethodUtils.showImePicker(context) },
            )
        } else {
            // Everything is ready — a calm, glassy confirmation card.
            DrsStaggerIn(index = 0) {
                DrsHomeStatusCard()
            }
        }

        // The three-system identity: current system card with its own
        // accent, one tap away from switching systems in Control Center.
        DrsStaggerIn(index = 1) {
            DrsHomeSystemCard(
                onClick = { navController.navigate(Routes.Settings.DrsControlCenter) },
            )
        }

        DrsStaggerIn(index = 1) {
            DrsHomeQuickActions(
                onControlCenter = { navController.navigate(Routes.Settings.DrsControlCenter) },
                onTheme = { navController.navigate(Routes.Settings.Theme) },
                onKeyboard = { navController.navigate(Routes.Settings.Keyboard) },
                onSmartbar = { navController.navigate(Routes.Settings.Smartbar) },
            )
        }
        DrsStaggerIn(index = 2) {
            DrsGroupCard(icon = Icons.Default.AutoAwesome, title = stringRes(R.string.settings__home__group__drs)) {
                // DRS v1.0.7: the unified «كلاهما» system leads the group.
                DrsHomeTile(
                    icon = Icons.Default.Dashboard,
                    accent = MaterialTheme.colorScheme.primary,
                    title = stringRes(R.string.drs__unified__dashboard_title),
                    summary = stringRes(R.string.drs__unified__home_summary),
                    onClick = { navController.navigate(Routes.Settings.DrsUnifiedDashboard) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Default.Extension,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = stringRes(R.string.drs__unified__tools_title),
                    summary = stringRes(R.string.drs__unified__tools_home_summary),
                    onClick = { navController.navigate(Routes.Settings.DrsUnifiedTools) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Default.Dashboard,
                    accent = MaterialTheme.colorScheme.primary,
                    title = stringRes(R.string.drs__control_center__title),
                    summary = stringRes(R.string.drs__control_center__home_summary),
                    onClick = { navController.navigate(Routes.Settings.DrsControlCenter) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.AutoMirrored.Outlined.Assignment,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = stringRes(R.string.drs__shortcuts__title),
                    summary = stringRes(R.string.drs__shortcuts__home_summary),
                    onClick = { navController.navigate(Routes.Settings.DrsShortcuts) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Default.People,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = stringRes(R.string.drs__profiles__title),
                    summary = stringRes(R.string.drs__profiles__home_summary),
                    onClick = { navController.navigate(Routes.Settings.DrsProfiles) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Default.TouchApp,
                    accent = MaterialTheme.colorScheme.primary,
                    title = stringRes(R.string.drs__gestures__title),
                    summary = stringRes(R.string.drs__gestures__home_summary),
                    onClick = { navController.navigate(Routes.Settings.DrsGestures) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Default.Healing,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = stringRes(R.string.drs__diagnostics__title),
                    summary = stringRes(R.string.drs__diagnostics__home_summary),
                    onClick = { navController.navigate(Routes.Settings.DrsDiagnostics) },
                )
            }
        }
        DrsStaggerIn(index = 3) {
            DrsGroupCard(icon = Icons.Default.Tune, title = stringRes(R.string.settings__home__group__basics)) {
                DrsHomeTile(
                    icon = Icons.Default.Language,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = stringRes(R.string.settings__localization__title),
                    summary = stringRes(R.string.settings__localization__summary),
                    onClick = { navController.navigate(Routes.Settings.Localization) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Outlined.Palette,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = stringRes(R.string.settings__theme__title),
                    summary = stringRes(R.string.settings__theme__summary),
                    onClick = { navController.navigate(Routes.Settings.Theme) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Outlined.Keyboard,
                    accent = MaterialTheme.colorScheme.primary,
                    title = stringRes(R.string.settings__keyboard__title),
                    summary = stringRes(R.string.settings__keyboard__summary),
                    onClick = { navController.navigate(Routes.Settings.Keyboard) },
                )
            }
        }
        DrsStaggerIn(index = 4) {
            DrsGroupCard(icon = Icons.Default.Edit, title = stringRes(R.string.settings__home__group__typing_tools)) {
                DrsHomeTile(
                    icon = Icons.Default.SmartButton,
                    accent = MaterialTheme.colorScheme.primary,
                    title = stringRes(R.string.settings__smartbar__title),
                    summary = stringRes(R.string.settings__smartbar__summary),
                    onClick = { navController.navigate(Routes.Settings.Smartbar) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Default.Spellcheck,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = stringRes(R.string.settings__typing__title),
                    summary = stringRes(R.string.settings__typing__summary),
                    onClick = { navController.navigate(Routes.Settings.Typing) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Default.Gesture,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = stringRes(R.string.settings__gestures__title),
                    summary = stringRes(R.string.settings__gestures__summary),
                    onClick = { navController.navigate(Routes.Settings.Gestures) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Default.ContentPaste,
                    accent = MaterialTheme.colorScheme.primary,
                    title = stringRes(R.string.settings__clipboard__title),
                    summary = stringRes(R.string.settings__clipboard__summary),
                    onClick = { navController.navigate(Routes.Settings.Clipboard) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Default.SentimentSatisfiedAlt,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = stringRes(R.string.settings__media__title),
                    summary = stringRes(R.string.settings__media__summary),
                    onClick = { navController.navigate(Routes.Settings.Media) },
                )
            }
        }
        DrsStaggerIn(index = 5) {
            DrsGroupCard(icon = Icons.Default.Settings, title = stringRes(R.string.settings__home__group__system)) {
                DrsHomeTile(
                    icon = Icons.Default.Extension,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = stringRes(R.string.ext__home__title),
                    summary = stringRes(R.string.ext__home__summary),
                    onClick = { navController.navigate(Routes.Ext.Home) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Default.SmartButton,
                    accent = MaterialTheme.colorScheme.primary,
                    title = stringRes(R.string.accessories__title),
                    summary = stringRes(R.string.accessories__summary),
                    onClick = { navController.navigate(Routes.Ext.Accessories) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Default.Redeem,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = stringRes(R.string.drs__rewards__title),
                    summary = stringRes(R.string.drs__rewards__summary),
                    onClick = { navController.navigate(Routes.Settings.DrsRewards) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Outlined.Build,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = stringRes(R.string.settings__other__title),
                    summary = stringRes(R.string.settings__other__summary),
                    onClick = { navController.navigate(Routes.Settings.Other) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Default.Security,
                    accent = MaterialTheme.colorScheme.primary,
                    title = stringRes(R.string.privacy__title),
                    summary = stringRes(R.string.privacy__summary),
                    onClick = { navController.navigate(Routes.Settings.Privacy) },
                )
                DrsTileDivider()
                DrsHomeTile(
                    icon = Icons.Outlined.Info,
                    accent = MaterialTheme.colorScheme.primary,
                    title = stringRes(R.string.about__title),
                    summary = stringRes(R.string.about__summary),
                    onClick = { navController.navigate(Routes.Settings.About) },
                )
            }
        }
        DrsStaggerIn(index = 6) {
            DrsHomeVersionFooter(onClick = { navController.navigate(Routes.Settings.About) })
        }
    }
}
