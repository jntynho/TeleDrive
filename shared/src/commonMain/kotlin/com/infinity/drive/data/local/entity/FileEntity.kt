package com.infinity.drive.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.FileCategory

@Entity(
    tableName = "files",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value=["chatId","trashedAt","modifiedAt","id","category"],orders=[Index.Order.ASC,Index.Order.ASC,Index.Order.DESC,Index.Order.ASC,Index.Order.ASC],name="index_files_gallery"),
        Index("folderId"),
        Index("name"),
        Index("category"),
        Index("backupState"),
        Index("trashedAt"),
        Index("remoteUniqueId"),
        Index(value = ["localPath"], unique = false),
        Index(value = ["contentHash"]),
        Index("pendingPublish")
    ]
)
data class FileEntity(
    @PrimaryKey val id: String,
    val folderId: String?,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val category: FileCategory,
    val localPath: String?,
    val contentHash: String?,
    val chatId: Long?,
    val messageId: Long?,
    val remoteFileId: String?,
    val remoteUniqueId: String?,
    val importReference:String? = null,
    @androidx.room.ColumnInfo(defaultValue = "1") val uploadRequested:Boolean = true,
    val sourceChatId: Long? = null,
    val sourceMessageId: Long? = null,
    val backupState: BackupState,
    val isHidden: Boolean = false,
    val isArchived: Boolean = false,
    val isFavorite: Boolean = false,
    val isEncrypted: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0") val offlinePinned: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val trashedAt: Long? = null,
    val preTrashFolderId: String? = null,
    val pendingPublish: Boolean = false,
    val partCount: Int = 0,
    val iconFileId: String? = null,
    val createdAt: Long,
    val modifiedAt: Long,
    val addedAt: Long
)
