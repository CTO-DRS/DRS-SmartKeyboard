/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.ime.clipboard.provider

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.drs.smartkeyboard.BuildConfig
import com.drs.smartkeyboard.lib.devtools.flogError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.drs.lib.kotlin.tryOrNull
import androidx.core.net.toUri

/**
 * Allows apps to access images and videos on the clipboard.
 *
 * This is sometimes called by the UI thread, so all functions are non blocking.
 * The dao handle is built synchronously in [onCreate] (DRS p7 (F12)); the
 * heavier database work stays async on [ioScope].
 */
class ClipboardMediaProvider : ContentProvider() {
    private var clipboardFilesDao: ClipboardFilesDao? = null
    private val cachedFileInfos: HashMap<Long, ClipboardFileInfo> = hashMapOf()
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider.clipboard"
        val IMAGE_CLIPS_URI: Uri = "content://$AUTHORITY/clips/images".toUri()
        val VIDEO_CLIPS_URI: Uri = "content://$AUTHORITY/clips/videos".toUri()

        private const val IMAGE_CLIP_ITEM = 0
        private const val IMAGE_CLIPS_TABLE = 1
        private const val VIDEO_CLIP_ITEM = 2
        private const val VIDEO_CLIPS_TABLE = 3

        private val Matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "clips/images/#", IMAGE_CLIP_ITEM)
            addURI(AUTHORITY, "clips/images", IMAGE_CLIPS_TABLE)
            addURI(AUTHORITY, "clips/videos/#", VIDEO_CLIP_ITEM)
            addURI(AUTHORITY, "clips/videos", VIDEO_CLIPS_TABLE)
        }
    }

    object Columns {
        const val MediaUri = "media_uri"
        const val MimeTypes = "mime_types"
    }

    /**
     * DRS p7 (F12): cache priming only — the dao itself is built
     * synchronously in [onCreate]; this walks the saved rows off-thread so
     * [cachedFileInfos] survives a process restart.
     */
    fun init() {
        for (clipboardFileInfo in clipboardFilesDao?.getAll() ?: emptyList()) {
            cachedFileInfos[clipboardFileInfo.id] = clipboardFileInfo
        }
    }

    override fun onCreate(): Boolean {
        // DRS p7 (F12): the dao used to be built asynchronously — an insert()
        // arriving before init() finished silently dropped its row (the file
        // stayed memory-only). Room's databaseBuilder does not open the
        // database on the calling thread, so the handle + dao are built
        // synchronously here, closing the null-dao startup window; only the
        // cache-priming getAll() stays on the IO scope.
        clipboardFilesDao = ClipboardFilesDatabase.new(context!!).clipboardFilesDao()
        ioScope.launch {
            init()
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val id = tryOrNull { ContentUris.parseId(uri) } ?: return null
        if (projection != null) {
            return if (projection.contains(MediaStore.Images.Media.ORIENTATION)) {
                // DRS p7 (F12): the ORIENTATION-projection cursor is the
                // answer — it used to be computed and then discarded by
                // falling through to the full-row cursor.
                clipboardFilesDao?.getCursorByIdWithColumns(id, MediaStore.Images.Media.ORIENTATION)
            } else {
                // Return null if the projection query is invalid
                null
            }
        }
        return clipboardFilesDao?.getCursorById(id)
    }

    override fun getType(uri: Uri): String? {
        return when (Matcher.match(uri)) {
            IMAGE_CLIP_ITEM, VIDEO_CLIP_ITEM -> {
                cachedFileInfos.getOrDefault(ContentUris.parseId(uri), null)?.mimeTypes?.getOrNull(0)
            }
            IMAGE_CLIPS_TABLE -> "${ContentResolver.CURSOR_DIR_BASE_TYPE}/vnd.drskeyboard.image_clip_table"
            VIDEO_CLIPS_TABLE -> "${ContentResolver.CURSOR_DIR_BASE_TYPE}/vnd.drskeyboard.video_clip_table"
            else -> null
        }
    }

    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? {
        return when (Matcher.match(uri)) {
            IMAGE_CLIP_ITEM, VIDEO_CLIP_ITEM -> {
                cachedFileInfos.getOrDefault(ContentUris.parseId(uri), null)?.mimeTypes?.toTypedArray()
            }
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = ContentUris.parseId(uri)
        val file = ClipboardFileStorage.getFileForId(context!!, id)

        // Nothing has permission to write anyway.
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        when (val m = Matcher.match(uri)) {
            IMAGE_CLIPS_TABLE, VIDEO_CLIPS_TABLE -> {
                // DRS p7 (F12): failure is null now — never a fake `.../0`
                // uri (parseId = 0, the file never exists and the row was
                // silently dropped). The values / dao guards close the
                // remaining silent-drop windows; the async row insert is
                // runCatching-wrapped so it cannot kill the scope quietly.
                val mediaUriRaw = values?.getAsString(Columns.MediaUri) ?: return null
                val mimeTypesRaw = values.getAsString(Columns.MimeTypes) ?: return null
                val displayName = values.getAsString(OpenableColumns.DISPLAY_NAME) ?: return null
                val dao = clipboardFilesDao ?: return null
                val mediaUri = Uri.parse(mediaUriRaw)
                return try {
                    // Get the orientation of the image
                    val exifInterface = ExifInterface(context!!.contentResolver.openInputStream(mediaUri)!!)
                    var rotation = 0
                    val orientation = exifInterface.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> rotation = 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> rotation = 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> rotation = 270
                    }
                    val id = ClipboardFileStorage.cloneUri(context!!, mediaUri)
                    val size = ClipboardFileStorage.getFileForId(context!!, id).length()
                    val mimeTypes = mimeTypesRaw.split(",")
                    val fileInfo = ClipboardFileInfo(id, displayName, size, rotation, mimeTypes)
                    cachedFileInfos[id] = fileInfo
                    ioScope.launch {
                        runCatching { dao.insert(fileInfo) }
                            .onFailure {
                                // DRS (r0-B): redacted log — a raw message can
                                // embed the copied media's uri/name; only the
                                // exception class is logged.
                                flogError { "clipboard media row insert failed: ${it.javaClass.simpleName}" }
                            }
                    }
                    if (m == IMAGE_CLIPS_TABLE) {
                        ContentUris.withAppendedId(IMAGE_CLIPS_URI, id)
                    } else {
                        ContentUris.withAppendedId(VIDEO_CLIPS_URI, id)
                    }
                } catch (e: Exception) {
                    // DRS (r0-B): redacted log (class only — messages can
                    // embed the copied media's uri/name).
                    flogError { "clipboard media insert failed: ${e.javaClass.simpleName}" }
                    null
                }
            }
            else -> error("Unable to identify type of $uri")
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        when (Matcher.match(uri)) {
            IMAGE_CLIP_ITEM, VIDEO_CLIP_ITEM -> {
                val id = ContentUris.parseId(uri)
                ClipboardFileStorage.deleteById(context!!, id)
                cachedFileInfos.remove(id)
                context?.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ioScope.launch {
                    clipboardFilesDao?.delete(id)
                }
                return 1
            }
            else -> error("Unable to identify type of $uri")
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        error("This ContentProvider does not support update.")
    }
}
