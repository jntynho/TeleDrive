// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.files

import app.tgdrive.ui.ElasticLazyVerticalGrid as LazyVerticalGrid
import com.infinity.drive.presentation.components.fileRevealHighlight
import app.tgdrive.ui.DriveIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import com.infinity.drive.presentation.components.PageHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import com.infinity.drive.presentation.common.AppBackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.model.DriveFolder
import com.infinity.drive.domain.model.FileSortField
import com.infinity.drive.domain.model.SortDirection
import com.infinity.drive.domain.model.ViewMode
import com.infinity.drive.presentation.common.CollectSnackbarMessages
import com.infinity.drive.presentation.common.add
import com.infinity.drive.presentation.common.isInitialLoad
import com.infinity.drive.presentation.common.rememberPosition
import com.infinity.drive.presentation.common.resolve
import com.infinity.drive.presentation.components.RefreshableContent
import com.infinity.drive.presentation.components.BlockingProgressDialog
import com.infinity.drive.presentation.components.BottomBarSnackbarHost
import com.infinity.drive.presentation.components.ConfirmDialog
import com.infinity.drive.presentation.components.EmptyState
import com.infinity.drive.presentation.components.FileGridItem
import com.infinity.drive.presentation.components.FileInfoSheet
import com.infinity.drive.presentation.components.FileListItem
import com.infinity.drive.presentation.components.FolderGridItem
import com.infinity.drive.presentation.components.FolderInfoSheet
import com.infinity.drive.presentation.components.FolderPickerDialog
import com.infinity.drive.presentation.components.FolderRow
import com.infinity.drive.presentation.components.LoadingState
import com.infinity.drive.presentation.components.PagingRetryRow
import com.infinity.drive.presentation.components.RefreshAction
import com.infinity.drive.presentation.components.RenameDialog
import com.infinity.drive.presentation.components.liftedTopAppBarColors
import com.infinity.drive.presentation.components.pinchZoom
import com.infinity.drive.presentation.components.rememberDragSelect
import com.infinity.drive.presentation.components.rememberToolbarLift
import com.infinity.drive.presentation.navigation.FabBottomBarInset
import com.infinity.drive.presentation.navigation.LocalBottomBarInset
import com.infinity.drive.presentation.platform.FileDropArea
import com.infinity.drive.presentation.platform.LocalDeleteConsentLauncher
import com.infinity.drive.presentation.platform.LocalFileRevealer
import com.infinity.drive.presentation.platform.LocalFileSharer
import com.infinity.drive.presentation.platform.LocalFolderPicker
import com.infinity.drive.presentation.platform.LocalMultiFilePicker
import com.infinity.drive.presentation.platform.PickResult
import com.infinity.drive.presentation.preview.PreviewSequence
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_actions
import com.infinity.drive.resources.common_add
import com.infinity.drive.resources.common_add_favorites
import com.infinity.drive.resources.common_back
import com.infinity.drive.resources.common_clear_selection
import com.infinity.drive.resources.common_confirm_trash_count_title
import com.infinity.drive.resources.common_create
import com.infinity.drive.resources.common_deselect_all
import com.infinity.drive.resources.common_download
import com.infinity.drive.resources.common_free_space
import com.infinity.drive.resources.common_move_trash
import com.infinity.drive.resources.common_rename
import com.infinity.drive.resources.common_restore_trash_emptied
import com.infinity.drive.resources.common_select_all
import com.infinity.drive.resources.common_selection_count
import com.infinity.drive.resources.common_share
import com.infinity.drive.resources.common_upload
import com.infinity.drive.resources.files
import com.infinity.drive.resources.files_add_files
import com.infinity.drive.resources.files_add_folder
import com.infinity.drive.resources.files_archive
import com.infinity.drive.resources.files_back_import_appear
import com.infinity.drive.resources.files_copy
import com.infinity.drive.resources.files_copy_here
import com.infinity.drive.resources.files_copy_to
import com.infinity.drive.resources.files_delete_local
import com.infinity.drive.resources.files_delete_local_copies
import com.infinity.drive.resources.files_hide
import com.infinity.drive.resources.files_move
import com.infinity.drive.resources.files_move_here
import com.infinity.drive.resources.files_move_to
import com.infinity.drive.resources.files_new_folder
import com.infinity.drive.resources.files_nothing_here_yet
import com.infinity.drive.resources.files_only_files_verified_telegram
import com.infinity.drive.resources.files_rename_file
import com.infinity.drive.resources.files_rename_folder
import com.infinity.drive.resources.files_search
import com.infinity.drive.resources.files_share_destination_confirm
import com.infinity.drive.resources.files_share_destination_title
import com.infinity.drive.resources.files_show_in_file_manager
import com.infinity.drive.resources.files_sort
import com.infinity.drive.resources.files_sort_ascending
import com.infinity.drive.resources.files_sort_backup_status
import com.infinity.drive.resources.files_sort_date_added
import com.infinity.drive.resources.files_sort_date_modified
import com.infinity.drive.resources.files_sort_descending
import com.infinity.drive.resources.files_sort_name
import com.infinity.drive.resources.files_sort_size
import com.infinity.drive.resources.files_sort_type
import com.infinity.drive.resources.info_details
import com.infinity.drive.resources.note_edit_action
import com.infinity.drive.resources.note_new
import com.infinity.drive.resources.preview_share_chooser_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.io.File
import org.koin.compose.viewmodel.koinViewModel

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun FilesScreen(
    onOpenFolder: (String) -> Unit,
    onOpenCrumb: (String?) -> Unit,
    onOpenFile: (String, PreviewSequence) -> Unit,
    onOpenSearch: () -> Unit,
    onNewNote: (String?) -> Unit,
    onEditNote: (String, String) -> Unit,
    onBack: (() -> Unit)?,
    audioOnly:Boolean=false,
    requestUpload:Boolean=false,
    contentMotion:Modifier=Modifier,
    viewModel: FilesViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searching by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val searchModel:com.infinity.drive.presentation.search.SearchViewModel=koinViewModel()
    val searchState by searchModel.uiState.collectAsStateWithLifecycle()
    AppBackHandler(searching){searching=false;searchModel.setQuery("")}

    val renameTarget by viewModel.renameTarget.collectAsStateWithLifecycle()
    val files = viewModel.pagedFiles.collectAsLazyPagingItems()
    val infoTarget by viewModel.infoTarget.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val allSelected by viewModel.allSelected.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var fileActions by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var confirmDeleteLocal by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var uploadRequestHandled by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var audioQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var showAddMenu by remember { mutableStateOf(false) }
    com.infinity.drive.presentation.upload.AddFilesSheet(showAddMenu,state.folderId){showAddMenu=false}
    var showMovePicker by remember { mutableStateOf(false) }
    var showCopyPicker by remember { mutableStateOf(false) }
    var showSelectionOverflow by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    val uploadPicker = LocalMultiFilePicker.current
    val onPickUploads = {
        uploadPicker.pick { references ->
            if (references.isNotEmpty()) viewModel.importAndUpload(references)
        }
    }
    LaunchedEffect(requestUpload){if(requestUpload&&!uploadRequestHandled){uploadRequestHandled=true;showAddMenu=true}}
    val folderUploadPicker = LocalFolderPicker.current
    val onPickUploadFolder = {
        folderUploadPicker.pick { result ->
            if (result is PickResult.Picked) viewModel.importAndUpload(listOf(result.path))
        }
    }

    val deleteConsentLauncher = LocalDeleteConsentLauncher.current

    CollectSnackbarMessages(viewModel.messages, snackbarHostState)
    LaunchedEffect(Unit) {
        viewModel.deleteConsentRequests.collect { request ->
            deleteConsentLauncher.launch(request) { granted ->
                if (granted) viewModel.retryLocalCopyRemoval()
            }
        }
    }

    val working by viewModel.working.collectAsStateWithLifecycle()
    working?.let { BlockingProgressDialog(message = it.resolve()) }

    AppBackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }

    val gridState = rememberLazyGridState()
    var revealHandled by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf(false)}
    var highlightedFile by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(viewModel.focusFileId,files.itemSnapshotList,state.folders.size,state.loaded){
        val focus=viewModel.focusFileId?:return@LaunchedEffect
        if(!revealHandled&&state.loaded){
            val index=files.itemSnapshotList.items.indexOfFirst{it.id==focus}
            if(index>=0){gridState.scrollToItem(state.folders.size+index);highlightedFile=focus;revealHandled=true}
        }
    }
    LaunchedEffect(highlightedFile){if(highlightedFile!=null){kotlinx.coroutines.delay(2600);highlightedFile=null}}
    gridState.rememberPosition(viewModel.listPosition, state.folders.size + files.itemCount)
    var fabVisible by remember { mutableStateOf(true) }
    val listScrolls by remember {
        derivedStateOf { gridState.canScrollForward || gridState.canScrollBackward }
    }
    LaunchedEffect(listScrolls) { if (!listScrolls) fabVisible = true }
    val fabScrollConnection = remember {
        object : NestedScrollConnection {
            @Suppress("SameReturnValue")
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!listScrolls) return Offset.Zero
                when {
                    available.y < -FAB_SCROLL_THRESHOLD -> fabVisible = false
                    available.y > FAB_SCROLL_THRESHOLD -> fabVisible = true
                }
                return Offset.Zero
            }
        }
    }
    val lifted by rememberToolbarLift(gridState)

    val density = LocalDensity.current
    var snackbarHeight by remember { mutableStateOf(0.dp) }
    val fabLift by animateDpAsState(targetValue = snackbarHeight, label = "fabLift")

    FileDropArea(onDropped = { references -> viewModel.importAndUpload(references) }) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor=androidx.compose.ui.graphics.Color.Transparent,
                snackbarHost = {},
                topBar = {
                    Column {
                    if (state.selectionMode) {
                        PageHeader(
                            colors = liftedTopAppBarColors(lifted),
                            title = {
                                Text(
                                    text = stringResource(
                                        Res.string.common_selection_count,
                                        state.selectionCount
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                                IconButton(onClick = { showMovePicker = true }) {
                                    Icon(
                                        DriveIcons.DriveFileMove,
                                        contentDescription = stringResource(Res.string.files_move)
                                    )
                                }
                                Box {
                                    IconButton(onClick = { showSelectionOverflow = true }) {
                                        Icon(
                                            DriveIcons.MoreVert,
                                            contentDescription = stringResource(Res.string.common_actions)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSelectionOverflow,
                                        onDismissRequest = { showSelectionOverflow = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.common_download)) },
                                            enabled = state.folderInSelection ||
                                                    state.capabilities.canDownload,
                                            onClick = {
                                                showSelectionOverflow = false
                                                viewModel.downloadSelected()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.common_upload)) },
                                            enabled = !state.folderInSelection &&
                                                    state.capabilities.canUpload,
                                            onClick = {
                                                showSelectionOverflow = false
                                                viewModel.uploadSelected()
                                            }
                                        )
                                        val fileRevealer = LocalFileRevealer.current
                                        if (fileRevealer != null) {
                                            val revealPath = state.capabilities.soleLocalPath
                                            DropdownMenuItem(
                                                text = {
                                                    Text(stringResource(Res.string.files_show_in_file_manager))
                                                },
                                                enabled = revealPath != null &&
                                                        remember(revealPath) { File(revealPath).exists() },
                                                onClick = {
                                                    showSelectionOverflow = false
                                                    revealPath?.let(fileRevealer::reveal)
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.note_edit_action)) },
                                            enabled = state.selectionCount == 1 &&
                                                    state.folderSelection.isEmpty(),
                                            onClick = {
                                                showSelectionOverflow = false
                                                viewModel.editSelectedNote()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.common_share)) },
                                            /* A folder is not a stream Android can hand
                                               to another app, so any folder in the
                                               selection rules sharing out. */
                                            enabled = state.selectionCount > 0 &&
                                                    state.folderSelection.isEmpty(),
                                            onClick = {
                                                showSelectionOverflow = false
                                                viewModel.shareSelected()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.info_details)) },
                                            enabled = state.selectionCount +
                                                    state.folderSelection.size == 1,
                                            onClick = {
                                                showSelectionOverflow = false
                                                viewModel.showInfoForSelection()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.common_rename)) },
                                            enabled = state.selectionCount == 1,
                                            onClick = {
                                                showSelectionOverflow = false
                                                viewModel.requestRenameSelected()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.files_copy)) },
                                            enabled = !state.folderInSelection,
                                            onClick = {
                                                showSelectionOverflow = false
                                                showCopyPicker = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.common_add_favorites)) },
                                            onClick = {
                                                showSelectionOverflow = false
                                                viewModel.favoriteSelected(true)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.common_free_space)) },
                                            enabled = !state.folderInSelection &&
                                                    state.capabilities.canFreeUpSpace,
                                            onClick = {
                                                showSelectionOverflow = false
                                                confirmDeleteLocal = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.files_hide)) },
                                            enabled = !state.folderInSelection,
                                            onClick = {
                                                showSelectionOverflow = false
                                                viewModel.hideSelected(true)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.files_archive)) },
                                            enabled = !state.folderInSelection,
                                            onClick = {
                                                showSelectionOverflow = false
                                                viewModel.archiveSelected(true)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.common_move_trash)) },
                                            onClick = {
                                                showSelectionOverflow = false
                                                confirmTrash = true
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    } else {
                        app.tgdrive.ui.ExpandableLibraryHeader(
                            title=if(audioOnly)"Audio"else "All files",titleStyle=MaterialTheme.typography.titleLarge,
                            query=searchState.query,onQueryChange=searchModel::setQuery,expanded=searching,onExpandedChange={searching=it},placeholder="Search files",
                            navigationIcon = {
                                onBack?.let {
                                    IconButton(onClick = it) {
                                        Icon(
                                            DriveIcons.ArrowBack,
                                            contentDescription = stringResource(Res.string.common_back)
                                        )
                                    }
                                }
                            },
                            actions = {
                                Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(
                                        DriveIcons.Sort,
                                        contentDescription = stringResource(Res.string.files_sort)
                                    )
                                }
                                SortMenu(
                                    expanded = showSortMenu,
                                    current = state.sortField,
                                    direction = state.sortDirection,
                                    onDismiss = { showSortMenu = false },
                                    onSelect = { field, direction ->
                                        showSortMenu = false
                                        viewModel.setSort(field, direction)
                                    }
                                )
                                }
                                Box {
                                    IconButton({fileActions=true}){Icon(DriveIcons.MoreVert,"More file actions")}
                                    DropdownMenu(fileActions,{fileActions=false}){
                                        DropdownMenuItem(text={Text("Refresh")},enabled=!refreshing,onClick={fileActions=false;viewModel.refresh()})
                                        if(!audioOnly)DropdownMenuItem(text={Text("New folder")},onClick={fileActions=false;showCreateFolder=true})
                                        DropdownMenuItem(text={Text("New note")},onClick={fileActions=false;onNewNote(state.folderId)})
                                    }
                                }

                            }
                        )
                    }


                    }
                },
                floatingActionButton = {}
            ) { padding ->
                if(searching){
                    com.infinity.drive.presentation.search.SearchScreen(onBack={searching=false;searchModel.setQuery("")},onOpenFile=onOpenFile,onOpenFolder=onOpenFolder,viewModel=searchModel,showHeader=false,modifier=Modifier.padding(padding))
                    return@Scaffold
                }
                var everLoaded by remember(state.folderId) { mutableStateOf(false) }
                val loadingNow = !state.loaded || files.isInitialLoad
                LaunchedEffect(loadingNow) {
                    if (!loadingNow) everLoaded = true
                }
                val isLoading = loadingNow && !everLoaded
                val isEmpty = files.itemCount == 0 && state.folders.isEmpty() && !loadingNow
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding())
                        .nestedScroll(fabScrollConnection).then(contentMotion).background(MaterialTheme.colorScheme.background)
                ) {
                    if(state.folderId!=null&&state.breadcrumbs.any{it.id!=null})Breadcrumbs(crumbs=state.breadcrumbs.filter{it.id!=null},onOpen=onOpenCrumb)
                    RefreshableContent(
                        isRefreshing = refreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isLoading) {
                            LoadingState()
                        } else if (files.loadState.refresh is androidx.paging.LoadState.Error && files.itemCount==0 && state.folders.isEmpty()) {
                            com.infinity.drive.presentation.components.ErrorState("Couldn't load this folder.",onRetry={files.retry()})
                        } else if (isEmpty) {
                            EmptyState(
                                icon = DriveIcons.FolderOff,
                                title = stringResource(Res.string.files_nothing_here_yet),
                                description = stringResource(Res.string.files_back_import_appear)
                            )
                        } else {
                            FilesContent(
                                gridState = gridState,
                                state = state,
                                files = files,
                                padding = PaddingValues(
                                    bottom = padding.calculateBottomPadding() +
                                            LocalBottomBarInset.current
                                ),
                                onOpenFolder = onOpenFolder,
                                onOpenFile = { id,sequence -> onOpenFile(id,if(audioOnly)sequence.copy(filterByFolder=false,categories=listOf(com.infinity.drive.domain.model.FileCategory.AUDIO),nameQuery=audioQuery.trim().takeIf{it.isNotBlank()})else sequence) },
                                viewModel = viewModel,
                                highlightedFile=highlightedFile
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                BottomBarSnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.onSizeChanged { size ->
                        snackbarHeight = with(density) { size.height.toDp() }
                    }
                )
            }
        }
    }

    if (showMovePicker || showCopyPicker) {
        val moving = showMovePicker
        FolderPickerHost(
            title = stringResource(
                if (moving) Res.string.files_move_to else Res.string.files_copy_to
            ),
            confirmLabel = stringResource(
                if (moving) Res.string.files_move_here else Res.string.files_copy_here
            ),
            viewModel = viewModel,
            excludedFolderIds = state.folderSelection,
            onConfirm = { target ->
                showMovePicker = false
                showCopyPicker = false
                if (moving) viewModel.moveSelected(target) else viewModel.copySelected(target)
            },
            onDismiss = {
                showMovePicker = false
                showCopyPicker = false
            }
        )
    }
    if (showCreateFolder) {
        RenameDialog(
            title = stringResource(Res.string.files_new_folder),
            initialValue = "",
            confirmLabel = stringResource(Res.string.common_create),
            onConfirm = {
                showCreateFolder = false
                viewModel.createFolder(it)
            },
            onDismiss = { showCreateFolder = false }
        )
    }
    renameTarget?.let { target ->
        RenameDialog(
            title = if (target.isFolder) stringResource(Res.string.files_rename_folder) else stringResource(
                Res.string.files_rename_file
            ),
            initialValue = target.name,
            confirmLabel = stringResource(Res.string.common_rename),
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::dismissRename
        )
    }
    if (confirmTrash) {
        ConfirmDialog(
            title = stringResource(
                Res.string.common_confirm_trash_count_title,
                state.selectionCount
            ),
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
    if (confirmDeleteLocal) {
        ConfirmDialog(
            title = stringResource(Res.string.files_delete_local_copies),
            message = stringResource(Res.string.files_only_files_verified_telegram),
            confirmLabel = stringResource(Res.string.files_delete_local),
            destructive = true,
            onConfirm = {
                confirmDeleteLocal = false
                viewModel.deleteLocalCopies()
            },
            onDismiss = { confirmDeleteLocal = false }
        )
    }
    LaunchedEffect(Unit) {
        viewModel.editRequests.collect { request ->
            onEditNote(request.fileId, request.title)
        }
    }

    val fileSharer = LocalFileSharer.current
    val shareChooserTitle = stringResource(Res.string.preview_share_chooser_title)
    LaunchedEffect(Unit) {
        viewModel.shareRequests.collect { request ->
            fileSharer.share(request.paths, request.mimeType, shareChooserTitle)
        }
    }

    val sharedUris by viewModel.sharedUris.collectAsStateWithLifecycle()
    if (sharedUris.isNotEmpty()) {
        FolderPickerHost(
            title = stringResource(Res.string.files_share_destination_title),
            confirmLabel = stringResource(Res.string.files_share_destination_confirm),
            viewModel = viewModel,
            onConfirm = { target -> viewModel.acceptShare(sharedUris, target) },
            onDismiss = viewModel::dismissShare
        )
    }

    val folderInfoTarget by viewModel.folderInfoTarget.collectAsStateWithLifecycle()
    folderInfoTarget?.let { folder ->
        FolderInfoSheet(folder = folder, onDismiss = viewModel::dismissFolderInfo)
    }

    infoTarget?.let { file ->
        FileInfoSheet(file = file, onDismiss = viewModel::dismissInfo)
    }
}

/**
 * Folder path strip. Deep trees keep only the last three folders inline; the
 * rest collapse into a leading menu so the row never grows past one line.
 */
@Composable
private fun Breadcrumbs(
    crumbs: List<FolderCrumb>,
    onOpen: (String?) -> Unit
) {
    var showCollapsed by remember { mutableStateOf(false) }
    val inlineCount = INLINE_CRUMBS.coerceAtMost(crumbs.size)
    val collapsed = crumbs.dropLast(inlineCount)
    val inline = crumbs.takeLast(inlineCount)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min=48.dp).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (collapsed.isNotEmpty()) {
            Box {
                CrumbLabel(
                    text = "…",
                    current = false,
                    onClick = { showCollapsed = true }
                )
                DropdownMenu(
                    expanded = showCollapsed,
                    onDismissRequest = { showCollapsed = false }
                ) {
                    collapsed.forEach { crumb ->
                        DropdownMenuItem(
                            text = { Text(crumb.name.resolve()) },
                            onClick = {
                                showCollapsed = false
                                onOpen(crumb.id)
                            }
                        )
                    }
                }
            }
            CrumbSeparator()
        }
        inline.forEachIndexed { index, crumb ->
            if (index > 0) CrumbSeparator()
            CrumbLabel(
                text = if(crumb.id==null)"My drive"else crumb.name.resolve(),
                current = index == inline.lastIndex,
                onClick = { onOpen(crumb.id) },
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun CrumbSeparator() {
    Icon(
        imageVector = DriveIcons.KeyboardArrowRight,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CrumbLabel(
    text: String,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (current) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.primary
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .then(if (current) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

private const val INLINE_CRUMBS = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilesContent(
    gridState: LazyGridState,
    state: FilesUiState,
    files: LazyPagingItems<DriveFile>,
    padding: PaddingValues,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (String, PreviewSequence) -> Unit,
    viewModel: FilesViewModel,
    highlightedFile:String?=null
) {
    val columns = if (state.viewMode == ViewMode.GRID) state.gridSize else 1
    val dragSelect = rememberDragSelect(
        gridState = gridState,
        onStart = viewModel::startRangeSelection,
        onRange = { range ->
            val folderIds = mutableListOf<String>()
            val fileIds = mutableListOf<String>()
            for (index in range) {
                if (index < state.folders.size) {
                    folderIds += state.folders[index].id
                } else {
                    files.peek(index - state.folders.size)?.let { fileIds += it.id }
                }
            }
            viewModel.extendRangeSelection(fileIds, folderIds)
        },
        onEnd = viewModel::endRangeSelection
    )
    LazyVerticalGrid(
        state = gridState,
        elasticEnabled = !state.selectionMode,
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()

            .then(dragSelect),
        contentPadding = padding.add(horizontal = 12.dp, top = 12.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(
            count = state.folders.size,
            key = { "folder-${state.folders[it].id}" },
            span = {
                if (state.viewMode == ViewMode.GRID) GridItemSpan(1)
                else GridItemSpan(maxLineSpan)
            }
        ) { index ->
            val folder = state.folders[index]
            val folderSelected = folder.id in state.folderSelection
            if (state.viewMode == ViewMode.GRID) {
                FolderGridItem(
                    folder = folder,
                    selected = folderSelected,
                    onClick = {
                        if (state.selectionMode) viewModel.toggleFolderSelection(folder.id)
                        else onOpenFolder(folder.id)
                    },
                    onLongClick = { viewModel.toggleFolderSelection(folder.id) },
                    modifier = Modifier.animateItem(placementSpec=null)
                )
            } else {
                FolderRow(
                    folder = folder,
                    selected = folderSelected,
                    onClick = {
                        if (state.selectionMode) viewModel.toggleFolderSelection(folder.id)
                        else onOpenFolder(folder.id)
                    },
                    onLongClick = { viewModel.toggleFolderSelection(folder.id) },
                    modifier = Modifier.animateItem(placementSpec=null)
                )
            }
        }

        items(
            count = files.itemCount,
            key = files.itemKey { "file-${it.id}" }
        ) { index ->
            val file = files[index] ?: return@items
            val selected = file.id in state.selection
            if (state.viewMode == ViewMode.GRID) {
                FileGridItem(
                    file = file,
                    selected = selected,
                    onClick = {
                        if (state.selectionMode) viewModel.toggleSelection(file.id)
                        else onOpenFile(
                            file.id,
                            PreviewSequence(
                                folderId = state.folderId,
                                filterByFolder = true,
                                sortField = state.sortField,
                                sortDirection = state.sortDirection
                            )
                        )
                    },
                    onLongClick = { viewModel.toggleSelection(file.id) },
                    modifier = Modifier.animateItem(placementSpec=null).fileRevealHighlight(file.id==highlightedFile)
                )
            } else {
                FileListItem(
                    file = file,
                    selected = selected,
                    selectionMode = state.selectionMode,
                    onClick = {
                        if (state.selectionMode) viewModel.toggleSelection(file.id)
                        else onOpenFile(
                            file.id,
                            PreviewSequence(
                                folderId = state.folderId,
                                filterByFolder = true,
                                sortField = state.sortField,
                                sortDirection = state.sortDirection
                            )
                        )
                    },
                    onLongClick = { viewModel.toggleSelection(file.id) },
                    modifier = Modifier.animateItem(placementSpec=null).fileRevealHighlight(file.id==highlightedFile)
                )
            }
        }
        if(files.loadState.append is androidx.paging.LoadState.Error || files.loadState.refresh is androidx.paging.LoadState.Error){
            item(key="file-load-retry",span={GridItemSpan(maxLineSpan)}){PagingRetryRow{files.retry()}}
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SortMenu(
    expanded: Boolean,
    current: FileSortField,
    direction: SortDirection,
    onDismiss: () -> Unit,
    onSelect: (FileSortField, SortDirection) -> Unit,
    fields:List<FileSortField> = FileSortField.entries,
    prefixContent:@Composable androidx.compose.foundation.layout.ColumnScope.()->Unit={}
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        prefixContent()
        fields.forEach { field ->
            val selected = field == current
            val ascending = direction == SortDirection.ASCENDING
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            when (field) {
                                FileSortField.NAME -> Res.string.files_sort_name
                                FileSortField.SIZE -> Res.string.files_sort_size
                                FileSortField.DATE_MODIFIED -> Res.string.files_sort_date_modified
                                FileSortField.DATE_ADDED -> Res.string.files_sort_date_added
                                FileSortField.TYPE -> Res.string.files_sort_type
                                FileSortField.BACKUP_STATUS -> Res.string.files_sort_backup_status
                            }
                        )
                    )
                },
                trailingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = if (ascending) {
                                DriveIcons.ArrowUpward
                            } else {
                                DriveIcons.ArrowDownward
                            },
                            contentDescription = stringResource(
                                if (ascending) {
                                    Res.string.files_sort_ascending
                                } else {
                                    Res.string.files_sort_descending
                                }
                            ),
                            modifier = Modifier.size(SORT_ICON_SIZE)
                        )
                    }
                } else null,
                onClick = {
                    val newDirection = if (selected && ascending) {
                        SortDirection.DESCENDING
                    } else {
                        SortDirection.ASCENDING
                    }
                    onSelect(field, newDirection)
                }
            )
        }
    }
}

/**
 * Bridges the picker's synchronous tree callbacks to the suspending
 * repository by caching each level as it is browsed.
 */
@Composable
private fun FolderPickerHost(
    title: String,
    confirmLabel: String,
    viewModel: FilesViewModel,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
    excludedFolderIds: Set<String> = emptySet()
) {
    val childrenCache = remember { mutableStateMapOf<String, List<DriveFolder>>() }
    val namesCache = remember { mutableStateMapOf<String, String>() }
    val parentsCache = remember { mutableStateMapOf<String, String?>() }
    var requestedLevel by remember { mutableStateOf<String?>(ROOT_KEY) }
    var reloadToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(requestedLevel, reloadToken) {
        val level = requestedLevel ?: return@LaunchedEffect
        val parentId = level.takeIf { it != ROOT_KEY }
        val folders = viewModel.childFolders(parentId)
        childrenCache[level] = folders
        folders.forEach { folder ->
            namesCache[folder.id] = folder.name
            parentsCache[folder.id] = parentId
        }
    }

    FolderPickerDialog(
        title = title,
        confirmLabel = confirmLabel,
        childrenOf = { parentId ->
            val key = parentId ?: ROOT_KEY
            childrenCache[key] ?: run {
                requestedLevel = key
                emptyList()
            }
        },
        nameOf = { namesCache[it].orEmpty() },
        parentOf = { parentsCache[it] },
        excludedFolderIds = excludedFolderIds,
        onCreateFolder = { parentId, name ->
            scope.launch {
                if (viewModel.createFolderIn(parentId, name)) {
                    /* Drop the cached level so the new folder shows up. */
                    childrenCache.remove(parentId ?: ROOT_KEY)
                    requestedLevel = parentId ?: ROOT_KEY
                    reloadToken++
                }
            }
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

private const val ROOT_KEY = "__root__"

private const val FAB_SCROLL_THRESHOLD = 6f

private val SORT_ICON_SIZE = 18.dp
