package com.infinity.drive.data.repository

import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.files.FileImporter
import com.infinity.drive.core.files.FileNameUtils
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.core.telegram.RemoteDocument
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.dao.FilePartDao
import com.infinity.drive.data.local.dao.TransferDao
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.local.entity.FilePartEntity
import com.infinity.drive.data.remote.telegram.ManifestCodec
import com.infinity.drive.data.remote.telegram.RemoteFileManifest
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.repository.TransferRepository
import com.infinity.drive.core.common.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class TelegramFileVersions(
    private val client: TelegramClient,
    private val codec: ManifestCodec,
    private val files: FileDao,
    private val parts: FilePartDao,
    private val active: ActiveChannel,
    private val access: HiddenContentAccess,
    private val reader: DriveContentReader,
    private val importer: FileImporter,
    private val storage: AppStoragePaths,
    private val transfers: TransferRepository,
    private val transferRows: TransferDao
) {
    data class Revision(val id: Long, val modifiedAt: Long, val size: Long)

    private data class Archived(val revision: Long, val manifest: RemoteFileManifest, val document: RemoteDocument)

    private val staging = Mutex()

    /** Snapshots confirmed bytes before the transfer can replace or discard the current mapping. */
    suspend fun retain(entity: FileEntity, drive: Long): List<Long> {
        val primary = entity.messageId?.takeIf { entity.chatId == drive } ?: return emptyList()
        val oldParts = parts.partsOf(entity.id)
        val ids = if (oldParts.isEmpty()) listOf(primary) else oldParts.map { requireNotNull(it.messageId) }
        val documents = ids.chunked(100).flatMap { client.getDocuments(drive, it) }.associateBy { it.messageId }
        check(ids.all(documents::containsKey)) { "The current Telegram version is incomplete." }
        check(entity.remoteUniqueId == null || documents[primary]?.uniqueFileId == entity.remoteUniqueId) {
            "The primary Telegram document changed."
        }
        check(oldParts.all { it.remoteUniqueId == null || documents[it.messageId]?.uniqueFileId == it.remoteUniqueId }) {
            "A part of the current Telegram version changed."
        }

        // A recent-page lookup deduplicates retries without requiring a full scan of the drive.
        val saved = client.fetchDocuments(drive, 0, 100).documents
            .mapNotNull(::unpack).filter { it.revision == primary && it.manifest.fileId == entity.id }
            .mapTo(mutableSetOf()) { it.manifest.partIndex }
        // Publish the first part last so an interrupted multipart snapshot is never presented as complete.
        for (id in ids.asReversed()) {
            val doc = documents.getValue(id)
            val old = codec.decode(doc.caption) ?: run {
                check(!entity.isEncrypted && oldParts.isEmpty()) { "The current version cannot be decoded." }
                RemoteFileManifest(
                    fileId = entity.id, name = doc.fileName.ifBlank { entity.name }, folderPath = "",
                    mimeType = doc.mimeType.ifBlank { entity.mimeType }, sizeBytes = doc.sizeBytes,
                    createdAt = doc.dateSeconds * 1000L, modifiedAt = doc.dateSeconds * 1000L
                )
            }
            check(old.fileId == entity.id)
            if (old.partIndex in saved) continue
            val manifest = old.copy(folderPath = "", folderId = null, iconFileId = null, tags = emptyList())
            client.copyDocument(drive, doc.remoteFileId, doc.fileName, doc.mimeType,
                PREFIX + primary + ":" + codec.encode(manifest, manifest.encrypted))
        }
        return ids
    }

    suspend fun list(fileId: String): List<Revision> = withContext(Dispatchers.IO) {
        val file = readable(fileId)
        archives(file).map { (id, group) ->
            val manifest = group.first().manifest
            Revision(id, manifest.modifiedAt, manifest.sizeBytes)
        }.sortedByDescending(Revision::modifiedAt)
    }

    suspend fun replace(fileId: String, reference: String) = withContext(Dispatchers.IO) { staging.withLock {
        val original = readable(fileId)
        val source = importer.prepare(reference) { ensureActive() } ?: error("The file is no longer available.")
        commit(original, source)
    } }

    suspend fun restore(fileId: String, revision: Long) = withContext(Dispatchers.IO) { staging.withLock {
        val original = readable(fileId)
        val items = archives(original)[revision] ?: error("This revision is no longer available.")
        val first = items.first()
        val metadata = first.manifest
        val chat = first.document.chatId
        val revisionParts = items.takeIf { metadata.isPart }.orEmpty().map { item ->
            FilePartEntity(fileId, item.manifest.partIndex, chat, item.document.messageId,
                item.document.remoteFileId, item.document.uniqueFileId, item.manifest.partOffset,
                item.manifest.partSize, item.document.sizeBytes, item.document.dateSeconds * 1000L)
        }
        val old = original.copy(
            sizeBytes = metadata.sizeBytes, chatId = chat, messageId = first.document.messageId,
            remoteFileId = first.document.remoteFileId, remoteUniqueId = first.document.uniqueFileId,
            isEncrypted = metadata.encrypted, partCount = revisionParts.size
        )
        val dir = File(storage.filesDir, "imports/" + UUID.randomUUID()).apply { mkdirs() }
        val temporary = File(dir, FileNameUtils.sanitize(metadata.name))
        importer.reserve(temporary.path)
        try {
            val hash = temporary.outputStream().buffered().use {
                reader.copyRemote(old, it, revisionParts) { check(readable(fileId).chatId == chat) }
            }
            check(metadata.contentHash == null || hash.equals(metadata.contentHash, true))
            commit(original, com.infinity.drive.core.files.ImportedFile(temporary.path, metadata.name, temporary.length()))
        } finally {
            importer.registered(temporary.path)
            if (files.byId(fileId)?.localPath != temporary.path) {
                temporary.delete()
                dir.delete()
            }
        }
    } }

    private suspend fun commit(original: FileEntity, source: com.infinity.drive.core.files.ImportedFile) {
        try {
            val current = readable(original.id)
            check(current.modifiedAt == original.modifiedAt && current.messageId == original.messageId) {
                "The current file changed. Open it again before replacing it."
            }
            check(source.sizeBytes > 0 && transferRows.unfinishedIdsForFiles(listOf(original.id)).isEmpty()) {
                "Wait for pending transfers before replacing this file."
            }
            check(com.infinity.drive.core.files.FileNameUtils.extensionOf(source.name)
                .equals(com.infinity.drive.core.files.FileNameUtils.extensionOf(current.name), true)) {
                "Choose the same file type as the current version."
            }
            files.upsert(current.copy(localPath = source.path, sizeBytes = source.sizeBytes,
                modifiedAt = maxOf(System.currentTimeMillis(), current.modifiedAt + 1), contentHash = null, importReference = null,
                width = null, height = null, durationMs = null, backupState = BackupState.NONE, uploadRequested = true))
            importer.registered(source.path)
            check(transfers.enqueueUpload(original.id) is AppResult.Success) {
                "The new revision was saved locally. Retry the upload from Activity."
            }
        } finally {
            if (files.byId(original.id)?.localPath != source.path) importer.discard(source)
        }
    }

    private suspend fun readable(id: String): FileEntity {
        val file = files.byId(id) ?: error("This file is no longer available.")
        check(file.chatId != null && active.id() == file.chatId && file.trashedAt == null && access.canRead(id))
        return file
    }

    private suspend fun archives(file: FileEntity): Map<Long, List<Archived>> {
        val chat = requireNotNull(file.chatId)
        val result = mutableListOf<Archived>()
        var cursor = 0L
        repeat(10_000) {
            check(readable(file.id).chatId == chat)
            val page = client.fetchDocuments(chat, cursor, 100)
            result.addAll(page.documents.asSequence().mapNotNull(::unpack).filter { it.manifest.fileId == file.id })
            if (page.nextFromMessageId == 0L) return result.groupBy(Archived::revision).mapNotNull { (id, items) ->
                val group = items.distinctBy { it.manifest.partIndex }.sortedBy { it.manifest.partIndex }
                val first = group.first().manifest
                val count = first.partCount.coerceAtLeast(1)
                (id to group).takeIf {
                    group.size == count && group.withIndex().all { (index, item) ->
                        item.manifest.partIndex == index && item.manifest.sizeBytes == first.sizeBytes &&
                            item.manifest.encrypted == first.encrypted && item.manifest.partCount.coerceAtLeast(1) == count
                    }
                }
            }.toMap()
            check(page.nextFromMessageId != cursor) { "Telegram did not advance the history scan." }
            cursor = page.nextFromMessageId
        }
        error("The Telegram history scan limit was reached.")
    }

    private fun unpack(doc: RemoteDocument): Archived? {
        if (!doc.caption.startsWith(PREFIX)) return null
        val envelope = doc.caption.removePrefix(PREFIX)
        val revision = envelope.substringBefore(':').toLongOrNull() ?: return null
        val manifest = codec.decode(envelope.substringAfter(':', "")) ?: return null
        return Archived(revision, manifest, doc)
    }

    companion object {
        const val PREFIX = "tdh1:"
    }
}
