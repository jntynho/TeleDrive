package com.infinity.drive.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.infinity.drive.data.repository.DriveIntegrityChecker
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.presentation.common.resolve
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.drive_integrity_details
import com.infinity.drive.resources.drive_integrity_hint
import com.infinity.drive.resources.drive_integrity_problems
import com.infinity.drive.resources.drive_integrity_progress
import com.infinity.drive.resources.drive_integrity_sample
import com.infinity.drive.resources.drive_integrity_start
import com.infinity.drive.resources.drive_integrity_title
import com.infinity.drive.resources.library_restore_close
import org.jetbrains.compose.resources.stringResource

internal data class IntegrityUiState(
    val running: Boolean = false,
    val report: DriveIntegrityChecker.Report? = null,
    val complete: Boolean = false,
    val error: UiText? = null
)

@Composable
internal fun DriveIntegrityDialog(
    state: IntegrityUiState,
    onStart: (Boolean) -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    var sample by rememberSaveable { mutableStateOf(false) }
    val report = state.report
    AlertDialog(
        onDismissRequest = { if (!state.running) onClose() },
        title = { Text(stringResource(Res.string.drive_integrity_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!state.running && !state.complete) {
                    Text(stringResource(Res.string.drive_integrity_hint))
                    Row(
                        Modifier.fillMaxWidth().toggleable(
                            value = sample, role = Role.Checkbox, onValueChange = { sample = it }
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = sample, onCheckedChange = null, modifier = Modifier.size(32.dp))
                        Text(stringResource(Res.string.drive_integrity_sample))
                    }
                }
                if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error) }
                if (report != null) {
                    Text(stringResource(
                        Res.string.drive_integrity_progress,
                        report.checked, report.available, report.missing, report.inconsistent
                    ))
                    Text(stringResource(
                        Res.string.drive_integrity_details,
                        report.unverified, report.locked, report.samplesVerified
                    ))
                    if (report.problemNames.isNotEmpty()) {
                        Text(stringResource(Res.string.drive_integrity_problems), style = MaterialTheme.typography.titleSmall)
                        report.problemNames.forEach { Text(it) }
                    }
                }
            }
        },
        confirmButton = {
            when {
                state.running -> TextButton(onClick = onStop) {
                    Text(stringResource(Res.string.common_cancel))
                }
                state.complete -> TextButton(onClick = onClose) {
                    Text(stringResource(Res.string.library_restore_close))
                }
                else -> TextButton(onClick = { onStart(sample) }) {
                    Text(stringResource(Res.string.drive_integrity_start))
                }
            }
        },
        dismissButton = {
            if (!state.running && !state.complete) TextButton(onClick = onClose) {
                Text(stringResource(Res.string.library_restore_close))
            }
        }
    )
}
