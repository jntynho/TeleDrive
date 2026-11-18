package com.infinity.drive.presentation.preview

import kotlinx.coroutines.ensureActive
import net.lingala.zip4j.ZipFile
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/** Opens one entry by its position in the original ZIP. No archive paths are used on disk. */
object ZipEntryReader {
    suspend fun copy(
        archive: PreviewContent.Archive,
        entry: PreviewContent.ArchiveEntry,
        password: CharArray?,
        output: OutputStream,
        canRead: suspend () -> Boolean
    ) {
        check(canRead())
        val zip = if (password != null) ZipFile(archive.path, password) else ZipFile(archive.path)
        zip.use {
            val header = zip.fileHeaders.getOrNull(entry.index)
            require(header != null && !header.isDirectory && header.fileName == entry.name &&
                header.uncompressedSize == entry.sizeBytes && header.crc == entry.crc)
            zip.getInputStream(header).use { input ->
                val buffer = ByteArray(64 * 1024)
                var remaining = entry.sizeBytes
                while (remaining > 0) {
                    coroutineContext.ensureActive()
                    check(canRead())
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    check(count > 0)
                    output.write(buffer, 0, count)
                    remaining -= count
                }
                check(input.read() == -1 && canRead())
            }
        }
    }

    fun suggestedName(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')
        .filterNot { it.isISOControl() || it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' }
        .trim().trim('.').take(180).ifBlank { "archive-entry" }
}
