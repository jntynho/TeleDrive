package com.infinity.drive.data.repository

import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.common.SafeLog
import com.infinity.drive.core.dispatchers.DispatcherProvider
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.files.Hashing
import com.infinity.drive.core.files.MimeTypes
import com.infinity.drive.core.media.MediaMetadataExtractor
import com.infinity.drive.core.transfer.BackupSessionTracker
import com.infinity.drive.data.local.dao.BackupDao
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.dao.TransferDao
import com.infinity.drive.data.local.entity.BackupRecordEntity
import com.infinity.drive.data.local.entity.BackupSessionEntity
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.local.entity.TransferEntity
import com.infinity.drive.data.mapper.toDomain
import com.infinity.drive.domain.model.BackupDecision
import com.infinity.drive.domain.model.BackupSession
import com.infinity.drive.domain.model.BackupSessionStatus
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.BackupTrigger
import com.infinity.drive.domain.model.ExclusionType
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.model.TransferState
import com.infinity.drive.domain.repository.BackupRepository
import com.infinity.drive.domain.repository.ChannelRepository
import com.infinity.drive.domain.repository.ExclusionRepository
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.domain.usecase.DecideBackupActionUseCase
import com.infinity.drive.domain.usecase.EvaluateExclusionsUseCase
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.coroutines.coroutineContext

