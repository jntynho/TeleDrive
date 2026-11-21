package com.infinity.drive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.infinity.drive.data.local.entity.TransferEntity
import com.infinity.drive.domain.model.TransferState
import kotlinx.coroutines.flow.Flow
import com.infinity.drive.domain.model.TransferStage

data class TransferNotificationProgress(
    val running: Int,
    val waitingToRetry: Int,
    val queued: Int,
    val preparing: Int,
    val sealing: Int,
    val joining: Int,
    val knownBytes: Double,
    val transferredBytes: Double,
    val unknownSize: Int
)

@Dao
interface TransferDao {

    @Query("""SELECT * FROM transfers WHERE state = 'QUEUED'
        AND ((:lane = 0 AND type IN ('UPLOAD','BACKUP') AND remoteFileId IS NULL) OR (:lane = 1 AND type IN ('UPLOAD','BACKUP') AND remoteFileId IS NOT NULL) OR (:lane = 2 AND type IN ('DOWNLOAD','RESTORE')))
        ORDER BY priority DESC, createdAt ASC LIMIT 128""")
    suspend fun queuedLane(lane: Int): List<TransferEntity>

    @Query("UPDATE transfers SET state = 'RUNNING', errorMessage = NULL, updatedAt = :now WHERE id = :id AND state = 'QUEUED'")
    suspend fun claim(id: String, now: Long): Int

    @Query("""UPDATE transfers SET state = 'QUEUED', nextAttemptAt = :deadline,
        retryCount = :attempt, errorMessage = :reason, speedBytesPerSecond = 0, updatedAt = :now
        WHERE id = :id AND state = 'RUNNING'""")
    suspend fun defer(id: String, deadline: Long, attempt: Int, reason: String, now: Long)

    @Query("SELECT id FROM transfers WHERE fileId = :fileId AND type = :type AND state IN ('QUEUED','RUNNING','PAUSED') LIMIT 1")
    suspend fun unfinishedOfType(fileId:String,type:com.infinity.drive.domain.model.TransferType):String?
    @Query("UPDATE transfers SET state = 'QUEUED', retryCount = 0, nextAttemptAt = 0, errorMessage = NULL, updatedAt = :now WHERE id = :id AND state IN ('FAILED','PAUSED','CANCELLED')")
    suspend fun resetForRetry(id:String,now:Long)

    @Query("UPDATE transfers SET chatId = :chatId WHERE id = :id AND chatId IS NULL AND state = 'RUNNING'")
    suspend fun bindChannel(id: String, chatId: Long)

    @Query("UPDATE transfers SET chatId = :chatId, messageId = :messageId WHERE id = :id AND state = 'RUNNING'")
    suspend fun recordPending(id:String,chatId:Long,messageId:Long)

    @Query("UPDATE transfers SET sizeBytes = :size WHERE id = :id")
    suspend fun updateSize(id:String,size:Long)

    @Query("UPDATE transfers SET state = 'QUEUED', telegramFileId = NULL, stage = NULL, speedBytesPerSecond = 0, updatedAt = :now WHERE state = 'RUNNING' AND id NOT IN (:liveIds)")
    suspend fun requeueRunningExcept(now:Long,liveIds:List<String>)
    @Query("UPDATE transfers SET state = :state, updatedAt = :now WHERE id = :id AND state = 'RUNNING'")
    suspend fun setRunningState(id:String,state:TransferState,now:Long)
    @Query("UPDATE transfers SET state = 'FAILED', errorMessage = :error, retryableFailure = :retryable, updatedAt = :now WHERE id = :id AND state = 'RUNNING'")
    suspend fun failRunning(id: String, error: String, now: Long, retryable: Boolean): Int

    @Query(
        """SELECT failed.id, failed.fileId, failed.type, failed.backupSessionId
           FROM transfers AS failed
           JOIN files ON files.id = failed.fileId AND files.trashedAt IS NULL
           WHERE failed.chatId = :chatId AND failed.state = 'FAILED' AND failed.retryableFailure = 1
             AND (:batchId IS NULL OR COALESCE(failed.backupSessionId, failed.uploadBatchId) = :batchId)
             AND NOT EXISTS (
                SELECT 1 FROM transfers AS other
                WHERE other.chatId = failed.chatId AND other.fileId = failed.fileId AND other.id != failed.id
                  AND (other.type = failed.type OR
                    (other.type IN ('UPLOAD','BACKUP') AND failed.type IN ('UPLOAD','BACKUP')))
                  AND (other.state IN ('QUEUED','RUNNING','PAUSED') OR
                    (other.state IN ('FAILED','COMPLETED','CANCELLED') AND
                      (other.createdAt > failed.createdAt OR
                        (other.createdAt = failed.createdAt AND other.id > failed.id))))
             )"""
    )
    suspend fun failedBatchCandidates(chatId: Long, batchId: String?): List<FailedTransferRetryCandidate>

