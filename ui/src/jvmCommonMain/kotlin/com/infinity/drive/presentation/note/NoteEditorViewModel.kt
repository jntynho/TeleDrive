package com.infinity.drive.presentation.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.note_saved
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.TransferRepository
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.presentation.common.toUiText
import com.infinity.drive.presentation.navigation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditorUiState(
    val title: String = "",
    val body: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val isNew: Boolean = true,
    val dirty: Boolean = false,
    val loadError: String? = null
)

class NoteEditorViewModel(
    private val fileRepository: FileRepository,
    private val transferRepository: TransferRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.NoteEditor>()

    private val _uiState = MutableStateFlow(NoteEditorUiState(isNew = route.fileId == null))
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    private val _saved = Channel<Unit>(Channel.CONFLATED)
    val saved = _saved.receiveAsFlow()
    private var editingFileId = route.fileId
    private var initialTitle = ""
    private var initialBody = ""

    init {
        val fileId = route.fileId
        if (fileId == null) {
            _uiState.update { it.copy(body = route.sharedText.orEmpty(), loading = false, dirty = !route.sharedText.isNullOrBlank()) }
        } else {
            viewModelScope.launch { load(fileId) }
        }
    }

    private suspend fun load(fileId: String) {
        try {
            when (val result = fileRepository.readNote(fileId)) {
                is AppResult.Success -> {
                    initialTitle=route.title.orEmpty();initialBody=result.value
                    _uiState.update{it.copy(title=initialTitle,body=initialBody,loading=false,dirty=false,loadError=null)}
                }
                is AppResult.Failure -> _uiState.update{it.copy(loading=false,loadError="Couldn't open this note. Your original file is unchanged.")}
            }
        }catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){_uiState.update{it.copy(loading=false,loadError="Couldn't open this note. Try again.")}}
    }
    fun retryLoad(){
        val id=editingFileId?:return
        if(_uiState.value.loading)return
        _uiState.update{it.copy(loading=true,loadError=null)}
        viewModelScope.launch{load(id)}
    }
    fun setTitle(value:String){_uiState.update{it.copy(title=value,dirty=value!=initialTitle||it.body!=initialBody)}}
    fun setBody(value:String){_uiState.update{it.copy(body=value,dirty=it.title!=initialTitle||value!=initialBody)}}
    fun save(){
        val state=_uiState.value
        if(state.saving||state.loading||state.loadError!=null||state.body.isBlank()||(!state.dirty&&!state.isNew))return
        _uiState.update{it.copy(saving=true)}
        viewModelScope.launch{
            try {
                when(val result=fileRepository.saveNote(editingFileId,route.folderId,state.title.trim(),state.body)){
                    is AppResult.Success->{
                        editingFileId=result.value
                        initialTitle=state.title;initialBody=state.body
                        _uiState.update{it.copy(isNew=false,dirty=false)}
                        val queued=transferRepository.enqueueUpload(result.value)
                        _uiState.update{it.copy(saving=false)}
                        when(queued){
                            is AppResult.Success->{_messages.tryEmit(UiText.Resource(Res.string.note_saved));_saved.trySend(Unit)}
                            is AppResult.Failure->_messages.tryEmit(UiText.Plain("Saved on this device. Upload couldn't be queued; retry from the file's options."))
                        }
                    }
                    is AppResult.Failure->{_uiState.update{it.copy(saving=false)};_messages.tryEmit(result.error.toUiText())}
                }
            }catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){
                _uiState.update{it.copy(saving=false)}
                _messages.tryEmit(UiText.Plain(if(editingFileId!=null&&!_uiState.value.dirty)"Saved locally, but upload couldn't be queued."else"Couldn't save this note. Your draft is still here."))
            }
        }
    }
}
