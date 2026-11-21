package com.infinity.drive.presentation.note

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.ExpandableLibraryHeader
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.files.Markdown
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.data.repository.ActiveChannel
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.TransferRepository
import com.infinity.drive.domain.repository.TrashRepository
import com.infinity.drive.presentation.common.AppBackHandler
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.components.ConfirmDialog
import com.infinity.drive.presentation.components.PageHeader
import com.infinity.drive.presentation.navigation.LocalBottomBarInset
import com.infinity.drive.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.abs

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
    val hiddenAccess = koinInject<HiddenContentAccess>()
    val activeChannel = koinInject<ActiveChannel>()
    val chatId by remember(activeChannel) { activeChannel.observe() }
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val offlineError = stringResource(Res.string.notes_offline_failed)
    val uploadError = stringResource(Res.string.notes_upload_queue_failed)
    val trashError = stringResource(Res.string.notes_trash_failed)
    val backIconModifier = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        Modifier.graphicsLayer { scaleX = -1f }
    } else Modifier
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember(chatId) { mutableStateOf(emptySet<String>()) }
    var pendingTrash by remember(chatId) { mutableStateOf(emptyList<String>()) }
    var updatingIds by remember { mutableStateOf(emptySet<String>()) }
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
    val visibleNotes = remember(notes, chatId) { notes?.filter { it.chatId == chatId } }
    val matching = remember(visibleNotes, query) {
        visibleNotes?.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val matchingIds = remember(matching) { matching.orEmpty().mapTo(mutableSetOf()) { it.id } }
    val selection = selectedIds.intersect(matchingIds)
    val selectionMode = selection.isNotEmpty()
    val allSelected = selectionMode && selection.size == matchingIds.size

    LaunchedEffect(matchingIds) { selectedIds = selectedIds.intersect(matchingIds) }

    fun toggleSelection(id: String) {
        selectedIds = if (id in selection) selection - id else selection + id
    }

    AppBackHandler(searching || selectionMode) {
        if (selectionMode) {
            selectedIds = emptySet()
        } else {
            searching = false
            query = ""
        }
    }

    suspend fun requireCurrentNote(id: String): DriveFile {
        val current = requireNotNull(files.getFile(id))
        check(!current.isTrashed && current.chatId == activeChannel.id())
        check(hiddenAccess.canRead(id))
        return current
    }

    fun runNoteAction(ids: List<String>, error: String, action: suspend () -> Unit) {
        if (ids.any { it in updatingIds }) return
        updatingIds = updatingIds + ids
        scope.launch {
            val failed = try {
                withContext(Dispatchers.IO) { action() }
                false
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                true
            } finally {
                updatingIds = updatingIds - ids.toSet()
            }
            if (failed) snackbar.showSnackbar(error)
        }
    }

    fun updateOffline(id: String, keep: Boolean) {
        runNoteAction(listOf(id), offlineError) {
            val current = requireCurrentNote(id)
            if (!keep && !current.hasLocalCopy) {
                check(!transfers.observeLatestForFile(id).first().isPendingDownload)
            }
            if (keep && !current.hasLocalCopy) check(current.canFetchOffline)
            if (current.offlinePinned != keep) files.setOfflinePinned(id, keep)
            if (keep && !current.hasLocalCopy) {
                check(transfers.enqueueDownload(id) is AppResult.Success)
            }
        }
    }

    val notesToTrash = visibleNotes.orEmpty().filter { it.id in pendingTrash }
    if (pendingTrash.isNotEmpty() && notesToTrash.size == pendingTrash.size) {
        val single = notesToTrash.singleOrNull()
        ConfirmDialog(
            title = if (single == null) {
                stringResource(Res.string.common_confirm_trash_count_title, notesToTrash.size)
            } else {
                stringResource(Res.string.notes_trash_title)
            },
            message = single?.name ?: stringResource(Res.string.notes_trash_selection_message),
            confirmLabel = stringResource(Res.string.common_move_trash),
            destructive = true,
            onConfirm = {
                val ids = pendingTrash
                pendingTrash = emptyList()
                runNoteAction(ids, trashError) {
                    ids.forEach { requireCurrentNote(it) }
                    ids.chunked(500).forEach { batch ->
                        check(activeChannel.id() == chatId)
                        check(trash.moveFilesToTrash(batch) is AppResult.Success)
                    }
                }
            },
            onDismiss = { pendingTrash = emptyList() }
        )
    } else if (pendingTrash.isNotEmpty()) {
        LaunchedEffect(pendingTrash) { pendingTrash = emptyList() }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                PageHeader(
                    title = {
                        Text(
                            stringResource(Res.string.common_selection_count, selection.size),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(DriveIcons.Close, stringResource(Res.string.common_clear_selection))
                        }
                    },
                    actions = {
                        TextButton(onClick = { selectedIds = if (allSelected) emptySet() else matchingIds }) {
                            Text(stringResource(if (allSelected) Res.string.common_deselect_all else Res.string.common_select_all))
                        }
                        var showActions by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showActions = true }) {
                                Icon(DriveIcons.MoreVert, stringResource(Res.string.common_actions))
                            }
                            DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.common_move_trash)) },
                                    leadingIcon = { Icon(DriveIcons.Delete, null) },
                                    enabled = selection.none { it in updatingIds },
                                    onClick = {
                                        showActions = false
                                        pendingTrash = selection.toList()
                                    }
                                )
                            }
                        }
                    }
                )
            } else {
                ExpandableLibraryHeader(
                    stringResource(Res.string.notes_title), query, { query = it }, searching, { searching = it },
                    stringResource(Res.string.notes_search),
                    searchContentDescription = stringResource(Res.string.notes_search),
                    closeSearchContentDescription = stringResource(Res.string.notes_search_close),
                    clearSearchContentDescription = stringResource(Res.string.notes_search_clear),
                    searchBackIconModifier = backIconModifier,
                    navigationIcon = {
                        IconButton(onBack) {
                            Icon(
                                DriveIcons.ArrowBack,
                                stringResource(Res.string.common_back),
                                backIconModifier
                            )
                        }
                    },
                    actions = { IconButton(onSettings) { Icon(DriveIcons.Settings, stringResource(Res.string.notes_settings)) } }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onNew, modifier = Modifier.padding(bottom = LocalBottomBarInset.current)) {
                    Icon(DriveIcons.Edit, stringResource(Res.string.notes_create))
                }
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
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp,
                    bottom = LocalBottomBarInset.current + 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(matching, key = { it.id }) { note ->
                    val updating = note.id in updatingIds
                    val latestTransfer = if (!note.hasLocalCopy && (note.offlinePinned || note.canFetchOffline)) {
                        remember(transfers, note.id) { transfers.observeLatestForFile(note.id) }
                            .collectAsStateWithLifecycle(initialValue = null).value
                    } else null
                    var excerpt by remember(note.id, note.modifiedAt, note.localPath) { mutableStateOf("") }
                    LaunchedEffect(note.id, note.modifiedAt, note.localPath) {
                        if (note.localPath != null) {
                            val result = withContext(Dispatchers.IO) { files.readNote(note.id) }
                            if (result is AppResult.Success) {
                                excerpt = Markdown.plain(
                                    result.value.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(160)
                                )
                            }
                        }
                    }
                    NoteCard(
                        note = note,
                        excerpt = excerpt,
                        selectionMode = selectionMode,
                        selected = note.id in selection,
                        updating = updating,
                        offlinePending = latestTransfer.isPendingDownload,
                        onEdit = { onEdit(note) },
                        onSelect = { toggleSelection(note.id) },
                        onOffline = { keep -> updateOffline(note.id, keep) },
                        onUpload = {
                            runNoteAction(listOf(note.id), uploadError) {
                                check(requireCurrentNote(note.id).canUploadNote)
                                check(transfers.enqueueUpload(note.id) is AppResult.Success)
                            }
                        },
                        onTrash = { pendingTrash = listOf(note.id) }
                    )
                }
            }
        }
    }
}

