/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.drs.smartkeyboard.app.devtools.AndroidLocalesScreen
import com.drs.smartkeyboard.app.devtools.AndroidSettingsScreen
import com.drs.smartkeyboard.app.devtools.DevtoolsScreen
import com.drs.smartkeyboard.app.devtools.ExportDebugLogScreen
import com.drs.smartkeyboard.app.ext.AccessoriesScreen
import com.drs.smartkeyboard.app.ext.DrsPackageCenterScreen
import com.drs.smartkeyboard.app.ext.DrsUpdateCenterScreen
import com.drs.smartkeyboard.app.ext.ExtensionEditScreen
import com.drs.smartkeyboard.app.ext.ExtensionExportScreen
import com.drs.smartkeyboard.app.ext.ExtensionHomeScreen
import com.drs.smartkeyboard.app.ext.ExtensionImportScreen
import com.drs.smartkeyboard.app.ext.ExtensionImportScreenType
import com.drs.smartkeyboard.app.ext.ExtensionListScreen
import com.drs.smartkeyboard.app.ext.ExtensionListScreenType
import com.drs.smartkeyboard.app.ext.ExtensionViewScreen
import com.drs.smartkeyboard.app.settings.HomeScreen
import com.drs.smartkeyboard.app.settings.about.AboutScreen
import com.drs.smartkeyboard.app.settings.about.ThirdPartyLicensesScreen
import com.drs.smartkeyboard.app.settings.advanced.BackupScreen
import com.drs.smartkeyboard.app.settings.advanced.OtherScreen
import com.drs.smartkeyboard.app.settings.advanced.PhysicalKeyboardScreen
import com.drs.smartkeyboard.app.settings.advanced.RestoreScreen
import com.drs.smartkeyboard.app.settings.help.HelpScreen
import com.drs.smartkeyboard.app.settings.privacy.PrivacyScreen
import com.drs.smartkeyboard.app.settings.clipboard.ClipboardScreen
import com.drs.smartkeyboard.app.settings.dictionary.DictionaryScreen
import com.drs.smartkeyboard.app.settings.dictionary.UserDictionaryScreen
import com.drs.smartkeyboard.app.settings.dictionary.UserDictionaryType
import com.drs.smartkeyboard.app.settings.gestures.GesturesScreen
import com.drs.smartkeyboard.app.settings.keyboard.InputFeedbackScreen
import com.drs.smartkeyboard.app.settings.keyboard.KeyboardScreen
import com.drs.smartkeyboard.app.settings.localization.LanguagePackManagerScreen
import com.drs.smartkeyboard.app.settings.localization.LanguagePackManagerScreenAction
import com.drs.smartkeyboard.app.settings.localization.LocalizationScreen
import com.drs.smartkeyboard.app.settings.localization.SelectLocaleScreen
import com.drs.smartkeyboard.app.settings.localization.SubtypeEditorScreen
import com.drs.smartkeyboard.app.settings.media.MediaScreen
import com.drs.smartkeyboard.app.settings.smartbar.SmartbarScreen
import com.drs.smartkeyboard.app.settings.theme.ThemeManagerScreen
import com.drs.smartkeyboard.app.settings.theme.ThemeManagerScreenAction
import com.drs.smartkeyboard.app.settings.theme.ThemeScreen
import com.drs.smartkeyboard.app.settings.typing.TypingScreen
import com.drs.smartkeyboard.app.setup.SetupScreen
import com.drs.smartkeyboard.drs.ui.DrsControlCenterScreen
import com.drs.smartkeyboard.drs.ui.DrsUnifiedDashboardScreen
import com.drs.smartkeyboard.drs.ui.DrsUnifiedToolsScreen
import com.drs.smartkeyboard.drs.ui.DrsUnifiedBasicsScreen
import com.drs.smartkeyboard.drs.ui.DrsUnifiedWritingScreen
import com.drs.smartkeyboard.drs.ui.DrsUnifiedStatsScreen
import com.drs.smartkeyboard.drs.ui.DrsUnifiedSystemScreen
import com.drs.smartkeyboard.app.ext.DrsRewardsScreen
import com.drs.smartkeyboard.drs.ui.DrsDiagnosticsScreen
import com.drs.smartkeyboard.drs.ui.DrsGesturesScreen
import com.drs.smartkeyboard.drs.ui.DrsProfilesScreen
import com.drs.smartkeyboard.drs.ui.DrsPerformanceScreen
import com.drs.smartkeyboard.drs.ui.DrsShortcutsScreen
import com.drs.smartkeyboard.drs.ui.DrsTechToolbarScreen
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Deeplink(val path: String)

