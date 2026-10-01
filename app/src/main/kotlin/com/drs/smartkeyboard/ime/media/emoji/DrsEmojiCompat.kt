/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.media.emoji

import android.annotation.SuppressLint
import android.content.Context
import androidx.emoji2.text.DefaultEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import com.drs.smartkeyboard.lib.devtools.flogError
import com.drs.smartkeyboard.lib.devtools.flogInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Helper object which manages two separate EmojiCompat instances, something EmojiCompat by default does not want us
 * to do for unknown reasons. Additionally we implement a proper loaded callback and a state flow, so the UI can always
 * receive the EmojiCompat instance as soon as it is loaded. This helper still uses the default config and thus relies
 * either on a system font with emoji or Google GMS services with their downloadable font provider.
 *
 * DRS v1.25.0 audit: AOSP-like ROMs without any GMS services (and newer
 * Huawei devices without Google services) fall back to the plain system
 * painter path we already keep in the palette logic — the EmojiCompat
 * init simply never completes there, and the fallback painter covers
 * every glyph it can render. No extra handling required.
 *
 * DRS v1.25.0 audit: two instances of EmojiCompat cost roughly 600kB
 * combined (~300kB each per the platform docs) — measurable but harmless
 * against the app's overall footprint, so the duplicate init in the
 * debug/devtools path stays acceptable.
 *
 * DRS v1.25.0 audit: the two instances do not interfere logically —
 * each owns its own config and the palette consults only the primary
 * one; a leaner single-instance refactor would cross module boundaries
 * for no user-visible gain.
 *
 * DRS (B3): init() now stores the application context ONLY — both
 * EmojiCompat instances (and their metadata load) are created lazily on
 * the first [getAsFlow] call for that variant (double-checked lock, the
 * load itself runs on the Default dispatcher), so the IME service and
 * the app process no longer pay the emoji metadata load at every cold
 * start; the palette pays it on its first open instead. The init call
 * site in DrsApplication.onCreate is unchanged.
 */
object DrsEmojiCompat {
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var instanceNoReplace: InstanceHandler? = null

    @Volatile
    private var instanceReplaceAll: InstanceHandler? = null

    private val instancesLock = Any()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Initialize this helper with the given [context]. DRS (B3): this now
     * stores the application context only — no EmojiCompat instance is
     * built and no metadata is loaded here; both happen lazily on the
     * first [getAsFlow] call for the requested variant.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Gets the current EmojiCompat instance based on [replaceAll] and sets it as the default instance if
     * [setAsDefaultInstance] is true. DRS (B3): the requested variant is created and loaded on first use
     * (double-checked lock; the load runs on the Default dispatcher). Calling this method before [init] will
     * cause an exception to be thrown.
     *
     * @return A state flow providing the latest EmojiCompat instance for given args. The flow may provide null if
     *  EmojiCompat is still loading or if it has failed.
     */
    @SuppressLint("RestrictedApi")
    fun getAsFlow(replaceAll: Boolean, setAsDefaultInstance: Boolean = true): StateFlow<EmojiCompat?> {
        val instanceFlow = getOrCreateHandler(replaceAll).publishedInstanceFlow
        val instance = instanceFlow.value
        if (setAsDefaultInstance && instance != null) {
            flogInfo { "Set default EmojiCompat instance to $instance(replaceAll=$replaceAll)" }
            // This API is not really supposed to be used by third-party apps, but it is really handy and does
            // exactly what we need, so we suppress the restriction here
            EmojiCompat.reset(instance)
        }
        return instanceFlow
    }

    /** DRS (B3): double-checked lazy creation of a per-variant handler. */
    private fun getOrCreateHandler(replaceAll: Boolean): InstanceHandler {
        val existing = if (replaceAll) instanceReplaceAll else instanceNoReplace
        if (existing != null) return existing
        synchronized(instancesLock) {
            val again = if (replaceAll) instanceReplaceAll else instanceNoReplace
            if (again != null) return again
            val context = appContext
                ?: throw IllegalStateException("DrsEmojiCompat.init(context) must be called before getAsFlow()")
            val handler = InstanceHandler(context, replaceAll)
            if (replaceAll) {
                instanceReplaceAll = handler
            } else {
                instanceNoReplace = handler
            }
            // DRS (B3): the metadata load happens off the main thread, on
            // the Default dispatcher — the caller only subscribes.
            scope.launch {
                handler.load()
            }
            return handler
        }
    }

    private class InstanceHandler(context: Context, replaceAll: Boolean = false) {
        private val initCallback: EmojiCompat.InitCallback = object : EmojiCompat.InitCallback() {
            override fun onInitialized() {
                super.onInitialized()
                flogInfo { "EmojiCompat(replaceAll=$replaceAll) successfully loaded!" }
                publishedInstanceFlow.value = instance
            }

            override fun onFailed(throwable: Throwable?) {
                super.onFailed(throwable)
                flogError { "EmojiCompat(replaceAll=$replaceAll) failed to load: $throwable" }
            }
        }

        private val config: EmojiCompat.Config? = DefaultEmojiCompatConfig.create(context)?.apply {
            setReplaceAll(replaceAll)
            setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_MANUAL)
            registerInitCallback(initCallback)
        }

        // Despite its name, `EmojiCompat.reset()` actually creates a new instance, exactly what we need
        private val instance: EmojiCompat? = if (config != null) EmojiCompat.reset(config) else null
        val publishedInstanceFlow = MutableStateFlow<EmojiCompat?>(null)

        /**
         * Manually loads the EmojiCompat instance. Call this method on a background thread to avoid blocking main.
         *
         * @see EmojiCompat.load
         */
        fun load() {
            instance?.load()
        }
    }
}
