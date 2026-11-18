package com.infinity.drive.core.transfer

import com.infinity.drive.core.crypto.CryptoKeys
import com.infinity.drive.core.crypto.StreamCrypto
import com.infinity.drive.core.crypto.WrappedKeyRepository
import com.infinity.drive.core.files.MimeTypes
import com.infinity.drive.core.media.ThumbnailStore
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramUploadEvent
import com.infinity.drive.data.local.entity.FileEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

class ApkIconUploader(
    private val telegramClient: TelegramClient,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val streamCrypto: StreamCrypto,
    private val thumbnailStore: ThumbnailStore,
    private val storagePaths: AppStoragePaths
) {
    private fun stagingDir(): File = storagePaths.cacheDir.resolve("staging").apply { mkdirs() }

    suspend fun uploadPreviewDocument(
        entity: FileEntity,
        chatId: Long,
        encrypt: Boolean
    ): String? {
        if (!canUploadPreview(entity.mimeType, entity.name, encrypt)) return null

        var staging: File? = null
        return try {
            val uploadPath: String
            val fileName: String
            val mimeType: String
            if (encrypt) {
                val key = wrappedKeyRepository.getOrCreate(CryptoKeys.CONTENT)
                val preview = thumbnailStore.thumbnailForBackup(entity.id)
                    ?.takeIf { it.size in 1..MAX_PREVIEW_BYTES } ?: return null
                fileName = "${UUID.randomUUID()}.tde"
                val sealed = File(stagingDir(), fileName).also { staging = it }
                sealed.writeBytes(streamCrypto.encryptBytes(key, preview))
                uploadPath = sealed.absolutePath
                mimeType = "application/octet-stream"
            } else {
                uploadPath = thumbnailStore.uploadThumbnailFile(entity.id)?.absolutePath ?: return null
                fileName = "${UUID.randomUUID()}.jpg"
                mimeType = "image/jpeg"
            }
            val completed = telegramClient.uploadDocument(
                chatId = chatId,
                localPath = uploadPath,
                fileName = fileName,
                mimeType = mimeType,
                caption = ICON_CAPTION,
                thumbnailPath = null
            ).first { it is TelegramUploadEvent.Completed } as TelegramUploadEvent.Completed
            val doc = completed.document
            "${doc.remoteFileId}:${doc.messageId}"
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            staging?.delete()
        }
    }

    companion object {
        private const val MAX_PREVIEW_BYTES = 512 * 1024
        const val ICON_CAPTION = "#driveinfinity-icon"
    }
}


internal fun canUploadPreview(mimeType: String, name: String, encrypted: Boolean): Boolean =
    MimeTypes.isApk(mimeType, name) ||
        encrypted && (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/"))
