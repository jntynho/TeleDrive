// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.gallery

import app.tgdrive.ui.LibraryLayout
import com.infinity.drive.presentation.components.resultReveal
import app.tgdrive.ui.DriveIcons
import com.infinity.drive.presentation.files.SortMenu
import com.infinity.drive.domain.model.FileSortField
import app.tgdrive.ui.LibrarySearchField
import com.infinity.drive.presentation.common.AppBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import com.infinity.drive.presentation.components.PageHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_actions
import com.infinity.drive.resources.common_add_favorites
import com.infinity.drive.resources.common_back
import com.infinity.drive.resources.common_clear_selection
import com.infinity.drive.resources.common_confirm_trash_count_title
import com.infinity.drive.resources.common_deselect_all
import com.infinity.drive.resources.common_download
import com.infinity.drive.resources.common_move_trash
import com.infinity.drive.resources.common_rename
import com.infinity.drive.resources.common_rename_file
import com.infinity.drive.resources.common_restore_trash_emptied
import com.infinity.drive.resources.common_select_all
import com.infinity.drive.resources.common_selection_count
import com.infinity.drive.resources.common_upload
import com.infinity.drive.resources.date_days_ago
import com.infinity.drive.resources.date_today
import com.infinity.drive.resources.date_yesterday
import com.infinity.drive.resources.gallery_albums_appear_organise_media
import com.infinity.drive.resources.gallery_no_albums
import com.infinity.drive.resources.gallery_no_media
import com.infinity.drive.resources.gallery_photos_videos_appear_here
import com.infinity.drive.resources.gallery_title
import com.infinity.drive.domain.model.MediaAlbum
import com.infinity.drive.domain.model.ViewMode
import com.infinity.drive.presentation.common.DayBucket
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.common.isInitialLoad
import com.infinity.drive.presentation.components.RefreshAction
import com.infinity.drive.presentation.components.RefreshableContent
import com.infinity.drive.presentation.components.ConfirmDialog
import com.infinity.drive.presentation.components.EmptyState
import com.infinity.drive.presentation.components.FileGridItem
import com.infinity.drive.presentation.components.FileListItem
import com.infinity.drive.presentation.components.LoadingState
import com.infinity.drive.presentation.components.PagingRetryRow
import com.infinity.drive.presentation.components.RenameDialog
import com.infinity.drive.presentation.components.liftedTopAppBarColors
import com.infinity.drive.presentation.components.pinchZoom
import com.infinity.drive.presentation.components.rememberDragSelect
import com.infinity.drive.presentation.components.rememberToolbarLift
import com.infinity.drive.presentation.navigation.LocalBottomBarInset
import com.infinity.drive.presentation.preview.PreviewSequence
import com.infinity.drive.presentation.common.rememberPosition

