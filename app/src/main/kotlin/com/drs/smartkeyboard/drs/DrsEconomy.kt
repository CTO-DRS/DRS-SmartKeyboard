/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 * Copyright (C) 2025 DRS Smart Keyboard contributors
 */

package com.drs.smartkeyboard.drs

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Memory
import androidx.compose.ui.graphics.vector.ImageVector
import com.drs.smartkeyboard.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * The DRS rewards economy (اقتصاد المكافآت).
 *
 * Every DRS system owns its own dedicated currency with its own identity:
 *
 *  - NORMAL    -> Harmony Points   (نقاط الوئام)
 *  - TECHNICAL -> Tech Chips       (شرائح التقنية)
 *  - HYBRID    -> Fusion Crystals  (بلورات التكامل)
 *
 * Points are earned by REAL keyboard usage only, and are credited to the
 * balance of whichever system is active at that moment, so each system's
 * economy grows independently. Everything is computed and stored locally;
 * nothing ever leaves the device.
 *
 * Earning rules:
 *  - every [KEYS_PER_POINT] keystrokes -> +1 point
 *  - using a smart feature (emoji, clipboard, shortcut, tech tool,
 *    gesture) -> +[FEATURE_USE_POINTS] points
 *  - first day of each day active -> daily bonus [DAILY_BONUS] + streak
 *    bonus (up to [STREAK_CAP] days), credited to the active system.
 */
object DrsEconomy {

    /** Keystrokes needed for one typing point. */
    const val KEYS_PER_POINT = 40L

    /** Points granted per smart-feature usage. */
    const val FEATURE_USE_POINTS = 2L

    /** Base daily bonus. */
    const val DAILY_BONUS = 25L

    /** Extra points per streak day, up to this many streak days. */
    const val STREAK_CAP = 7

    /** Maximum ledger entries kept. */
    private const val LEDGER_CAP = 24

    private val pendingKeys = AtomicLong()

    // ------------------------------------------------------------------
    // Earning hooks (cheap, never block, never throw)
    // ------------------------------------------------------------------

    /** Per-keystroke earning. Called next to the adaptation recorder. */
    fun recordKeyEarn(code: Int) {
        try {
            if (code >= 0x1F300) {
                // Emoji usage is a deliberate feature use: bonus points.
                award(FEATURE_USE_POINTS, "earn_emoji")
            }
            val n = pendingKeys.incrementAndGet()
            if (n % KEYS_PER_POINT == 0L) {
                award(1L, "earn_typing")
            }
        } catch (_: Throwable) {
            // Rewards must never break typing.
        }
    }

    /** Feature usage reward (clipboard, shortcut, tech tool, gesture). */
    fun recordFeatureUse(reason: String) {
        try {
            award(FEATURE_USE_POINTS, reason)
        } catch (_: Throwable) {
        }
    }

    /** Credits [delta] points to the currently active system's balance. */
    private fun award(delta: Long, reason: String) {
        DrsStore.update { state ->
            credit(state, delta, reason)
        }
    }

    private fun credit(state: DrsState, delta: Long, reason: String): DrsState {
        val w = state.wallet
        val today = today()
        val path = state.userPath
        var normal = w.normal
        var technical = w.technical
        var hybrid = w.hybrid
        var earnedNormal = w.earnedNormal
        var earnedTechnical = w.earnedTechnical
        var earnedHybrid = w.earnedHybrid
        when (path) {
            DrsUserPath.TECHNICAL.name -> {
                technical += delta; earnedTechnical += delta
            }
            DrsUserPath.HYBRID.name -> {
                hybrid += delta; earnedHybrid += delta
            }
            else -> {
                normal += delta; earnedNormal += delta
            }
        }
        val credited = state.copy(
            wallet = w.copy(
                normal = normal.coerceAtLeast(0),
                technical = technical.coerceAtLeast(0),
                hybrid = hybrid.coerceAtLeast(0),
                earnedNormal = earnedNormal,
                earnedTechnical = earnedTechnical,
                earnedHybrid = earnedHybrid,
                lastActiveDay = today,
            ),
        )
        // DRS v1.0.8: feature rewards now really reach the ledger — the
        // DrsLedgerEntry documentation always promised "daily bonuses,
        // feature rewards and store purchases", but only the bonus and
        // purchase paths ever wrote entries. Typing rewards stay silent
        // (the ledger would flood with one entry per 40 keystrokes) and
        // the daily bonus / purchase paths keep their own entries.
        return if (reason.startsWith("earn_") && reason != "earn_typing") {
            credited.copy(
                wallet = credited.wallet.copy(
                    ledger = prependLedger(
                        credited.wallet.ledger,
                        DrsLedgerEntry(day = today, reason = reason, delta = delta),
                    ),
                ),
            )
        } else {
            credited
        }
    }

