package com.infinity.drive.presentation.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tgdrive.R
import com.infinity.drive.domain.model.DriveFile
import kotlinx.coroutines.CancellationException

@Composable
internal fun TextRecognitionDialog(
    source: DriveFile,
    recognizer: AndroidTextRecognition,
    onCreateNote: (String, String) -> Unit,
    dismiss: () -> Unit
) {
    var extracted by remember(source.id) { mutableStateOf<RecognizedText?>(null) }
    var text by remember(source.id) { mutableStateOf("") }
    var failed by remember(source.id) { mutableStateOf(false) }
    LaunchedEffect(source.id, source.modifiedAt) {
        try {
            val result = recognizer.extract(source)
            text = result.text
            extracted = result
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            failed = true
        }
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.extract_text)) },
        text = {
            Column {
                when {
                    failed -> Text(stringResource(R.string.extract_text_failed))
                    extracted == null -> {
                        Text(stringResource(R.string.extract_text_working), Modifier.padding(bottom = 12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    else -> {
                        if (extracted?.truncated == true) {
                            Text(stringResource(R.string.extract_text_partial), Modifier.padding(bottom = 8.dp))
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.take(32_000) },
                            label = { Text(stringResource(R.string.extract_text_result)) },
                            supportingText = {
                                if (extracted?.text.isNullOrBlank()) Text(stringResource(R.string.extract_text_empty))
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 270.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (extracted != null) {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = { onCreateNote(source.name.substringBeforeLast('.'), text) }
                ) { Text(stringResource(R.string.extract_text_note)) }
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}
