package com.infinity.drive.presentation.preview

import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.SlimSlider
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@Composable
fun PlayerControls(
    player:Player,visible:Boolean,audioOnly:Boolean,resizeMode:PlayerResizeMode,
    onCycleResizeMode:()->Unit,onInteraction:()->Unit,
    onScrubbingChanged:(Boolean)->Unit={},onOverlayChanged:(Boolean)->Unit={},
    modifier:Modifier=Modifier
) {
    var requested by remember(player){mutableStateOf(player.playWhenReady)}
    var playbackState by remember(player){mutableIntStateOf(player.playbackState)}
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
    LaunchedEffect(player,visible){while(visible){position=player.currentPosition.coerceAtLeast(0);duration=player.duration.coerceAtLeast(0);delay(250)}}
    LaunchedEffect(speedMenu){overlay(speedMenu)}
    LaunchedEffect(visible){if(!visible){speedMenu=false;scrub=null;scrubbing(false)}}

    AnimatedVisibility(visible,enter=fadeIn(tween(200)),exit=fadeOut(tween(160)),modifier=modifier.fillMaxSize()){
        BoxWithConstraints(Modifier.fillMaxSize()){
            val compact=maxWidth>maxHeight||maxHeight<400.dp
            val iconSize=if(compact)20.dp else 26.dp
            val playSize=if(compact)30.dp else 38.dp
            val ended=playbackState==Player.STATE_ENDED
            val canSeek=seekable&&duration>0
            Row(Modifier.align(Alignment.Center).testTag("video-transport"),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(if(compact)20.dp else 28.dp)){
                SeekButton(DriveIcons.SkipPrevious,"Back 10 seconds","−10s",iconSize,canSeek){onInteraction();player.seekTo(videoSeekTarget(player.currentPosition,-10_000,player.duration))}
                IconButton(onClick={onInteraction();when{ended->{player.seekTo(0);player.play()};player.playWhenReady->player.pause();else->player.play()}},
                    modifier=Modifier.size(if(compact)48.dp else 64.dp).testTag("video-play")){
                    Box(contentAlignment=Alignment.Center){
                        if(playbackState==Player.STATE_BUFFERING&&requested)CircularProgressIndicator(
                            Modifier.size(if(compact)40.dp else 50.dp),color=Color.White.copy(alpha=.55f),strokeWidth=1.5.dp)
                        Icon(if(ended||!requested)DriveIcons.PlayArrow else DriveIcons.Pause,
                            if(ended)"Replay"else if(requested)"Pause"else"Play",Modifier.size(playSize),tint=Color.White)
                    }
                }
                SeekButton(DriveIcons.SkipNext,"Forward 10 seconds","+10s",iconSize,canSeek){onInteraction();player.seekTo(videoSeekTarget(player.currentPosition,10_000,player.duration))}
            }
            val speed:@Composable ()->Unit={
                Box {
                    TextButton({onInteraction();speedMenu=true},Modifier.heightIn(min=48.dp)){
                        Text(speedLabel(rate),color=Color.White,style=MaterialTheme.typography.labelMedium)
                    }
                    DropdownMenu(speedMenu,{speedMenu=false;onInteraction()},containerColor=Color(0xFF191B1E)){
                        listOf(.5f,.75f,1f,1.25f,1.5f,2f).forEach{value->
                            DropdownMenuItem(text={Text(speedLabel(value),color=Color.White)},
                                onClick={player.setPlaybackSpeed(value);speedMenu=false;onInteraction()})
                        }
                    }
                }
            }
            val fit:@Composable ()->Unit={IconButton({onInteraction();onCycleResizeMode()}){
                Icon(resizeMode.icon,resizeMode.description,Modifier.size(if(compact)18.dp else 22.dp),tint=Color.White)
            }}
            val rotate:@Composable ()->Unit={IconButton({onInteraction();val next=rotation.next();if(orientationOwner!=null)orientationOwner.select(next)else{fallbackRotation=next;activity?.requestedOrientation=next.orientation}}){
                Icon(rotation.icon,rotation.description,Modifier.size(if(compact)18.dp else 22.dp),tint=Color.White)
            }}
            val timeline:@Composable (Modifier)->Unit={trackModifier->
                SlimSlider(scrub?:position.coerceIn(0,duration).toFloat(),onValueChange={scrub=it;scrubbing(true);onInteraction()},
                    onValueChangeFinished={scrub?.let{player.seekTo(videoSeekTarget(it.toLong(),0,duration))};scrub=null;scrubbing(false);onInteraction()},
                    valueRange=0f..duration.coerceAtLeast(1).toFloat(),enabled=canSeek,
                    trackHeight=2.dp,thumbSize=8.dp,
                    activeColor=Color.White,inactiveColor=Color.White.copy(alpha=.3f),
                    modifier=trackModifier.semantics{contentDescription="Seek video"})
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.75f))))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal+WindowInsetsSides.Bottom))
                .padding(horizontal=if(compact)16.dp else 24.dp,vertical=8.dp)){
                timeline(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){
                    Text("${Formatters.duration(scrub?.toLong()?:position)} / ${Formatters.duration(duration)}",
                        color=Color.White.copy(alpha=.85f),style=MaterialTheme.typography.labelSmall,
                        modifier=Modifier.weight(1f),maxLines=1)
                    speed();fit();rotate()
                }
            }
        }
    }
}

@Composable
private fun SeekButton(icon:ImageVector,description:String,label:String,size:androidx.compose.ui.unit.Dp,enabled:Boolean,onClick:()->Unit){
    IconButton(onClick,enabled=enabled,modifier=Modifier.size(48.dp)){
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)){
            Icon(icon,description,Modifier.size(size),tint=Color.White.copy(alpha=if(enabled)1f else .35f))
            Text(label,color=Color.White.copy(alpha=if(enabled).8f else .35f),style=MaterialTheme.typography.labelSmall)
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
        app.tgdrive.ui.MediaIcons.Rotate,
        "Rotation: follow device",
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    ),
    LANDSCAPE(
        app.tgdrive.ui.MediaIcons.Rotate,
        "Rotation: locked landscape",
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    ),
    PORTRAIT(
        app.tgdrive.ui.MediaIcons.Rotate,
        "Rotation: locked portrait",
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    );

    fun next(): PlayerRotation = entries[(ordinal + 1) % entries.size]
}
