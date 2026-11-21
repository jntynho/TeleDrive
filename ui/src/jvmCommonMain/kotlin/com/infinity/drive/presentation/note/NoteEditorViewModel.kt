package com.infinity.drive.presentation.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.infinity.drive.resources.Res
import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.SafeLog
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.TransferState
import com.infinity.drive.domain.model.TransferType
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
    val attaching: Boolean = false,
    val uploadState: BackupState? = null,
    val pendingSync: Boolean = false
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
    private var noteChatId: Long? = null
    private var uploadJob: Job? = null
    private var pendingJob: Job? = null
    val canAttach: Boolean get() = importer != null

    init {
        val fileId = route.fileId
        if (fileId == null && draftStore == null) {
            val body = route.sharedText.orEmpty()
            _uiState.update { it.copy(title = initialTitle, body = body, loading = false, dirty = body.isNotEmpty()) }
        } else {
            viewModelScope.launch {
                val chat = settings?.preferences?.first()?.storageChatId
                noteChatId = chat
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
            check(access?.canRead(fileId) != false)
            val pending = draftSlot?.let { draftStore?.read("$it:pending") }
            val draft = readDraft()
            if (pending != null) {
                initialTitle = pending.title
                initialBody = pending.body
            } else {
                when (val result = fileRepository.readNote(fileId)) {
                    is AppResult.Success -> {
                        initialTitle = route.title ?: fileRepository.getFile(fileId)?.name.orEmpty()
                        if (initialTitle.endsWith(".md", ignoreCase = true)) initialTitle = initialTitle.dropLast(3)
                        initialBody = result.value
                    }
                    is AppResult.Failure -> {
                        if (draft == null) {
                            _uiState.update { it.copy(loading = false, loadError = "Couldn't open this note. Your original file is unchanged.") }
                            return
                        }
                    }
                }
            }
            val title = draft?.title ?: initialTitle
            val body = draft?.body ?: initialBody
            _uiState.update {
                it.copy(title = title, body = body, loading = false,
                    dirty = title != initialTitle || body != initialBody, loadError = null,
                    pendingSync = pending != null)
            }
            observeUpload(fileId)
            if (pending != null) watchPending(fileId)
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

    fun save() {
        val state = _uiState.value
        if (state.saving || state.attaching || state.loading || state.loadError != null ||
            state.body.isBlank() || (!state.dirty && !state.isNew)
        ) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch { persist(NoteDraft(state.title, state.body), explicit = true) }
    }

    private fun allowedToSync(): Boolean =
        access?.notesLock?.state?.value?.restricted != true

    private suspend fun persist(snapshot: NoteDraft, explicit: Boolean): Boolean {
        try {
            check(allowedToSync())
            val expectedDrive = noteChatId
            check(expectedDrive == null || settings?.preferences?.first()?.storageChatId == expectedDrive)
            val id = editingFileId
            when (val result = fileRepository.saveNote(id, route.folderId, snapshot.title.trim(), snapshot.body)) {
                is AppResult.Success -> {
                    val oldSlot = draftSlot
                    editingFileId = result.value
                    if (oldSlot != null) {
                        draftJob?.cancelAndJoin()
                        try {
                            draftStore?.clear("$oldSlot:pending")
                            draftStore?.clear(oldSlot)
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            // Local save is committed; draft cleanup must not stop its upload.
                        }
                    }
                    draftSlot = noteChatId?.let { "$it:note:${result.value}" }
                    acceptSnapshot(snapshot, pending = false)
                    _uiState.update { it.copy(uploadState = BackupState.NONE) }
                    observeUpload(result.value)
                    if (_uiState.value.dirty) scheduleDraft()
                    enqueueSync(result.value)
                    if (explicit) _saved.trySend(Unit)
                }
                is AppResult.Failure -> {
                    val slot = draftSlot
                    val store = draftStore
                    if (result.error == AppError.NoteTransferInProgress && id != null && slot != null && store != null) {
                        if (explicit) {
                            draftJob?.cancelAndJoin()
                            store.write("$slot:pending", snapshot)
                            store.clear(slot)
                            acceptSnapshot(snapshot, pending = true)
                            if (_uiState.value.dirty) scheduleDraft()
                            _saved.trySend(Unit)
                            watchPending(id)
                        } else return false
                    } else {
                        if (!explicit) _uiState.update { it.copy(uploadState = BackupState.FAILED) }
                        _messages.tryEmit(result.error.toUiText())
                    }
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            if (!explicit) _uiState.update { it.copy(uploadState = BackupState.FAILED) }
            _messages.tryEmit(UiText.Resource(Res.string.note_draft_failed))
        } finally {
            _uiState.update { it.copy(saving = false) }
        }
        return true
    }

    private fun acceptSnapshot(snapshot: NoteDraft, pending: Boolean) {
        initialTitle = snapshot.title
        initialBody = snapshot.body
        _uiState.update {
            it.copy(isNew = false, pendingSync = pending, dirty = it.title != initialTitle || it.body != initialBody)
        }
    }

    private fun observeUpload(id: String) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            val files = flow { emitAll(fileRepository.observeFile(id)) }
                .catch { SafeLog.w("NoteEditor", "Could not observe note upload", it) }
            val transfers = flow { emitAll(transferRepository.observeLatestForFile(id)) }
                .catch { emit(null) }
            combine(files, transfers) { file, task ->
                val upload = task?.takeIf { it.type == TransferType.UPLOAD }
                when (upload?.state) {
                    TransferState.FAILED -> BackupState.FAILED
                    TransferState.QUEUED, TransferState.PAUSED -> BackupState.QUEUED
                    TransferState.RUNNING -> BackupState.UPLOADING
                    TransferState.CANCELLED -> BackupState.NONE
                    else -> file?.backupState
                }
            }.collect { state ->
                if (state != null) _uiState.update { it.copy(uploadState = state) }
            }
        }
    }

    private fun watchPending(id: String) {
        pendingJob?.cancel()
        pendingJob = viewModelScope.launch {
            try {
                do {
                    transferRepository.observeUnfinishedForFile(id).first { !it }
                    _uiState.first { !it.saving }
                    if (!allowedToSync()) return@launch
                    _uiState.update { it.copy(saving = true) }
                    val promoted = try {
                        val pending = draftSlot?.let { draftStore?.read("$it:pending") } ?: return@launch
                        persist(pending, explicit = false)
                    } finally {
                        _uiState.update { it.copy(saving = false) }
                    }
                    if (promoted) return@launch
                    delay(250)
                } while (_uiState.value.pendingSync)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                _uiState.update { it.copy(uploadState = BackupState.FAILED) }
                _messages.tryEmit(UiText.Resource(Res.string.note_draft_failed))
            }
        }
    }

    fun retrySync() {
        val id = editingFileId ?: return
        if (_uiState.value.saving || !allowedToSync()) return
        if (_uiState.value.pendingSync) {
            watchPending(id)
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                enqueueSync(id)
            } finally {
                _uiState.update { it.copy(saving = false) }
            }
        }
    }

    private suspend fun enqueueSync(id: String) {
        val queued = try {
            transferRepository.enqueueUpload(id).isSuccess
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            false
        }
        _uiState.update { it.copy(uploadState = if (queued) BackupState.QUEUED else BackupState.FAILED) }
    }
}
