/*
 * Copyright (C) 2024-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.media.emoji

import com.drs.smartkeyboard.app.DrsPreferenceModel
import com.drs.smartkeyboard.lib.devtools.flogError
import org.drs.jetpref.datastore.model.PreferenceSerializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EmojiHistory(
    val pinned: List<@Serializable(with = Emoji.ValueOnlySerializer::class) Emoji>,
    val recent: List<@Serializable(with = Emoji.ValueOnlySerializer::class) Emoji>,
) {
    fun edit(): Editor {
        return Editor(pinned.toMutableList(), recent.toMutableList())
    }

    data class Editor(
        val pinned: MutableList<Emoji>,
        val recent: MutableList<Emoji>,
    ) {
        fun build(): EmojiHistory {
            return EmojiHistory(pinned.toList(), recent.toList())
        }
    }

    enum class UpdateStrategy(val isAutomatic: Boolean, val isPrepend: Boolean) {
        AUTO_SORT_PREPEND(isAutomatic = true, isPrepend = true),
        AUTO_SORT_APPEND(isAutomatic = true, isPrepend = false),
        MANUAL_SORT_PREPEND(isAutomatic = false, isPrepend = true),
        MANUAL_SORT_APPEND(isAutomatic = false, isPrepend = false);
    }

    object Serializer : PreferenceSerializer<EmojiHistory> {
        override fun serialize(value: EmojiHistory): String {
            return Json.encodeToString(value)
        }

        override fun deserialize(value: String): EmojiHistory {
            try {
                return Json.decodeFromString(value)
            } catch (e: Exception) {
                flogError { "Failed to deserialize EmojiHistory: $e" }
                return Empty
            }
        }
    }

    companion object {
        val Empty = EmojiHistory(emptyList(), emptyList())

        @Suppress("ConstPropertyName")
        const val MaxSizeUnlimited: Int = 0
    }
}

object EmojiHistoryHelper {
    private var emojiGuard = Mutex(locked = false)

    /**
     * DRS v2.14.0 «ذاكرة الإيموجي الأمينة»: المسار الوحيد لتسجيل استخدام
     * إيموجي في السجل الدائم — وهو الآن محكوم ببوابة [DrsEmojiHistoryGate]
     * حصرًا: السياق المُمرر من موقع الالتقاط يُحكم عند الحدث نفسه، والرفض
     * صامت في الذاكرة (لا استثناء ولا أثر). لا قيمة افتراضية للسياق — كل
     * موقع التقاط يبني الحقائق بنفسه، فلا التقاط صامت ولا تجاوز عرضي.
     *
     * أفعال الإدارة (pin/unpin/move/remove/delete) لا تمر هنا — هي تعمل
     * على ذاكرات محفوظة سلفًا ولا تلتقط شيئًا من الحقل الحالي.
     */
    suspend fun markEmojiUsed(
        prefs: DrsPreferenceModel,
        emoji: Emoji,
        recordContext: DrsEmojiHistoryGate.RecordContext,
    ): Unit = emojiGuard.withLock {
        when (DrsEmojiHistoryGate.decide(
            historyEnabled = prefs.emoji.historyEnabled.get(),
            isIncognitoMode = recordContext.isIncognitoMode,
            isPasswordVariation = recordContext.isPasswordVariation,
            isRawInputEditor = recordContext.isRawInputEditor,
            isComposingEnabled = recordContext.isComposingEnabled,
            isDeviceLocked = recordContext.isDeviceLocked,
        )) {
            is DrsEmojiHistoryGate.Verdict.Refuse -> return
            DrsEmojiHistoryGate.Verdict.Record -> {
                /* كل الحقائق سليمة — أكمل الالتقاط أدناه */
            }
        }

        val dataMut = prefs.emoji.historyData.get().edit()
        val pinnedUS = prefs.emoji.historyPinnedUpdateStrategy.get()
        val recentUS = prefs.emoji.historyRecentUpdateStrategy.get()
        val pinnedMaxSize = prefs.emoji.historyPinnedMaxSize.get().let { maxSize ->
            if (maxSize == EmojiHistory.MaxSizeUnlimited) Int.MAX_VALUE else maxSize
        }
        val recentMaxSize = prefs.emoji.historyRecentMaxSize.get().let { maxSize ->
            if (maxSize == EmojiHistory.MaxSizeUnlimited) Int.MAX_VALUE else maxSize
        }

        val pinnedIndex = dataMut.pinned.indexOf(emoji)
        if (pinnedIndex != -1) {
            if (pinnedUS.isAutomatic) {
                dataMut.pinned.removeAt(pinnedIndex)
                dataMut.pinned.addWithStrategy(pinnedUS, emoji)
            } else {
                // manual sort, keep item in place
            }
        } else {
            val recentIndex = dataMut.recent.indexOf(emoji)
            if (recentIndex != -1) {
                if (recentUS.isAutomatic) {
                    dataMut.recent.removeAt(recentIndex)
                    dataMut.recent.addWithStrategy(recentUS, emoji)
                } else {
                    // manual sort, keep item in place
                }
            } else {
                dataMut.recent.addWithStrategy(recentUS, emoji)
            }
        }

        prefs.emoji.historyData.set(
            EmojiHistory(
                pinned = dataMut.pinned.takeWithStrategy(pinnedUS, pinnedMaxSize),
                recent = dataMut.recent.takeWithStrategy(recentUS, recentMaxSize),
            )
        )
    }

    suspend fun pinEmoji(prefs: DrsPreferenceModel, emoji: Emoji): Unit = emojiGuard.withLock {
        if (!prefs.emoji.historyEnabled.get()) {
            return
        }

        val dataMut = prefs.emoji.historyData.get().edit()
        val pinnedUS = prefs.emoji.historyPinnedUpdateStrategy.get()

        val recentIndex = dataMut.recent.indexOf(emoji)
        if (recentIndex != -1) {
            dataMut.recent.removeAt(recentIndex)
            dataMut.pinned.addWithStrategy(pinnedUS, emoji)
        }

        prefs.emoji.historyData.set(dataMut.build())
    }

    suspend fun unpinEmoji(prefs: DrsPreferenceModel, emoji: Emoji): Unit = emojiGuard.withLock {
        if (!prefs.emoji.historyEnabled.get()) {
            return
        }

        val dataMut = prefs.emoji.historyData.get().edit()
        val recentUS = prefs.emoji.historyRecentUpdateStrategy.get()

        val pinnedIndex = dataMut.pinned.indexOf(emoji)
        if (pinnedIndex != -1) {
            dataMut.pinned.removeAt(pinnedIndex)
            dataMut.recent.addWithStrategy(recentUS, emoji)
        }

        prefs.emoji.historyData.set(dataMut.build())
    }

    suspend fun moveEmoji(prefs: DrsPreferenceModel, emoji: Emoji, offset: Int): Unit = emojiGuard.withLock {
        if (!prefs.emoji.historyEnabled.get() || offset == 0) {
            return
        }

        val dataMut = prefs.emoji.historyData.get().edit()

        val pinnedIndex = dataMut.pinned.indexOf(emoji)
        if (pinnedIndex != -1) {
            dataMut.pinned.move(pinnedIndex, offset)
        } else {
            val recentIndex = dataMut.recent.indexOf(emoji)
            if (recentIndex != -1) {
                dataMut.recent.move(recentIndex, offset)
            }
        }

        prefs.emoji.historyData.set(dataMut.build())
    }

    suspend fun removeEmoji(prefs: DrsPreferenceModel, emoji: Emoji): Unit = emojiGuard.withLock {
        if (!prefs.emoji.historyEnabled.get()) {
            return
        }

        val dataMut = prefs.emoji.historyData.get().edit()

        val pinnedIndex = dataMut.pinned.indexOf(emoji)
        if (pinnedIndex != -1) {
            dataMut.pinned.removeAt(pinnedIndex)
        } else {
            val recentIndex = dataMut.recent.indexOf(emoji)
            if (recentIndex != -1) {
                dataMut.recent.removeAt(recentIndex)
            }
        }

        prefs.emoji.historyData.set(dataMut.build())
    }

    suspend fun deleteHistory(prefs: DrsPreferenceModel): Unit = emojiGuard.withLock {
        if (!prefs.emoji.historyEnabled.get()) {
            return
        }
        val dataMut = prefs.emoji.historyData.get().edit()
        prefs.emoji.historyData.set(EmojiHistory(pinned = dataMut.pinned, listOf()))
    }

    suspend fun deletePinned(prefs: DrsPreferenceModel): Unit = emojiGuard.withLock {
        if (!prefs.emoji.historyEnabled.get()) {
            return
        }
        val dataMut = prefs.emoji.historyData.get().edit()
        prefs.emoji.historyData.set(EmojiHistory(pinned = listOf(), dataMut.recent))
    }

    private fun MutableList<Emoji>.addWithStrategy(strategy: EmojiHistory.UpdateStrategy, emoji: Emoji) {
        if (strategy.isPrepend) {
            add(0, emoji)
        } else {
            add(emoji)
        }
    }

    private fun MutableList<Emoji>.takeWithStrategy(
        strategy: EmojiHistory.UpdateStrategy,
        n: Int,
    ): List<Emoji> {
        return if (strategy.isPrepend) {
            take(n)
        } else {
            takeLast(n)
        }
    }

    private fun MutableList<Emoji>.move(itemIndex: Int, offset: Int) {
        val newIndex = (itemIndex + offset).coerceIn(0..<size)
        val item = removeAt(itemIndex)
        if (newIndex == size) {
            add(item)
        } else {
            add(newIndex, item)
        }
    }
}
