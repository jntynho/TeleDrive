// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.trash

import app.tgdrive.ui.ElasticLazyColumn as LazyColumn
import app.tgdrive.ui.DriveIcons
import com.infinity.drive.presentation.common.AppBackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.infinity.drive.presentation.components.PageHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_back
import com.infinity.drive.resources.common_clear_selection
import com.infinity.drive.resources.common_delete_forever
import com.infinity.drive.resources.common_deselect_all
import com.infinity.drive.resources.common_restore
import com.infinity.drive.resources.common_select_all
import com.infinity.drive.resources.common_selection_count
import com.infinity.drive.resources.trash
import com.infinity.drive.resources.trash_delete_permanently
import com.infinity.drive.resources.trash_delete_selected_message
import com.infinity.drive.resources.trash_empty
import com.infinity.drive.resources.trash_empty_trash_action
import com.infinity.drive.resources.trash_empty_trash_title
import com.infinity.drive.resources.trash_items_permanently_deleted_device
import com.infinity.drive.resources.trash_restores_with_folder
import com.infinity.drive.resources.trash_trashed_at
import com.infinity.drive.domain.model.TrashItem
import com.infinity.drive.presentation.common.resolve
import com.infinity.drive.presentation.common.CollectSnackbarMessages
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.common.add
import com.infinity.drive.presentation.components.BlockingProgressDialog
import com.infinity.drive.presentation.components.ConfirmDialog
import com.infinity.drive.presentation.components.EmptyState
import com.infinity.drive.presentation.components.ActivityLibraryRow
import com.infinity.drive.presentation.components.FileThumbnail
import com.infinity.drive.presentation.components.liftedTopAppBarColors
import com.infinity.drive.presentation.components.rememberDragSelect
import com.infinity.drive.presentation.components.rememberToolbarLift

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    val hidden=org.koin.compose.koinInject<com.infinity.drive.core.security.HiddenLockManager>()
    val lock by hidden.state.collectAsStateWithLifecycle()
    var unlocking by remember{mutableStateOf(false)}
    var pendingAction by remember{mutableStateOf<String?>(null)}
    androidx.compose.runtime.LaunchedEffect(lock.unlocked){
        if(lock.unlocked&&unlocking){
            unlocking=false
            when(pendingAction){"empty"->confirmEmpty=true;"delete"->confirmDeleteSelected=true}
            pendingAction=null
        }
    }
    androidx.compose.runtime.LaunchedEffect(lock.epoch){confirmEmpty=false;confirmDeleteSelected=false;viewModel.clearSelection()}
    if(unlocking){
        com.infinity.drive.presentation.hidden.HiddenLockScreen(onBack={unlocking=false;pendingAction=null})
        return
    }


    CollectSnackbarMessages(viewModel.messages, snackbarHostState)

    state.working?.let { message ->
        BlockingProgressDialog(message = message.resolve())
    }

    AppBackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }

    val listState = rememberLazyListState()
    val lifted by rememberToolbarLift(listState)
    val allSelected = state.rows.any { it.selectable } &&
            state.selection.size == state.rows.count { it.selectable }
    val dragSelect = rememberDragSelect(
        listState = listState,
        onStart = viewModel::startRangeSelection,
        onRange = { range ->
            viewModel.extendRangeSelection(
                range.mapNotNull { index ->
                    state.rows.getOrNull(index-1)?.takeIf { it.selectable }?.item?.id
                }
            )
        },
        onEnd = viewModel::endRangeSelection
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.selectionMode) {
                PageHeader(
                    colors = liftedTopAppBarColors(lifted),
                    title = {
                        Text(stringResource(Res.string.common_selection_count, state.selection.size))
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(
                                DriveIcons.Close,
                                contentDescription = stringResource(Res.string.common_clear_selection)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (allSelected) viewModel.clearSelection() else viewModel.selectAll()
                            }
                        ) {
                            Icon(
                                imageVector = if (allSelected) {
                                    DriveIcons.Deselect
                                } else {
                                    DriveIcons.SelectAll
                                },
                                contentDescription = if (allSelected) {
                                    stringResource(Res.string.common_deselect_all)
                                } else {
                                    stringResource(Res.string.common_select_all)
                                }
                            )
                        }
                        IconButton(onClick = viewModel::restoreSelected) {
                            Icon(
                                DriveIcons.RestoreFromTrash,
                                contentDescription = stringResource(Res.string.common_restore)
                            )
                        }
                        IconButton(onClick = { if(lock.configured&&!lock.unlocked){pendingAction="delete";unlocking=true}else confirmDeleteSelected=true }) {
                            Icon(
                                DriveIcons.DeleteForever,
                                contentDescription = stringResource(Res.string.common_delete_forever)
                            )
                        }
                    }
                )
            } else {
                PageHeader(
                    colors = liftedTopAppBarColors(lifted),
                    title = { Text(stringResource(Res.string.trash)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                DriveIcons.ArrowBack,
                                contentDescription = stringResource(Res.string.common_back)
                            )
                        }
                    },
                    actions = {
                        if(lock.configured)IconButton(onClick={if(lock.unlocked)hidden.lock()else{pendingAction=null;unlocking=true}}){Icon(DriveIcons.Lock,if(lock.unlocked)"Lock Hidden"else"Unlock hidden items")}
                        if (state.items.isNotEmpty()) {
                            IconButton(onClick = { if(lock.configured&&!lock.unlocked){pendingAction="empty";unlocking=true}else confirmEmpty=true }) {
                                Icon(
                                    DriveIcons.DeleteForever,
                                    contentDescription = stringResource(Res.string.trash_empty_trash_action)
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (state.items.isEmpty() && !state.loading) {
            EmptyState(
                icon = DriveIcons.Delete,
                title = stringResource(Res.string.trash_empty),
                description = if (state.autoClearDays > 0) {
                    "Items are deleted permanently after ${state.autoClearDays} days."
                } else {
                    "Deleted items stay here until you remove them."
                },
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(dragSelect),
            contentPadding = padding.add(horizontal = 16.dp, top = 12.dp, bottom = 12.dp)
        ) {
            item("trash-summary"){
                Text(if(state.autoClearDays>0)"Restore items before they are removed after ${state.autoClearDays} days."else"Restore items, or remove them permanently when you are ready.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=12.dp,vertical=16.dp))
            }
            itemsIndexed(state.rows, key = { _, row -> row.key }) { index, row ->
                val item = row.item
                val selected = row.selectable && item.id in state.selection
                ActivityLibraryRow(
                    title = item.name,
                    subtitle = if (row.depth == 0) stringResource(Res.string.trash_trashed_at, Formatters.dateTime(item.trashedAt))
                        else stringResource(Res.string.trash_restores_with_folder),
                    first = index == 0, last = index == state.rows.lastIndex, selected = selected,
                    onClick = {
                        if (state.selectionMode && row.selectable) viewModel.toggleSelection(item.id)
                        else if (row.expandable) viewModel.toggleExpanded(item.id)
                        else if (row.selectable) viewModel.toggleSelection(item.id)
                    },
                    onLongClick = { if (row.selectable) viewModel.toggleSelection(item.id) },
                    indent = row.depth,
                    leading = {
                        when (item) {
                            is TrashItem.File -> FileThumbnail(item.file, Modifier.fillMaxSize())
                            is TrashItem.Folder -> Icon(DriveIcons.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.animateItem(),
                    actions = {
                        if (row.expandable) IconButton({viewModel.toggleExpanded(item.id)}) {
                            Icon(if(row.expanded) DriveIcons.ExpandLess else DriveIcons.ExpandMore,
                                if(row.expanded) "Collapse ${item.name}" else "Show contents of ${item.name}")
                        }
                    }
                )
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(Res.string.trash_empty_trash_title),
            message = stringResource(Res.string.trash_items_permanently_deleted_device),
            confirmLabel = stringResource(Res.string.trash_empty_trash_action),
            destructive = true,
            onConfirm = {
                confirmEmpty = false
                viewModel.emptyTrash()
            },
            onDismiss = { confirmEmpty = false }
        )
    }
    if (confirmDeleteSelected) {
        ConfirmDialog(
            title = stringResource(Res.string.trash_delete_permanently),
            message = stringResource(
                Res.string.trash_delete_selected_message,
                state.selection.size
            ),
            confirmLabel = stringResource(Res.string.common_delete_forever),
            destructive = true,
            onConfirm = {
                confirmDeleteSelected = false
                viewModel.deleteSelectedForever()
            },
            onDismiss = { confirmDeleteSelected = false }
        )
    }
}
