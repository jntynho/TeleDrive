package com.infinity.drive.core.files

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/** Verifies retained bytes against the current provider stream before appending any new data. */
object CheckpointedImport {
    suspend fun copy(
        partial: File,
        expectedBytes: Long,
        open: () -> InputStream,
        onProgress: suspend () -> Unit
    ) {
        if (expectedBytes > 0 && partial.length() > expectedBytes && !partial.delete()) {
            throw IOException("Could not reset the import checkpoint")
        }
        val retained = partial.length()
        var restart = false
        open().buffered().use { input ->
            if (retained != 0L && !prefixMatches(partial, retained, input, onProgress)) {
                restart = true
            } else {
                append(partial, retained, expectedBytes, input, onProgress)
            }
        }
        if (restart) {
            if (!partial.delete() && partial.exists()) throw IOException("Could not reset the import checkpoint")
            open().buffered().use { append(partial, 0L, expectedBytes, it, onProgress) }
        }
    }

    private suspend fun prefixMatches(
        partial: File,
        length: Long,
        input: InputStream,
        onProgress: suspend () -> Unit
    ): Boolean {
        val buffer = ByteArray(BUFFER_BYTES)
        val existing = ByteArray(BUFFER_BYTES)
        var remaining = length
        partial.inputStream().buffered().use { checkpoint ->
            while (remaining > 0) {
                onProgress()
                val size = minOf(remaining, buffer.size.toLong()).toInt()
                if (!readExactly(input, buffer, size) || !readExactly(checkpoint, existing, size)) return false
                for (i in 0 until size) if (buffer[i] != existing[i]) return false
                remaining -= size
            }
        }
        return true
    }

    private fun readExactly(input: InputStream, buffer: ByteArray, size: Int): Boolean {
        var offset = 0
        while (offset < size) {
            val bytes = input.read(buffer, offset, size - offset)
            if (bytes <= 0) return false
            offset += bytes
        }
        return true
    }

    private suspend fun append(
        partial: File,
        retained: Long,
        expectedBytes: Long,
        input: InputStream,
        onProgress: suspend () -> Unit
    ) {
        var bytes = retained
        val buffer = ByteArray(BUFFER_BYTES)
        FileOutputStream(partial, retained != 0L).use { output ->
            while (true) {
                onProgress()
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) throw IOException("The selected provider returned no data")
                if (expectedBytes > 0 && count > expectedBytes - bytes) {
                    throw IOException("The selected file changed while being prepared")
                }
                output.write(buffer, 0, count)
                bytes += count
            }
            if (expectedBytes > 0 && bytes != expectedBytes) throw IOException("The selected file is incomplete")
            output.fd.sync()
        }
    }

    private const val BUFFER_BYTES = 256 * 1024
}
