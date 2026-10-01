/*
 * Copyright (C) 2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.drs.smartkeyboard.R
import com.drs.smartkeyboard.app.DrsPreferenceStore
import com.drs.smartkeyboard.app.apptheme.DrsAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.drs.jetpref.datastore.model.collectAsState
import org.drs.lib.android.AndroidClipboardManager
import org.drs.lib.android.AndroidVersion
import org.drs.lib.android.stringRes
import org.drs.lib.android.systemService
import org.drs.lib.compose.ProvideLocalizedResources
import org.drs.lib.compose.stringRes
import org.drs.lib.kotlin.mimeTypeFilterOf

class DrsCopyToClipboardActivity : ComponentActivity() {
    // DRS p7 (F5): the decode result / error are Compose snapshot states —
    // publishing them after the async decode recomposes the sheet.
    private var error: CopyToClipboardError? by mutableStateOf(null)
    private var bitmap: Bitmap? by mutableStateOf(null)

    // DRS p8 (C-1): the shared Uri is only STAGED here on launch; the system
    // clipboard is written from BottomSheet's confirm button after the user
    // has seen the preview (see uriToBitmap for the rationale).
    private var pendingUri: Uri? = null

    private val clipboardManager by lazy { systemService(AndroidClipboardManager::class) }
    private val filter = mimeTypeFilterOf("image/*")

    internal enum class CopyToClipboardError {
        UNKNOWN_ERROR,
        TYPE_NOT_SUPPORTED_ERROR;

        @Composable
        fun showError(): String {
            val textId = when (this) {
                UNKNOWN_ERROR -> R.string.send_to_clipboard__unknown_error
                TYPE_NOT_SUPPORTED_ERROR -> R.string.send_to_clipboard__type_not_supported_error
            }
            return stringRes(id = textId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            Content()
        }
    }

    override fun onPause() {
        finish()
        super.onPause()
    }

    private fun handleIntent(intent: Intent) {
        val type = intent.type
        val action = intent.action

        if (Intent.ACTION_SEND != action || type == null) {
            error = CopyToClipboardError.UNKNOWN_ERROR
            return
        }
        if (!filter.matches(type) || !intent.hasExtra(Intent.EXTRA_STREAM)) {
            error = CopyToClipboardError.TYPE_NOT_SUPPORTED_ERROR
            return
        }

        val uri: Uri? =
            if (AndroidVersion.ATLEAST_API33_T) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }

        if (uri == null) {
            error = CopyToClipboardError.TYPE_NOT_SUPPORTED_ERROR
            return
        }
        uriToBitmap(uri)
    }

    /**
     * DRS p7 (F5): the full bitmap used to be decoded on MAIN inside
     * onCreate (ImageDecoder.decodeBitmap / MediaStore.getBitmap) — jank /
     * ANR on large images, and an uncaught IOException crashed the activity
     * on a malformed stream. The decode itself moved into
     * lifecycleScope.launch(Dispatchers.IO) wrapped in runCatching — success
     * publishes the bitmap, failure surfaces the existing UNKNOWN_ERROR so
     * the sheet shows its error text.
     *
     * DRS p8 (C-1): this activity is an EXPORTED share target, and the old
     * code set the GLOBAL system primary clip synchronously here in onCreate
     * — any zero-permission app could poison the clipboard via
     * ACTION_SEND + EXTRA_STREAM with no user consent. The clip is no longer
     * created on launch: the Uri is only staged in [pendingUri] and the
     * decode runs for the preview. The actual setPrimaryClip happens in
     * [BottomSheet]'s OK handler, on the user's explicit tap, and ONLY when
     * the decode succeeded — a failed decode leaves the clipboard untouched.
     * Finish-on-pause behavior is unchanged.
     */
    private fun uriToBitmap(uri: Uri) {
        pendingUri = uri
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                if (AndroidVersion.ATLEAST_API28_P) {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
            }.onSuccess { decoded ->
                bitmap = decoded
            }.onFailure {
                error = CopyToClipboardError.UNKNOWN_ERROR
            }
        }
    }

    @Composable
    private fun Content() {
        val prefs by DrsPreferenceStore
        ProvideLocalizedResources(
            resourcesContext = this,
            appName = R.string.app_name,
            forceLayoutDirection = LayoutDirection.Ltr,
        ) {
            val theme by prefs.other.settingsTheme.collectAsState()
            DrsAppTheme(theme) {
                BottomSheet {
                    Row {
                        Text(
                            text = error?.showError()
                                ?: bitmap?.let { stringRes(id = R.string.send_to_clipboard__description__copied_image_to_clipboard) }
                                ?: stringRes(R.string.send_to_clipboard__unknown_error),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    bitmap?.let {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Image(
                                modifier = Modifier
                                    .padding(start = 64.dp, end = 64.dp, top = 32.dp, bottom = 8.dp),
                                bitmap = it.asImageBitmap(),
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BottomSheet(
        content: @Composable ColumnScope.() -> Unit,
    ) {
        ModalBottomSheet(
            modifier = Modifier.navigationBarsPadding(),
            onDismissRequest = { finish() }
        ) {
            Column {
                content()
                // DRS p8 (C-1): the copy is user-consented now — the GLOBAL
                // clipboard write happens HERE, on the explicit OK tap, never
                // on launch. While the decode is still in flight there is
                // nothing to confirm yet (button disabled); when the decode
                // failed the button only dismisses the sheet and the clipboard
                // is left untouched.
                Button(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(16.dp),
                    enabled = bitmap != null || error != null,
                    onClick = {
                        if (bitmap != null) {
                            pendingUri?.let { uri ->
                                clipboardManager.setPrimaryClip(
                                    ClipData.newUri(contentResolver, "image", uri)
                                )
                            }
                        }
                        finish()
                    },
                    colors = ButtonDefaults.textButtonColors(),
                ) {
                    Text(text = stringRes(id = R.string.action__ok))
                }
            }
        }
    }
}
