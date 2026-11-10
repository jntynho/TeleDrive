// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.gallery

import org.jetbrains.compose.resources.StringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.gallery_tab_albums
import com.infinity.drive.resources.gallery_tab_all
import com.infinity.drive.resources.gallery_tab_photos
import com.infinity.drive.resources.gallery_tab_videos
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.model.FileQuerySpec
import com.infinity.drive.domain.model.FileSortField
import com.infinity.drive.domain.model.MediaAlbum
import com.infinity.drive.domain.model.SortDirection
import com.infinity.drive.domain.model.ViewMode
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.domain.repository.SyncRepository
import com.infinity.drive.domain.repository.TransferRepository
import com.infinity.drive.domain.repository.TrashRepository
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.common.ListPosition
import com.infinity.drive.presentation.components.GridZoomLevel
import com.infinity.drive.presentation.components.MAX_GRID_COLUMNS
import com.infinity.drive.presentation.components.MIN_GRID_COLUMNS
import com.infinity.drive.presentation.components.SelectionCapabilities
import com.infinity.drive.presentation.components.zoomedIn
import com.infinity.drive.presentation.components.zoomedOut
import com.infinity.drive.presentation.navigation.Route
import com.infinity.drive.presentation.preview.PreviewSequence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds

enum class GalleryTab(val labelRes: StringResource) {
    ALL(Res.string.gallery_tab_all),
    PHOTOS(Res.string.gallery_tab_photos),
    VIDEOS(Res.string.gallery_tab_videos),
    ALBUMS(Res.string.gallery_tab_albums)
}