    // ------------------------------------------------------------------
    // Daily bonus + streak
    // ------------------------------------------------------------------

    /**
     * Grants the daily bonus for a new day and maintains the streak.
     * Called once per process start; idempotent per day.
     *
     * DRS p9 (D-2): the day stamps are computed from ONE [LocalDate.now]
     * call. The previous code called now() twice (plus once more inside
     * [credit]), so a process straddling midnight compared `lastActiveDay`
     * against a yesterday stamp that belonged to a DIFFERENT day than the
     * `today` it granted — corrupting the streak. Deriving yesterday from
     * the same [today] instance keeps the comparison coherent.
     */
    fun onProcessStart() {
        try {
            val now = LocalDate.now()
            val today = now.toString()
            val yesterday = now.minusDays(1).toString()
            DrsStore.update { state ->
                val w = state.wallet
                if (w.dailyBonusDay == today) return@update state
                val streak = if (w.lastActiveDay == yesterday || w.dailyBonusDay == yesterday) {
                    (w.streakDays + 1).coerceAtMost(365)
                } else {
                    1
                }
                val bonus = DAILY_BONUS + (streak.coerceAtMost(STREAK_CAP) - 1) * 5L
                val credited = credit(state, bonus, "daily_bonus")
                credited.copy(
                    wallet = credited.wallet.copy(
                        streakDays = streak,
                        dailyBonusDay = today,
                        ledger = prependLedger(
                            credited.wallet.ledger,
                            DrsLedgerEntry(day = today, reason = "daily_bonus", delta = bonus),
                        ),
                    ),
                )
            }
        } catch (_: Throwable) {
        }
    }

    private fun prependLedger(
        ledger: List<DrsLedgerEntry>,
        entry: DrsLedgerEntry,
    ): List<DrsLedgerEntry> {
        return (listOf(entry) + ledger).take(LEDGER_CAP)
    }

    // ------------------------------------------------------------------
    // Store: buy / equip
    // ------------------------------------------------------------------

    /**
     * DRS p9 (D-1-doc): the payer is the item's OWNING system for
     * exclusive items ([DrsRewardItem.exclusiveTo]) and the ACTIVE system
     * for common items — there is deliberately NO active-path gate on
     * exclusive items (they stay purchasable from any active system, as
     * long as the owning currency covers the price). Returns false
     * (without side effects) when the item is already owned or the payer
     * balance is insufficient.
     *
     * DRS (D1): the whole check-and-mutate sequence now runs INSIDE the
     * store lock ([DrsStore.updateNow] is mutex-guarded), so a double-tap
     * or a racing award can no longer double-spend or produce a duplicate
     * reward — the previous code checked ownership/balance outside the
     * write and applied the mutation unconditionally (TOCTOU). The write
     * stays durable (economy-critical => updateNow), bridged with
     * runBlocking(Dispatchers.IO) so the synchronous Boolean contract for
     * the store UI is preserved; the critical section is one tiny atomic
     * JSON write. The payer balance is floored at 0 defensively.
     */
    fun buy(item: DrsRewardItem): Boolean = try {
        runBlocking(Dispatchers.IO) {
            var purchased = false
            DrsStore.updateNow { st ->
                if (st.wallet.ownedItems.contains(item.id)) return@updateNow st
                val payerPath = item.exclusiveTo ?: DrsSystems.specOfName(st.userPath).path
                if (balanceOf(st.wallet, payerPath) < item.price) return@updateNow st
                purchased = true
                val today = today()
                val w = st.wallet
                var next = st.copy(
                    wallet = w.copy(
                        ownedItems = w.ownedItems + item.id,
                        ledger = prependLedger(
                            w.ledger,
                            DrsLedgerEntry(day = today, reason = "buy:${item.id}", delta = -item.price),
                        ),
                    ),
                )
                // DRS (D1): deduct from the payer balance, floored at zero.
                next = when (payerPath) {
                    DrsUserPath.TECHNICAL -> next.copy(
                        wallet = next.wallet.copy(
                            technical = (next.wallet.technical - item.price).coerceAtLeast(0),
                        ),
                    )
                    DrsUserPath.HYBRID -> next.copy(
                        wallet = next.wallet.copy(
                            hybrid = (next.wallet.hybrid - item.price).coerceAtLeast(0),
                        ),
                    )
                    else -> next.copy(
                        wallet = next.wallet.copy(
                            normal = (next.wallet.normal - item.price).coerceAtLeast(0),
                        ),
                    )
                }
                // Auto-equip on purchase for a delightful flow.
                equipInternal(next, item)
            }
            purchased
        }
    } catch (_: Throwable) {
        false
    }