class BackupRepositoryImpl(
    private val backupDao: BackupDao,
    private val fileDao: FileDao,
    private val transferDao: TransferDao,
    private val transferRepository: TransferRepositoryImpl,
    private val backupSessionTracker: BackupSessionTracker,
    private val exclusionRepository: ExclusionRepository,
    private val settingsRepository: SettingsRepository,
    private val decideBackupAction: DecideBackupActionUseCase,
    private val mediaMetadataExtractor: MediaMetadataExtractor,
    private val folderPathResolver: FolderPathResolver,
    private val fileRepository: FileRepository,
    private val channelRepository: ChannelRepository,
    private val storagePaths: AppStoragePaths,
    private val dispatchers: DispatcherProvider
) : BackupRepository {

    override fun observeActiveSession(): Flow<BackupSession?> =
        backupDao.observeActiveSession().map { it?.toDomain() }

    override fun observeLastBackupAt(): Flow<Long?> = backupDao.observeLastBackupAt()

    private val startMutex = Mutex()

    override suspend fun startBackup(trigger: BackupTrigger): AppResult<String?> =
        withContext(dispatchers.io) {
            startMutex.withLock { runBackup(trigger) }
        }

    override suspend fun previewBackup(): AppResult<BackupRepository.BackupPreview> =
        withContext(dispatchers.io) {
            when (val result = scanBackup(applyLinks = false)) {
                is AppResult.Success -> AppResult.Success(result.value.preview)
                is AppResult.Failure -> result
            }
        }

    private data class BackupScan(
        val chatId: Long,
        val uploads: List<File>,
        val preview: BackupRepository.BackupPreview
    )

    private suspend fun runBackup(trigger: BackupTrigger): AppResult<String?> {
        if (backupDao.activeSession() != null) {
            return AppResult.Failure(AppError.BackupAlreadyRunning)
        }
        val scan = when (val result = scanBackup(applyLinks = true)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        if (scan.uploads.isEmpty()) return AppResult.Success(null)
        if (settingsRepository.preferences.first().storageChatId != scan.chatId) {
            return AppResult.Failure(AppError.NotFound)
        }

        val session = BackupSessionEntity(
            id = UUID.randomUUID().toString(),
            trigger = trigger,
            status = BackupSessionStatus.RUNNING,
            totalFiles = scan.preview.uploads,
            skippedFiles = scan.preview.skips.values.sum(),
            totalBytes = scan.preview.uploadBytes,
            startedAt = System.currentTimeMillis()
        )
        backupDao.upsertSession(session)
        for (batch in scan.uploads.chunked(ENQUEUE_BATCH)) {
            val fileIds = batch.map { registerFile(it, backupFolderIdFor(it), scan.chatId) }
            transferRepository.enqueueBackupBatch(fileIds, session.id)
        }
        return AppResult.Success(session.id)
    }

    private suspend fun scanBackup(applyLinks: Boolean): AppResult<BackupScan> {
        val prefs = settingsRepository.preferences.first()
        val chatId = prefs.storageChatId ?: return AppResult.Failure(AppError.NotFound)
        val folders = channelRepository.backupFolders(chatId, migrateLegacy = applyLinks)
        if (folders.isEmpty()) {
            return AppResult.Failure(
                AppError.UnsupportedOperation("No backup folders selected. Pick folders in Settings.")
            )
        }

        if (applyLinks) backupDao.deleteOrphanedRecords()
        val exclusions = exclusionRepository.getEnabled()
        val maxSizeBytes = prefs.backupMaxFileSizeMb.toLong() * 1024 * 1024
        val minSizeBytes = prefs.backupMinFileSizeMb.toLong() * 1024 * 1024
        val skipHidden = exclusions.any { it.type == ExclusionType.HIDDEN }
        val candidates = linkedMapOf<String, File>()
        var unreadable = 0
        for (folderPath in folders) {
            val root = File(folderPath)
            if (!root.isDirectory) {
                unreadable++
                continue
            }
            val context = coroutineContext
            root.walkTopDown()
                .onEnter { dir ->
                    context.ensureActive()
                    dir == root || (!Files.isSymbolicLink(dir.toPath()) && (!skipHidden || !isHiddenName(dir)))
                }
                .onFail { _, _ -> unreadable++ }
                .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) && it.length() > 0 }
                .forEach {
                    context.ensureActive()
                    candidates.putIfAbsent(it.absolutePath, it)
                }
        }

        if (applyLinks && candidates.isEmpty() && (unreadable > 0 || storageUnreadable())) {
            return AppResult.Failure(AppError.BackupFoldersUnreadable)
        }

        var totalBytes = 0L
        val reasons = mutableMapOf<BackupDecision, Int>()
        val toBackup = mutableListOf<File>()
        for (candidate in candidates.values) {
            coroutineContext.ensureActive()
            val size = candidate.length()
            val sizeSkip = when {
                minSizeBytes > 0 && size < minSizeBytes -> BackupDecision.SKIP_TOO_SMALL
                maxSizeBytes > 0 && size > maxSizeBytes -> BackupDecision.SKIP_TOO_LARGE
                else -> null
            }
            val decision = if (sizeSkip != null) sizeSkip else {
                val record = backupDao.recordForRemoteFile(candidate.absolutePath, chatId)
                if (applyLinks && record == null && settingsRepository.preferences.first().storageChatId != chatId) {
                    return AppResult.Failure(AppError.NotFound)
                }
                if (record == null && hasReusableCopy(candidate, chatId, applyLinks)) {
                    BackupDecision.SKIP_DUPLICATE
                } else decideBackupAction(
                    candidate = EvaluateExclusionsUseCase.Candidate(
                        absolutePath = candidate.absolutePath,
                        sizeBytes = size,
                        mimeType = MimeTypes.fromFileName(candidate.name),
                        isHidden = isHiddenName(candidate)
                    ),
                    modifiedAt = candidate.lastModified(),
                    existingRecord = record?.let {
                        DecideBackupActionUseCase.ExistingRecord(it.sizeBytes, it.modifiedAt, it.contentHash)
                    },
                    exclusions = exclusions,
                    maxFileSizeBytes = maxSizeBytes,
                    minFileSizeBytes = minSizeBytes,
                    contentHashProvider = { Hashing.sha256(candidate) }
                )
            }
            if (decision == BackupDecision.BACKUP) {
                toBackup.add(candidate)
                totalBytes = totalBytes.coerceAtMost(Long.MAX_VALUE - size) + size
            } else {
                reasons[decision] = (reasons[decision] ?: 0) + 1
            }
        }

        val preview = BackupRepository.BackupPreview(toBackup.size, totalBytes, reasons, unreadable)
        return AppResult.Success(BackupScan(chatId, toBackup, preview))
    }

    private suspend fun hasReusableCopy(candidate: File, chatId: Long, applyLinks: Boolean): Boolean {
        val existing = linkedUpload(candidate, chatId)
        if (existing != null) {
            if (applyLinks) recordBackedUp(candidate, existing.id, existing.contentHash)
            return true
        }
        if (applyLinks) {
            if (reviveTrashedUpload(candidate)) return true
        } else {
            val trashed = fileDao.trashedMatches(candidate.name, candidate.length(), chatId)
            if (trashed.isNotEmpty()) {
                val hash = Hashing.sha256(candidate)
                if (hash != null && trashed.firstOrNull { it.contentHash == hash }?.messageId != null) return true
            }
        }
        return adoptUploadedCopy(candidate, chatId, applyLinks)
    }

    /**
     * A file uploaded by hand already carries its remote mapping, so a folder
     * added to backup later must claim it instead of uploading a second copy.
     */
    private suspend fun linkedUpload(candidate: File, chatId: Long): FileEntity? =
        fileDao.byLocalPath(candidate.absolutePath)?.takeIf {
            it.chatId == chatId && it.messageId != null && it.backupState == BackupState.BACKED_UP
        }

    /**
     * A file deleted inside the app but still on disk is restored rather than
     * uploaded again, so the drive keeps one copy with its original history.
     */
    private suspend fun reviveTrashedUpload(candidate: File): Boolean {
        val revived = fileRepository.reviveTrashedCopy(
            localPath = candidate.absolutePath,
            folderId = backupFolderIdFor(candidate)
        ) ?: return false
        if (!revived.hasRemoteCopy) return false
        recordBackedUp(candidate, revived.id, revived.contentHash)
        SafeLog.d(TAG, "Restored a trashed file instead of uploading a copy")
        return true
    }

    /**
     * After a reinstall the drive is rebuilt from Telegram, so uploaded files
     * have no path on this device. Matching them back to the identical local
     * file avoids uploading a second copy and keeps the local copy reclaimable.
     */
    private suspend fun adoptUploadedCopy(candidate: File, chatId: Long, applyLinks: Boolean): Boolean {
        val matches = fileDao.unlinkedRemoteMatches(candidate.name, candidate.length(), chatId)
        if (matches.isEmpty()) return false

        val localHash = Hashing.sha256(candidate)
        val match = matches.firstOrNull { it.contentHash != null && it.contentHash == localHash }
            ?: matches.singleOrNull()?.takeIf { it.contentHash == null }
            ?: return false

        if (applyLinks) {
            fileDao.setLocalPath(match.id, candidate.absolutePath)
            recordBackedUp(candidate, match.id, localHash)
            SafeLog.d(TAG, "Linked an existing upload to its local copy")
        }
        return true
    }

    private suspend fun recordBackedUp(candidate: File, fileId: String, contentHash: String?) {
        backupDao.upsertRecord(
            BackupRecordEntity(
                id = UUID.randomUUID().toString(),
                sourcePath = candidate.absolutePath,
                fileId = fileId,
                sizeBytes = candidate.length(),
                modifiedAt = candidate.lastModified(),
                contentHash = contentHash,
                backedUpAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Mirrors the device folder structure at the drive root, so backed-up
     * files share the same tree as manual uploads instead of a separate silo.
     */
    private suspend fun backupFolderIdFor(source: File): String? {
        val parent = source.parentFile?.absolutePath ?: return null
        val relative = relativeToStorageRoot(parent)
        return if (relative.isEmpty()) null else folderPathResolver.resolveOrCreate(relative)
    }

    /** Strips the primary or removable volume mount point from [absolutePath]. */
    private fun relativeToStorageRoot(absolutePath: String): String {
        val normalized = absolutePath.trimEnd('/')
        val primary = storagePaths.externalStorageRoot?.absolutePath?.trimEnd('/')
        if (primary != null) {
            if (normalized == primary) return ""
            if (normalized.startsWith("$primary/")) return normalized.removePrefix("$primary/")
        }
        if (normalized.startsWith(VOLUME_MOUNT_ROOT)) {
            val rest = normalized.removePrefix(VOLUME_MOUNT_ROOT)
            val separator = rest.indexOf('/')
            return if (separator >= 0) rest.substring(separator + 1) else ""
        }
        return normalized.trimStart('/')
    }

    /**
     * An existing row for this path may describe an older version of the file.
     * Its size, timestamp, and hash must be refreshed before the upload is
     * queued, otherwise the backup record written on completion carries stale
     * values and every following scan re-uploads the file.
     */
    private suspend fun registerFile(
        source: File,
        folderId: String?,
        chatId: Long?
    ): String {
        fileDao.byLocalPath(source.absolutePath)?.let { existing ->
            val changed = existing.sizeBytes != source.length() ||
                    existing.modifiedAt != source.lastModified()
            if (changed) {
                val media = mediaMetadataExtractor.extract(
                    source, MimeTypes.fromFileName(source.name)
                )
                fileDao.upsert(
                    existing.copy(
                        sizeBytes = source.length(),
                        modifiedAt = source.lastModified()
                            .takeIf { it > 0 } ?: existing.modifiedAt,
                        contentHash = null,
                        width = media.width,
                        height = media.height,
                        durationMs = media.durationMs
                    )
                )
            }
            return existing.id
        }

        val mime = MimeTypes.fromFileName(source.name)
        val media = mediaMetadataExtractor.extract(source, mime)
        val now = System.currentTimeMillis()
        val entity = FileEntity(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            folderId = folderId,
            name = source.name,
            sizeBytes = source.length(),
            mimeType = mime,
            category = FileCategory.fromMimeType(mime),
            localPath = source.absolutePath,
            contentHash = null,
            messageId = null,
            remoteFileId = null,
            remoteUniqueId = null,
            backupState = BackupState.QUEUED,
            width = media.width,
            height = media.height,
            durationMs = media.durationMs,
            createdAt = source.lastModified().takeIf { it > 0 } ?: now,
            modifiedAt = source.lastModified().takeIf { it > 0 } ?: now,
            addedAt = now
        )
        fileDao.upsert(entity)
        return entity.id
    }

    override suspend fun pauseBackup(sessionId: String) {
        setTransfers(sessionId, from = TransferState.QUEUED, to = TransferState.PAUSED)
        setTransfers(sessionId, from = TransferState.RUNNING, to = TransferState.PAUSED)
        backupDao.setSessionStatus(sessionId, BackupSessionStatus.PAUSED, null)
        backupSessionTracker.refresh(sessionId)
    }

    override suspend fun resumeBackup(sessionId: String) {
        setTransfers(sessionId, from = TransferState.PAUSED, to = TransferState.QUEUED)
        backupDao.setSessionStatus(sessionId, BackupSessionStatus.RUNNING, null)
        backupSessionTracker.refresh(sessionId)
        transferRepository.recoverOrphanedTransfers()
    }

    override suspend fun cancelBackup(sessionId: String) {
        for (transfer in transferDao.bySession(sessionId)) {
            if (!transfer.state.isTerminal) {
                cancelTransfer(transfer)
            }
        }
        fileDao.deleteCancelledBackupEntries()
        backupDao.setSessionStatus(
            sessionId,
            BackupSessionStatus.CANCELLED,
            System.currentTimeMillis()
        )
    }

    override suspend fun syncActiveSessionWithSelection() {
        val session = backupDao.activeSession() ?: return
        val folders = settingsRepository.preferences.first().storageChatId
            ?.let { channelRepository.backupFolders(it) }
            .orEmpty()
        var dropped = 0
        for (transfer in transferDao.bySession(session.id)) {
            if (transfer.state.isTerminal) continue
            val sourcePath = transfer.fileId?.let { fileDao.byId(it)?.localPath } ?: continue
            if (folders.none { isInsideFolder(sourcePath, it) }) {
                cancelTransfer(transfer)
                dropped++
            }
        }
        if (dropped > 0) {
            fileDao.deleteCancelledBackupEntries()
            backupSessionTracker.refresh(session.id)
        }
    }

    private suspend fun cancelTransfer(transfer: TransferEntity) {
        transferDao.setState(transfer.id, TransferState.CANCELLED, System.currentTimeMillis())
        transfer.fileId?.let { fileDao.setBackupStateIfLocalOnly(it, BackupState.NONE) }
    }

    private fun isInsideFolder(path: String, folder: String): Boolean {
        val root = folder.trimEnd('/')
        return path == root || path.startsWith("$root/")
    }

    override suspend fun refreshActiveSession() {
        backupDao.activeSession()?.let { backupSessionTracker.refresh(it.id) }
    }

    private suspend fun setTransfers(sessionId: String, from: TransferState, to: TransferState) {
        for (transfer in transferDao.bySession(sessionId)) {
            if (transfer.state == from) {
                transferDao.setState(transfer.id, to, System.currentTimeMillis())
            }
        }
    }

    /**
     * Only the item itself is hidden by its name. A marker file such as
     * .nomedia says how a gallery should index a folder, never whether its
     * contents are worth keeping, so it excludes nothing but itself.
     */
    private fun isHiddenName(file: File): Boolean = file.name.startsWith('.')

    private fun storageUnreadable(): Boolean {
        val root = storagePaths.externalStorageRoot ?: return false
        return root.exists() && root.listFiles() == null
    }

    companion object {
        private const val TAG = "BackupRepository"
        private const val ENQUEUE_BATCH = 500
        private const val VOLUME_MOUNT_ROOT = "/storage/"
    }
}
