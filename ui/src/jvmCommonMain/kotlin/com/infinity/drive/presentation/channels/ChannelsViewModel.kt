package com.infinity.drive.presentation.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.app_drive_recreated
import com.infinity.drive.resources.channels_creating_drive
import com.infinity.drive.resources.channels_deleting
import com.infinity.drive.resources.channels_looking_for_drives
import com.infinity.drive.resources.channels_opening_drive
import com.infinity.drive.resources.channels_removed_missing
import com.infinity.drive.resources.channels_renaming
import com.infinity.drive.resources.message_created_drive
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.domain.model.DriveChannel
import com.infinity.drive.domain.repository.ChannelRepository
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.presentation.common.toUiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelsViewModel(
    private val channelRepository: ChannelRepository
) : ViewModel() {

    private val _loading=MutableStateFlow(true)
    val loading:StateFlow<Boolean> = _loading.asStateFlow()
    private val _refreshing=MutableStateFlow(false)
    val refreshing:StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _error=MutableStateFlow<String?>(null)
    val error:StateFlow<String?> = _error.asStateFlow()
    private val observationRevision=MutableStateFlow(0)
    val channels: StateFlow<List<DriveChannel>> = observationRevision.flatMapLatest {
        channelRepository.observeChannels()
            .onEach{_loading.value=false}
            .catch{if(it is CancellationException)throw it;_loading.value=false;_error.value="Couldn't read your drives.";emit(emptyList())}
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _working = MutableStateFlow<UiText?>(null)
    val working: StateFlow<UiText?> = _working.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh(){
        if(_refreshing.value||_working.value!=null)return
        _refreshing.value=true;_error.value=null;observationRevision.value++
        viewModelScope.launch {
            try {
                val pruned=channelRepository.pruneDeleted()
                if(pruned is AppResult.Success&&pruned.value>0)_messages.tryEmit(UiText.Resource(Res.string.channels_removed_missing,pruned.value))
                when(val result=channelRepository.refresh()){
                    is AppResult.Success->Unit
                    is AppResult.Failure->{_error.value="Couldn't refresh your drives. Check the connection and retry.";_messages.tryEmit(result.error.toUiText())}
                }
            }catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){_error.value="Couldn't refresh your drives. Try again."}
            finally{_refreshing.value=false}
        }
    }

    fun create(label: String) = run(UiText.Resource(Res.string.channels_creating_drive)) {
        when (val result = channelRepository.create(label)) {
            is AppResult.Success -> {
                _messages.tryEmit(
                    UiText.Resource(Res.string.message_created_drive,
                        result.value.displayName
                    )
                )
                channelRepository.switchTo(result.value.chatId)
            }

            is AppResult.Failure -> result
        }
    }

    fun switchTo(chatId: Long) =
        run(UiText.Resource(Res.string.channels_opening_drive)) { channelRepository.switchTo(chatId) }

    fun rename(chatId: Long, label: String) = run(UiText.Resource(Res.string.channels_renaming)) {
        channelRepository.rename(chatId, label)
    }

    fun deleteRemotely(chatId: Long) = run(UiText.Resource(Res.string.channels_deleting)) {
        channelRepository.deleteRemotely(chatId)
    }

    private fun run(label: UiText, block: suspend () -> AppResult<*>) {
        if (_working.value != null) return
        _working.value = label
        viewModelScope.launch {
            try {
                val result=block()
                if(result is AppResult.Failure)_messages.tryEmit(result.error.toUiText())
            }catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){_messages.tryEmit(UiText.Plain("Couldn't complete this action. Your drives have been preserved."))}
            finally{_working.value=null}
        }
    }

}
