package com.infinity.drive.domain.model

data class BulkRenameEntry(
    val id: String,
    val oldName: String,
    val newName: String,
    val status: BulkRenameStatus
)

enum class BulkRenameStatus { READY, UNCHANGED, INVALID, CONFLICT }

data class BulkRenamePreview(
    val template: String,
    val rows: List<BulkRenameEntry>
) {
    val canApply: Boolean
        get() = rows.any { it.status == BulkRenameStatus.READY } &&
            rows.none { it.status == BulkRenameStatus.INVALID || it.status == BulkRenameStatus.CONFLICT }
}
