package com.infinity.drive.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinity.drive.domain.model.BackupDecision
import com.infinity.drive.domain.repository.BackupRepository
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.presentation.common.resolve
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.backup_preview_duplicates
import com.infinity.drive.resources.backup_preview_excluded
import com.infinity.drive.resources.backup_preview_large
import com.infinity.drive.resources.backup_preview_scanning
import com.infinity.drive.resources.backup_preview_small
import com.infinity.drive.resources.backup_preview_start
import com.infinity.drive.resources.backup_preview_title
import com.infinity.drive.resources.backup_preview_unchanged
import com.infinity.drive.resources.backup_preview_unreadable
import com.infinity.drive.resources.backup_preview_uploads
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.library_restore_close
import org.jetbrains.compose.resources.stringResource

internal data class BackupPreviewState(
    val running: Boolean = false,
    val report: BackupRepository.BackupPreview? = null,
    val error: UiText? = null
)

@Composable
internal fun BackupPreviewDialog(
    state: BackupPreviewState,
    onClose: () -> Unit,
    onStart: () -> Unit
) {
    val report = state.report
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.backup_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.running) {
                    Text(stringResource(Res.string.backup_preview_scanning))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                state.error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error) }
                if (report != null) {
                    Text(
                        stringResource(Res.string.backup_preview_uploads, report.uploads, Formatters.bytes(report.uploadBytes)),
                        style = MaterialTheme.typography.titleMedium
                    )
                    for ((decision, count) in report.skips.entries.sortedBy { it.key.ordinal }) {
                        val label = when (decision) {
                            BackupDecision.SKIP_UNCHANGED -> Res.string.backup_preview_unchanged
                            BackupDecision.SKIP_EXCLUDED -> Res.string.backup_preview_excluded
                            BackupDecision.SKIP_TOO_LARGE -> Res.string.backup_preview_large
                            BackupDecision.SKIP_TOO_SMALL -> Res.string.backup_preview_small
                            BackupDecision.SKIP_DUPLICATE -> Res.string.backup_preview_duplicates
                            BackupDecision.BACKUP -> continue
                        }
                        Text(stringResource(label, count))
                    }
                    if (report.unreadableFolders > 0) {
                        Text(
                            stringResource(Res.string.backup_preview_unreadable, report.unreadableFolders),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (report != null && report.uploads > 0) {
                TextButton(onClick = onStart) { Text(stringResource(Res.string.backup_preview_start)) }
            } else {
                TextButton(onClick = onClose) {
                    Text(stringResource(if (state.running) Res.string.common_cancel else Res.string.library_restore_close))
                }
            }
        },
        dismissButton = {
            if (report != null && report.uploads > 0) {
                TextButton(onClick = onClose) { Text(stringResource(Res.string.library_restore_close)) }
            }
        }
    )
}
