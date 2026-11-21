package com.infinity.drive.presentation.audio

import app.tgdrive.ui.*
import coil3.request.crossfade
import androidx.compose.animation.*
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import app.tgdrive.ui.elasticVerticalScroll as verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.platform.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

@Composable
fun LibraryAudioMiniPlayer(){
    val player=LocalAudioPlayback.current
    val state by remember(player){player.state.map{it.copy(positionMs=0)}.distinctUntilChanged()}.collectAsStateWithLifecycle(remember(player){player.state.value.copy(positionMs=0)})
    val openPlayer=LocalOpenAudioPlayer.current
    var last by remember{mutableStateOf(AudioPlaybackState())}
    var expanded by rememberSaveable{mutableStateOf(false)}
    SideEffect { if(state.file!=null)last=state }
    LaunchedEffect(state.file?.id){if(state.file==null)expanded=false}
    AnimatedVisibility(state.file!=null,enter=fadeIn(tween(220)),exit=fadeOut(tween(160))){
        val current=if(state.file!=null)state else last
        val file=current.file
        if(file!=null){
            val first by animateColorAsState(artworkTone(current.metadata,0),tween(800),label="miniFirst")
            val second by animateColorAsState(artworkTone(current.metadata,1),tween(800),label="miniSecond")
            val pause=(current.playing||current.playRequested)&&current.error==null
            Surface(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),shape=RoundedCornerShape(18.dp),color=Color.Transparent,contentColor=Color.White){
                BoxWithConstraints {
                val compact=maxWidth<360.dp||androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
                Column(Modifier.background(Brush.linearGradient(listOf(first,second)))
                    .border(1.dp,Color.White.copy(alpha=.08f),RoundedCornerShape(18.dp)).testTag("audio-mini-player")){
                    Row(Modifier.fillMaxWidth().heightIn(min=66.dp).clickable(onClickLabel="Open now playing"){if(openPlayer!=null)openPlayer(file.id)else expanded=true}
                        .padding(start=10.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)){
                        TrackArtwork(file,current.metadata,Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                            Text(current.metadata.title?.takeIf(String::isNotBlank)?:file.name.substringBeforeLast('.'),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(current.error?:current.metadata.artist?.takeIf(String::isNotBlank)?:file.extension.uppercase().ifBlank{"Audio"},style=MaterialTheme.typography.bodySmall,
                                color=Color.White.copy(alpha=.7f),maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        IconButton(player::toggle){Icon(if(pause)MediaIcons.Pause else MediaIcons.Play,
                            if(current.error!=null)"Retry audio"else if(pause)"Pause audio"else"Play audio",Modifier.size(24.dp))}
                        if(!compact)IconButton(player::next,enabled=current.canSkipNext){Icon(MediaIcons.Next,"Next audio",Modifier.size(22.dp))}
                        IconButton(player::stop){Icon(DriveIcons.Close,"Dismiss audio player",Modifier.size(21.dp))}
                    }
                    MiniPlayerProgress(file.id)
                    Spacer(Modifier.height(6.dp))
                }
                }
            }
        }
    }
    if(expanded&&state.file!=null&&openPlayer==null){
        androidx.compose.ui.window.Dialog(onDismissRequest={expanded=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
            Surface(Modifier.fillMaxSize(),color=Color.Black){
                FullscreenAudioPlayer(state.file!!, true, Modifier.fillMaxSize(), headerActions = {
                    IconButton({ expanded = false }) {
                        Icon(DriveIcons.ArrowBack, "Back to library", tint = Color.White)
                    }
                })
            }
        }
    }
}

@Composable
private fun MiniPlayerProgress(fileId:String){
    val state by LocalAudioPlayback.current.state.collectAsStateWithLifecycle()
    LinearProgressIndicator(progress={if(state.file?.id==fileId&&state.durationMs>0)(state.positionMs.toFloat()/state.durationMs).coerceIn(0f,1f)else 0f},
        modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp).height(2.dp),color=Color.White.copy(alpha=.85f),trackColor=Color.White.copy(alpha=.2f),gapSize=0.dp,drawStopIndicator={})
}

@Composable
fun FullscreenAudioPlayer(
    file: DriveFile,
    isActivePage: Boolean,
    modifier: Modifier = Modifier,
    onTrackChanged: (String) -> Unit = {},
    onSwipeDismiss: (() -> Unit)? = null,
    headerActions: @Composable RowScope.() -> Unit = {}
) {
    val audio = LocalAudioPlayback.current
    val state by remember(audio) { audio.state.map { it.copy(positionMs = 0) }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(remember(audio) { audio.state.value.copy(positionMs = 0) })
    val displayedFile = if (isActivePage) state.file ?: file else file
    val metadata = state.metadata.takeIf { state.file?.id == displayedFile.id } ?: AudioMetadata()
    val scope = rememberCoroutineScope()
    val dismiss = rememberUpdatedState(onSwipeDismiss)

    BoxWithConstraints(modifier.fillMaxSize().ambientArtwork(if (isActivePage) state else state.copy(playing = false))) {
        val swipe = if (isActivePage && onSwipeDismiss != null) {
            val distance = with(LocalDensity.current) { 80.dp.toPx() }
            val height = with(LocalDensity.current) { maxHeight.toPx() }
            remember(scope, distance, height) { AudioDismissDrag(scope, distance, height) { dismiss.value?.invoke() } }
        } else null

        Column(Modifier.fillMaxSize().graphicsLayer {
            translationY = swipe?.offset ?: 0f
            alpha = 1f - (swipe?.progress ?: 0f)
        }.systemBarsPadding()) {
            // The metadata is always anchored to the physical left, even in a right-to-left app.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 64.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AudioTrackLabels(displayedFile, metadata, Modifier.weight(1f).audioDismissGesture(swipe))
                    headerActions()
                }
            }
            NowPlayingContent(displayedFile, Modifier.weight(1f), swipe)
        }
    }
}

@Composable
private fun AudioTrackLabels(file: DriveFile, metadata: AudioMetadata, modifier: Modifier) {
    val labels = Triple(
        file.id,
        metadata.title?.takeIf(String::isNotBlank) ?: file.name.substringBeforeLast('.'),
        metadata.artist?.takeIf(String::isNotBlank) ?: file.extension.uppercase().ifBlank { "Audio" }
    )
    Crossfade(labels, modifier.testTag("audio-track-labels"), animationSpec = tween(240), label = "audioTrackLabels") { (_, name, artist) ->
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, Modifier.fillMaxWidth(), color = Color.White,
                style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Content),
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Left, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, Modifier.fillMaxWidth(), color = Color.White.copy(alpha = .62f),
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                textAlign = TextAlign.Left, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private class AudioDismissDrag(
    private val scope: CoroutineScope,
    private val threshold: Float,
    private val height: Float,
    private val onDismiss: () -> Unit
) {
    var offset by mutableFloatStateOf(0f)
        private set

    val progress: Float get() = (offset / height.coerceAtLeast(1f)).coerceIn(0f, 1f)
    var closing = false
        private set
    private var animation: Job? = null
    private var dragging = false

    fun start(): Boolean {
        if (dragging || closing) return false
        dragging = true
        animation?.cancel()
        return true
    }

    fun move(delta: Float) {
        offset = (offset + delta).coerceIn(0f, height)
    }

    fun finish(completed: Boolean) {
        dragging = false
        val dismissed = completed && offset >= threshold
        closing = dismissed
        animation = scope.launch {
            animate(offset, if (dismissed) height else 0f, animationSpec = tween(if (dismissed) 210 else 180)) { value, _ ->
                offset = value
            }
            if (dismissed) onDismiss()
        }
    }
}

/** A pull can start on the header, artwork or lyric window and leaves the playback controls to handle touch. */
private fun Modifier.audioDismissGesture(drag: AudioDismissDrag?, scroll: ScrollState? = null): Modifier {
    if (drag == null) return this
    return pointerInput(drag, scroll) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (drag.closing || (scroll != null && scroll.value > 0)) return@awaitEachGesture
            var rejected = false
            val start = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                if (overSlop.y <= 0f || abs(overSlop.x) >= overSlop.y) rejected = true
                if (!rejected) {
                    rejected = !drag.start()
                    if (!rejected) {
                        drag.move(overSlop.y)
                        change.consume()
                    }
                }
            } ?: return@awaitEachGesture
            val completed = verticalDrag(start.id) { change ->
                drag.move(change.positionChange().y)
                change.consume()
            }
            drag.finish(completed)
        }
    }
}

