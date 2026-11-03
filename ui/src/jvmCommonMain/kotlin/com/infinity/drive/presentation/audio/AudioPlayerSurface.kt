package com.infinity.drive.presentation.audio

import app.tgdrive.ui.*
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.components.FileThumbnail
import com.infinity.drive.presentation.platform.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryAudioMiniPlayer(){
    val player=LocalAudioPlayback.current
    val state by player.state.collectAsStateWithLifecycle()
    var last by remember{mutableStateOf(AudioPlaybackState())}
    var expanded by rememberSaveable{mutableStateOf(false)}
    LaunchedEffect(state.file,state.metadata){if(state.file!=null)last=state}
    AnimatedVisibility(state.file!=null,enter=expandVertically(tween(260))+fadeIn(tween(220)),exit=shrinkVertically(tween(200))+fadeOut(tween(160))){
        val current=if(state.file!=null)state else last
        val file=current.file
        if(file!=null){
            val pause=current.playing||(current.loading&&current.playRequested)
            Surface(modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){
                Column {
                    Row(Modifier.fillMaxWidth().clickable(onClickLabel="Open now playing"){expanded=true}.padding(start=12.dp,end=4.dp,top=10.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        FileThumbnail(file,Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                            Text(current.metadata.title?:file.name.substringBeforeLast('.'),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(current.error?:if(current.loading)"Loading audio…"else current.metadata.artist?:"Unknown artist",style=MaterialTheme.typography.bodySmall,color=if(current.error!=null)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        FilledIconButton(player::toggle,shape=CircleShape,colors=IconButtonDefaults.filledIconButtonColors(containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary)){
                            Icon(if(pause)DriveIcons.Pause else DriveIcons.PlayArrow,if(current.error!=null)"Retry audio"else if(pause)"Pause audio"else"Play audio")
                        }
                        IconButton(player::stop){Icon(DriveIcons.Close,"Close audio player",Modifier.size(20.dp))}
                    }
                    LinearProgressIndicator(progress={if(current.durationMs>0)current.positionMs.toFloat()/current.durationMs else 0f},modifier=Modifier.fillMaxWidth().height(2.dp),gapSize=0.dp,drawStopIndicator={})
                }
            }
        }
    }
    if(expanded&&state.file!=null){
        androidx.compose.ui.window.Dialog(onDismissRequest={expanded=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
            Surface(Modifier.fillMaxSize()){
                Box(Modifier.fillMaxSize().ambientArtwork(state).systemBarsPadding()){
                    NowPlayingContent(state.file!!,onStop={expanded=false;player.stop()},modifier=Modifier.fillMaxSize().padding(top=audioHeaderHeight()))
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
fun FullscreenAudioPlayer(file:DriveFile,isActivePage:Boolean,modifier:Modifier=Modifier,onTrackChanged:(String)->Unit={}){
    val player=LocalAudioPlayback.current
    val state by player.state.collectAsStateWithLifecycle()
    var connected by remember(file.id){mutableStateOf(false)}
    val notifyTrack by rememberUpdatedState(onTrackChanged)
    LaunchedEffect(file.id,isActivePage){
        if(isActivePage){if(player.state.value.file?.id!=file.id)player.play(file);connected=true}
        else {connected=false;if(player.state.value.file?.id==file.id)player.pause()}
    }
    LaunchedEffect(state.file?.id,connected,isActivePage){
        val id=state.file?.id
        if(connected&&isActivePage&&id!=null&&id!=file.id)notifyTrack(id)
    }
    Box(modifier.fillMaxSize().ambientArtwork(state).systemBarsPadding().padding(top=audioHeaderHeight())){
        NowPlayingContent(file,player::stop,Modifier.fillMaxSize())
    }
}

@Composable
fun audioHeaderHeight() = maxOf(72.dp,(52 * androidx.compose.ui.platform.LocalDensity.current.fontScale).dp)

@Composable
fun AudioNowPlayingTitle(file:DriveFile,modifier:Modifier=Modifier){
    val state by LocalAudioPlayback.current.state.collectAsStateWithLifecycle()
    val metadata=state.metadata.takeIf{state.file?.id==file.id}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(2.dp)){
        Text(metadata?.title?.takeIf(String::isNotBlank)?:file.name.substringBeforeLast('.'),color=Color.White,
            style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(metadata?.artist?.takeIf(String::isNotBlank)?:"Unknown artist",color=Color.White.copy(alpha=.65f),
            style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

/** Shared seek state is committed only on release; lyric seeking uses this same player. */
@Composable
internal fun AudioSeekBar(state:AudioPlaybackState){
    val player=LocalAudioPlayback.current
    var scrub by remember(state.file?.id){mutableStateOf<Float?>(null)}
    val duration=state.durationMs.coerceAtLeast(0)
    val position=scrub?.toLong()?:state.positionMs.coerceIn(0,duration)
    Column(Modifier.fillMaxWidth()){
        SlimSlider(value=position.toFloat(),onValueChange={scrub=it},
            onValueChangeFinished={scrub?.let{player.seek(it.toLong())};scrub=null},
            enabled=state.file?.id==player.state.value.file?.id&&duration>0,
            valueRange=0f..duration.coerceAtLeast(1).toFloat(),trackHeight=2.dp,thumbSize=0.dp,
            activeColor=Color.White,inactiveColor=Color.White.copy(alpha=.25f),
            modifier=Modifier.fillMaxWidth().semantics{contentDescription="Seek audio"})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text(Formatters.duration(position),style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=.65f))
            Text(Formatters.duration(duration),style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=.65f))
        }
    }
}

@Composable
internal fun AudioTransport(state:AudioPlaybackState){
    val player=LocalAudioPlayback.current
    val active=state.file?.id==player.state.value.file?.id
    val pause=active&&(state.playing||(state.loading&&state.playRequested))
    Row(Modifier.fillMaxWidth().height(72.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(32.dp,Alignment.CenterHorizontally)){
        IconButton(player::previous,enabled=active&&(state.canSkipPrevious||state.positionMs>3000)){
            Icon(DriveIcons.SkipPrevious,"Previous audio",Modifier.size(30.dp))
        }
        IconButton(onClick={if(!active)state.file?.let(player::play)else player.toggle()},modifier=Modifier.size(68.dp)){
            Icon(if(pause)DriveIcons.Pause else DriveIcons.PlayArrow,
                if(pause)"Pause audio"else if(active&&state.error!=null)"Retry audio"else"Play audio",Modifier.size(38.dp))
        }
        IconButton(player::next,enabled=active&&state.canSkipNext){Icon(DriveIcons.SkipNext,"Next audio",Modifier.size(30.dp))}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingContent(file:DriveFile,onStop:()->Unit,modifier:Modifier=Modifier){
    val player=LocalAudioPlayback.current
    val playback by player.state.collectAsStateWithLifecycle()
    val active=playback.file?.id==file.id
    val state=if(active)playback else AudioPlaybackState(file=file,durationMs=file.durationMs?:0L)
    var showLyrics by rememberSaveable(file.id){mutableStateOf(false)}
    var showInfo by rememberSaveable(file.id){mutableStateOf(false)}
    var showQueue by rememberSaveable(file.id){mutableStateOf(false)}
    var options by remember(file.id){mutableStateOf(false)}
    var timer by remember(file.id){mutableStateOf(false)}
    val lyrics=remember(state.metadata.lyrics){AudioLyricsParser.parse(state.metadata.lyrics)}
    CompositionLocalProvider(LocalContentColor provides Color.White){
        BoxWithConstraints(modifier.padding(horizontal=24.dp)){
            val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
            val wide=maxWidth>maxHeight
            val cover=if(wide)minOf(maxHeight-24.dp,maxWidth*.43f).coerceAtLeast(80.dp)
                else minOf(maxWidth,(maxHeight-(360*fontScale).dp).coerceAtLeast(96.dp),420.dp)
            val artwork:@Composable ()->Unit={
                Crossfade(file,animationSpec=tween(320),label="audioCover"){shown->
                    FileThumbnail(shown,Modifier.size(cover).clip(RoundedCornerShape(12.dp)))
                }
            }
            val controls:@Composable ()->Unit={
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    LyricsSnippet(lyrics,state.positionMs,{showLyrics=true},Modifier.fillMaxWidth())
                    if(state.error!=null)Text(state.error,color=Color(0xFFFFC7C2),style=MaterialTheme.typography.bodySmall)
                    else if(state.loading)Text("Loading audio…",color=Color.White.copy(alpha=.7f),style=MaterialTheme.typography.bodySmall)
                    AudioSeekBar(state)
                    AudioTransport(state)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                        IconButton(player::cycleRepeat,enabled=active){
                            Icon(if(state.repeatMode==AudioRepeatMode.ONE)DriveIcons.RepeatOne else DriveIcons.Repeat,
                                "Repeat: ${state.repeatMode.name.lowercase()}",Modifier.size(23.dp),
                                tint=Color.White.copy(alpha=if(state.repeatMode==AudioRepeatMode.OFF).55f else 1f))
                        }
                        Box {
                            IconButton({timer=true},enabled=active){Icon(DriveIcons.Update,"Sleep timer",Modifier.size(23.dp),
                                tint=Color.White.copy(alpha=if(state.sleepTimerMinutes==null).55f else 1f))}
                            DropdownMenu(timer,{timer=false}){
                                listOf(15,30,60).forEach{minutes->DropdownMenuItem(text={Text("$minutes minutes")},onClick={timer=false;player.setSleepTimer(minutes)})}
                                if(state.sleepTimerMinutes!=null)DropdownMenuItem(text={Text("Cancel sleep timer")},onClick={timer=false;player.setSleepTimer(null)})
                            }
                        }
                        IconButton({showLyrics=true},enabled=lyrics.plainText.isNotBlank()){
                            Icon(DriveIcons.Subtitles,"Show lyrics",Modifier.size(23.dp))
                        }
                        IconButton({showQueue=true},enabled=active){Icon(DriveIcons.FormatListBulleted,"Play queue",Modifier.size(23.dp))}
                        Box {
                            IconButton({options=true}){Icon(DriveIcons.MoreHoriz,"Playback options",Modifier.size(23.dp))}
                            DropdownMenu(options,{options=false}){
                                DropdownMenuItem(text={Text("File details")},onClick={options=false;showInfo=true})
                                DropdownMenuItem(text={Text("Stop playback")},onClick={options=false;onStop()},enabled=active)
                            }
                        }
                    }
                }
            }
            if(wide){
                Row(Modifier.fillMaxSize().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(24.dp),verticalAlignment=Alignment.CenterVertically){
                    artwork();Box(Modifier.weight(1f).verticalScroll(rememberScrollState())){controls()}
                }
            }else{
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top=16.dp,bottom=12.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(24.dp)){
                    artwork();controls()
                }
            }
        }
    }
    if(showLyrics&&active)SynchronizedLyrics(lyrics,{showLyrics=false})
    if(showInfo)com.infinity.drive.presentation.components.FileInfoSheet(file,{showInfo=false})
    if(showQueue){
        var queue by remember{mutableStateOf<List<DriveFile>?>(null)}
        LaunchedEffect(playback.file?.id){queue=player.queuedFiles()}
        ModalBottomSheet(onDismissRequest={showQueue=false}){
            Text("Play queue",Modifier.padding(24.dp),style=MaterialTheme.typography.titleLarge)
            if(queue==null)LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=24.dp))
            else if(queue!!.isEmpty())Text("No other tracks in this queue",Modifier.padding(24.dp))
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max=420.dp)){
                items(queue!!,key={it.id}){track->
                    ListItem(headlineContent={Text(track.name.substringBeforeLast('.'),maxLines=1,overflow=TextOverflow.Ellipsis)},
                        leadingContent={FileThumbnail(track,Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))},
                        trailingContent={if(track.id==playback.file?.id)Icon(DriveIcons.Audiotrack,"Playing")},
                        modifier=Modifier.clickable{showQueue=false;player.play(track)})
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
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
    val motion=if(state.playing&&resumed){
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
