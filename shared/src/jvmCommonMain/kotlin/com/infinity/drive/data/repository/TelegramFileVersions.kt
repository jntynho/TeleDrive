package com.infinity.drive.data.repository

import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.files.FileImporter
import com.infinity.drive.core.files.FileNameUtils
import com.infinity.drive.core.files.ImportedFile
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.core.telegram.RemoteDocument
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.dao.FilePartDao
import com.infinity.drive.data.local.dao.TransferDao
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.remote.telegram.ManifestCodec
import com.infinity.drive.data.remote.telegram.RemoteFileManifest
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.repository.TransferRepository
import com.infinity.drive.core.common.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.UUID

class TelegramFileVersions(
    private val client: TelegramClient,
    private val codec: ManifestCodec,
    private val files: FileDao,
    private val parts: FilePartDao,
    private val active: ActiveChannel,
    private val access: HiddenContentAccess,
    private val importer: FileImporter,
    private val transfers: TransferRepository,
    private val transferRows: TransferDao,
    private val reader: DriveContentReader,
    private val storage: AppStoragePaths
) {
    private data class Archived(val revision: Long, val manifest: RemoteFileManifest)

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

    suspend fun replace(reviewed: FileEntity, reference: String, expectedContentHash: String) =
        withContext(Dispatchers.IO) {
            staging.withLock {
                requireCurrentRevision(reviewed)
                val source = prepareVerified(reference, expectedContentHash)
                commit(reviewed, source)
            }
        }

    /** Verifies a private copy of the reviewed bytes on the caller's I/O context. */
    suspend fun prepareVerified(reference: String, expectedContentHash: String): ImportedFile {
        val context = kotlinx.coroutines.currentCoroutineContext()
        val source = importer.prepare(reference) { context.ensureActive() }
            ?: error("The selected file is unavailable.")
        val snapshot = if (importer.isStaged(source.path)) null else File(
            storage.filesDir, "imports/${UUID.randomUUID()}/${FileNameUtils.sanitize(source.name)}"
        )
        val staged = if (snapshot == null) source else source.copy(path = snapshot.absolutePath)
        try {
            val input = File(source.path)
            val hash = if (snapshot == null) {
                reader.copyLocal(input, source.sizeBytes, discardOutput)
            } else {
                importer.reserve(snapshot.absolutePath)
                check(requireNotNull(snapshot.parentFile).mkdirs())
                snapshot.outputStream().buffered().use { reader.copyLocal(input, source.sizeBytes, it) }
            }
            check(hash.equals(expectedContentHash, ignoreCase = true)) {
                "The selected file changed since the sync review."
            }
            return staged
        } catch (error: Throwable) {
            importer.discard(staged)
            throw error
        }
    }

    private suspend fun commit(original: FileEntity, source: ImportedFile) {
        try {
            val current = requireCurrentRevision(original)
            check(source.sizeBytes > 0 && transferRows.unfinishedIdsForFiles(listOf(original.id)).isEmpty()) {
                "Wait for pending transfers before replacing this file."
            }
            check(FileNameUtils.extensionOf(source.name)
                .equals(FileNameUtils.extensionOf(current.name), true)) {
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
            withContext(NonCancellable) {
                if (files.byId(original.id)?.localPath != source.path) importer.discard(source)
            }
        }
    }

    private suspend fun requireCurrentRevision(reviewed: FileEntity): FileEntity =
        readable(reviewed.id).also { current ->
            check(current.chatId == reviewed.chatId && current.modifiedAt == reviewed.modifiedAt &&
                current.messageId == reviewed.messageId && current.backupState == BackupState.BACKED_UP) {
                "The current file changed. Open it again before replacing it."
            }
        }

    private suspend fun readable(id: String): FileEntity {
        val file = files.byId(id) ?: error("This file is no longer available.")
        check(file.chatId != null && active.id() == file.chatId && file.trashedAt == null && access.canRead(id))
        return file
    }

    private fun unpack(doc: RemoteDocument): Archived? {
        if (!doc.caption.startsWith(PREFIX)) return null
        val envelope = doc.caption.removePrefix(PREFIX)
        val revision = envelope.substringBefore(':').toLongOrNull() ?: return null
        val manifest = codec.decode(envelope.substringAfter(':', "")) ?: return null
        return Archived(revision, manifest)
    }

    companion object {
        const val PREFIX = "tdh1:"
        private val discardOutput = object : OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
        }
    }
}
