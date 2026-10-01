/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.LocaleList
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.ui.DrsOnboardingActivity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.apptheme.DrsAppTheme
import com.drs.smartkeyboard.app.drsupdater.DrsUpdateCenter
import com.drs.smartkeyboard.app.ext.ExtensionImportScreenType
import com.drs.smartkeyboard.app.setup.NotificationPermissionState
import com.drs.smartkeyboard.appContext
import com.drs.smartkeyboard.cacheManager
import com.drs.smartkeyboard.lib.DrsLocale
import com.drs.smartkeyboard.lib.compose.DrsFloatingPreviewPill
import com.drs.smartkeyboard.lib.compose.LocalPreviewFieldController
import com.drs.smartkeyboard.lib.compose.PreviewKeyboardField
import com.drs.smartkeyboard.lib.compose.rememberPreviewFieldController
import com.drs.smartkeyboard.lib.util.AppVersionUtils
import org.drs.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.drs.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import java.util.concurrent.atomic.AtomicBoolean
import org.drs.lib.android.AndroidVersion
import org.drs.lib.android.hideAppIcon
import org.drs.lib.android.showAppIcon
import org.drs.lib.compose.ProvideLocalizedResources
import org.drs.lib.compose.conditional
import org.drs.lib.compose.stringRes
import org.drs.lib.kotlin.collectIn

enum class AppTheme(val id: String) {
    AUTO("auto"),
    AUTO_AMOLED("auto_amoled"),
    LIGHT("light"),
    DARK("dark"),
    AMOLED_DARK("amoled_dark");
}

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("LocalNavController not initialized")
}

class DrsAppActivity : ComponentActivity() {
    private val prefs by DrsPreferenceStore
    private val appContext by appContext()
    private val cacheManager by cacheManager()
    private var appTheme by mutableStateOf(AppTheme.AUTO)
    private var showAppIcon = true
    private var resourcesContext by mutableStateOf(this as Context)
    private var intentToBeHandled by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen should be installed before calling super.onCreate()
        installSplashScreen().apply {
            setKeepOnScreenCondition { !appContext.preferenceStoreLoaded.value }
        }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        prefs.other.settingsTheme.asFlow().collectIn(lifecycleScope) {
            appTheme = it
        }
        var isInitialSettingsLanguageEmission = true
        prefs.other.settingsLanguage.asFlow().collectIn(lifecycleScope) {
            val config = Configuration(resources.configuration)
            val locale = if (it == "auto") DrsLocale.default() else DrsLocale.fromTag(it)
            config.setLocale(locale.base)
            resourcesContext = createConfigurationContext(config)
            // DRS: keep the system per-app language (Android 13+) in sync with the
            // in-app language so that the launcher label and IME surfaces are native
            // Arabic by default. The initial emission only fills an UNSET value, so a
            // language explicitly chosen in system settings is never overridden here;
            // an active change made inside the app always propagates ("auto" resets
            // the per-app locale back to the system language).
            if (AndroidVersion.ATLEAST_API33_T) {
                runCatching {
                    val localeManager = getSystemService(LocaleManager::class.java)
                    if (localeManager != null) {
                        if (isInitialSettingsLanguageEmission) {
                            if (localeManager.applicationLocales.isEmpty && it != "auto") {
                                localeManager.setApplicationLocales(LocaleList.forLanguageTags(it))
                            }
                        } else {
                            if (it == "auto") {
                                localeManager.setApplicationLocales(LocaleList.getEmptyLocaleList())
                            } else {
                                localeManager.setApplicationLocales(LocaleList.forLanguageTags(it))
                            }
                        }
                    }
                }
                isInitialSettingsLanguageEmission = false
            }
        }
        if (AndroidVersion.ATMOST_API28_P) {
            prefs.other.showAppIcon.asFlow().collectIn(lifecycleScope) {
                showAppIcon = it
            }
        }

