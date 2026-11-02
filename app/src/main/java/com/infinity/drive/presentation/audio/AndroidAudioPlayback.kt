package com.infinity.drive.presentation.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.infinity.drive.core.media.TelegramDataSourceFactory
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.presentation.platform.AudioMetadata
import com.infinity.drive.presentation.platform.AudioPlayback
import com.infinity.drive.presentation.platform.AudioPlaybackState
import com.infinity.drive.presentation.preview.PreviewContent
import com.infinity.drive.presentation.preview.PreviewContentResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

/** A single player survives tab changes; resolving or dismissing a track cancels its old work. */
@androidx.annotation.OptIn(UnstableApi::class)
class AndroidAudioPlayback(context:Context,private val resolver:PreviewContentResolver,
    private val sources:TelegramDataSourceFactory,private val files:FileRepository):AudioPlayback {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val mutable=MutableStateFlow(AudioPlaybackState())
    override val state:StateFlow<AudioPlaybackState> = mutable.asStateFlow()
    private val player=ExoPlayer.Builder(context.applicationContext).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
        setHandleAudioBecomingNoisy(true)
    }
    private var resolveJob:Job?=null
    private var queueJob:Job?=null
    private var queue=emptyList<String>()
    private var requestedPlay=false
    private val metadataSlots=Semaphore(2)
    private val metadataCache=object:LinkedHashMap<String,AudioMetadata>(128,.75f,true){
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,AudioMetadata>?)=size>128
    }
    init {
        player.addListener(object:Player.Listener {
            override fun onIsPlayingChanged(isPlaying:Boolean){mutable.update{it.copy(playing=isPlaying)}}
            override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int){mutable.update{it.copy(playRequested=playWhenReady)}}
            override fun onPlaybackStateChanged(playbackState:Int){
                mutable.update{it.copy(loading=playbackState==Player.STATE_BUFFERING,durationMs=player.duration.coerceAtLeast(0))}
                if(playbackState==Player.STATE_ENDED)next()
            }
            override fun onMediaMetadataChanged(metadata:MediaMetadata){mutable.update{
                it.copy(metadata=it.metadata.copy(title=metadata.title?.toString()?:it.metadata.title,artist=metadata.artist?.toString()?:it.metadata.artist))
            }}
            override fun onPlayerError(error:PlaybackException){mutable.update{it.copy(playing=false,loading=false,error="Couldn't play this audio. Tap play to retry.")}}
        })
        scope.launch {
            state.map{it.file!=null}.distinctUntilChanged().collectLatest { active ->
                if(active)while(isActive){delay(750);mutable.update{it.copy(positionMs=player.currentPosition.coerceAtLeast(0),durationMs=player.duration.coerceAtLeast(0))}}
            }
        }
    }
    override fun play(file:DriveFile){
        resolveJob?.cancel();queueJob?.cancel()
        if(file.id !in queue)queue=emptyList()
        player.stop();player.clearMediaItems()
        requestedPlay=true
        mutable.value=AudioPlaybackState(file=file,loading=true,playRequested=true)
        resolveJob=scope.launch {
            try {
                mutable.update{it.copy(metadata=metadata(file))}
                val content=resolver.resolve(file).first { it is PreviewContent.LocalMedia || it is PreviewContent.StreamedMedia || it is PreviewContent.Failed || it is PreviewContent.Unsupported || it is PreviewContent.RequiresDownload }
                ensureActive()
                when(content){
                    is PreviewContent.LocalMedia->player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(content.path))))
                    is PreviewContent.StreamedMedia->{
                        val factory=if(content.parts.isNotEmpty())sources.createParted(content.parts,content.encrypted)else sources.create(content.remoteFileId)
                        player.setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri("telegram://audio/${file.id}")))
                    }
                    else->{mutable.update{it.copy(loading=false,error="Audio is unavailable. Open file options to download or retry.")};return@launch}
                }
                player.prepare();player.playWhenReady=requestedPlay
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){mutable.update{it.copy(loading=false,playing=false,error="Couldn't load this audio. Tap play to retry.")}}
        }
    }
    override fun setQueue(ids:List<String>){queue=ids}
    override fun toggle(){
        val current=mutable.value
        if(current.error!=null){current.file?.let(::play);return}
        if(current.loading&&player.mediaItemCount==0){requestedPlay=!requestedPlay;mutable.update{it.copy(playRequested=requestedPlay)};return}
        if(player.isPlaying||player.playWhenReady)pause()
        else {requestedPlay=true;if(player.playbackState==Player.STATE_ENDED)player.seekTo(0);player.play()}
    }
    override fun pause(){requestedPlay=false;player.pause();mutable.update{it.copy(playRequested=false)}}
    override fun next(){
        queueJob?.cancel()
        val index=queue.indexOf(mutable.value.file?.id)
        if(index<0||index>=queue.lastIndex)return
        val following=queue.drop(index+1)
        queueJob=scope.launch {
            for(id in following){val file=files.getFile(id);if(file!=null&&!file.isTrashed){
                // Clear the queue lookup before play, so play cannot cancel this coroutine.
                queueJob=null;play(file);return@launch
            }}
        }
    }
    override fun stop(){requestedPlay=false;resolveJob?.cancel();queueJob?.cancel();player.stop();player.clearMediaItems();queue=emptyList();mutable.value=AudioPlaybackState()}
    override fun seek(positionMs:Long){player.seekTo(positionMs.coerceIn(0,player.duration.coerceAtLeast(0)))}
    override suspend fun metadata(file:DriveFile):AudioMetadata {
        val path=file.localPath?:return AudioMetadata(durationMs=file.durationMs)
        val key="${file.id}:${file.modifiedAt}:$path"
        synchronized(metadataCache){metadataCache[key]}?.let{return it}
        val metadata=metadataSlots.withPermit { withContext(Dispatchers.IO){
            val retriever=MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                AudioMetadata(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:file.durationMs)
            } catch(_:Exception){AudioMetadata(durationMs=file.durationMs)}
            finally{runCatching{retriever.release()}}
        }}
        synchronized(metadataCache){metadataCache[key]=metadata}
        return metadata
    }
    fun release(){resolveJob?.cancel();scope.cancel();player.release()}
}
