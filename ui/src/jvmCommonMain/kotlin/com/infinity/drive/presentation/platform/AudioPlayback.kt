package com.infinity.drive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.infinity.drive.domain.model.DriveFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AudioMetadata(val title:String?=null,val artist:String?=null,val durationMs:Long?=null,val artworkColors:List<Long> = emptyList(),val lyrics:String?=null)
data class AudioPlaybackState(val file:DriveFile?=null,val playing:Boolean=false,val loading:Boolean=false,val playRequested:Boolean=false,
    val error:String?=null,val metadata:AudioMetadata=AudioMetadata(),val positionMs:Long=0,val durationMs:Long=0,val canSkipPrevious:Boolean=false,val canSkipNext:Boolean=false)
interface AudioPlayback {
    val state:StateFlow<AudioPlaybackState>
    fun play(file:DriveFile)
    fun setQueue(ids:List<String>)
    fun toggle()
    fun pause()
    fun previous(){seek(0)}
    fun next()
    fun stop()
    fun seek(positionMs:Long)
    suspend fun metadata(file:DriveFile):AudioMetadata
}
val LocalAudioPlayback=staticCompositionLocalOf<AudioPlayback> {
    object:AudioPlayback {
        override val state=MutableStateFlow(AudioPlaybackState())
        override fun play(file:DriveFile){}
        override fun setQueue(ids:List<String>){}
        override fun toggle(){}
        override fun pause(){}
        override fun next(){}
        override fun stop(){}
        override fun seek(positionMs:Long){}
        override suspend fun metadata(file:DriveFile)=AudioMetadata()
    }
}
