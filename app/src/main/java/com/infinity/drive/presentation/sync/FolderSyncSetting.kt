package com.infinity.drive.presentation.sync

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import app.tgdrive.R
import com.infinity.drive.presentation.settings.SettingsClickRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun FolderSyncSetting() {
    var open by remember { mutableStateOf(false) }
    SettingsClickRow(stringResource(R.string.folder_sync_title), { open = true },
        subtitle = stringResource(R.string.folder_sync_subtitle))
    if (open) FolderSyncDialog { open = false }
}

@Composable
private fun FolderSyncDialog(close: () -> Unit) {
    val context = LocalContext.current
    val service = koinInject<AndroidFolderSync>()
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var folder by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(SyncDirection.BOTH) }
    var plan by remember { mutableStateOf<SyncPlan?>(null) }
    var done by remember { mutableStateOf<SyncResult?>(null) }
    var running by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { chosen ->
        if (chosen != null) {
            uri = chosen
            if (folder.isBlank()) folder = DocumentFile.fromTreeUri(context, chosen)?.name.orEmpty()
            plan = null
            done = null
        }
    }

    fun run(operation: suspend () -> Unit) {
        running = true
        failed = false
        scope.launch {
            try { operation() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failed = true }
            finally { running = false }
        }
    }

    val pending = plan
    val changes = pending?.entries?.filterNot { it.action == SyncAction.MATCH || it.action == SyncAction.SKIP }.orEmpty()
    val uploads = changes.count { it.action == SyncAction.UPLOAD }
    val downloads = changes.count { it.action == SyncAction.DOWNLOAD }
    AlertDialog(
        onDismissRequest = { if (!running) close() },
        title = { Text(stringResource(R.string.folder_sync_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.folder_sync_failed))
                val report = done
                if (report != null) Text(stringResource(R.string.folder_sync_result, report.uploaded, report.downloaded, report.failed))
                if (pending == null) {
                    Text(stringResource(R.string.folder_sync_description))
                    TextButton({
                        picker.launch(uri)
                    }, enabled = !running) {
                        Text(uri?.let { DocumentFile.fromTreeUri(context, it)?.name }
                            ?: stringResource(R.string.folder_sync_choose))
                    }
                    OutlinedTextField(folder, { folder = it.take(180) }, singleLine = true, enabled = !running,
                        label = { Text(stringResource(R.string.folder_sync_cloud_folder)) })
                    Column {
                        SyncDirection.entries.forEach { mode ->
                            FilterChip(
                                selected = direction == mode, enabled = !running,
                                onClick = { direction = mode },
                                label = { Text(stringResource(when (mode) {
                                    SyncDirection.UPLOAD -> R.string.folder_sync_upload
                                    SyncDirection.DOWNLOAD -> R.string.folder_sync_download
                                    SyncDirection.BOTH -> R.string.folder_sync_both
                                })) }
                            )
                        }
                    }
                } else {
                    Text(stringResource(R.string.folder_sync_preview, uploads, downloads, changes.size - uploads - downloads))
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(changes, key = { it.path }) { item ->
                            ListItem(headlineContent = { Text(item.path) }, supportingContent = {
                                Text(stringResource(when (item.action) {
                                    SyncAction.UPLOAD -> R.string.folder_sync_upload
                                    SyncAction.DOWNLOAD -> R.string.folder_sync_download
                                    SyncAction.WAITING -> R.string.folder_sync_waiting
                                    else -> R.string.folder_sync_conflict
                                }))
                            })
                        }
                    }
                    TextButton({ plan = null }, enabled = !running) {
                        Text(stringResource(R.string.workspaces_up))
                    }
                }
            }
        },
        confirmButton = {
            if (pending == null) TextButton({
                val location = uri ?: return@TextButton
                run {
                    done = null
                    plan = service.preview(location, folder.trim(), direction)
                }
            }, enabled = !running && uri != null && com.infinity.drive.core.files.FileNameUtils.isValidName(folder)) {
                Text(stringResource(R.string.folder_sync_review))
            } else TextButton({
                run {
                    done = service.apply(pending)
                    plan = null
                }
            }, enabled = !running) { Text(stringResource(R.string.folder_sync_apply)) }
        },
        dismissButton = { TextButton(close, enabled = !running) { Text(stringResource(android.R.string.cancel)) } }
    )
}
