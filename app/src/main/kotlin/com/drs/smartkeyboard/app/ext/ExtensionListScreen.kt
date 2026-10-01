/*
 * Copyright (C) 2024-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.ext

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.LocalNavController
import com.drs.smartkeyboard.app.Routes
import com.drs.smartkeyboard.extensionManager
import com.drs.smartkeyboard.ime.theme.ThemeExtension
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.lib.ext.Extension
import com.drs.smartkeyboard.lib.ext.ExtensionManager
import kotlinx.coroutines.flow.StateFlow
import org.drs.lib.compose.DrsOutlinedBox
import org.drs.lib.compose.DrsTextButton
import org.drs.lib.compose.defaultDrsOutlinedBox
import org.drs.lib.compose.drsScrollbar
import org.drs.lib.compose.stringRes

enum class ExtensionListScreenType(
    val id: String,
    @StringRes val titleResId: Int,
    val getExtensionIndex: (ExtensionManager) -> ExtensionManager.ExtensionIndex<*>,
    val launchExtensionCreate: ((NavController) -> Unit)?,
    // Only theme extensions ship with a working visual editor; keyboard and
    // language-pack extensions are data-driven packages, so the Edit action
    // is hidden for them to avoid a dead-end screen.
    val supportsEdit: Boolean,
) {
    EXT_THEME(
        id = "ext-theme",
        titleResId = R.string.ext__list__ext_theme,
        getExtensionIndex = { it.themes },
        launchExtensionCreate = { it.navigate(Routes.Ext.Edit("null", ThemeExtension.SERIAL_TYPE)) },
        supportsEdit = true,
    ),
    EXT_KEYBOARD(
        id = "ext-keyboard",
        titleResId = R.string.ext__list__ext_keyboard,
        getExtensionIndex = { it.keyboardExtensions },
        launchExtensionCreate = null,
        supportsEdit = false,
    ),
    EXT_LANGUAGEPACK(
        id = "ext-languagepack",
        titleResId = R.string.ext__list__ext_languagepack,
        getExtensionIndex = { it.languagePacks },
        launchExtensionCreate = null,
        supportsEdit = false,
    );
}

@Composable
fun ExtensionListScreen(type: ExtensionListScreenType, showUpdate: Boolean) = DrsScreen {
    title = stringRes(type.titleResId)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val extensionManager by context.extensionManager()
    val extensionIndex by type.getExtensionIndex(extensionManager).collectAsState()
    // DRS M0.2 — the state-inclusive listing: disabled extensions stay
    // visible in the list (dimmed + badge) while the runtime skips them.
    @Suppress("UNCHECKED_CAST")
    val extensionIndexWithState by (type.getExtensionIndex(extensionManager).allWithDisabledState
        as StateFlow<List<Pair<Extension, Boolean>>>).collectAsState()

    var query by remember { mutableStateOf("") }
    val filteredWithState = remember(extensionIndexWithState, query) {
        if (query.isBlank()) {
            extensionIndexWithState
        } else {
            extensionIndexWithState.filter { (ext, _) ->
                ext.meta.title.contains(query, ignoreCase = true) ||
                    ext.meta.id.contains(query, ignoreCase = true) ||
                    (ext.meta.description?.contains(query, ignoreCase = true) == true)
            }
        }
    }

    var fabHeight by remember {
        mutableStateOf(0)
    }
    val fabHeightDp = with(LocalDensity.current) { fabHeight.toDp()+16.dp }
    val listState = rememberLazyListState()

    content {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .drsScrollbar(state = listState, isVertical = true),
            state = listState,
            contentPadding = PaddingValues(top = 4.dp, bottom = fabHeightDp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    placeholder = { Text(stringRes(R.string.ext__list__search_hint)) },
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
            if (showUpdate) {
                item {
                    ImportExtensionBox(navController)
                }
                item {
                    UpdateBox(extensionIndex = extensionIndex)
                }
            }
            if (filteredWithState.isEmpty() && query.isNotBlank()) {
                item {
                    Text(
                        text = stringRes(R.string.ext__hub__search_empty, "query" to query),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(filteredWithState) { (ext, isEnabled) ->
                DrsOutlinedBox(
                    modifier = Modifier
                        .defaultDrsOutlinedBox()
                        .alpha(if (isEnabled) 1f else 0.45f),
                    title = ext.meta.title,
                    subtitle = ext.meta.id,
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        text = ext.meta.description ?: "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        text = stringRes(
                            R.string.ext__list__components_count,
                            "count" to ext.components().size.toString(),
                            "version" to ext.meta.version,
                        ),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringRes(
                            if (ext.sourceRef?.isInternal == true) {
                                R.string.ext__list__badge_imported
                            } else {
                                R.string.ext__list__badge_bundled
                            },
                        ),
                        fontSize = 10.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (ext.sourceRef?.isInternal == true) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .background(
                                (if (ext.sourceRef?.isInternal == true) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }).copy(alpha = 0.12f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    if (!isEnabled) {
                        // DRS M0.2 — honest state badge next to the dimming.
                        Text(
                            text = stringRes(R.string.ext__lifecycle__disabled_badge),
                            fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                    ) {
                        DrsTextButton(
                            onClick = {
                                navController.navigate(Routes.Ext.View(ext.meta.id))
                            },
                            icon = Icons.Outlined.Info,
                            text = stringRes(id = R.string.ext__list__view_details),
                            colors = ButtonDefaults.textButtonColors(),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (type.supportsEdit) {
                            DrsTextButton(
                                onClick = {
                                    navController.navigate(Routes.Ext.Edit(ext.meta.id))
                                },
                                icon = Icons.Default.Edit,
                                text = stringRes(R.string.action__edit),
                                enabled = extensionManager.canDelete(ext),
                            )
                        }
                    }
                }
            }
        }
    }

    if (type.launchExtensionCreate != null) {
        floatingActionButton {
            ExtendedFloatingActionButton(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringRes(id = R.string.ext__editor__title_create_any),
                    )
                },
                text = {
                    Text(
                        text = stringRes(id = R.string.ext__editor__title_create_any),
                    )
                },
                modifier = Modifier.onGloballyPositioned {
                    fabHeight = it.size.height
                },
                shape = FloatingActionButtonDefaults.extendedFabShape,
                onClick = { type.launchExtensionCreate.invoke(navController) },
            )
        }
    }
}
