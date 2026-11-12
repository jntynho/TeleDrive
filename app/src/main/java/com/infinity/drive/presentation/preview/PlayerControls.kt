package com.infinity.drive.presentation.preview

import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.SlimSlider
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import app.tgdrive.ui.elasticVerticalScroll as verticalScroll
import app.tgdrive.ui.elasticHorizontalScroll as horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.infinity.drive.presentation.common.Formatters
import kotlinx.coroutines.delay

/** One quiet toolbar in both orientations. No subtitle/audio-track picker or expandable tools. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(
    player:Player,visible:Boolean,audioOnly:Boolean,resizeMode:PlayerResizeMode,
    onCycleResizeMode:()->Unit,onInteraction:()->Unit,
    onScrubbingChanged:(Boolean)->Unit={},onOverlayChanged:(Boolean)->Unit={},
    modifier:Modifier=Modifier
) {
    var requested by remember(player){mutableStateOf(player.playWhenReady)}
    var playbackState by remember(player){mutableIntStateOf(player.playbackState)}
    var buffered by remember(player){mutableLongStateOf(player.bufferedPosition.coerceAtLeast(0))}
    var position by remember(player){mutableLongStateOf(player.currentPosition.coerceAtLeast(0))}
    var duration by remember(player){mutableLongStateOf(player.duration.coerceAtLeast(0))}
    var seekable by remember(player){mutableStateOf(player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))}
    var rate by remember(player){mutableFloatStateOf(player.playbackParameters.speed)}
    var scrub by remember(player){mutableStateOf<Float?>(null)}
    var speedMenu by remember(player){mutableStateOf(false)}
    val overlay by rememberUpdatedState(onOverlayChanged)
    val scrubbing by rememberUpdatedState(onScrubbingChanged)
    val activity=LocalActivity.current
    val orientationOwner=LocalVideoOrientation.current
    var fallbackRotation by rememberSaveable{mutableStateOf(PlayerRotation.AUTO)}
    val rotation=orientationOwner?.rotation?.value?:fallbackRotation
    val originalOrientation=rememberSaveable{activity?.requestedOrientation?:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}

    DisposableEffect(player){
        val listener=object:Player.Listener {
            override fun onEvents(player:Player,events:Player.Events){
                requested=player.playWhenReady;playbackState=player.playbackState
                duration=player.duration.coerceAtLeast(0);rate=player.playbackParameters.speed
                seekable=player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            }
            override fun onPositionDiscontinuity(oldPosition:Player.PositionInfo,newPosition:Player.PositionInfo,reason:Int){position=newPosition.positionMs.coerceAtLeast(0)}
        }
        player.addListener(listener)
        onDispose{player.removeListener(listener);scrubbing(false);overlay(false)}
    }
    DisposableEffect(activity,orientationOwner){onDispose{
        if(orientationOwner==null&&activity!=null&&!activity.isChangingConfigurations)activity.requestedOrientation=originalOrientation
    }}
    // Read the authoritative clock only while visible. The isolated timeline owns frame updates.
    LaunchedEffect(player,visible){while(visible){
        position=player.currentPosition.coerceAtLeast(0)
        duration=player.duration.coerceAtLeast(0);buffered=player.bufferedPosition.coerceAtLeast(0)
        delay(250)
    }}
    LaunchedEffect(speedMenu){overlay(speedMenu)}
    LaunchedEffect(visible){if(!visible){speedMenu=false;scrub=null;scrubbing(false)}}

    if(speedMenu){
        MaterialTheme(colorScheme=darkColorScheme(primary=Color.White,surface=Color(0xFF191B1E),surfaceContainerLow=Color(0xFF191B1E))){
            com.infinity.drive.presentation.components.DriveBottomSheet(onDismissRequest={speedMenu=false;onInteraction()}){dismiss->
                val closeThen=com.infinity.drive.presentation.components.LocalSheetCloseThen.current
                Column(Modifier.fillMaxWidth().heightIn(max=440.dp).verticalScroll(rememberScrollState()).padding(bottom=16.dp)){
                    Text("Playback speed",Modifier.padding(horizontal=24.dp,vertical=12.dp),style=MaterialTheme.typography.titleLarge)
                    listOf(.5f,.75f,1f,1.25f,1.5f,1.75f,2f).forEach{value->
                        ListItem(headlineContent={Text(if(value==1f)"Normal · 1×"else speedLabel(value))},
                            leadingContent={Icon(DriveIcons.SlowMotionVideo,null)},
                            trailingContent={if(kotlin.math.abs(value-rate)<.001f)Icon(DriveIcons.Check,"Selected")},
                            colors=ListItemDefaults.colors(containerColor=Color.Transparent),
                            modifier=Modifier.clickable{closeThen{player.setPlaybackSpeed(value);speedMenu=false;onInteraction()}})
                    }
                }
            }
        }
    }
    AnimatedVisibility(visible,enter=fadeIn(tween(200)),exit=fadeOut(tween(160)),modifier=modifier.fillMaxSize()){
        BoxWithConstraints(Modifier.fillMaxSize()){
            val viewportHeight=maxHeight
            val compact=maxWidth>viewportHeight||viewportHeight<400.dp
            val iconSize=if(compact)22.dp else 28.dp
            val playSize=if(compact)28.dp else 34.dp
            val ended=playbackState==Player.STATE_ENDED
            val canSeek=seekable&&duration>0
            if(viewportHeight>=240.dp)Row(Modifier.align(Alignment.Center).testTag("video-transport"),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(if(compact)20.dp else 28.dp)){
                SeekButton(DriveIcons.Replay10,"Back 10 seconds",iconSize,canSeek){onInteraction();player.seekTo(videoSeekTarget(player.currentPosition,-10_000,player.duration))}
                IconButton(onClick={onInteraction();when{ended->{player.seekTo(0);player.play()};player.playWhenReady->player.pause();else->player.play()}},
                    modifier=Modifier.size(if(compact)48.dp else 64.dp).testTag("video-play")){
                    Box(contentAlignment=Alignment.Center){
                        if(playbackState==Player.STATE_BUFFERING&&requested)CircularProgressIndicator(
                            Modifier.size(if(compact)40.dp else 50.dp),color=Color.White.copy(alpha=.55f),strokeWidth=1.5.dp)
                        Icon(if(ended||!requested)DriveIcons.PlayArrow else DriveIcons.Pause,
                            if(ended)"Replay"else if(requested)"Pause"else"Play",Modifier.size(playSize),tint=Color.White)
                    }
                }
                SeekButton(DriveIcons.Forward10,"Forward 10 seconds",iconSize,canSeek){onInteraction();player.seekTo(videoSeekTarget(player.currentPosition,10_000,player.duration))}
            }
            val speed:@Composable ()->Unit={
                IconButton({onInteraction();speedMenu=true},modifier=Modifier.semantics{contentDescription="Playback speed, ${speedLabel(rate)}"}){
                    Icon(DriveIcons.SlowMotionVideo,null,Modifier.size(if(compact)21.dp else 24.dp),tint=Color.White)
                }
            }
            val fit:@Composable ()->Unit={IconButton({onInteraction();onCycleResizeMode()}){
                Icon(resizeMode.icon,resizeMode.description,Modifier.size(if(compact)18.dp else 22.dp),tint=Color.White)
            }}
            val rotate:@Composable ()->Unit={IconButton({onInteraction();val next=rotation.next();if(orientationOwner!=null)orientationOwner.select(next)else{fallbackRotation=next;activity?.requestedOrientation=next.orientation}}){
                Icon(rotation.icon,rotation.description,Modifier.size(if(compact)20.dp else 24.dp).graphicsLayer{rotationZ=if(rotation==PlayerRotation.LANDSCAPE)90f else 0f;alpha=if(rotation==PlayerRotation.AUTO).7f else 1f},tint=Color.White)
            }}
            val timeline:@Composable (Modifier)->Unit={trackModifier->
                Box(trackModifier){
                val displayPosition=interpolatedVideoPosition(player,position,visible,requested&&playbackState==Player.STATE_READY,rate)
                SlimSlider(scrub?:displayPosition.coerceIn(0,duration).toFloat(),onValueChange={scrub=it;scrubbing(true);onInteraction()},
                    onValueChangeFinished={scrub?.let{player.seekTo(videoSeekTarget(it.toLong(),0,duration))};scrub=null;scrubbing(false);onInteraction()},
                    valueRange=0f..duration.coerceAtLeast(1).toFloat(),enabled=canSeek,
                    trackHeight=3.dp,thumbSize=8.dp,
                    activeColor=Color.White,inactiveColor=Color.White.copy(alpha=.3f),
                    bufferedFraction=if(duration>0)buffered.toFloat()/duration else 0f,bufferedColor=Color.White.copy(alpha=.4f),
                    modifier=Modifier.fillMaxWidth().semantics{contentDescription="Seek video"})
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.75f))))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal+WindowInsetsSides.Bottom))
                .padding(horizontal=if(compact)16.dp else 24.dp,vertical=8.dp)){
                timeline(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).then(if(viewportHeight<240.dp)Modifier.horizontalScroll(rememberScrollState())else Modifier),verticalAlignment=Alignment.CenterVertically){
                    if(viewportHeight<240.dp){
                        SeekButton(DriveIcons.Replay10,"Back 10 seconds",iconSize,canSeek){onInteraction();player.seekTo(videoSeekTarget(player.currentPosition,-10_000,player.duration))}
                        IconButton({onInteraction();if(ended){player.seekTo(0);player.play()}else if(player.playWhenReady)player.pause()else player.play()}){Icon(if(ended||!requested)DriveIcons.PlayArrow else DriveIcons.Pause,if(requested)"Pause"else"Play",Modifier.size(playSize),tint=Color.White)}
                        SeekButton(DriveIcons.Forward10,"Forward 10 seconds",iconSize,canSeek){onInteraction();player.seekTo(videoSeekTarget(player.currentPosition,10_000,player.duration))}
                    }
                    Text("${Formatters.duration((scrub?.toLong()?:position).coerceIn(0,duration.coerceAtLeast(0)))} / ${if(duration>0)Formatters.duration(duration)else "--:--"}",
                        color=Color.White.copy(alpha=.85f),style=MaterialTheme.typography.labelSmall,
                        modifier=if(viewportHeight<240.dp)Modifier.width(100.dp)else Modifier.weight(1f),maxLines=1)
                    speed();fit();rotate()
                }
            }
        }
    }
}

@Composable
private fun SeekButton(icon:ImageVector,description:String,size:androidx.compose.ui.unit.Dp,enabled:Boolean,onClick:()->Unit){
    IconButton(onClick,enabled=enabled,modifier=Modifier.size(48.dp)){
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)){
            Icon(icon,description,Modifier.size(size),tint=Color.White.copy(alpha=if(enabled)1f else .35f))

        }
    }
}
private fun speedLabel(value:Float)=if(value%1f==0f)"${value.toInt()}×"else"${value}×"
/** Video scaling applied to the surface, cycled from the controls. */
enum class PlayerResizeMode(val icon: ImageVector, val description: String) {
    FIT(app.tgdrive.ui.MediaIcons.Fit, "Scaling: fit"),
    ZOOM(app.tgdrive.ui.MediaIcons.Crop, "Scaling: crop to fill"),
    FILL(app.tgdrive.ui.MediaIcons.Fit, "Scaling: stretch");

