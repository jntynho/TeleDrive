// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.navigation

import app.tgdrive.ui.DriveIcons
import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.nav_audio
import com.infinity.drive.resources.nav_gallery
import com.infinity.drive.resources.nav_home
import com.infinity.drive.resources.nav_settings

enum class TopLevelDestination(
    val route: Route,
    val labelRes: StringResource,
    private val iconVector: @Composable () -> ImageVector
) {
    HOME(Route.Home, Res.string.nav_home, { DriveIcons.Home }),
    GALLERY(Route.Gallery(), Res.string.nav_gallery, { DriveIcons.Photo }),
    AUDIO(Route.Audio(true), Res.string.nav_audio, { DriveIcons.MusicNotes }),
    SETTINGS(Route.Settings, Res.string.nav_settings, { DriveIcons.Settings });

    val icon: ImageVector @Composable get() = iconVector()
}
