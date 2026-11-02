// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.core.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.core.net.toUri
import com.infinity.drive.core.common.SafeLog
import java.io.File

/**
 * Turns a SAF-picked document into a real path the transfer engine can upload
 * and resume from. When the picked document is a file this app can already read
 * directly, that original path is used so the upload does not duplicate the
 * bytes and "Free up space" can reclaim the real copy later. Anything else
 * (cloud providers, in-memory providers) is copied into app storage, because a
 * content URI permission is not guaranteed to survive a reboot.
 */
class AndroidFileImporter(
    private val context: Context,
    private val storagePaths: AppStoragePaths
) : FileImporter {

    override fun import(reference: String): ImportedFile? {
        val direct = runCatching { File(reference) }.getOrNull()?.takeIf { it.isFile }
        if (direct != null) {
            return ImportedFile(direct.absolutePath, direct.name, direct.length())
        }
        return import(reference.toUri())
    }

    private fun import(uri: Uri): ImportedFile? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val direct = uri.path?.let(::File)?.takeIf { it.isFile }
            if (direct != null) {
                return ImportedFile(direct.absolutePath, direct.name, direct.length())
            }
        }
        readablePath(uri)?.let { source ->
            return ImportedFile(source.absolutePath, source.name, source.length())
        }

        val metadata = queryMetadata(uri)
        val displayName = FileNameUtils.sanitize(
            metadata?.first ?: uri.lastPathSegment ?: "selected-file"
        )
        // A provider's display name and byte length do not identify its content.
        // Each import owns a directory, retaining the original document name
        // while simultaneous selections can never overwrite each other.
        val targetDir = File(File(storagePaths.filesDir, IMPORT_DIR),java.util.UUID.randomUUID().toString()).apply { mkdirs() }
        val target = File(targetDir, displayName)

        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: run {
                SafeLog.w(TAG, "Import failed: the provider refused to open the document")
                return null
            }
            ImportedFile(target.absolutePath, displayName, target.length())
        }.getOrElse {
            SafeLog.w(TAG, "Import failed", it)
            target.delete()
            null
        }
    }

    /** Drops a staged copy the drive turned out not to need. */
    override fun discard(imported: ImportedFile) {
        if (isStaged(imported.path)) {
            val file=File(imported.path)
            file.delete()
            if(file.parentFile!=File(storagePaths.filesDir,IMPORT_DIR))file.parentFile?.delete()
        }
    }

    override fun expand(reference: String): List<ImportSource> =
        expandDirectory(reference)
            ?: expandSafTree(reference)
            ?: listOf(ImportSource(reference, ""))

    override fun isStaged(path: String): Boolean =
        runCatching { File(path).canonicalFile.toPath().startsWith(File(storagePaths.filesDir,IMPORT_DIR).canonicalFile.toPath()) }.getOrDefault(false)

    override fun sweepOrphans(referencedPaths: Set<String>) {
        val root=File(storagePaths.filesDir,IMPORT_DIR)
        if(!root.isDirectory)return
        root.walkBottomUp().forEach { file ->
            if(file.isFile && file.absolutePath !in referencedPaths)file.delete()
            else if(file.isDirectory && file!=root && file.list()?.isEmpty()==true)file.delete()
        }
    }

    /**
     * SAF providers do not always expose a real filesystem path. Walk the tree
     * through DocumentFile instead of rejecting folders selected from those
     * providers. The selected folder itself becomes the first path segment.
     */
    private fun expandSafTree(reference: String): List<ImportSource>? {
        val uri = runCatching { Uri.parse(reference) }.getOrNull()
            ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
            ?: return null
        if (!DocumentsContract.isTreeUri(uri)) return null
        val root = runCatching { DocumentFile.fromTreeUri(context, uri) }
            .getOrNull()
            ?.takeIf { it.isDirectory }
            ?: return null
        val rootName = FileNameUtils.sanitize(root.name ?: "Folder")
        val result = mutableListOf<ImportSource>()

        fun visit(directory: DocumentFile, relativeFolder: String) {
            directory.listFiles()
                .sortedBy { it.name.orEmpty().lowercase() }
                .forEach { child ->
                    val childName = FileNameUtils.sanitize(child.name ?: "unnamed")
                    if (child.isDirectory) {
                        visit(child, "$relativeFolder/$childName")
                    } else if (child.isFile && child.canRead()) {
                        result += ImportSource(
                            reference = child.uri.toString(),
                            relativeFolder = relativeFolder
                        )
                    }
                }
        }

        visit(root, rootName)
        return result
    }

    private fun readablePath(uri: Uri): File? {
        val candidate = DocumentTreePaths.documentToFilePath(context, uri) ?: return null
        val file = File(candidate)
        return file.takeIf { it.isFile && it.canRead() && it.length() > 0 }
    }

    private fun queryMetadata(uri: Uri): Pair<String, Long>? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
            (name ?: uri.lastPathSegment ?: "file") to size
        }

    companion object {
        private const val TAG = "FileImporter"
        private const val IMPORT_DIR = "imports"
    }
}
