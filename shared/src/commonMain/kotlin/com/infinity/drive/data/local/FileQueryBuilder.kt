package com.infinity.drive.data.local

import androidx.room.RoomRawQuery
import com.infinity.drive.data.local.FileQueryBuilder.build
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.model.FileQuerySpec
import com.infinity.drive.domain.model.FileSortField
import com.infinity.drive.domain.model.SortDirection

/**
 * Builds parameterized queries against the `files` table for the browser,
 * gallery, and search screens. Sorting cannot be expressed with Room's
 * compile-time queries without an explosion of variants, hence raw queries.
 */
object FileQueryBuilder {
    fun build(spec: FileQuerySpec): RoomRawQuery = query("*", spec)

    /** Same filter and order as [build], reading only the ids of every match. */
    fun buildIds(spec: FileQuerySpec): RoomRawQuery = query("id", spec)

    fun buildLimited(spec:FileQuerySpec,limit:Int):RoomRawQuery = query("*",spec,limit.coerceIn(1,400))

    fun buildWindow(spec:FileQuerySpec,offset:Int,limit:Int):RoomRawQuery = query("*",spec,limit.coerceIn(1,400),offset.coerceAtLeast(0))
    fun rank(spec:FileQuerySpec,file:DriveFile):RoomRawQuery {
        val column=when(spec.sortField){
            FileSortField.NAME->"name COLLATE NOCASE";FileSortField.DATE_MODIFIED->"modifiedAt";FileSortField.DATE_ADDED->"addedAt"
            FileSortField.SIZE->"sizeBytes";FileSortField.TYPE->"mimeType";FileSortField.BACKUP_STATUS->"backupState"
        }
        val value:Any=when(spec.sortField){
            FileSortField.NAME->file.name;FileSortField.DATE_MODIFIED->file.modifiedAt;FileSortField.DATE_ADDED->file.addedAt
            FileSortField.SIZE->file.sizeBytes;FileSortField.TYPE->file.mimeType;FileSortField.BACKUP_STATUS->file.backupState.name
        }
        val operator=if(spec.sortDirection==SortDirection.ASCENDING)"<"else">"
        return query("COUNT(*)",spec,extra=" AND ($column $operator ? OR ($column = ? AND id < ?))",extraArgs=listOf(value,value,file.id))
    }

    private fun query(projection: String, spec: FileQuerySpec,limit:Int?=null,offset:Int=0,extra:String="",extraArgs:List<Any> = emptyList()): RoomRawQuery {
        val where = StringBuilder("trashedAt IS NULL")
        val args = mutableListOf<Any>()

        val chatId = spec.chatId
        if (chatId != null) {
            where.append(" AND chatId = ?")
            args.add(chatId)
        } else if(spec.scopeToChat) {
            where.append(" AND chatId IS NULL")
        }

        val folderId = spec.folderId
        if (spec.filterByFolder) {
            if (folderId == null) {
                where.append(" AND folderId IS NULL")
            } else {
                where.append(" AND folderId = ?")
                args.add(folderId)
            }
        }
        if (spec.categories.isNotEmpty()) {
            where.append(" AND category IN (${spec.categories.joinToString(",") { "?" }})")
            args.addAll(spec.categories.map { it.name })
        }
        spec.nameQuery?.takeIf { it.isNotBlank() }?.let {
            where.append(" AND (name LIKE ? ESCAPE '\\' OR EXISTS (SELECT 1 FROM audio_tags a WHERE a.fileId = files.id AND a.modifiedAt = files.modifiedAt AND a.localPath IS files.localPath AND (a.title LIKE ? ESCAPE '\\' OR a.artist LIKE ? ESCAPE '\\')))")
            repeat(3){_->args.add("%${escapeLike(it)}%") }
        }
        spec.extension?.takeIf { it.isNotBlank() }?.let {
            where.append(" AND name LIKE ? ESCAPE '\\'")
            args.add("%.${escapeLike(it)}")
        }
        spec.minSizeBytes?.let {
            where.append(" AND sizeBytes >= ?")
            args.add(it)
        }
        spec.maxSizeBytes?.let {
            where.append(" AND sizeBytes <= ?")
            args.add(it)
        }
        spec.modifiedAfter?.let {
            where.append(" AND modifiedAt >= ?")
            args.add(it)
        }
        spec.modifiedBefore?.let {
            where.append(" AND modifiedAt <= ?")
            args.add(it)
        }
        if (spec.backedUpOnly) where.append(" AND backupState = 'BACKED_UP'")
        if (spec.notBackedUpOnly) where.append(" AND backupState != 'BACKED_UP'")
        if (spec.localCopiesOnly) where.append(" AND localPath IS NOT NULL")
        if (spec.favoritesOnly) where.append(" AND isFavorite = 1")
        if (spec.hiddenOnly) where.append(" AND isHidden = 1")
        if (spec.archivedOnly) where.append(" AND isArchived = 1")
        if (!spec.showHidden) where.append(" AND isHidden = 0")
        if (!spec.showArchived) where.append(" AND isArchived = 0")
        if (spec.excludeHiddenAncestors) where.append(" AND (folderId IS NULL OR folderId NOT IN (SELECT id FROM protected_folders))")

        val orderColumn = when (spec.sortField) {
            FileSortField.NAME -> "name COLLATE NOCASE"
            FileSortField.SIZE -> "sizeBytes"
            FileSortField.DATE_MODIFIED -> "modifiedAt"
            FileSortField.DATE_ADDED -> "addedAt"
            FileSortField.TYPE -> "mimeType"
            FileSortField.BACKUP_STATUS -> "backupState"
        }
        val direction = when (spec.sortDirection) {
            SortDirection.ASCENDING -> "ASC"
            SortDirection.DESCENDING -> "DESC"
        }

        where.append(extra);args.addAll(extraArgs)
        val prefix=if(spec.excludeHiddenAncestors) "WITH RECURSIVE protected_folders(id) AS (SELECT id FROM folders WHERE isHidden = 1 UNION SELECT f.id FROM folders f JOIN protected_folders p ON f.parentId = p.id) " else ""
        val sql = prefix+"SELECT $projection FROM files WHERE $where " +
                "ORDER BY $orderColumn $direction, id ASC" + (limit?.let{" LIMIT $it OFFSET $offset"}?:"")
        return RoomRawQuery(sql) { statement ->
            args.forEachIndexed { index, value ->
                when (value) {
                    is String -> statement.bindText(index + 1, value)
                    is Long -> statement.bindLong(index + 1, value)
                    is Int -> statement.bindLong(index + 1, value.toLong())
                    else -> error("Unsupported query argument: $value")
                }
            }
        }
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
