/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsTechToolbarKey
import com.drs.smartkeyboard.drs.DrsTechToolbarKeys
import com.drs.smartkeyboard.lib.compose.DrsScreen
import org.drs.lib.compose.stringRes

/**
 * DRS v1.0.6: editor for the technical toolbar arrangement. The user picks
 * which keys appear in the strip, in which order, and can hide the ones they
 * never use. The arrangement is persisted in the DRS state and read live by
 * the IME strip - changes apply on the next keyboard show without restart.
 *
 * The leading "text tools" toggle is fixed and therefore not listed here.
 */
@Composable
fun DrsTechToolbarScreen() = DrsScreen {
    title = stringRes(R.string.drs__tech_toolbar__title)
    navigationIconVisible = true
    previewFieldVisible = false

    val drsState by DrsStore.state.collectAsState()

    val isCustomized = drsState.techToolbarKeys.isNotEmpty()
    val visibleIds = drsState.techToolbarKeys.ifEmpty { DrsTechToolbarKeys.DEFAULT_IDS }
    val byId = DrsTechToolbarKeys.ALL.associateBy { it.id }
    val visibleKeys = visibleIds.mapNotNull { byId[it] }
    val hiddenKeys = DrsTechToolbarKeys.ALL.filter { it.id !in visibleIds }

    fun persist(ids: List<String>) {
        // Persisting the full default explicitly is fine; an empty list is
        // reserved for "never customized".
        val cleaned = ids.distinct()
        DrsStore.update { it.copy(techToolbarKeys = cleaned) }
    }

    fun move(ids: List<String>, from: Int, to: Int): List<String> {
        if (to < 0 || to >= ids.size) return ids
        val mutable = ids.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        return mutable
    }

    content {
        // DRS fix (v1.0.9): DrsScreen already scrolls — a nested verticalScroll
        // here was measured with infinite height constraints and crashed the
        // screen on open.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringRes(R.string.drs__tech_toolbar__description),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isCustomized) {
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                DrsStore.update { it.copy(techToolbarKeys = emptyList()) }
                            },
                        ) {
                            Text(text = stringRes(R.string.drs__tech_toolbar__reset))
                        }
                    }
                }
            }

            // ---------------- visible keys (ordered) ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringRes(R.string.drs__tech_toolbar__keys_section),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    visibleKeys.forEachIndexed { index, key ->
                        ToolbarKeyRow(
                            key = key,
                            isFirst = index == 0,
                            isLast = index == visibleKeys.lastIndex,
                            onMoveUp = { persist(move(visibleIds, index, index - 1)) },
                            onMoveDown = { persist(move(visibleIds, index, index + 1)) },
                            onHide = {
                                if (visibleIds.size > 1) {
                                    persist(visibleIds.filterIndexed { i, _ -> i != index })
                                }
                            },
                        )
                    }
                }
            }

            // ---------------- hidden keys ----------------
            if (hiddenKeys.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringRes(R.string.drs__tech_toolbar__hidden_section),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        hiddenKeys.forEach { key ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KeySymbolBox(key)
                                Spacer(Modifier.widthIn(min = 8.dp))
                                Text(
                                    modifier = Modifier.weight(1f),
                                    text = stringRes(keyLabelRes(key.id)),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                IconButton(onClick = { persist(visibleIds + key.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringRes(R.string.drs__tech_toolbar__show_action),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun KeySymbolBox(key: DrsTechToolbarKey) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Text(
            text = key.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ToolbarKeyRow(
    key: DrsTechToolbarKey,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHide: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeySymbolBox(key)
        Spacer(Modifier.widthIn(min = 8.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = stringRes(keyLabelRes(key.id)),
            fontSize = 14.sp,
        )
        IconButton(onClick = onMoveUp, enabled = !isFirst) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = stringRes(R.string.drs__tech_toolbar__move_up),
            )
        }
        IconButton(onClick = onMoveDown, enabled = !isLast) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringRes(R.string.drs__tech_toolbar__move_down),
            )
        }
        IconButton(onClick = onHide) {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = stringRes(R.string.drs__tech_toolbar__hide_action),
            )
        }
    }
}

/** Human-readable name for every catalogue key (falls back to the label). */
private fun keyLabelRes(id: String): Int = when (id) {
    "arrow_left" -> R.string.drs__tech_toolbar__key_arrow_left
    "arrow_right" -> R.string.drs__tech_toolbar__key_arrow_right
    "arrow_up" -> R.string.drs__tech_toolbar__key_arrow_up
    "arrow_down" -> R.string.drs__tech_toolbar__key_arrow_down
    "tab" -> R.string.drs__tech_toolbar__key_tab
    "esc" -> R.string.drs__tech_toolbar__key_esc
    "brace_open" -> R.string.drs__tech_toolbar__key_brace_open
    "brace_close" -> R.string.drs__tech_toolbar__key_brace_close
    "bracket_open" -> R.string.drs__tech_toolbar__key_bracket_open
    "bracket_close" -> R.string.drs__tech_toolbar__key_bracket_close
    "paren_open" -> R.string.drs__tech_toolbar__key_paren_open
    "paren_close" -> R.string.drs__tech_toolbar__key_paren_close
    "less" -> R.string.drs__tech_toolbar__key_less
    "greater" -> R.string.drs__tech_toolbar__key_greater
    "slash" -> R.string.drs__tech_toolbar__key_slash
    "backslash" -> R.string.drs__tech_toolbar__key_backslash
    "pipe" -> R.string.drs__tech_toolbar__key_pipe
    "at" -> R.string.drs__tech_toolbar__key_at
    "hash" -> R.string.drs__tech_toolbar__key_hash
    "dollar" -> R.string.drs__tech_toolbar__key_dollar
    "percent" -> R.string.drs__tech_toolbar__key_percent
    "caret" -> R.string.drs__tech_toolbar__key_caret
    "amp" -> R.string.drs__tech_toolbar__key_amp
    "asterisk" -> R.string.drs__tech_toolbar__key_asterisk
    else -> R.string.drs__tech_toolbar__title
}
