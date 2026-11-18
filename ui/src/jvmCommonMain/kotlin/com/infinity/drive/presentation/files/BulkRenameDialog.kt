package com.infinity.drive.presentation.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.domain.model.BulkRenamePreview
import com.infinity.drive.domain.model.BulkRenameStatus
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.presentation.common.resolve
import com.infinity.drive.presentation.common.toUiText
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.common_rename
import com.infinity.drive.resources.files_bulk_rename_conflict
import com.infinity.drive.resources.files_bulk_rename_help
import com.infinity.drive.resources.files_bulk_rename_invalid
import com.infinity.drive.resources.files_bulk_rename_preview
import com.infinity.drive.resources.files_bulk_rename_template
import com.infinity.drive.resources.files_bulk_rename_title
import com.infinity.drive.resources.files_bulk_rename_unchanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BulkRenameDialog(
    ids: List<String>,
    load: suspend (List<String>, String) -> AppResult<BulkRenamePreview>,
    apply: suspend (BulkRenamePreview) -> AppResult<Int>,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var template by remember { mutableStateOf("{name}") }
    var preview by remember { mutableStateOf<BulkRenamePreview?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var applying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }

    LaunchedEffect(ids, template, revision) {
        delay(180)
        when (val result = load(ids, template)) {
            is AppResult.Success -> preview = result.value
            is AppResult.Failure -> error = result.error.toUiText()
        }
    }

    val current = preview?.takeIf { it.template == template }
    AlertDialog(
        onDismissRequest = { if (!applying) onClose() },
        title = { Text(stringResource(Res.string.files_bulk_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.files_bulk_rename_help))
                OutlinedTextField(
                    value = template,
                    onValueChange = {
                        template = it
                        preview = null
                        error = null
                    },
                    label = { Text(stringResource(Res.string.files_bulk_rename_template)) },
                    singleLine = true,
                    enabled = !applying,
                    modifier = Modifier.fillMaxWidth()
                )
                if (applying || current == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error) }
                if (current != null) LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(current.rows, key = { it.id }) { row ->
                        Text(row.oldName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val changed = when (row.status) {
                            BulkRenameStatus.INVALID -> stringResource(Res.string.files_bulk_rename_invalid)
                            BulkRenameStatus.CONFLICT -> stringResource(Res.string.files_bulk_rename_conflict, row.newName)
                            BulkRenameStatus.UNCHANGED -> stringResource(Res.string.files_bulk_rename_unchanged)
                            BulkRenameStatus.READY -> stringResource(Res.string.files_bulk_rename_preview, row.newName)
                        }
                        val invalid = row.status == BulkRenameStatus.INVALID || row.status == BulkRenameStatus.CONFLICT
                        Text(
                            changed,
                            color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !applying && current?.canApply == true,
                onClick = {
                    if (current != null) scope.launch {
                        applying = true
                        error = null
                        try {
                            when (val result = apply(current)) {
                                is AppResult.Success -> onClose()
                                is AppResult.Failure -> {
                                    error = result.error.toUiText()
                                    preview = null
                                    revision++
                                }
                            }
                        } finally {
                            applying = false
                        }
                    }
                }
            ) { Text(stringResource(Res.string.common_rename)) }
        },
        dismissButton = {
            TextButton(enabled = !applying, onClick = onClose) {
                Text(stringResource(Res.string.common_cancel))
            }
        }
    )
}
