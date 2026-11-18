package com.infinity.drive.domain.model

data class FolderStatistics(
    val fileCount: Int,
    val subfolderCount: Int,
    val totalBytes: Long,
    val localCopyBytes: Long,
    val backedUpBytes: Long
)
