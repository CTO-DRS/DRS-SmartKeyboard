/*
 * Copyright (C) 2025-2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.lib.devtools.flogWarning

/**
 * DRS v1.23.0: the microphone permission trampoline — «بوابة الإذن
 * الشفافة». An IME service cannot host a permission dialog, so the first
 * mic-key press launches this translucent activity (nothing paints but a
 * system dialog), which requests RECORD_AUDIO, then either emits a start
 * request on the [DrsVoiceInputBus] (granted — the controller begins
 * listening immediately) or shows an honest toast (denied) and finishes.
 * Single-session safety: onDeviceAvailable checks happen in the
 * controller, not here.
 *
 * DRS v1.26.0: the denied path is no longer one dead-end toast. The pure
 * [nextPermissionAction] reads the system's rationale availability: a
 * plain denial (the system can still show its dialog again) answers with
 * the honest explanation toast, while a permanent denial ("don't ask
 * again" — Android 11+ answers future requests immediately with a result
 * and never shows the dialog) opens the app's system settings page so
 * the user has a real way out, with a toast explaining why.
 */

/** DRS v1.26.0: what a denied mic permission must do next. */
enum class PermissionNextAction {
    /** The system dialog may appear again — explain and stay light. */
    RE_EXPLAIN,

    /** Permanently denied — the only real path is the app settings page. */
    OPEN_SETTINGS,
}

/**
 * DRS v1.26.0: the pure denied-permission decision — JVM-tested. After a
 * denial, `shouldShowRequestPermissionRationale == false` means the
 * system will never show its dialog again for this app (the permanent
 * denial), so the honest answer is the settings page; `true` means the
 * dialog is still reachable and a re-explanation toast suffices.
 */
fun nextPermissionAction(canShowRationale: Boolean): PermissionNextAction =
    if (canShowRationale) PermissionNextAction.RE_EXPLAIN else PermissionNextAction.OPEN_SETTINGS

class VoicePermissionActivity : ComponentActivity() {

    private var languageTag: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languageTag = intent?.getStringExtra(EXTRA_LANGUAGE_TAG).orEmpty()

        if (hasMicrophonePermission()) {
            // A previous grant survived — never re-prompt, just start.
            DrsVoiceInputBus.requestStart(languageTag)
            finish()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            DrsVoiceInputBus.requestStart(languageTag)
        } else {
            // DRS v1.26.0: the honest denied path — a plain denial keeps
            // the light explanation toast, a permanent denial opens the
            // app's settings page because re-prompting is impossible.
            when (nextPermissionAction(
                shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
            )) {
                PermissionNextAction.RE_EXPLAIN ->
                    Toast.makeText(this, R.string.voice__permission_denied, Toast.LENGTH_SHORT)
                        .show()
                PermissionNextAction.OPEN_SETTINGS -> {
                    Toast.makeText(
                        this,
                        R.string.voice__permission_permanent,
                        Toast.LENGTH_LONG,
                    ).show()
                    runCatching { launchAppSettings() }
                        .onFailure { flogWarning { "app settings launch failed: $it" } }
                }
            }
        }
        finish()
    }

    private fun launchAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val REQUEST_CODE = 7231
        const val EXTRA_LANGUAGE_TAG = "drs:extra:voice_language_tag"

        fun createIntent(context: Context, languageTag: String): Intent =
            Intent(context, VoicePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_LANGUAGE_TAG, languageTag)
            }
    }
}
