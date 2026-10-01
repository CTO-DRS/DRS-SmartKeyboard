/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.app.ext

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.drs.DrsCurrencies
import com.drs.smartkeyboard.drs.DrsEconomy
import com.drs.smartkeyboard.drs.DrsRewardCatalog
import com.drs.smartkeyboard.drs.DrsRewardItem
import com.drs.smartkeyboard.drs.DrsRewardKind
import com.drs.smartkeyboard.drs.DrsSystems
import com.drs.smartkeyboard.drs.DrsStore
import com.drs.smartkeyboard.drs.DrsUserPath
import com.drs.smartkeyboard.drs.ui.pathTitleRes
import com.drs.smartkeyboard.lib.compose.DrsScreen
import com.drs.smartkeyboard.app.Routes
import androidx.compose.ui.platform.LocalContext
import org.drs.lib.compose.stringRes
import androidx.compose.ui.platform.LocalUriHandler

/**
 * DRS Rewards Store (متجر المكافآت).
 *
 * Each DRS system owns its own currency earned by real usage of the
 * keyboard. The store sells titles, badges and system-card styles; items
 * marked exclusive to a system can only be bought with that system's own
 * currency while it is active, making every system's store its own.
 */
@Composable
fun DrsRewardsScreen() = DrsScreen {
    title = stringRes(R.string.drs__rewards__title)
    previewFieldVisible = false
    iconSpaceReserved = true

    val drsState by DrsStore.state.collectAsState()
    val spec = DrsSystems.specOfName(drsState.userPath)
    val currency = DrsCurrencies.of(spec.path)
    val accent = if (isSystemInDarkTheme()) spec.accentNight else spec.accent
    val wallet = drsState.wallet
    val balance = DrsEconomy.balanceOf(wallet, spec.path)
    val lifetime = DrsEconomy.earnedOf(wallet, spec.path)
    val ownedCount = wallet.ownedItems.size

    content {
        // DRS fix: this Column must NOT scroll on its own. DrsScreen already
        // wraps the content in a vertical scroll container (scrollable = true
        // by default); nesting a second vertical scroll here crashes during
        // measure with "Vertically scrollable component was measured with an
        // infinity maximum height constraints". The outer scroll owns the
        // gesture, the app-bar scroll-away and the DRS scrollbar.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------------- wallet hero ----------------
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(accent.copy(alpha = 0.18f), accent.copy(alpha = 0.05f)),
                            ),
                        )
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(accent.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = currency.icon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringRes(currency.nameRes),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringRes(pathTitleRes(spec)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = balance.toString(),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        WalletStatChip(
                            icon = Icons.Default.Stars,
                            label = stringRes(R.string.drs__rewards__lifetime_earned),
                            value = lifetime.toString(),
                            accent = accent,
                        )
                        WalletStatChip(
                            icon = Icons.Default.LocalFireDepartment,
                            label = stringRes(R.string.drs__rewards__streak),
                            value = stringRes(
                                R.string.drs__rewards__streak_days,
                                "{count}" to wallet.streakDays.toString(),
                            ),
                            accent = accent,
                        )
                        WalletStatChip(
                            icon = Icons.Default.Redeem,
                            label = stringRes(R.string.drs__rewards__owned),
                            value = ownedCount.toString(),
                            accent = accent,
                        )
                    }
                }
            }

            // ---------------- earning rules ----------------
            RewardsSectionCard(title = stringRes(R.string.drs__rewards__how_to_earn)) {
                // DRS v1.28.0 audit fix (UI consistency): the earn-rule rows used
                // raw emoji glyphs (⌨ ✨ 🔥) as icons — platform-dependent,
                // visually inconsistent with the app's vector iconography.
                // Proper Material icons render identically everywhere and
                // inherit the theme tint.
                EarnRuleRow(
                    icon = Icons.Outlined.Keyboard,
                    text = stringRes(R.string.drs__rewards__rule_typing, "{count}" to DrsEconomy.KEYS_PER_POINT.toString()),
                )
                EarnRuleRow(
                    icon = Icons.Outlined.AutoAwesome,
                    text = stringRes(R.string.drs__rewards__rule_features, "{count}" to DrsEconomy.FEATURE_USE_POINTS.toString()),
                )
                EarnRuleRow(
                    icon = Icons.Outlined.LocalFireDepartment,
                    text = stringRes(
                        R.string.drs__rewards__rule_daily,
                        "{base}" to DrsEconomy.DAILY_BONUS.toString(),
                        "{max}" to (DrsEconomy.DAILY_BONUS + (DrsEconomy.STREAK_CAP - 1) * 5).toString(),
                    ),
                )
                Text(
                    text = stringRes(R.string.drs__rewards__rule_currency_note),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ---------------- titles ----------------
            RewardsSectionCard(title = stringRes(R.string.drs__rewards__section_titles)) {
                DrsRewardCatalog.titles().forEachIndexed { index, item ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        )
                    }
                    RewardItemRow(item = item, accent = accent, wallet = wallet, activePath = spec.path)
                }
            }

            // ---------------- badges ----------------
            RewardsSectionCard(title = stringRes(R.string.drs__rewards__section_badges)) {
                DrsRewardCatalog.badges().forEachIndexed { index, item ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        )
                    }
                    RewardItemRow(item = item, accent = accent, wallet = wallet, activePath = spec.path)
                }
            }

            // ---------------- card styles ----------------
            RewardsSectionCard(title = stringRes(R.string.drs__rewards__section_cards)) {
                DrsRewardCatalog.cards().forEachIndexed { index, item ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        )
                    }
                    RewardItemRow(item = item, accent = accent, wallet = wallet, activePath = spec.path)
                }
            }

            // ---------------- ledger ----------------
            if (wallet.ledger.isNotEmpty()) {
                RewardsSectionCard(title = stringRes(R.string.drs__rewards__ledger_title)) {
                    wallet.ledger.take(8).forEachIndexed { index, entry ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            )
                        }
                        LedgerRow(entry = entry)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun WalletStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RewardsSectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun EarnRuleRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * One store row. Buttons:
 *  - not owned + affordable + allowed  -> شراء
 *  - not owned + not affordable        -> disabled price
 *  - not owned + other system owns it  -> locked chip
 *  - owned + equipped                  -> ✓ مجهّز
 *  - owned + not equipped              -> تجهيز
 */
@Composable
private fun RewardItemRow(
    item: DrsRewardItem,
    accent: Color,
    wallet: com.drs.smartkeyboard.drs.DrsWallet,
    activePath: DrsUserPath,
) {
    val owned = wallet.ownedItems.contains(item.id)
    val equippedId = when (item.kind) {
        DrsRewardKind.TITLE -> wallet.equippedTitle
        DrsRewardKind.BADGE -> wallet.equippedBadge
        DrsRewardKind.CARD -> wallet.equippedCard
    }
    val equipped = owned && equippedId == item.id
    val lockedToOther = item.exclusiveTo != null && item.exclusiveTo != activePath
    val payerPath = item.exclusiveTo ?: activePath
    val affordable = DrsEconomy.balanceOf(wallet, payerPath) >= item.price

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    if (lockedToOther) {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    } else {
                        accent.copy(alpha = 0.13f)
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (lockedToOther) Icons.Default.Lock else item.icon,
                contentDescription = null,
                tint = if (lockedToOther) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    accent
                },
                modifier = Modifier.size(19.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringRes(item.titleRes),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringRes(item.descRes),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.exclusiveTo != null) {
                Text(
                    text = stringRes(
                        R.string.drs__rewards__exclusive_badge,
                        "{system}" to stringRes(pathTitleRes(DrsSystems.specOf(item.exclusiveTo))),
                    ),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        RewardAction(
            item = item,
            owned = owned,
            equipped = equipped,
            lockedToOther = lockedToOther,
            affordable = affordable,
            accent = accent,
        )
    }
}

@Composable
private fun RewardAction(
    item: DrsRewardItem,
    owned: Boolean,
    equipped: Boolean,
    lockedToOther: Boolean,
    affordable: Boolean,
    accent: Color,
) {
    when {
        equipped -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringRes(R.string.drs__rewards__equipped),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
        }
        owned -> {
            OutlinedButton(
                onClick = { DrsEconomy.equip(item) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp, vertical = 4.dp,
                ),
                modifier = Modifier.height(30.dp),
            ) {
                Text(
                    text = stringRes(R.string.drs__rewards__equip),
                    fontSize = 11.sp,
                )
            }
        }
        lockedToOther -> {
            Text(
                text = stringRes(
                    R.string.drs__rewards__locked_other_system,
                    "{system}" to stringRes(pathTitleRes(DrsSystems.specOf(item.exclusiveTo ?: DrsUserPath.NORMAL))),
                ),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(72.dp),
            )
        }
        else -> {
            Button(
                onClick = { DrsEconomy.buy(item) },
                enabled = affordable,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp, vertical = 4.dp,
                ),
                modifier = Modifier.height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) {
                Icon(
                    imageVector = Icons.Default.Redeem,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = item.price.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LedgerRow(entry: com.drs.smartkeyboard.drs.DrsLedgerEntry) {
    val reasonRes = when {
        entry.reason == "daily_bonus" -> R.string.drs__rewards__ledger_daily
        entry.reason.startsWith("buy:") -> {
            DrsRewardCatalog.itemOf(entry.reason.removePrefix("buy:"))?.titleRes
        }
        entry.reason == "earn_emoji" -> R.string.drs__rewards__ledger_emoji
        entry.reason.startsWith("earn_feature_clipboard") -> R.string.drs__rewards__ledger_clipboard
        entry.reason.startsWith("earn_feature_shortcut") -> R.string.drs__rewards__ledger_shortcut
        entry.reason.startsWith("earn_feature_tech_tool") -> R.string.drs__rewards__ledger_tech_tool
        entry.reason.startsWith("earn_feature_gesture") -> R.string.drs__rewards__ledger_gesture
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reasonRes?.let { stringRes(it) } ?: entry.reason,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = entry.day,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = (if (entry.delta >= 0) "+" else "") + entry.delta.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (entry.delta >= 0) {
                Color(0xFF0E9488)
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}
