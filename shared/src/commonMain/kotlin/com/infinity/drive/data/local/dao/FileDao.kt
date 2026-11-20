// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Update
import com.infinity.drive.core.files.FileOrderKeys
import com.infinity.drive.data.local.entity.AlbumSummary
import com.infinity.drive.data.local.entity.FolderEntity
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.domain.model.DuplicateFile
import com.infinity.drive.data.local.entity.HomeAggregates
import com.infinity.drive.data.local.entity.LocalCopyRef
import com.infinity.drive.domain.model.BackupState
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @RawQuery
    suspend fun queryCount(query:RoomRawQuery):Int

    @Query("SELECT * FROM audio_tags WHERE fileId = :id")
    suspend fun audioTagsById(id:String):com.infinity.drive.data.local.entity.AudioTagEntity?

    @androidx.room.Upsert
    suspend fun storeAudioTags(tags:com.infinity.drive.data.local.entity.AudioTagEntity)

    /** Read, revision check and merge are one transaction across thumbnail and player writers. */
    @androidx.room.Transaction
    suspend fun mergeAudioTags(tags:com.infinity.drive.data.local.entity.AudioTagEntity) {
        val current=byId(tags.fileId)?:return
        if(current.modifiedAt!=tags.modifiedAt||current.sizeBytes!=tags.sizeBytes||current.contentHash!=tags.contentHash)return
        val previous=audioTagsById(tags.fileId)?.takeIf{it.modifiedAt==tags.modifiedAt&&(it.sizeBytes<0||it.sizeBytes==tags.sizeBytes&&it.contentHash==tags.contentHash)}
        val next=tags.copy(localPath=current.localPath,
            title=tags.title?.takeIf(String::isNotBlank)?:previous?.title,
            artist=tags.artist?.takeIf(String::isNotBlank)?:previous?.artist,
            durationMs=tags.durationMs?:previous?.durationMs?:current.durationMs,
            inspected=tags.inspected||previous?.inspected==true)
        if(previous?.copy(localPath=next.localPath)!=next)storeAudioTags(next)
    }



    @Query("SELECT * FROM files WHERE chatId IS :chatId AND folderId IS :folderId AND name = :name COLLATE NOCASE AND trashedAt IS NULL LIMIT 1")
    suspend fun nameClash(name:String,folderId:String?,chatId:Long?):FileEntity?
    @Query("UPDATE files SET offlinePinned = :pinned WHERE id = :id")
    suspend fun setOfflinePinned(id:String,pinned:Boolean)

    @Query("UPDATE files SET localPath = NULL WHERE id = :id AND localPath = :expected AND (:evenPinned = 1 OR offlinePinned = 0)")
    suspend fun clearLocalPathIfMatches(id:String,expected:String,evenPinned:Boolean=false):Int

    @Query("UPDATE files SET uploadRequested = :requested WHERE id IN (:ids)")
    suspend fun setUploadRequested(ids:List<String>,requested:Boolean)
    @Query("UPDATE files SET uploadRequested = 0 WHERE messageId IS NULL AND id IN (SELECT fileId FROM transfers WHERE id IN (:ids) AND type IN ('UPLOAD','BACKUP'))")
    suspend fun suppressUploadsForTransfers(ids:List<String>)
    @Query("UPDATE files SET uploadRequested = 0 WHERE messageId IS NULL AND id IN (SELECT fileId FROM transfers WHERE chatId = :chatId AND type IN ('UPLOAD','BACKUP') AND state IN ('QUEUED','RUNNING','PAUSED','FAILED'))")
    suspend fun suppressPendingUploads(chatId: Long)

    @Query("UPDATE files SET contentHash = :hash WHERE id = :id")
    suspend fun setContentHash(id:String,hash:String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: FileEntity)

    @Update
    suspend fun update(file: FileEntity)

    @Query(
        """SELECT * FROM files
            WHERE sizeBytes = :sizeBytes AND chatId IS :chatId
              AND trashedAt IS NULL
              """
    )
    suspend fun liveMatchesBySize(sizeBytes: Long, chatId: Long?): List<FileEntity>

    @Query(
        """SELECT category, COUNT(*) AS fileCount, SUM(sizeBytes) AS totalBytes
             FROM files
            WHERE chatId IS :chatId AND trashedAt IS NULL
              AND isHidden = 0 AND isArchived = 0
            GROUP BY category
            ORDER BY totalBytes DESC"""
    )
    fun observeUsageByCategory(chatId: Long?): Flow<List<CategoryUsage>>

    @Query("""SELECT COALESCE(SUM(sizeBytes), 0) FROM files
        WHERE chatId IS :chatId AND trashedAt IS NULL AND isHidden = 0 AND isArchived = 0
        AND localPath IS NOT NULL AND localPath != ''""")
    fun observeVisibleLocalBytes(chatId: Long?): Flow<Long>


    @Query("UPDATE files SET pendingPublish = 1 WHERE id IN (:ids)")
    suspend fun markPendingPublish(ids: List<String>)

    @Query("UPDATE files SET pendingPublish = 1 WHERE folderId IN (:folderIds)")
    suspend fun markPendingPublishInFolders(folderIds: List<String>)

    @Query("UPDATE files SET partCount = :partCount WHERE id = :id")
    suspend fun setPartCount(id: String, partCount: Int)

    @Query("UPDATE files SET pendingPublish = 0 WHERE id = :id")
    suspend fun clearPendingPublish(id: String)

    @Query("SELECT * FROM files WHERE pendingPublish = 1 LIMIT :limit")
    suspend fun pendingPublish(limit: Int): List<FileEntity>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun byId(id: String): FileEntity?

    @Query("SELECT * FROM files WHERE id = :id")
    fun observeById(id: String): Flow<FileEntity?>

    @Query("SELECT * FROM files WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<FileEntity>

    @Query("SELECT * FROM files WHERE id IN (:ids)")
    fun observeByIds(ids: List<String>): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE localPath = :path LIMIT 1")
    suspend fun byLocalPath(path: String): FileEntity?

    @Query(
        """SELECT * FROM files
           WHERE trashedAt IS NOT NULL AND name = :name AND sizeBytes = :sizeBytes
             AND chatId IS :chatId"""
    )
    suspend fun trashedMatches(
        name: String,
        sizeBytes: Long,
        chatId: Long?
    ): List<FileEntity>

    @RawQuery(observedEntities = [FileEntity::class, com.infinity.drive.data.local.entity.AudioTagEntity::class, FolderEntity::class])
    fun pagingSource(query: RoomRawQuery): PagingSource<Int, FileEntity>

    /** Non-search pages need not reload for each optional tag discovered in a visible row. */
    @RawQuery(observedEntities = [FileEntity::class, FolderEntity::class])
    fun contentPagingSource(query:RoomRawQuery):PagingSource<Int,FileEntity>

    @RawQuery(observedEntities = [FileEntity::class, com.infinity.drive.data.local.entity.AudioTagEntity::class, FolderEntity::class])
    fun observeList(query: RoomRawQuery): Flow<List<FileEntity>>

    @RawQuery
    suspend fun idList(query: RoomRawQuery): List<String>

    @Query(
        """SELECT * FROM files
           WHERE trashedAt IS NULL AND chatId IS :chatId AND isHidden = 0 AND isArchived = 0
           ORDER BY addedAt DESC LIMIT :limit"""
    )
    fun observeRecent(limit: Int, chatId: Long?): Flow<List<FileEntity>>

    @Query(
        """SELECT * FROM files
           WHERE trashedAt IS NULL AND isFavorite = 1 AND chatId IS :chatId
           ORDER BY name COLLATE NOCASE ASC"""
    )
    fun observeFavorites(chatId: Long?): Flow<List<FileEntity>>

    @Query("UPDATE files SET chatId = :chatId WHERE chatId IS NULL")
    suspend fun claimUnownedRows(chatId: Long)

    @Query(
        """SELECT folderId AS folderId, chatId AS chatId FROM files
           WHERE folderId IS NOT NULL AND chatId IS NOT NULL AND trashedAt IS NULL
           GROUP BY folderId, chatId"""
    )
    suspend fun folderOwners(): List<FolderOwner>

    @Query("SELECT COUNT(*) FROM files WHERE trashedAt IS NULL AND chatId IS :chatId")
    suspend fun fileCount(chatId: Long?): Int

    @Query("DELETE FROM files WHERE chatId = :chatId AND localPath IS NULL")
    suspend fun deleteRemoteOnlyInChat(chatId: Long): Int

    @Query(
        """UPDATE files SET chatId = NULL, messageId = NULL, remoteFileId = NULL,
           remoteUniqueId = NULL, backupState = 'NONE' WHERE chatId = :chatId"""
    )
    suspend fun detachChat(chatId: Long): Int

    @Query(
        """SELECT * FROM files
           WHERE name = :name AND sizeBytes = :sizeBytes AND chatId IS :chatId
             AND messageId IS NOT NULL AND localPath IS NULL AND trashedAt IS NULL"""
    )
    suspend fun unlinkedRemoteMatches(
        name: String,
        sizeBytes: Long,
        chatId: Long?
    ): List<FileEntity>

    @Query("SELECT * FROM files WHERE remoteUniqueId IN (:uniqueIds)")
    suspend fun byRemoteUniqueIds(uniqueIds: List<String>): List<FileEntity>

    /**
     * The home screen needs every headline number at once, and separate
     * observed queries each rescan the table on any write. One pass keeps a
     * large library from saturating the query executors during a backup.
     */
    @Query(
        """SELECT COUNT(*) AS total,
                  COALESCE(SUM(CASE WHEN messageId IS NOT NULL THEN sizeBytes END), 0)
                      AS remoteBytes,
                  COALESCE(SUM(CASE WHEN backupState = 'BACKED_UP' THEN 1 END), 0)
                      AS backedUp,
                  COALESCE(SUM(CASE WHEN backupState = 'QUEUED' THEN 1 END), 0) AS queued,
                  COALESCE(SUM(CASE WHEN backupState = 'FAILED' THEN 1 END), 0) AS failed,
                  COALESCE(SUM(
                      CASE WHEN localPath IS NOT NULL AND messageId IS NULL
                                AND backupState IN ('NONE', 'FAILED') THEN 1 END
                  ), 0) AS localOnly
           FROM files
           WHERE trashedAt IS NULL AND chatId IS :chatId"""
    )
    fun observeHomeAggregates(chatId: Long?): Flow<HomeAggregates>

    suspend fun rename(id: String, name: String, modifiedAt: Long) = updateName(
        id, name, modifiedAt, FileOrderKeys.natural(name), FileOrderKeys.byExtension(name)
    )

    @Query(
        """UPDATE files SET name = :name, modifiedAt = :modifiedAt, naturalNameKey = :naturalNameKey,
                               extensionSortKey = :extensionSortKey WHERE id = :id"""
    )
    suspend fun updateName(
        id: String, name: String, modifiedAt: Long, naturalNameKey: String, extensionSortKey: String
    )

    @Query("UPDATE files SET folderId = :folderId, modifiedAt = :modifiedAt WHERE id IN (:ids)")
    suspend fun move(ids: List<String>, folderId: String?, modifiedAt: Long)

    @Query("UPDATE files SET tagsEncoded = :tags, pendingPublish = 1 WHERE id IN (:ids)")
    suspend fun updateTags(ids: List<String>, tags: String)

    @Query("UPDATE files SET isFavorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<String>, favorite: Boolean)

    @Query("UPDATE files SET isHidden = :hidden WHERE id IN (:ids)")
    suspend fun setHidden(ids: List<String>, hidden: Boolean)

    @Query("UPDATE files SET isArchived = :archived WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<String>, archived: Boolean)

    @Query("UPDATE files SET backupState = :state WHERE id = :id")
    suspend fun setBackupState(id: String, state: BackupState)

    @Query("UPDATE files SET backupState = :state WHERE id IN (:ids)")
    suspend fun setBackupStates(ids: List<String>, state: BackupState)

    /**
     * Transfer bookkeeping must never claim a file is unsaved while its copy
     * still sits in the channel, so downgrades only apply to local-only rows.
     */
    @Query("""UPDATE files SET backupState = :state WHERE id = :id
        AND (messageId IS NULL OR (lower(name) LIKE '%.md' AND backupState != 'BACKED_UP'))""")
    suspend fun setBackupStateIfLocalOnly(id: String, state: BackupState)

    @Query("""UPDATE files SET backupState = 'FAILED' WHERE id = :id
        AND (messageId IS NULL OR (lower(name) LIKE '%.md' AND backupState != 'BACKED_UP'))
        AND EXISTS (SELECT 1 FROM transfers WHERE transfers.id = :transferId AND state = 'FAILED')""")
    suspend fun markLocalFailureIfTransferStillFailed(id: String, transferId: String)

    @Query(
        """UPDATE files SET backupState = 'BACKED_UP'
           WHERE messageId IS NOT NULL
             AND lower(name) NOT LIKE '%.md'
             AND backupState NOT IN ('BACKED_UP', 'UPLOADING')"""
    )
    suspend fun repairBackedUpStates(): Int

    @Query(
        """UPDATE files SET backupState = 'NONE'
           WHERE backupState IN ('QUEUED', 'UPLOADING')
             AND id NOT IN (
                 SELECT fileId FROM transfers
                  WHERE fileId IS NOT NULL
                    AND state IN ('QUEUED', 'RUNNING', 'PAUSED')
             )"""
    )
    suspend fun clearStaleQueuedStates(): Int

    @Query(
        """UPDATE files SET backupState = 'NONE'
           WHERE backupState = 'FAILED'
             AND id NOT IN (
                 SELECT fileId FROM transfers
                  WHERE fileId IS NOT NULL AND state = 'FAILED'
             )"""
    )
    suspend fun clearStaleFailedStates(): Int

    @Query(
        """DELETE FROM files
           WHERE messageId IS NULL AND trashedAt IS NULL
             AND id IN (
                 SELECT fileId FROM transfers
                  WHERE fileId IS NOT NULL
                    AND backupSessionId IS NOT NULL
                    AND state = 'CANCELLED'
             )"""
    )
    suspend fun deleteCancelledBackupEntries(): Int

    @Query(
        """UPDATE files SET chatId = :chatId, messageId = :messageId,
           remoteFileId = :remoteFileId, remoteUniqueId = :remoteUniqueId,
           iconFileId = COALESCE(:iconFileId, iconFileId),
           isEncrypted = COALESCE(:encrypted, isEncrypted),
           backupState = :state WHERE id = :id"""
    )
    suspend fun setRemoteMapping(
        id: String,
        chatId: Long?,
        messageId: Long?,
        remoteFileId: String?,
        remoteUniqueId: String?,
        state: BackupState,
        iconFileId: String? = null,
        encrypted: Boolean? = null
    )

    @Query(
        """UPDATE files
           SET messageId = NULL, remoteFileId = NULL, remoteUniqueId = NULL,
               backupState = 'NONE'
           WHERE id = :id"""
    )
    suspend fun detachRemote(id: String)

    @Query("UPDATE files SET localPath = :localPath WHERE id = :id")
    suspend fun setLocalPath(id: String, localPath: String?)

    @Query(
        """UPDATE files SET trashedAt = :trashedAt, preTrashFolderId = folderId, folderId = NULL
           WHERE id IN (:ids)"""
    )
    suspend fun moveToTrash(ids: List<String>, trashedAt: Long)

    @Query(
        """UPDATE files SET folderId = preTrashFolderId, trashedAt = NULL, preTrashFolderId = NULL
           WHERE id IN (:ids)"""
    )
    suspend fun restoreFromTrash(ids: List<String>)

    @Query(
        """SELECT * FROM files
           WHERE trashedAt IS NOT NULL AND chatId IS :chatId
             AND (preTrashFolderId IS NULL
                  OR preTrashFolderId NOT IN
                     (SELECT id FROM folders WHERE trashedAt IS NOT NULL))
           ORDER BY trashedAt DESC"""
    )
    fun observeTrashRoots(chatId: Long?): Flow<List<FileEntity>>

    @Query(
        """SELECT * FROM files
           WHERE trashedAt IS NOT NULL AND preTrashFolderId IN (:folderIds)"""
    )
    suspend fun trashedInFolders(folderIds: List<String>): List<FileEntity>

    @Query(
        """SELECT preTrashFolderId AS parentId, COUNT(*) AS childCount FROM files
           WHERE trashedAt IS NOT NULL AND preTrashFolderId IN (:folderIds)
           GROUP BY preTrashFolderId"""
    )
    suspend fun trashedFileCounts(folderIds: List<String>): List<TrashChildCount>

    @Query("SELECT * FROM files WHERE trashedAt IS NOT NULL AND trashedAt < :threshold")
    suspend fun trashOlderThan(threshold: Long): List<FileEntity>

    @Query("DELETE FROM files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM files WHERE folderId = :folderId AND trashedAt IS NULL")
    suspend fun filesInFolder(folderId: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE messageId IS NOT NULL")
    suspend fun filesWithRemote(): List<FileEntity>

    @Query(
        """SELECT f.* FROM files f
           WHERE f.id > :afterId AND f.trashedAt IS NULL AND
             ((f.chatId = :chatId AND (f.messageId IS NOT NULL OR f.partCount > 0)) OR
              (f.chatId IS NULL AND EXISTS (
                 SELECT 1 FROM file_parts p WHERE p.fileId = f.id AND p.chatId = :chatId)))
           ORDER BY f.id LIMIT :limit"""
    )
    suspend fun integrityPage(chatId: Long, afterId: String, limit: Int): List<FileEntity>

    @Query(
        """WITH RECURSIVE blocked(id) AS (
               SELECT id FROM folders WHERE isHidden = 1 AND NOT :includeHidden
               UNION
               SELECT f.id FROM folders f JOIN blocked b ON f.parentId = b.id
           ), eligible AS (
               SELECT f.id, f.name, f.folderId, p.name AS folderName, f.sizeBytes,
                      CASE
                        WHEN length(f.contentHash) = 64 AND f.contentHash NOT GLOB '*[^0-9a-fA-F]*'
                          THEN 'sha256:' || lower(f.contentHash)
                        WHEN length(f.remoteUniqueId) > 0 THEN 'telegram:' || f.remoteUniqueId
                      END AS identity
                 FROM files f LEFT JOIN folders p ON p.id = f.folderId AND p.chatId IS :chatId
                WHERE f.chatId IS :chatId AND f.trashedAt IS NULL AND f.sizeBytes > 0
                  AND (:includeHidden OR f.isHidden = 0)
                  AND (:includeNotes OR lower(f.name) NOT LIKE '%.md')
                  AND (f.folderId IS NULL OR f.folderId NOT IN (SELECT id FROM blocked))
           ), matches AS (
               SELECT identity, sizeBytes FROM eligible WHERE identity IS NOT NULL
               GROUP BY identity, sizeBytes HAVING COUNT(*) > 1
           )
           SELECT e.* FROM eligible e JOIN matches m
             ON e.identity = m.identity AND e.sizeBytes = m.sizeBytes
           ORDER BY e.sizeBytes DESC, e.identity, e.name COLLATE NOCASE, e.id"""
    )
    fun observeDuplicateFiles(
        chatId: Long?,
        includeHidden: Boolean,
        includeNotes: Boolean
    ): Flow<List<DuplicateFile>>

    @Query("SELECT localPath FROM files WHERE localPath IS NOT NULL")
    suspend fun allLocalPaths(): List<String>

    @Query("SELECT id FROM files WHERE importReference IS NOT NULL AND localPath IS NULL AND uploadRequested = 1 AND trashedAt IS NULL")
    suspend fun pendingImportIds(): List<String>

    @Query(
        """SELECT COALESCE(SUM(sizeBytes), 0) FROM files
           WHERE localPath IS NOT NULL
             AND messageId IS NOT NULL
             AND backupState = 'BACKED_UP' AND offlinePinned = 0
             AND chatId IS :chatId"""
    )
    fun observeReclaimableBytes(chatId: Long?): Flow<Long>

    @Query(
        """SELECT f.* FROM files f
           WHERE f.localPath IS NOT NULL AND f.messageId IS NOT NULL AND f.chatId = :chatId
             AND f.backupState = 'BACKED_UP' AND f.offlinePinned = 0 AND f.pendingPublish = 0
             AND f.trashedAt IS NULL
             AND NOT EXISTS (SELECT 1 FROM files other WHERE other.localPath = f.localPath AND other.id != f.id)
             AND NOT EXISTS (
                SELECT 1 FROM transfers t WHERE t.fileId = f.id AND t.state IN ('QUEUED','RUNNING','PAUSED'))
           ORDER BY f.sizeBytes, f.id"""
    )
    suspend fun cleanupCandidates(chatId: Long): List<FileEntity>

    @Query(
        """SELECT id, localPath FROM files
           WHERE localPath IS NOT NULL
             AND messageId IS NOT NULL
             AND backupState = 'BACKED_UP' AND offlinePinned = 0"""
    )
    suspend fun reclaimableLocalCopies(): List<LocalCopyRef>

    /**
     * Held only on this device: never uploaded, or canceled or failed on the
     * way up. A backup scan walks device folders and never sees these, so they
     * need queueing by id.
     */
    @Query(
        """SELECT id FROM files
           WHERE trashedAt IS NULL
             AND (localPath IS NOT NULL OR importReference IS NOT NULL OR sourceMessageId IS NOT NULL)
             AND uploadRequested = 1
             AND (messageId IS NULL OR lower(name) LIKE '%.md')
             AND backupState IN ('NONE', 'FAILED')
             AND chatId IS :chatId"""
    )
    suspend fun localOnlyFileIds(chatId: Long?): List<String>

    @Query(
        """SELECT id FROM files
           WHERE trashedAt IS NULL
             AND (messageId IS NULL OR lower(name) LIKE '%.md')
             AND backupState = 'QUEUED'
             AND chatId IS :chatId
             AND id NOT IN (
               SELECT fileId FROM transfers
               WHERE state IN ('QUEUED', 'RUNNING', 'PAUSED')
             )"""
    )
    suspend fun queuedWithoutTransferFileIds(chatId: Long?): List<String>

    @Query(
        """SELECT f.folderId AS folderId,
                  fo.name AS name,
                  COUNT(*) AS itemCount,
                  MAX(f.modifiedAt) AS latestAt,
                  (SELECT c.id FROM files c
                     WHERE c.folderId IS f.folderId AND c.chatId IS :chatId
                       AND c.trashedAt IS NULL
                       AND c.category IN ('IMAGE', 'VIDEO')
                       AND (:showHidden = 1 OR c.isHidden = 0)
                       AND (:showArchived = 1 OR c.isArchived = 0)
                     ORDER BY c.modifiedAt DESC, c.id ASC LIMIT 1) AS coverFileId,
                  (SELECT c.modifiedAt || ':' || COALESCE(c.localPath,'null') || ':' || COALESCE(c.remoteFileId,'null') || ':' || COALESCE(c.iconFileId,'null') || ':' || COALESCE(c.contentHash,'null')
                     FROM files c WHERE c.folderId IS f.folderId AND c.chatId IS :chatId
                       AND c.trashedAt IS NULL AND c.category IN ('IMAGE','VIDEO')
                       AND (:showHidden = 1 OR c.isHidden = 0) AND (:showArchived = 1 OR c.isArchived = 0)
                     ORDER BY c.modifiedAt DESC, c.id ASC LIMIT 1) AS coverRevision
           FROM files f
           LEFT JOIN folders fo ON fo.id = f.folderId
           WHERE f.trashedAt IS NULL AND f.chatId IS :chatId
             AND f.category IN ('IMAGE', 'VIDEO')
             AND (:showHidden = 1 OR f.isHidden = 0)
             AND (:showArchived = 1 OR f.isArchived = 0)
           GROUP BY f.folderId
           ORDER BY latestAt DESC"""
    )
    fun observeAlbumsScoped(
        showHidden: Boolean,
        showArchived: Boolean,
        chatId: Long?
    ): Flow<List<AlbumSummary>>

    @Query("SELECT * FROM files WHERE folderId IS :folderId AND chatId IS :chatId AND trashedAt IS NULL")
    suspend fun filesWithNamesInFolder(folderId: String?, chatId: Long?): List<FileEntity>

    @Query("SELECT name FROM files WHERE folderId IS :folderId AND trashedAt IS NULL")
    suspend fun namesInFolder(folderId: String?): List<String>

    @Query(
        """SELECT name FROM files
           WHERE folderId IS :folderId AND trashedAt IS NULL AND id != :excludeId"""
    )
    suspend fun namesInFolderExcluding(folderId: String?, excludeId: String): List<String>
}
