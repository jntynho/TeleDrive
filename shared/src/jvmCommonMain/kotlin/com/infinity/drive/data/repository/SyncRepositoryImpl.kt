package com.infinity.drive.data.repository

import com.infinity.drive.data.local.database.inImmediateTransaction
import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.common.SafeLog
import com.infinity.drive.core.publish.PublishScheduler
import com.infinity.drive.core.crypto.KeyBackupCodec
import com.infinity.drive.core.files.MimeTypes
import com.infinity.drive.core.telegram.RemoteDocument
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramException
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.dao.FilePartDao
import com.infinity.drive.data.local.dao.FolderDao
import com.infinity.drive.data.local.dao.PendingDeleteDao
import com.infinity.drive.data.local.database.DriveInfinityDatabase
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.local.entity.FilePartEntity
import com.infinity.drive.data.remote.telegram.ManifestCodec
import com.infinity.drive.data.remote.telegram.RemoteFileManifest
import com.infinity.drive.data.remote.telegram.RemoteFolderState
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.model.FileTags
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.domain.repository.SyncRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

internal fun isAdvancingHistory(cursor: Long, next: Long): Boolean =
    next == 0L || next > 0 && (cursor == 0L || next < cursor)

/**
 * Reconciles local metadata with the storage chat. The chat is the source of
 * truth for remote-backed files: caption manifests are decoded and upserted,
 * and local rows pointing at deleted messages are detached. After a complete
 * local wipe this rebuilds the whole drive, including the folder tree.
 */
