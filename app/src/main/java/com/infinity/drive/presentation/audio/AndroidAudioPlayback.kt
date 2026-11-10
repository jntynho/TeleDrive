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
    private val playerDelegate=lazy {
        ExoPlayer.Builder(appContext).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
            setHandleAudioBecomingNoisy(true)
        }.also { attachPlayer(it) }
    }
    internal val media3Player:ExoPlayer get()=playerDelegate.value
    private val player get()=media3Player
    private var preloadJob:Job?=null
    private var preparedNext:DriveFile?=null
    private var resolveJob:Job?=null
    private var metadataJob:Job?=null
    private var queueJob:Job?=null
    private var sleepJob:Job?=null
    private var queue=emptyList<String>()
    private var desiredIndex=-1
    private var requestedPlay=false
    private var playbackEpoch=0L
    private var contextJob:Job?=null
    private var restorationChecked=false
    private var libraryContext:Pair<Long?,Boolean>?=null
    private val metadataSlots=Semaphore(2)
    private val metadataCache=object:LinkedHashMap<String,AudioMetadata>(128,.75f,true){
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,AudioMetadata>?)=size>128
    }
    private fun attachPlayer(player:ExoPlayer) {
        player.addListener(object:Player.Listener {
            override fun onMediaItemTransition(mediaItem:MediaItem?,reason:Int){
                val next=preparedNext?.takeIf{it.id==mediaItem?.mediaId}?:return
                preparedNext=null
                val epoch=++playbackEpoch
                metadataJob?.cancel()
                desiredIndex=queue.indexOf(next.id)
                val old=mutable.value
                mutable.value=AudioPlaybackState(file=next,playing=player.isPlaying,playRequested=requestedPlay,loading=player.playbackState==Player.STATE_BUFFERING,
                    durationMs=next.durationMs?:0,repeatMode=old.repeatMode,queueRevision=old.queueRevision)
                refreshQueueState()
                metadataJob=scope.launch{val info=metadata(next);if(epoch==playbackEpoch){mutable.update{it.copy(metadata=info)};publishMetadata()}}
                scope.launch{if(epoch==playbackEpoch&&player.currentMediaItemIndex>0)player.removeMediaItems(0,player.currentMediaItemIndex);prepareNext()}
            }
            override fun onIsPlayingChanged(isPlaying:Boolean){if(player.currentMediaItem?.mediaId==mutable.value.file?.id)mutable.update{it.copy(playing=isPlaying)}}
            override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int){
                if(player.currentMediaItem?.mediaId!=mutable.value.file?.id)return
                requestedPlay=playWhenReady
                mutable.update{it.copy(playRequested=playWhenReady)}
            }
            override fun onPositionDiscontinuity(oldPosition:Player.PositionInfo,newPosition:Player.PositionInfo,reason:Int){mutable.update{it.copy(positionMs=newPosition.positionMs.coerceAtLeast(0))}}
            override fun onPlaybackStateChanged(playbackState:Int){
                if(player.currentMediaItem?.mediaId!=mutable.value.file?.id)return
                mutable.update{it.copy(loading=playbackState==Player.STATE_BUFFERING,durationMs=knownDuration())}
                if(playbackState==Player.STATE_ENDED&&requestedPlay){
                    if(mutable.value.repeatMode==AudioRepeatMode.ONE){player.seekTo(0);player.play()}
                    else if(mutable.value.canSkipNext)skip(1,autoplay=true)
                    else { requestedPlay=false;mutable.update{it.copy(playing=false,playRequested=false,positionMs=knownDuration())} }
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
                        publishMetadata()
                    }
                }
            }
            override fun onTracksChanged(tracks:Tracks){
                mutable.update{it.copy(metadata=it.metadata.copy(resolved=true))}
                for(group in tracks.groups)for(index in 0 until group.length)if(group.isTrackSelected(index)){
                    group.getTrackFormat(index).metadata?.let(::acceptLyricsMetadata)
                }
            }
            override fun onMetadata(metadata:Metadata){acceptLyricsMetadata(metadata)}
            override fun onPlayerError(error:PlaybackException){requestedPlay=false;mutable.update{it.copy(playing=false,loading=false,playRequested=false,error="Couldn't play this audio. Retry, or download an offline copy.")}}
        })
    }
    init {
        observeCurrentFile()
        scope.launch { state.map{it.copy(positionMs=it.positionMs/5000*5000)}.distinctUntilChanged().filter{restorationChecked&&libraryContext?.second==true}.collectLatest{saveSession(it)} }
        scope.launch {
            state.map{it.file!=null&&(it.playing||it.loading)}.distinctUntilChanged().collectLatest { active ->
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
    override fun updateLibraryContext(chatId:Long?,signedIn:Boolean){
        val next=chatId to signedIn
        if(libraryContext==next)return
        contextJob?.cancel()
        contextJob=scope.launch {
            val previous=mutable.value
            val previousQueue=queue.toList()
            val previousChat=libraryContext?.first
            val changed=libraryContext!=null&&libraryContext!=next
            restorationChecked=false
            libraryContext=next
            if(changed){stop();synchronized(metadataCache){metadataCache.clear()}}
            if(changed&&previous.file!=null)saveSession(previous,previousChat,previousQueue)
            if(signedIn)restoreSession(chatId)
            if(libraryContext==next)restorationChecked=true
        }
    }
    override fun play(file:DriveFile){queueJob?.cancel();start(file,true)}
    private fun start(file:DriveFile,autoplay:Boolean,initialPosition:Long=0){
        val epoch=++playbackEpoch
        resolveJob?.cancel();metadataJob?.cancel();preloadJob?.cancel();preparedNext=null
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
            .onFailure{mutable.update{it.copy(queueError="Background controls are unavailable. Keep the app open to continue playback.")}}
        metadataJob=scope.launch {
            val info=metadata(file)
            if(playbackEpoch==epoch&&mutable.value.file?.id==file.id){mutable.update{it.copy(metadata=mergeMetadata(it.metadata,info),durationMs=knownDuration())};publishMetadata()}
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
                        val factory=if(content.parts.isNotEmpty())sources.createParted(content.parts,content.encrypted,content.nominalBytesPerSecond)else sources.create(content.remoteFileId)
                        player.setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(item.setUri("telegram://audio/${file.id}").build()))
                    }
                    else->{mutable.update{it.copy(loading=false,error="Audio is unavailable. Retry, or download an offline copy.")};return@launch}
                }
                val intent=requestedPlay
                player.seekTo(initialPosition.coerceAtLeast(0));player.prepare();player.playWhenReady=intent;prepareNext()
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){mutable.update{it.copy(loading=false,playing=false,error="Couldn't load this audio. Tap play to retry.")}}
        }
    }
    private fun prepareNext(){
        preloadJob?.cancel()
        if(!playerDelegate.isInitialized()||player.currentMediaItem?.mediaId!=mutable.value.file?.id)return
        val current=mutable.value.file?:return
        val index=queue.indexOf(current.id)
        val nextIndex=audioQueueStep(index,1,queue.size,mutable.value.repeatMode==AudioRepeatMode.ALL)
        val nextId=nextIndex?.let{queue[it]}
        if(preparedNext?.id!=nextId){
            preparedNext=null
            if(player.mediaItemCount>player.currentMediaItemIndex+1)player.removeMediaItems(player.currentMediaItemIndex+1,player.mediaItemCount)
        }
        if(nextIndex==null)return
        val id=queue[nextIndex];val epoch=playbackEpoch;val revision=mutable.value.queueRevision
        if(preparedNext?.id==id)return
        preloadJob=scope.launch {
            try {
                val next=withContext(ioDispatcher){files.getFile(id)}?:return@launch
                if(next.isTrashed)return@launch
                val content=resolver.resolve(next).first{it !is PreviewContent.Loading && it !is PreviewContent.DownloadProgress}
                ensureActive()
                if(epoch!=playbackEpoch||revision!=mutable.value.queueRevision)return@launch
                val item=MediaItem.Builder().setMediaId(next.id).setMediaMetadata(MediaMetadata.Builder().setTitle(next.name.substringBeforeLast('.')).build())
                val source=when(content){
                    is PreviewContent.LocalMedia->ProgressiveMediaSource.Factory(androidx.media3.datasource.DefaultDataSource.Factory(appContext))
                        .createMediaSource(item.setUri(Uri.fromFile(File(content.path))).build())
                    is PreviewContent.StreamedMedia->{val factory=if(content.parts.isNotEmpty())sources.createParted(content.parts,content.encrypted,content.nominalBytesPerSecond)else sources.create(content.remoteFileId)
                        ProgressiveMediaSource.Factory(factory).createMediaSource(item.setUri("telegram://audio/${next.id}").build())}
                    else->return@launch
                }
                if(player.mediaItemCount>player.currentMediaItemIndex+1)player.removeMediaItems(player.currentMediaItemIndex+1,player.mediaItemCount)
                preparedNext=next
                player.addMediaSource(source)
            }catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){ /* Optional preloading must never stop the current track. */ }
        }
    }
    private fun publishMetadata(){
        if(!playerDelegate.isInitialized()||player.mediaItemCount==0)return
        val state=mutable.value
        val item=player.currentMediaItem?:return
        if(item.mediaId!=state.file?.id)return
        val meta=state.metadata
        val value=MediaMetadata.Builder().setTitle(meta.title?:state.file?.name?.substringBeforeLast('.'))
            .setArtist(meta.artist).setArtworkUri(meta.artworkPath?.let{Uri.fromFile(File(it))}).build()
        if(item.mediaMetadata!=value)player.replaceMediaItem(player.currentMediaItemIndex,item.buildUpon().setMediaMetadata(value).build())
    }
    private fun mergeMetadata(current:AudioMetadata,next:AudioMetadata)=AudioMetadata(
        next.title?.takeIf(String::isNotBlank)?:current.title,next.artist?.takeIf(String::isNotBlank)?:current.artist,
        next.durationMs?:current.durationMs,next.artworkColors.ifEmpty{current.artworkColors},next.lyrics?:current.lyrics,next.artworkPath?:current.artworkPath,current.resolved||next.resolved,next.failed)
    override fun containsQueuedFile(id:String)=id in queue
    override fun setQueue(ids:List<String>){
        val next=ids.distinct()
        if(next==queue)return
        queue=next;desiredIndex=queue.indexOf(mutable.value.file?.id)
        mutable.update{it.copy(queueRevision=it.queueRevision+1)};refreshQueueState();prepareNext()
    }
    private fun refreshQueueState(){
        val i=queue.indexOf(mutable.value.file?.id)
        val wrap=mutable.value.repeatMode==AudioRepeatMode.ALL&&queue.size>1
        mutable.update{it.copy(canSkipPrevious=i>0||wrap,canSkipNext=i>=0&&(i<queue.lastIndex||wrap))}
    }
    override fun toggle(){
        val current=mutable.value
        if(current.error!=null){current.file?.let(::play);return}
        if(!current.loading&&player.mediaItemCount==0){current.file?.let{start(it,true,current.positionMs)};return}
        if(current.loading&&player.mediaItemCount==0){requestedPlay=!requestedPlay;mutable.update{it.copy(playRequested=requestedPlay)};return}
        if(requestedPlay||player.playWhenReady)pause()
        else {requestedPlay=true;if(player.playbackState==Player.STATE_ENDED)player.seekTo(0);player.play()}
    }
    override fun pause(){requestedPlay=false;if(playerDelegate.isInitialized())player.pause();mutable.update{it.copy(playRequested=false)}}
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
                    if(file!=null&&!file.isTrashed){
                        if(playerDelegate.isInitialized()&&player.hasNextMediaItem()&&player.getMediaItemAt(player.currentMediaItemIndex+1).mediaId==file.id){player.seekToNextMediaItem();requestedPlay=autoplay;player.playWhenReady=autoplay}
                        else start(file,autoplay)
                        return@launch
                    }
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
    override fun stop(){playbackEpoch++;preloadJob?.cancel();preparedNext=null;sleepJob?.cancel();sleepJob=null;resolveJob?.cancel();metadataJob?.cancel();queueJob?.cancel();requestedPlay=false;if(playerDelegate.isInitialized()){player.stop();player.clearMediaItems()};queue=emptyList();desiredIndex=-1;mutable.value=AudioPlaybackState()}
    override fun seek(positionMs:Long){val target=positionMs.coerceIn(0,knownDuration());player.seekTo(target);mutable.update{it.copy(positionMs=target)}}
    override fun cycleRepeat(){mutable.update{it.copy(repeatMode=AudioRepeatMode.entries[(it.repeatMode.ordinal+1)%AudioRepeatMode.entries.size])};if(playerDelegate.isInitialized())player.repeatMode=if(mutable.value.repeatMode==AudioRepeatMode.ONE)Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF;refreshQueueState();prepareNext()}
    override fun setSleepTimer(minutes:Int?){
        sleepJob?.cancel();sleepJob=null
        val duration=minutes?.coerceIn(1,180);mutable.update{it.copy(sleepTimerMinutes=duration)}
        if(duration!=null)sleepJob=scope.launch{delay(duration*60_000L);pause();mutable.update{it.copy(sleepTimerMinutes=null)}}
    }
    override suspend fun queuedFiles():List<DriveFile> {
        val ids=queue.toList()
        return withContext(ioDispatcher){ids.chunked(400).flatMap{batch->val found=files.getFiles(batch).associateBy{it.id};batch.mapNotNull(found::get)}.filterNot{it.isTrashed}}
    }
    override suspend fun basicMetadata(file:DriveFile):AudioMetadata = readMetadata(file,false)
    override suspend fun metadata(file:DriveFile):AudioMetadata = readMetadata(file,true)
    private suspend fun readMetadata(file:DriveFile,detailed:Boolean):AudioMetadata {
        val key=metadataKey(file)+(if(detailed)""else":basic")
        synchronized(metadataCache){metadataCache[key]}?.let{return it}
        val cachedDisk=withContext(ioDispatcher){readMetadataCache(key)}
        if(cachedDisk!=null){synchronized(metadataCache){metadataCache[key]=cachedDisk};return cachedDisk}
        val path=withContext(ioDispatcher){file.localPath?.takeIf{File(it).exists()}}
        val remote=file.remoteFileId?.takeIf{path==null&&!file.isEncrypted&&file.hasRemoteCopy}
        if(path==null&&(remote==null||mutable.value.file?.id==file.id))return AudioMetadata(durationMs=file.durationMs)
        suspend fun inspect():AudioMetadata=metadataSlots.withPermit {
            val retriever=MediaMetadataRetriever()
            var source:android.media.MediaDataSource?=null
            try {
                if(path!=null)retriever.setDataSource(path)
                else {
                    val owner=currentCoroutineContext()[Job]
                    source=sources.metadataSource(remote!!){owner?.isActive==false||mutable.value.file?.id==file.id}
                    retriever.setDataSource(source)
                }
                val picture=if(!detailed||path==null)null else retriever.embeddedPicture?.takeIf{it.size<=5*1024*1024}
                AudioMetadata(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank)
                        ?:retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.takeIf(String::isNotBlank),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:file.durationMs,
                    picture?.let(::artworkColors).orEmpty(),if(detailed)path?.let{readEmbeddedLyrics(File(it))}else null,picture?.let{saveArtwork(file,it)})
            } finally {runCatching{retriever.release()};runCatching{source?.close()}}
        }
        return try {
            // Optional remote inspection belongs to the visible row: leaving it cancels queued work.
            val result=withContext(ioDispatcher){inspect().copy(resolved=true)}
            withContext(ioDispatcher){
                try{files.storeAudioTags(file,result.title,result.artist)}catch(cancel:CancellationException){throw cancel}catch(_:Exception){ /* Playback metadata survives an optional index write failure. */ }
                writeMetadataCache(key,result)
            }
            result.also{synchronized(metadataCache){metadataCache[key]=it}}
        }catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){AudioMetadata(durationMs=file.durationMs,resolved=true,failed=true)}
    }

    private val sessionPrefs by lazy { appContext.getSharedPreferences("audio-playback-session",Context.MODE_PRIVATE) }
    private suspend fun restoreSession(chatId:Long?){
        if(mutable.value.file!=null)return
        val saved=withContext(ioDispatcher){
            val raw=sessionPrefs.getString("session:$chatId",null)?:return@withContext null
            runCatching{org.json.JSONObject(raw)}.getOrNull()
        }?:return
        val file=withContext(ioDispatcher){files.getFile(saved.optString("id"))}?:return
        if(mutable.value.file!=null||libraryContext!=chatId to true||file.isTrashed||file.category!=com.infinity.drive.domain.model.FileCategory.AUDIO||file.chatId!=chatId)return
        val values=saved.optJSONArray("queue")
        queue=if(values==null)listOf(file.id)else (0 until minOf(values.length(),2000)).map{values.getString(it)}.distinct()
        if(file.id !in queue)queue=listOf(file.id)
        desiredIndex=queue.indexOf(file.id)
        mutable.value=AudioPlaybackState(file=file,positionMs=saved.optLong("position").coerceAtLeast(0),durationMs=file.durationMs?:0)
        refreshQueueState()
        val epoch=playbackEpoch
        metadataJob=scope.launch{val info=metadata(file);if(epoch==playbackEpoch&&mutable.value.file?.id==file.id)mutable.update{it.copy(metadata=info,durationMs=info.durationMs?:it.durationMs)}}
    }
    private suspend fun saveSession(snapshot:AudioPlaybackState,chatId:Long?=libraryContext?.first,ids:List<String> = queue.toList()){
        val chat=snapshot.file?.chatId?:chatId
        val track=snapshot.file
        withContext(ioDispatcher){
            if(track==null)sessionPrefs.edit().remove("session:$chat").apply()
            else {
                val index=ids.indexOf(track.id).coerceAtLeast(0)
                val nearby=ids.subList((index-500).coerceAtLeast(0),(index+1500).coerceAtMost(ids.size))
                val value=org.json.JSONObject().put("id",track.id).put("position",snapshot.positionMs)
                    .put("queue",org.json.JSONArray(nearby))
                sessionPrefs.edit().putString("session:$chat",value.toString()).apply()
            }
        }
    }
    private fun metadataDiskKey(key:String)=java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun readMetadataCache(key:String):AudioMetadata?=runCatching {
        val file=File(appContext.cacheDir,"audio-tags/${metadataDiskKey(key)}.json")
        if(!file.isFile||file.length()>100_000)return@runCatching null
        val j=org.json.JSONObject(file.readText())
        fun text(name:String)=j.optString(name).takeIf{it.isNotBlank()&&it!="null"}
        AudioMetadata(text("title"),text("artist"),j.optLong("duration").takeIf{it>0},
            (0 until j.getJSONArray("colors").length()).map{j.getJSONArray("colors").getLong(it)},text("lyrics"),text("artwork")?.takeIf{File(it).isFile},true)
    }.getOrNull()
    private fun writeMetadataCache(key:String,metadata:AudioMetadata){runCatching {
        val dir=File(appContext.cacheDir,"audio-tags").apply{mkdirs()}
        val j=org.json.JSONObject().put("title",metadata.title).put("artist",metadata.artist).put("duration",metadata.durationMs?:0)
            .put("colors",org.json.JSONArray(metadata.artworkColors)).put("lyrics",metadata.lyrics).put("artwork",metadata.artworkPath)
        val target=File(dir,"${metadataDiskKey(key)}.json")
        val temp=File.createTempFile("tags-",".tmp",dir)
        try{temp.writeText(j.toString());temp.renameTo(target)}finally{temp.delete()}
        dir.listFiles()?.sortedByDescending{it.lastModified()}?.drop(256)?.forEach{it.delete()}
    }}
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
    fun release(){stop();scope.cancel();if(playerDelegate.isInitialized())player.release()}
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
            var plain:String?=null
            while(!last&&input.filePointer+4<=input.length()){
                val type=input.readUnsignedByte();last=type and 128!=0
                val count=(input.readUnsignedByte() shl 16) or (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
                if(count>1024*1024||count>input.length()-input.filePointer)return@use null
                if(type and 127==4){
                    val bytes=ByteArray(count);input.readFully(bytes)
                    val buffer=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    fun text():String {val length=buffer.int;require(length>=0&&length<=buffer.remaining());val value=ByteArray(length);buffer.get(value);return String(value,Charsets.UTF_8)}
                    text();val comments=buffer.int.coerceIn(0,10000)
                    repeat(comments){
                        val comment=text()
                        if(comment.substringBefore('=').uppercase() in listOf("LYRICS","UNSYNCEDLYRICS","SYNCEDLYRICS")){
                            val value=comment.substringAfter('=').take(32000)
                            if(AudioLyricsParser.parse(value).cues.isNotEmpty())return@use value
                            if(plain==null)plain=value
                        }
                    }
                }else input.seek(input.filePointer+count)
            };return@use plain
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
