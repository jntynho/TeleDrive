package com.infinity.drive.presentation.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.domain.model.FileTags
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.presentation.common.resolve
import com.infinity.drive.presentation.common.toUiText
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.common_save
import com.infinity.drive.resources.files_tags_edit
import com.infinity.drive.resources.files_tags_edit_help
import com.infinity.drive.resources.files_tags_invalid
import com.infinity.drive.resources.files_tags_label
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EditFileTagsDialog(
    ids: List<String>,
    load: suspend (String) -> List<String>,
    save: suspend (List<String>, List<String>) -> AppResult<Int>,
    close: () -> Unit
) {
    var text by remember(ids) { mutableStateOf("") }
    var loading by remember(ids) { mutableStateOf(ids.size == 1) }
    var saving by remember(ids) { mutableStateOf(false) }
    var error by remember(ids) { mutableStateOf<UiText?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ids) {
        ids.singleOrNull()?.let { text = load(it).joinToString(", ") }
        loading = false
    }
    val tags = FileTags.parse(text)
    AlertDialog(
        onDismissRequest = { if (!saving) close() },
        title = { Text(stringResource(Res.string.files_tags_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.files_tags_edit_help))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.files_tags_label)) },
                    enabled = !loading && !saving,
                    isError = tags == null
                )
                if (tags == null) Text(stringResource(Res.string.files_tags_invalid), color = MaterialTheme.colorScheme.error)
                error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !loading && !saving && tags != null, onClick = {
                if (tags != null) scope.launch {
                    saving = true
                    try {
                        when (val result = save(ids, tags)) {
                            is AppResult.Success -> close()
                            is AppResult.Failure -> error = result.error.toUiText()
                        }
                    } finally {
                        saving = false
                    }
                }
            }) { Text(stringResource(Res.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = close, enabled = !saving) { Text(stringResource(Res.string.common_cancel)) }
        }
    )
}
