package com.infinity.drive.domain.repository

import com.infinity.drive.core.common.AppResult
import com.infinity.drive.domain.model.TransferTask
import com.infinity.drive.domain.model.TransferSection
import kotlinx.coroutines.flow.Flow

interface TransferRepository {

    /**
     * The newest rows of one section. A backup can queue tens of thousands of
     * transfers, far more than a list can show, so every observer is bounded.
     */
    fun observeSection(chatId: Long, section: TransferSection, limit: Int): Flow<List<TransferTask>>

    fun observeSectionCount(chatId: Long, section: TransferSection): Flow<Int>

    fun observeActiveCount(): Flow<Int>

    /** The queued or running transfer that is fetching [fileId], if any. */
    fun observeActiveForFile(fileId: String): Flow<TransferTask?>

    fun observeLatestForFile(fileId:String):Flow<TransferTask?> = observeActiveForFile(fileId)

    fun observeUnfinishedForFile(fileId: String): Flow<Boolean>

    /** Queues an upload of [fileId]'s local copy to the storage chat. */
    suspend fun enqueueUpload(fileId: String, priority: Int = 0): AppResult<String>

    suspend fun enqueueUploads(fileIds: List<String>): AppResult<Int> = AppResult.Success(fileIds.count { enqueueUpload(it) is AppResult.Success })

    suspend fun enqueueUploads(fileIds: List<String>, batchId: String): AppResult<Int> = enqueueUploads(fileIds)

    /** Queues every file held only on this device. Returns how many were added. */
    suspend fun enqueuePendingUploads(): AppResult<Int>

    /**
     * Queues a whole backup at once. The checks a single transfer repeats per
     * file are answered once for the run, and the rows are written in batches,
     * so a scan of tens of thousands of files does not spend a Telegram round
     * trip and a separate write on each one.
     */
    suspend fun enqueueBackupBatch(fileIds: List<String>, sessionId: String): AppResult<Int>

    /** Queues a download of [fileId]'s remote copy into local storage. */
    suspend fun enqueueDownload(fileId: String, priority: Int = 0): AppResult<String>
    suspend fun enqueueSaveToPhone(fileId:String):AppResult<String> = enqueueDownload(fileId)

    suspend fun pause(id: String)

    suspend fun resume(id: String)

    suspend fun cancel(id: String, dismissFinished: Boolean = false)

    /** Stops any queued, running or paused transfer belonging to [fileIds]. */
    suspend fun cancelForFiles(fileIds: List<String>)

    suspend fun retry(id: String)

    /** Requeues eligible, temporarily failed transfers, optionally scoped to one batch. */
    suspend fun retryFailedBatch(chatId: Long, batchId: String? = null): Int = 0

    /** Bulk controls applied in one database write so the list updates at once. */
    suspend fun pauseAll(chatId: Long)

    suspend fun resumeAll(chatId: Long)

    suspend fun cancelAll(chatId: Long)

    suspend fun clearFinished(chatId: Long)

    /** Re-queues transfers left RUNNING by a killed process. */
    suspend fun recoverOrphanedTransfers()
}