@Composable
internal fun AudioSeekBar(snapshot:AudioPlaybackState){
    val live by LocalAudioPlayback.current.state.collectAsStateWithLifecycle()
    val state=if(live.file?.id==snapshot.file?.id)live else snapshot
    val player=LocalAudioPlayback.current
    var scrub by remember(state.file?.id){mutableStateOf<Float?>(null)}
    val duration=state.durationMs.coerceAtLeast(0)
    val position=(scrub?.toLong()?:state.positionMs).coerceIn(0,duration)
    Column(Modifier.fillMaxWidth()){
        key(state.file?.id){SlimSlider(value=position.toFloat(),onValueChange={scrub=it},
            onValueChangeFinished={if(player.state.value.file?.id==state.file?.id&&duration>0)scrub?.let{player.seek(it.toLong().coerceIn(0,duration))};scrub=null},
            enabled=state.file?.id==player.state.value.file?.id&&duration>0,
            valueRange=0f..duration.coerceAtLeast(1).toFloat(),trackHeight=5.dp,thumbSize=12.dp,
            activeColor=Color.White,inactiveColor=Color.White.copy(alpha=.25f),
            modifier=Modifier.fillMaxWidth().semantics{contentDescription="Seek audio"})}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text(Formatters.duration(position),style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=.65f),maxLines=1)
            Text(if(duration>0)Formatters.duration(duration)else"--:--",style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=.65f),maxLines=1)
        }
    }
}

