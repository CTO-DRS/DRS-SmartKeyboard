/*
 * Copyright (C) 2020-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.theme

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.appContext
import com.drs.smartkeyboard.drs.DrsEventLog
import com.drs.smartkeyboard.drs.DrsThemeService
import com.drs.smartkeyboard.extensionManager
import com.drs.smartkeyboard.ime.smartbar.CachedInlineSuggestionsChipStyleSet
import com.drs.smartkeyboard.lib.devtools.flogInfo
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import com.drs.smartkeyboard.lib.ext.ExtensionMeta
import com.drs.smartkeyboard.lib.io.ZipUtils
import com.drs.smartkeyboard.lib.util.TimeUtils.javaLocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drs.lib.kotlin.collectIn
import org.drs.lib.kotlin.io.FsDir
import org.drs.lib.kotlin.io.deleteContentsRecursively
import org.drs.lib.kotlin.io.subDir
import org.drs.lib.kotlin.io.subFile
import org.drs.lib.snygg.SnyggStylesheet
import org.drs.lib.snygg.value.SnyggStaticColorValue
import java.time.LocalTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Core class which manages the keyboard theme. Note, that this does not affect the UI theme of the
 * Settings Activities.
 */
class ThemeManager(context: Context) {
    private val prefs by DrsPreferenceStore
    private val appContext by context.appContext()
    private val extensionManager by context.extensionManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _indexedThemeConfigs = MutableStateFlow(mapOf<ExtensionComponentName, ThemeExtensionComponent>() to 0)
    val indexedThemeConfigs get() = _indexedThemeConfigs.asStateFlow()
    private val indexedThemeConfigVersion = AtomicInteger(0)

    val previewThemeId = MutableStateFlow<ExtensionComponentName?>(null)
    val previewThemeInfo = MutableStateFlow<ThemeInfo?>(null)
    val configurationChangeCounter = MutableStateFlow(0)

    private val cachedThemeInfos = mutableListOf<ThemeInfo>()
    private val activeThemeGuard = Mutex(locked = false)
    private val _activeThemeInfo = MutableStateFlow(ThemeInfo.DEFAULT)
    val activeThemeInfo get() = _activeThemeInfo.asStateFlow()

    init {
        extensionManager.themes.collectIn(scope) { themeExtensions ->
            val version = indexedThemeConfigVersion.incrementAndGet()
            _indexedThemeConfigs.value = buildMap {
                for (themeExtension in themeExtensions) {
                    for (themeComponent in themeExtension.themes) {
                        put(ExtensionComponentName(themeExtension.meta.id, themeComponent.id), themeComponent)
                    }
                }
            } to version
        }
        indexedThemeConfigs.collectIn(scope) {
            // DRS v1.5.0: the extension-driven cache reset used to leak
            // EVERY loaded dir (the cache is cleared, but the unzipped
            // folders stayed in cacheDir/loaded forever). Delete the
            // folders of the infos being dropped before clearing.
            updateActiveTheme {
                cachedThemeInfos.forEach { info ->
                    runCatching {
                        info.loadedDir?.let { dir ->
                            java.io.File(dir.canonicalPath).deleteRecursively()
                        }
                    }
                }
                cachedThemeInfos.clear()
            }
        }
        combine(
            prefs.theme.mode.asFlow(),
            prefs.theme.dayThemeId.asFlow(),
            prefs.theme.nightThemeId.asFlow(),
            previewThemeId,
            previewThemeInfo,
            configurationChangeCounter,
        ) {}.collectIn(scope) {
            updateActiveTheme()
        }
        // DRS v2.8.0 «خلفيتك من ألبومك»: the user background image is patched
        // into the stylesheet AT LOAD TIME, so any change must evict the cached
        // theme infos (same eviction pattern as the extension-driven reset
        // above) — the next updateActiveTheme re-loads and re-patches.
        combine(
            prefs.theme.backgroundImage.asFlow(),
            prefs.theme.backgroundDimness.asFlow(),
        ) { _, _ ->
            updateActiveTheme {
                cachedThemeInfos.forEach { info ->
                    runCatching {
                        info.loadedDir?.let { dir ->
                            java.io.File(dir.canonicalPath).deleteRecursively()
                        }
                    }
                }
                cachedThemeInfos.clear()
            }
        }.collectIn(scope) { }
    }