    /** Equips an owned item of the given kind. */
    fun equip(item: DrsRewardItem) {
        DrsStore.update { state -> equipInternal(state, item) }
    }

    private fun equipInternal(state: DrsState, item: DrsRewardItem): DrsState {
        val w = state.wallet
        return when (item.kind) {
            DrsRewardKind.TITLE -> state.copy(
                wallet = w.copy(
                    equippedTitle = item.id.takeIf { w.ownedItems.contains(it) } ?: "",
                ),
            )
            DrsRewardKind.BADGE -> state.copy(
                wallet = w.copy(
                    equippedBadge = item.id.takeIf { w.ownedItems.contains(it) } ?: "",
                ),
            )
            DrsRewardKind.CARD -> state.copy(
                wallet = w.copy(
                    equippedCard = item.id.takeIf { w.ownedItems.contains(it) } ?: "",
                ),
            )
        }
    }


    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** Balance of a system's own currency. */
    fun balanceOf(wallet: DrsWallet, path: DrsUserPath): Long = when (path) {
        DrsUserPath.TECHNICAL -> wallet.technical
        DrsUserPath.HYBRID -> wallet.hybrid
        else -> wallet.normal
    }

    /** Lifetime earned counter of a system's currency. */
    fun earnedOf(wallet: DrsWallet, path: DrsUserPath): Long = when (path) {
        DrsUserPath.TECHNICAL -> wallet.earnedTechnical
        DrsUserPath.HYBRID -> wallet.earnedHybrid
        else -> wallet.earnedNormal
    }

    private fun today(): String = LocalDate.now().toString()
}

/** Kind of a reward item, deciding where the equipped item shows up. */
enum class DrsRewardKind {
    /** A title shown next to the active system in the control center. */
    TITLE,

    /** A badge shown in the diagnostics info card. */
    BADGE,

    /** A decorative style for the control center system card. */
    CARD,
}

/**
 * One purchasable reward. DRS p9 (D-1-doc): [exclusiveTo] items are paid
 * from the owning system's own currency — the buyer's ACTIVE system does
 * not matter (the previous "only while that system is active" claim was
 * doc drift; the purchase path never gated on it).
 */
data class DrsRewardItem(
    val id: String,
    val kind: DrsRewardKind,
    val price: Long,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val icon: ImageVector,
    val exclusiveTo: DrsUserPath? = null,
)

/** The store catalog: titles, badges and card styles. */
object DrsRewardCatalog {

