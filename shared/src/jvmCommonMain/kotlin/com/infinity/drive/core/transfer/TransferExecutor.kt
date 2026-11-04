package com.infinity.drive.core.transfer

import com.infinity.drive.core.crypto.CryptoKeys
import com.infinity.drive.core.crypto.KeyUnavailableException
import com.infinity.drive.core.crypto.StreamCrypto
import com.infinity.drive.core.crypto.WrappedKeyRepository
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.files.DownloadWriter
import com.infinity.drive.core.files.FileImporter
import com.infinity.drive.core.files.Hashing
import com.infinity.drive.core.media.ThumbnailStore
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramDownloadEvent
import com.infinity.drive.core.telegram.TelegramException
import com.infinity.drive.core.telegram.TelegramLimits
import com.infinity.drive.core.telegram.TelegramUploadEvent
import com.infinity.drive.data.local.dao.BackupDao
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.dao.FilePartDao
import com.infinity.drive.data.local.dao.TransferDao
import com.infinity.drive.data.local.entity.BackupRecordEntity
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.local.entity.TransferEntity
import com.infinity.drive.data.remote.telegram.ManifestCodec
import com.infinity.drive.data.remote.telegram.RemoteFileManifest
import com.infinity.drive.data.repository.FolderPathResolver
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.TransferStage
import com.infinity.drive.domain.model.TransferState
import com.infinity.drive.domain.model.TransferType
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/**
 * Executes one transfer end to end: staging (optional encryption), the
 * Telegram operation, progress persistence, and remote-mapping bookkeeping.
 * Pause and cancel are cooperative: the DB row's state is checked on every
 * progress event and the underlying TDLib operation is canceled by aborting
 * flow collection.
 */