@Composable
internal fun AudioTransport(state:AudioPlaybackState,isFavorite:Boolean?=null,favoriteEnabled:Boolean=true,onFavorite:()->Unit={}){
    val player=LocalAudioPlayback.current
    val active = state.file?.id == player.state.value.file?.id
    val pause = active && (state.playing || state.playRequested) && state.error == null
    val repeatButton: @Composable () -> Unit = {
        IconButton(player::cycleRepeat, enabled = active) {
            Icon(
                if (state.repeatMode == AudioRepeatMode.ONE) DriveIcons.RepeatOne else DriveIcons.Repeat,
                "Repeat: ${state.repeatMode.name.lowercase()}", Modifier.size(24.dp),
                tint = Color.White.copy(alpha = when {
                    !active -> .35f
                    state.repeatMode == AudioRepeatMode.OFF -> .55f
                    else -> 1f
                })
            )
        }
    }
    val favoriteButton: @Composable () -> Unit = {
        val selected = isFavorite == true
        val selection by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "favoriteSelection")
        IconButton(onFavorite, enabled = active && favoriteEnabled) {
            Box(
                Modifier.size(32.dp)
                    .background(Color.White.copy(alpha = .16f * selection), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(DriveIcons.Favorite, if (selected) "Remove from favorites" else "Add to favorites",
                    Modifier.size(24.dp), tint = Color.White.copy(alpha = if (active && favoriteEnabled) 1f else .35f))
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("audio-transport")) {
        val splitUtilities = isFavorite != null && maxWidth < singleRowTransportWidth
        Column {
            Row(
                Modifier.fillMaxWidth().height(80.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isFavorite != null && !splitUtilities) Arrangement.SpaceBetween
                else Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                if (isFavorite != null && !splitUtilities) repeatButton()
                val canGoBack = active && (state.canSkipPrevious || state.positionMs > 3000)
                IconButton(player::previous, enabled = canGoBack) {
                    Icon(MediaIcons.Previous, "Previous audio", Modifier.size(28.dp),
                        tint = Color.White.copy(alpha = if (canGoBack) 1f else .35f))
                }
                IconButton(
                    onClick = { if (!active) state.file?.let(player::play) else player.toggle() },
                    modifier = Modifier.size(64.dp).testTag("audio-play")
                ) {
                    Icon(if (pause) MediaIcons.Pause else MediaIcons.Play,
                        if (pause) "Pause audio" else if (active && state.error != null) "Retry audio" else "Play audio",
                        Modifier.size(40.dp))
                }
                val canGoForward = active && state.canSkipNext
                IconButton(player::next, enabled = canGoForward) {
                    Icon(MediaIcons.Next, "Next audio", Modifier.size(28.dp),
                        tint = Color.White.copy(alpha = if (canGoForward) 1f else .35f))
                }
                if (isFavorite != null && !splitUtilities) favoriteButton()
            }
            if (splitUtilities) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeatButton()
                    favoriteButton()
                }
            }
        }
    }
}