    val ITEMS: List<DrsRewardItem> = listOf(
        // ----- titles -----
        DrsRewardItem(
            id = "title_chat_legend", kind = DrsRewardKind.TITLE, price = 180,
            titleRes = R.string.drs__reward__title_chat_legend,
            descRes = R.string.drs__reward__title_chat_legend_desc,
            icon = Icons.Default.Favorite, exclusiveTo = DrsUserPath.NORMAL,
        ),
        DrsRewardItem(
            id = "title_symbol_engineer", kind = DrsRewardKind.TITLE, price = 180,
            titleRes = R.string.drs__reward__title_symbol_engineer,
            descRes = R.string.drs__reward__title_symbol_engineer_desc,
            icon = Icons.Default.Memory, exclusiveTo = DrsUserPath.TECHNICAL,
        ),
        DrsRewardItem(
            id = "title_two_worlds", kind = DrsRewardKind.TITLE, price = 220,
            titleRes = R.string.drs__reward__title_two_worlds,
            descRes = R.string.drs__reward__title_two_worlds_desc,
            icon = Icons.Default.AutoAwesome, exclusiveTo = DrsUserPath.HYBRID,
        ),
        DrsRewardItem(
            id = "title_legendary_writer", kind = DrsRewardKind.TITLE, price = 90,
            titleRes = R.string.drs__reward__title_legendary_writer,
            descRes = R.string.drs__reward__title_legendary_writer_desc,
            icon = Icons.Default.AutoAwesome,
        ),
        DrsRewardItem(
            id = "title_explorer", kind = DrsRewardKind.TITLE, price = 60,
            titleRes = R.string.drs__reward__title_explorer,
            descRes = R.string.drs__reward__title_explorer_desc,
            icon = Icons.Default.AutoAwesome,
        ),

        // ----- badges -----
        DrsRewardItem(
            id = "badge_pathfinder", kind = DrsRewardKind.BADGE, price = 50,
            titleRes = R.string.drs__reward__badge_pathfinder,
            descRes = R.string.drs__reward__badge_pathfinder_desc,
            icon = Icons.Default.AutoAwesome,
        ),
        DrsRewardItem(
            id = "badge_persistent", kind = DrsRewardKind.BADGE, price = 80,
            titleRes = R.string.drs__reward__badge_persistent,
            descRes = R.string.drs__reward__badge_persistent_desc,
            icon = Icons.Default.AutoAwesome,
        ),
        DrsRewardItem(
            id = "badge_chat_heart", kind = DrsRewardKind.BADGE, price = 100,
            titleRes = R.string.drs__reward__badge_chat_heart,
            descRes = R.string.drs__reward__badge_chat_heart_desc,
            icon = Icons.Default.Favorite, exclusiveTo = DrsUserPath.NORMAL,
        ),
        DrsRewardItem(
            id = "badge_analytic", kind = DrsRewardKind.BADGE, price = 100,
            titleRes = R.string.drs__reward__badge_analytic,
            descRes = R.string.drs__reward__badge_analytic_desc,
            icon = Icons.Default.Memory, exclusiveTo = DrsUserPath.TECHNICAL,
        ),
        DrsRewardItem(
            id = "badge_compass", kind = DrsRewardKind.BADGE, price = 100,
            titleRes = R.string.drs__reward__badge_compass,
            descRes = R.string.drs__reward__badge_compass_desc,
            icon = Icons.Default.AutoAwesome, exclusiveTo = DrsUserPath.HYBRID,
        ),

        // ----- card styles -----
        DrsRewardItem(
            id = "card_glow", kind = DrsRewardKind.CARD, price = 90,
            titleRes = R.string.drs__reward__card_glow,
            descRes = R.string.drs__reward__card_glow_desc,
            icon = Icons.Default.AutoAwesome,
        ),
        DrsRewardItem(
            id = "card_shimmer", kind = DrsRewardKind.CARD, price = 160,
            titleRes = R.string.drs__reward__card_shimmer,
            descRes = R.string.drs__reward__card_shimmer_desc,
            icon = Icons.Default.AutoAwesome,
        ),
        DrsRewardItem(
            id = "card_harmony", kind = DrsRewardKind.CARD, price = 120,
            titleRes = R.string.drs__reward__card_harmony,
            descRes = R.string.drs__reward__card_harmony_desc,
            icon = Icons.Default.Favorite, exclusiveTo = DrsUserPath.NORMAL,
        ),
        DrsRewardItem(
            id = "card_stream", kind = DrsRewardKind.CARD, price = 120,
            titleRes = R.string.drs__reward__card_stream,
            descRes = R.string.drs__reward__card_stream_desc,
            icon = Icons.Default.Memory, exclusiveTo = DrsUserPath.TECHNICAL,
        ),
        DrsRewardItem(
            id = "card_fusion", kind = DrsRewardKind.CARD, price = 120,
            titleRes = R.string.drs__reward__card_fusion,
            descRes = R.string.drs__reward__card_fusion_desc,
            icon = Icons.Default.AutoAwesome, exclusiveTo = DrsUserPath.HYBRID,
        ),
    )

    fun itemOf(id: String): DrsRewardItem? = ITEMS.firstOrNull { it.id == id }

    fun titles(): List<DrsRewardItem> = ITEMS.filter { it.kind == DrsRewardKind.TITLE }
    fun badges(): List<DrsRewardItem> = ITEMS.filter { it.kind == DrsRewardKind.BADGE }
    fun cards(): List<DrsRewardItem> = ITEMS.filter { it.kind == DrsRewardKind.CARD }
}

/**
 * The three currency identities. Each system's currency carries its own
 * name, icon and inherits the system accent color, so the wallet and the
 * store feel genuinely different in every system.
 */
data class DrsCurrencySpec(
    val path: DrsUserPath,
    @StringRes val nameRes: Int,
    @StringRes val unitRes: Int,
    val icon: ImageVector,
)

object DrsCurrencies {
    val NORMAL = DrsCurrencySpec(
        path = DrsUserPath.NORMAL,
        nameRes = R.string.drs__currency__normal,
        unitRes = R.string.drs__currency__normal_unit,
        icon = Icons.Default.Favorite,
    )
    val TECHNICAL = DrsCurrencySpec(
        path = DrsUserPath.TECHNICAL,
        nameRes = R.string.drs__currency__technical,
        unitRes = R.string.drs__currency__technical_unit,
        icon = Icons.Default.Memory,
    )
    val HYBRID = DrsCurrencySpec(
        path = DrsUserPath.HYBRID,
        nameRes = R.string.drs__currency__hybrid,
        unitRes = R.string.drs__currency__hybrid_unit,
        icon = Icons.Default.AutoAwesome,
    )

    val ALL = listOf(NORMAL, TECHNICAL, HYBRID)

    fun of(path: DrsUserPath): DrsCurrencySpec =
        ALL.firstOrNull { it.path == path } ?: NORMAL
}
