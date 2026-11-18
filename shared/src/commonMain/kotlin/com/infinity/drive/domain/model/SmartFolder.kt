package com.infinity.drive.domain.model

import kotlinx.serialization.Serializable

/** A drive-specific shortcut to a search; membership is evaluated when it opens. */
@Serializable
data class SmartFolder(
    val id: String,
    val chatId: Long,
    val name: String,
    val query: String,
    val category: FileCategory? = null,
    val backedUpOnly: Boolean = false,
    val notBackedUpOnly: Boolean = false,
    val minimumSizeMb: Int? = null,
    val sortField: FileSortField = FileSortField.DATE_MODIFIED,
    val sortDirection: SortDirection = SortDirection.DESCENDING
)
