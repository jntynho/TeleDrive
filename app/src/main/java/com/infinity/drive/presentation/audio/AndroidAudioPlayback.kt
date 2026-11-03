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
import com.infinity.drive.presentation.platform.AudioRepeatMode
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
    private var sleepJob:Job?=null
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
                if(playbackState==Player.STATE_ENDED&&requestedPlay&&player.playWhenReady){
                    if(mutable.value.repeatMode==AudioRepeatMode.ONE){player.seekTo(0);player.play()}
                    else next()
                }
            }
            override fun onMediaMetadataChanged(metadata:MediaMetadata){
                mutable.update{it.copy(metadata=it.metadata.copy(title=metadata.title?.toString()?:it.metadata.title,artist=metadata.artist?.toString()?:it.metadata.artist))}
                metadata.artworkData?.takeIf{it.size<=5*1024*1024}?.let{picture->
                    val track=mutable.value.file?.id
                    scope.launch {
                        val colors=withContext(Dispatchers.Default){artworkColors(picture)}
                        mutable.update{if(it.file?.id==track&&colors.isNotEmpty())it.copy(metadata=it.metadata.copy(artworkColors=colors))else it}
                    }
                }
            }
            override fun onTracksChanged(tracks:androidx.media3.common.Tracks){
                for(group in tracks.groups)for(index in 0 until group.length)if(group.isTrackSelected(index)){
                    group.getTrackFormat(index).metadata?.let(::acceptLyricsMetadata)
                }
            }
            override fun onMetadata(metadata:androidx.media3.common.Metadata){acceptLyricsMetadata(metadata)}
            override fun onPlayerError(error:PlaybackException){mutable.update{it.copy(playing=false,loading=false,error="Couldn't play this audio. Tap play to retry.")}}
        })
        scope.launch {
            state.map{it.file!=null}.distinctUntilChanged().collectLatest { active ->
                if(active)while(isActive){delay(if(mutable.value.playing&&mutable.value.metadata.lyrics!=null)150 else 500);mutable.update{it.copy(positionMs=player.currentPosition.coerceAtLeast(0),durationMs=player.duration.coerceAtLeast(0))}}
            }
        }
    }
    private fun acceptLyricsMetadata(metadata:androidx.media3.common.Metadata){
        val track=mutable.value.file?.id?:return
        scope.launch {
            val lyrics=withContext(Dispatchers.Default){streamedLyrics(metadata)}?:return@launch
            mutable.update{if(it.file?.id==track)it.copy(metadata=it.metadata.copy(lyrics=lyrics))else it}
        }
    }
    override fun play(file:DriveFile){
        resolveJob?.cancel();queueJob?.cancel()
        if(file.id !in queue)queue=emptyList()
        player.stop();player.clearMediaItems()
        requestedPlay=true
        mutable.value=AudioPlaybackState(file=file,loading=true,playRequested=true,repeatMode=mutable.value.repeatMode,sleepTimerMinutes=mutable.value.sleepTimerMinutes)
        setQueue(queue)
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
    override fun setQueue(ids:List<String>){
        queue=ids
        val index=queue.indexOf(mutable.value.file?.id)
        mutable.update{it.copy(canSkipPrevious=index>0,canSkipNext=index>=0&&(index<queue.lastIndex||it.repeatMode==AudioRepeatMode.ALL))}
    }
    override fun toggle(){
        val current=mutable.value
        if(current.error!=null){current.file?.let(::play);return}
        if(current.loading&&player.mediaItemCount==0){requestedPlay=!requestedPlay;mutable.update{it.copy(playRequested=requestedPlay)};return}
        if(player.isPlaying||player.playWhenReady)pause()
        else {requestedPlay=true;if(player.playbackState==Player.STATE_ENDED)player.seekTo(0);player.play()}
    }
    override fun pause(){requestedPlay=false;player.pause();mutable.update{it.copy(playRequested=false)}}
    override fun previous(){if(player.currentPosition>3000){seek(0);return};skip(-1)}
    override fun next()=skip(1)
    private fun skip(direction:Int){
        queueJob?.cancel()
        val index=queue.indexOf(mutable.value.file?.id)
        if(index<0)return
        val following=if(direction>0)queue.drop(index+1)+(if(mutable.value.repeatMode==AudioRepeatMode.ALL)queue.take(index+1)else emptyList())else queue.take(index).asReversed()
        queueJob=scope.launch{
            for(id in following){val file=files.getFile(id);if(file!=null&&!file.isTrashed){queueJob=null;play(file);return@launch}}
        }
    }
    override fun stop(){sleepJob?.cancel();sleepJob=null;requestedPlay=false;resolveJob?.cancel();queueJob?.cancel();player.stop();player.clearMediaItems();queue=emptyList();mutable.value=AudioPlaybackState()}
    override fun seek(positionMs:Long){val target=positionMs.coerceIn(0,player.duration.coerceAtLeast(0));player.seekTo(target);mutable.update{it.copy(positionMs=target)}}
    override fun cycleRepeat(){
        mutable.update{it.copy(repeatMode=AudioRepeatMode.entries[(it.repeatMode.ordinal+1)%AudioRepeatMode.entries.size])}
        setQueue(queue)
    }
    override fun setSleepTimer(minutes:Int?){
        sleepJob?.cancel();sleepJob=null
        val duration=minutes?.coerceIn(1,180)
        mutable.update{it.copy(sleepTimerMinutes=duration)}
        if(duration!=null)sleepJob=scope.launch{delay(duration*60_000L);pause();mutable.update{it.copy(sleepTimerMinutes=null)}}
    }
    override suspend fun queuedFiles():List<DriveFile> = queue.drop(queue.indexOf(mutable.value.file?.id).coerceAtLeast(0)).take(100).mapNotNull{files.getFile(it)}.filterNot{it.isTrashed}
    override suspend fun metadata(file:DriveFile):AudioMetadata {
        val path=file.localPath?:return AudioMetadata(durationMs=file.durationMs)
        val key="${file.id}:${file.modifiedAt}:$path"
        synchronized(metadataCache){metadataCache[key]}?.let{return it}
        val metadata=metadataSlots.withPermit { withContext(Dispatchers.IO){
            val retriever=MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                run {
                    val picture=retriever.embeddedPicture?.takeIf{it.size<=5*1024*1024}
                    val colors=picture?.let(::artworkColors).orEmpty()
                    AudioMetadata(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:file.durationMs,colors,readEmbeddedLyrics(File(path)))
                }
            } catch(_:Exception){AudioMetadata(durationMs=file.durationMs,lyrics=readEmbeddedLyrics(File(path)))}
            finally{runCatching{retriever.release()}}
        }}
        synchronized(metadataCache){metadataCache[key]=metadata}
        return metadata
    }
    fun release(){resolveJob?.cancel();scope.cancel();player.release()}
}

