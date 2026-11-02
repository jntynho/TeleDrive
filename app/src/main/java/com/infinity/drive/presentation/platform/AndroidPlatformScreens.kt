// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.platform

import androidx.compose.runtime.Composable
import com.infinity.drive.presentation.preview.PreviewScreen

object AndroidPlatformScreens : PlatformScreens {

    @Composable
    override fun PreviousLibrary(onBack: () -> Unit) {
        app.tgdrive.migration.PreviousLibraryScreen(onBack)
    }

    @Composable
    override fun Preview(onBack: () -> Unit, onEditNote: (String, String) -> Unit) {
        app.tgdrive.ui.DriveTheme { PreviewScreen(onBack = onBack, onEditNote = onEditNote) }
    }
}
