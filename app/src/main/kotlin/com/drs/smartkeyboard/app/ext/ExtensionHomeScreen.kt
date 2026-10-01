/*
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.app.ext

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.drs.smartkeyboard.BuildConfig
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.DrsUserPath
import com.drs.smartkeyboard.extensionManager
import com.drs.smartkeyboard.ime.nlp.LanguagePackExtension
import com.drs.smartkeyboard.ime.theme.ThemeExtension
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.ext.Extension
import com.drs.smartkeyboard.lib.util.launchUrl
import kotlinx.coroutines.delay
import org.drs.lib.compose.stringRes

/**
 * DRS Add-ons Hub (مركز الإضافات والملحقات) - the complete home of the
 * "System & App" section. It unifies in one system-aware screen:
 *  - live statistics over every installed add-on,
 *  - on-device search across all add-ons,
 *  - the three add-on families (themes / keyboard layouts / language packs),
 *  - a data-driven catalog of the bundled and imported packages,
 *  - curated recommendations matching the active DRS system, and
 *  - every tool needed to import, create, update and back up add-ons.
 */
@Composable
fun ExtensionHomeScreen() = DrsScreen {
    title = stringRes(R.string.ext__home__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val extensionManager by context.extensionManager()

    val themes by extensionManager.themes.collectAsState()
    val keyboards by extensionManager.keyboardExtensions.collectAsState()
    val languagePacks by extensionManager.languagePacks.collectAsState()

    val drsState by DrsStore.state.collectAsState()
    val spec = DrsSystems.specOfName(drsState.userPath)
    val accent = if (isSystemInDarkTheme()) spec.accentNight else spec.accent

    val allExtensions: List<Extension> = remember(themes, keyboards, languagePacks) {
        buildList {
            addAll(themes)
            addAll(keyboards)
            addAll(languagePacks)
        }
    }

    var query by remember { mutableStateOf("") }
    val results = remember(query, allExtensions) {
        if (query.isBlank()) {
            emptyList()
        } else {
            allExtensions.filter { ext ->
                ext.meta.title.contains(query, ignoreCase = true) ||
                    ext.meta.id.contains(query, ignoreCase = true) ||
                    (ext.meta.description?.contains(query, ignoreCase = true) == true) ||
                    (ext.meta.keywords?.any { it.contains(query, ignoreCase = true) } == true)
            }
        }
    }

    val mediaItemCount = remember {
        runCatching {
            (context.assets.list("ime/media/emoji")?.size ?: 0) +
                (context.assets.list("ime/media/emoticon")?.size ?: 0)
        }.getOrDefault(0)
    }

    content {
        HubStaggerIn(index = 0) {
            HubHeroCard(
                accent = accent,
                themesCount = themes.size,
                keyboardsCount = keyboards.size,
                languagePacksCount = languagePacks.size,
                totalCount = allExtensions.size,
            )
        }

        HubStaggerIn(index = 1) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringRes(R.string.ext__hub__search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            // DRS v1.21.0: an actionable button must announce itself.
                            Icon(Icons.Default.Close, contentDescription = stringRes(R.string.clip__a11y_clear_find))
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
            )
        }

        if (query.isNotBlank()) {
            HubSectionCard(title = stringRes(R.string.ext__hub__search_results)) {
                if (results.isEmpty()) {
                    Text(
                        text = stringRes(R.string.ext__hub__search_empty, "query" to query),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                results.forEach { ext ->
                    HubAddonRow(
                        icon = hubIconOfType(ext),
                        title = ext.meta.title,
                        subtitle = ext.meta.id,
                        onClick = { navController.navigate(Routes.Ext.View(ext.meta.id)) },
                    )
                }
            }
        } else {
            // ---------------- installed add-on families ----------------
            HubStaggerIn(index = 2) {
                HubSectionCard(title = stringRes(R.string.ext__hub__section_addons)) {
                    Text(
                        text = stringRes(R.string.ext__hub__addons_hint),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HubTypeCard(
                        icon = Icons.Default.Palette,
                        accent = accent,
                        title = stringRes(R.string.ext__list__ext_theme),
                        count = themes.size,
                        onClick = {
                            navController.navigate(Routes.Ext.List(ExtensionListScreenType.EXT_THEME, false))
                        },
                    )
                    HubTypeCard(
                        icon = Icons.Default.Keyboard,
                        accent = accent,
                        title = stringRes(R.string.ext__list__ext_keyboard),
                        count = keyboards.size,
                        onClick = {
                            navController.navigate(Routes.Ext.List(ExtensionListScreenType.EXT_KEYBOARD, false))
                        },
                    )
                    HubTypeCard(
                        icon = Icons.Default.Language,
                        accent = accent,
                        title = stringRes(R.string.ext__list__ext_languagepack),
                        count = languagePacks.size,
                        onClick = {
                            navController.navigate(Routes.Ext.List(ExtensionListScreenType.EXT_LANGUAGEPACK, false))
                        },
                    )
                }
            }

            // ---------------- bundled & imported packages ----------------
            HubStaggerIn(index = 3) {
                HubSectionCard(title = stringRes(R.string.ext__hub__section_packages)) {
                    Text(
                        text = stringRes(R.string.ext__hub__packages_hint),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val packages = remember(allExtensions) { collectPackages(allExtensions) }
                    packages.forEach { pkg ->
                        HubPackageRow(
                            icon = hubIconOfType(pkg.representative),
                            title = hubPackageTitle(pkg),
                            subtitle = hubCountsSummary(pkg),
                            badgeBundled = pkg.isBundled,
                            onClick = {
                                navController.navigate(
                                    Routes.Ext.List(hubListTypeOf(pkg.representative), false),
                                )
                            },
                        )
                    }
                    // The media package (emoji + emoticons) ships inside the
                    // APK assets and is surfaced here as a first-class pack.
                    HubPackageRow(
                        icon = Icons.Default.SentimentSatisfiedAlt,
                        title = stringRes(R.string.ext__hub__pkg_media),
                        subtitle = stringRes(
                            R.string.ext__hub__pkg_media_summary,
                            "count" to mediaItemCount.toString(),
                        ),
                        badgeBundled = true,
                        onClick = { navController.navigate(Routes.Settings.Media) },
                    )
                }
            }

            // ---------------- curated for the current system ----------------
            HubStaggerIn(index = 4) {
                HubSectionCard(
                    title = stringRes(R.string.ext__hub__system_section),
                    accent = accent,
                ) {
                    Text(
                        text = stringRes(R.string.ext__hub__system_hint),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    hubSystemRecommendations(spec.path, navController).forEach { rec ->
                        HubAddonRow(
                            icon = rec.icon,
                            title = stringRes(rec.titleRes),
                            subtitle = null,
                            onClick = rec.onClick,
                        )
                    }
                    HubAddonRow(
                        icon = Icons.Default.SmartButton,
                        title = stringRes(R.string.accessories__title),
                        subtitle = stringRes(R.string.accessories__summary),
                        onClick = { navController.navigate(Routes.Ext.Accessories) },
                    )
                }
            }

            // ---------------- tools & commands ----------------
            HubStaggerIn(index = 5) {
                HubSectionCard(title = stringRes(R.string.ext__hub__section_tools)) {
                    HubAddonRow(
                        icon = Icons.Default.Shop,
                        title = stringRes(R.string.ext__hub__visit_store),
                        subtitle = null,
                        onClick = { context.launchUrl("https://${BuildConfig.DRS_ADDONS_URL}/") },
                    )
                    HubAddonRow(
                        icon = Icons.AutoMirrored.Filled.Input,
                        title = stringRes(R.string.ext__hub__import_addon),
                        subtitle = stringRes(R.string.ext__home__info),
                        onClick = {
                            navController.navigate(Routes.Ext.Import(ExtensionImportScreenType.EXT_ANY, null))
                        },
                    )
                    HubAddonRow(
                        icon = Icons.Default.Add,
                        title = stringRes(R.string.ext__hub__create_theme),
                        subtitle = null,
                        onClick = {
                            navController.navigate(Routes.Ext.Edit("null", ThemeExtension.SERIAL_TYPE))
                        },
                    )
                    HubAddonRow(
                        icon = Icons.Outlined.Inventory2,
                        title = stringRes(R.string.packages__center__title),
                        subtitle = stringRes(R.string.packages__hub__subtitle),
                        onClick = { navController.navigate(Routes.Ext.Packages) },
                    )
                    HubAddonRow(
                        icon = Icons.Outlined.FileDownload,
                        title = stringRes(R.string.ext__hub__check_updates),
                        subtitle = stringRes(R.string.ext__update_box__in_app_hint),
                        onClick = { navController.navigate(Routes.Ext.CheckUpdates) },
                    )
                    HubAddonRow(
                        icon = Icons.Default.Archive,
                        title = stringRes(R.string.ext__hub__backup),
                        subtitle = null,
                        onClick = { navController.navigate(Routes.Settings.Backup) },
                    )
                    HubAddonRow(
                        icon = Icons.Default.Build,
                        title = stringRes(R.string.devtools__title),
                        subtitle = null,
                        onClick = { navController.navigate(Routes.Devtools.Home) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------------------
// Data layer of the hub
// ---------------------------------------------------------------------------

private fun hubIconOfType(ext: Extension): ImageVector = when (ext) {
    is ThemeExtension -> Icons.Default.Palette
    is LanguagePackExtension -> Icons.Default.Language
    else -> Icons.Default.Keyboard
}

private fun hubListTypeOf(ext: Extension): ExtensionListScreenType = when (ext) {
    is ThemeExtension -> ExtensionListScreenType.EXT_THEME
    is LanguagePackExtension -> ExtensionListScreenType.EXT_LANGUAGEPACK
    else -> ExtensionListScreenType.EXT_KEYBOARD
}

/** One row of the package catalog. */
private data class HubPackage(
    val representative: Extension,
    /** Asset family id for bundled packages, null for user imports. */
    val family: String?,
    val themeCount: Int,
    val keyboardCount: Int,
    val languagePackCount: Int,
    val isBundled: Boolean,
    /** Number of add-ons inside this package. */
    val total: Int,
)

/**
 * Groups every installed add-on by its origin package. Bundled packages
 * keep their asset family id (e.g. org.drs.themes.drs) while user-imported
 * archives collapse into a single "imported by you" pseudo package.
 */
private fun collectPackages(extensions: List<Extension>): List<HubPackage> {
    val bundled = extensions.filter { it.sourceRef != null && !it.sourceRef!!.isInternal }
    val imported = extensions.filter { it.sourceRef?.isInternal == true }

    val packages = bundled
        .groupBy { ext -> ext.sourceRef?.uri?.pathSegments?.getOrNull(2) ?: ext.meta.id }
        .map { (family, members) ->
            HubPackage(
                representative = members.first(),
                family = family,
                themeCount = members.count { it is ThemeExtension },
                keyboardCount = members.count { it !is ThemeExtension && it !is LanguagePackExtension },
                languagePackCount = members.count { it is LanguagePackExtension },
                isBundled = true,
                total = members.size,
            )
        }
        .sortedBy { it.family ?: "" }

    if (imported.isEmpty()) {
        return packages
    }
    return packages + HubPackage(
        representative = imported.first(),
        family = null,
        themeCount = imported.count { it is ThemeExtension },
        keyboardCount = imported.count { it !is ThemeExtension && it !is LanguagePackExtension },
        languagePackCount = imported.count { it is LanguagePackExtension },
        isBundled = false,
        total = imported.size,
    )
}

/**
 * Builds the localized counts summary of a package, e.g.
 * "6 ثيمات · 1 تخطيط · 3 حزم لغة".
 */
@Composable
private fun hubCountsSummary(pkg: HubPackage): String {
    val parts = mutableListOf<String>()
    if (pkg.themeCount > 0) {
        parts.add(stringRes(R.string.ext__hub__count_themes, "count" to pkg.themeCount.toString()))
    }
    if (pkg.keyboardCount > 0) {
        parts.add(stringRes(R.string.ext__hub__count_layouts, "count" to pkg.keyboardCount.toString()))
    }
    if (pkg.languagePackCount > 0) {
        parts.add(stringRes(R.string.ext__hub__count_packs, "count" to pkg.languagePackCount.toString()))
    }
    return parts.joinToString(" · ")
}

/**
 * Resolves the friendly display title of a package; falls back to the raw
 * family id for unknown (future) packages.
 */
@Composable
private fun hubPackageTitle(pkg: HubPackage): String {
    if (!pkg.isBundled) {
        return stringRes(R.string.ext__hub__pkg_user)
    }
    val res = when (pkg.family) {
        "org.drs.themes" -> R.string.ext__hub__pkg_themes_core
        "org.drs.themes.arabic" -> R.string.ext__hub__pkg_themes_arabic
        "org.drs.themes.drs" -> R.string.ext__hub__pkg_themes_drs
        "org.drs.themes.extra" -> R.string.ext__hub__pkg_themes_extra
        "org.drs.themes.my" -> R.string.ext__hub__pkg_themes_my
        "org.drs.layouts" -> R.string.ext__hub__pkg_layouts_core
        "org.drs.layouts.drs" -> R.string.ext__hub__pkg_layouts_drs
        "org.drs.localization" -> R.string.ext__hub__pkg_localization
        "org.drs.composers" -> R.string.ext__hub__pkg_composers
        "org.drs.currencysets" -> R.string.ext__hub__pkg_currencysets
        "org.drs.languagepack" -> R.string.ext__hub__pkg_languagepack_core
        "org.drs.languagepack.arabic" -> R.string.ext__hub__pkg_languagepack_arabic
        "org.drs.hanshapebasedbasicpack" -> R.string.ext__hub__pkg_han_shapebased
        else -> 0
    }
    return if (res != 0) stringRes(res) else (pkg.family ?: pkg.representative.meta.id)
}

private data class HubRecommendation(
    val icon: ImageVector,
    val titleRes: Int,
    val onClick: () -> Unit,
)

/** Curated add-on suggestions per DRS system identity. */
private fun hubSystemRecommendations(path: DrsUserPath, navController: NavController): List<HubRecommendation> =
    when (path) {
        DrsUserPath.NORMAL -> listOf(
            HubRecommendation(Icons.Default.Palette, R.string.ext__hub__pkg_themes_arabic) {
                navController.navigate(Routes.Ext.List(ExtensionListScreenType.EXT_THEME, false))
            },
            HubRecommendation(Icons.Default.Language, R.string.ext__hub__pkg_languagepack_arabic) {
                navController.navigate(Routes.Ext.List(ExtensionListScreenType.EXT_LANGUAGEPACK, false))
            },
            HubRecommendation(Icons.Default.TouchApp, R.string.ext__hub__rec_gestures) {
                navController.navigate(Routes.Settings.DrsGestures)
            },
        )
        DrsUserPath.TECHNICAL -> listOf(
            HubRecommendation(Icons.Default.Keyboard, R.string.ext__hub__pkg_layouts_drs) {
                navController.navigate(Routes.Ext.List(ExtensionListScreenType.EXT_KEYBOARD, false))
            },
            HubRecommendation(Icons.Default.Palette, R.string.ext__hub__pkg_themes_drs) {
                navController.navigate(Routes.Ext.List(ExtensionListScreenType.EXT_THEME, false))
            },
            HubRecommendation(Icons.Default.TouchApp, R.string.ext__hub__rec_shortcuts) {
                navController.navigate(Routes.Settings.DrsShortcuts)
            },
        )
        DrsUserPath.HYBRID -> listOf(
            HubRecommendation(Icons.Default.Palette, R.string.ext__hub__pkg_themes_extra) {
                navController.navigate(Routes.Ext.List(ExtensionListScreenType.EXT_THEME, false))
            },
            HubRecommendation(Icons.Default.Extension, R.string.ext__hub__rec_all) {
                navController.navigate(Routes.Ext.List(ExtensionListScreenType.EXT_KEYBOARD, false))
            },
            HubRecommendation(Icons.Default.TouchApp, R.string.ext__hub__rec_control_center) {
                navController.navigate(Routes.Settings.DrsControlCenter)
            },
        )
    }

// ---------------------------------------------------------------------------
// UI building blocks of the hub
// ---------------------------------------------------------------------------

/** Hero card with live add-on statistics, tinted with the system accent. */
@Composable
private fun HubHeroCard(
    accent: Color,
    themesCount: Int,
    keyboardsCount: Int,
    languagePacksCount: Int,
    totalCount: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.05f)),
                ),
                RoundedCornerShape(20.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = stringRes(R.string.ext__hub__hero_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringRes(R.string.ext__hub__hero_subtitle),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HubStatChip(modifier = Modifier.weight(1f), accent = accent, value = themesCount, labelRes = R.string.ext__hub__stat_themes)
            HubStatChip(modifier = Modifier.weight(1f), accent = accent, value = keyboardsCount, labelRes = R.string.ext__hub__stat_layouts)
            HubStatChip(modifier = Modifier.weight(1f), accent = accent, value = languagePacksCount, labelRes = R.string.ext__hub__stat_packs)
            HubStatChip(modifier = Modifier.weight(1f), accent = accent, value = totalCount, labelRes = R.string.ext__hub__stat_total)
        }
    }
}

@Composable
private fun HubStatChip(modifier: Modifier, accent: Color, value: Int, labelRes: Int) {
    Column(
        modifier = modifier
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            text = stringRes(labelRes),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Section card carrying the DRS section identity (accent bar + title). */
@Composable
private fun HubSectionCard(
    title: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .background(accent, RoundedCornerShape(2.dp)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            content()
        }
    }
}

/** One selectable add-on family card with a count badge. */
@Composable
private fun HubTypeCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HubCountBadge(count = count, accent = accent)
    }
}

@Composable
private fun HubCountBadge(count: Int, accent: Color) {
    Text(
        text = count.toString(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = accent,
        modifier = Modifier
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** One package row inside the package catalog. */
@Composable
private fun HubPackageRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    badgeBundled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = stringRes(
                if (badgeBundled) R.string.ext__hub__badge_bundled else R.string.ext__hub__badge_user,
            ),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (badgeBundled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .background(
                    (if (badgeBundled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary)
                        .copy(alpha = 0.12f),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** A simple icon + (title, optional subtitle) list row. */
@Composable
private fun HubAddonRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Entry animation used by the hub sections: a gentle staggered fade +
 * rise that keeps the screen alive without stealing attention.
 */
@Composable
private fun HubStaggerIn(index: Int, content: @Composable () -> Unit) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        started = true
    }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
        label = "hubStagger",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 32f
        },
    ) {
        content()
    }
}