private val singleRowTransportWidth = 256.dp

/** Artwork, labels and transport have reserved bounds; asynchronous metadata never moves them. */
@Composable
private fun NowPlayingContent(file:DriveFile,modifier:Modifier=Modifier,swipe:AudioDismissDrag?=null){
    val player=LocalAudioPlayback.current
    val playback by remember(player){player.state.map{it.copy(positionMs=if(it.positionMs>3000)3001 else 0)}.distinctUntilChanged()}.collectAsStateWithLifecycle(remember(player){player.state.value})
    val active=playback.file?.id==file.id
    val state=if(active)playback else AudioPlaybackState(file=file,durationMs=file.durationMs?:0L)
    val repository=org.koin.compose.koinInject<FileRepository>()
    var favoriteBusy by remember(file.id){mutableStateOf(false)}
    var favoriteOverride by remember(file.id,file.isFavorite){mutableStateOf<Boolean?>(null)}
    var favoriteError by remember(file.id){mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()

    CompositionLocalProvider(LocalContentColor provides Color.White){
        BoxWithConstraints(modifier.padding(horizontal = 24.dp)) {
            val viewportHeight = maxHeight
            val typography = MaterialTheme.typography
            val density = LocalDensity.current
            val wide = maxWidth > viewportHeight
            val scroll = rememberScrollState()
            val noticeHeight = with(density) { maxOf(32.dp, typography.bodySmall.lineHeight.toDp() * 2) }
            val transportHeight = with(density) { 128.dp + typography.labelSmall.lineHeight.toDp() } +
                if (maxWidth < singleRowTransportWidth) 48.dp else 0.dp
            val lyricLineHeight = audioLyricLineHeight()
            val cover = if (wide) minOf(viewportHeight - 24.dp, maxWidth * .43f).coerceAtLeast(48.dp)
                else minOf(maxWidth, viewportHeight - transportHeight - noticeHeight - lyricLineHeight * 3 - 34.dp, 420.dp).coerceAtLeast(48.dp)
            val artwork: @Composable () -> Unit = {
                TrackArtwork(file, state.metadata, Modifier.size(cover).clip(RoundedCornerShape(16.dp))
                    .audioDismissGesture(swipe, if (wide) null else scroll).testTag("audio-cover"))
            }
            val favorite= favoriteOverride?:file.isFavorite
            val controls:@Composable ()->Unit={
                Column(Modifier.fillMaxWidth().testTag("audio-controls")){
                    AudioSeekBar(state)
                    AudioTransport(state,favorite,!favoriteBusy){
                        val next=!favorite
                        favoriteOverride=next;favoriteBusy=true;favoriteError=null
                        scope.launch{
                            try{repository.setFilesFavorite(listOf(file.id),next)}
                            catch(cancel:CancellationException){throw cancel}
                            catch(_:Exception){favoriteOverride=null;favoriteError="Couldn't update favorites. Try again."}
                            finally{favoriteBusy=false}
                        }
                    }
                }
            }
            val notice:@Composable ()->Unit={
                Box(Modifier.fillMaxWidth().height(noticeHeight).testTag("audio-status"),contentAlignment=Alignment.CenterStart){
                    val error=state.error?:state.queueError?:favoriteError
                    if(error!=null)Text(error,maxLines=2,overflow=TextOverflow.Ellipsis,color=Color.White.copy(alpha=.8f),style=MaterialTheme.typography.bodySmall)
                    else if(state.loading)Text("Buffering…",color=Color.White.copy(alpha=.65f),style=MaterialTheme.typography.bodySmall)
                }
            }
            val details: @Composable () -> Unit = {
                AudioLyricWindow(file.id, state.metadata.lyrics, lyricLineHeight, Modifier.audioDismissGesture(swipe, scroll))
                Spacer(Modifier.height(12.dp))
                controls()
                notice()
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    artwork()
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll),
                        verticalArrangement = Arrangement.Center) { details() }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.Center) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { artwork() }
                    Spacer(Modifier.height(22.dp))
                    details()
                }
            }
        }
    }
}

