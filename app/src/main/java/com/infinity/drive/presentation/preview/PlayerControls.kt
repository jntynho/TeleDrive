package com.infinity.drive.presentation.preview

import app.tgdrive.ui.MediaIcons

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.compose.ui.res.stringResource
import app.tgdrive.R
import com.infinity.drive.presentation.common.Formatters
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** Playback controls in both orientations with track selection when the video offers choices. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerControls(
    player:Player,visible:Boolean,audioOnly:Boolean,resizeMode:PlayerResizeMode,
    onCycleResizeMode:()->Unit,onInteraction:()->Unit,
    onScrubbingChanged:(Boolean)->Unit={},onOverlayChanged:(Boolean)->Unit={},
    modifier:Modifier=Modifier,
    onPictureInPicture: (() -> Unit)? = null
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
    var trackMenu by remember(player) { mutableStateOf(false) }
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    var trackParameters by remember(player) { mutableStateOf(player.trackSelectionParameters) }
    val audioTracks = remember(tracks) { supportedVideoTracks(tracks, C.TRACK_TYPE_AUDIO) }
    val textTracks = remember(tracks) { supportedVideoTracks(tracks, C.TRACK_TYPE_TEXT) }
    val canChooseTracks = textTracks.isNotEmpty() || audioTracks.size > 1
    val overlay by rememberUpdatedState(onOverlayChanged)
    val scrubbing by rememberUpdatedState(onScrubbingChanged)
    val activity=LocalActivity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
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
                if (events.contains(Player.EVENT_TRACKS_CHANGED)) tracks = player.currentTracks
                if (events.contains(Player.EVENT_TRACK_SELECTION_PARAMETERS_CHANGED)) {
                    trackParameters = player.trackSelectionParameters
                }
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
    LaunchedEffect(player,visible,lifecycle){if(visible)lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
        while(true){
            position=player.currentPosition.coerceAtLeast(0)
            duration=player.duration.coerceAtLeast(0);buffered=player.bufferedPosition.coerceAtLeast(0)
            delay(250)
        }
    }}
    LaunchedEffect(seekable,duration){if(!seekable||duration<=0){scrub=null;scrubbing(false)}}
    LaunchedEffect(speedMenu, trackMenu) { overlay(speedMenu || trackMenu) }
    LaunchedEffect(visible) {
        if (!visible) {
            speedMenu = false
            trackMenu = false
            scrub = null
            scrubbing(false)
        }
    }
    LaunchedEffect(canChooseTracks) { if (!canChooseTracks) trackMenu = false }

    if (trackMenu && canChooseTracks) {
        MaterialTheme(colorScheme = darkColorScheme(
            primary = Color.White,
            surface = Color(0xFF191B1E),
            surfaceContainerLow = Color(0xFF191B1E)
        )) {
            VideoTrackPicker(player, audioTracks, textTracks, trackParameters, onDismiss = {
                trackMenu = false
                onInteraction()
            })
        }
    }

    if(speedMenu){
        MaterialTheme(colorScheme=darkColorScheme(primary=Color.White,surface=Color(0xFF191B1E),surfaceContainerLow=Color(0xFF191B1E))){
            com.infinity.drive.presentation.components.DriveBottomSheet(onDismissRequest={speedMenu=false;onInteraction()}){dismiss->
                val closeThen=com.infinity.drive.presentation.components.LocalSheetCloseThen.current
                Column(Modifier.fillMaxWidth().heightIn(max=440.dp).verticalScroll(rememberScrollState()).padding(bottom=16.dp)){
                    Text("Playback speed",Modifier.padding(horizontal=24.dp,vertical=12.dp),style=MaterialTheme.typography.titleLarge)
                    listOf(.5f,.75f,1f,1.25f,1.5f,1.75f,2f).forEach{value->
                        ListItem(headlineContent={Text(if(value==1f)"Normal · 1×"else speedLabel(value))},
                            leadingContent={Icon(MediaIcons.VideoDockSpeed,null)},
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
            val playTarget=if(compact)56.dp else 64.dp
            val density=LocalDensity.current
            val dockInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .union(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            val direction = LocalLayoutDirection.current
            val dockPadding = if (compact) 12.dp else 16.dp
            val safeBottom = with(density) { dockInsets.getBottom(this).toDp() }
            val safeWidth = with(density) {
                (dockInsets.getLeft(this, direction) + dockInsets.getRight(this, direction)).toDp()
            }
            val dockWidth = (maxWidth - dockPadding * 2 - safeWidth).coerceAtLeast(0.dp)
            val timeWidth = (100.dp * density.fontScale.coerceAtLeast(1f)).coerceAtMost(dockWidth * .55f)
            val shortViewport = viewportHeight < 240.dp
            val toolRowHeight = maxOf(48.dp, 20.dp * density.fontScale)
            val bottomReserved = 48.dp + toolRowHeight + (if (shortViewport) 48.dp else 0.dp) +
                16.dp + safeBottom
            val transportOffset=minOf(0.dp,viewportHeight/2-bottomReserved-playTarget/2-12.dp)
            val ended=playbackState==Player.STATE_ENDED
            val canSeek=seekable&&duration>0
            val playIcon = when {
                ended -> MediaIcons.VideoRestart
                requested -> MediaIcons.VideoPause
                else -> MediaIcons.VideoStart
            }
            val playDescription = if (ended) "Replay" else if (requested) "Pause" else "Play"
            val togglePlayback = {
                onInteraction()
                when {
                    ended -> { player.seekTo(0); player.play() }
                    player.playWhenReady -> player.pause()
                    else -> player.play()
                }
            }
            if(!shortViewport)Row(Modifier.align(Alignment.Center).offset(y=transportOffset).testTag("video-transport"),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(if(compact)20.dp else 28.dp)){
                SeekButton(DriveIcons.Replay10,"Back 10 seconds",canSeek){onInteraction();player.seekTo(videoSeekTarget(player.currentPosition,-10_000,player.duration))}
                IconButton(onClick = togglePlayback,
                    modifier=Modifier.size(playTarget).clip(CircleShape).background(Color.Black.copy(alpha=.5f)).testTag("video-play")){
                    Box(contentAlignment=Alignment.Center){
                        if(playbackState==Player.STATE_BUFFERING&&requested)CircularProgressIndicator(
                            Modifier.size(if(compact)40.dp else 50.dp),color=Color.White.copy(alpha=.55f),strokeWidth=1.5.dp)
                        Icon(playIcon, playDescription, Modifier.size(24.dp), tint = Color.White)
                    }
                }
                SeekButton(DriveIcons.Forward10,"Forward 10 seconds",canSeek){onInteraction();player.seekTo(videoSeekTarget(player.currentPosition,10_000,player.duration))}
            }
            val tools: @Composable () -> Unit = {
                if (canChooseTracks) {
                    VideoToolButton(MediaIcons.VideoDockTracks, stringResource(R.string.video_track_picker)) {
                        onInteraction()
                        trackMenu = true
                    }
                }
                if (onPictureInPicture != null) {
                    VideoToolButton(MediaIcons.VideoDockPip, stringResource(R.string.video_picture_in_picture)) {
                        onInteraction()
                        onPictureInPicture()
                    }
                }
                VideoToolButton(MediaIcons.VideoDockSpeed, "Playback speed, ${speedLabel(rate)}") {
                    onInteraction()
                    speedMenu = true
                }
                VideoToolButton(resizeMode.icon, resizeMode.description) {
                    onInteraction()
                    onCycleResizeMode()
                }
                VideoToolButton(rotation.icon, rotation.description,
                    iconRotation = if (rotation == PlayerRotation.LANDSCAPE) 90f else 0f) {
                    onInteraction()
                    val next = rotation.next()
                    if (orientationOwner != null) orientationOwner.select(next)
                    else {
                        fallbackRotation = next
                        activity?.requestedOrientation = next.orientation
                    }
                }
            }
            val timeline: @Composable () -> Unit = {
                val current = interpolatedVideoPosition(player, position, visible,
                    requested && playbackState == Player.STATE_READY, rate)
                val elapsed = (scrub?.toLong() ?: current).coerceIn(0, duration)
                SlimSlider(elapsed.toFloat(), onValueChange = { scrub = it; scrubbing(true); onInteraction() },
                    onValueChangeFinished = {
                        if (canSeek) scrub?.let { player.seekTo(videoSeekTarget(it.toLong(), 0, player.duration)) }
                        scrub = null
                        scrubbing(false)
                        onInteraction()
                    },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = canSeek,
                    trackHeight = 6.dp, thumbSize = 14.dp,
                    activeColor = Color.White, inactiveColor = Color.White.copy(alpha = .3f),
                    bufferedFraction = if (duration > 0) buffered.toFloat() / duration else 0f,
                    bufferedColor = Color.White.copy(alpha = .4f),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Seek video" })
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val total = if (duration > 0) Formatters.duration(duration) else "--:--"
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = toolRowHeight),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${Formatters.duration(elapsed)} / $total", color = Color.White.copy(alpha = .88f),
                            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).testTag("video-timeline-time")
                        )
                        Row(
                            Modifier.widthIn(max = (dockWidth - timeWidth).coerceAtLeast(0.dp))
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) { tools() }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag("video-bottom-dock")
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f))))
                    .windowInsetsPadding(dockInsets)
                    .padding(horizontal = dockPadding, vertical = 8.dp)
            ) {
                if (shortViewport) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        SeekButton(DriveIcons.Replay10, "Back 10 seconds", canSeek) {
                            onInteraction()
                            player.seekTo(videoSeekTarget(player.currentPosition, -10_000, player.duration))
                        }
                        VideoToolButton(playIcon, playDescription, action = togglePlayback)
                        SeekButton(DriveIcons.Forward10, "Forward 10 seconds", canSeek) {
                            onInteraction()
                            player.seekTo(videoSeekTarget(player.currentPosition, 10_000, player.duration))
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().testTag("video-timeline")) { timeline() }
            }
        }
    }
}

