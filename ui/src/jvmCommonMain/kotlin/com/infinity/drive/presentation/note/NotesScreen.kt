package com.infinity.drive.presentation.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.ExpandableLibraryHeader
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.TransferRepository
import com.infinity.drive.domain.repository.TrashRepository
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.components.ConfirmDialog
import com.infinity.drive.presentation.navigation.LocalBottomBarInset
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_back
import com.infinity.drive.resources.notes_create
import com.infinity.drive.resources.notes_empty
import com.infinity.drive.resources.notes_local_only
import com.infinity.drive.resources.notes_search
import com.infinity.drive.resources.notes_search_empty
import com.infinity.drive.resources.notes_settings
import com.infinity.drive.resources.notes_title
import com.infinity.drive.resources.notes_upload_done
import com.infinity.drive.resources.notes_upload_failed
import com.infinity.drive.resources.notes_upload_waiting
import com.infinity.drive.resources.notes_uploading
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun NotesScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (DriveFile) -> Unit,
    onSettings: () -> Unit
) {
    val files = koinInject<FileRepository>()
    val transfers = koinInject<TransferRepository>()
    val trash = koinInject<TrashRepository>()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var pendingTrash by remember { mutableStateOf<DriveFile?>(null) }
    val notes by remember(files) {
        files.observeFiles(
            FileQuerySpec(
                extension = "md",
                showArchived = true,
                sortField = FileSortField.DATE_MODIFIED,
                sortDirection = SortDirection.DESCENDING
            )
        )
    }.collectAsStateWithLifecycle(initialValue = null)
    val matching = notes?.filter { it.name.contains(query.trim(), ignoreCase = true) }

    com.infinity.drive.presentation.common.AppBackHandler(searching) {
        searching = false
        query = ""
    }
    pendingTrash?.let { note ->
        ConfirmDialog(
            title = "Move note to Trash?",
            message = note.name,
            confirmLabel = "Move to Trash",
            destructive = true,
            onConfirm = {
                pendingTrash = null
                scope.launch {
                    if (trash.moveFilesToTrash(listOf(note.id)) is AppResult.Failure) {
                        snackbar.showSnackbar("Couldn’t move the note to Trash.")
                    }
                }
            },
            onDismiss = { pendingTrash = null }
        )
    }

    Scaffold(
        topBar = {
            ExpandableLibraryHeader(
                stringResource(Res.string.notes_title), query, { query = it }, searching, { searching = it },
                stringResource(Res.string.notes_search),
                navigationIcon = { IconButton(onBack) { Icon(DriveIcons.ArrowBack, stringResource(Res.string.common_back)) } },
                actions = { IconButton(onSettings) { Icon(DriveIcons.Settings, stringResource(Res.string.notes_settings)) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onNew, modifier = Modifier.padding(bottom = LocalBottomBarInset.current)) {
                Icon(DriveIcons.EditNote, stringResource(Res.string.notes_create))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            matching == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(padding))
            matching.isEmpty() -> Text(
                stringResource(if (query.isBlank()) Res.string.notes_empty else Res.string.notes_search_empty),
                Modifier.padding(padding).padding(24.dp)
            )
            else -> LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + 88.dp)
            ) {
                items(matching, key = { it.id }) { note ->
                    val status = when (note.backupState) {
                        BackupState.BACKED_UP -> stringResource(Res.string.notes_upload_done)
                        BackupState.QUEUED -> stringResource(Res.string.notes_upload_waiting)
                        BackupState.UPLOADING -> stringResource(Res.string.notes_uploading)
                        BackupState.FAILED -> stringResource(Res.string.notes_upload_failed)
                        BackupState.NONE -> stringResource(Res.string.notes_local_only)
                    }
                    var excerpt by remember(note.id, note.modifiedAt, note.localPath) { mutableStateOf("") }
                    LaunchedEffect(note.id, note.modifiedAt, note.localPath) {
                        if (note.localPath != null) {
                            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                files.readNote(note.id)
                            }
                            if (result is AppResult.Success) {
                                excerpt = com.infinity.drive.core.files.Markdown.plain(
                                    result.value.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(160)
                                )
                            }
                        }
                    }
                    ListItem(
                        headlineContent = { Text(note.name.substringBeforeLast('.')) },
                        supportingContent = {
                            Column {
                                if (excerpt.isNotBlank()) {
                                    Text(excerpt, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                Text("${Formatters.date(note.modifiedAt)} · $status")
                            }
                        },
                        leadingContent = { Icon(DriveIcons.EditNote, null) },
                        trailingContent = {
                            Row {
                                if (note.backupState in setOf(BackupState.NONE, BackupState.FAILED)) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            if (transfers.enqueueUpload(note.id) is AppResult.Failure) {
                                                snackbar.showSnackbar("Couldn’t queue this note for upload.")
                                            }
                                        }
                                    }) { Text("Upload") }
                                }
                                IconButton({ pendingTrash = note }) { Icon(DriveIcons.Delete, "Move to Trash") }
                            }
                        },
                        modifier = Modifier.clickable { onEdit(note) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
