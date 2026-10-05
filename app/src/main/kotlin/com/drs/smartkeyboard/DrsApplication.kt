/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.StrictMode
import android.util.Log
import androidx.core.os.UserManagerCompat
import com.drs.smartkeyboard.app.DrsPreferenceModel
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.ime.clipboard.ClipboardManager
import com.drs.smartkeyboard.ime.core.SubtypeManager
import com.drs.smartkeyboard.ime.dictionary.DictionaryManager
import com.drs.smartkeyboard.ime.editor.EditorInstance
import com.drs.smartkeyboard.ime.keyboard.KeyboardManager
import com.drs.smartkeyboard.ime.media.emoji.DrsEmojiCompat
import com.drs.smartkeyboard.ime.nlp.NlpManager
import com.drs.smartkeyboard.ime.text.gestures.GlideTypingManager
import com.drs.smartkeyboard.ime.theme.ThemeManager
import com.drs.smartkeyboard.lib.cache.CacheManager
import com.drs.smartkeyboard.lib.crashutility.CrashUtility
import com.drs.smartkeyboard.lib.devtools.Flog
import com.drs.smartkeyboard.lib.devtools.LogTopic
import com.drs.smartkeyboard.lib.devtools.flogError
import com.drs.smartkeyboard.lib.ext.ExtensionManager
import org.drs.jetpref.datastore.runtime.initAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.drs.lib.kotlin.io.deleteContentsRecursively
import org.drs.lib.kotlin.tryOrNull
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [DrsApplication] class. This is needed as in certain scenarios an application
 * reference is needed, but the Android framework hasn't finished setting up
 */
private var DrsApplicationReference = WeakReference<DrsApplication?>(null)

@Suppress("unused")
class DrsApplication : Application() {
    private val mainHandler by lazy { Handler(mainLooper) }
    private val scope = CoroutineScope(Dispatchers.Default)
    val preferenceStoreLoaded = MutableStateFlow(false)

    /** DRS roadmap phase 3: prefs delegation for the privacy-lock provider wiring. */
    private val prefs by DrsPreferenceStore

    val cacheManager = lazy { CacheManager(this) }
    val clipboardManager = lazy { ClipboardManager(this) }
    val editorInstance = lazy { EditorInstance(this) }
    val extensionManager = lazy { ExtensionManager(this) }
    val glideTypingManager = lazy { GlideTypingManager(this) }
    val keyboardManager = lazy { KeyboardManager(this) }
    val nlpManager = lazy { NlpManager(this) }
    val subtypeManager = lazy { SubtypeManager(this) }
    val themeManager = lazy { ThemeManager(this) }

    // DRS v2.8.0: app-private store of the user's picked background image and
    // of imported external dictionaries — same-process singletons, instantly
    // visible to both the settings UI and the live IME.
    val backgroundStore = lazy { com.drs.smartkeyboard.ime.theme.DrsBackgroundImageStore(this) }
    val externalDictStore = lazy { com.drs.smartkeyboard.ime.nlp.DrsExternalDictStore(this) }

