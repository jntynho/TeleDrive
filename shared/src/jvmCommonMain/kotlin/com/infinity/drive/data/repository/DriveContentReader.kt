package com.infinity.drive.data.repository

import com.infinity.drive.core.crypto.CryptoKeys
import com.infinity.drive.core.crypto.KeyUnavailableException
import com.infinity.drive.core.crypto.StreamCrypto
import com.infinity.drive.core.crypto.WrappedKeyRepository
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramDownloadEvent
import com.infinity.drive.data.local.dao.FilePartDao
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.local.entity.FilePartEntity
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Copies a verified Telegram document or multipart file, decrypting directly to the destination. */
class DriveContentReader(
    private val telegram: TelegramClient,
    private val parts: FilePartDao,
    private val keys: WrappedKeyRepository,
    private val crypto: StreamCrypto
) {
    suspend fun copyRemote(file: FileEntity, output: OutputStream, checkAccess: suspend () -> Unit): String =
        copyRemote(file, output, if (file.partCount > 0) parts.partsOf(file.id) else emptyList(), checkAccess)

    internal suspend fun copyRemote(
        file: FileEntity, output: OutputStream, revisions: List<FilePartEntity>, checkAccess: suspend () -> Unit
    ): String {
        checkAccess()
        val chat = file.chatId ?: throw IOException("No remote copy")
        val layout = layoutFor(chat, file, revisions)
            ?: throw IOException("Incomplete remote layout")
        val documents = layout.map { it.messageId }.distinct().chunked(100)
            .flatMap { telegram.getDocuments(chat, it) }.associateBy { it.messageId }
        if (referenceStatus(layout, documents, emptySet()) != Status.AVAILABLE) {
            throw IOException("Remote document or part has changed")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val key = if (file.isEncrypted) keys.get(CryptoKeys.CONTENT)?.copyOf()
            ?: throw KeyUnavailableException(CryptoKeys.CONTENT) else null
        try {
            for (part in layout) {
                checkAccess()
                val document = documents.getValue(part.messageId)
                var source: File? = null
                telegram.downloadDocument(document.remoteFileId).collect { event ->
                    if (event is TelegramDownloadEvent.Completed) source = File(event.localPath)
                }
                val downloaded = source?.takeIf { it.isFile && (document.sizeBytes <= 0 || it.length() == document.sizeBytes) }
                    ?: throw IOException("Remote download is incomplete")
                copyChecked(downloaded, part.plaintextBytes, output, digest, key)
            }
            checkAccess()
            return digest.digest().toHexString()
        } finally {
            key?.fill(0)
        }
    }

    suspend fun copyLocal(file: File, expectedBytes: Long, output: OutputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        copyChecked(file, expectedBytes, output, digest, null)
        return digest.digest().toHexString()
    }

    private suspend fun copyChecked(
        source: File,
        expectedBytes: Long,
        destination: OutputStream,
        digest: MessageDigest,
        key: ByteArray?
    ) {
        val context = coroutineContext
        var bytes = 0L
        val output = object : OutputStream() {
            override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                context.ensureActive()
                if (length > expectedBytes - bytes) throw IOException("Unexpected file size")
                digest.update(buffer, offset, length)
                destination.write(buffer, offset, length)
                bytes += length
            }
        }
        source.inputStream().buffered().use { input ->
            if (key == null) input.copyTo(output) else crypto.decryptStream(key, input, output)
        }
        if (bytes != expectedBytes) throw IOException("File is truncated")
    }
}
