// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.core.files

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.infinity.drive.core.common.SafeLog
import java.io.File
import java.io.OutputStream

/**
 * Writes finished downloads to the shared Downloads/TG Drive folder, so they
 * survive uninstall, are reachable from any file manager, and get indexed for
 * the gallery. MediaStore owns the entry on Android 10 and above; older
 * releases and devices with all-files access write the file directly.
 */
class MediaStoreDownloadWriter(
    private val context: Context
) : DownloadWriter {

    /**
     * Saves one download. [folderPath] mirrors the drive folder the file lives
     * in, so downloading a folder recreates its tree under Downloads/TG Drive.
     * Returns the absolute path of the saved copy, or null when it could not
     * be written.
     */
    override fun write(
        fileName: String,
        mimeType: String,
        folderPath: String?,
        body: (OutputStream) -> Unit
    ): String? {
        val safeName=FileNameUtils.sanitize(fileName)
        val safeFolder=FileNameUtils.relativeFolderPath(folderPath)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeThroughMediaStore(safeName, mimeType, safeFolder, body)
        } else {
            writeDirectly(safeName, safeFolder, body)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeThroughMediaStore(
        fileName: String,
        mimeType: String,
        folderPath: String?,
        body: (OutputStream) -> Unit
    ): String? {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(folderPath))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null

        val written = runCatching {
            resolver.openOutputStream(uri)?.use(body) ?: error("No output stream")
        }.isSuccess

        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }

        runCatching {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
        }

        return resolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.toString()
    }

    private fun writeDirectly(
        fileName: String,
        folderPath: String?,
        body: (OutputStream) -> Unit
    ): String? {
        val root = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER_NAME
        )
        val directory = folderPath?.takeIf { it.isNotBlank() }?.let { File(root, it) } ?: root
        if (!directory.exists() && !directory.mkdirs()) return null

        val target = File(directory, FileNameUtils.uniqueName(fileName) { candidate ->
            !File(directory,candidate).createNewFile()
        })
        return runCatching {
            target.outputStream().buffered().use(body)
            target.absolutePath
        }.getOrElse {
            SafeLog.w(TAG, "Download write failed", it)
            target.delete()
            null
        }
    }

    private fun relativePath(folderPath: String?): String {
        val suffix = folderPath?.trim('/')?.takeIf { it.isNotBlank() } ?: return RELATIVE_PATH
        return "$RELATIVE_PATH/$suffix"
    }

    private companion object {
        const val TAG = "DownloadWriter"
        const val FOLDER_NAME = "TG Drive"
        val RELATIVE_PATH = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER_NAME"
    }
}