    override fun onCreate() {
        super.onCreate()
        DrsApplicationReference = WeakReference(this)
        if (BuildConfig.DEBUG) {
            // DRS v1.17.0: strict mode in debug builds only — an app whose
            // core promise is never dropping a keystroke must never
            // silently do disk I/O on the main thread during development.
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build(),
            )
        }
        try {
            Flog.install(
                context = this,
                isFloggingEnabled = BuildConfig.DEBUG,
                flogTopics = LogTopic.ALL,
                flogLevels = Flog.LEVEL_ALL,
                flogOutputs = Flog.OUTPUT_CONSOLE,
            )
            CrashUtility.install(this)
            com.drs.smartkeyboard.drs.DrsStore.init(this)
            com.drs.smartkeyboard.drs.DrsCrashHandler.install(this)
            com.drs.smartkeyboard.drs.DrsEconomy.onProcessStart()
            // DRS roadmap phase 3 (privacy & trust): the provable network block.
            // (1) Baseline the per-UID traffic counters once, at process start,
            //     so the sentinel's attestation reports "bytes moved by this app
            //     during this session" — the number that can only move if OUR
            //     code touched a socket.
            com.drs.smartkeyboard.drs.privacy.DrsNetworkSentinel.captureBaseline()
            // (2) Wire the absolute-privacy-mode reader. If the pref layer is
            //     somehow unavailable, the provider throwing keeps the gates
            //     CLOSED (DrsPrivacyLock treats a throwing provider as locked) —
            //     a broken settings store must never silently reopen the
            //     network surfaces.
            com.drs.smartkeyboard.drs.privacy.DrsPrivacyLock.init {
                prefs.privacy.absoluteMode.get()
            }
            DrsEmojiCompat.init(this)
            if (!UserManagerCompat.isUserUnlocked(this)) {
                wipeCacheDirInBackground()
                extensionManager.value.init()
                registerReceiver(BootComplete(), IntentFilter(Intent.ACTION_USER_UNLOCKED))
                return
            }

            init()
        } catch (t: Throwable) {
            CrashUtility.stageException(t)
            return
        }
    }

    fun init() {
        wipeCacheDirInBackground()
        scope.launch {
            try {
                val result = DrsPreferenceStore.initAndroid(
                    context = this@DrsApplication,
                    datastoreName = DrsPreferenceModel.NAME,
                )
                Log.i("PREFS", result.toString())
                preferenceStoreLoaded.value = true
            } catch (t: Throwable) {
                // DRS: a failed preference store must never leave the splash screen
                // hanging forever — surface the crash through the crash handlers so
                // the process dies visibly and the user gets a crash notification.
                CrashUtility.stageException(t)
                throw t
            }
        }
        extensionManager.value.init()
        clipboardManager.value.initializeForContext(this)
        DictionaryManager.init(this)
        // DRS v1.2.0: the 3000-word vocalization lexicon lands on a
        // background thread — an atomic install, no locks; the seed
        // lexicon of DrsWordTashkeel serves until it arrives, and a
        // failed load only means the seed keeps serving (the extended
        // lexicon is an upgrade, never a dependency).
        scope.launch {
            try {
                val lines = assets.open("drs/tashkeel_lexicon.txt")
                    .bufferedReader().use { reader -> reader.readLines() }
                val result = com.drs.smartkeyboard.drs.DrsTashkeelLexicon.parse(lines)
                com.drs.smartkeyboard.drs.DrsTashkeelLexicon.install(result.entries)
            } catch (t: Throwable) {
                flogError(LogTopic.OTHER) { "tashkeel lexicon load failed: $t" }
            }
        }
    }


    /**
     * DRS (B4): the cacheDir wipe used to run synchronously on the MAIN
     * thread at BOTH call sites (the pre-unlock branch above and [init]) —
     * a stale/large cache dir stalled every cold start. The wipe is pure
     * hygiene: the extension index reads assets+filesDir only and the
     * extension load's cache staging dirs are created on demand after
     * startup, so nothing depends on it completing before startup
     * continues. Runs on the IO dispatcher via the same [scope].
     */
    private fun wipeCacheDirInBackground() {
        scope.launch(Dispatchers.IO) {
            runCatching { cacheDir?.deleteContentsRecursively() }
        }
    }

    private inner class BootComplete : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                try {
                    unregisterReceiver(this)
                } catch (e: Exception) {
                    flogError { e.toString() }
                }
                mainHandler.post { init() }
            }
        }
    }
}

private tailrec fun Context.drsApplication(): DrsApplication {
    return when (this) {
        is DrsApplication -> this
        is ContextWrapper -> when {
            this.baseContext != null -> this.baseContext.drsApplication()
            else -> DrsApplicationReference.get()!!
        }
        else -> tryOrNull { this.applicationContext as DrsApplication } ?: DrsApplicationReference.get()!!
    }
}

fun Context.appContext() = lazyOf(this.drsApplication())

fun Context.cacheManager() = this.drsApplication().cacheManager

fun Context.clipboardManager() = this.drsApplication().clipboardManager

fun Context.editorInstance() = this.drsApplication().editorInstance

fun Context.extensionManager() = this.drsApplication().extensionManager

fun Context.glideTypingManager() = this.drsApplication().glideTypingManager

fun Context.keyboardManager() = this.drsApplication().keyboardManager

fun Context.nlpManager() = this.drsApplication().nlpManager

fun Context.subtypeManager() = this.drsApplication().subtypeManager

fun Context.themeManager() = this.drsApplication().themeManager

fun Context.backgroundStore() = this.drsApplication().backgroundStore

fun Context.externalDictStore() = this.drsApplication().externalDictStore