    /**
     * Updates the current theme ref and loads the corresponding theme, as well as notifies all
     * callback receivers about the new theme.
     */
    suspend fun updateActiveTheme(action: () -> Unit = { }) = activeThemeGuard.withLock {
        action()
        previewThemeInfo.value?.let { previewThemeInfo ->
            _activeThemeInfo.value = previewThemeInfo
            return@withLock
        }
        val activeName = evaluateActiveThemeName()
        val cachedInfo = cachedThemeInfos.find { it.name == activeName }
        if (cachedInfo != null) {
            _activeThemeInfo.value = cachedInfo
            return@withLock
        }
        val themeExt = extensionManager.getExtensionById(activeName.extensionId) as? ThemeExtension
        val themeExtRef = themeExt?.sourceRef
        if (themeExtRef == null) {
            return@withLock
        }
        val themeConfig = themeExt.themes.find { it.id == activeName.componentId }
        if (themeConfig == null) {
            return@withLock
        }
        // DRS v1.5.0: bound the loaded-dir cache — the upstream TODO said
        // "this leaks the loaded dir": every theme switch unzipped into a
        // fresh cacheDir/loaded/<uuid> folder and never cleaned up. Keep
        // at most 3 cached infos (day + night + preview alternation fits)
        // and physically delete the folder of every evicted entry.
        val loadedRoot = appContext.cacheDir.subDir("loaded")
        runCatching {
            val livePaths = cachedThemeInfos.mapNotNull { it.loadedDir?.canonicalPath }.toSet()
            loadedRoot.listFiles()?.forEach { stale ->
                if (stale.canonicalPath !in livePaths) {
                    stale.deleteRecursively()
                }
            }
        }
        val loadedDir = loadedRoot.subDir(UUID.randomUUID().toString())
        runCatching {
            loadedDir.mkdirs()
            loadedDir.deleteContentsRecursively()
            ZipUtils.unzip(appContext, themeExtRef, loadedDir).getOrThrow()
            flogInfo { "Loaded extension ${themeExt.meta.id} into $loadedDir" }
            // DRS p7 (E3-4) — canonical containment of the stylesheet INSIDE the loaded
            // dir: a crafted/typo'd stylesheetPath (e.g. "../../files/drs_state.json")
            // used to be read as JSON from anywhere in the app sandbox. Violation →
            // error() inside this runCatching → the existing LoadFailure path.
            val stylesheetFile = loadedDir.subFile(themeConfig.stylesheetPath())
            val stylesheetCanonical = stylesheetFile.canonicalPath
            val loadedDirCanonical = loadedDir.canonicalPath
            if (stylesheetCanonical != loadedDirCanonical &&
                !stylesheetCanonical.startsWith(loadedDirCanonical + java.io.File.separator)
            ) {
                error(
                    "Stylesheet path '${themeConfig.stylesheetPath()}' of component " +
                        "${themeConfig.id} escapes the loaded theme dir"
                )
            }
            val stylesheetJson = stylesheetFile.readText()
            val parsed = SnyggStylesheet.fromJson(stylesheetJson).getOrThrow()
            // DRS v2.8.0: inject the user background image IN MEMORY — the theme
            // file on disk is never modified, and the total patcher means a
            // broken preference value can never fail a theme load.
            DrsThemeBackgroundPatcher.apply(
                parsed,
                prefs.theme.backgroundImage.get(),
                prefs.theme.backgroundDimness.get(),
            )
        }.fold(
            onSuccess = { newStylesheet ->
                val newInfo = ThemeInfo(activeName, themeConfig, newStylesheet, loadedDir, null)
                // DRS v1.5.0: evict the oldest cached infos beyond the
                // 3-entry bound and delete their loaded dirs so repeated
                // theme switching cannot grow the cache without limit.
                // The info currently live in _activeThemeInfo is never
                // evicted, so no in-flight composition loses its assets.
                val liveName = _activeThemeInfo.value?.name
                while (cachedThemeInfos.size >= 3) {
                    val idx = cachedThemeInfos
                        .indexOfFirst { it.name != liveName }
                        .let { if (it >= 0) it else 0 }
                    val evicted = cachedThemeInfos.removeAt(idx)
                    runCatching {
                        evicted.loadedDir?.let { dir ->
                            java.io.File(dir.canonicalPath).deleteRecursively()
                        }
                    }
                }
                cachedThemeInfos.add(newInfo)
                _activeThemeInfo.value = newInfo
            },
            onFailure = { cause ->
                // DRS v1.0.6: sanitized event log entry for theme load
                // failures (class name only - never file or user content).
                runCatching {
                    DrsEventLog.recordError(
                        DrsEventLog.Categories.THEME,
                        "${themeConfig.id}: ${DrsEventLog.throwableDetail(cause)}",
                    )
                }
                _activeThemeInfo.value = ThemeInfo.DEFAULT.copy(
                    loadFailure = LoadFailure(themeExt.meta, themeConfig, cause)
                )
            },
        )
        // DRS M2.4 — every published theme change bumps the save generation:
        // the editor (and any listener) compares before/after to prove the
        // live keyboard actually re-colored. Eagerly-created shared instance
        // and a monotonic, deterministic counter — see DrsThemeService.
        DrsThemeService.notifyPublished()
    }

