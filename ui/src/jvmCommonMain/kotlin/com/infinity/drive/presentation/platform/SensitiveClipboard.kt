package com.infinity.drive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

fun interface SensitiveClipboard { fun copy(text:String) }
val LocalSensitiveClipboard=staticCompositionLocalOf<SensitiveClipboard?>{null}
