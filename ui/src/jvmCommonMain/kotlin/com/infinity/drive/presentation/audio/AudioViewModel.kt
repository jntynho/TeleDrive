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
import java.util.UUID

enum class AudioFilter { ALL,FAVORITES,ON_DEVICE }
@OptIn(ExperimentalCoroutinesApi::class,FlowPreview::class)
class AudioViewModel(private val handle:SavedStateHandle,private val files:FileRepository,private val settings:SettingsRepository):ViewModel() {
    val query=handle.getStateFlow("audioQuery","")
    val filter=handle.getStateFlow("audioFilter",AudioFilter.ALL)
    fun search(value:String){handle["audioQuery"]=value}
    fun filter(value:AudioFilter){handle["audioFilter"]=value}
    val preferences=settings.preferences.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),UserPreferences())
    val appliedQuery=query.debounce { if(it.isBlank())0L else 180L }.stateIn(viewModelScope,SharingStarted.Eagerly,query.value)
    private val spec=combine(appliedQuery,filter,settings.preferences){text,filter,prefs->
        FileQuerySpec(categories=listOf(FileCategory.AUDIO),nameQuery=text.trim().takeIf{it.isNotEmpty()},
            favoritesOnly=filter==AudioFilter.FAVORITES,localCopiesOnly=filter==AudioFilter.ON_DEVICE,
            sortField=prefs.audioSortField,sortDirection=prefs.audioSortDirection)
    }.distinctUntilChanged()
    val audio=spec.flatMapLatest{files.pagedFiles(it)}.cachedIn(viewModelScope)
    fun sort(field:FileSortField,direction:SortDirection){viewModelScope.launch{settings.update{it.copy(audioSortField=field,audioSortDirection=direction)}}}
    fun favorite(file:DriveFile){viewModelScope.launch{files.setFilesFavorite(listOf(file.id),!file.isFavorite)}}
    val playlists = settings.preferences.map { prefs ->
        prefs.audioPlaylists.filter { it.chatId == prefs.storageChatId }.sortedBy { it.name.lowercase() }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun playlistTracks(playlist: AudioPlaylist): Flow<List<DriveFile>> =
        settings.preferences.map { it.storageChatId }.distinctUntilChanged().flatMapLatest { chat ->
            if (chat != playlist.chatId) flowOf(emptyList())
            else files.observeFiles(FileQuerySpec(categories = listOf(FileCategory.AUDIO), showHidden = true, showArchived = true))
                .map { rows ->
                    val visible = rows.filter { it.chatId == chat && !it.isTrashed }.associateBy { it.id }
                    playlist.trackIds.mapNotNull(visible::get)
                }
        }

    suspend fun savePlaylist(name: String, trackIds: List<String>): Boolean {
        val title = name.trim()
        if (title.length !in 1..60) return false
        val chat = settings.preferences.first().storageChatId ?: return false
        val candidate = AudioPlaylist(UUID.randomUUID().toString(), chat, title, trackIds.distinct())
        val visible = playlistTracks(candidate).first().map { it.id }
        if (visible.isEmpty()) return false
        settings.update { prefs ->
            if (prefs.storageChatId != chat || prefs.audioPlaylists.any { it.chatId == chat && it.name.equals(title, true) }) prefs
            else prefs.copy(audioPlaylists = prefs.audioPlaylists + candidate.copy(trackIds = visible))
        }
        return settings.preferences.first().audioPlaylists.any { it.id == candidate.id }
    }

    fun playPlaylist(playlist: AudioPlaylist, player: AudioPlayback) {
        queueJob?.cancel()
        queueJob = viewModelScope.launch {
            val tracks = playlistTracks(playlist).first()
            val first = tracks.firstOrNull() ?: return@launch
            if (settings.preferences.first().storageChatId != playlist.chatId) return@launch
            player.play(first)
            player.setQueue(tracks.map { it.id })
        }
    }

    fun addToPlaylist(playlist: AudioPlaylist, file: DriveFile) {
        if (file.chatId != playlist.chatId || file.category != FileCategory.AUDIO || file.isTrashed) return
        changePlaylist(playlist) { it.copy(trackIds = (it.trackIds + file.id).distinct()) }
    }

    fun removePlaylistTrack(playlist: AudioPlaylist, fileId: String) =
        changePlaylist(playlist) { it.copy(trackIds = it.trackIds - fileId) }

    fun movePlaylistTrack(playlist: AudioPlaylist, fileId: String, direction: Int) =
        changePlaylist(playlist) { it.copy(trackIds = it.trackIds.movePlaylistTrack(fileId, direction)) }

    fun deletePlaylist(playlist: AudioPlaylist) = changePlaylist(playlist) { null }

    private fun changePlaylist(playlist: AudioPlaylist, transform: (AudioPlaylist) -> AudioPlaylist?) {
        viewModelScope.launch {
            settings.update { prefs ->
                if (prefs.storageChatId != playlist.chatId) prefs
                else prefs.copy(audioPlaylists = prefs.audioPlaylists.mapNotNull {
                    if (it.id == playlist.id && it.chatId == playlist.chatId) transform(it) else it
                })
            }
        }
    }

    private var queueJob:Job?=null
    fun play(file:DriveFile,player:AudioPlayback){
        if(player.state.value.file?.id!=file.id)player.play(file)
        else if(!player.state.value.playRequested)player.toggle()
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