/** Frozen target snapshot keeps the outgoing artwork attached to the correct song during a fade. */
private data class ArtworkFrame(val file:DriveFile,val metadata:AudioMetadata)
@Composable
private fun TrackArtwork(file:DriveFile,metadata:AudioMetadata,modifier:Modifier){
    val frame=remember(file.id,file.modifiedAt,file.localPath,metadata.artworkPath,metadata.artworkColors){ArtworkFrame(file,metadata)}
    Crossfade(frame,modifier=modifier,animationSpec=tween(320, easing=FastOutSlowInEasing),label="trackArtwork"){
        AudioArtwork(it.file,it.metadata,Modifier.fillMaxSize())
    }
}

private fun artworkTone(metadata:AudioMetadata,index:Int):Color{
    val base=Color(0xFF11202C)
    val color=metadata.artworkColors.getOrNull(index)?.let{Color(it.toInt())}?:if(index==0)Color(0xFF286180)else Color(0xFF615485)
    return androidx.compose.ui.graphics.lerp(base,color,.38f)
}

/** Slow draw-layer movement; playback/lifecycle pause cancels it without changing the layout. */
@Composable
internal fun Modifier.ambientArtwork(state:AudioPlaybackState):Modifier {
    val frozen=remember{mutableFloatStateOf(.25f)}
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumed by remember(owner){mutableStateOf(owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))}
    DisposableEffect(owner){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,_->resumed=owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}
    }
    val motion=if(state.playing&&resumed&&!app.tgdrive.ui.LocalNavigationInProgress.current){
        val start=remember{frozen.floatValue}
        androidx.compose.animation.core.rememberInfiniteTransition(label="ambientMotion").animateFloat(
            initialValue=start,targetValue=if(start<.5f)1f else 0f,
            animationSpec=androidx.compose.animation.core.infiniteRepeatable(tween(22000),androidx.compose.animation.core.RepeatMode.Reverse),label="ambientPhase")
    }else null
    DisposableEffect(motion){onDispose{if(motion!=null)frozen.floatValue=motion.value}}
    val colors=state.metadata.artworkColors
    val first=animateColorAsState(colors.firstOrNull()?.let{Color(it.toInt())}?:Color(0xFF70687D),tween(1400),label="artworkColor")
    val second=animateColorAsState(colors.getOrNull(1)?.let{Color(it.toInt())}?:Color(0xFFA47759),tween(1400),label="secondaryArtworkColor")
    return drawBehind {
        val base=Color(0xFF10151D)
        val top=androidx.compose.ui.graphics.lerp(base,first.value,.32f)
        val bottom=androidx.compose.ui.graphics.lerp(base,second.value,.32f)
        drawRect(Brush.verticalGradient(listOf(top,bottom)))
        drawRect(Brush.radialGradient(listOf(top.copy(alpha=.2f),Color.Transparent),
            center=androidx.compose.ui.geometry.Offset(size.width*(motion?.value?:frozen.floatValue),size.height*.35f),radius=size.maxDimension*.8f))
        drawRect(Brush.radialGradient(listOf(bottom.copy(alpha=.2f),Color.Transparent),
            center=androidx.compose.ui.geometry.Offset(size.width*(1-(motion?.value?:frozen.floatValue)),size.height*.8f),radius=size.maxDimension*.7f))
    }
}

@Composable
private fun AudioArtwork(file:DriveFile,metadata:AudioMetadata,modifier:Modifier){
    val context=coil3.compose.LocalPlatformContext.current
    val model=remember(file.id,file.modifiedAt,file.localPath,metadata.artworkPath){
        metadata.artworkPath?:if(file.hasLocalCopy||file.hasRemoteCopy)
            coil3.request.ImageRequest.Builder(context).data(com.infinity.drive.core.media.thumbnailModel(file)).crossfade(160).build()else null
    }
    Box(modifier.background(Brush.linearGradient(listOf(artworkTone(metadata,0),artworkTone(metadata,1)))),contentAlignment=Alignment.Center){
        Icon(DriveIcons.MusicNotes,null,Modifier.size(56.dp),tint=Color.White.copy(alpha=.5f))
        if(model!=null)coil3.compose.AsyncImage(model,file.name,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
    }
}
