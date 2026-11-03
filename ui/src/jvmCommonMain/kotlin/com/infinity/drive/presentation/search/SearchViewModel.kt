package com.infinity.drive.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.FileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds

data class SearchFilters(
    val category: FileCategory? = null,
    val backedUpOnly: Boolean = false,
    val notBackedUpOnly: Boolean = false,
    val minSizeMb: Int? = null,
    val sortField: FileSortField = FileSortField.DATE_MODIFIED,
    val sortDirection: SortDirection = SortDirection.DESCENDING
) {
    fun hasCriteria() = category != null || backedUpOnly || notBackedUpOnly || minSizeMb != null
}

data class SearchUiState(
    val query: String = "", val filters: SearchFilters = SearchFilters(),
    val results: List<DriveFile> = emptyList(), val folders: List<DriveFolder> = emptyList(),
    val searching: Boolean = false, val searched: Boolean = false, val error: String? = null
)

private data class SearchRequest(val text: String, val filters: SearchFilters, val retry: Int) {
    val active get() = text.isNotBlank() || filters.hasCriteria()
}
private data class SearchSnapshot(val request: SearchRequest? = null,
    val files: List<DriveFile> = emptyList(), val folders: List<DriveFolder> = emptyList(),
    val loading: Boolean = false, val error: String? = null)

/** One cancellable request owns both result types, loading and error; stale results never escape. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val fileRepository: FileRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow(SearchFilters())
    private val retry = MutableStateFlow(0)
    private val results = combine(query, filters, retry) { text, filter, revision -> SearchRequest(text.trim(), filter, revision) }
        .distinctUntilChanged().debounce(250.milliseconds)
        .flatMapLatest { request ->
            if (!request.active) flowOf(SearchSnapshot(request))
            else {
                flow {
                val filter=request.filters
                val files=fileRepository.observeFiles(FileQuerySpec(
                    nameQuery=request.text.takeIf(String::isNotBlank),
                    categories=filter.category?.let{listOf(it)}.orEmpty(),
                    backedUpOnly=filter.backedUpOnly,notBackedUpOnly=filter.notBackedUpOnly,
                    minSizeBytes=filter.minSizeMb?.toLong()?.times(1024*1024),
                    sortField=filter.sortField,sortDirection=filter.sortDirection))
                val folders=if(request.text.isBlank()||filter.hasCriteria())flowOf(emptyList())
                    else fileRepository.searchFolders(request.text)
                emitAll(combine(files,folders){fileRows,folderRows->SearchSnapshot(request,fileRows.take(500),folderRows.take(500))})
                }.onStart{emit(SearchSnapshot(request,loading=true))}
                    .catch { error ->
                        if(error is CancellationException)throw error
                        emit(SearchSnapshot(request,error="Couldn't search your library. Try again."))
                    }
            }
        }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),SearchSnapshot())
    val uiState: StateFlow<SearchUiState> = combine(query,filters,retry,results){text,filter,revision,snapshot->
        val request=SearchRequest(text.trim(),filter,revision)
        val current=request==snapshot.request
        SearchUiState(query=text,filters=filter,
            results=if(current)snapshot.files else emptyList(),folders=if(current)snapshot.folders else emptyList(),
            searching=request.active&&(!current||snapshot.loading),searched=request.active,
            error=if(current)snapshot.error else null)
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),SearchUiState())
    fun setQuery(value:String){query.value=value}
    fun retry(){retry.update{it+1}}
    fun setCategory(category:FileCategory?){filters.update{it.copy(category=category)}}
    fun setBackedUpOnly(value:Boolean){filters.update{it.copy(backedUpOnly=value,notBackedUpOnly=if(value)false else it.notBackedUpOnly)}}
    fun setNotBackedUpOnly(value:Boolean){filters.update{it.copy(notBackedUpOnly=value,backedUpOnly=if(value)false else it.backedUpOnly)}}
    fun setSort(field:FileSortField,direction:SortDirection){filters.update{it.copy(sortField=field,sortDirection=direction)}}
}