    private fun evaluateActiveThemeName(): ExtensionComponentName {
        previewThemeId.value?.let { return it }
        return when (prefs.theme.mode.get()) {
            ThemeMode.ALWAYS_DAY -> {
                prefs.theme.dayThemeId.get()
            }
            ThemeMode.ALWAYS_NIGHT -> {
                prefs.theme.nightThemeId.get()
            }
            ThemeMode.FOLLOW_SYSTEM -> if (appContext.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            ) {
                prefs.theme.nightThemeId.get()
            } else {
                prefs.theme.dayThemeId.get()
            }
            ThemeMode.FOLLOW_TIME -> {
                val current = LocalTime.now()
                val sunrise = prefs.theme.sunriseTime.get().javaLocalTime
                val sunset = prefs.theme.sunsetTime.get().javaLocalTime
                if (ThemeDayWindow.isDaytime(current, sunrise, sunset)) {
                    prefs.theme.dayThemeId.get()
                } else {
                    prefs.theme.nightThemeId.get()
                }
            }
        }
    }

    /**
     * DRS v1.0.8: cycles the keyboard theme of the currently effective
     * day/night slot to the next installed theme (deterministic order by
     * extension id then component id). The live keyboard re-styles
     * immediately because the theme prefs drive [updateActiveTheme].
     *
     * @return the newly selected theme id, or null when fewer than two
     *         themes are installed (nothing to cycle).
     */
    suspend fun cycleTheme(): ExtensionComponentName? {
        val all = _indexedThemeConfigs.value.first.keys
        if (all.size < 2) return null
        val current = evaluateActiveThemeName()
        val ordered = all.sortedWith(compareBy({ it.extensionId }, { it.componentId }))
        val next = ordered[(ordered.indexOf(current) + 1).mod(ordered.size)]
        val isNightSlot = when (prefs.theme.mode.get()) {
            ThemeMode.ALWAYS_NIGHT -> true
            ThemeMode.ALWAYS_DAY -> false
            ThemeMode.FOLLOW_SYSTEM -> appContext.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            ThemeMode.FOLLOW_TIME -> {
                // DRS v1.28.0 audit fix (Medium): cycleTheme used the old
                // non-wrapping `now !in sunrise..sunset` test while the real
                // evaluator uses ThemeDayWindow.isDaytime (v1.21.0 midnight
                // wrap). With a night-shift schedule (sunrise 22:00, sunset
                // 06:00) the two disagreed — at 03:00 the evaluator showed
                // the day theme but cycleTheme wrote the night slot, so
                // "cycle theme" appeared to do nothing. One decision, one
                // function.
                val now = LocalTime.now()
                val sunrise = prefs.theme.sunriseTime.get().javaLocalTime
                val sunset = prefs.theme.sunsetTime.get().javaLocalTime
                !ThemeDayWindow.isDaytime(now, sunrise, sunset)
            }
        }
        if (isNightSlot) {
            prefs.theme.nightThemeId.set(next)
        } else {
            prefs.theme.dayThemeId.set(next)
        }
        return next
    }

