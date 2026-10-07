/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.appContext
import com.drs.smartkeyboard.drs.DrsAdaptationEngine
import com.drs.smartkeyboard.editorInstance
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardFileStorage
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardFilesDatabase
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardHistoryDao
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardHistoryDatabase
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardItem
import com.drs.smartkeyboard.ime.clipboard.provider.ClipboardMediaProvider
import com.drs.smartkeyboard.ime.clipboard.provider.ItemType
import com.drs.smartkeyboard.ime.text.key.KeyVariation
import com.drs.smartkeyboard.keyboardManager
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.drs.lib.android.AndroidClipboardManager
import org.drs.lib.android.AndroidClipboardManager_OnPrimaryClipChangedListener
import org.drs.lib.android.AndroidKeyguardManager
import org.drs.lib.android.clearPrimaryClipAnyApi
import org.drs.lib.android.setOrClearPrimaryClip
import org.drs.lib.android.showShortToastSync
import org.drs.lib.android.systemService
import org.drs.lib.kotlin.io.FsFile
import org.drs.lib.kotlin.tryOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * [ClipboardManager] manages the clipboard and clipboard history.
 *
 * Also just going to document how all the classes here work.
 *
 * [ClipboardManager] handles storage and retrieval of clipboard items. All manipulation of the
 * clipboard goes through here.
 */
