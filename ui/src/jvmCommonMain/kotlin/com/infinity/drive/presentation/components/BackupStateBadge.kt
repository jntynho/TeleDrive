// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.components

import app.tgdrive.ui.DriveIcons
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_backup_failed
import com.infinity.drive.resources.common_backup_not_backed_up
import com.infinity.drive.resources.common_backup_queued
import com.infinity.drive.resources.common_backup_uploading
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.DriveFile

/**
 * Backup state for files that still need attention. A file already in Telegram
 * draws nothing, since that is the normal state and a badge on every row would
 * carry no information.
 */
@Composable
fun BackupStateBadge(file: DriveFile, modifier: Modifier = Modifier) {
    if (file.backupState == BackupState.BACKED_UP) return

    val (icon, tint, label) = when (file.backupState) {
        BackupState.UPLOADING -> Triple(
            DriveIcons.CloudUpload,
            MaterialTheme.colorScheme.tertiary,
            stringResource(Res.string.common_backup_uploading)
        )

        BackupState.QUEUED -> Triple(
            DriveIcons.CloudQueue,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(Res.string.common_backup_queued)
        )

        BackupState.FAILED -> Triple(
            DriveIcons.ErrorOutline,
            MaterialTheme.colorScheme.error,
            stringResource(Res.string.common_backup_failed)
        )

        else -> Triple(
            DriveIcons.CloudOff,
            MaterialTheme.colorScheme.outline,
            stringResource(Res.string.common_backup_not_backed_up)
        )
    }
    Icon(
        imageVector = icon,
        contentDescription = label,
        modifier = modifier.size(18.dp),
        tint = tint
    )
}
