package com.infinity.drive.presentation.audio

import app.tgdrive.ui.*
import coil3.request.crossfade
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import app.tgdrive.ui.elasticVerticalScroll as verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.platform.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

private val audioInk = Color(0xFFF6F3FA)
private val audioSecondary = Color(0xFFC7BFD3)
private val audioAccent = Color(0xFFE7D9FF)
private val audioPanel = Color(0xFF282336)

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
                Box(Modifier.fillMaxSize().ambientArtwork(state).systemBarsPadding()){
                    NowPlayingContent(state.file!!,modifier=Modifier.fillMaxSize().padding(top=audioHeaderHeight()))
                    Row(Modifier.fillMaxWidth().height(audioHeaderHeight()).padding(end=24.dp),verticalAlignment=Alignment.CenterVertically){
                        IconButton({expanded=false}){Icon(DriveIcons.ArrowBack,"Back to library",tint=Color.White)}
                        AudioNowPlayingTitle(state.file!!,Modifier.weight(1f))
                    }
                }
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
fun FullscreenAudioPlayer(file:DriveFile,isActivePage:Boolean,modifier:Modifier=Modifier,onTrackChanged:(String)->Unit={}){
    val audio=LocalAudioPlayback.current
    val state by remember(audio){audio.state.map{it.copy(positionMs=0)}.distinctUntilChanged()}.collectAsStateWithLifecycle(remember(audio){audio.state.value.copy(positionMs=0)})
    Box(modifier.fillMaxSize().ambientArtwork(if(isActivePage)state else state.copy(playing=false)).systemBarsPadding().padding(top=audioHeaderHeight())){
        NowPlayingContent(if(isActivePage)state.file?:file else file,Modifier.fillMaxSize())
    }
}

@Composable
fun audioHeaderHeight()=maxOf(56.dp,(32*androidx.compose.ui.platform.LocalDensity.current.fontScale).dp)

@Composable
fun AudioNowPlayingTitle(file:DriveFile,modifier:Modifier=Modifier){
    Text("Now playing", modifier, color = audioInk, style = MaterialTheme.typography.titleMedium, maxLines = 1)
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
            activeColor=audioAccent,inactiveColor=Color(0xFF554A69),
            modifier=Modifier.fillMaxWidth().semantics{contentDescription="Seek audio"})}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text(Formatters.duration(position),style=MaterialTheme.typography.labelSmall,color=audioSecondary,maxLines=1)
            Text(if(duration>0)Formatters.duration(duration)else"--:--",style=MaterialTheme.typography.labelSmall,color=audioSecondary,maxLines=1)
        }
    }
}