class SyncRepositoryImpl(
    private val telegramClient: TelegramClient,
    private val fileDao: FileDao,
    private val manifestCodec: ManifestCodec,
    private val pendingDeleteDao: PendingDeleteDao,
    private val filePartDao: FilePartDao,
    private val folderDao: FolderDao,
    private val folderPathResolver: FolderPathResolver,
    private val activeChannel: ActiveChannel,
    private val channelOwnership: ChannelOwnership,
    private val folderStateSynchronizer: FolderStateSynchronizer,
    private val publishScheduler: PublishScheduler,
    private val settingsRepository: SettingsRepository,
    private val database: DriveInfinityDatabase
) : SyncRepository {

    private val _syncing = MutableStateFlow(false)
    override val syncing: Flow<Boolean> = _syncing

    private val syncMutex = Mutex()

    override suspend fun fullResync(): AppResult<SyncRepository.SyncStats> =
        runSync(incremental = false)

    override suspend fun incrementalSync(): AppResult<SyncRepository.SyncStats> =
        runSync(incremental = true)

    override suspend fun syncOnStart(): AppResult<SyncRepository.SyncStats> {
        fileDao.repairBackedUpStates()
        return runSync(incremental = fileDao.fileCount(activeChannel.id()) > 0)
    }

    private suspend fun runSync(incremental: Boolean): AppResult<SyncRepository.SyncStats> =
        syncMutex.withLock {
            _syncing.value = true
            try {
                doSync(incremental)
            } catch (e: TelegramException) {
                AppResult.Failure(
                    if (e.isRateLimit) AppError.RateLimited(e.retryAfterSeconds ?: 0)
                    else AppError.TelegramError(e.code, e.message)
                )
            } finally {
                _syncing.value = false
            }
        }

    private suspend fun forgetChat(chatId: Long) {
        val removed = fileDao.deleteRemoteOnlyInChat(chatId)
        val detached = fileDao.detachChat(chatId)
        SafeLog.d(TAG, "Storage chat changed: dropped $removed, detached $detached")
    }

    private suspend fun doSync(incremental: Boolean): AppResult<SyncRepository.SyncStats> {
        val prefs = settingsRepository.preferences.first()
        val chatId = telegramClient.ensureStorageChat(prefs.storageChatId)
        channelOwnership.claimUnowned(chatId)
        if (chatId != prefs.storageChatId) {
            prefs.storageChatId?.let { previous -> forgetChat(previous) }
            settingsRepository.update { it.copy(storageChatId = chatId) }
        }

        if (!incremental) {
            if (folderDao.pendingPublishCount() > 0) publishScheduler.kick()
            runCatching { folderStateSynchronizer.pull() }
                .onFailure { SafeLog.w(TAG, "Folder state pull failed", it) }
        }

        val pendingDeletes = pendingDeleteDao.messageIdsIn(chatId).toSet()
        val folderCache = mutableMapOf<String, String?>()
        SafeLog.d(TAG, "Sync start chat=$chatId incremental=$incremental")
        var inserted = 0
        var updated = 0
        var locked = 0
        val seenMessageIds = mutableSetOf<Long>()
        var fromMessageId = 0L
        var pages = 0
        var exhausted = false

        while (pages++ < MAX_PAGES) {
            val page = try {
                telegramClient.fetchDocuments(chatId, fromMessageId, PAGE_SIZE)
            } catch (e: TelegramException) {
                if (e.isRateLimit) {
                    delay((((e.retryAfterSeconds ?: 5) + 1) * 1000L).milliseconds)
                    continue
                }
                throw e
            }
            SafeLog.d(TAG, "Sync page ${page.documents.size} documents")
            if (!isAdvancingHistory(fromMessageId, page.nextFromMessageId)) {
                return AppResult.Failure(AppError.Timeout())
            }

            var reachedKnown = false
            val known = existingForPage(page.documents)
            database.inImmediateTransaction {
                for (document in page.documents) {
                    seenMessageIds.add(document.messageId)
                    // Still in the chat, but a delete is owed for it.
                    if (document.messageId in pendingDeletes) continue
                    if (isInternalDocument(document)) continue
                    if (manifestCodec.isLocked(document.caption)) {
                        locked++
                        continue
                    }
                    if (incremental && !isNewToLocal(document, known)) {
                        reachedKnown = true
                        break
                    }
                    when (reconcile(document, folderCache, known)) {
                        ReconcileResult.INSERTED -> {
                            inserted++
                        }

                        ReconcileResult.UPDATED -> updated++
                        ReconcileResult.UNCHANGED -> Unit
                    }
                }
            }
            if (reachedKnown) {
                return finishSync(
                    chatId, seenMessageIds, inserted, updated, locked, partial = true
                )
            }
            if (page.nextFromMessageId == 0L) {
                exhausted = true
                break
            }
            fromMessageId = page.nextFromMessageId
        }

        if (!exhausted) return AppResult.Failure(AppError.Timeout())
        return finishSync(
            chatId, seenMessageIds, inserted, updated, locked, partial = incremental
        )
    }

    private suspend fun finishSync(
        chatId: Long,
        seenMessageIds: Set<Long>,
        inserted: Int,
        updated: Int,
        locked: Int,
        partial: Boolean
    ): AppResult<SyncRepository.SyncStats> {
        var detached = 0
        // Locked files still count as seen so a missing local key cannot mark them as detached.
        if (!partial) {
            val stale = fileDao.filesWithRemote().filter { entity ->
                val messageId = entity.messageId
                messageId != null && entity.chatId == chatId && messageId !in seenMessageIds
            }
            if (stale.isNotEmpty()) {
                database.inImmediateTransaction {
                    val (orphaned, localOnly) = stale.partition { it.localPath == null }
                    if (orphaned.isNotEmpty()) {
                        fileDao.deleteByIds(orphaned.map { it.id })
                    }
                    localOnly.forEach { entity -> fileDao.detachRemote(entity.id) }
                }
                detached = stale.size
            }
        }
        SafeLog.d(TAG, "Sync done: +$inserted ~$updated -$detached, $locked locked")
        return AppResult.Success(
            SyncRepository.SyncStats(inserted, updated, detached, locked)
        )
    }

    /** App-managed bookkeeping documents must never surface as user files. */
    private fun isInternalDocument(document: RemoteDocument): Boolean =
        document.fileName == RemoteFolderState.FILE_NAME ||
                document.fileName == KeyBackupCodec.BACKUP_FILE_NAME ||
                document.caption.startsWith(RemoteFolderState.MARKER) ||
                document.caption.startsWith(KEY_BACKUP_MARKER) ||
                document.caption.startsWith(ICON_MARKER) ||
                document.caption.startsWith(TelegramFileVersions.PREFIX)

    private fun isNewToLocal(document: RemoteDocument, known: KnownRows): Boolean {
        val existing = known.byUniqueId[document.uniqueFileId] ?: return true
        return existing.messageId != document.messageId
    }

    private suspend fun existingForPage(documents: List<RemoteDocument>): KnownRows {
        val manifests = documents.associateWith { manifestCodec.decode(it.caption) }
        val fileIds = manifests.values.mapNotNull { it?.fileId }.distinct()
        val uniqueIds = documents.map { it.uniqueFileId }.distinct()
        val byId = if (fileIds.isEmpty()) {
            emptyMap()
        } else {
            fileDao.byIds(fileIds).associateBy { it.id }
        }
        val byUniqueId = if (uniqueIds.isEmpty()) {
            emptyMap()
        } else {
            fileDao.byRemoteUniqueIds(uniqueIds).mapNotNull { entity ->
                entity.remoteUniqueId?.let { it to entity }
            }.toMap()
        }
        return KnownRows(manifests, byId, byUniqueId)
    }

    private data class KnownRows(
        val manifests: Map<RemoteDocument, RemoteFileManifest?>,
        val byId: Map<String, FileEntity>,
        val byUniqueId: Map<String, FileEntity>
    )

    /**
     * Folder identity comes from the manifest id when it is known locally.
     * Paths are only a fallback, because two folders can share a name, and a
     * trashed file must never recreate the folder it was deleted from.
     */
    private suspend fun resolveFolder(
        manifest: RemoteFileManifest,
        cache: MutableMap<String, String?>
    ): String? {
        manifest.folderId?.let { id ->
            if (cache.containsValue(id)) return id
            if (folderPathResolver.exists(id)) {
                cache[manifest.folderPath] = id
                return id
            }
        }
        if (cache.containsKey(manifest.folderPath)) return cache[manifest.folderPath]
        val resolved = if (manifest.trashedAt != null) {
            folderPathResolver.resolveExisting(manifest.folderPath)
        } else {
            folderPathResolver.resolveOrCreate(manifest.folderPath, manifest.folderId)
        }
        cache[manifest.folderPath] = resolved
        return resolved
    }

    private suspend fun reconcile(
        incoming: RemoteDocument,
        folderCache: MutableMap<String, String?>,
        known: KnownRows
    ): ReconcileResult {
        var document = incoming
        var manifest = known.manifests[document]
        val existing = manifest?.let { fileDao.byId(it.fileId) }
            ?: known.byUniqueId[document.uniqueFileId]
        if (manifest != null && existing != null && existing.chatId == document.chatId &&
            (existing.modifiedAt > manifest.modifiedAt ||
                !manifest.isPart && existing.modifiedAt == manifest.modifiedAt &&
                    (existing.messageId ?: 0L) > document.messageId)
        ) return ReconcileResult.UNCHANGED

        val remote = manifest
        if (remote?.isPart == true && remote.replaces &&
            (existing?.messageId == null || remote.modifiedAt > existing.modifiedAt)
        ) {
            val key = "revision:${document.chatId}:${remote.fileId}:${remote.modifiedAt}"
            recordPart(document, remote, key)
            val staged = filePartDao.partsOf(key)
            if (staged.size != remote.partCount || staged.withIndex().any { (index, part) ->
                part.partIndex != index || part.chatId != document.chatId || part.messageId == null || part.remoteFileId == null
            }) return ReconcileResult.UNCHANGED
            var offset = 0L
            for (part in staged) {
                if (part.plainOffset != offset || part.plainSize !in 1..(remote.sizeBytes - offset)) {
                    return ReconcileResult.UNCHANGED
                }
                offset += part.plainSize
            }
            if (offset != remote.sizeBytes) return ReconcileResult.UNCHANGED
            filePartDao.deleteFor(listOf(remote.fileId, key))
            staged.forEach { filePartDao.upsert(it.copy(fileId = remote.fileId)) }
            val first = staged.first()
            document = document.copy(messageId = requireNotNull(first.messageId),
                remoteFileId = requireNotNull(first.remoteFileId), uniqueFileId = first.remoteUniqueId.orEmpty(),
                sizeBytes = first.storedSize)
            manifest = remote.copy(partIndex = 0, partOffset = 0, partSize = first.plainSize)
        } else if (remote?.isPart == true) {
            recordPart(document, remote)
        } else if (remote != null && existing?.partCount?.let { it > 0 } == true) {
            filePartDao.deleteFor(listOf(existing.id))
        }

        val installed = manifest
        val local = existing?.takeIf {
            it.pendingPublish && (installed == null || it.modifiedAt >= installed.modifiedAt)
        }

        val remoteFolderId = manifest?.let { resolveFolder(it, folderCache) } ?: existing?.folderId
        val folderId = if (local != null) {
            local.folderId ?: local.preTrashFolderId
        } else {
            remoteFolderId
        }
        val folderTrashedAt = folderId?.let { folderDao.byId(it)?.trashedAt }

        val name = local?.name ?: manifest?.name ?: existing?.name
        ?: document.fileName.ifBlank { "file-${document.messageId}" }
        val mime = manifest?.mimeType ?: document.mimeType.ifBlank {
            MimeTypes.fromFileName(name)
        }

        val now = System.currentTimeMillis()
        val trashedAt = if (local != null) {
            local.trashedAt
        } else {
            manifest?.trashedAt ?: folderTrashedAt ?: existing?.trashedAt
        }
        val entity = FileEntity(
            id = manifest?.fileId ?: existing?.id ?: UUID.randomUUID().toString(),
            folderId = if (trashedAt != null) null else folderId,
            name = name,
            sizeBytes = manifest?.sizeBytes ?: document.sizeBytes,
            mimeType = mime,
            category = FileCategory.fromMimeType(mime),
            localPath = existing?.localPath?.takeUnless {
                installed?.replaces == true && installed.modifiedAt > existing.modifiedAt
            },
            contentHash = manifest?.contentHash ?: existing?.contentHash,
            chatId = document.chatId,
            messageId = document.messageId,
            remoteFileId = document.remoteFileId,
            remoteUniqueId = document.uniqueFileId,
            backupState = BackupState.BACKED_UP,
            isHidden = local?.isHidden ?: manifest?.hidden ?: existing?.isHidden ?: false,
            isArchived = local?.isArchived ?: manifest?.archived ?: existing?.isArchived ?: false,
            isFavorite = local?.isFavorite ?: manifest?.favorite ?: existing?.isFavorite ?: false,
            isEncrypted = manifest?.encrypted
                ?: manifestCodec.isEncryptedManifest(document.caption),
            width = manifest?.width ?: existing?.width,
            height = manifest?.height ?: existing?.height,
            durationMs = manifest?.durationMs ?: existing?.durationMs,
            trashedAt = trashedAt,
            preTrashFolderId = if (trashedAt != null) {
                folderId ?: existing?.preTrashFolderId
            } else {
                existing?.preTrashFolderId
            },
            pendingPublish = existing?.pendingPublish == true,
            createdAt = manifest?.createdAt ?: existing?.createdAt
            ?: (document.dateSeconds * 1000L),
            modifiedAt = local?.modifiedAt ?: manifest?.modifiedAt ?: existing?.modifiedAt
            ?: (document.dateSeconds * 1000L),
            addedAt = existing?.addedAt ?: now,
            partCount = manifest?.partCount ?: existing?.partCount ?: 0,
            tagsEncoded = local?.tagsEncoded ?: manifest?.let {
                FileTags.encode(FileTags.normalize(it.tags).orEmpty())
            } ?: existing?.tagsEncoded.orEmpty(),
            iconFileId = manifest?.iconFileId ?: existing?.iconFileId
        )

        if (manifest != null && manifest.isPart && manifest.partIndex != 0) {
            return if (existing == null) {
                fileDao.upsert(
                    entity.copy(
                        chatId = null,
                        messageId = null,
                        remoteFileId = null,
                        remoteUniqueId = null
                    )
                )
                ReconcileResult.INSERTED
            } else {
                ReconcileResult.UNCHANGED
            }
        }

        return when {
            existing == null -> {
                fileDao.upsert(entity)
                ReconcileResult.INSERTED
            }

            existing.id != entity.id -> {
                fileDao.deleteByIds(listOf(existing.id))
                fileDao.upsert(entity)
                ReconcileResult.UPDATED
            }

            existing != entity -> {
                fileDao.upsert(entity)
                ReconcileResult.UPDATED
            }

            else -> ReconcileResult.UNCHANGED
        }
    }

    private suspend fun recordPart(document: RemoteDocument, manifest: RemoteFileManifest, fileId: String = manifest.fileId) {
        filePartDao.upsert(
            FilePartEntity(
                fileId = fileId,
                partIndex = manifest.partIndex,
                chatId = document.chatId,
                messageId = document.messageId,
                remoteFileId = document.remoteFileId,
                remoteUniqueId = document.uniqueFileId,
                plainOffset = manifest.partOffset,
                plainSize = manifest.partSize,
                storedSize = document.sizeBytes,
                uploadedAt = document.dateSeconds * 1000L
            )
        )
    }

    private enum class ReconcileResult { INSERTED, UPDATED, UNCHANGED }

    companion object {
        private const val TAG = "SyncRepository"
        private const val KEY_BACKUP_MARKER = "#driveinfinity-keybackup"
        private const val ICON_MARKER = "#driveinfinity-icon"
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 2000
    }
}
