// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Screens whose implementation differs per platform, slotted into the nav host. */
interface PlatformScreens {

    @Composable
    fun PreviousLibrary(onBack: () -> Unit)

    @Composable
    fun Preview(onBack: () -> Unit, onEditNote: (String, String) -> Unit)
}

val LocalPlatformScreens = staticCompositionLocalOf<PlatformScreens> {
    error("PlatformScreens is not provided")
}
