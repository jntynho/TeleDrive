// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.components

import app.tgdrive.ui.DriveIcons
import androidx.compose.ui.graphics.vector.ImageVector
import com.infinity.drive.domain.model.FileCategory

fun iconFor(category: FileCategory): ImageVector = when (category) {
    FileCategory.IMAGE -> DriveIcons.Image
    FileCategory.VIDEO -> DriveIcons.VideoFile
    FileCategory.AUDIO -> DriveIcons.AudioFile
    FileCategory.DOCUMENT -> DriveIcons.Description
    FileCategory.ARCHIVE -> DriveIcons.Archive
    FileCategory.OTHER -> DriveIcons.InsertDriveFile
}
