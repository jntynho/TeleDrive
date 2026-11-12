// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.gallery

import app.tgdrive.ui.ElasticLazyVerticalGrid as LazyVerticalGrid
import app.tgdrive.ui.ElasticLazyColumn as LazyColumn
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
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
import com.infinity.drive.presentation.components.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.first

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
    searching:Boolean=false,
    position:com.infinity.drive.presentation.common.ListPosition?=null,
    context:Any?=null
) {
    if (albums.isEmpty()) {
        EmptyState(
            icon = DriveIcons.PhotoLibrary,
            title = if(searching) "No matching albums" else stringResource(Res.string.gallery_no_albums),
            description = if(searching) "Try another album name." else stringResource(Res.string.gallery_albums_appear_organise_media)
        )
        return
    }
    val grid=rememberSaveable(context,saver=LazyGridState.Saver){LazyGridState()}
    val motion=rememberGridDensityMotion(grid,context)
    val latestColumns by rememberUpdatedState(columns)
    var frozen by remember(context){mutableStateOf<List<MediaAlbum>?>(null)}
    val rows=frozen?:albums
    val density=LocalDensity.current
    val spacing=with(density){10.dp.toPx()}
    val inset=with(density){12.dp.toPx()}
    var restored by remember(position){mutableStateOf(false)}
    LaunchedEffect(motion.busy){if(!motion.busy)frozen=null}
    LaunchedEffect(position,albums,loading){
        if(!restored&&!loading&&albums.isNotEmpty()){
            val index=position?.anchorId?.let{id->albums.indexOfFirst{(it.folderId?:UNFILED_ALBUM_KEY)==id}}?.takeIf{it>=0}?:position?.index?:0
            grid.requestScrollToItem(index.coerceIn(0,albums.lastIndex),position?.offset?:0)
            restored=true
        }
    }
    LaunchedEffect(grid,position,restored,motion.busy){
        if(restored&&!motion.busy&&position!=null)snapshotFlow{grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset}.collect{(index,offset)->
            position.anchorId=rows.getOrNull(index)?.folderId?:UNFILED_ALBUM_KEY;position.remember(index,offset)
        }
    }
    LazyVerticalGrid(
        state=grid,
        elasticEnabled=!motion.busy,
        userScrollEnabled=!motion.busy,
        columns=GridCells.Fixed(columns),
        modifier=Modifier.fillMaxSize().then(motion.fade()).densityAccessibility(columns,onZoomIn,onZoomOut)
            .pinchZoom(onZoomIn,onZoomOut,
                onGestureStarted={point->frozen=albums.toList();motion.begin(columns,rows.map{DensityItem(it.folderId?:UNFILED_ALBUM_KEY)},point,spacing,inset)},
                onGestureFinished={committed->if(!committed)motion.cancel();if(!motion.busy)frozen=null},
                onDensityCommit={step->motion.release(step,{latestColumns}){if(step>0)onZoomIn()else onZoomOut()}},
                densityKey=context,columns=columns,enabled=!loading&&!motion.transitioning,
                canZoomIn=columns>MIN_GRID_COLUMNS,canZoomOut=columns<MAX_GRID_COLUMNS,commitAllowed={motion.dragging}),
        contentPadding=PaddingValues(start=12.dp,end=12.dp,top=12.dp,bottom=12.dp+bottomPadding),
        horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)
    ) {
        items(rows,key={it.folderId?:UNFILED_ALBUM_KEY}){album->
            AlbumCard(album,loading=loading,onClick={onOpenAlbum(album)},
                enabled=motion.acceptsInput,modifier=if(motion.busy)Modifier else Modifier.animateItem())
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
    val appliedQuery by viewModel.appliedQuery.collectAsStateWithLifecycle()
    val albumQuery by viewModel.albumQuery.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    AppBackHandler(searching){searching=false;viewModel.setQuery("");viewModel.searchAlbums("")}
    var sorting by remember { mutableStateOf(false) }
    val media = viewModel.pagedMedia.collectAsLazyPagingItems()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val busy by viewModel.working.collectAsStateWithLifecycle()
    val snack=remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(viewModel){viewModel.messages.collect{snack.showSnackbar(it)}}
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

    val contextRevision by viewModel.contextRevision.collectAsStateWithLifecycle()
    val mediaListState = rememberSaveable(state.tab,contextRevision,appliedQuery,state.sortField,state.sortDirection, saver = LazyListState.Saver) {
        LazyListState()
    }
    val allSelected by viewModel.allSelected.collectAsStateWithLifecycle()
    val previewSequence by viewModel.previewSequence.collectAsStateWithLifecycle()
    val mediaGridState = rememberSaveable(state.tab,contextRevision,appliedQuery,state.sortField,state.sortDirection, saver = LazyGridState.Saver) {
        LazyGridState()
    }
    val densityContext=listOf(state.tab,contextRevision,appliedQuery,state.sortField,state.sortDirection,state.viewMode)
    val gridMotion=rememberGridDensityMotion(mediaGridState,densityContext)
    val latestColumns by rememberUpdatedState(state.gridSize)
    val density=LocalDensity.current
    val gap=with(density){LibraryLayout.GalleryGutter.toPx()}
    var pinching by remember(densityContext){mutableStateOf(false)}
    var frozenMedia by remember(densityContext){mutableStateOf<List<GalleryListItem>?>(null)}
    val shownCount=frozenMedia?.size?:media.itemCount
    val shownAt:(Int)->GalleryListItem?={ index -> frozenMedia?.getOrNull(index) ?: if(index in 0 until media.itemCount)media.peek(index)else null }
    var pinchAnchor by remember(state.tab,contextRevision,appliedQuery,state.sortField,state.sortDirection){mutableStateOf<DensityAnchor?>(null)}
    LaunchedEffect(gridMotion.busy){if(!gridMotion.busy)frozenMedia=null}
    val capturePinchAnchor:(androidx.compose.ui.geometry.Offset)->Unit={point->
        pinching=true;viewModel.endRangeSelection()
        if(state.viewMode==ViewMode.GRID){
            val snapshot=media.itemSnapshotList.items.toList()
            frozenMedia=snapshot
            gridMotion.begin(state.gridSize,snapshot.map{DensityItem(it.key,it is GalleryListItem.DayHeader)},point,gap)
        }else{
            val item=mediaListState.layoutInfo.visibleItemsInfo.filter{shownAt(it.index) is GalleryListItem.Media}
                .minByOrNull{kotlin.math.abs(it.offset+it.size/2f-point.y)}
            val file=(item?.let{shownAt(it.index)} as? GalleryListItem.Media)?.file
            pinchAnchor=if(file!=null&&item!=null)DensityAnchor(file.id,((point.y-item.offset)/item.size).coerceIn(0f,1f),point.y)else null
        }
    }
    LaunchedEffect(state.gridSize,state.viewMode){
        val anchor=pinchAnchor
        if(anchor!=null){
            val index=media.itemSnapshotList.items.indexOfFirst{it is GalleryListItem.Media&&it.file.id==anchor.key}
            if(index>=0){
                if(state.viewMode==ViewMode.GRID){
                    kotlinx.coroutines.withTimeoutOrNull(700){snapshotFlow{mediaGridState.layoutInfo.viewportSize.width}.first{it>0}}
                    val width=(mediaGridState.layoutInfo.viewportSize.width-gap*(state.gridSize-1))/state.gridSize
                    if(width>0){
                        val offset=anchoredScrollOffset(anchor,width/LibraryLayout.GalleryAspect,mediaGridState.layoutInfo.beforeContentPadding.toFloat())
                        viewModel.positionFor(ViewMode.GRID).apply{anchorId=anchor.key;remember(index,offset)}
                        kotlinx.coroutines.withTimeoutOrNull(700){snapshotFlow{mediaGridState.layoutInfo}.first{layout->
                            layout.visibleItemsInfo.any{item->shownAt(item.index) is GalleryListItem.Media&&kotlin.math.abs(item.size.width-width)<2f}
                        }}
                        mediaGridState.scrollToItem(index,offset)
                    }
                }else mediaListState.requestScrollToItem(index)
            }
            pinchAnchor=null
        }
    }

    val scrolled = if (state.viewMode == ViewMode.LIST) mediaListState else mediaGridState
    val position=remember(viewModel,state.tab,appliedQuery,state.sortField,state.sortDirection,state.viewMode,contextRevision,albumQuery){viewModel.positionFor(state.viewMode)}
    var anchorRestored by remember(position){mutableStateOf(false)}
    LaunchedEffect(position,media.itemCount,gridMotion.busy,pinching,pinchAnchor){
        if(!anchorRestored&&media.itemCount>0&&pinchAnchor==null&&!gridMotion.busy&&!pinching&&state.tab!=GalleryTab.ALBUMS){
            val wanted=position.anchorId
            val index=if(wanted==null)0 else media.itemSnapshotList.items.indexOfFirst{(it as? GalleryListItem.Media)?.file?.id==wanted}
            if(index>=0){
                if(state.viewMode==ViewMode.LIST)mediaListState.scrollToItem(index,position.offset)else mediaGridState.scrollToItem(index,position.offset)
                anchorRestored=true
            }else if(media.loadState.refresh is androidx.paging.LoadState.NotLoading){
                // The pager has already loaded the saved file's ranked window. A missing
                // anchor here was removed/filtered; settle on its nearest loaded survivor.
                val fallback=media.itemSnapshotList.items.mapIndexedNotNull{ i,item->if(item is GalleryListItem.Media)i else null}
                    .minByOrNull{kotlin.math.abs(it-position.index)}?:0
                if(state.viewMode==ViewMode.LIST)mediaListState.scrollToItem(fallback,position.offset)else mediaGridState.scrollToItem(fallback,position.offset)
                anchorRestored=true
            }
        }
    }
    LaunchedEffect(position,scrolled,anchorRestored,gridMotion.busy,pinching){
        if(anchorRestored&&!gridMotion.busy&&!pinching)androidx.compose.runtime.snapshotFlow {
            if(state.viewMode==ViewMode.LIST){
                val first=mediaListState.layoutInfo.visibleItemsInfo.firstOrNull{shownAt(it.index) is GalleryListItem.Media}
                (first?.index?:mediaListState.firstVisibleItemIndex) to -(first?.offset?:-mediaListState.firstVisibleItemScrollOffset)
            }else{
                val first=mediaGridState.layoutInfo.visibleItemsInfo.firstOrNull{shownAt(it.index) is GalleryListItem.Media}
                (first?.index?:mediaGridState.firstVisibleItemIndex) to -(first?.offset?.y?:-mediaGridState.firstVisibleItemScrollOffset)
            }
        }.collect{(index,offset)->
            (shownAt(index) as? GalleryListItem.Media)?.file?.id?.let{position.anchorId=it;position.remember(index,offset)
                position.neighborIds=listOf(index+1,index-1,index+2,index-2).mapNotNull{neighbor->(shownAt(neighbor) as? GalleryListItem.Media)?.file?.id}.distinct()}
        }
    }
    val lifted by rememberToolbarLift(scrolled)

    val filters=com.infinity.drive.presentation.components.rememberCollapsingFilters(query to state.tab,enabled=!gridMotion.busy&&!pinching){scrolled.canScrollBackward}
    val networkRetryEpoch=LocalThumbnailRetryEpoch.current
    var manualRetryEpoch by remember{mutableIntStateOf(0)}
    var wasRefreshing by remember{mutableStateOf(false)}
    LaunchedEffect(refreshing){if(wasRefreshing&&!refreshing)manualRetryEpoch++;wasRefreshing=refreshing}
    CompositionLocalProvider(LocalThumbnailRetryEpoch provides (networkRetryEpoch+manualRetryEpoch)){
    Scaffold(
        snackbarHost={androidx.compose.material3.SnackbarHost(snack)},
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
                            enabled=!busy,
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
                                    enabled = !busy && state.capabilities.canDownload,
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.downloadSelected()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.common_upload)) },
                                    enabled = !busy && state.capabilities.canUpload,
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.uploadSelected()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.common_rename)) },
                                    enabled = !busy && state.selection.size == 1,
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
                    onQueryChange={if(state.tab==GalleryTab.ALBUMS)viewModel.searchAlbums(it) else viewModel.setQuery(it)},
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
                            SortMenu(expanded=sorting,current=state.sortField,direction=state.sortDirection,onDismiss={sorting=false},onSelect={field,direction->sorting=false;viewModel.setSort(field,direction)},fields=if(state.tab==GalleryTab.ALBUMS)listOf(FileSortField.NAME,FileSortField.DATE_MODIFIED)else listOf(FileSortField.DATE_MODIFIED,FileSortField.DATE_ADDED,FileSortField.NAME,FileSortField.SIZE,FileSortField.TYPE),prefixContent={
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
            androidx.compose.animation.AnimatedVisibility(!state.isAlbumView && !state.selectionMode && state.tab!=GalleryTab.ALBUMS && filters.visible,enter=androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180))+androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(220)),exit=androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140))+androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(220))) {
                GalleryTabSelector(selected=state.tab,onSelect=viewModel::setTab)
            }

            if (state.tab == GalleryTab.ALBUMS && !state.isAlbumView) {
                if (!state.loaded||(state.albumsLoading&&state.albums.isEmpty())) {
                    app.tgdrive.ui.SkeletonScene { AlbumGrid(List(state.albumGridSize*4){MediaAlbum("skeleton-$it","Album",0,null,0)},state.albumGridSize,padding.calculateBottomPadding()+LocalBottomBarInset.current,{},{},{},loading=true) }
                    return@Column
                }
                if(state.albumsError&&state.albums.isEmpty()){EmptyState(DriveIcons.Info,"Couldn't load albums",description="Check your connection and try again.",actionLabel="Retry",onAction=viewModel::retryAlbums);return@Column}
                AlbumGrid(
                    albums = state.albums,
                    position=position,
                    context=densityContext to albumQuery,
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
                (shownAt(index) as? GalleryListItem.Media)?.file?.id
            }
            val listDragSelect = rememberDragSelect(
                listState = mediaListState,
                onStart = viewModel::startRangeSelection,
                onRange = { range ->
                    viewModel.extendRangeSelection(range.mapNotNull(mediaIdAt))
                },
                onEnd = viewModel::endRangeSelection,
                enabled=!gridMotion.busy&&!pinching&&!busy
            )
            val gridDragSelect = rememberDragSelect(
                gridState = mediaGridState,
                onStart = viewModel::startRangeSelection,
                onRange = { range ->
                    viewModel.extendRangeSelection(range.mapNotNull(mediaIdAt))
                },
                onEnd = viewModel::endRangeSelection,
                enabled=!gridMotion.busy&&!pinching&&!busy
            )
            RefreshableContent(
                isRefreshing = refreshing,
                enabled=!gridMotion.busy&&!pinching&&!busy,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    (frozenMedia==null&&media.isInitialLoad) || !state.loaded -> com.infinity.drive.presentation.components.GallerySkeleton(state.gridSize,bottomPadding=padding.calculateBottomPadding()+LocalBottomBarInset.current,listMode=state.viewMode==ViewMode.LIST)

                    media.loadState.refresh is androidx.paging.LoadState.Error && shownCount == 0 -> EmptyState(
                        icon=DriveIcons.Info,title="Couldn't load your gallery",
                        description="Your files are still safe. Try loading the library again.",
                        actionLabel="Retry",onAction={media.retry()}
                    )
                    shownCount == 0 -> EmptyState(
                        icon = DriveIcons.Photo,
                        title = if(query.isNotBlank())"No matching photos or videos"else stringResource(Res.string.gallery_no_media),
                        description = if(query.isNotBlank())"Try another filename or change the filter."else stringResource(Res.string.gallery_photos_videos_appear_here)
                    )

                    state.viewMode == ViewMode.LIST -> LazyColumn(
                        state = mediaListState,
                        elasticEnabled=!pinching&&!gridMotion.busy&&!state.selectionMode,
                        userScrollEnabled=!gridMotion.busy&&!pinching,
                        modifier = Modifier
                            .fillMaxSize().testTag("gallery-grid")
                            .pinchZoom(
                                onZoomIn = viewModel::zoomIn,
                                onZoomOut = viewModel::zoomOut,
                                onGestureStarted=capturePinchAnchor,
                                onGestureFinished={committed->pinching=false;if(!committed||!viewModel.flushZoom())pinchAnchor=null},
                                densityKey=densityContext,enabled=!busy
                            )
                            .then(listDragSelect).resultReveal(media.itemSnapshotList.items.take(8).map{it.key}),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 8.dp + padding.calculateBottomPadding() + LocalBottomBarInset.current
                        )
                    ) {
                        items(
                            count = shownCount,
                            key = {index->shownAt(index)?.key?:"gallery-placeholder-$index"},
                            contentType={index->if(shownAt(index) is GalleryListItem.DayHeader)"day"else"media"}
                        ) { index ->
                            when (val item = frozenMedia?.getOrNull(index) ?: media[index]) {
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
                        elasticEnabled=!pinching&&!gridMotion.busy&&!state.selectionMode,
                        userScrollEnabled=!gridMotion.busy&&!pinching,
                        columns = GridCells.Fixed(state.gridSize),
                        modifier = Modifier
                            .fillMaxSize().then(gridMotion.fade()).testTag("gallery-grid")
                            .pinchZoom(
                                onZoomIn = viewModel::zoomIn,
                                onZoomOut = viewModel::zoomOut,
                                onGestureStarted=capturePinchAnchor,
                                onGestureFinished={committed->pinching=false;if(!committed)gridMotion.cancel();if(!gridMotion.busy)frozenMedia=null},
                                onDensityCommit={step->gridMotion.release(step,{latestColumns}){if(step>0)viewModel.zoomIn()else viewModel.zoomOut()}},
                                columns=state.gridSize,enabled=!busy&&!gridMotion.transitioning,
                                canZoomIn=state.gridSize>MIN_GRID_COLUMNS,canZoomOut=state.gridSize<MAX_GRID_COLUMNS,commitAllowed={gridMotion.dragging},
                                densityKey=densityContext
                            )
                            .then(gridDragSelect).densityAccessibility(state.gridSize,viewModel::zoomIn,viewModel::zoomOut),
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
                            count = shownCount,
                            key = {index->shownAt(index)?.key?:"gallery-placeholder-$index"},
                            contentType={index->if(shownAt(index) is GalleryListItem.DayHeader)"day"else"media"},
                            span = { index ->
                                if (shownAt(index) is GalleryListItem.DayHeader) {
                                    GridItemSpan(maxLineSpan)
                                } else {
                                    GridItemSpan(1)
                                }
                            }
                        ) { index ->
                            when (val item = frozenMedia?.getOrNull(index) ?: media[index]) {
                                is GalleryListItem.DayHeader -> DayHeaderRow(
                                    dayStartMillis = item.dayStartMillis,
                                    modifier = (if(gridMotion.busy)Modifier else Modifier.animateItem())
                                )

                                is GalleryListItem.Media -> {
                                    val file = item.file
                                    FileGridItem(
                                        file = file,
                                        selected = file.id in state.selection,
                                        compact = true,
                                        galleryTile=true,
                                        selectionMode=state.selectionMode,
                                        enabled=gridMotion.acceptsInput,
                                        onClick = {
                                            if (state.selectionMode) {
                                                viewModel.toggleSelection(file.id)
                                            } else {
                                                onOpenFile(file.id, previewSequence)
                                            }
                                        },
                                        onLongClick = { viewModel.toggleSelection(file.id) },
                                        modifier = (if(gridMotion.busy)Modifier else Modifier.animateItem()).testTag("gallery-file-${file.id}")
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
}

private const val TAB_SCROLL_THRESHOLD = 6f