    @Query(
        """UPDATE transfers SET state = 'QUEUED', retryableFailure = 0, errorMessage = NULL,
             transferredBytes = 0, speedBytesPerSecond = 0, retryCount = 0, nextAttemptAt = 0,
             updatedAt = :now
           WHERE id IN (:ids) AND state = 'FAILED' AND retryableFailure = 1"""
    )
    suspend fun resetFailedBatch(ids: List<String>, now: Long): Int

    @Query("SELECT COUNT(*) FROM transfers WHERE state = 'COMPLETED' AND completedAt >= :startedAt")
    suspend fun completedSince(startedAt:Long):Int


    @Query("SELECT COUNT(*) FROM transfers WHERE state = 'COMPLETED' AND completedAt >= :startedAt AND backupSessionId IS NULL")
    suspend fun completedOutsideBackupSince(startedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transfer: TransferEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transfers: List<TransferEntity>)

    @Query(
        """SELECT fileId FROM transfers
           WHERE fileId IN (:fileIds) AND state IN ('QUEUED', 'RUNNING', 'PAUSED')"""
    )
    suspend fun unfinishedFileIds(fileIds: List<String>): List<String>

    @Update
    suspend fun update(transfer: TransferEntity)

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun byId(id: String): TransferEntity?

    @Query("SELECT * FROM transfers WHERE id = :id")
    fun observeById(id: String): Flow<TransferEntity?>

    @Query(
        """SELECT * FROM transfers WHERE chatId = :chatId AND state IN (:states)
           ORDER BY CASE WHEN state = 'COMPLETED' THEN COALESCE(completedAt, updatedAt) END DESC,
                    CASE WHEN state = 'FAILED' THEN updatedAt END DESC,
                    CASE WHEN state IN ('QUEUED', 'RUNNING') THEN priority END DESC,
                    createdAt ASC, id ASC LIMIT :limit"""
    )
    fun observeSection(chatId: Long, states: List<TransferState>, limit: Int): Flow<List<TransferEntity>>

    @Query("SELECT COUNT(*) FROM transfers WHERE chatId = :chatId AND state IN (:states)")
    fun observeSectionCount(chatId: Long, states: List<TransferState>): Flow<Int>

    @Query(
        """SELECT * FROM transfers
           WHERE fileId = :fileId AND state IN ('QUEUED', 'RUNNING')
           ORDER BY createdAt ASC LIMIT 1"""
    )
    fun observeActiveForFile(fileId: String): Flow<TransferEntity?>

    @Query("SELECT * FROM transfers WHERE fileId = :fileId ORDER BY createdAt DESC, id DESC LIMIT 1")
    fun observeLatestForFile(fileId:String):Flow<TransferEntity?>

