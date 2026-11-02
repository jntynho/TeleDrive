// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.navigation

import app.tgdrive.ui.DriveIcons
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.graphics.vector.ImageVector
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.nav_files
import com.infinity.drive.resources.nav_audio
import com.infinity.drive.resources.nav_gallery
import com.infinity.drive.resources.nav_home
import com.infinity.drive.resources.nav_settings

enum class TopLevelDestination(
    val route: Route,
    val labelRes: StringResource,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME(Route.Home, Res.string.nav_home, DriveIcons.Home, DriveIcons.Home),
    AUDIO(Route.Audio(true), Res.string.nav_audio,DriveIcons.MusicNotes,DriveIcons.MusicNotes),
    GALLERY(Route.Gallery, Res.string.nav_gallery, DriveIcons.Photo, DriveIcons.Photo),
    SETTINGS(Route.Settings, Res.string.nav_settings, DriveIcons.Settings, DriveIcons.Settings)
}
