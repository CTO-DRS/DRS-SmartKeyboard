/*
 * Copyright (C) 2020-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.lib.crashutility

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import androidx.activity.ComponentActivity
import com.drs.smartkeyboard.BuildConfig
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceModel
import com.drs.smartkeyboard.app.DrsPreferenceStore
import org.drs.lib.android.stringRes
import com.drs.smartkeyboard.lib.devtools.Devtools
import com.drs.smartkeyboard.lib.devtools.LogTopic
import com.drs.smartkeyboard.lib.devtools.flogWarning
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private class SafePreferenceInstanceWrapper : ReadOnlyProperty<Any?, DrsPreferenceModel?> {
    val cachedPreferenceModel = try {
        DrsPreferenceStore
    } catch (_: Throwable) {
        null
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): DrsPreferenceModel? {
        return cachedPreferenceModel?.getValue(thisRef, property)
    }
}

class CrashDialogActivity : ComponentActivity() {
    private var stacktraces: List<CrashUtility.Stacktrace> = listOf()
    private var errorReport: StringBuilder = StringBuilder()
    private val prefs by SafePreferenceInstanceWrapper()

    private val stacktrace by lazy { findViewById<TextView>(R.id.stacktrace) }
    private val reportInstructions by lazy { findViewById<TextView>(R.id.report_instructions) }
    private val copyToClipboard by lazy { findViewById<Button>(R.id.copy_to_clipboard) }
    private val openBugReportForm by lazy { findViewById<Button>(R.id.open_bug_report_form) }
    private val close by lazy { findViewById<Button>(R.id.close) }

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = layoutInflater.inflate(R.layout.crash_dialog, null)
        setContentView(layout)

        val toolbar = layout.findViewById<Toolbar>(R.id.crash_dialog_toolbar)
        setActionBar(toolbar)

        stacktraces = CrashUtility.getUnhandledStacktraces(this)
        val versionName = buildString {
            append("[")
            append(BuildConfig.VERSION_NAME)
            append("](")
            if (BuildConfig.DEBUG) {
                append(stringRes(R.string.drs__commit_by_hash_url, "hash" to BuildConfig.BUILD_COMMIT_HASH))
            } else {
                append(stringRes(R.string.drs__changelog_url, "version" to BuildConfig.VERSION_NAME))
            }
            append(")")
        }
        errorReport.apply {
            appendLine("#### Environment information")
            appendLine("- DRS Smart Keyboard $versionName (${BuildConfig.VERSION_CODE})")
            appendLine("- Device: ${Devtools.getDeviceName()}")
            appendLine("- Android: ${Devtools.getAndroidVersion()}")
            appendLine()
            appendLine("#### Attached logs and stacktrace files")
            appendCollapsibleSection(
                // DRS a11y/i18n/ux (r0-I) FIX 10 — literal → localized string.
                summary = resources.getString(R.string.crash__debug_header_summary),
                details = Devtools.generateDebugLog(this@CrashDialogActivity, prefs, includeLogcat = false),
            )
            appendLine()
            if (stacktraces.isNotEmpty()) {
                stacktraces.forEach {
                    appendCollapsibleSection(it.name, it.details)
                    appendLine()
                }
            } else {
                flogWarning(LogTopic.CRASH_UTILITY) {
                    "Stacktrace file list is empty."
                }
            }
        }
        stacktrace.text = errorReport

        reportInstructions.text =
            reportInstructions.text.toString().format(
                resources.getString(R.string.crash_dialog__bug_report_template)
            )

        copyToClipboard.setOnClickListener {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE)
            val toastMessage: String = if (clipboardManager != null && clipboardManager is ClipboardManager) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText(errorReport, errorReport))
                resources.getString(R.string.crash_dialog__copy_to_clipboard_success)
            } else {
                resources.getString(R.string.crash_dialog__copy_to_clipboard_failure)
            }
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
        }

        openBugReportForm.setOnClickListener {
            try {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(resources.getString(R.string.drs__issue_tracker_url))
                )
                startActivity(browserIntent)
            } catch (_: android.content.ActivityNotFoundException) {
                // DRS fix: never crash inside the crash dialog itself.
                Toast.makeText(this, resources.getString(R.string.crash_dialog__copy_to_clipboard_failure), Toast.LENGTH_SHORT).show()
            }
        }

        close.setOnClickListener {
            finish()
        }
    }

    /**
     * Rules for collapsible markdown on GitHub:
     *  https://gist.github.com/pierrejoubert73/902cc94d79424356a8d20be2b382e1ab
     */
    private fun StringBuilder.appendCollapsibleSection(summary: String, details: String) {
        this.appendLine("<details>")
        this.append("<summary>").append(summary).appendLine("</summary>")
        this.appendLine()
        this.appendLine("```")
        this.appendLine(details)
        this.appendLine("```")
        this.appendLine("</details>")
    }
}
