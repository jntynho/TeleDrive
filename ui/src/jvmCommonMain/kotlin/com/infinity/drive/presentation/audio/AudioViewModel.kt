package com.infinity.drive.presentation.audio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.*
import com.infinity.drive.presentation.platform.AudioPlayback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class AudioFilter { ALL,FAVORITES,ON_DEVICE }
@OptIn(ExperimentalCoroutinesApi::class,FlowPreview::class)
class AudioViewModel(private val handle:SavedStateHandle,private val files:FileRepository,private val settings:SettingsRepository):ViewModel() {
    val query=handle.getStateFlow("audioQuery","")
    val filter=handle.getStateFlow("audioFilter",AudioFilter.ALL)
    fun search(value:String){handle["audioQuery"]=value}
    fun filter(value:AudioFilter){handle["audioFilter"]=value}
    val preferences=settings.preferences.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),UserPreferences())
    private val spec=combine(query.debounce(180),filter,settings.preferences){text,filter,prefs->
        FileQuerySpec(categories=listOf(FileCategory.AUDIO),nameQuery=text.trim().takeIf{it.isNotEmpty()},
            favoritesOnly=filter==AudioFilter.FAVORITES,localCopiesOnly=filter==AudioFilter.ON_DEVICE,
            sortField=prefs.audioSortField,sortDirection=prefs.audioSortDirection)
    }.distinctUntilChanged()
    val audio=spec.flatMapLatest{files.pagedFiles(it)}.cachedIn(viewModelScope)
    fun sort(field:FileSortField,direction:SortDirection){viewModelScope.launch{settings.update{it.copy(audioSortField=field,audioSortDirection=direction)}}}
    fun favorite(file:DriveFile){viewModelScope.launch{files.setFilesFavorite(listOf(file.id),!file.isFavorite)}}
    private var queueJob:Job?=null
    fun play(file:DriveFile,player:AudioPlayback){
        if(player.state.value.file?.id!=file.id)player.play(file)
        queueJob?.cancel()
        queueJob=viewModelScope.launch{
            try {
                val ids=files.fileIds(spec.first())
                if(player.state.value.file?.id==file.id)player.setQueue(ids)
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){ /* Playback remains usable with its current queue. */ }
        }
    }

}
