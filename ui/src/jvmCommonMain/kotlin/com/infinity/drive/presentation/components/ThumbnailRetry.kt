package com.infinity.drive.presentation.components

import androidx.compose.runtime.staticCompositionLocalOf

/** One shared connectivity subscription; a bounded retry is reset when connectivity returns. */
val LocalThumbnailRetryEpoch = staticCompositionLocalOf { 0 }
