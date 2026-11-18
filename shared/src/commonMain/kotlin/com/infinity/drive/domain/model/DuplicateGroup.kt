package com.infinity.drive.domain.model

data class DuplicateGroup(
    val identity: String,
    val sizeBytes: Long,
    val copies: List<DuplicateFile>
)

data class DuplicateFile(
    val id: String,
    val name: String,
    val folderId: String?,
    val folderName: String?,
    val identity: String,
    val sizeBytes: Long
)
