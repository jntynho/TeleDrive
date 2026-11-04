package app.tgdrive.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.tgdrive.PlaybackService
import app.tgdrive.SessionBundle
import app.tgdrive.library.DriveDataSource
import kotlinx.coroutines.*

internal fun mediaTime(milliseconds:Long):String {
    val seconds=milliseconds.coerceAtLeast(0)/1000
    return if(seconds>=3600)"%d:%02d:%02d".format(java.util.Locale.ROOT,seconds/3600,seconds/60%60,seconds%60)else"%d:%02d".format(java.util.Locale.ROOT,seconds/60,seconds%60)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun GalleryVideoPlayer(item:app.tgdrive.library.Item,bundle:SessionBundle,onRetry:()->Unit,controls:Boolean,onToggle:()->Unit,onHide:()->Unit,modifier:Modifier,onOpen:()->Unit={}) {
    val touchExploration = rememberTouchExploration()
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val checkpoint = rememberSaveable(item.versionId, saver = com.infinity.drive.presentation.preview.VideoPlaybackCheckpoint.Saver) {
        com.infinity.drive.presentation.preview.VideoPlaybackCheckpoint()
    }
    val player = remember(item.versionId) {
        ExoPlayer.Builder(context.applicationContext)
            .setRenderersFactory(androidx.media3.exoplayer.DefaultRenderersFactory(context.applicationContext).setEnableDecoderFallback(true))
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                .setDataSourceFactory(DriveDataSource.Factory((context.applicationContext as app.tgdrive.DriveApp).sessions)))
            .setAudioAttributes(AudioAttributes.DEFAULT, true).setHandleAudioBecomingNoisy(true).build()
    }
    var error by remember(player) { mutableStateOf<String?>(null) }
    var playing by remember(player) { mutableStateOf(false) }
    var buffering by remember(player) { mutableStateOf(false) }
    var interaction by remember(player) { mutableIntStateOf(0) }
    var scrubbing by remember(player) { mutableStateOf(false) }
    var overlayOpen by remember(player) { mutableStateOf(false) }
    var resize by rememberSaveable(item.versionId) { mutableStateOf(com.infinity.drive.presentation.preview.PlayerResizeMode.FIT) }
    val hide by rememberUpdatedState(onHide)
    val libraryState by bundle.library.state.collectAsStateWithLifecycle()
    val account = libraryState.accountId
    fun persist() {
        val saved = checkpoint.position
        bundle.library.scope.launch(Dispatchers.IO) {
            runCatching {
                bundle.library.checkAccount(account)
                bundle.library.db().put("settings", "playback:${item.versionId}", saved.toString())
            }
        }
    }
    DisposableEffect(player, lifecycle) {
        PlaybackService.pause(context)
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
                buffering = player.playbackState == Player.STATE_BUFFERING
            }
            override fun onPlayerError(failure: PlaybackException) {
                buffering = false
                error = "This video could not be played. Retry or open it in another app."
            }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> { checkpoint.suspend(player); persist() }
                androidx.lifecycle.Lifecycle.Event.ON_START -> checkpoint.resume(player)
                else -> Unit
            }
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            checkpoint.capture(player)
            persist()
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        try {
            if (checkpoint.position == 0L) checkpoint.position = withContext(Dispatchers.IO) {
                bundle.library.checkAccount(account)
                bundle.library.db().get("settings", "playback:${item.versionId}")?.toLongOrNull()?.coerceAtLeast(0) ?: 0
            }
            player.setMediaItem(MediaItem.Builder().setUri("tgdrive://file/${bundle.profile.id}/${item.id}/${item.versionId}")
                .setMimeType(item.mime).build())
            checkpoint.restore(player)
            player.prepare()
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) checkpoint.suspend(player)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "This video could not be opened. Retry or open it in another app."
            return@LaunchedEffect
        }
        var ticks = 0
        while (isActive) {
            checkpoint.capture(player)
            if (++ticks % 20 == 0) persist()
            delay(250)
        }
    }
    LaunchedEffect(controls, playing, scrubbing, overlayOpen, interaction, touchExploration) {
        if (controls && playing && !scrubbing && !overlayOpen && !touchExploration) { delay(3500); hide() }
    }
    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { android.view.LayoutInflater.from(it).inflate(app.tgdrive.R.layout.teledrive_video_surface, null, false) as PlayerView },
            update = {
                it.player = player
                it.keepScreenOn = playing || buffering
                it.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                it.resizeMode = when (resize) {
                    com.infinity.drive.presentation.preview.PlayerResizeMode.FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    com.infinity.drive.presentation.preview.PlayerResizeMode.ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    com.infinity.drive.presentation.preview.PlayerResizeMode.FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
            }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.matchParentSize().clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
            onClickLabel = if (controls) "Hide video controls" else "Show video controls", onClick = onToggle))
        if (buffering && !controls && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color=Color.White)
        if (error == null) com.infinity.drive.presentation.preview.PlayerControls(
            player, controls, false, resize, { resize = resize.next() }, { interaction++ },
            onScrubbingChanged = { scrubbing = it }, onOverlayChanged = { overlayOpen = it }
        )
        error?.let { text ->
            Column(Modifier.align(Alignment.Center).background(Color.Black).padding(24.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(text, color=Color.White)
                DriveButton(onRetry) { Text("Retry video") }
                TextButton(onOpen) { Text("Open in another app",color=Color.White) }
            }
        }
    }
}
