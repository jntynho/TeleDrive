// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Screens whose implementation differs per platform, slotted into the nav host. */
interface PlatformScreens {

    @Composable
    fun PreviousLibrary(onBack: () -> Unit)

    val supportsRecoveryImport: Boolean get() = false

    @Composable
    fun RecoveryImport(
        onBack: () -> Unit,
        onImport: (com.infinity.drive.core.telegram.TelegramLoginRecovery) -> Unit
    ) = Unit

    @Composable
    fun RecoveryExport(onBack: () -> Unit) = Unit

    @Composable
    fun AccountProfilesSetting(compact: Boolean = false) = Unit

    @Composable
    fun WorkspacesSetting() = Unit

    @Composable
    fun FolderSyncSetting() = Unit

    val supportsPublicLinks: Boolean get() = false

    @Composable
    fun PublicLinksSetting() = Unit

    @Composable
    fun PhotoHistoryControl(onAlbum: Boolean, folder: String?, query: String, openFile: (String) -> Unit) = Unit

    @Composable
    fun Preview(onBack: () -> Unit, onEditNote: (String, String) -> Unit, onCreateNote: (String, String) -> Unit)
}

val LocalPlatformScreens = staticCompositionLocalOf<PlatformScreens> {
    error("PlatformScreens is not provided")
}
