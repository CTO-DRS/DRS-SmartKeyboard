/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.app.ext

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.ime.nlp.LanguagePackComponent
import com.drs.smartkeyboard.ime.theme.ThemeExtensionComponent
import com.drs.smartkeyboard.lib.ext.ExtensionComponent
import com.drs.smartkeyboard.lib.ext.ExtensionComponentName
import com.drs.smartkeyboard.lib.ext.ExtensionMeta
import org.drs.lib.compose.DrsIconButton
import org.drs.lib.compose.DrsOutlinedBox
import org.drs.lib.compose.DrsTextButton
import org.drs.lib.compose.stringRes

@Composable
fun ExtensionComponentNoneFoundView() {
    Text(
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
        text = stringRes(R.string.ext__meta__components_none_found),
        fontStyle = FontStyle.Italic,
    )
}

@Composable
fun ExtensionComponentView(
    meta: ExtensionMeta,
    component: ExtensionComponent,
    modifier: Modifier = Modifier,
    onDeleteBtnClick: (() -> Unit)? = null,
    onEditBtnClick: (() -> Unit)? = null,
) {
    val componentName = remember(meta.id, component.id) { ExtensionComponentName(meta.id, component.id).toString() }
    DrsOutlinedBox(
        modifier = modifier,
        title = component.label,
        subtitle = componentName,
    ) {
        Column(
            modifier = Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                bottom = if (onDeleteBtnClick == null && onEditBtnClick == null) 8.dp else 0.dp,
            ),
        ) {
            when (component) {
                is ThemeExtensionComponent -> {
                    val text = remember(
                        component.authors, component.isNightTheme, component.stylesheetPath(),
                    ) {
                        buildString {
                            appendLine("authors = ${component.authors}")
                            appendLine("isNightTheme = ${component.isNightTheme}")
                            append("stylesheetPath = ${component.stylesheetPath()}")
                        }
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current,
                    )
                }
                is LanguagePackComponent -> {
                    val text = remember(
                        component.authors, component.locale, component.hanShapeBasedKeyCode,
                    ) {
                        buildString {
                            appendLine("authors = ${component.authors}")
                            appendLine("locale = ${component.locale.localeTag()}")
                            appendLine("hanShapeBasedKeyCode = ${component.hanShapeBasedKeyCode}")
                        }
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current,
                    )
                }
                else -> { }
            }
        }
        if (onDeleteBtnClick != null || onEditBtnClick != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
            ) {
                if (onDeleteBtnClick != null) {
                    DrsTextButton(
                        onClick = onDeleteBtnClick,
                        icon = Icons.Default.Delete,
                        text = stringRes(R.string.action__delete),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (onEditBtnClick != null) {
                    DrsTextButton(
                        onClick = onEditBtnClick,
                        icon = Icons.Default.Edit,
                        text = stringRes(R.string.action__edit),
                    )
                }
            }
        }
    }
}

@Composable
fun <T : ExtensionComponent> ExtensionComponentListView(
    modifier: Modifier = Modifier,
    title: String,
    components: List<T>,
    onCreateBtnClick: (() -> Unit)? = null,
    componentGenerator: @Composable (T) -> Unit,
) {
    Column(modifier = modifier) {
        ListItem(
            headlineContent = { Text(
                text = title,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            ) },
            trailingContent = if (onCreateBtnClick != null) {
                @Composable {
                    // DRS a11y/i18n/ux (r0-I) FIX 8 — add-component button labeled.
                    DrsIconButton(
                        onClick = onCreateBtnClick,
                        icon = Icons.Default.Add,
                        iconColor = MaterialTheme.colorScheme.secondary,
                        contentDescription = stringRes(R.string.action__create),
                    )
                }
            } else { null },
        )
        if (components.isEmpty()) {
            ExtensionComponentNoneFoundView()
        } else {
            for (component in components) {
                componentGenerator(component)
            }
        }
    }
}
