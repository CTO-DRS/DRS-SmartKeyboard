/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.drs.smartkeyboard.lib.compose

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceModel
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.LocalNavController
import org.drs.jetpref.datastore.ui.PreferenceLayout
import org.drs.jetpref.datastore.ui.PreferenceUiContent
import org.drs.lib.android.AndroidVersion
import org.drs.lib.compose.DrsAppBar
import org.drs.lib.compose.DrsIconButton
import org.drs.lib.compose.autoMirrorForRtl
import org.drs.lib.compose.drsVerticalScroll

@Composable
fun DrsScreen(builder: @Composable DrsScreenScope.() -> Unit) {
    val scope = remember { DrsScreenScopeImpl() }
    builder(scope)
    scope.Render()
}

typealias DrsScreenActions = @Composable RowScope.() -> Unit
typealias DrsScreenBottomBar = @Composable () -> Unit
typealias DrsScreenContent = PreferenceUiContent<DrsPreferenceModel>
typealias DrsScreenFab = @Composable () -> Unit
typealias DrsScreenNavigationIcon = @Composable () -> Unit
// DRS: a fully custom top bar (receives the screen scroll behavior so it can
// react to scrolling, e.g. for a dynamic hairline divider) — used by the Home
// screen to render its smart hero welcome header.
typealias DrsScreenTopBar = @Composable (TopAppBarScrollBehavior) -> Unit

interface DrsScreenScope {
    var title: String

    var navigationIconVisible: Boolean

    var previewFieldVisible: Boolean

    /**
     * Whether this screen wraps the content in its own vertical scroll
     * container (with the shared DRS scrollbar). Defaults to `true`.
     *
     * DRS convention: set this to `false` whenever the content provides its
     * OWN vertically scrollable container (a LazyColumn or a
     * `verticalScroll` Column) — nesting a vertical scrollable inside this
     * screen's scroll container measures it with an infinite max height and
     * crashes with "Vertically scrollable component was measured with an
     * infinity maximum height constraints". See DrsProfilesScreen for the
     * reference implementation of this convention.
     */
    var scrollable: Boolean

    var iconSpaceReserved: Boolean

    fun actions(actions: DrsScreenActions)

    /** DRS: replace the standard app bar with a fully custom composable. */
    fun topBar(topBar: DrsScreenTopBar)

    fun bottomBar(bottomBar: DrsScreenBottomBar)

    fun content(content: DrsScreenContent)

    fun floatingActionButton(fab: DrsScreenFab)

    fun navigationIcon(navigationIcon: DrsScreenNavigationIcon)
}

private class DrsScreenScopeImpl : DrsScreenScope {
    override var title: String by mutableStateOf("")
    override var navigationIconVisible: Boolean by mutableStateOf(true)
    override var previewFieldVisible: Boolean by mutableStateOf(false)
    override var scrollable: Boolean by mutableStateOf(true)
    override var iconSpaceReserved: Boolean by mutableStateOf(true)

    private var actions: DrsScreenActions = @Composable { }
    private var topBar: DrsScreenTopBar? = null
    private var bottomBar: DrsScreenBottomBar = @Composable { }
    private var content: DrsScreenContent = @Composable { }
    private var fab: DrsScreenFab = @Composable { }
    private var navigationIcon: DrsScreenNavigationIcon = @Composable {
        val navController = LocalNavController.current
        // DRS: modern tinted circular back button (Pixel/Google 2025 style) —
        // a soft primary-tinted pill behind the arrow, mirrored for RTL.
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            DrsIconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.autoMirrorForRtl(),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                // DRS v1.28.0 audit fix (High, a11y): the back button on every
                // screen was silent to TalkBack. It now announces its purpose.
                contentDescription = stringResource(R.string.action__navigate_back),
            )
        }
    }

    override fun actions(actions: DrsScreenActions) {
        this.actions = actions
    }

    override fun topBar(topBar: DrsScreenTopBar) {
        this.topBar = topBar
    }

    override fun bottomBar(bottomBar: DrsScreenBottomBar) {
        this.bottomBar = bottomBar
    }

    override fun content(content: DrsScreenContent) {
        this.content = content
    }

    override fun floatingActionButton(fab: DrsScreenFab) {
        this.fab = fab
    }

    override fun navigationIcon(navigationIcon: DrsScreenNavigationIcon) {
        this.navigationIcon = navigationIcon
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Render() {
        val context = LocalContext.current
        val previewFieldController = LocalPreviewFieldController.current
        val colorScheme = MaterialTheme.colorScheme

        SideEffect {
            previewFieldController?.isVisible = previewFieldVisible
            // DRS fix: unwrap the context and cast safely so that a non-activity
            // context (e.g. an OEM insets wrapper) can never crash the UI.
            var unwrapped = context
            while (unwrapped is ContextWrapper && unwrapped !is Activity) {
                unwrapped = unwrapped.baseContext
            }
            val window = (unwrapped as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            if (AndroidVersion.ATLEAST_API29_Q) {
                window.navigationBarColor = Color.Transparent.toArgb()
                window.isNavigationBarContrastEnforced = true
            } else {
                window.navigationBarColor = colorScheme.scrim.toArgb()
            }
        }

        // DRS: enter-always scroll behavior — the app bar is no longer pinned.
        // It glides away smoothly (1:1 with the finger) as the user drags the
        // content up, springs back instantly on any downward drag, and settles
        // smartly with a snap animation when a fling ends.
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        val customTopBar = topBar

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                if (customTopBar != null) {
                    customTopBar(scrollBehavior)
                } else {
                    DrsAppBar(title, navigationIcon.takeIf { navigationIconVisible }, actions, scrollBehavior)
                }
            },
            bottomBar = bottomBar,
            floatingActionButton = fab,
        ) { innerPadding ->
            val scrollModifier = if (scrollable) {
                Modifier.drsVerticalScroll()
            } else {
                Modifier
            }
            // DRS: a pinned ambient glow under the app bar — a faint primary
            // wash that stays put while the content scrolls, giving every
            // screen a soft sense of depth instead of a flat wall of color.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to colorScheme.primary.copy(alpha = 0.06f),
                                0.35f to Color.Transparent,
                            ),
                        )
                    },
            ) {
                PreferenceLayout(
                    DrsPreferenceStore,
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxWidth()
                        .then(scrollModifier),
                    iconSpaceReserved = iconSpaceReserved,
                    content = content,
                )
            }
        }
    }
}
