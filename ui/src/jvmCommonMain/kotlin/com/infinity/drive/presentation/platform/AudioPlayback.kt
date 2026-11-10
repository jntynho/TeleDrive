package com.infinity.drive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.infinity.drive.domain.model.DriveFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AudioMetadata(val title:String?=null,val artist:String?=null,val durationMs:Long?=null,val artworkColors:List<Long> = emptyList(),val lyrics:String?=null,val artworkPath:String?=null,val resolved:Boolean=false,val failed:Boolean=false)
enum class AudioRepeatMode { OFF, ALL, ONE }
data class AudioPlaybackState(val file:DriveFile?=null,val playing:Boolean=false,val loading:Boolean=false,val playRequested:Boolean=false,
    val error:String?=null,val metadata:AudioMetadata=AudioMetadata(),val positionMs:Long=0,val durationMs:Long=0,val canSkipPrevious:Boolean=false,val canSkipNext:Boolean=false,
    val repeatMode:AudioRepeatMode=AudioRepeatMode.OFF,val sleepTimerMinutes:Int?=null,val queueRevision:Long=0,val queueError:String?=null)
interface AudioPlayback {
    val state:StateFlow<AudioPlaybackState>
    fun play(file:DriveFile)
    fun containsQueuedFile(id:String):Boolean = false
    fun setQueue(ids:List<String>)
    fun toggle()
    fun pause()
    fun previous(){seek(0)}
    fun next()
    fun stop()
    fun seek(positionMs:Long)
    fun updateLibraryContext(chatId:Long?,signedIn:Boolean) {}
    fun cycleRepeat() {}
    fun setSleepTimer(minutes:Int?) {}
    suspend fun queuedFiles():List<DriveFile> = emptyList()
    suspend fun basicMetadata(file:DriveFile):AudioMetadata = metadata(file)
    suspend fun metadata(file:DriveFile):AudioMetadata
}
val LocalOpenAudioPlayer=staticCompositionLocalOf<((String)->Unit)?>{null}

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