        // We defer the setContent call until the datastore model is loaded, until then the splash screen stays drawn
        val isModelLoaded = AtomicBoolean(false)
        appContext.preferenceStoreLoaded.collectIn(lifecycleScope) { loaded ->
            if (!loaded || isModelLoaded.getAndSet(true)) return@collectIn
            // Check if android 13+ is running and the NotificationPermission is not set
            if (AndroidVersion.ATLEAST_API33_T &&
                prefs.internal.notificationPermissionState.get() == NotificationPermissionState.NOT_SET
            ) {
                // update pref value to show the setup screen again
                prefs.internal.isImeSetUp.set(false)
            }
            AppVersionUtils.updateVersionOnInstallAndLastUse(this, prefs)
            // DRS Update Center: run the opt-in automatic check (if a schedule
            // is configured) once persisted preferences are available.
            DrsUpdateCenter.maybeAutoCheck(
                context = applicationContext,
                mode = prefs.updates.checkMode.get(),
                notifyEnabled = prefs.updates.notifyOnUpdate.get(),
                lastCheckTimestamp = prefs.updates.lastCheckTimestamp.get(),
                onChecked = { timestamp -> prefs.updates.lastCheckTimestamp.set(timestamp) },
            )
            // DRS: first-run adaptive onboarding (path detection, profile creation,
            // live preview, IME activation). Launched once; skipped state is persisted.
            if (!DrsStore.state.value.onboardingDone && !DrsStore.onboardingLaunchGuard) {
                DrsStore.onboardingLaunchGuard = true
                startActivity(Intent(this, DrsOnboardingActivity::class.java))
            }
            setContent {
                ProvideLocalizedResources(
                    resourcesContext,
                    appName = R.string.app_name,
                ) {
                    DrsAppTheme(theme = appTheme) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            AppContent()
                        }
                    }
                }
            }
            onNewIntent(intent)
        }
    }

    override fun onPause() {
        super.onPause()

        // App icon visibility control was restricted in Android 10.
        // See https://developer.android.com/reference/android/content/pm/LauncherApps#getActivityList(java.lang.String,%20android.os.UserHandle)
        if (AndroidVersion.ATMOST_API28_P) {
            if (showAppIcon) {
                this.showAppIcon()
            } else {
                this.hideAppIcon()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent.action == Intent.ACTION_VIEW && intent.categories?.contains(Intent.CATEGORY_BROWSABLE) == true) {
            intentToBeHandled = intent
            return
        }
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            intentToBeHandled = intent
            return
        }
        if (intent.action == Intent.ACTION_SEND && intent.clipData != null) {
            intentToBeHandled = intent
            return
        }
        intentToBeHandled = null
    }

    @Composable
    private fun AppContent() {
        val navController = rememberNavController()
        val previewFieldController = rememberPreviewFieldController()
        val scope = rememberCoroutineScope()

        val isImeSetUp by prefs.internal.isImeSetUp.collectAsState()

        CompositionLocalProvider(
            LocalNavController provides navController,
            LocalPreviewFieldController provides previewFieldController,
        ) {
            ProvideDefaultDialogPrefStrings(
                confirmLabel = stringRes(R.string.action__ok),
                dismissLabel = stringRes(R.string.action__cancel),
                neutralLabel = stringRes(R.string.action__default),
            ) {
                // DRS: outer full-screen Box hosts the draggable preview pill
                // overlay above EVERYTHING (screens + bottom preview field bar),
                // so the pill can be freely moved to any spot on the screen.
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            //.statusBarsPadding()
                            .navigationBarsPadding()
                            .conditional(LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                displayCutoutPadding()
                            }
                            .imePadding(),
                    ) {
                        Box(modifier = Modifier.weight(1.0f)) {
                            Routes.AppNavHost(
                                modifier = Modifier.fillMaxSize(),
                                navController = navController,
                                startDestination = if (isImeSetUp) Routes.Settings.Home::class else Routes.Setup.Screen::class,
                            )
                        }
                        PreviewKeyboardField(previewFieldController)
                    }
                    val previewPillHintShown by prefs.internal.previewPillHintShown.collectAsState()
                    val previewPillAnchorX by prefs.internal.previewPillAnchorX.collectAsState()
                    val previewPillAnchorY by prefs.internal.previewPillAnchorY.collectAsState()
                    DrsFloatingPreviewPill(
                        controller = previewFieldController,
                        modifier = Modifier.fillMaxSize(),
                        showHint = !previewPillHintShown,
                        savedAnchorX = previewPillAnchorX.takeIf { it >= 0f },
                        savedAnchorY = previewPillAnchorY.takeIf { it >= 0f },
                        onAnchorChanged = { fx, fy ->
                            scope.launch {
                                prefs.internal.previewPillAnchorX.set(fx)
                                prefs.internal.previewPillAnchorY.set(fy)
                            }
                        },
                        onHintDismiss = {
                            scope.launch { prefs.internal.previewPillHintShown.set(true) }
                        },
                    )
                }
            }
        }

        LaunchedEffect(intentToBeHandled) {
            val intent = intentToBeHandled
            if (intent != null) {
                // DRS p8 (C-2): external VIEW intents must never route into
                // the Devtools screens — the devtools check runs BEFORE both
                // branches below and simply ignores such a deep link, leaving
                // the normal entry destination in place (cold start stays on
                // the NavHost start destination; a warm onNewIntent keeps the
                // screen the user was already on).
                if (intent.action == Intent.ACTION_VIEW && isDevtoolsRouteDeeplink(intent.data)) {
                    // ignored — see isDevtoolsRouteDeeplink KDoc
                } else if (intent.action == Intent.ACTION_VIEW && intent.categories?.contains(Intent.CATEGORY_BROWSABLE) == true) {
                    navController.handleDeepLink(intent)
                } else {
                    val data = if (intent.action == Intent.ACTION_VIEW) {
                        intent.data
                    } else {
                        intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                    }
                    if (data != null) {
                        val workspace = runCatching { cacheManager.readFromUriIntoCache(data) }.getOrNull()
                        navController.navigate(Routes.Ext.Import(ExtensionImportScreenType.EXT_ANY, workspace?.uuid))
                    }
                }
            }
            intentToBeHandled = null
        }
    }

    /**
     * DRS p8 (C-2): decision — Devtools UI must never be reachable from
     * OUTSIDE the app. All four Devtools routes declared in Routes.kt
     * (Devtools.Home = "devtools", Devtools.AndroidLocales =
     * "devtools/android/locales", Devtools.AndroidSettings =
     * "devtools/android/settings", Devtools.ExportDebugLog =
     * "export-debug-log") sat on the BROWSABLE ui://drs-keyboard deep-link
     * surface, so any zero-permission app or web page could drive the
     * trusted keyboard's UI into devtools (system Global/Secure/System
     * settings-value enumeration; the export-debug-log screen auto-generates
     * a logcat -d report on open). Any ACTION_VIEW intent whose data resolves
     * to one of these routes is therefore rejected here, before routing —
     * covering both CATEGORY_BROWSABLE links and implicit external VIEW
     * intents. In-app navigation is NOT affected: it goes through
     * LocalNavController.navigate(Routes.Devtools.*) and never passes
     * through this intent path. This single choke point is complete because
     * navigation-compose 2.9.7 applies deep links ONLY through this explicit
     * handleDeepLink call (its NavHost does not auto-dispatch the activity's
     * launch intent, verified in the 2.9.7 artifact). No in-app code sends
     * devtools deep-link intents (the two internal ui:// VIEW senders target
     * settings/drs/control-center and ext/check-updates); any future flow
     * that wants devtools should navigate in-app as well.
     */
    private fun isDevtoolsRouteDeeplink(data: Uri?): Boolean {
        if (data == null) return false
        if (data.scheme != "ui" || data.host != "drs-keyboard") return false
        val route = data.path?.trimStart('/').orEmpty()
        return route == "devtools" ||
            route.startsWith("devtools/") ||
            route == "export-debug-log" ||
            route.startsWith("export-debug-log/")
    }
}