@Composable
internal fun AudioTransport(state:AudioPlaybackState,isFavorite:Boolean?=null,favoriteEnabled:Boolean=true,onFavorite:()->Unit={}){
    val player=LocalAudioPlayback.current
    val active=state.file?.id==player.state.value.file?.id
    val pause=active&&((state.playing||state.playRequested)&&state.error==null)
    val repeatButton: @Composable () -> Unit = {
        IconButton(player::cycleRepeat, enabled = active) {
            Icon(
                if (state.repeatMode == AudioRepeatMode.ONE) DriveIcons.RepeatOne else DriveIcons.Repeat,
                "Repeat: ${state.repeatMode.name.lowercase()}", Modifier.size(23.dp),
                tint = if (!active) audioSecondary.copy(alpha = .35f)
                    else if (state.repeatMode == AudioRepeatMode.OFF) audioSecondary else audioAccent
            )
        }
    }
    val favoriteButton: @Composable () -> Unit = {
        val selected = isFavorite == true
        val selection by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "favoriteSelection")
        IconButton(onFavorite, enabled = active && favoriteEnabled) {
            Box(Modifier.size(32.dp).background(audioAccent.copy(alpha = .18f * selection), CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(DriveIcons.Favorite, if (selected) "Remove from favorites" else "Add to favorites",
                    Modifier.size(23.dp), tint = if (selected) audioAccent else audioSecondary)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("audio-transport")) {
        val splitUtilities = isFavorite != null && maxWidth < 260.dp
        val playSize = if (splitUtilities) 60.dp else 68.dp
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 80.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isFavorite != null && !splitUtilities) Arrangement.SpaceBetween
                else Arrangement.spacedBy(if (splitUtilities) 8.dp else 24.dp, Alignment.CenterHorizontally)
            ) {
                if (isFavorite != null && !splitUtilities) repeatButton()
                val canGoBack = active && (state.canSkipPrevious || state.positionMs > 3000)
                IconButton(player::previous, enabled = canGoBack) {
                    Icon(MediaIcons.Previous, "Previous audio", Modifier.size(26.dp),
                        tint = if (canGoBack) audioInk else audioSecondary.copy(alpha = .4f))
                }
                FilledIconButton(
                    onClick = { if (!active) state.file?.let(player::play) else player.toggle() },
                    modifier = Modifier.size(playSize).testTag("audio-play"),
                    shape = RoundedCornerShape(25.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = audioAccent, contentColor = Color(0xFF302642))
                ) {
                    Icon(if (pause) MediaIcons.Pause else MediaIcons.Play,
                        if (pause) "Pause audio" else if (active && state.error != null) "Retry audio" else "Play audio",
                        Modifier.size(32.dp))
                }
                val canGoForward = active && state.canSkipNext
                IconButton(player::next, enabled = canGoForward) {
                    Icon(MediaIcons.Next, "Next audio", Modifier.size(26.dp),
                        tint = if (canGoForward) audioInk else audioSecondary.copy(alpha = .4f))
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

/** Artwork, labels and transport have reserved bounds; asynchronous metadata never moves them. */
@Composable
internal fun NowPlayingContent(file:DriveFile,modifier:Modifier=Modifier){
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
        BoxWithConstraints(modifier.padding(horizontal = 20.dp)){
            val viewportHeight=maxHeight
            val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
            val wide=maxWidth>viewportHeight
            val titleHeight=(56*fontScale).dp
            val noticeHeight=(32*fontScale).dp
            val transportHeight=(168+20*fontScale).dp + if (maxWidth < 296.dp) 48.dp else 0.dp
            val cover=if(wide)minOf(viewportHeight-24.dp,maxWidth*.43f).coerceAtLeast(48.dp)
                else minOf(maxWidth - 24.dp, (viewportHeight - titleHeight - transportHeight - noticeHeight - 52.dp).coerceAtLeast(48.dp), 360.dp).coerceAtLeast(48.dp)
            val artwork:@Composable ()->Unit={TrackArtwork(file,state.metadata,Modifier.size(cover).clip(RoundedCornerShape(28.dp)).testTag("audio-cover"))}
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
            val title:@Composable ()->Unit={
                val labels=Triple(file.id,state.metadata.title?.takeIf(String::isNotBlank)?:file.name.substringBeforeLast('.'),
                    state.metadata.artist?.takeIf(String::isNotBlank)?:file.extension.uppercase().ifBlank{"Audio"})
                Crossfade(labels,Modifier.fillMaxWidth().height(titleHeight).testTag("audio-track-labels"),animationSpec=tween(240),label="audioTrackLabels"){(_,name,artist)->
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(name,color=Color.White,style=MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(artist,color=audioSecondary,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
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
                Surface(
                    Modifier.fillMaxWidth().testTag("audio-playback-panel"),
                    shape = RoundedCornerShape(28.dp), color = audioPanel, contentColor = audioInk
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        title()
                        Spacer(Modifier.height(8.dp))
                        controls()
                        notice()
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    artwork()
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center) { details() }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
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

/** Only artwork changes animate the background. Playback ticks never start a draw loop. */
@Composable
internal fun Modifier.ambientArtwork(state: AudioPlaybackState): Modifier {
    val base = Color(0xFF181522)
    val colors = state.metadata.artworkColors
    val first = animateColorAsState(colors.firstOrNull()?.let { Color(it.toInt()) } ?: base,
        tween(700), label = "artworkColor")
    val second = animateColorAsState(colors.getOrNull(1)?.let { Color(it.toInt()) } ?: base,
        tween(700), label = "secondaryArtworkColor")
    return drawBehind {
        drawRect(Brush.verticalGradient(listOf(
            androidx.compose.ui.graphics.lerp(base, first.value, .26f),
            androidx.compose.ui.graphics.lerp(base, second.value, .14f)
        )))
    }
}

@Composable
private fun AudioArtwork(file:DriveFile,metadata:AudioMetadata,modifier:Modifier){
    val context=coil3.compose.LocalPlatformContext.current
    val model=remember(file.id,file.modifiedAt,file.localPath,metadata.artworkPath){
        metadata.artworkPath?:if(file.hasLocalCopy||file.hasRemoteCopy)
            coil3.request.ImageRequest.Builder(context).data(com.infinity.drive.core.media.thumbnailModel(file)).crossfade(160).build()else null
    }
    val fallback = if (metadata.artworkColors.isEmpty()) Color(0xFF3B3555) else artworkTone(metadata, 0)
    Box(modifier.background(fallback).drawBehind {
        drawCircle(Color.White.copy(alpha = .055f), radius = size.minDimension * .38f)
        drawCircle(Color.White.copy(alpha = .06f), radius = size.minDimension * .27f)
    }, contentAlignment = Alignment.Center) {
        Icon(DriveIcons.MusicNote, null, Modifier.size(56.dp), tint = audioAccent.copy(alpha = .86f))
        if(model!=null)coil3.compose.AsyncImage(model,file.name,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
    }
}
