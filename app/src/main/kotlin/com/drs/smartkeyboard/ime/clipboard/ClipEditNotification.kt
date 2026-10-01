/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardItem
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType

/**
 * DRS v1.21.0 — «نافذة الاشعارات المنبثقه الخاصه بالتعديل»: the
 * notification-style edit window the user asked for since v1.11 and that
 * never actually shipped. When a new text clip is captured, a heads-up
 * notification offers a «تعديل» action; tapping it opens the floating
 * editor OUTSIDE the keyboard panel, exactly like the in-panel entry
 * point — but reachable from anywhere, panel closed or not.
 *
 * The decision is pure and JVM-testable ([ClipEditNotificationPolicy]);
 * only the posting itself touches Android. Privacy is strict: sensitive
 * clips (flagged passwords etc.), non-text items, and incognito-captured
 * content never surface in a notification, and the whole channel is
 * pref-gated from the comprehensive clipboard settings.
 */
object ClipEditNotificationPolicy {

    /** The preview length the notification body shows (BigTextStyle). */
    const val PREVIEW_CHARS: Int = 220

    /**
     * The pure decision: does this captured clip deserve the edit
     * notification? Requires the pref, a history-backed capture (the
     * capture path is already incognito/password-gated upstream), a text
     * item with real content, and a non-sensitive clip — a notification
     * surfaces content on the lock screen, so sensitive text never rides it.
     */
    fun shouldNotify(
        prefEnabled: Boolean,
        historyEnabled: Boolean,
        type: ItemType,
        text: String?,
        isSensitive: Boolean,
    ): Boolean {
        if (!prefEnabled || !historyEnabled) return false
        if (type != ItemType.TEXT) return false
        if (isSensitive) return false
        if (text.isNullOrEmpty() || text.isBlank()) return false
        return true
    }

    /** The preview text the notification body shows (newline-flattened). */
    fun previewOf(text: String, maxChars: Int = PREVIEW_CHARS): String {
        val flattened = text.replace("\n", " ").trim()
        if (flattened.length <= maxChars) return flattened
        return flattened.take(maxChars).trimEnd() + "…"
    }
}

object ClipEditNotification {

    /** The heads-up channel — HIGH importance so it banners over the screen. */
    const val CHANNEL_ID: String = "drs_clip_edit"

    /** One notification at a time — a new capture replaces the previous. */
    const val NOTIFICATION_ID: Int = 4211

    /**
     * Posts the heads-up edit notification for [item] when the pure policy
     * approves and notifications are actually granted. Never throws: the
     * capture path must not break because a notification could not post.
     */
    fun maybePost(context: Context, item: ClipboardItem, prefEnabled: Boolean, historyEnabled: Boolean) {
        try {
            if (!ClipEditNotificationPolicy.shouldNotify(
                    prefEnabled = prefEnabled,
                    historyEnabled = historyEnabled,
                    type = item.type,
                    text = item.text,
                    isSensitive = item.isSensitive,
                )
            ) {
                return
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (!notificationManager.areNotificationsEnabled()) return
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.clip__notification_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    // DRS v1.28.0 audit fix (Medium): the notification carries up
                    // to ~220 characters of copied text; on the lock screen it
                    // must be redacted like the system redacts sensitive
                    // notifications. VISIBILITY_SECRET hides the preview
                    // content on locked screens while keeping the heads-up
                    // behavior fully intact while unlocked.
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                },
            )
            val editIntent = Intent(context, ClipEditorPopupActivity::class.java)
                .putExtra(ClipEditorPopupActivity.EXTRA_EDIT_TEXT, item.text.orEmpty())
                .putExtra(ClipEditorPopupActivity.EXTRA_EDIT_TS, item.creationTimestampMs)
                // DRS p7 (F1): the edit round-trips the item id — without it
                // the popup rebuilt the clip with id = 0 and the save was a
                // silent no-op (Room UPDATE ... WHERE id = 0 → 0 rows).
                .putExtra(ClipEditorPopupActivity.EXTRA_EDIT_ID, item.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val contentIntent = PendingIntent.getActivity(
                context,
                item.id.coerceAtLeast(0).toInt(),
                editIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val preview = ClipEditNotificationPolicy.previewOf(item.text.orEmpty())
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.clip__notification_title))
                .setContentText(preview)
                .setStyle(Notification.BigTextStyle().bigText(preview))
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // The clipboard capture path must survive a failed notification.
        }
    }
}