@Composable
private fun VideoToolButton(icon: ImageVector, label: String, iconRotation: Float = 0f, action: () -> Unit) {
    IconButton(action, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        Icon(icon, null, Modifier.size(24.dp).graphicsLayer { rotationZ = iconRotation }, tint = Color.White)
    }
}

@Composable
private fun SeekButton(icon:ImageVector,description:String,enabled:Boolean,onClick:()->Unit){
    IconButton(onClick,enabled=enabled,modifier=Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha=.4f))){
        Icon(icon, description, Modifier.size(24.dp), tint = Color.White.copy(alpha = if (enabled) 1f else .35f))
    }
}
private fun speedLabel(value:Float)=if(value%1f==0f)"${value.toInt()}×"else"${value}×"
/** Video scaling applied to the surface, cycled from the controls. */
enum class PlayerResizeMode(val description: String) {
    FIT("Scaling: fit"),
    ZOOM("Scaling: crop to fill"),
    FILL("Scaling: stretch");

    val icon: ImageVector @Composable get() = if (this == ZOOM) MediaIcons.VideoDockCrop else MediaIcons.VideoDockFit

    fun next(): PlayerResizeMode = if (this == FIT) ZOOM else FIT
}

/** Screen orientation while a video is open, cycled from the controls. */
internal enum class PlayerRotation(
    val description: String,
    val orientation: Int
) {
    AUTO(
        "Rotation: follow device",
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    ),
    LANDSCAPE(
        "Rotation: locked landscape",
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    ),
    PORTRAIT(
        "Rotation: locked portrait",
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    );

    val icon: ImageVector @Composable get() = if (this == AUTO) MediaIcons.VideoDockRotate else MediaIcons.VideoDockRotateLocked

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