inline fun <reified T : Any> NavGraphBuilder.composableWithDeepLink(
    kClass: KClass<T>,
    noinline content: @Composable (AnimatedContentScope.(NavBackStackEntry) -> Unit),
) {
    val deeplink = requireNotNull(kClass.annotations.firstOrNull { it is Deeplink } as? Deeplink) {
        "faulty class: $kClass with annotations ${kClass.annotations}"
    }
    composable<T>(
        deepLinks = listOf(navDeepLink<T>(basePath = "ui://drs-keyboard/${deeplink.path}")),
        content = content,
    )
}

object Routes {
    object Setup {
        @Serializable
        object Screen
    }

    object Settings {
        @Serializable
        @Deeplink("settings/home")
        object Home

        @Serializable
        @Deeplink("settings/localization")
        object Localization

        @Serializable
        @Deeplink("settings/localization/select-locale")
        object SelectLocale

        @Serializable
        @Deeplink("settings/localization/language-pack-manage")
        data class LanguagePackManager(val action: LanguagePackManagerScreenAction)

        @Serializable
        @Deeplink("settings/localization/subtype/add")
        object SubtypeAdd

        @Serializable
        @Deeplink("settings/localization/subtype/edit")
        data class SubtypeEdit(val id: Long)

        @Serializable
        @Deeplink("settings/theme")
        object Theme

        @Serializable
        @Deeplink("settings/theme/manage")
        data class ThemeManager(val action: ThemeManagerScreenAction)

        @Serializable
        @Deeplink("settings/keyboard")
        object Keyboard

        @Serializable
        @Deeplink("settings/keyboard/input-feedback")
        object InputFeedback

        @Serializable
        @Deeplink("settings/smartbar")
        object Smartbar

        @Serializable
        @Deeplink("settings/typing")
        object Typing

        @Serializable
        @Deeplink("settings/dictionary")
        object Dictionary

        @Serializable
        @Deeplink("settings/dictionary/user-dictionary")
        data class UserDictionary(val type: UserDictionaryType)

        @Serializable
        @Deeplink("settings/gestures")
        object Gestures

        @Serializable
        @Deeplink("settings/clipboard")
        object Clipboard

        @Serializable
        @Deeplink("settings/media")
        object Media

        @Serializable
        @Deeplink("settings/other")
        object Other

        @Serializable
        @Deeplink("settings/other/physical-keyboard")
        object PhysicalKeyboard

        @Serializable
        @Deeplink("settings/other/backup")
        object Backup

        @Serializable
        @Deeplink("settings/other/restore")
        object Restore

        @Serializable
        @Deeplink("settings/about")
        object About

        @Serializable
        @Deeplink("settings/about/third-party-licenses")
        object ThirdPartyLicenses

        @Serializable
        @Deeplink("settings/help")
        object Help

        @Serializable
        @Deeplink("settings/privacy")
        object Privacy

        @Serializable
        @Deeplink("settings/drs/control-center")
        object DrsControlCenter

        @Serializable
        @Deeplink("settings/drs/diagnostics")
        object DrsDiagnostics

        @Serializable
        @Deeplink("settings/drs/shortcuts")
        object DrsShortcuts

        @Serializable
        @Deeplink("settings/drs/profiles")
        object DrsProfiles

        @Serializable
        @Deeplink("settings/drs/gestures")
        object DrsGestures

        @Serializable
        @Deeplink("settings/drs/rewards")
        object DrsRewards

        @Serializable
        @Deeplink("settings/drs/tech-toolbar")
        object DrsTechToolbar

        @Serializable
        @Deeplink("settings/drs/performance")
        object DrsPerformance

        // DRS v1.0.7: unified "كلاهما" system screens.
        @Serializable
        @Deeplink("settings/drs/unified-dashboard")
        object DrsUnifiedDashboard