private val DriveFile.canFetchOffline: Boolean
    get() = backupState == BackupState.BACKED_UP && hasRemoteCopy && remoteFileId != null

private val DriveFile.canUploadNote: Boolean
    get() = hasLocalCopy && (backupState == BackupState.NONE || backupState == BackupState.FAILED)

private val TransferTask?.isPendingDownload: Boolean
    get() = this?.let { it.type == TransferType.DOWNLOAD && !it.state.isTerminal } == true

@Composable
private fun NoteCard(
    note: DriveFile,
    excerpt: String,
    selectionMode: Boolean,
    selected: Boolean,
    updating: Boolean,
    offlinePending: Boolean,
    onEdit: () -> Unit,
    onSelect: () -> Unit,
    onOffline: (Boolean) -> Unit,
    onUpload: () -> Unit,
    onTrash: () -> Unit
) {
    val editLabel = stringResource(Res.string.note_edit)
    val selectLabel = stringResource(Res.string.notes_select)
    val trashLabel = stringResource(Res.string.common_move_trash)
    val downloadLabel = stringResource(
        if (note.offlinePinned) Res.string.notes_offline_retry else Res.string.common_download
    )
    val download = if (note.canFetchOffline && !note.hasLocalCopy && !offlinePending && !updating) {
        { onOffline(true) }
    } else null
    val trash = onTrash.takeUnless { updating }
    val actions = if (selectionMode) emptyList() else buildList {
        if (download != null) add(CustomAccessibilityAction(downloadLabel) { download(); true })
        if (trash != null) add(CustomAccessibilityAction(trashLabel) { trash(); true })
    }

    val status = stringResource(
        when (note.backupState) {
            BackupState.BACKED_UP -> Res.string.notes_upload_done
            BackupState.QUEUED -> Res.string.notes_upload_waiting
            BackupState.UPLOADING -> Res.string.notes_uploading
            BackupState.FAILED -> Res.string.notes_upload_failed
            BackupState.NONE -> Res.string.notes_local_only
        }
    )
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    NoteSwipeCard(
        modifier = Modifier.fillMaxWidth().clip(shape),
        gesturesEnabled = !selectionMode,
        onDownload = download,
        onTrash = trash
    ) { offset ->
        ListItem(
            headlineContent = {
                Text(
                    note.name.substringBeforeLast('.'),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                )
            },
            supportingContent = {
                Column {
                    if (excerpt.isNotBlank()) {
                        Text(
                            excerpt,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                        )
                    }
                    Text("${Formatters.date(note.modifiedAt)} · $status")
                    if (note.offlinePinned) {
                        Text(
                            stringResource(
                                when {
                                    note.hasLocalCopy -> Res.string.notes_offline_ready
                                    offlinePending || updating -> Res.string.notes_offline_preparing
                                    else -> Res.string.notes_offline_missing
                                }
                            ),
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            },
            leadingContent = {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = null)
                } else {
                    Icon(DriveIcons.Notes, null)
                }
            },
            trailingContent = if (selectionMode) null else {
                {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(DriveIcons.MoreVert, stringResource(Res.string.common_actions))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text(editLabel) },
                                leadingIcon = { Icon(DriveIcons.Edit, null) },
                                onClick = {
                                    expanded = false
                                    onEdit()
                                }
                            )
                            if (download != null) {
                                DropdownMenuItem(
                                    text = { Text(downloadLabel) },
                                    leadingIcon = { Icon(DriveIcons.Download, null) },
                                    onClick = {
                                        expanded = false
                                        download()
                                    }
                                )
                            }
                            if (note.canUploadNote && !updating) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.common_upload)) },
                                    leadingIcon = { Icon(DriveIcons.Upload, null) },
                                    onClick = {
                                        expanded = false
                                        onUpload()
                                    }
                                )
                            }
                            if (!updating && !offlinePending && (note.offlinePinned || note.hasLocalCopy)) {
                                val offlineLabel = if (note.offlinePinned) {
                                    Res.string.notes_offline_stop_keeping
                                } else Res.string.notes_offline_keep
                                DropdownMenuItem(
                                    text = { Text(stringResource(offlineLabel)) },
                                    leadingIcon = {
                                        Icon(if (note.offlinePinned) DriveIcons.CheckCircle else DriveIcons.OfflinePin, null)
                                    },
                                    onClick = {
                                        expanded = false
                                        onOffline(!note.offlinePinned)
                                    }
                                )
                            }
                            if (trash != null) {
                                DropdownMenuItem(
                                    text = { Text(trashLabel) },
                                    leadingIcon = { Icon(DriveIcons.Delete, null) },
                                    onClick = {
                                        expanded = false
                                        trash()
                                    }
                                )
                            }
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = if (selected) colors.secondaryContainer else colors.surfaceContainerLow
            ),
            modifier = Modifier
                .graphicsLayer { translationX = offset() }
                .semantics {
                    if (selectionMode) this.selected = selected
                    if (actions.isNotEmpty()) customActions = actions
                }
                .combinedClickable(
                    onClickLabel = if (selectionMode) null else editLabel,
                    onLongClickLabel = if (selectionMode) null else selectLabel,
                    onLongClick = if (selectionMode) null else onSelect,
                    onClick = if (selectionMode) onSelect else onEdit
                )
        )
    }
}

