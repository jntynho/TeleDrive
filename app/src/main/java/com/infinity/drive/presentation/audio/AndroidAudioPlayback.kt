package com.infinity.drive.presentation.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.infinity.drive.core.media.TelegramDataSourceFactory
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.presentation.platform.*
import com.infinity.drive.presentation.preview.PreviewContent
import com.infinity.drive.presentation.preview.PreviewContentResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

/** One application-owned player. Navigation observes playback; it never owns its lifetime. */
@androidx.annotation.OptIn(UnstableApi::class)
class AndroidAudioPlayback(context:Context, private val resolver:PreviewContentResolver,
    private val sources:TelegramDataSourceFactory, private val files:FileRepository,
    private val ioDispatcher:CoroutineDispatcher=Dispatchers.IO,ownerScope:CoroutineScope?=null):AudioPlayback {
    private val appContext=context.applicationContext
    private val scope=ownerScope?:CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val mutable=MutableStateFlow(AudioPlaybackState())
    override val state:StateFlow<AudioPlaybackState> = mutable.asStateFlow()
    internal val media3Player=ExoPlayer.Builder(appContext).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
        setHandleAudioBecomingNoisy(true)
    }
    private val player get()=media3Player
    private var resolveJob:Job?=null
    private var metadataJob:Job?=null
    private var queueJob:Job?=null
    private var sleepJob:Job?=null
    private var queue=emptyList<String>()
    private var desiredIndex=-1
    private var requestedPlay=false
    private var playbackEpoch=0L
    private var libraryContext:Pair<Long?,Boolean>?=null
    private val metadataSlots=Semaphore(2)
    private val metadataInFlight=mutableMapOf<String,Deferred<AudioMetadata>>()
    private val metadataCache=object:LinkedHashMap<String,AudioMetadata>(128,.75f,true){
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,AudioMetadata>?)=size>128
    }
    init {
        observeCurrentFile()
        player.addListener(object:Player.Listener {
            override fun onIsPlayingChanged(isPlaying:Boolean){if(player.currentMediaItem?.mediaId==mutable.value.file?.id)mutable.update{it.copy(playing=isPlaying)}}
            override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int){
                if(player.currentMediaItem?.mediaId!=mutable.value.file?.id)return
                requestedPlay=playWhenReady
                mutable.update{it.copy(playRequested=playWhenReady)}
            }
            override fun onPlaybackStateChanged(playbackState:Int){
                if(player.currentMediaItem?.mediaId!=mutable.value.file?.id)return
                mutable.update{it.copy(loading=playbackState==Player.STATE_BUFFERING,durationMs=knownDuration())}
                if(playbackState==Player.STATE_ENDED&&requestedPlay){
                    if(mutable.value.repeatMode==AudioRepeatMode.ONE){player.seekTo(0);player.play()}
                    else skip(1,autoplay=true)
                }
            }
            override fun onMediaMetadataChanged(metadata:MediaMetadata){
                val track=mutable.value.file?:return
                if(player.currentMediaItem?.mediaId!=track.id)return
                val epoch=playbackEpoch
                scope.launch {
                    val artwork=metadata.artworkData?.takeIf{it.size<=5*1024*1024}?.let { bytes ->
                        withContext(ioDispatcher){artworkColors(bytes) to saveArtwork(track,bytes)}
                    }
                    if(playbackEpoch==epoch&&mutable.value.file?.id==track.id){
                        mutable.update{it.copy(metadata=it.metadata.copy(
                            title=metadata.title?.toString()?.takeIf(String::isNotBlank)?:it.metadata.title,
                            artist=metadata.artist?.toString()?.takeIf(String::isNotBlank)?:it.metadata.artist,
                            artworkColors=artwork?.first?.takeIf{it.isNotEmpty()}?:it.metadata.artworkColors,
                            artworkPath=artwork?.second?:it.metadata.artworkPath))}
                        synchronized(metadataCache){metadataCache[metadataKey(track)]=mutable.value.metadata}
                    }
                }
            }
            override fun onTracksChanged(tracks:Tracks){
                for(group in tracks.groups)for(index in 0 until group.length)if(group.isTrackSelected(index)){
                    group.getTrackFormat(index).metadata?.let(::acceptLyricsMetadata)
                }
            }
            override fun onMetadata(metadata:Metadata){acceptLyricsMetadata(metadata)}
            override fun onPlayerError(error:PlaybackException){requestedPlay=false;mutable.update{it.copy(playing=false,loading=false,playRequested=false,error="Couldn't play this audio. Retry, or download an offline copy.")}}
        })
        scope.launch {
            state.map{it.file!=null}.distinctUntilChanged().collectLatest { active ->
                if(active)while(isActive){delay(200);mutable.update{it.copy(positionMs=player.currentPosition.coerceAtLeast(0),durationMs=knownDuration())}}
            }
        }
    }
    private fun observeCurrentFile() {
        scope.launch {
            state.map{it.file?.id}.distinctUntilChanged().collectLatest { id ->
                if(id!=null)files.observeFile(id).collect { latest ->
                    if(latest==null||latest.isTrashed)stop()
                    else mutable.update{if(it.file?.id==id)it.copy(file=latest)else it}
                }
            }
        }
    }
    private fun knownDuration()=player.duration.takeIf{it>0}?:mutable.value.metadata.durationMs?:mutable.value.file?.durationMs?:0L
    private fun metadataKey(file:DriveFile)="${file.id}:${file.modifiedAt}:${file.localPath}"
    private fun acceptLyricsMetadata(metadata:Metadata){
        val track=mutable.value.file?:return
        if(player.currentMediaItem?.mediaId!=track.id)return
        val epoch=playbackEpoch
        scope.launch {
            val lyrics=withContext(Dispatchers.Default){streamedLyrics(metadata)}?:return@launch
            if(playbackEpoch==epoch&&mutable.value.file?.id==track.id){
                mutable.update{it.copy(metadata=it.metadata.copy(lyrics=lyrics))}
                synchronized(metadataCache){metadataCache[metadataKey(track)]=mutable.value.metadata}
            }
        }
    }
    override fun updateLibraryContext(chatId:Long?,signedIn:Boolean){scope.launch {
        val next=chatId to signedIn
        if(libraryContext!=null&&libraryContext!=next){stop();synchronized(metadataCache){metadataCache.clear()}}
        libraryContext=next
    }}
    override fun play(file:DriveFile){queueJob?.cancel();start(file,true)}
    private fun start(file:DriveFile,autoplay:Boolean){
        val epoch=++playbackEpoch
        resolveJob?.cancel();metadataJob?.cancel()
        if(file.id !in queue)queue=listOf(file.id)
        desiredIndex=queue.indexOf(file.id)
        // Listener callbacks from stop belong to the old item; install the new intent afterwards.
        player.stop();player.clearMediaItems();requestedPlay=autoplay
        val previous=mutable.value
        val cached=synchronized(metadataCache){metadataCache[metadataKey(file)]}
        mutable.value=AudioPlaybackState(file=file,loading=true,playRequested=autoplay,metadata=cached?:AudioMetadata(durationMs=file.durationMs),durationMs=cached?.durationMs?:file.durationMs?:0,
            repeatMode=previous.repeatMode,sleepTimerMinutes=previous.sleepTimerMinutes,queueRevision=previous.queueRevision)
        refreshQueueState()
        runCatching { appContext.startService(android.content.Intent(appContext,TeleDriveAudioService::class.java)) }
        metadataJob=scope.launch {
            val info=metadata(file)
            if(playbackEpoch==epoch&&mutable.value.file?.id==file.id)mutable.update{it.copy(metadata=mergeMetadata(it.metadata,info),durationMs=knownDuration())}
        }
        resolveJob=scope.launch {
            try {
                // Decoder preparation is independent of optional artwork/lyrics extraction.
                val content=resolver.resolve(file).first { it is PreviewContent.LocalMedia || it is PreviewContent.StreamedMedia || it is PreviewContent.Failed || it is PreviewContent.Unsupported || it is PreviewContent.RequiresDownload }
                ensureActive()
                val item=MediaItem.Builder().setMediaId(file.id).setMediaMetadata(MediaMetadata.Builder().setDisplayTitle(file.name.substringBeforeLast('.')).build())
                when(content){
                    is PreviewContent.LocalMedia->player.setMediaItem(item.setUri(Uri.fromFile(File(content.path))).build())
                    is PreviewContent.StreamedMedia->{
                        val factory=if(content.parts.isNotEmpty())sources.createParted(content.parts,content.encrypted)else sources.create(content.remoteFileId)
                        player.setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(item.setUri("telegram://audio/${file.id}").build()))
                    }
                    else->{mutable.update{it.copy(loading=false,error="Audio is unavailable. Retry, or download an offline copy.")};return@launch}
                }
                val intent=requestedPlay
                player.prepare();player.playWhenReady=intent
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){mutable.update{it.copy(loading=false,playing=false,error="Couldn't load this audio. Tap play to retry.")}}
        }
    }
    private fun mergeMetadata(current:AudioMetadata,next:AudioMetadata)=AudioMetadata(
        next.title?.takeIf(String::isNotBlank)?:current.title,next.artist?.takeIf(String::isNotBlank)?:current.artist,
        next.durationMs?:current.durationMs,next.artworkColors.ifEmpty{current.artworkColors},next.lyrics?:current.lyrics,next.artworkPath?:current.artworkPath)
    override fun containsQueuedFile(id:String)=id in queue
    override fun setQueue(ids:List<String>){
        val next=ids.distinct()
        if(next==queue)return
        queue=next;desiredIndex=queue.indexOf(mutable.value.file?.id)
        mutable.update{it.copy(queueRevision=it.queueRevision+1)};refreshQueueState()
    }
    private fun refreshQueueState(){
        val i=queue.indexOf(mutable.value.file?.id)
        val wrap=mutable.value.repeatMode==AudioRepeatMode.ALL&&queue.size>1
        mutable.update{it.copy(canSkipPrevious=i>0||wrap,canSkipNext=i>=0&&(i<queue.lastIndex||wrap))}
    }
    override fun toggle(){
        val current=mutable.value
        if(current.error!=null){current.file?.let(::play);return}
        if(current.loading&&player.mediaItemCount==0){requestedPlay=!requestedPlay;mutable.update{it.copy(playRequested=requestedPlay)};return}
        if(requestedPlay||player.playWhenReady)pause()
        else {requestedPlay=true;if(player.playbackState==Player.STATE_ENDED)player.seekTo(0);player.play()}
    }
    override fun pause(){requestedPlay=false;player.pause();mutable.update{it.copy(playRequested=false)}}
    override fun previous(){if(player.currentPosition>3000){seek(0);return};skip(-1)}
    override fun next()=skip(1)
    private fun skip(direction:Int,autoplay:Boolean=requestedPlay){
        if(queue.isEmpty()||desiredIndex<0)return
        val next=audioQueueStep(desiredIndex,direction,queue.size,mutable.value.repeatMode==AudioRepeatMode.ALL)?:return
        // Reserve the destination synchronously: a second tap advances from this target.
        desiredIndex=next
        queueJob?.cancel()
        mutable.update{it.copy(queueError=null)}
        queueJob=scope.launch {
            try {
                val visited=mutableSetOf<String>()
                while(desiredIndex in queue.indices){
                    val id=queue[desiredIndex]
                    if(!visited.add(id))break
                    val file=withContext(ioDispatcher){files.getFile(id)}
                    ensureActive()
                    if(file!=null&&!file.isTrashed){start(file,autoplay);return@launch}
                    val oldIndex=desiredIndex
                    queue=queue.filterNot{it==id}
                    mutable.update{it.copy(queueRevision=it.queueRevision+1)}
                    desiredIndex=if(direction>0)oldIndex else oldIndex-1
                    if(desiredIndex !in queue.indices&&mutable.value.repeatMode==AudioRepeatMode.ALL&&queue.isNotEmpty())desiredIndex=if(direction>0)0 else queue.lastIndex
                }
                refreshQueueState()
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){mutable.update{it.copy(queueError="Couldn't load the next track. Try Next again.")}}
        }
    }
    override fun stop(){playbackEpoch++;sleepJob?.cancel();sleepJob=null;resolveJob?.cancel();metadataJob?.cancel();queueJob?.cancel();requestedPlay=false;player.stop();player.clearMediaItems();queue=emptyList();desiredIndex=-1;mutable.value=AudioPlaybackState()}
    override fun seek(positionMs:Long){val target=positionMs.coerceIn(0,knownDuration());player.seekTo(target);mutable.update{it.copy(positionMs=target)}}
    override fun cycleRepeat(){mutable.update{it.copy(repeatMode=AudioRepeatMode.entries[(it.repeatMode.ordinal+1)%AudioRepeatMode.entries.size])};refreshQueueState()}
    override fun setSleepTimer(minutes:Int?){
        sleepJob?.cancel();sleepJob=null
        val duration=minutes?.coerceIn(1,180);mutable.update{it.copy(sleepTimerMinutes=duration)}
        if(duration!=null)sleepJob=scope.launch{delay(duration*60_000L);pause();mutable.update{it.copy(sleepTimerMinutes=null)}}
    }
    override suspend fun queuedFiles():List<DriveFile> {
        val ids=queue.toList()
        return withContext(ioDispatcher){ids.chunked(400).flatMap{batch->val found=files.getFiles(batch).associateBy{it.id};batch.mapNotNull(found::get)}.filterNot{it.isTrashed}}
    }
    override suspend fun metadata(file:DriveFile):AudioMetadata {
        val key=metadataKey(file)
        synchronized(metadataCache){metadataCache[key]}?.let{return it}
        val path=file.localPath?:return AudioMetadata(durationMs=file.durationMs)
        // Coalesce requests from list rows and Now Playing; callers do not own the task.
        val task=withContext(Dispatchers.Main.immediate){metadataInFlight.getOrPut(key){scope.async(ioDispatcher){
            metadataSlots.withPermit {
                val retriever=MediaMetadataRetriever()
                try {
                    retriever.setDataSource(path)
                    val picture=retriever.embeddedPicture?.takeIf{it.size<=5*1024*1024}
                    AudioMetadata(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank),
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank),
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:file.durationMs,
                        picture?.let(::artworkColors).orEmpty(),readEmbeddedLyrics(File(path)),picture?.let{saveArtwork(file,it)})
                } finally {runCatching{retriever.release()}}
            }
        }.also{job->job.invokeOnCompletion{scope.launch{if(metadataInFlight[key]===job)metadataInFlight.remove(key)}}}}}
        return try { task.await().also{synchronized(metadataCache){metadataCache[key]=it}} }
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){AudioMetadata(durationMs=file.durationMs)}
    }
    private fun saveArtwork(file:DriveFile,bytes:ByteArray):String?=runCatching {
        val dir=File(appContext.cacheDir,"audio-artwork").apply{mkdirs()}
        val key=java.security.MessageDigest.getInstance("SHA-256").digest(metadataKey(file).toByteArray()).joinToString(""){"%02x".format(it)}
        val target=File(dir,"$key.img")
        if(!target.exists()){
            val tmp=File.createTempFile("artwork-",".tmp",dir)
            try{tmp.writeBytes(bytes);if(!tmp.renameTo(target))tmp.delete()}finally{tmp.delete()}
        }
        target.setLastModified(System.currentTimeMillis())
        // Bound disk artwork separately from the existing thumbnail cache.
        var total=0L
        dir.listFiles()?.sortedByDescending{it.lastModified()}?.forEach{entry->total+=entry.length();if(total>24L*1024*1024&&entry!=target)entry.delete()}
        target.absolutePath
    }.getOrNull()
    fun release(){stop();scope.cancel();player.release()}
}

internal fun audioQueueStep(index:Int,direction:Int,size:Int,repeatAll:Boolean):Int? {
    if(size<=0||index !in 0 until size)return null
    val next=index+direction
    return if(next in 0 until size)next else if(repeatAll&&size>1)(next+size)%size else null
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
