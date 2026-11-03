// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.collection

import app.tgdrive.ui.DriveIcons
import com.infinity.drive.presentation.common.AppBackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.infinity.drive.presentation.components.PageHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.collection_empty_title
import com.infinity.drive.resources.collection_remove_from
import com.infinity.drive.resources.common_back
import com.infinity.drive.resources.common_clear_selection
import com.infinity.drive.resources.common_confirm_trash_count_title
import com.infinity.drive.resources.common_deselect_all
import com.infinity.drive.resources.common_move_trash
import com.infinity.drive.resources.common_restore_trash_emptied
import com.infinity.drive.resources.common_select_all
import com.infinity.drive.resources.common_selection_count
import com.infinity.drive.presentation.common.isInitialLoad
import com.infinity.drive.presentation.components.ConfirmDialog
import com.infinity.drive.presentation.components.EmptyState
import com.infinity.drive.presentation.components.ActivityLibraryRow
import com.infinity.drive.presentation.components.FileThumbnail
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.components.LoadingState
import com.infinity.drive.presentation.components.liftedTopAppBarColors
import com.infinity.drive.presentation.components.rememberDragSelect
import com.infinity.drive.presentation.components.rememberToolbarLift
import com.infinity.drive.presentation.preview.PreviewSequence

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    onBack: () -> Unit,
    onOpenFile: (String, PreviewSequence) -> Unit,
    viewModel: CollectionViewModel = koinViewModel()
) {
    val files = viewModel.files.collectAsLazyPagingItems()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selectionMode = selection.isNotEmpty()
    val allSelected by viewModel.allSelected.collectAsStateWithLifecycle()
    var confirmTrash by remember { mutableStateOf(false) }

    AppBackHandler(enabled = selectionMode) { viewModel.clearSelection() }

    if (confirmTrash) {
        ConfirmDialog(
            title = stringResource(Res.string.common_confirm_trash_count_title, selection.size),
            message = stringResource(Res.string.common_restore_trash_emptied),
            confirmLabel = stringResource(Res.string.common_move_trash),
            destructive = true,
            onConfirm = {
                confirmTrash = false
                viewModel.trashSelected()
            },
            onDismiss = { confirmTrash = false }
        )
    }

    val listState = rememberLazyListState()
    val lifted by rememberToolbarLift(listState)

    Scaffold(
        topBar = {
            PageHeader(
                colors = liftedTopAppBarColors(lifted),
                title = {
                    Text(
                        text = if (selectionMode) {
                            stringResource(Res.string.common_selection_count, selection.size)
                        } else {
                            stringResource(viewModel.type.titleRes)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (selectionMode) viewModel.clearSelection() else onBack() }
                    ) {
                        Icon(
                            imageVector = if (selectionMode) {
                                DriveIcons.Close
                            } else {
                                DriveIcons.ArrowBack
                            },
                            contentDescription = if (selectionMode) stringResource(Res.string.common_clear_selection) else stringResource(
                                Res.string.common_back
                            )
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
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
                        IconButton(onClick = viewModel::removeFromCollection) {
                            Icon(
                                DriveIcons.RemoveCircleOutline,
                                contentDescription = stringResource(
                                    Res.string.collection_remove_from,
                                    stringResource(viewModel.type.titleRes)
                                )
                            )
                        }
                        IconButton(onClick = { confirmTrash = true }) {
                            Icon(
                                DriveIcons.Delete,
                                contentDescription = stringResource(Res.string.common_move_trash)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (files.isInitialLoad) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if(files.loadState.refresh is androidx.paging.LoadState.Error && files.itemCount==0){
            EmptyState(DriveIcons.Info,"Couldn't load this collection","Try loading your library again.",Modifier.padding(padding),"Retry",{files.retry()})
            return@Scaffold
        }
        if (files.itemCount == 0) {
            EmptyState(
                icon = viewModel.type.icon,
                title = stringResource(
                    Res.string.collection_empty_title,
                    stringResource(viewModel.type.titleRes).lowercase()
                ),
                description = stringResource(viewModel.type.emptyMessageRes),
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        val dragSelect = rememberDragSelect(
            listState = listState,
            onStart = viewModel::startRangeSelection,
            onRange = { range ->
                viewModel.extendRangeSelection(
                    range.mapNotNull { index -> if(index>0)files.peek(index-1)?.id else null }
                )
            },
            onEnd = viewModel::endRangeSelection
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .then(dragSelect),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 8.dp + padding.calculateBottomPadding()
            )
        ) {
            item("collection-summary"){
                Text(stringResource(viewModel.type.subtitleRes),style=androidx.compose.material3.MaterialTheme.typography.bodyMedium,color=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=12.dp,vertical=16.dp))
            }
            items(
                count = files.itemCount,
                key = files.itemKey { it.id }
            ) { index ->
                val file = files[index] ?: return@items
                ActivityLibraryRow(
                    title = file.name,
                    subtitle = "${Formatters.bytes(file.sizeBytes)} · ${Formatters.dateTime(file.modifiedAt)}",
                    first = index == 0,
                    last = index == files.itemCount - 1,
                    leading = { FileThumbnail(file, Modifier.fillMaxSize()) },
                    selected = file.id in selection,
                    onClick = {
                        if (selectionMode) viewModel.toggleSelection(file.id)
                        else onOpenFile(file.id, viewModel.previewSequence)
                    },
                    onLongClick = { viewModel.toggleSelection(file.id) },
                    modifier = Modifier.animateItem()
                )
            }
            if(files.loadState.append is androidx.paging.LoadState.Error){
                item("collection-load-retry"){com.infinity.drive.presentation.components.PagingRetryRow{files.retry()}}
            }
        }
    }
}