@Composable
private fun NoteSwipeCard(
    modifier: Modifier,
    gesturesEnabled: Boolean,
    onDownload: (() -> Unit)?,
    onTrash: (() -> Unit)?,
    content: @Composable (() -> Float) -> Unit
) {
    var offset by remember { mutableFloatStateOf(0f) }
    val download by rememberUpdatedState(onDownload)
    val trash by rememberUpdatedState(onTrash)
    val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val gestures = if (gesturesEnabled) {
        Modifier.pointerInput(swipeThresholdPx) {
            coroutineScope {
                var returnAnimation: Job? = null
                fun returnToRest() {
                    returnAnimation?.cancel()
                    returnAnimation = launch {
                        Animatable(offset).animateTo(0f, tween(220)) { offset = value }
                    }
                }
                try {
                    // Pointer deltas are physical, so the actions keep their directions in RTL.
                    detectHorizontalDragGestures(
                        onDragStart = { returnAnimation?.cancel() },
                        onDragCancel = { returnToRest() },
                        onDragEnd = {
                            val threshold = minOf(swipeThresholdPx, size.width * 0.30f)
                            when {
                                offset <= -threshold -> download?.invoke()
                                offset >= threshold -> trash?.invoke()
                            }
                            returnToRest()
                        }
                    ) { change, delta ->
                        change.consume()
                        val travel = minOf(144.dp.toPx(), size.width * 0.60f)
                        offset = (offset + delta).coerceIn(
                            if (download != null) -travel else 0f,
                            if (trash != null) travel else 0f
                        )
                    }
                } finally {
                    offset = 0f
                }
            }
        }
    } else Modifier

    Box(modifier.then(gestures)) {
        val isTrash = offset > 0f
        if (offset != 0f) {
            val colors = MaterialTheme.colorScheme
            Box(
                modifier = Modifier.matchParentSize().background(
                    if (isTrash) colors.errorContainer else colors.primaryContainer
                ),
                contentAlignment = if (isTrash) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight
            ) {
                Icon(
                    if (isTrash) DriveIcons.Delete else DriveIcons.Download,
                    contentDescription = null,
                    tint = if (isTrash) colors.onErrorContainer else colors.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 20.dp).graphicsLayer {
                        alpha = (abs(offset) / swipeThresholdPx).coerceIn(0f, 1f)
                    }
                )
            }
        }
        content { offset }
    }
}