/** Millisecond SYLT takes priority over plain USLT; MPEG-frame timestamps cannot be guessed. */
private fun readId3Lyrics(file:File):String?=runCatching {file.inputStream().buffered().use(::readId3Lyrics)}.getOrNull()

private fun readId3Lyrics(input:java.io.InputStream):String?=runCatching {
    input.use { input ->
        val header=input.readBounded(10)
        if(header.size!=10||String(header,0,3,Charsets.ISO_8859_1)!="ID3")return@use null
        val version=header[3].toInt()
        if(version !in 3..4 || header[5].toInt() and 0xC0 != 0)return@use null
        val size=(6..9).fold(0){a,i->(a shl 7) or (header[i].toInt() and 127)}
        val bytes=input.readBounded(size.coerceAtMost(1024*1024));var offset=0
        var plain:String?=null
        while(offset+10<=bytes.size){
            val id=String(bytes,offset,4,Charsets.ISO_8859_1)
            val length=(4..7).fold(0){a,i->(a shl if(version==4)7 else 8) or (bytes[offset+i].toInt() and if(version==4)127 else 255)}
            if(length<=0||length>bytes.size-offset-10)break
            val flags=bytes[offset+9].toInt() and 255
            if(flags and (if(version==4)0x4F else 0xE0)==0 && id in listOf("USLT","SYLT")&&length>4){
                val frame=bytes.copyOfRange(offset+10,offset+10+length)
                val encoding=frame[0].toInt() and 255
                if(encoding in 0..3){
                    val descriptorStart=if(id=="SYLT")6 else 4
                    val charset=when(encoding){1->if(frame.getOrNull(descriptorStart)==0xFF.toByte()&&frame.getOrNull(descriptorStart+1)==0xFE.toByte())Charsets.UTF_16LE else Charsets.UTF_16BE;2->Charsets.UTF_16BE;3->Charsets.UTF_8;else->Charsets.ISO_8859_1}
                    fun terminated(start:Int):Pair<String,Int>? {
                        val unit=if(encoding in 1..2)2 else 1
                        var end=start
                        while(end+unit<=frame.size){
                            if(frame[end].toInt()==0&&(unit==1||frame[end+1].toInt()==0))return String(frame,start,end-start,charset).removePrefix("\uFEFF") to end+unit
                            end+=unit
                        };return null
                    }
                    if(id=="USLT"){
                        val description=terminated(4)
                        if(description!=null)plain=String(frame,description.second,frame.size-description.second,charset)
                            .trim('\u0000','\uFEFF',' ','\n','\r').take(32000).takeIf{it.isNotBlank()}?:plain
                    }else if(frame.size>6&&frame[4].toInt()==2){
                        var cursor=terminated(6)?.second?:frame.size
                        val lines=mutableListOf<String>();var total=0
                        var startTime:Long?=null
                        val current=StringBuilder()
                        fun stamp(time:Long)="%02d:%02d:%02d.%03d".format(java.util.Locale.ROOT,time/3_600_000,time/60_000%60,time/1000%60,time%1000)
                        fun flush(){if(startTime!=null&&current.isNotEmpty())lines+="[${stamp(startTime!!)}]$current";current.clear();startTime=null}
                        while(cursor<frame.size&&lines.size<1024){
                            val text=terminated(cursor)?:break;cursor=text.second
                            if(cursor+4>frame.size)break
                            val time=(0..3).fold(0L){a,i->(a shl 8) or (frame[cursor+i].toLong() and 255)};cursor+=4
                            if(time>86_400_000)continue
                            val value=text.first.replace("\r\n","\n").replace('\r','\n').take(4000)
                            total+=value.length+24
                            if(total>30000)break
                            value.split('\n').forEachIndexed { index, part ->
                                if(index>0)flush()
                                if(part.isNotEmpty()){
                                    if(startTime==null)startTime=time
                                    current.append("<${stamp(time)}>").append(part)
                                }
                            }
                        }
                        flush()
                        if(lines.isNotEmpty())return@use lines.joinToString("\n").take(32000)
                    }
                }
            };offset+=10+length
        };plain
    }
}.getOrNull()

