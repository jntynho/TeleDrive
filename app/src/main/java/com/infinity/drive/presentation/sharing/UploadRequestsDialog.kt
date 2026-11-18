package com.infinity.drive.presentation.sharing

import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tgdrive.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun UploadRequestsDialog(client: AndroidPublicLinks, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var requests by remember { mutableStateOf<List<UploadRequest>>(emptyList()) }
    var hours by remember { mutableStateOf("24") }
    var megabytes by remember { mutableStateOf("50") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Set<String>>(emptySet()) }
    var revoke by remember { mutableStateOf<UploadRequest?>(null) }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try {
                action()
                requests = client.requests()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(Unit) { run {} }

    val limit = megabytes.toIntOrNull()?.takeIf { it in 1..512 }
    val duration = hours.toIntOrNull()?.takeIf { it in 1..168 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.upload_requests_title)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.upload_requests_explainer))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.live_links_failure))
                OutlinedTextField(
                    hours, { hours = it.filter(Char::isDigit).take(3) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy,
                    label = { Text(stringResource(R.string.live_links_duration)) }
                )
                OutlinedTextField(
                    megabytes, { megabytes = it.filter(Char::isDigit).take(3) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy,
                    label = { Text(stringResource(R.string.upload_requests_maximum)) }
                )
                Row {
                    TextButton(
                        enabled = !busy && limit != null && duration != null,
                        onClick = { if (limit != null && duration != null) run { client.newRequest(duration, limit) } }
                    ) { Text(stringResource(R.string.upload_requests_create)) }
                    TextButton(onClick = { run {} }, enabled = !busy) { Text(stringResource(R.string.live_links_refresh)) }
                }
                for (entry in requests) {
                    val incoming = entry.incoming
                    Text(incoming?.name ?: stringResource(R.string.upload_requests_waiting))
                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.expiresAt)))
                    Row {
                        if (incoming == null) {
                            TextButton(enabled = !busy, onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("TeleDrive", entry.url))
                            }) { Text(stringResource(R.string.live_links_copy)) }
                        } else {
                            TextButton(enabled = !busy && entry.id !in saved, onClick = {
                                run {
                                    client.accept(entry)
                                    saved = saved + entry.id
                                }
                            }) {
                                Text(stringResource(if (entry.id in saved) R.string.upload_requests_saved else R.string.upload_requests_save))
                            }
                        }
                        TextButton(onClick = { revoke = entry }, enabled = !busy) {
                            Text(stringResource(R.string.live_links_revoke))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } }
    )
    revoke?.let { item ->
        AlertDialog(
            onDismissRequest = { revoke = null },
            title = { Text(stringResource(R.string.live_links_revoke)) },
            text = { Text(stringResource(R.string.upload_requests_revoke)) },
            confirmButton = {
                TextButton(onClick = {
                    revoke = null
                    run { client.closeRequest(item.id) }
                }) { Text(stringResource(R.string.live_links_revoke)) }
            },
            dismissButton = { TextButton({ revoke = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}
