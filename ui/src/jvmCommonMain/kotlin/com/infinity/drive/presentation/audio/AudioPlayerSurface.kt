package com.infinity.drive.presentation.audio

import app.tgdrive.ui.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
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
                    NowPlayingContent(state.file!!,onStop={expanded=false;player.stop()},modifier=Modifier.fillMaxWidth().align(Alignment.Center).padding(top=48.dp))
                    IconButton({expanded=false},Modifier.align(Alignment.TopStart)){Icon(DriveIcons.ArrowBack,"Back to library")}
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
    Box(modifier.fillMaxSize().ambientArtwork(state).statusBarsPadding().navigationBarsPadding().padding(top=64.dp),contentAlignment=Alignment.Center){NowPlayingContent(file,player::stop,Modifier.fillMaxWidth())}
}

/** The sheet and fullscreen player use the same bounded, scrollable layout. */
@Composable
private fun NowPlayingContent(file:DriveFile,onStop:()->Unit,modifier:Modifier=Modifier){
    val player=LocalAudioPlayback.current
    val state by player.state.collectAsStateWithLifecycle()
    val active=state.file?.id==file.id
    val pause=active&&(state.playing||(state.loading&&state.playRequested))
    val duration=if(active)state.durationMs else file.durationMs?:0L
    key(file.id){
        var scrub by remember{mutableStateOf<Float?>(null)}
        BoxWithConstraints(modifier){
            val cover=minOf(maxWidth-48.dp,if(maxHeight<500.dp)160.dp else 320.dp)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
                Column(Modifier.fillMaxWidth().padding(top=8.dp,bottom=12.dp)){
                    Text(state.metadata.title?:file.name.substringBeforeLast('.'),style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(state.metadata.artist?:"Unknown artist",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Crossfade(file,animationSpec=tween(240),label="audioCover"){shown->FileThumbnail(shown,Modifier.size(cover).clip(RoundedCornerShape(14.dp)))}
                if(active&&state.error!=null)Text(state.error!!,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                else Text(if(active&&state.loading)"Loading audio…"else " ",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.fillMaxWidth()){
                    SlimSlider(value=scrub?:state.positionMs.coerceIn(0,duration.coerceAtLeast(0)).toFloat(),onValueChange={scrub=it},onValueChangeFinished={scrub?.let{player.seek(it.toLong())};scrub=null},enabled=active&&duration>0,valueRange=0f..duration.coerceAtLeast(1).toFloat(),trackHeight=3.dp,thumbSize=12.dp)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                        Text(Formatters.duration(scrub?.toLong()?:if(active)state.positionMs else 0),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formatters.duration(duration),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp,Alignment.CenterHorizontally)){
                    IconButton(player::previous,enabled=active&&(state.canSkipPrevious||state.positionMs>3000)){Icon(DriveIcons.SkipPrevious,"Previous audio",Modifier.size(28.dp))}
                    FilledIconButton(onClick={if(!active)player.play(file)else player.toggle()},modifier=Modifier.size(72.dp),shape=CircleShape,colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.Transparent,contentColor=MaterialTheme.colorScheme.onSurface)){
                        Icon(if(pause)DriveIcons.Pause else DriveIcons.PlayArrow,if(pause)"Pause audio"else if(active&&state.error!=null)"Retry audio"else"Play audio",Modifier.size(34.dp))
                    }
                    IconButton(player::next,enabled=active&&state.canSkipNext){Icon(DriveIcons.SkipNext,"Next audio",Modifier.size(28.dp))}
                }
                var showLyrics by remember{mutableStateOf(false)}
                if(active&&state.metadata.lyrics!=null){
                    TextButton({showLyrics=!showLyrics}){Text(if(showLyrics)"Hide lyrics"else"Lyrics")}
                    AnimatedVisibility(showLyrics){Text(state.metadata.lyrics.orEmpty(),style=MaterialTheme.typography.bodyLarge,modifier=Modifier.fillMaxWidth().padding(vertical=12.dp))}
                }
                TextButton(onStop,enabled=active){Text("Stop playback")}
            }
        }
    }
}

@Composable
private fun Modifier.ambientArtwork(state:AudioPlaybackState):Modifier {
    val transition=androidx.compose.animation.core.rememberInfiniteTransition(label="artworkAmbient")
    val phase=transition.animateFloat(0f,1f,androidx.compose.animation.core.infiniteRepeatable(tween(18000),androidx.compose.animation.core.RepeatMode.Reverse),label="artworkDrift")
    val colors=state.metadata.artworkColors
    val first by animateColorAsState(colors.firstOrNull()?.let{Color(it.toInt())}?:MaterialTheme.colorScheme.primary,tween(900),label="artworkColor")
    val second by animateColorAsState(colors.getOrNull(1)?.let{Color(it.toInt())}?:MaterialTheme.colorScheme.tertiary,tween(900),label="secondaryArtworkColor")
    val base=MaterialTheme.colorScheme.background
    return drawBehind {
        drawRect(base)
        val offset=if(state.playing)phase.value else .5f
        drawRect(Brush.radialGradient(listOf(first.copy(alpha=.35f),Color.Transparent),center=androidx.compose.ui.geometry.Offset(size.width*offset,size.height*.3f),radius=size.maxDimension))
        drawRect(Brush.radialGradient(listOf(second.copy(alpha=.28f),Color.Transparent),center=androidx.compose.ui.geometry.Offset(size.width*(1-offset),size.height*.85f),radius=size.maxDimension*.8f))
    }
}