    /**
     * Creates a new inline suggestion UI bundle.
     *
     * @param context The context of the parent view/controller.
     *
     * @return A bundle containing all necessary attributes for the inline suggestion views to properly display.
     */
    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.R)
    fun createInlineSuggestionUiStyleBundle(context: Context): Bundle? {
        val styleSet = CachedInlineSuggestionsChipStyleSet ?: return null
        val bgColor = styleSet.background(default = Color.White)
        val fgColor = styleSet.foreground(default = Color.Black)

        val bgDrawableId = R.drawable.inline_autofill_chip_bg
        val bgDrawable = Icon.createWithResource(context, bgDrawableId).apply {
            setTint(bgColor.toArgb())
        }
        val chipStyle = ViewStyle.Builder().run {
            setBackground(bgDrawable)
            setPadding(
                context.resources.getDimension(R.dimen.suggestion_chip_bg_padding_start).toInt(),
                context.resources.getDimension(R.dimen.suggestion_chip_bg_padding_top).toInt(),
                context.resources.getDimension(R.dimen.suggestion_chip_bg_padding_end).toInt(),
                context.resources.getDimension(R.dimen.suggestion_chip_bg_padding_bottom).toInt(),
            )
            build()
        }
        val iconStyle = ImageViewStyle.Builder().run {
            setLayoutMargin(0, 0, 0, 0)
            build()
        }
        val titleStyle = TextViewStyle.Builder().run {
            setLayoutMargin(
                context.resources.getDimension(R.dimen.suggestion_chip_fg_title_margin_start).toInt(),
                context.resources.getDimension(R.dimen.suggestion_chip_fg_title_margin_top).toInt(),
                context.resources.getDimension(R.dimen.suggestion_chip_fg_title_margin_end).toInt(),
                context.resources.getDimension(R.dimen.suggestion_chip_fg_title_margin_bottom).toInt(),
            )
            setTextColor(fgColor.toArgb())
            setTextSize(16f)
            build()
        }
        val subtitleStyle = TextViewStyle.Builder().run {
            setLayoutMargin(
                context.resources.getDimension(R.dimen.suggestion_chip_fg_subtitle_margin_start).toInt(),
                context.resources.getDimension(R.dimen.suggestion_chip_fg_subtitle_margin_top).toInt(),
                context.resources.getDimension(R.dimen.suggestion_chip_fg_subtitle_margin_end).toInt(),
                context.resources.getDimension(R.dimen.suggestion_chip_fg_subtitle_margin_bottom).toInt(),
            )
            setTextColor(ColorUtils.setAlphaComponent(fgColor.toArgb(), 150))
            setTextSize(14f)
            build()
        }
        val suggestionStyle = InlineSuggestionUi.newStyleBuilder().run {
            setSingleIconChipStyle(chipStyle)
            setChipStyle(chipStyle)
            setStartIconStyle(iconStyle)
            setEndIconStyle(iconStyle)
            setTitleStyle(titleStyle)
            setSubtitleStyle(subtitleStyle)
            build()
        }
        return UiVersions.newStylesBuilder().run {
            addStyle(suggestionStyle)
            build()
        }
    }

    data class ThemeInfo(
        val name: ExtensionComponentName,
        val config: ThemeExtensionComponent,
        val stylesheet: SnyggStylesheet,
        val loadedDir: FsDir?,
        val loadFailure: LoadFailure?,
    ) {
        override fun toString(): String {
            return "ThemeInfo(name=$name, config=$config, loadedDir=$loadedDir)"
        }

        companion object {
            val DEFAULT = ThemeInfo(
                name = extCoreTheme("base"),
                config = ThemeExtensionComponentImpl(id = "base", label = "Base", authors = listOf()),
                stylesheet = DrsImeThemeBaseStyle,
                loadedDir = null,
                loadFailure = null,
            )
        }
    }

    data class LoadFailure(
        val extension: ExtensionMeta,
        val component: ThemeExtensionComponent,
        val cause: Throwable,
    )

    data class RemoteColors(
        val packageName: String,
        val colorPrimary: SnyggStaticColorValue?,
        val colorPrimaryVariant: SnyggStaticColorValue?,
        val colorSecondary: SnyggStaticColorValue?,
    ) {
        companion object {
            val DEFAULT = RemoteColors("undefined", null, null, null)
        }
    }

    /**
     * DRS v1.21.0 — the pure FOLLOW_TIME day-window decision. The old
     * inline `current in sunrise..sunset` collapsed to an empty range
     * whenever sunset ≤ sunrise (a night-shift schedule like 22:00→06:00,
     * or the degenerate equal pair), silently forcing night forever; the
     * day window now wraps midnight honestly, and the degenerate equal
     * pair falls back to day.
     */
    object ThemeDayWindow {
        fun isDaytime(current: LocalTime, sunrise: LocalTime, sunset: LocalTime): Boolean {
            return when {
                sunrise < sunset -> !current.isBefore(sunrise) && !current.isAfter(sunset)
                sunrise == sunset -> true
                else -> !current.isBefore(sunrise) || !current.isAfter(sunset)
            }
        }
    }
}