    @Query(
        """SELECT id FROM transfers
            WHERE fileId IN (:fileIds) AND state IN ('QUEUED', 'RUNNING', 'PAUSED')"""
    )
    suspend fun unfinishedIdsForFiles(fileIds: List<String>): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM transfers WHERE fileId = :fileId AND state IN ('QUEUED', 'RUNNING', 'PAUSED'))")
    fun observeUnfinishedForFile(fileId: String): Flow<Boolean>

    @Query("SELECT * FROM transfers WHERE state = 'QUEUED' ORDER BY priority DESC, createdAt ASC LIMIT :limit")
    suspend fun nextQueued(limit: Int): List<TransferEntity>

    @Query("SELECT COUNT(*) FROM transfers WHERE state IN ('QUEUED', 'RUNNING')")
    fun observeActiveCount(): Flow<Int>

    @Query(
        """SELECT
            COUNT(CASE WHEN state = 'RUNNING' THEN 1 END) AS running,
            COUNT(CASE WHEN state = 'QUEUED' AND nextAttemptAt > 0 AND errorMessage IS NOT NULL THEN 1 END) AS waitingToRetry,
            COUNT(CASE WHEN state = 'QUEUED' AND (nextAttemptAt = 0 OR errorMessage IS NULL) THEN 1 END) AS queued,
            COUNT(CASE WHEN state = 'RUNNING' AND stage = 'PREPARING' THEN 1 END) AS preparing,
            COUNT(CASE WHEN state = 'RUNNING' AND stage = 'SEALING' THEN 1 END) AS sealing,
            COUNT(CASE WHEN state = 'RUNNING' AND stage = 'JOINING' THEN 1 END) AS joining,
            COALESCE(SUM(CASE WHEN state = 'RUNNING' AND sizeBytes > 0 THEN CAST(sizeBytes AS REAL) ELSE 0.0 END), 0.0) AS knownBytes,
            COALESCE(SUM(CASE WHEN state = 'RUNNING' AND sizeBytes > 0 THEN CAST(MIN(sizeBytes, MAX(transferredBytes, 0)) AS REAL) ELSE 0.0 END), 0.0) AS transferredBytes,
            COUNT(CASE WHEN state = 'RUNNING' AND sizeBytes <= 0 THEN 1 END) AS unknownSize
          FROM transfers WHERE state IN ('QUEUED', 'RUNNING')"""
    )
    fun observeNotificationProgress(): Flow<TransferNotificationProgress>

    @Query("UPDATE transfers SET stage = :stage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setStage(id: String, stage: TransferStage?, updatedAt: Long)

    @Query(
        """UPDATE transfers SET transferredBytes = :transferred, speedBytesPerSecond = :speed,
           updatedAt = :updatedAt WHERE id = :id"""
    )
    suspend fun updateProgress(id: String, transferred: Long, speed: Long, updatedAt: Long)

    @Query("UPDATE transfers SET state = :state, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setState(id: String, state: TransferState, updatedAt: Long)

    @Query(
        """UPDATE transfers SET state = :state, errorMessage = :error, updatedAt = :updatedAt
           WHERE id = :id"""
    )
    suspend fun setFailed(id: String, state: TransferState, error: String?, updatedAt: Long)

    @Query(
        """UPDATE transfers SET state = :completed, transferredBytes = sizeBytes,
           completedAt = :completedAt, updatedAt = :completedAt WHERE id = :id"""
    )
    suspend fun setCompleted(
        id: String,
        completedAt: Long,
        completed: TransferState = TransferState.COMPLETED
    )

    /** Recovers transfers that were RUNNING when the process died. */
    @Query(
        """UPDATE transfers SET state = 'QUEUED', telegramFileId = NULL,
           stage = NULL, speedBytesPerSecond = 0, errorMessage = NULL,
           updatedAt = :now WHERE state = 'RUNNING'"""
    )
    suspend fun requeueRunning(now: Long)

    @Query("DELETE FROM transfers WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM transfers WHERE chatId = :chatId AND state IN ('COMPLETED', 'CANCELLED')")
    suspend fun clearFinished(chatId: Long)

    @Query(
        """UPDATE transfers SET state = 'PAUSED', updatedAt = :now
           WHERE chatId = :chatId AND state IN ('QUEUED', 'RUNNING')"""
    )
    suspend fun pauseAll(chatId: Long, now: Long): Int

    @Query(
        """UPDATE transfers SET state = 'QUEUED', retryCount = 0, nextAttemptAt = 0, errorMessage = NULL, updatedAt = :now
           WHERE chatId = :chatId AND state = 'PAUSED'"""
    )
    suspend fun resumeAll(chatId: Long, now: Long): Int

    @Query(
        """UPDATE transfers SET state = 'CANCELLED', updatedAt = :now
           WHERE chatId = :chatId AND state IN ('QUEUED', 'RUNNING', 'PAUSED', 'FAILED')"""
    )
    suspend fun cancelAll(chatId: Long, now: Long): Int

    @Query("SELECT * FROM transfers WHERE backupSessionId = :sessionId")
    suspend fun bySession(sessionId: String): List<TransferEntity>
}

data class FailedTransferRetryCandidate(
    val id: String,
    val fileId: String,
    val type: com.infinity.drive.domain.model.TransferType,
    val backupSessionId: String?
)