    fun next(): PlayerResizeMode = if (this == FIT) ZOOM else FIT
}

/** Screen orientation while a video is open, cycled from the controls. */
internal enum class PlayerRotation(
    val icon: ImageVector,
    val description: String,
    val orientation: Int
) {
    AUTO(
        app.tgdrive.ui.DriveIcons.ScreenRotation,
        "Rotation: follow device",
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    ),
    LANDSCAPE(
        app.tgdrive.ui.DriveIcons.ScreenRotation,
        "Rotation: locked landscape",
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    ),
    PORTRAIT(
        app.tgdrive.ui.DriveIcons.ScreenRotation,
        "Rotation: locked portrait",
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    );

    fun next(): PlayerRotation = entries[(ordinal + 1) % entries.size]
}

/** Only the timeline interpolates; transport/header composition remains at player-event frequency. */
@Composable
private fun interpolatedVideoPosition(player:Player,anchor:Long,visible:Boolean,advancing:Boolean,speed:Float):Long {
    val position by produceState(anchor,player,anchor,visible,advancing,speed){
        value=anchor
        if(visible&&advancing){
            val start=androidx.compose.animation.core.withInfiniteAnimationFrameNanos{it}
            while(true)androidx.compose.animation.core.withInfiniteAnimationFrameNanos{frame->value=anchor+(((frame-start)/1_000_000).coerceIn(0,350)*speed).toLong()}
        }
    }
    return position
}
