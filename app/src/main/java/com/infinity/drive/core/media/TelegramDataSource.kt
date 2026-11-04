package com.infinity.drive.core.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.infinity.drive.core.telegram.TelegramClient
import java.io.IOException
import kotlinx.coroutines.runBlocking

/**
 * Media3 DataSource over the shared Telegram byte source. Media3 calls these
 * methods on its own IO thread, so blocking on TDLib here is safe.
 */
@UnstableApi
class TelegramDataSource(
    telegramClient: TelegramClient,
    remoteFileId: String
) : BaseDataSource(true) {

    private val source = TelegramMediaByteSource(telegramClient, remoteFileId)

    private var uri: Uri? = null
    private var position: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        position = dataSpec.position

        val totalSize = runTelegram("resolve") { source.size() }
        if (position > totalSize) {
            source.close()
            throw androidx.media3.datasource.DataSourceException(androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        val available = (totalSize - position).coerceAtLeast(0)
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(dataSpec.length, available) else available

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val data = runTelegram("read") { source.read(position, toRead) }
        if (data.isEmpty()) throw java.io.EOFException("Telegram media ended before the requested range was available")

        System.arraycopy(data, 0, buffer, offset, data.size)
        position += data.size
        bytesRemaining -= data.size
        bytesTransferred(data.size)
        return data.size
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            source.close()
        } finally {
            if (opened) {
                opened = false
                transferEnded()
            }
            uri = null
        }
    }

    private fun <T> runTelegram(stage: String, block: suspend () -> T): T = try {
        runBlocking { block() }
    } catch (e: Exception) {
        throw IOException("Telegram stream $stage failed", e)
    }
}
