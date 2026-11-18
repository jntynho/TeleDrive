package com.infinity.drive.data.repository

import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramException
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.local.dao.FilePartDao
import com.infinity.drive.domain.model.FileTags
import com.infinity.drive.data.remote.telegram.ManifestCodec
import com.infinity.drive.data.remote.telegram.RemoteFileManifest

/**
 * Rewrites the caption manifest of already-uploaded files so organisational
 * state (name, folder, favorite, hidden, archived, trash) lives in Telegram
 * and survives a local wipe. Local rows are the fast path; the caption is the
 * durable copy.
 */
class FileManifestPublisher(
    private val telegramClient: TelegramClient,
    private val manifestCodec: ManifestCodec,
    private val folderPathResolver: FolderPathResolver,
    private val parts: FilePartDao
) {

    suspend fun publish(entity: FileEntity): AppResult<Unit> {
        val chatId = entity.chatId ?: return AppResult.Success(Unit)
        val messageId = entity.messageId ?: return AppResult.Success(Unit)
        val part = if (entity.partCount > 1) {
            parts.partsOf(entity.id).firstOrNull { it.chatId == chatId && it.messageId == messageId }
                ?: return AppResult.Failure(AppError.NotFound)
        } else null
        val manifest = RemoteFileManifest(
            version = if (part == null) RemoteFileManifest.VERSION else RemoteFileManifest.PART_VERSION,
            fileId = entity.id,
            name = entity.name,
            folderPath = folderPathResolver.pathOf(entity.folderId ?: entity.preTrashFolderId),
            folderId = entity.folderId ?: entity.preTrashFolderId,
            mimeType = entity.mimeType,
            sizeBytes = entity.sizeBytes,
            contentHash = entity.contentHash,
            hidden = entity.isHidden,
            archived = entity.isArchived,
            favorite = entity.isFavorite,
            trashedAt = entity.trashedAt,
            encrypted = entity.isEncrypted,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
            width = entity.width,
            height = entity.height,
            durationMs = entity.durationMs,
            iconFileId = entity.iconFileId,
            tags = FileTags.decode(entity.tagsEncoded),
            partCount = entity.partCount,
            partIndex = part?.partIndex ?: 0,
            partOffset = part?.plainOffset ?: 0,
            partSize = part?.plainSize ?: 0
        )
        return try {
            telegramClient.editCaption(
                chatId,
                messageId,
                manifestCodec.encode(manifest, entity.isEncrypted)
            )
            AppResult.Success(Unit)
        } catch (e: TelegramException) {
            AppResult.Failure(
                if (e.isRateLimit) AppError.RateLimited(e.retryAfterSeconds ?: 0)
                else AppError.TelegramError(e.code, e.message)
            )
        }
    }

}
