package com.infinity.drive.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupFolderRule(
    val chatId: Long,
    val path: String,
    val includeSubfolders: Boolean = true,
    val mediaOnly: Boolean = false,
    /** Null inherits the drive's limit. Zero allows any size. */
    val maximumSizeMb: Int? = null
)
