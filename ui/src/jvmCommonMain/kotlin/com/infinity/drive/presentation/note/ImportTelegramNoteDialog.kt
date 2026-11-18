package com.infinity.drive.presentation.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.core.telegram.TelegramAuthState
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramException
import com.infinity.drive.core.telegram.TelegramMessageLink
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.note_import_telegram
import com.infinity.drive.resources.note_import_telegram_action
import com.infinity.drive.resources.note_import_telegram_error
import com.infinity.drive.resources.note_import_telegram_invalid
import com.infinity.drive.resources.note_import_telegram_link
import com.infinity.drive.resources.note_import_telegram_unavailable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun ImportTelegramNoteDialog(onClose: () -> Unit, onImport: (String) -> Unit) {
    val telegram = koinInject<TelegramClient>()
    val access = koinInject<HiddenContentAccess>()
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<StringResource?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.note_import_telegram)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = link,
                    onValueChange = {
                        link = it
                        error = null
                    },
                    label = { Text(stringResource(Res.string.note_import_telegram_link)) },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = link.isNotBlank() && !loading,
                onClick = {
                    val target = TelegramMessageLink.normalize(link)
                    if (target == null) {
                        error = Res.string.note_import_telegram_invalid
                    } else scope.launch {
                        error = null
                        loading = true
                        try {
                            val text = telegram.messageMarkdown(target)
                            val permitted = telegram.authState.value == TelegramAuthState.Ready &&
                                access.notesLock?.state?.value?.restricted != true
                            if (!text.isNullOrBlank() && permitted) onImport(text)
                            else error = Res.string.note_import_telegram_unavailable
                        } catch (_: TelegramException) {
                            error = Res.string.note_import_telegram_error
                        } finally {
                            loading = false
                        }
                    }
                }
            ) { Text(stringResource(Res.string.note_import_telegram_action)) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(Res.string.common_cancel)) }
        }
    )
}
