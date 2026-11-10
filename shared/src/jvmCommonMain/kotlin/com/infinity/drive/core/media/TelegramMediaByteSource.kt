package com.infinity.drive.core.media

import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramFileInfo
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Reads a single Telegram file through TDLib's ranged download. Bytes become
 * readable as soon as the requested range is buffered instead of after the
 * whole file lands.
 */
class TelegramMediaByteSource(
    private val telegramClient: TelegramClient,
    private val remoteFileId: String
) : MediaByteSource {

    private var fileId: Int = 0
    private var totalSize: Long = -1

    override suspend fun size(): Long {
        resolve()
        return totalSize
    }

    override suspend fun read(position: Long, count: Int): ByteArray {
        resolve()
        val toRead = minOf(count.toLong(), totalSize - position)
        if (toRead <= 0) return ByteArray(0)
        readDownloaded(fileId, position, toRead)?.let { return it }
        awaitAvailable(position, toRead)
        return telegramClient.readFilePart(fileId, position, toRead)
    }

    private suspend fun readDownloaded(
        fileId: Int,
        position: Long,
        count: Long
    ): ByteArray? = runCatching {
        telegramClient.readFilePart(fileId, position, count).takeIf { it.isNotEmpty() }
    }.getOrNull()

    private suspend fun resolve() {
        if (totalSize >= 0) return
        val info = telegramClient.resolveFile(remoteFileId)
        fileId = info.fileId
        totalSize = info.sizeBytes

    }

    private suspend fun awaitAvailable(readPosition:Long,count:Long)=awaitTelegramRange(telegramClient,fileId,readPosition,count)

    override fun close() {
        // Reads request finite ranges. Closing one reader must not cancel a shared TDLib download.
        fileId = 0
        totalSize = -1
    }

    private companion object {
        const val BUFFER_TIMEOUT_MS = 30_000L
    }
}