        @Serializable
        @Deeplink("settings/drs/unified-tools")
        object DrsUnifiedTools

        @Serializable
        @Deeplink("settings/drs/unified-basics")
        object DrsUnifiedBasics

        @Serializable
        @Deeplink("settings/drs/unified-writing")
        object DrsUnifiedWriting

        @Serializable
        @Deeplink("settings/drs/unified-system")
        object DrsUnifiedSystem

        // DRS v1.0.8: real daily usage statistics (local counts only).
        @Serializable
        @Deeplink("settings/drs/unified-stats")
        object DrsUnifiedStats
    }

    object Devtools {
        @Serializable
        @Deeplink("devtools")
        object Home

        @Serializable
        @Deeplink("devtools/android/locales")
        object AndroidLocales

        @Serializable
        @Deeplink("devtools/android/settings")
        data class AndroidSettings(val name: String)

        @Serializable
        @Deeplink("export-debug-log")
        object ExportDebugLog
    }

    object Ext {
        @Serializable
        @Deeplink("ext")
        object Home

        @Serializable
        @Deeplink("ext/accessories")
        object Accessories

        @Serializable
        @Deeplink("ext/list")
        data class List(val type: ExtensionListScreenType, val showUpdate: Boolean? = null)

        @Serializable
        @Deeplink("ext/edit")
        data class Edit(val id: String, @SerialName("create") val serialType: String? = null)

        @Serializable
        @Deeplink("ext/export")
        data class Export(val id: String)

        @Serializable
        @Deeplink("ext/import")
        data class Import(val type: ExtensionImportScreenType, val uuid: String? = null)

        @Serializable
        @Deeplink("ext/view")
        data class View(val id: String)

        @Serializable
        @Deeplink("ext/check-updates")
        object CheckUpdates

        @Serializable
        @Deeplink("ext/packages")
        object Packages
    }