class ClipboardManager(
    context: Context,
) : AndroidClipboardManager_OnPrimaryClipChangedListener, Closeable {
    companion object {
        /**
         * Taken from ClipboardDescription.java from the AOSP
         *
         * Helper to compare two MIME types, where one may be a pattern.
         * @param concreteType A fully-specified MIME type.
         * @param desiredType A desired MIME type that may be a pattern such as * / *.
         * @return Returns true if the two MIME types match.
         */
        fun compareMimeTypes(concreteType: String, desiredType: String): Boolean {
            val typeLength = desiredType.length
            if (typeLength == 3 && desiredType == "*/*") {
                return true
            }
            val slashpos = desiredType.indexOf('/')
            if (slashpos > 0) {
                if (typeLength == slashpos + 2 && desiredType[slashpos + 1] == '*') {
                    if (desiredType.regionMatches(0, concreteType, 0, slashpos + 1)) {
                        return true
                    }
                } else if (desiredType == concreteType) {
                    return true
                }
            }
            return false
        }

        // DRS p8 (S-1): startup reconcile sweep tuning — how long init waits
        // before judging orphans, and how fresh a backing file may be to
        // still belong to an in-flight capture (clone → history insert
        // round trip) instead of a refusal leak.
        private const val RECONCILE_START_DELAY_MS = 10_000L
        private const val RECONCILE_FILE_AGE_GRACE_MS = 60_000L
    }

    private val prefs by DrsPreferenceStore
    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val systemClipboardManager = context.systemService(AndroidClipboardManager::class)

    // DRS v2.15.0 «ذاكرة الحافظة الأمينة»: مدير القفل الشاشي — حقيقة
    // البيئة لبوابة الالتقاط. تُقرأ حالتُها لحظة كل حدث التقط لا عند
    // البناء، فالقفل يتقلب والخدمة تبقى (نفس نمط مُقيّم السياق في
    // KeyboardManager ومزوّد اقتراح الإيموجي).
    private val keyguardManager = context.systemService(AndroidKeyguardManager::class)

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cleanUpJob: Job
    private var clipHistoryDb: ClipboardHistoryDatabase? = null
    private val clipHistoryDao: ClipboardHistoryDao? get() = clipHistoryDb?.clipboardItemDao()

    // DRS (D3): single-init coordination for [initializeForContext] — the
    // AtomicBoolean takes the cheap fast path once initialized, the Mutex
    // makes the check-and-build section atomic so two racing callers can
    // never build two databases or register two history collectors.
    private val initMutex = Mutex(locked = false)
    private val initialized = AtomicBoolean(false)

    // DRS (B1): expiry wake-up — every expiry-relevant change (new history
    // content or one of the five expiry/history prefs) bumps this counter
    // so the smart timer recomputes the earliest pending expiry instantly.
    private val expiryWakeUp = MutableStateFlow(0L)
    private val expiryWakeUpCounter = AtomicLong(0)

    private fun wakeExpiryTimer() {
        expiryWakeUp.value = expiryWakeUpCounter.incrementAndGet()
    }

    val historyFlow: StateFlow<ClipboardHistory>
        field = MutableStateFlow(ClipboardHistory.EMPTY)
    val currentHistory: ClipboardHistory
        get() = historyFlow.value

    private val primaryClipLastFromCallbackGuard = Mutex(locked = false)
    private var primaryClipLastFromCallback: ClipData? = null
    val primaryClipFlow: StateFlow<ClipboardItem?>
        field = MutableStateFlow(null)
    inline var primaryClip
        get() = primaryClipFlow.value
        private set(v) {
            primaryClipFlow.value = v
        }

    init {
        systemClipboardManager.addPrimaryClipChangedListener(this)
        // DRS (B1): the smart expiry timer. The blind 60-second loop is gone:
        // combine(historyFlow, expiryWakeUp).collectLatest recomputes the
        // earliest pending expiry on every history/wake-up emission, sleeps
        // exactly delta+1 ms when something is pending, purges, and is then
        // rescheduled by the purged history re-emitting through historyFlow.
        // With nothing pending the block returns immediately — the timer is
        // idle (zero scheduled wakeups). ONE cancelable cleanUpJob owns the
        // timer and the five expiry/history pref collector children below,
        // so close() still tears everything down with a single cancel().
        cleanUpJob = ioScope.launch {
            launch {
                combine(historyFlow, expiryWakeUp) { history, _ -> history }
                    .collectLatest { history ->
                        val earliestAt = earliestPendingExpiryAt(history)
                        if (earliestAt == null) {
                            return@collectLatest
                        }
                        val delta = earliestAt - System.currentTimeMillis()
                        if (delta > 0) {
                            // +1 ms grace so the purge below finds the item
                            // strictly past its expiry threshold.
                            delay(delta + 1)
                        }
                        enforceExpiryDate(currentHistory)
                        // Recompute happens through the DB flow: the purge
                        // deleted rows, getAllAsFlow re-emits, updateHistory
                        // publishes a new historyFlow value and this block
                        // restarts for the next pending expiry.
                    }
            }
            // DRS (B1): the five expiry/history prefs all change what the
            // timer should wait for — each bump triggers an instant recompute.
            launch { prefs.clipboard.historyEnabled.asFlow().collect { wakeExpiryTimer() } }
            launch { prefs.clipboard.historyAutoCleanOldEnabled.asFlow().collect { wakeExpiryTimer() } }
            launch { prefs.clipboard.historyAutoCleanOldAfter.asFlow().collect { wakeExpiryTimer() } }
            launch { prefs.clipboard.historyAutoCleanSensitiveEnabled.asFlow().collect { wakeExpiryTimer() } }
            launch { prefs.clipboard.historyAutoCleanSensitiveAfter.asFlow().collect { wakeExpiryTimer() } }
        }
    }

    /**
     * DRS (B1): the earliest time any item of [clipHistory] becomes
     * expiry-removable, mirroring the item sets of [enforceExpiryDate]:
     * the age rule applies to the unpinned items (recent + other — pinned
     * are exempt), the sensitive rule applies to every sensitive item.
     * Null when nothing is pending (both rules disabled or nothing in the
     * matching sets) — the timer then stays idle.
     */
    private fun earliestPendingExpiryAt(clipHistory: ClipboardHistory): Long? {
        var earliest: Long? = null
        if (prefs.clipboard.historyAutoCleanOldEnabled.get()) {
            val windowMs = prefs.clipboard.historyAutoCleanOldAfter.get() * 60 * 1000L
            for (item in clipHistory.recent + clipHistory.other) {
                val pendingAt = item.creationTimestampMs + windowMs
                if (earliest == null || pendingAt < earliest) earliest = pendingAt
            }
        }
        if (prefs.clipboard.historyAutoCleanSensitiveEnabled.get()) {
            val windowMs = prefs.clipboard.historyAutoCleanSensitiveAfter.get() * 1000L
            for (item in clipHistory.all) {
                if (!item.isSensitive) continue
                val pendingAt = item.creationTimestampMs + windowMs
                if (earliest == null || pendingAt < earliest) earliest = pendingAt
            }
        }
        return earliest
    }

    fun initializeForContext(context: Context) {
        // DRS (D3): the old body was a plain background null-check — two
        // callers racing before the DB existed each built their own
        // ClipboardHistoryDatabase and registered a duplicate collector.
        // The AtomicBoolean guards the fast path; the Mutex makes the
        // check-and-build section atomic, so init runs exactly once.
        if (initialized.get()) return
        ioScope.launch {
            val doInit = initMutex.withLock {
                if (initialized.get()) {
                    false
                } else {
                    clipHistoryDb = ClipboardHistoryDatabase.new(context.applicationContext)
                    initialized.set(true)
                    true
                }
            }
            if (doInit) {
                // DRS p8 (S-1): once per init, reconcile clipboard_files
                // orphans — provider rows + backing files no history item
                // references (pre-fix refusal leaks included).
                reconcileOrphanedClipboardFiles()
                withContext(Dispatchers.Main) {
                    clipHistoryDao?.getAllAsFlow()?.collect { items ->
                        updateHistory(items)
                    }
                }
            }
        }
    }

    /**
     * DRS p8 (S-1): startup reconcile sweep — deletes clipboard_files
     * provider rows + backing files whose id is absent from the history.
     * Before the S-1 pre-gate existed, a media clip captured while an
     * incognito/password field was active (or any sensitive-flagged media)
     * was cloned to disk BEFORE the refusal in [insertOrMoveBeginning] and
     * the clone was never closed — file + provider row orphaned forever.
     * The sweep also cleans orphaned files with no row at all, so pre-fix
     * leaks of every shape disappear on the next process start.
     *
     * Race guards: the sweep starts after a small delay, and a backing file
     * younger than [RECONCILE_FILE_AGE_GRACE_MS] is never judged — a fresh
     * file belongs to an in-flight capture whose history insert may not have
     * landed yet (clone → insert is a two-step async round trip). A row is
     * likewise spared while its backing file is fresh; a row WITHOUT a file
     * is broken by definition (the provider always writes the file first)
     * and is removed immediately.
     *
     * Deletes go through the provider delete path, which removes the backing
     * file + row + cache entry in one idempotent step (the image/video
     * prefix of the delete uri is cosmetic — both clip item types delete
     * identically).
     */
    private fun reconcileOrphanedClipboardFiles() {
        ioScope.launch {
            delay(RECONCILE_START_DELAY_MS)
            val dao = clipHistoryDao ?: return@launch
            val now = System.currentTimeMillis()
            val historyIds = HashSet<Long>()
            tryOrNull {
                for (item in dao.getAll()) {
                    if (item.uri?.authority == ClipboardMediaProvider.AUTHORITY) {
                        item.uri?.lastPathSegment?.toLongOrNull()?.let(historyIds::add)
                    }
                }
            }
            // 1) Provider rows whose id no history item references.
            tryOrNull {
                val filesDb = ClipboardFilesDatabase.new(appContext)
                try {
                    for (info in filesDb.clipboardFilesDao().getAll()) {
                        if (info.id !in historyIds) {
                            val file = ClipboardFileStorage.getFileForId(appContext, info.id)
                            if (!file.isFile ||
                                now - file.lastModified() >= RECONCILE_FILE_AGE_GRACE_MS
                            ) {
                                deleteOrphanProviderItem(info.id)
                            }
                        }
                    }
                } finally {
                    runCatching { filesDb.close() }
                }
            }
            // 2) Backing files with no provider row (or whose row was
            //    equally orphaned — the provider delete above already
            //    removed those). A non-numeric file name is never a clone
            //    artifact and is left alone.
            tryOrNull {
                val filesDir = FsFile(appContext.noBackupFilesDir, ClipboardFileStorage.CLIPBOARD_FILES_PATH)
                for (file in filesDir.listFiles() ?: emptyArray()) {
                    val id = file.name.toLongOrNull()
                    if (id != null && id !in historyIds &&
                        now - file.lastModified() >= RECONCILE_FILE_AGE_GRACE_MS
                    ) {
                        deleteOrphanProviderItem(id)
                    }
                }
            }
        }
    }

    /**
     * DRS p8 (S-1): deletes one orphaned clipboard media artifact through
     * the [ClipboardMediaProvider] delete path (backing file + row + cache
     * entry, idempotent).
     */
    private fun deleteOrphanProviderItem(id: Long) {
        tryOrNull {
            appContext.contentResolver.delete(
                ContentUris.withAppendedId(ClipboardMediaProvider.IMAGE_CLIPS_URI, id),
                null,
                null,
            )
        }
    }

    private fun updateHistory(items: List<ClipboardItem>) {
        val itemsSorted = items.sortedByDescending { it.creationTimestampMs }
        val clipHistory = ClipboardHistory(itemsSorted)
        enforceHistoryLimit(clipHistory)
        historyFlow.value = clipHistory
    }

    /**
     * Sets the current primary clip without updating the internal clipboard history.
     */
    fun updatePrimaryClip(item: ClipboardItem?) {
        primaryClip = item
        if (prefs.clipboard.useInternalClipboard.get()) {
            val syncBehavior = prefs.clipboard.syncToSystem.get()
            val clipData = item?.toClipData(appContext)
            if (clipData != null && syncBehavior.shouldSyncSet) {
                systemClipboardManager.setPrimaryClip(clipData)
            } else if (clipData == null && syncBehavior.shouldSyncClear) {
                systemClipboardManager.clearPrimaryClipAnyApi()
            }
        } else {
            systemClipboardManager.setOrClearPrimaryClip(item?.toClipData(appContext))
        }
    }

    /**
     * Called by system clipboard when the system primary clip has changed.
     */
    override fun onPrimaryClipChanged() {
        val syncBehavior = prefs.clipboard.syncToDrs.get()
        if (!prefs.clipboard.useInternalClipboard.get() || syncBehavior != ClipboardSyncBehavior.NO_EVENTS) {
            val systemPrimaryClip = systemClipboardManager.primaryClip
            ioScope.launch {
                val isDuplicate: Boolean
                primaryClipLastFromCallbackGuard.withLock {
                    val a = primaryClipLastFromCallback?.getItemAt(0)
                    val b = systemPrimaryClip?.getItemAt(0)
                    isDuplicate = when {
                        a === b -> true
                        a == null || b == null -> false
                        else -> a.text == b.text && a.uri == b.uri
                    }
                    primaryClipLastFromCallback = systemPrimaryClip
                }
                if (isDuplicate) return@launch

                val internalPrimaryClip = primaryClip

                if (systemPrimaryClip == null) {
                    if (syncBehavior.shouldSyncClear) {
                        primaryClip = null
                    }
                    return@launch
                }

                if (systemPrimaryClip.getItemAt(0).let { it.text == null && it.uri == null }) {
                    if (syncBehavior.shouldSyncClear) {
                        primaryClip = null
                    }
                    return@launch
                }

                if (!syncBehavior.shouldSyncSet) {
                    return@launch
                }

                val isEqual = internalPrimaryClip?.isEqualTo(systemPrimaryClip) == true
                if (!isEqual) {
                    // DRS p8 (S-1) + v2.15.0: the history-refusal gate is
                    // evaluated BEFORE any media clone. fromClipData
                    // (cloneUri = false) never touches disk — a media clip
                    // whose capture verdict refuses (incognito mode /
                    // password field / EXTRA_IS_SENSITIVE / device locked —
                    // the exact conditions insertOrMoveBeginning refuses
                    // storage on) keeps the source uri for the live paste
                    // (RAM ClipData) and no provider row + clipboard_files
                    // clone is created for it, so nothing is orphaned on
                    // disk. The gate verdict is the single source of truth
                    // for both the clone decision and the storage decision.
                    val probe = ClipboardItem.fromClipData(appContext, systemPrimaryClip, cloneUri = false)
                    val isMedia = probe.type == ItemType.IMAGE || probe.type == ItemType.VIDEO
                    val sourceUri = systemPrimaryClip.getItemAt(0).uri
                    val willClone = isMedia &&
                        sourceUri != null &&
                        sourceUri.authority != ClipboardMediaProvider.AUTHORITY
                    val captureVerdict = DrsClipboardCaptureGate.decide(
                        historyEnabled = prefs.clipboard.historyEnabled.get(),
                        isIncognitoMode = keyboardManager.activeState.isIncognitoMode,
                        isPasswordVariation = keyboardManager.activeState.keyVariation == KeyVariation.PASSWORD,
                        isSensitiveClip = probe.isSensitive,
                        isDeviceLocked = isDeviceLockedNow(),
                    )
                    var item = probe
                    var cloned = false
                    if (isMedia && willClone && DrsClipboardCaptureGate.allowsMediaClone(captureVerdict)) {
                        item = ClipboardItem.fromClipData(appContext, systemPrimaryClip, cloneUri = true)
                        cloned = true
                        // DRS p7 (F12): a captured media item whose provider
                        // clone failed comes back with a null uri — refuse it
                        // so no broken uri-less IMAGE/VIDEO row ever enters
                        // the history or the primary clip.
                        if (item.uri == null) {
                            return@launch
                        }
                    }
                    primaryClip = item
                    if (!insertOrMoveBeginning(item)) {
                        // DRS p8 (S-1): the state raced between the pre-check
                        // above and the insert's own gate check and the insert
                        // still refused — close the just-created provider row
                        // + backing file so no orphan survives, then keep the
                        // live paste on the RAM/source clip. Only genuinely
                        // CLONED items are closed: an item still holding the
                        // source uri must never trigger a
                        // contentResolver.delete on third-party data.
                        if (cloned) {
                            tryOrNull { closeRemovedItems(listOf(item)) }
                            primaryClip = probe
                        }
                    }
                }
            }
        }
    }

    /**
     * DRS v2.15.0 «ذاكرة الحافظة الأمينة»: حقيقة القفل لحظة الحدث —
     * يُقرأ عند كل التقاط لا مرة واحدة، فالبوابة تحكم بحقائق حية.
     */
    private fun isDeviceLockedNow(): Boolean {
        return keyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }
    }

    /**
     * Change the current text on clipboard, update history (if enabled).
     */
    private fun addNewClip(item: ClipboardItem) {
        // DRS v2.15.0: قيمة الإرجاع صارت إلزامية الاستهلاك — false تعني
        // رفضًا خصوصيًا (تخفي/كلمة مرور/حساس/قفل)، وإشعار «تعديل» سطح
        // عرض مشتق من التقاطٍ قَبِلَ فقط: رفضٌ لا يُعلن ولا يُسرَّب،
        // ولا صف يفتحه الإشعار أصلًا. عطْل السجل يعيد true هنا لكن
        // السياسة نفسها ترفض الإشعار بعدها (historyEnabled).
        val captureAccepted = insertOrMoveBeginning(item)
        updatePrimaryClip(item)
        // DRS v1.21.0: «نافذة الاشعارات المنبثقه الخاصه بالتعديل» — a new
        // capture can surface a heads-up «تعديل» notification that opens
        // the floating editor from anywhere. The pure policy keeps it
        // honest (text only, non-sensitive, pref + history gated).
        ClipEditNotification.maybePost(
            appContext,
            item,
            prefEnabled = prefs.clipboard.editNotificationEnabled.get(),
            historyEnabled = prefs.clipboard.historyEnabled.get(),
            captureAccepted = captureAccepted,
        )
    }

    /**
     * Wraps some plaintext in a ClipData and calls [addNewClip]
     */
    fun addNewPlaintext(newText: String) {
        val newData = ClipboardItem.text(newText)
        addNewClip(newData)
    }

    /**
     * Adds a new item to the clipboard history (if enabled).
     *
     * DRS v2.15.0 «ذاكرة الحافظة الأمينة»: الحكم الواحد من
     * [DrsClipboardCaptureGate] يحكم التخزين حصريًا — الفحوص اليدوية
     * المتفرقة (تخفي/كلمة مرور/حساس) صارت حقائق تُغذّي البوابة مع
     * إضافة العلة المفقودة: القفل الشاشي (اللصق محجوب مقفولًا فالالتقاط
     * محجوب بالتماثل).
     *
     * DRS p8 (S-1): returns false when the item was REFUSED by the privacy
     * gate (incognito mode / password field / sensitive flag / device
     * locked — the exact conditions the S-1 pre-gate in
     * [onPrimaryClipChanged] mirrors through the same verdict), so the
     * caller can clean up an already-cloned media backing. Returns true
     * when the item was stored, merged, or history is simply disabled
     * (nothing to clean up in those cases — the gate's
     * [DrsClipboardCaptureGate.storageRefused] translation).
     */
    private fun insertOrMoveBeginning(newItem: ClipboardItem): Boolean {
        // DRS v2.15.0: بوابة واحدة، حقائق حية لحظة الحدث — التخفي
        // وكلمة المرور من حالة المحرر الحية، الحساسية من العنصر نفسه،
        // والقفل من مدير القفل. الرفض صامت في الذاكرة: لا استثناء ولا
        // سجل أخطاء ولا أثر.
        val verdict = DrsClipboardCaptureGate.decide(
            historyEnabled = prefs.clipboard.historyEnabled.get(),
            isIncognitoMode = keyboardManager.activeState.isIncognitoMode,
            isPasswordVariation = keyboardManager.activeState.keyVariation == KeyVariation.PASSWORD,
            isSensitiveClip = newItem.isSensitive,
            isDeviceLocked = isDeviceLockedNow(),
        )
        if (DrsClipboardCaptureGate.storageRefused(verdict)) {
            return false
        }
        if (verdict is DrsClipboardCaptureGate.Verdict.Refuse) {
            // HISTORY_DISABLED: لا شيء خُزِن ولا شيء يُنظَّف — عقد اللا-انحدار
            // مع سلوك التراث (استنساخ الوسائط الليفي يظل ظهرَ المقص الحي).
            return true
        }
        // DRS v1.9.0: one history text retains at most 50,000 characters
        // (ClipboardTextPolicy). The primary clip keeps the full text;
        // the stored history variant is what the panel re-pastes.
        val historyVariant = if (newItem.type == ItemType.TEXT && newItem.text != null) {
            newItem.copy(text = ClipboardTextPolicy.truncateForStorage(newItem.text))
        } else {
            newItem
        }
        val historyElement = currentHistory.all.firstOrNull { item ->
            item.type == ItemType.TEXT && item.text == historyVariant.text && item.isSensitive == historyVariant.isSensitive
        }
        if (historyElement != null) {
            moveToTheBeginning(
                oldItem = historyElement,
                newItem = if (historyElement.isPinned) {
                    historyVariant.copy(isPinned = true)
                } else {
                    historyVariant
                }
            )
        } else {
            insertClip(historyVariant)
        }
        return true
    }

    /**
     * DRS p7 (F2): closes the media backing of [items] BEFORE a bulk dao
     * delete, so no orphaned files / provider rows survive the removal.
     * TEXT items have nothing to close and are skipped. IMAGE deletion is
     * covered by [ClipboardItem.close] (provider file + clipboard_files row
     * via the provider delete path); VIDEO — whose [ClipboardItem.close] is
     * IMAGE-only in provider/ClipboardDatabase.kt — is deleted through the
     * same provider delete path here. Provider deletes are idempotent:
     * a missing file/row is a no-op.
     */
    private fun closeRemovedItems(items: List<ClipboardItem>) {
        for (item in items) {
            if (item.type == ItemType.TEXT) continue
            tryOrNull { item.close(appContext) }
            if (item.type == ItemType.VIDEO) {
                val uri = item.uri
                if (uri != null) {
                    tryOrNull { appContext.contentResolver.delete(uri, null, null) }
                }
            }
        }
    }

    private fun enforceHistoryLimit(clipHistory: ClipboardHistory) {
        if (prefs.clipboard.historySizeLimitEnabled.get()) {
            val nonPinnedItems = clipHistory.recent + clipHistory.other
            val nToRemove = nonPinnedItems.size - prefs.clipboard.historySizeLimit.get()
            if (nToRemove > 0) {
                val itemsToRemove = nonPinnedItems.asReversed().filterIndexed { n, _ -> n < nToRemove }
                ioScope.launch {
                    // DRS p7 (F2): close before delete — no media orphans.
                    closeRemovedItems(itemsToRemove)
                    clipHistoryDao?.delete(itemsToRemove)
                }
            }
        }
    }

    private fun enforceExpiryDate(clipHistory: ClipboardHistory) {
        val itemsToRemove = mutableSetOf<ClipboardItem>()
        if (prefs.clipboard.historyAutoCleanOldEnabled.get()) {
            val nonPinnedItems = clipHistory.recent + clipHistory.other
            val expiryTime = System.currentTimeMillis() - (prefs.clipboard.historyAutoCleanOldAfter.get() * 60 * 1000)
            itemsToRemove.addAll(nonPinnedItems.filter { it.creationTimestampMs < expiryTime })
        }
        if (prefs.clipboard.historyAutoCleanSensitiveEnabled.get()) {
            val sensitiveData = clipHistory.all.filter { it.isSensitive }
            val expiryTime = System.currentTimeMillis() - (prefs.clipboard.historyAutoCleanSensitiveAfter.get() * 1000)
            itemsToRemove.addAll(sensitiveData.filter { it.creationTimestampMs < expiryTime })
        }
        if (itemsToRemove.isNotEmpty()) {
            ioScope.launch {
                // DRS p7 (F2): close before delete on the expiry-timer path
                // too — the provider delete is idempotent (missing file/row
                // are no-ops).
                closeRemovedItems(itemsToRemove.toList())
                clipHistoryDao?.delete(itemsToRemove.toList())
            }
        }
    }

    private fun moveToTheBeginning(oldItem: ClipboardItem, newItem: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.delete(oldItem.id)
            clipHistoryDao?.insert(newItem)
        }
    }

    fun insertClip(item: ClipboardItem) {
        ioScope.launch {
            val id = clipHistoryDao?.insert(item)
            item.id = id ?: 0
        }
    }

    fun clearExactHistory(items: List<ClipboardItem>) {
        ioScope.launch {
            for (item in items) {
                item.close(appContext)
            }
            clipHistoryDao?.delete(items)
        }
    }

    /**
     * Clears all unpinned items from the clipboard history
     */
    fun clearHistory() {
        ioScope.launch {
            // DRS v1.28.0 audit fix (Medium-Low): close() deletes the backing
            // media file, but only UNPINNED rows are removed from the DB. The
            // old loop closed EVERY item, so a pinned image survived in the
            // history pointing at a deleted file — pasting it failed silently
            // forever. Only the items actually being deleted are closed now.
            for (item in currentHistory.all) {
                if (!item.isPinned) {
                    item.close(appContext)
                }
            }
            clipHistoryDao?.deleteAllUnpinned()
        }
    }

    /**
     * Clears the full clipboard history
     */
    fun clearFullHistory() {
        ioScope.launch {
            for (item in currentHistory.all) {
                item.close(appContext)
            }
            clipHistoryDao?.deleteAll()
        }
    }


    /**
     * Restore the clipboard history from a [List]
     *
     * DRS p8 (S-4): restored rows obey the same storage contract as live
     * captures — sensitive rows are refused (mirroring the S-6 gate in
     * [insertOrMoveBeginning]) and TEXT rows go through
     * [ClipboardTextPolicy.truncateForStorage], so a crafted archive can
     * neither smuggle an unbounded nor a never-capturable entry into the
     * history DB.
     *
     * @param items the [ClipboardItem] list with the new items
     */
    fun restoreHistory(items: List<ClipboardItem>) {
        ioScope.launch {
            val currentHistory = currentHistory.all
            for (item in items) {
                // DRS p8 (S-4): mirror the live-insert policy gates.
                if (item.isSensitive) continue
                val policyItem = if (item.type == ItemType.TEXT && item.text != null) {
                    item.copy(text = ClipboardTextPolicy.truncateForStorage(item.text))
                } else {
                    item
                }
                if (!currentHistory.map { it.copy(id = 0) }.contains(policyItem.copy(id = 0))) {
                    insertClip(policyItem.copy(id = 0))
                }
            }
        }
    }

    fun deleteClip(item: ClipboardItem, onlyIfUnpinned: Boolean) {
        ioScope.launch {
            if (onlyIfUnpinned) {
                clipHistoryDao?.deleteIfUnpinned(item.id)
            } else {
                clipHistoryDao?.delete(item.id)
            }
            tryOrNull {
                val uri = item.uri
                if (uri != null) {
                    appContext.contentResolver.delete(uri, null, null)
                }
            }
        }
    }

    /**
     * DRS v1.22.0: pins one history item — honestly bounded now. The cap
     * (clipboard__pinned_max_size, default 50) is checked synchronously
     * against the in-memory history before the DB write; the caller shows
     * the honest toast when the pin is refused. Re-pinning an already
     * pinned item is always allowed, and pins above a lowered cap are
     * never auto-destroyed — only new pins are gated.
     */
    fun pinClip(item: ClipboardItem): Boolean {
        if (!ClipboardTextPolicy.pinCapAllows(
                alreadyPinned = item.isPinned,
                pinnedCount = currentHistory.pinned.size,
                cap = prefs.clipboard.pinnedMaxSize.get(),
            )
        ) {
            return false
        }
        ioScope.launch {
            clipHistoryDao?.update(item.copy(isPinned = true))
        }
        return true
    }

    fun unpinClip(item: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.update(item.copy(isPinned = false))
        }
    }

    /**
     * DRS v1.9.0: replaces the text of a history item through
     * [ClipboardEditPlan] (policy-capped, timestamp bumped so the item
     * floats to the top). If the item currently sits on the primary clip
     * (matched by id), the primary clip is refreshed with the edited text
     * through the same sync path as a normal clipboard set.
     */
    fun editClipText(item: ClipboardItem, newText: String) {
        if (item.type != ItemType.TEXT) return
        val edited = ClipboardEditPlan.plan(item, newText, System.currentTimeMillis())
        ioScope.launch {
            clipHistoryDao?.update(edited)
            if (primaryClip?.id == item.id) {
                updatePrimaryClip(edited)
            }
        }
    }

    /**
     * DRS v1.9.0: the whole text history as one portable JSON document
     * (text items only — media bytes cannot round-trip through JSON).
     */
    fun exportHistoryJson(): String {
        return ClipboardHistoryExport.toJson(historyFlow.value.all)
    }

    fun pasteItem(item: ClipboardItem) {
        val editorInstance by appContext.editorInstance()
        editorInstance.commitClipboardItem(item).also { result ->
            if (!result) {
                // DRS v1.19.0: the failure toast is localized now (was a
                // hardcoded English literal in every locale).
                appContext.showShortToastSync(R.string.clipboard__paste_failed)
            }
        }
        // DRS v1.0.5: clipboard use from the clipboard panel is now credited
        // (the hook existed in the adaptation engine but was never called).
        DrsAdaptationEngine.recordClipboardUse()
    }

    /**
     * DRS v1.0.6: "copy again" - puts a history item back as the primary
     * system clipboard content without inserting it into the editor. Text
     * items only (media stays where it is); the call goes through the same
     * sync path as a normal clipboard set, so behavior is identical.
     */
    fun copyItemBack(item: ClipboardItem): Boolean {
        if (item.type != ItemType.TEXT) return false
        return try {
            updatePrimaryClip(item)
            appContext.showShortToastSync(R.string.clip__copied_item_again)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns true if the editor can accept the clip item, else false.
     */
    fun canBePasted(clipItem: ClipboardItem?): Boolean {
        if (clipItem == null) return false

        return clipItem.mimeTypes.contains("text/plain") || editorInstance.activeInfo.contentMimeTypes.any { editorType ->
            clipItem.mimeTypes.any { clipType ->
                compareMimeTypes(clipType, editorType)
            }
        }
    }

    /**
     * Cleans up.
     *
     * Unregisters the system clipboard listener, cancels clipboard clean ups.
     */
    override fun close() {
        systemClipboardManager.removePrimaryClipChangedListener(this)
        cleanUpJob.cancel()
    }
}
