package com.infinity.drive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.infinity.drive.core.common.AppResult

fun interface FolderArchiveDocument {
    fun save(folderId: String?, onProgress: (Int) -> Unit, onResult: (AppResult<Int>?) -> Unit)
}

val LocalFolderArchiveDocument = staticCompositionLocalOf<FolderArchiveDocument?> { null }
