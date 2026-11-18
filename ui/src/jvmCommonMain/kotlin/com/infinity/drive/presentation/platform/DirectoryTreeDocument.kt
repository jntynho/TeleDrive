package com.infinity.drive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.infinity.drive.core.common.AppResult

fun interface DirectoryTreeDocument {
    fun save(
        write: suspend ((String) -> Unit) -> AppResult<Int>,
        onResult: (AppResult<Int>?) -> Unit
    )
}

val LocalDirectoryTreeDocument = staticCompositionLocalOf<DirectoryTreeDocument?> { null }
