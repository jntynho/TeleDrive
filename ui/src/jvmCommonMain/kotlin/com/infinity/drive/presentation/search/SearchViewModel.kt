package com.infinity.drive.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.SettingsRepository
import java.util.UUID
import kotlinx.coroutines.launch
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
    val searching: Boolean = false, val searched: Boolean = false, val error: String? = null,
    val searchingContents: Boolean = false, val contentSkipped: Int = 0
)

private data class SearchRequest(val text: String, val filters: SearchFilters, val retry: Int, val contents: Boolean) {
    val active get() = text.isNotBlank() || filters.hasCriteria()
}
private data class SearchSnapshot(val request: SearchRequest? = null,
    val files: List<DriveFile> = emptyList(), val folders: List<DriveFolder> = emptyList(),
    val loading: Boolean = false, val error: String? = null, val contentSkipped: Int = 0)

/** One cancellable request owns both result types, loading and error; stale results never escape. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val fileRepository: FileRepository,
    private val settings: SettingsRepository? = null,
    private val textSearch: DriveTextSearch? = null
) : ViewModel() {
    val supportsContentSearch = textSearch != null
    val supportsPdfSearch = textSearch?.supportsPdf == true

    private val contentTerm = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow(SearchFilters())
    private val retry = MutableStateFlow(0)
    private val request = combine(query, filters, retry, contentTerm) { text, filter, revision, term ->
        SearchRequest(text.trim(), filter, revision, contents = term != null && term == text.trim())
    }.distinctUntilChanged()

    private val results = request.debounce(250.milliseconds).flatMapLatest { request ->
            if (!request.active) flowOf(SearchSnapshot(request))
            else {
                flow {
                val filter=request.filters
                val tag = FileTags.searchTag(request.text)
                val files=fileRepository.observeFiles(FileQuerySpec(
                    nameQuery=request.text.takeIf { !request.contents && it.isNotBlank() && tag == null },
                    tag = tag,
                    categories=filter.category?.let{listOf(it)}.orEmpty(),
                    backedUpOnly=filter.backedUpOnly,notBackedUpOnly=filter.notBackedUpOnly,
                    minSizeBytes=filter.minSizeMb?.toLong()?.times(1024*1024),
                    sortField=filter.sortField,sortDirection=filter.sortDirection))
                if (request.contents && textSearch != null) {
                    emitAll(files.transformLatest { candidates ->
                        emit(SearchSnapshot(request, loading = true))
                        val matches = textSearch.search(request.text, candidates)
                        emit(SearchSnapshot(request, files = matches.files, contentSkipped = matches.skipped))
                    })
                } else {
                    val folders=if(request.text.isBlank()||tag != null||filter.hasCriteria())flowOf(emptyList())
                        else fileRepository.searchFolders(request.text)
                    emitAll(combine(files,folders){fileRows,folderRows->SearchSnapshot(request,fileRows.take(500),folderRows.take(500))})
                }
                }.onStart{emit(SearchSnapshot(request,loading=true))}
                    .catch { error ->
                        if(error is CancellationException)throw error
                        emit(SearchSnapshot(request,error="Couldn't search your library. Try again."))
                    }
            }
        }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),SearchSnapshot())
    val uiState: StateFlow<SearchUiState> = combine(query, request, results) { text, request, snapshot ->
        val current = request == snapshot.request && text.trim() == request.text
        SearchUiState(query=text,filters=request.filters,
            results=if(current)snapshot.files else emptyList(),folders=if(current)snapshot.folders else emptyList(),
            searching=request.active&&(!current||snapshot.loading),searched=request.active,
            error=if(current)snapshot.error else null,
            searchingContents = current && request.contents,
            contentSkipped = if (current) snapshot.contentSkipped else 0)
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),SearchUiState())
    val smartFolders: StateFlow<List<SmartFolder>> = (settings?.preferences?.map { prefs ->
        prefs.smartFolders.filter { it.chatId == prefs.storageChatId }.sortedBy { it.name.lowercase() }
    } ?: flowOf(emptyList())).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun saveSmartFolder(name: String): Boolean {
        val repository = settings ?: return false
        val title = name.trim()
        val text = query.value.trim()
        val filter = filters.value
        if (title.isBlank() || title.length > 60 || contentTerm.value != null ||
            (text.isBlank() && !filter.hasCriteria())
        ) return false
        val id = UUID.randomUUID().toString()
        repository.update { prefs ->
            val chat = prefs.storageChatId
            if (chat == null || prefs.smartFolders.any { it.chatId == chat && it.name.equals(title, true) }) prefs
            else prefs.copy(smartFolders = prefs.smartFolders + SmartFolder(
                id, chat, title, text, filter.category, filter.backedUpOnly, filter.notBackedUpOnly,
                filter.minSizeMb, filter.sortField, filter.sortDirection
            ))
        }
        return repository.preferences.first().smartFolders.any { it.id == id }
    }

    fun openSmartFolder(folder: SmartFolder) {
        viewModelScope.launch {
            if (settings?.preferences?.first()?.storageChatId != folder.chatId) return@launch
            filters.value = SearchFilters(
                folder.category, folder.backedUpOnly, folder.notBackedUpOnly, folder.minimumSizeMb,
                folder.sortField, folder.sortDirection
            )
            contentTerm.value = null
            query.value = folder.query
        }
    }

    fun removeSmartFolder(folder: SmartFolder) {
        viewModelScope.launch {
            settings?.update { prefs ->
                if (prefs.storageChatId != folder.chatId) prefs
                else prefs.copy(smartFolders = prefs.smartFolders.filterNot { it.id == folder.id && it.chatId == folder.chatId })
            }
        }
    }

    fun setQuery(value: String) {
        if (value.trim() != query.value.trim()) contentTerm.value = null
        query.value = value
    }

    fun toggleContentSearch() {
        val text = query.value.trim()
        if (textSearch != null && text.isNotBlank() && FileTags.searchTag(text) == null) {
            contentTerm.value = text.takeUnless { it == contentTerm.value }
        }
    }
    fun retry(){retry.update{it+1}}
    fun setCategory(category:FileCategory?){filters.update{it.copy(category=category)}}
    fun setBackedUpOnly(value:Boolean){filters.update{it.copy(backedUpOnly=value,notBackedUpOnly=if(value)false else it.notBackedUpOnly)}}
    fun setNotBackedUpOnly(value:Boolean){filters.update{it.copy(notBackedUpOnly=value,backedUpOnly=if(value)false else it.backedUpOnly)}}
    fun setSort(field:FileSortField,direction:SortDirection){filters.update{it.copy(sortField=field,sortDirection=direction)}}
}