class TransferExecutor(
    private val messages: TransferErrorMessages,
    private val storagePaths: AppStoragePaths,
    private val telegramClient: TelegramClient,
    private val transferDao: TransferDao,
    private val fileDao: FileDao,
    private val backupDao: BackupDao,
    private val manifestCodec: ManifestCodec,
    private val folderPathResolver: FolderPathResolver,
    private val thumbnailStore: ThumbnailStore,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val downloadWriter: DownloadWriter,
    private val fileImporter: FileImporter,
    private val settingsRepository: SettingsRepository,
    private val filePartDao: FilePartDao,
    private val partUploader: PartUploader,
    private val partDownloader: PartDownloader,
    private val apkIconUploader: ApkIconUploader,
    private val backupSessionTracker: BackupSessionTracker
) : TransferRunner {

    /**
     * Telegram reports transfers through updates, so a connection that dies
     * without an error simply stops emitting. Without this the collector waits
     * forever and the row stays RUNNING with no way back.
     */
    private fun <T> Flow<T>.failWhenIdle(message: String): Flow<T> = channelFlow {
        val relay = Channel<T>(Channel.BUFFERED)
        launch {
            try {
                collect { relay.send(it) }
                relay.close()
            } catch (e: Throwable) {
                relay.close(e)
            }
        }
        while (true) {
            val received =
                withTimeoutOrNull(STALL_TIMEOUT_MS.milliseconds) { relay.receiveCatching() }
                    ?: throw TelegramException(STALL_CODE, message)
            received.exceptionOrNull()?.let { throw it }
            if (received.isClosed) break
            send(received.getOrThrow())
        }
    }

    sealed interface Outcome {
        data object Completed : Outcome
        data object Paused : Outcome
        data object Canceled : Outcome
        data class Failed(val message: String, val retryAfterSeconds: Int? = null,val retryable:Boolean=false) : Outcome
    }

    override suspend fun execute(transfer: TransferEntity): Outcome = try {
        checkControl(transfer.id)
        when (transfer.type) {
            TransferType.UPLOAD, TransferType.BACKUP -> executeUpload(transfer)
            TransferType.DOWNLOAD, TransferType.RESTORE -> executeDownload(transfer)
        }
    } catch (control: TransferControlException) {
        if(control.paused) Outcome.Paused else Outcome.Canceled
    } catch (_: KeyUnavailableException) {
        Outcome.Failed(messages.keyMissing)
    } catch (_: CancellationException) {
        throw CancellationException("Transfer ${transfer.id} was cancelled")
    } catch (error: TelegramException) {
        Outcome.Failed(error.message, if(error.isRateLimit) error.retryAfterSeconds ?: 5 else null,retryable=error.code>=500||error.code==408)
    } catch (error: Throwable) {
        Outcome.Failed(error.message ?: messages.uploadEnded,retryable=error is java.io.IOException)
    }

    private suspend fun recordProgress(
        transfer: TransferEntity,
        transferredBytes: Long,
        speed: Long,
        now: Long
    ) {
        transferDao.updateProgress(transfer.id, transferredBytes, speed, now)
        transfer.backupSessionId?.let { backupSessionTracker.refresh(it) }
    }

    private suspend fun executeUpload(transfer: TransferEntity): Outcome {
        val fileId = transfer.fileId
            ?: return Outcome.Failed(messages.noFileReference)
        var entity = fileDao.byId(fileId)
            ?: return Outcome.Failed(messages.fileRecordMissing)
        val currentPrefs=settingsRepository.preferences.first()
        if(!currentPrefs.onboardingComplete||(currentPrefs.storageChatId!=null&&entity.chatId!=null&&entity.chatId!=currentPrefs.storageChatId))return Outcome.Paused
        if(entity.localPath==null&&entity.importReference!=null){
            transferDao.setStage(transfer.id,TransferStage.PREPARING,System.currentTimeMillis())
            var lastControlCheck=0L
            val prepared=fileImporter.prepare(entity.importReference!!){
                currentCoroutineContext().ensureActive()
                val now=System.currentTimeMillis()
                if(now-lastControlCheck>=250){checkControl(transfer.id);lastControlCheck=now}
            }
                ?:return Outcome.Failed("The original is no longer accessible. Select this file again.")
            fileDao.setLocalPath(entity.id,prepared.path)
            entity=entity.copy(localPath=prepared.path,sizeBytes=prepared.sizeBytes)
            fileDao.upsert(entity)
            fileImporter.registered(prepared.path)
            transferDao.updateSize(transfer.id,prepared.sizeBytes)
            transferDao.setStage(transfer.id,null,System.currentTimeMillis())
            if(prepared.sizeBytes<=0)return Outcome.Failed("Telegram cannot store an empty file")
        }
        if(entity.trashedAt!=null)return Outcome.Canceled
        if(transfer.remoteFileId!=null&&entity.messageId!=null){
            transferDao.setCompleted(transfer.id,System.currentTimeMillis());return Outcome.Completed
        }
        if(transfer.remoteFileId!=null&&entity.localPath==null&&entity.messageId==null) {
            val prefs=settingsRepository.preferences.first()
            if(prefs.encryptFiles)return Outcome.Failed("Turn off file encryption to copy directly from Telegram, or upload the original file instead.")
            val target=entity.chatId?:prefs.storageChatId?:telegramClient.ensureStorageChat(null)
            val manifest=RemoteFileManifest(fileId=entity.id,name=entity.name,folderPath=folderPathResolver.pathOf(entity.folderId),folderId=entity.folderId,
                mimeType=entity.mimeType,sizeBytes=entity.sizeBytes,contentHash=null,hidden=false,archived=false,encrypted=false,createdAt=entity.createdAt,modifiedAt=entity.modifiedAt)
            val caption=manifestCodec.encode(manifest,false)
            val recovered=if(transfer.messageId!=null){
                val landed=telegramClient.fetchDocuments(target,0,100).documents.firstOrNull { manifestCodec.decode(it.caption)?.fileId==entity.id }
                if(landed!=null)kotlinx.coroutines.flow.flowOf(TelegramUploadEvent.Completed(landed))
                else telegramClient.recoverUpload(target,transfer.messageId)
            }else null
            var copied:com.infinity.drive.core.telegram.RemoteDocument?=null
            val copyEvents=recovered ?: if(entity.sourceChatId!=null&&entity.sourceMessageId!=null)
                telegramClient.copyMessageEvents(target,entity.sourceChatId,entity.sourceMessageId,caption)
            else kotlinx.coroutines.flow.flowOf(TelegramUploadEvent.Completed(telegramClient.copyDocument(target,transfer.remoteFileId,entity.name,entity.mimeType,caption)))
            copyEvents.failWhenIdle(messages.uploadStalled).collect { event ->
                when(event){
                    is TelegramUploadEvent.Started->{transferDao.recordPending(transfer.id,target,event.temporaryMessageId);checkControl(transfer.id)}
                    is TelegramUploadEvent.Progress->checkControl(transfer.id)
                    is TelegramUploadEvent.Completed->copied=event.document
                }
            }
            val document=copied?:return Outcome.Failed(messages.uploadEnded)
            // Delivery is complete: index it before responding to a concurrent pause/cancel.
            fileDao.setRemoteMapping(id=entity.id,chatId=document.chatId,messageId=document.messageId,remoteFileId=document.remoteFileId,remoteUniqueId=document.uniqueFileId,state=BackupState.BACKED_UP,iconFileId=null)
            checkControl(transfer.id)
            transferDao.setCompleted(transfer.id,System.currentTimeMillis())
            return Outcome.Completed
        }
        val localPath = entity.localPath
            ?: return Outcome.Failed(messages.noLocalCopy)
        val sourceFile = File(localPath)
        if (!sourceFile.exists()) return Outcome.Failed(messages.localFileGone)

        val prefs = settingsRepository.preferences.first()
        val chatId = transfer.chatId ?: prefs.storageChatId
            ?: telegramClient.ensureStorageChat(null).also { resolved ->
                if (resolved != prefs.storageChatId) {
                    settingsRepository.update { it.copy(storageChatId = resolved) }
                }
            }

        val encrypt = prefs.encryptFiles && prefs.keyBackupCreated
        val uploadSize = sourceFile.length()
        val sizeChanged = entity.sizeBytes != uploadSize
        val contentHash = entity.contentHash.takeUnless { sizeChanged }
        if(sizeChanged) fileDao.upsert(entity.copy(sizeBytes=uploadSize,contentHash=null))
        val supersededMessageId = entity.messageId
            ?.takeIf { entity.chatId == chatId && filePartDao.countOf(entity.id) == 0 }

        val rawIconPath = if(encrypt||entity.category==com.infinity.drive.domain.model.FileCategory.IMAGE)null else thumbnailStore.uploadThumbnailFile(entity.id)?.absolutePath
        val iconFileId = apkIconUploader.uploadIconIfApk(entity, chatId, encrypt)
        val previewPath = if (encrypt || entity.category == com.infinity.drive.domain.model.FileCategory.IMAGE) null else rawIconPath

        val manifest = RemoteFileManifest(
            fileId = entity.id,
            name = entity.name,
            folderPath = folderPathResolver.pathOf(entity.folderId),
            folderId = entity.folderId,
            mimeType = entity.mimeType,
            sizeBytes = uploadSize,
            contentHash = contentHash,
            hidden = entity.isHidden,
            archived = entity.isArchived,
            encrypted = encrypt,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
            width = entity.width,
            height = entity.height,
            durationMs = entity.durationMs,
            iconFileId = iconFileId
        )
        val caption = manifestCodec.encode(manifest, encrypt)

        fileDao.setBackupState(entity.id, BackupState.UPLOADING)

        if (splitsIntoParts(entity, sourceFile, encrypt)) {
            return uploadInParts(
                transfer = transfer,
                entity = entity,
                sourceFile = sourceFile,
                localPath = localPath,
                chatId = chatId,
                manifest = manifest,
                encrypt = encrypt,
                contentHash = contentHash,
                supersededMessageId = supersededMessageId
            )
        }

        val recovered:Flow<TelegramUploadEvent>? = if(transfer.messageId!=null||transfer.retryCount>0) {
            val landed=telegramClient.fetchDocuments(chatId,0,100).documents.firstOrNull { document ->
                manifestCodec.decode(document.caption)?.let{it.fileId==entity.id&&it.modifiedAt==entity.modifiedAt&&it.sizeBytes==uploadSize&&it.partCount<=1}==true
            }
            if(landed!=null)kotlinx.coroutines.flow.flowOf(TelegramUploadEvent.Completed(landed))
            else transfer.messageId?.let{telegramClient.recoverUpload(chatId,it)}
        }else null
        var stagingFile: File? = null
        val (uploadPath, uploadName) = if (encrypt&&recovered==null) {
            val staging = File(stagingDir(), "${entity.id}.tde")
            val key = wrappedKeyRepository.getOrCreate(CryptoKeys.CONTENT)
            sourceFile.inputStream().use { input ->
                staging.outputStream().buffered().use { output ->
                    streamCrypto.encryptStream(key, input, output)
                }
            }
            stagingFile = staging
            staging.absolutePath to "${entity.id}.tde"
        } else {
            sourceFile.absolutePath to entity.name
        }

        val startedAt = System.currentTimeMillis()
        val ticker = ProgressTicker().apply { start(0, startedAt) }

        return try {
            var outcome: Outcome =
                Outcome.Failed(messages.uploadEnded)
            (recovered ?: telegramClient.uploadDocument(
                chatId = chatId,
                localPath = uploadPath,
                fileName = uploadName,
                mimeType = if (encrypt) "application/octet-stream" else entity.mimeType,
                caption = caption,
                thumbnailPath = previewPath
            )).failWhenIdle(messages.uploadStalled).collect { event ->
                currentCoroutineContext().ensureActive()
                when (event) {
                    is TelegramUploadEvent.Started -> {transferDao.recordPending(transfer.id,chatId,event.temporaryMessageId);checkControl(transfer.id)}
                    is TelegramUploadEvent.Progress -> {
                        val now = System.currentTimeMillis()
                        ticker.tick(event.transferredBytes, now)?.let { speed ->
                            recordProgress(transfer, event.transferredBytes, speed, now)
                        }
                        checkControl(transfer.id)
                    }

                    is TelegramUploadEvent.Completed -> {
                        val document = event.document
                        fileDao.setRemoteMapping(
                            id = entity.id,
                            chatId = document.chatId,
                            messageId = document.messageId,
                            remoteFileId = document.remoteFileId,
                            remoteUniqueId = document.uniqueFileId,
                            state = BackupState.BACKED_UP,
                            iconFileId = iconFileId
                        )
                        if (transfer.type == TransferType.BACKUP) {
                            backupDao.upsertRecord(
                                BackupRecordEntity(
                                    id = UUID.randomUUID().toString(),
                                    sourcePath = localPath,
                                    fileId = entity.id,
                                    sizeBytes = uploadSize,
                                    modifiedAt = sourceFile.lastModified(),
                                    contentHash = contentHash,
                                    backedUpAt = System.currentTimeMillis()
                                )
                            )
                        }
                        supersededMessageId
                            ?.takeIf { it != document.messageId }
                            ?.let { stale ->
                                runCatching {
                                    telegramClient.deleteMessages(chatId, listOf(stale))
                                }
                            }
                        dropStagedSource(entity.id, localPath)
                        transferDao.setCompleted(transfer.id, System.currentTimeMillis())
                        outcome = Outcome.Completed
                    }
                }
            }
            outcome
        } catch (e: TransferControlException) {
            fileDao.setBackupStateIfLocalOnly(
                entity.id,
                if (e.paused) BackupState.QUEUED else BackupState.NONE
            )
            if (e.paused) Outcome.Paused else Outcome.Canceled
        } catch (e: TelegramException) {
            fileDao.setBackupStateIfLocalOnly(entity.id, BackupState.FAILED)
            Outcome.Failed(e.message, if(e.isRateLimit) e.retryAfterSeconds ?: 5 else null,retryable=e.code>=500||e.code==408)
        } finally {
            stagingFile?.delete()
        }
    }

    /**
     * Splitting is decided by the account's current limit, but a file that was
     * already split stays split: the parts on record are what the file is made
     * of, whatever the limit happens to be today.
     *
     * What the limit is measured against is the sealed size, not the file on
     * disk, because sealing is what Telegram receives and it grows the file by
     * a frame header per megabyte. A file sitting just under the cap would
     * otherwise be sent whole and come back rejected.
     */
    private suspend fun splitsIntoParts(
        entity: FileEntity,
        source: File,
        encrypt: Boolean
    ): Boolean {
        if (filePartDao.countOf(entity.id) > 0) return true
        val limit = runCatching { telegramClient.getLimits() }
            .getOrDefault(TelegramLimits.REGULAR)
            .maxFileBytes
        val plainSize = source.length()
        val uploadSize = if (encrypt) streamCrypto.storedSize(plainSize) else plainSize
        return FileParts.splits(uploadSize, limit)
    }

    private suspend fun uploadInParts(
        transfer: TransferEntity,
        entity: FileEntity,
        sourceFile: File,
        localPath: String,
        chatId: Long,
        manifest: RemoteFileManifest,
        encrypt: Boolean,
        contentHash: String?,
        supersededMessageId: Long?
    ): Outcome {
        val ticker = ProgressTicker().apply { start(0, System.currentTimeMillis()) }
        var outcome: Outcome =
            Outcome.Failed(messages.uploadEnded)

        return try {
            partUploader.upload(entity, sourceFile, chatId, manifest, encrypt)
                .collect { event ->
                    currentCoroutineContext().ensureActive()
                    when (event) {
                        is PartUploader.Event.Progress -> {
                            val now = System.currentTimeMillis()
                            ticker.tick(event.transferredBytes, now)?.let { speed ->
                                recordProgress(transfer, event.transferredBytes, speed, now)
                            }
                            checkControl(transfer.id)
                        }

                        is PartUploader.Event.Sealing -> {
                            transferDao.setStage(
                                transfer.id,
                                TransferStage.SEALING,
                                System.currentTimeMillis()
                            )
                            checkControl(transfer.id)
                        }

                        is PartUploader.Event.PartDone -> {
                            transferDao.setStage(transfer.id, null, System.currentTimeMillis())
                            checkControl(transfer.id)
                        }

                        is PartUploader.Event.Completed -> {
                            val first = event.parts.firstOrNull()
                                ?: error("Upload finished with no parts")
                            fileDao.setPartCount(entity.id, event.parts.size)
                            fileDao.setRemoteMapping(
                                id = entity.id,
                                chatId = first.chatId,
                                messageId = first.messageId,
                                remoteFileId = first.remoteFileId,
                                remoteUniqueId = first.remoteUniqueId,
                                state = BackupState.BACKED_UP,
                                iconFileId = manifest.iconFileId
                            )
                            if (transfer.type == TransferType.BACKUP) {
                                backupDao.upsertRecord(
                                    BackupRecordEntity(
                                        id = UUID.randomUUID().toString(),
                                        sourcePath = localPath,
                                        fileId = entity.id,
                                        sizeBytes = manifest.sizeBytes,
                                        modifiedAt = sourceFile.lastModified(),
                                        contentHash = contentHash,
                                        backedUpAt = System.currentTimeMillis()
                                    )
                                )
                            }
                            supersededMessageId?.let { stale ->
                                runCatching {
                                    telegramClient.deleteMessages(chatId, listOf(stale))
                                }
                            }
                            dropStagedSource(entity.id, localPath)
                            transferDao.setCompleted(transfer.id, System.currentTimeMillis())
                            outcome = Outcome.Completed
                        }
                    }
                }
            outcome
        } catch (e: TransferControlException) {
            fileDao.setBackupStateIfLocalOnly(
                entity.id,
                if (e.paused) BackupState.QUEUED else BackupState.NONE
            )
            if (!e.paused) partUploader.discardParts(entity.id)
            if (e.paused) Outcome.Paused else Outcome.Canceled
        } catch (e: TelegramException) {
            fileDao.setBackupStateIfLocalOnly(entity.id, BackupState.FAILED)
            Outcome.Failed(e.message, if(e.isRateLimit) e.retryAfterSeconds ?: 5 else null,retryable=e.code>=500||e.code==408)
        }
    }

    private suspend fun executeDownload(transfer: TransferEntity): Outcome {
        val fileId = transfer.fileId
            ?: return Outcome.Failed(messages.noFileReference)
        val entity = fileDao.byId(fileId)
            ?: return Outcome.Failed(messages.fileRecordMissing)

        if(transfer.type==TransferType.RESTORE){
            val original=entity.localPath?.let(::File)?.takeIf{it.isFile}
            if(original!=null)return finalizeDownload(transfer,entity.id,original.absolutePath,alreadyPlain=true)
        }
        if (filePartDao.countOf(entity.id) > 1) return downloadInParts(transfer, entity)

        val remoteFileId = entity.remoteFileId
            ?: return Outcome.Failed(messages.noRemoteCopy)

        val startedAt = System.currentTimeMillis()
        val ticker = ProgressTicker().apply { start(0, startedAt) }

        return try {
            var outcome: Outcome =
                Outcome.Failed(messages.downloadEnded)
            telegramClient.downloadDocument(remoteFileId)
                .failWhenIdle(messages.downloadStalled)
                .collect { event ->
                    currentCoroutineContext().ensureActive()
                    when (event) {
                        is TelegramDownloadEvent.Progress -> {
                            val now = System.currentTimeMillis()
                            ticker.tick(event.transferredBytes, now)?.let { speed ->
                                recordProgress(transfer, event.transferredBytes, speed, now)
                            }
                            checkControl(transfer.id)
                        }

                        is TelegramDownloadEvent.Completed -> {
                            outcome = finalizeDownload(transfer, entity.id, event.localPath)
                        }
                    }
                }
            outcome
        } catch (e: TransferControlException) {
            if (e.paused) Outcome.Paused else Outcome.Canceled
        } catch (e: TelegramException) {
            Outcome.Failed(e.message, if(e.isRateLimit) e.retryAfterSeconds ?: 5 else null,retryable=e.code>=500||e.code==408)
        }
    }

    private suspend fun downloadInParts(
        transfer: TransferEntity,
        entity: FileEntity
    ): Outcome {
        val ticker = ProgressTicker().apply { start(0, System.currentTimeMillis()) }
        var outcome: Outcome =
            Outcome.Failed(messages.downloadEnded)

        return try {
            partDownloader.download(entity.id, entity.isEncrypted)
                .failWhenIdle(messages.downloadStalled)
                .collect { event ->
                    currentCoroutineContext().ensureActive()
                    when (event) {
                        is PartDownloader.Event.Progress -> {
                            val now = System.currentTimeMillis()
                            transferDao.setStage(transfer.id, null, now)
                            ticker.tick(event.transferredBytes, now)?.let { speed ->
                                recordProgress(transfer, event.transferredBytes, speed, now)
                            }
                            checkControl(transfer.id)
                        }

                        is PartDownloader.Event.Joining -> {
                            transferDao.setStage(
                                transfer.id,
                                TransferStage.JOINING,
                                System.currentTimeMillis()
                            )
                            checkControl(transfer.id)
                        }

                        is PartDownloader.Event.Completed -> {
                            transferDao.setStage(transfer.id, null, System.currentTimeMillis())
                            outcome = finalizeDownload(
                                transfer = transfer,
                                fileId = entity.id,
                                tdlibPath = event.localPath,
                                alreadyPlain = true
                            )
                            partDownloader.discardAssembly(entity.id)
                        }
                    }
                }
            outcome
        } catch (e: TransferControlException) {
            if (!e.paused) partDownloader.discardAssembly(entity.id)
            if (e.paused) Outcome.Paused else Outcome.Canceled
        } catch (e: TelegramException) {
            Outcome.Failed(e.message, if(e.isRateLimit) e.retryAfterSeconds ?: 5 else null,retryable=e.code>=500||e.code==408)
        }
    }

    private suspend fun finalizeDownload(
        transfer: TransferEntity,
        fileId: String,
        tdlibPath: String,
        alreadyPlain: Boolean = false
    ): Outcome {
        val entity = fileDao.byId(fileId)
            ?: return Outcome.Failed(messages.fileRecordMissing)
        val source = File(tdlibPath)
        if (!source.exists()) return Outcome.Failed(messages.downloadedMissing)

        val key = if (entity.isEncrypted && !alreadyPlain) {
            wrappedKeyRepository.get(CryptoKeys.CONTENT)
                ?: return Outcome.Failed(messages.keyMissing)
        } else {
            null
        }

        val folderPath = folderPathResolver.pathOf(entity.folderId)
        val savedPath = (if(entity.offlinePinned&&transfer.type!=TransferType.RESTORE) offlineWriter(entity.id) else downloadWriter).write(
            fileName = entity.name,
            mimeType = entity.mimeType,
            folderPath = folderPath
        ) { output ->
            source.inputStream().buffered().use { input ->
                if (key != null) {
                    streamCrypto.decryptStream(key, input, output)
                } else {
                    input.copyTo(output)
                }
            }
        } ?: return Outcome.Failed(
            if (entity.isEncrypted) {
                messages.decryptionFailed
            } else {
                messages.saveFailed
            }
        )

        if(!(transfer.type==TransferType.RESTORE&&entity.offlinePinned&&entity.localPath!=null))fileDao.setLocalPath(fileId, savedPath)
        transferDao.setCompleted(transfer.id, System.currentTimeMillis())
        return Outcome.Completed
    }

    private suspend fun dropStagedSource(fileId: String, localPath: String) {
        if (!fileImporter.isStaged(localPath)) return
        val latest=fileDao.byId(fileId)?:return
        if(latest.offlinePinned)return
        // A pin or another operation can have replaced the registered local path during upload.
        if(latest.localPath==localPath){File(localPath).delete();fileDao.clearLocalPathIfMatches(fileId,localPath)}
    }

    private suspend fun checkControl(transferId: String) {
        when (transferDao.byId(transferId)?.state) {
            TransferState.PAUSED -> throw TransferControlException(paused = true)
            TransferState.CANCELLED -> throw TransferControlException(paused = false)
            else -> Unit
        }
    }

    private fun offlineWriter(id:String)=object:DownloadWriter {
        override fun write(fileName:String,mimeType:String,folderPath:String?,body:(java.io.OutputStream)->Unit):String? {
            val directory=File(storagePaths.filesDir,"offline/$id").apply{mkdirs()}
            val target=File(directory,com.infinity.drive.core.files.FileNameUtils.sanitize(fileName))
            val temporary=File(directory,".download")
            return try { temporary.outputStream().buffered().use(body);check(temporary.renameTo(target));target.absolutePath }
            catch(e:Exception){temporary.delete();throw e}
        }
    }

    private fun stagingDir(): File =
        File(storagePaths.cacheDir, "staging").apply { mkdirs() }

    private class TransferControlException(val paused: Boolean) : Exception()

    companion object {
        private const val STALL_TIMEOUT_MS = 180_000L
        private const val STALL_CODE = 408
    }
}

fun interface TransferRunner {
    suspend fun execute(transfer:TransferEntity):TransferExecutor.Outcome
}
