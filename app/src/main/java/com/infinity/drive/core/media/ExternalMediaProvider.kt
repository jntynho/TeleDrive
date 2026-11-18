package com.infinity.drive.core.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext
import java.io.FileNotFoundException

/** The receiving player gets a seekable, read-only Android URI for one media item. */
class ExternalMediaProvider : ContentProvider() {
    private val streams by lazy { GlobalContext.get().get<ExternalMediaStreams>() }

    override fun onCreate() = true

    override fun getType(uri: Uri): String = "application/octet-stream"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val grant = streams.grant(uri) ?: return null
        try {
            blocking { streams.assertReadable(grant) }
        } catch (_: Exception) {
            return null
        }
        val columns = projection?.toList() ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val values = columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> grant.file.name
                OpenableColumns.SIZE -> grant.file.sizeBytes
                else -> null
            }
        }
        return MatrixCursor(columns.toTypedArray(), 1).apply { addRow(values) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only stream")
        val grant = streams.grant(uri) ?: throw FileNotFoundException("Media stream unavailable")
        if (!streams.openSlots.tryAcquire()) throw FileNotFoundException("Too many media streams")
        var source: MediaByteSource? = null
        var thread: HandlerThread? = null
        try {
            blocking { streams.assertReadable(grant) }
            val opened = streams.source(grant).also { source = it }
            val size = grant.file.sizeBytes
            val handler = HandlerThread("TeleDriveMedia").also { thread = it; it.start() }
            val callback = object : ProxyFileDescriptorCallback() {
                override fun onGetSize(): Long = checked("size") {
                    streams.assertReadable(grant)
                    size
                }

                override fun onRead(offset: Long, sizeRequested: Int, data: ByteArray): Int = checked("read") {
                    require(offset >= 0 && sizeRequested in 0..data.size)
                    streams.assertReadable(grant)
                    val expected = minOf(sizeRequested.toLong(), (size - offset).coerceAtLeast(0)).toInt()
                    var count = 0
                    while (count < expected) {
                        val bytes = opened.read(offset + count, expected - count)
                        check(bytes.isNotEmpty() && bytes.size <= expected - count)
                        bytes.copyInto(data, destinationOffset = count)
                        count += bytes.size
                    }
                    streams.assertReadable(grant)
                    count
                }

                override fun onRelease() {
                    try {
                        opened.close()
                    } finally {
                        streams.openSlots.release()
                        handler.quitSafely()
                    }
                }
            }
            val storage = requireNotNull(context?.getSystemService(StorageManager::class.java))
            return storage.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, callback, Handler(handler.looper))
        } catch (error: Exception) {
            try {
                source?.close()
            } finally {
                thread?.quitSafely()
                streams.openSlots.release()
            }
            throw FileNotFoundException("Media stream unavailable").apply { initCause(error) }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    private fun <T> checked(operation: String, access: suspend () -> T): T =
        try {
            blocking(access)
        } catch (_: Exception) {
            throw ErrnoException(operation, OsConstants.EIO)
        }

    private fun <T> blocking(access: suspend () -> T): T =
        runBlocking(Dispatchers.IO) { withTimeout(35_000) { access() } }
}
