package com.infinity.drive.presentation.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.note_saved
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.files.FileImporter
import com.infinity.drive.core.files.NoteDraft
import com.infinity.drive.core.files.NoteDraftStore
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.resources.note_attachment_failed
import com.infinity.drive.resources.note_attachment_waiting
import com.infinity.drive.resources.note_draft_failed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    val loadError: String? = null,
    val attaching: Boolean = false
)

class NoteEditorViewModel(
    private val fileRepository: FileRepository,
    private val transferRepository: TransferRepository,
    savedStateHandle: SavedStateHandle,
    private val draftStore: NoteDraftStore? = null,
    private val settings: SettingsRepository? = null,
    private val importer: FileImporter? = null,
    private val access: HiddenContentAccess? = null
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.NoteEditor>()

    private val _uiState = MutableStateFlow(NoteEditorUiState(isNew = route.fileId == null))
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    private val _saved = Channel<Unit>(Channel.CONFLATED)
    val saved = _saved.receiveAsFlow()
    private var editingFileId = route.fileId
    private var initialTitle = route.title.orEmpty()
    private var initialBody = ""
    private var draftJob: Job? = null
    private var draftSlot: String? = null
    val canAttach: Boolean get() = importer != null

    init {
        val fileId = route.fileId
        if (fileId == null && draftStore == null) {
            val body = route.sharedText.orEmpty()
            _uiState.update { it.copy(title = initialTitle, body = body, loading = false, dirty = body.isNotEmpty()) }
        } else {
            viewModelScope.launch {
                val chat = settings?.preferences?.first()?.storageChatId
                draftSlot = chat?.let {
                    when {
                        fileId != null -> "$it:note:$fileId"
                        !route.sharedText.isNullOrBlank() -> "$it:share:${route.folderId}:${route.sharedText}"
                        else -> "$it:new:${route.folderId.orEmpty()}"
                    }
                }
                if (fileId == null) {
                    val draft = readDraft()
                    val body = draft?.body ?: route.sharedText.orEmpty()
                    val title = draft?.title ?: initialTitle
                    _uiState.update { it.copy(
                        title = title, body = body, loading = false,
                        dirty = body.isNotEmpty() || title != initialTitle
                    ) }
                } else load(fileId)
            }
        }
    }

    private suspend fun readDraft(): NoteDraft? = draftSlot?.let { draftStore?.read(it) }

    private suspend fun load(fileId: String) {
        try {
            when (val result = fileRepository.readNote(fileId)) {
                is AppResult.Success -> {
                    initialTitle = route.title ?: fileRepository.getFile(fileId)?.name.orEmpty()
                    if (initialTitle.endsWith(".md", ignoreCase = true)) initialTitle = initialTitle.dropLast(3)
                    initialBody = result.value
                    val draft = readDraft()
                    val title = draft?.title ?: initialTitle
                    val body = draft?.body ?: initialBody
                    _uiState.update {
                        it.copy(title = title, body = body, loading = false,
                            dirty = title != initialTitle || body != initialBody, loadError = null)
                    }
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
    fun setTitle(value: String) {
        _uiState.update { it.copy(title = value, dirty = value != initialTitle || it.body != initialBody) }
        scheduleDraft()
    }

    fun setBody(value: String) {
        _uiState.update { it.copy(body = value, dirty = it.title != initialTitle || value != initialBody) }
        scheduleDraft()
    }

    private fun scheduleDraft() {
        val slot = draftSlot ?: return
        val state = _uiState.value
        val prior = draftJob?.also { it.cancel() }
        draftJob = viewModelScope.launch {
            prior?.join()
            delay(250)
            try {
                writeDraft(slot, state)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keeping a draft explicitly reports any persistence error.
            }
        }
    }

    private suspend fun writeDraft(slot: String, state: NoteEditorUiState) {
        if (state.dirty && (state.body.isNotEmpty() || state.title.isNotEmpty())) {
            draftStore?.write(slot, NoteDraft(state.title, state.body))
        } else {
            draftStore?.clear(slot)
        }
    }

    fun leaveEditor(keepDraft: Boolean, onDone: () -> Unit) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                draftJob?.cancelAndJoin()
                draftSlot?.let {
                    if (keepDraft) writeDraft(it, _uiState.value) else draftStore?.clear(it)
                }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _messages.tryEmit(UiText.Resource(Res.string.note_draft_failed))
            } finally {
                _uiState.update { it.copy(saving = false) }
            }
        }
    }

    fun addAttachment(reference: String) {
        val fileImporter = importer ?: return
        if (_uiState.value.attaching || _uiState.value.saving) return
        _uiState.update { it.copy(attaching = true) }
        viewModelScope.launch {
            try {
                val active = settings?.preferences?.first()?.storageChatId
                val source = withContext(Dispatchers.IO) { fileImporter.describe(reference) }
                if (source == null || active != settings?.preferences?.first()?.storageChatId ||
                    access?.notesLock?.state?.value?.restricted == true) {
                    _messages.tryEmit(UiText.Resource(Res.string.note_attachment_failed))
                    return@launch
                }
                when (val result = fileRepository.registerImport(source, route.folderId, source.name, allowDuplicate = true)) {
                    is AppResult.Failure -> _messages.tryEmit(result.error.toUiText())
                    is AppResult.Success -> {
                        val file = result.value
                        val link = NoteExtras.attachmentLink(file.name, file.id)
                        setBody(listOf(_uiState.value.body.trimEnd(), link).filter { it.isNotBlank() }.joinToString("\n\n"))
                        if (!file.hasRemoteCopy && !transferRepository.enqueueUpload(file.id).isSuccess) {
                            _messages.tryEmit(UiText.Resource(Res.string.note_attachment_waiting))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _messages.tryEmit(UiText.Resource(Res.string.note_attachment_failed))
            } finally {
                _uiState.update { it.copy(attaching = false) }
            }
        }
    }

    fun save(){
        val state=_uiState.value
        if(state.saving||state.attaching||state.loading||state.loadError!=null||state.body.isBlank()||(!state.dirty&&!state.isNew))return
        _uiState.update{it.copy(saving=true)}
        viewModelScope.launch{
            try {
                when(val result=fileRepository.saveNote(editingFileId,route.folderId,state.title.trim(),state.body)){
                    is AppResult.Success->{
                        editingFileId=result.value
                        initialTitle=state.title;initialBody=state.body
                        draftJob?.cancelAndJoin()
                        try { draftSlot?.let { draftStore?.clear(it) } }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { /* The note itself was saved. */ }
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
