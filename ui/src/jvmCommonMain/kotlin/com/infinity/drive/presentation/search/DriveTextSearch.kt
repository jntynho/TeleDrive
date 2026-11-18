package com.infinity.drive.presentation.search

import com.infinity.drive.domain.model.DriveFile

interface DriveTextSearch {
    val supportsPdf: Boolean

    suspend fun search(query: String, candidates: List<DriveFile>): ContentMatches
}

data class ContentMatches(val files: List<DriveFile>, val skipped: Int)