private fun java.io.InputStream.readBounded(count:Int):ByteArray {
    val bytes=ByteArray(count);var offset=0
    while(offset<count){val read=read(bytes,offset,count-offset);if(read<0)break;if(read==0)continue;offset+=read}
    return if(offset==count)bytes else bytes.copyOf(offset)
}

private fun readEmbeddedLyrics(file:File):String? = readId3Lyrics(file) ?: runCatching {
    java.io.RandomAccessFile(file,"r").use { input ->
        val signature=ByteArray(4);if(input.length()<4)return@use null;input.readFully(signature)
        if(String(signature,Charsets.ISO_8859_1)=="fLaC"){
            var last=false
            while(!last&&input.filePointer+4<=input.length()){
                val type=input.readUnsignedByte();last=type and 128!=0
                val count=(input.readUnsignedByte() shl 16) or (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
                if(count>1024*1024||count>input.length()-input.filePointer)return@use null
                if(type and 127==4){
                    val bytes=ByteArray(count);input.readFully(bytes)
                    val buffer=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    fun text():String {val length=buffer.int;require(length>=0&&length<=buffer.remaining());val value=ByteArray(length);buffer.get(value);return String(value,Charsets.UTF_8)}
                    text();val comments=buffer.int.coerceIn(0,10000)
                    repeat(comments){val comment=text();if(comment.substringBefore('=').uppercase() in listOf("LYRICS","UNSYNCEDLYRICS","SYNCEDLYRICS"))return@use comment.substringAfter('=').take(32000)}
                }else input.seek(input.filePointer+count)
            };return@use null
        }
        fun atoms(start:Long,end:Long,depth:Int,lyric:Boolean=false):String? {
            if(depth>8)return null
            var offset=start;var boxes=0
            while(offset+8<=end&&boxes++<10000){
                input.seek(offset);val length=input.readInt().toLong() and 0xffffffffL
                val typeBytes=ByteArray(4);input.readFully(typeBytes);val type=String(typeBytes,Charsets.ISO_8859_1)
                if(length<8||length>end-offset)return null
                val content=offset+8
                if(lyric&&type=="data"&&length>16){
                    input.seek(content+8);val text=ByteArray((length-16).coerceAtMost(32000).toInt());input.readFully(text);return String(text,Charsets.UTF_8).trim('\u0000')
                }
                if(type in listOf("moov","udta","meta","ilst","©lyr")){
                    atoms(content+if(type=="meta")4 else 0,offset+length,depth+1,lyric||type=="©lyr")?.let{return it}
                }
                offset+=length
            };return null
        }
        atoms(0,input.length(),0)
    }
}.getOrNull()?.takeIf{it.isNotBlank()}

/** Convert existing extractor entries only; timed lyrics win over an untimed fallback. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun streamedLyrics(metadata:androidx.media3.common.Metadata):String? {
    var plain:String?=null
    for(index in 0 until minOf(metadata.length(),256)){
        val entry=metadata[index]
        val text=when(entry){
            is androidx.media3.extractor.metadata.id3.BinaryFrame->if(entry.id in listOf("USLT","SYLT")&&entry.data.size<=1024*1024){
                val length=entry.data.size
                val frame=java.nio.ByteBuffer.allocate(length+10).put(entry.id.toByteArray(Charsets.ISO_8859_1)).putInt(length).putShort(0).put(entry.data).array()
                val count=frame.size
                val header="ID3".toByteArray()+byteArrayOf(3,0,0,((count shr 21)and 127).toByte(),((count shr 14)and 127).toByte(),((count shr 7)and 127).toByte(),(count and 127).toByte())
                readId3Lyrics((header+frame).inputStream())
            }else null
            is androidx.media3.extractor.metadata.vorbis.VorbisComment->if(entry.key.uppercase() in listOf("LYRICS","UNSYNCEDLYRICS","SYNCEDLYRICS"))entry.value.take(32000)else null
            is androidx.media3.extractor.metadata.id3.TextInformationFrame->if(entry.id=="TXXX"&&entry.description?.uppercase() in listOf("LYRICS","UNSYNCEDLYRICS","SYNCEDLYRICS"))entry.values.firstOrNull()?.take(32000)else null
            else->null
        }
        if(!text.isNullOrBlank()){
            if(AudioLyricsParser.parse(text).cues.isNotEmpty())return text
            if(plain==null)plain=text
        }
    }
    return plain
}