data class GalleryUiState(
    val tab: GalleryTab = GalleryTab.ALL,
    val albums: List<MediaAlbum> = emptyList(),
    val albumTitle: String? = null,
    val viewMode: ViewMode = ViewMode.GRID,
    val isAlbumView: Boolean = false,
    val gridSize: Int = 3,
    val albumGridSize: Int = 3,
    val loaded: Boolean = false,
    val capabilities: SelectionCapabilities = SelectionCapabilities(),
    val sortField: FileSortField = FileSortField.DATE_MODIFIED,
    val sortDirection: SortDirection = SortDirection.DESCENDING,
    val selection: Set<String> = emptySet()
) {
    val selectionMode: Boolean get() = selection.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class GalleryViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val fileRepository: FileRepository,
    private val trashRepository: TrashRepository,
    private val transferRepository: TransferRepository,
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val route: Route.GalleryAlbum? = runCatching {
        savedStateHandle.toRoute<Route.GalleryAlbum>()
    }.getOrNull()

    private val albumFolderId: String? = route?.folderId
    private val isAlbumView: Boolean = route != null

    private val tab = MutableStateFlow(runCatching{GalleryTab.valueOf(savedStateHandle.get<String>("gallery-tab")?:savedStateHandle.get<String>("initialTab")?:"ALL")}.getOrDefault(GalleryTab.ALL))
    val query = MutableStateFlow(savedStateHandle.get<String>("gallery-query").orEmpty())
    fun setQuery(value:String) { if(query.value==value)return;invalidateSelection();query.value=value;savedStateHandle["gallery-query"]=value;listPosition.remember(0,0);clearSelection() }

    val albumQuery = savedStateHandle.getStateFlow("album-query", "")
    fun searchAlbums(value:String) { savedStateHandle["album-query"] = value }
    private val _message = MutableSharedFlow<String>(extraBufferCapacity=8)
    val messages = _message.asSharedFlow()
    val working = MutableStateFlow(false)
    private var generation = 0L
    private var selectJob:Job? = null
    private var operationJob:Job?=null
    private var operationSerial=0L
    private fun invalidateSelection() { generation++; selectJob?.cancel(); clearSelection(); rangeBase=null }
    init { viewModelScope.launch {
        settingsRepository.preferences.map { it.storageChatId }.distinctUntilChanged().drop(1).collect {
            invalidateSelection(); listPosition.remember(0,0)
        }
    } }
    private val sort = MutableStateFlow(
        runCatching{FileSortField.valueOf(savedStateHandle.get<String>("gallery-sort")?:"DATE_MODIFIED")}.getOrDefault(FileSortField.DATE_MODIFIED) to
        runCatching{SortDirection.valueOf(savedStateHandle.get<String>("gallery-direction")?:"DESCENDING")}.getOrDefault(SortDirection.DESCENDING))

    private val positions=linkedMapOf<String,ListPosition>()
    private var channelKey="initial"
    fun positionFor(mode:ViewMode):ListPosition {
        val key="$channelKey:${tab.value}:${query.value}:${sort.value}:$mode"
        positions[key]?.let{return it}
        if(positions.size>=24)positions.remove(positions.keys.first())?.discard()
        return ListPosition(savedStateHandle,"gallery:$key").also{positions[key]=it}
    }
    val listPosition:ListPosition get()=positionFor(ViewMode.GRID)
    val contextRevision=MutableStateFlow(0L)
    init {
        viewModelScope.launch {settingsRepository.preferences.map{it.storageChatId}.distinctUntilChanged().collect{channelKey=it.toString();contextRevision.value++}}
        viewModelScope.launch {val prefs=settingsRepository.preferences.first();if(savedStateHandle.get<String>("gallery-sort")==null)sort.value=prefs.gallerySortField to prefs.gallerySortDirection}
    }

    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private var rangeBase: Set<String>? = null
    private val _allSelected = MutableStateFlow(false)
    val allSelected: StateFlow<Boolean> = _allSelected.asStateFlow()

    private val spec: Flow<FileQuerySpec> = combine(
        tab,
        sort,
        settingsRepository.preferences, query.debounce { if(it.isBlank())0L else 180L }
    ) { currentTab, (field, direction), _, nameQuery ->
        FileQuerySpec(
            folderId = albumFolderId,
            nameQuery = nameQuery.trim().takeIf { it.isNotBlank() },
            filterByFolder = isAlbumView,
            categories = when (currentTab) {
                GalleryTab.PHOTOS -> listOf(FileCategory.IMAGE)
                GalleryTab.VIDEOS -> listOf(FileCategory.VIDEO)
                else -> listOf(FileCategory.IMAGE, FileCategory.VIDEO)
            },
            showHidden = false,
            showArchived = false,
            sortField = field,
            sortDirection = direction
        )
    }.distinctUntilChanged()

    private val albums: Flow<List<MediaAlbum>> = combine(tab,albumQuery,sort) { t,q,s -> Triple(t,q,s) }
        .flatMapLatest { (t,q,s) ->
            if (isAlbumView || t!=GalleryTab.ALBUMS) flowOf(emptyList())
            else fileRepository.observeAlbums(false,false).map { rows ->
                val filtered=rows.filter { q.isBlank() || (it.name?:"Not in a folder").contains(q.trim(),true) }
                val ordered=when(s.first){
                    FileSortField.NAME->filtered.sortedWith(compareBy<MediaAlbum>{it.name?.lowercase().orEmpty()}.thenBy{it.folderId.orEmpty()})
                    else->filtered.sortedWith(compareBy<MediaAlbum>{it.latestAt}.thenBy{it.folderId.orEmpty()})
                }
                if(s.second==SortDirection.DESCENDING)ordered.asReversed()else ordered
            }.flowOn(Dispatchers.Default)
        }

    val previewSequence: StateFlow<PreviewSequence> = spec.map { current ->
        PreviewSequence(nameQuery=current.nameQuery,folderId=current.folderId,filterByFolder=current.filterByFolder,
            categories=current.categories,sortField=current.sortField,sortDirection=current.sortDirection)
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),PreviewSequence())

    val pagedMedia: Flow<PagingData<GalleryListItem>> = spec
        .flatMapLatest { current ->
            if(tab.value==GalleryTab.ALBUMS) flowOf(PagingData.empty()) else flow {
                val anchor=positionFor(uiState.value.viewMode).anchorId
                val rank=anchor?.let{fileRepository.indexOfFile(current,it)}
                emitAll(fileRepository.pagedFiles(current.copy(initialLoadIndex=rank)).map{data->withDayHeaders(data,current)})
            }
        }
        .cachedIn(viewModelScope)

    private val selectionCapabilities: Flow<Pair<Set<String>, SelectionCapabilities>> = selection
        .flatMapLatest { ids ->
            if(ids.isEmpty())flowOf(ids to SelectionCapabilities()) else
                fileRepository.observeFilesByIds(ids.toList()).map { rows -> ids to SelectionCapabilities.of(rows.filterNot{it.isTrashed}) }
        }

    val uiState: StateFlow<GalleryUiState> = combine(
        tab,
        sort,
        selectionCapabilities,
        settingsRepository.preferences
            .map { it.galleryGridSize to it.albumGridSize }
            .distinctUntilChanged(),
        combine(
            albums,
            settingsRepository.preferences.map { it.galleryViewMode }.distinctUntilChanged()
        ) { albumList, mode -> albumList to mode }
    ) { currentTab, (field, direction), selectionState, sizes, extras ->
        val (selected, capabilities) = selectionState
        val (albumList, mode) = extras
        val (gridSize, albumGridSize) = sizes
        GalleryUiState(
            tab = currentTab,
            albums = albumList,
            albumTitle = route?.title,
            viewMode = mode,
            isAlbumView = isAlbumView,
            gridSize = gridSize,
            albumGridSize = albumGridSize,
            sortField = field,
            sortDirection = direction,
            selection = selected,
            capabilities = capabilities,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    private fun withDayHeaders(
        data: PagingData<DriveFile>,
        spec: FileQuerySpec
    ): PagingData<GalleryListItem> {
        val scope = spec.categories.joinToString("+") { it.name }
        val mapped = data.map<DriveFile, GalleryListItem> { GalleryListItem.Media(it, scope) }
        if (spec.sortField != FileSortField.DATE_MODIFIED &&
            spec.sortField != FileSortField.DATE_ADDED
        ) {
            return mapped
        }
        return mapped.insertSeparators { before, after ->
            val next = (after as? GalleryListItem.Media)?.file ?: return@insertSeparators null
            val nextDay = Formatters.dayStart(timestampFor(next, spec.sortField))
            val previous = (before as? GalleryListItem.Media)?.file
            val previousDay = previous
                ?.let { Formatters.dayStart(timestampFor(it, spec.sortField)) }
            if (previousDay == nextDay) null else GalleryListItem.DayHeader(nextDay, scope)
        }
    }

    private fun timestampFor(file: DriveFile, sortField: FileSortField): Long =
        if (sortField == FileSortField.DATE_ADDED) file.addedAt else file.modifiedAt

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.update { true }
        viewModelScope.launch {
            try { check(syncRepository.fullResync().isSuccess){"Refresh failed"} }
            catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){_message.tryEmit("Couldn't refresh the gallery. Your current files are still available.")}
            finally { _refreshing.value=false }
        }
    }


    fun setTab(value: GalleryTab) {
        if(tab.value==value)return
        invalidateSelection();listPosition.remember(0,0)
        savedStateHandle["gallery-tab"]=value.name
        tab.update { value }
    }
    fun setSort(field: FileSortField, direction: SortDirection) {
        if(sort.value==field to direction)return
        invalidateSelection()
        savedStateHandle["gallery-sort"]=field.name;savedStateHandle["gallery-direction"]=direction.name
        viewModelScope.launch{settingsRepository.update{it.copy(gallerySortField=field,gallerySortDirection=direction)}}
        listPosition.remember(0,0);sort.value=field to direction
    }

    fun zoomIn() = applyZoom(GridZoomLevel::zoomedIn)

    fun zoomOut() = applyZoom { current -> if(current.viewMode==ViewMode.LIST)current else current.copy(gridSize=(current.gridSize+1).coerceAtMost(MAX_GRID_COLUMNS)) }

    fun zoomAlbumsIn() = stepAlbumColumns(-1)

    fun zoomAlbumsOut() = stepAlbumColumns(1)

    private fun stepAlbumColumns(step: Int) {
        viewModelScope.launch {
            settingsRepository.update { prefs ->
                prefs.copy(
                    albumGridSize = (prefs.albumGridSize + step)
                        .coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
                )
            }
        }
    }

    private var zoomJob:Job?=null
    private var pendingZoom:GridZoomLevel?=null
    fun flushZoom():Boolean {
        zoomJob?.cancel()
        val desired=pendingZoom?:return false
        pendingZoom=null
        if(desired==GridZoomLevel(uiState.value.viewMode,uiState.value.gridSize))return false
        viewModelScope.launch{settingsRepository.update{it.copy(galleryViewMode=desired.viewMode,galleryGridSize=desired.gridSize)}}
        return true
    }
    private fun applyZoom(transform: (GridZoomLevel) -> GridZoomLevel) {
        pendingZoom=transform(pendingZoom?:GridZoomLevel(uiState.value.viewMode,uiState.value.gridSize))
        zoomJob?.cancel()
        zoomJob=viewModelScope.launch {
            delay(160)
            val desired=pendingZoom?:return@launch
            pendingZoom=null
            settingsRepository.update { prefs ->
                val next = desired
                prefs.copy(galleryViewMode = next.viewMode, galleryGridSize = next.gridSize)
            }
        }
    }

    fun toggleSelection(id: String) {
        if(working.value)return
        _allSelected.update { false }
        selection.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        if(working.value){operationSerial++;operationJob?.cancel();working.value=false}
        _allSelected.update { false }
        selection.update { emptySet() }
    }

    /** Selects every media file the current tab shows, loaded or not. */
    fun selectAll() {
        if(working.value)return
        selectJob?.cancel()
        val token=generation
        selectJob=viewModelScope.launch {
            try {
                val request=spec.first()
                val ids=fileRepository.fileIds(request).toSet()
                ensureActive()
                if(token==generation && request==spec.first()) {
                    selection.value=ids; _allSelected.value=true
                    _message.tryEmit("Selected ${ids.size} matching files. New arrivals are not included.")
                }
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){_message.tryEmit("Couldn't select all files. Try again.")}
        }
    }

    fun startRangeSelection() {
        rangeBase = selection.value
    }

    fun extendRangeSelection(ids: List<String>) {
        val base = rangeBase ?: return
        _allSelected.update { false }
        selection.update { base + ids }
    }

    fun endRangeSelection() {
        rangeBase = null
    }

    private fun operate(label:String,eligible:(DriveFile)->Boolean={true},action:suspend(DriveFile)->Boolean){
        if(working.value)return
        val ids=selection.value.toList();val token=generation
        if(ids.isEmpty())return
        working.value=true
        val serial=++operationSerial
        operationJob=viewModelScope.launch {
            val failed=mutableSetOf<String>();var completed=0;var skipped=0
            try {
                for(batch in ids.chunked(200)) {
                    ensureActive(); if(token!=generation)break
                    val found=fileRepository.getFiles(batch).associateBy{it.id}
                    for(id in batch){
                        ensureActive(); if(token!=generation)break
                        val file=found[id]
                        if(file==null||file.isTrashed||!eligible(file)){skipped++;continue}
                        try { if(action(file))completed++ else failed+=id }
                        catch(cancel:CancellationException){throw cancel}
                        catch(_:Exception){failed+=id}
                    }
                }
                if(token==generation){selection.value=failed;_allSelected.value=false}
                _message.tryEmit("$label: $completed completed, ${failed.size} failed, $skipped skipped.")
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){_message.tryEmit("Couldn't complete $label. Try again.")}
            finally{if(serial==operationSerial)working.value=false}
        }
    }
    fun trashSelected()=operate("Move to Trash"){trashRepository.moveFilesToTrash(listOf(it.id)).isSuccess}
    fun downloadSelected()=operate("Download",{it.hasRemoteCopy&&!it.hasLocalCopy}){transferRepository.enqueueDownload(it.id).isSuccess}
    fun uploadSelected()=operate("Upload",{!it.hasRemoteCopy}){transferRepository.enqueueUpload(it.id).isSuccess}
    private val _renameTarget = MutableStateFlow<DriveFile?>(null)
    val renameTarget: StateFlow<DriveFile?> = _renameTarget.asStateFlow()

    fun requestRenameSelected() {
        val id = selection.value.singleOrNull() ?: return
        viewModelScope.launch { _renameTarget.value = fileRepository.getFile(id) }
    }

    fun dismissRename() {
        _renameTarget.value = null
    }

    fun confirmRename(newName: String) {
        val target = _renameTarget.value ?: return
        _renameTarget.value = null
        operate("Rename",{it.id==target.id}){fileRepository.renameFile(it.id,newName).isSuccess}
    }

    fun favoriteSelected()=operate("Add to favorites"){fileRepository.setFilesFavorite(listOf(it.id),true);true}

}
