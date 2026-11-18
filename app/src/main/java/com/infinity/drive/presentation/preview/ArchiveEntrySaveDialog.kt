package com.infinity.drive.presentation.preview

import android.content.ActivityNotFoundException
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.infinity.drive.core.files.MimeTypes
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.library_restore_close
import com.infinity.drive.resources.preview_zip_entry_error
import com.infinity.drive.resources.preview_zip_entry_password
import com.infinity.drive.resources.preview_zip_entry_save
import com.infinity.drive.resources.preview_zip_entry_saved
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun ArchiveEntrySaveDialog(
    archive: PreviewContent.Archive,
    entry: PreviewContent.ArchiveEntry,
    onClose: () -> Unit
) {
    val contentResolver = LocalContext.current.contentResolver
    val hidden = koinInject<HiddenContentAccess>()
    val scope = rememberCoroutineScope()
    val name = remember(entry) { ZipEntryReader.suggestedName(entry.name) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val target = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MimeTypes.fromFileName(name))
    ) { uri ->
        if (uri == null) {
            busy = false
        } else scope.launch {
            val secret = if (entry.isEncrypted) password.toCharArray() else null
            password = ""
            exported = withContext(Dispatchers.IO) {
                var complete = false
                try {
                    val output = contentResolver.openOutputStream(uri, "wt") ?: error("File unavailable")
                    output.buffered().use {
                        ZipEntryReader.copy(archive, entry, secret, it) { hidden.canRead(archive.sourceFileId) }
                    }
                    complete = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    complete = false
                } finally {
                    secret?.fill('\u0000')
                    if (!complete) runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
                }
                complete
            }
            busy = false
            failed = !exported
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Formatters.bytes(entry.sizeBytes))
                if (entry.isEncrypted && !exported) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        enabled = !busy,
                        label = { Text(stringResource(Res.string.preview_zip_entry_password)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(
                    stringResource(Res.string.preview_zip_entry_error),
                    color = MaterialTheme.colorScheme.error
                )
                if (exported) Text(stringResource(Res.string.preview_zip_entry_saved))
            }
        },
        confirmButton = {
            if (exported) TextButton(onClick = onClose) {
                Text(stringResource(Res.string.library_restore_close))
            } else TextButton(
                enabled = !busy && (!entry.isEncrypted || password.isNotEmpty()),
                onClick = {
                    failed = false
                    busy = true
                    try {
                        target.launch(name)
                    } catch (_: ActivityNotFoundException) {
                        busy = false
                        failed = true
                    }
                }
            ) { Text(stringResource(Res.string.preview_zip_entry_save)) }
        },
        dismissButton = {
            if (!busy && !exported) {
                TextButton(onClick = onClose) { Text(stringResource(Res.string.common_cancel)) }
            }
        }
    )
}
