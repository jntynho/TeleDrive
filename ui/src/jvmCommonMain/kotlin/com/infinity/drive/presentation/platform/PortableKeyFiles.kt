package com.infinity.drive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

interface PortableKeyFiles {
    fun save(bytes: ByteArray, onResult: (PortableKeyFileResult<Unit>) -> Unit)
    fun open(onResult: (PortableKeyFileResult<ByteArray>) -> Unit)
}

sealed interface PortableKeyFileResult<out T> {
    data class Success<T>(val value: T) : PortableKeyFileResult<T>
    data object Cancelled : PortableKeyFileResult<Nothing>
    data object Failed : PortableKeyFileResult<Nothing>
}

val LocalPortableKeyFiles = staticCompositionLocalOf<PortableKeyFiles?> { null }