    @Composable
    fun AppNavHost(
        modifier: Modifier,
        navController: NavHostController,
        startDestination: KClass<*>,
    ) {
        NavHost(
            modifier = modifier,
            navController = navController,
            startDestination = startDestination,
            enterTransition = {
                slideIn { IntOffset(it.width, 0) } + fadeIn()
            },
            exitTransition = {
                slideOut { IntOffset(-it.width, 0) } + fadeOut()
            },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = {
                scaleOut(
                    targetScale = 0.85F,
                    transformOrigin = TransformOrigin(pivotFractionX = 0.8f, pivotFractionY = 0.5f)
                ) + fadeOut(spring(stiffness = Spring.StiffnessMedium))
            },
        ) {
            composable<Setup.Screen> { SetupScreen() }

            composableWithDeepLink(Settings.Home::class) { HomeScreen() }

            composableWithDeepLink(Settings.Localization::class) { LocalizationScreen() }
            composableWithDeepLink(Settings.SelectLocale::class) { SelectLocaleScreen() }
            composableWithDeepLink(Settings.LanguagePackManager::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.LanguagePackManager>()
                LanguagePackManagerScreen(payload.action)
            }
            composableWithDeepLink(Settings.SubtypeAdd::class) { SubtypeEditorScreen(null) }
            composableWithDeepLink(Settings.SubtypeEdit::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.SubtypeEdit>()
                SubtypeEditorScreen(payload.id)
            }

            composableWithDeepLink(Settings.Theme::class) { ThemeScreen() }
            composableWithDeepLink(Settings.ThemeManager::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.ThemeManager>()
                ThemeManagerScreen(payload.action)
            }

            composableWithDeepLink(Settings.Keyboard::class) { KeyboardScreen() }
            composableWithDeepLink(Settings.InputFeedback::class) { InputFeedbackScreen() }

            composableWithDeepLink(Settings.Smartbar::class) { SmartbarScreen() }

            composableWithDeepLink(Settings.Typing::class) { TypingScreen() }

            composableWithDeepLink(Settings.Dictionary::class) { DictionaryScreen() }
            composableWithDeepLink(Settings.UserDictionary::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.UserDictionary>()
                UserDictionaryScreen(payload.type)
            }

            composableWithDeepLink(Settings.Gestures::class) { GesturesScreen() }

            composableWithDeepLink(Settings.Clipboard::class) { ClipboardScreen() }

            composableWithDeepLink(Settings.Media::class) { MediaScreen() }

            composableWithDeepLink(Settings.Other::class) { OtherScreen() }
            composableWithDeepLink(Settings.PhysicalKeyboard::class) { PhysicalKeyboardScreen() }
            composableWithDeepLink(Settings.Backup::class) { BackupScreen() }
            composableWithDeepLink(Settings.Restore::class) { RestoreScreen() }
            composableWithDeepLink(Settings.Help::class) { HelpScreen() }
            composableWithDeepLink(Settings.Privacy::class) { PrivacyScreen() }
            composableWithDeepLink(Settings.About::class) { AboutScreen() }
            composableWithDeepLink(Settings.ThirdPartyLicenses::class) { ThirdPartyLicensesScreen() }

            composableWithDeepLink(Settings.DrsControlCenter::class) { DrsControlCenterScreen() }
            composableWithDeepLink(Settings.DrsDiagnostics::class) { DrsDiagnosticsScreen() }
            composableWithDeepLink(Settings.DrsShortcuts::class) { DrsShortcutsScreen() }
            composableWithDeepLink(Settings.DrsProfiles::class) { DrsProfilesScreen() }
            composableWithDeepLink(Settings.DrsGestures::class) { DrsGesturesScreen() }
            composableWithDeepLink(Settings.DrsRewards::class) { DrsRewardsScreen() }
            composableWithDeepLink(Settings.DrsTechToolbar::class) { DrsTechToolbarScreen() }
            composableWithDeepLink(Settings.DrsPerformance::class) { DrsPerformanceScreen() }

            // DRS v1.0.7: unified "كلاهما" system screens.
            composableWithDeepLink(Settings.DrsUnifiedDashboard::class) { DrsUnifiedDashboardScreen() }
            composableWithDeepLink(Settings.DrsUnifiedTools::class) { DrsUnifiedToolsScreen() }
            composableWithDeepLink(Settings.DrsUnifiedBasics::class) { DrsUnifiedBasicsScreen() }
            composableWithDeepLink(Settings.DrsUnifiedWriting::class) { DrsUnifiedWritingScreen() }
            composableWithDeepLink(Settings.DrsUnifiedSystem::class) { DrsUnifiedSystemScreen() }
            composableWithDeepLink(Settings.DrsUnifiedStats::class) { DrsUnifiedStatsScreen() }

            composableWithDeepLink(Devtools.Home::class) { DevtoolsScreen() }
            composableWithDeepLink(Devtools.AndroidLocales::class) { AndroidLocalesScreen() }
            composableWithDeepLink(Devtools.AndroidSettings::class) { navBackStack ->
                val payload = navBackStack.toRoute<Devtools.AndroidSettings>()
                AndroidSettingsScreen(payload.name)
            }
            composableWithDeepLink(Devtools.ExportDebugLog::class) { ExportDebugLogScreen() }

            composableWithDeepLink(Ext.Home::class) { ExtensionHomeScreen() }
            composableWithDeepLink(Ext.Accessories::class) { AccessoriesScreen() }
            composableWithDeepLink(Ext.List::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.List>()
                val showUpdate = payload.showUpdate != null && payload.showUpdate
                ExtensionListScreen(payload.type, showUpdate)
            }
            composableWithDeepLink(Ext.Edit::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.Edit>()
                val extensionId = payload.id
                val serialType = payload.serialType
                ExtensionEditScreen(
                    id = extensionId,
                    createSerialType = serialType.takeIf { !it.isNullOrBlank() },
                )
            }
            composableWithDeepLink(Ext.Export::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.Export>()
                val extensionId = payload.id
                ExtensionExportScreen(id = extensionId)
            }
            composableWithDeepLink(Ext.Import::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.Import>()
                val uuid = payload.uuid
                ExtensionImportScreen(payload.type, uuid)
            }
            composableWithDeepLink(Ext.View::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.View>()
                val extensionId = payload.id
                ExtensionViewScreen(id = extensionId)
            }
            composableWithDeepLink(Ext.CheckUpdates::class) {
                DrsUpdateCenterScreen()
            }
            composableWithDeepLink(Ext.Packages::class) {
                DrsPackageCenterScreen()
            }
        }
    }
}