@Composable
private fun DayHeaderRow(dayStartMillis: Long, modifier: Modifier = Modifier) {
    Text(
        text = when (val bucket = Formatters.dayBucket(dayStartMillis)) {
            DayBucket.Today -> stringResource(Res.string.date_today)
            DayBucket.Yesterday -> stringResource(Res.string.date_yesterday)
            is DayBucket.DaysAgo -> stringResource(Res.string.date_days_ago, bucket.days)
            is DayBucket.Absolute -> bucket.text
        },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun GalleryTabSelector(selected:GalleryTab,onSelect:(GalleryTab)->Unit) {
    val tabs=GalleryTab.entries.filter{it!=GalleryTab.ALBUMS}
    app.tgdrive.ui.LibraryFilters(tabs.map{stringResource(it.labelRes)},tabs.indexOf(selected),{onSelect(tabs[it])})
}

@Composable
private fun AlbumGrid(
    albums: List<MediaAlbum>,
    columns: Int,
    bottomPadding: Dp,
    onOpenAlbum: (MediaAlbum) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    loading:Boolean=false,
    searching:Boolean=false
) {
    if (albums.isEmpty()) {
        EmptyState(
            icon = DriveIcons.PhotoLibrary,
            title = if(searching) "No matching albums" else stringResource(Res.string.gallery_no_albums),
            description = if(searching) "Try another album name." else stringResource(Res.string.gallery_albums_appear_organise_media)
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize().resultReveal(albums.map{it.folderId})
            .pinchZoom(onZoomIn = onZoomIn, onZoomOut = onZoomOut),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 12.dp,
            bottom = 12.dp + bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(albums, key = { it.folderId ?: UNFILED_ALBUM_KEY }) { album ->
            AlbumCard(
                album = album,
                loading=loading,
                onClick = { onOpenAlbum(album) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

private const val UNFILED_ALBUM_KEY = "__unfiled__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onOpenFile: (String, PreviewSequence) -> Unit,
    onOpenAlbum: (MediaAlbum) -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: GalleryViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var albumQuery by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    AppBackHandler(searching){searching=false;viewModel.setQuery("");albumQuery=""}
    var sorting by remember { mutableStateOf(false) }
    val media = viewModel.pagedMedia.collectAsLazyPagingItems()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    var confirmTrash by remember { mutableStateOf(false) }
    var showSelectionOverflow by remember { mutableStateOf(false) }
    val renameTarget by viewModel.renameTarget.collectAsStateWithLifecycle()

    if (confirmTrash) {
        ConfirmDialog(
            title = stringResource(
                Res.string.common_confirm_trash_count_title,
                state.selection.size
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

    renameTarget?.let { file ->
        RenameDialog(
            title = stringResource(Res.string.common_rename_file),
            initialValue = file.name,
            confirmLabel = stringResource(Res.string.common_rename),
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::dismissRename
        )
    }

    AppBackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }

    val mediaListState = rememberSaveable(state.tab, saver = LazyListState.Saver) {
        LazyListState()
    }
    val allSelected by viewModel.allSelected.collectAsStateWithLifecycle()
    val previewSequence by viewModel.previewSequence.collectAsStateWithLifecycle()
    val mediaGridState = rememberSaveable(state.tab, saver = LazyGridState.Saver) {
        LazyGridState()
    }
    val scrolled = if (state.viewMode == ViewMode.LIST) mediaListState else mediaGridState
    if (state.viewMode == ViewMode.LIST) {
        mediaListState.rememberPosition(viewModel.listPosition, media.itemCount)
    } else {
        mediaGridState.rememberPosition(viewModel.listPosition, media.itemCount)
    }
    val lifted by rememberToolbarLift(scrolled)
    val scopeKey=listOf(query,state.tab.name,state.sortField.name,state.sortDirection.name).joinToString("\u0000")
    var lastScope by rememberSaveable{mutableStateOf(scopeKey)}
    LaunchedEffect(scopeKey){if(lastScope!=scopeKey){mediaListState.scrollToItem(0);mediaGridState.scrollToItem(0);lastScope=scopeKey}}

    val filters=com.infinity.drive.presentation.components.rememberCollapsingFilters(query to state.tab){scrolled.canScrollBackward}
    Scaffold(
        modifier=filters.scrollModifier,
        topBar = { Column {
            if (state.selectionMode) {
                PageHeader(
                    colors = liftedTopAppBarColors(lifted),
                    title = {
                        Text(
                            text = stringResource(
                                Res.string.common_selection_count,
                                state.selection.size
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
                                    enabled = state.capabilities.canDownload,
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.downloadSelected()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.common_upload)) },
                                    enabled = state.capabilities.canUpload,
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.uploadSelected()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.common_rename)) },
                                    enabled = state.selection.size == 1,
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.requestRenameSelected()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.common_add_favorites)) },
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.favoriteSelected()
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
                    query=if(state.tab==GalleryTab.ALBUMS)albumQuery else query,
                    onQueryChange={if(state.tab==GalleryTab.ALBUMS)albumQuery=it else viewModel.setQuery(it)},
                    expanded=searching,onExpandedChange={searching=it},
                    placeholder=if(state.tab==GalleryTab.ALBUMS)"Search albums"else"Search photos and videos",
                    title = state.albumTitle ?: if(state.tab==GalleryTab.ALBUMS) "Albums" else stringResource(Res.string.gallery_title),
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
                            IconButton(onClick={sorting=true}){Icon(DriveIcons.Sort,"Sort gallery")}
                            SortMenu(expanded=sorting,current=state.sortField,direction=state.sortDirection,onDismiss={sorting=false},onSelect={field,direction->sorting=false;viewModel.setSort(field,direction)},fields=listOf(FileSortField.DATE_MODIFIED,FileSortField.DATE_ADDED,FileSortField.NAME,FileSortField.SIZE,FileSortField.TYPE),prefixContent={
                                if(!state.isAlbumView){
                                    Text("View",Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    DropdownMenuItem(text={Text("All media")},onClick={sorting=false;viewModel.setTab(GalleryTab.ALL)},trailingIcon={if(state.tab!=GalleryTab.ALBUMS)Icon(DriveIcons.Check,null)})
                                    DropdownMenuItem(text={Text("Albums")},onClick={sorting=false;viewModel.setTab(GalleryTab.ALBUMS)},trailingIcon={if(state.tab==GalleryTab.ALBUMS)Icon(DriveIcons.Check,null)})
                                    androidx.compose.material3.HorizontalDivider()
                                    Text("Sort by",Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            })
                        }
                    }
                )
            }
        } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top=padding.calculateTopPadding())) {
            androidx.compose.animation.AnimatedVisibility(!state.isAlbumView && !state.selectionMode && state.tab!=GalleryTab.ALBUMS && filters.visible,enter=androidx.compose.animation.fadeIn()+androidx.compose.animation.expandVertically(),exit=androidx.compose.animation.fadeOut()+androidx.compose.animation.shrinkVertically()) {
                GalleryTabSelector(selected=state.tab,onSelect=viewModel::setTab)
            }

            if (state.tab == GalleryTab.ALBUMS && !state.isAlbumView) {
                if (!state.loaded) {
                    app.tgdrive.ui.SkeletonScene { AlbumGrid(List(state.albumGridSize*4){MediaAlbum("skeleton-$it","Album",0,null,0)},state.albumGridSize,padding.calculateBottomPadding()+LocalBottomBarInset.current,{},{},{},loading=true) }
                    return@Column
                }
                AlbumGrid(
                    albums = state.albums.filter { albumQuery.isBlank() || (it.name ?: "Not in a folder").contains(albumQuery.trim(),true) },
                    columns = state.albumGridSize,
                    bottomPadding = padding.calculateBottomPadding() + LocalBottomBarInset.current,
                    onOpenAlbum = onOpenAlbum,
                    onZoomIn = viewModel::zoomAlbumsIn,
                    onZoomOut = viewModel::zoomAlbumsOut,
                    searching = albumQuery.isNotBlank()
                )
                return@Column
            }

            val mediaIdAt: (Int) -> String? = { index ->
                (media.peek(index) as? GalleryListItem.Media)?.file?.id
            }
            val listDragSelect = rememberDragSelect(
                listState = mediaListState,
                onStart = viewModel::startRangeSelection,
                onRange = { range ->
                    viewModel.extendRangeSelection(range.mapNotNull(mediaIdAt))
                },
                onEnd = viewModel::endRangeSelection
            )
            val gridDragSelect = rememberDragSelect(
                gridState = mediaGridState,
                onStart = viewModel::startRangeSelection,
                onRange = { range ->
                    viewModel.extendRangeSelection(range.mapNotNull(mediaIdAt))
                },
                onEnd = viewModel::endRangeSelection
            )
            RefreshableContent(
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    media.isInitialLoad || !state.loaded -> com.infinity.drive.presentation.components.GallerySkeleton(state.gridSize,bottomPadding=padding.calculateBottomPadding()+LocalBottomBarInset.current,listMode=state.viewMode==ViewMode.LIST)

                    media.loadState.refresh is androidx.paging.LoadState.Error && media.itemCount == 0 -> EmptyState(
                        icon=DriveIcons.Info,title="Couldn't load your gallery",
                        description="Your files are still safe. Try loading the library again.",
                        actionLabel="Retry",onAction={media.retry()}
                    )
                    media.itemCount == 0 -> EmptyState(
                        icon = DriveIcons.Photo,
                        title = if(query.isNotBlank())"No matching photos or videos"else stringResource(Res.string.gallery_no_media),
                        description = if(query.isNotBlank())"Try another filename or change the filter."else stringResource(Res.string.gallery_photos_videos_appear_here)
                    )

                    state.viewMode == ViewMode.LIST -> LazyColumn(
                        state = mediaListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pinchZoom(
                                onZoomIn = viewModel::zoomIn,
                                onZoomOut = viewModel::zoomOut
                            )
                            .then(listDragSelect).resultReveal(query to media.itemSnapshotList.items.take(8).map{it.key}),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 8.dp + padding.calculateBottomPadding() + LocalBottomBarInset.current
                        )
                    ) {
                        items(
                            count = media.itemCount,
                            key = media.itemKey { it.key }
                        ) { index ->
                            when (val item = media[index]) {
                                is GalleryListItem.DayHeader -> DayHeaderRow(
                                    dayStartMillis = item.dayStartMillis,
                                    modifier = Modifier.animateItem()
                                )

                                is GalleryListItem.Media -> {
                                    val file = item.file
                                    FileListItem(
                                        file = file,
                                        selected = file.id in state.selection,
                                        selectionMode = state.selectionMode,
                                        onClick = {
                                            if (state.selectionMode) {
                                                viewModel.toggleSelection(file.id)
                                            } else {
                                                onOpenFile(file.id, previewSequence)
                                            }
                                        },
                                        onLongClick = { viewModel.toggleSelection(file.id) },
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                null -> Unit
                            }
                        }
                        if(media.loadState.append is androidx.paging.LoadState.Error){item("gallery-load-retry"){PagingRetryRow{media.retry()}}}
                    }

                    else -> LazyVerticalGrid(
                        state = mediaGridState,
                        columns = GridCells.Fixed(state.gridSize),
                        modifier = Modifier
                            .fillMaxSize()
                            .pinchZoom(
                                onZoomIn = viewModel::zoomIn,
                                onZoomOut = viewModel::zoomOut
                            )
                            .then(gridDragSelect).resultReveal(query to media.itemSnapshotList.items.take(8).map{it.key}),
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            end = 0.dp,
                            top = 12.dp,
                            bottom = 12.dp + padding.calculateBottomPadding() + LocalBottomBarInset.current
                        ),
                        horizontalArrangement = Arrangement.spacedBy(LibraryLayout.GalleryGutter),
                        verticalArrangement = Arrangement.spacedBy(LibraryLayout.GalleryGutter)
                    ) {
                        items(
                            count = media.itemCount,
                            key = media.itemKey { it.key },
                            span = { index ->
                                if (media.peek(index) is GalleryListItem.DayHeader) {
                                    GridItemSpan(maxLineSpan)
                                } else {
                                    GridItemSpan(1)
                                }
                            }
                        ) { index ->
                            when (val item = media[index]) {
                                is GalleryListItem.DayHeader -> DayHeaderRow(
                                    dayStartMillis = item.dayStartMillis,
                                    modifier = Modifier.animateItem()
                                )

                                is GalleryListItem.Media -> {
                                    val file = item.file
                                    FileGridItem(
                                        file = file,
                                        selected = file.id in state.selection,
                                        compact = true,
                                        galleryTile=true,
                                        onClick = {
                                            if (state.selectionMode) {
                                                viewModel.toggleSelection(file.id)
                                            } else {
                                                onOpenFile(file.id, previewSequence)
                                            }
                                        },
                                        onLongClick = { viewModel.toggleSelection(file.id) },
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                null -> Unit
                            }
                        }
                        if(media.loadState.append is androidx.paging.LoadState.Error){
                            item(key="gallery-load-retry",span={GridItemSpan(maxLineSpan)}){PagingRetryRow{media.retry()}}
                        }
                    }
                }
            }
        }
    }
}

private const val TAB_SCROLL_THRESHOLD = 6f
