// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.preview

import app.tgdrive.ui.DriveIcons
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.infinity.drive.core.media.TelegramDataSourceFactory
import com.infinity.drive.core.media.ThumbnailModel
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.player_codec_unsupported
import com.infinity.drive.resources.player_playback_failed
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

/**
 * Media3 player for local and Telegram-streamed playback. PlayerView renders
 * the video only; the controls are Compose, so they follow the app theme and
 * the window insets. Controls share visibility with the surrounding chrome and
 * time out together with it while playback runs.
 */
@OptIn(UnstableApi::class)
@Composable
fun MediaPlayer(
    content: PreviewContent,
    title: String,
    fileId: String,
    dataSourceFactory: TelegramDataSourceFactory,
    isActivePage: Boolean,
    controlsVisible: Boolean = true,
    allowBackgroundPlayback: Boolean = false,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
    interactionBlocked: Boolean = false,
    onGestureInProgress: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    contentRevision:String=""
) {
    val context = LocalContext.current
    val activity=androidx.activity.compose.LocalActivity.current
    val activePage by rememberUpdatedState(isActivePage)
    val notifyControls by rememberUpdatedState(onControlsVisibilityChanged)

    val checkpointKey=remember(fileId,contentRevision){"$fileId:$contentRevision"}
    val checkpoint = rememberSaveable(checkpointKey, saver = VideoPlaybackCheckpoint.Saver) { VideoPlaybackCheckpoint() }
    val checkpointPrefs=remember(context){context.applicationContext.getSharedPreferences("video-checkpoints",android.content.Context.MODE_PRIVATE)}
    var checkpointReady by remember(checkpointKey){mutableStateOf(false)}
    LaunchedEffect(checkpointKey){
        val saved=withContext(Dispatchers.IO){runCatching{checkpointPrefs.getString(checkpointKey,null)?.let{org.json.JSONObject(it)}}.getOrNull()}
        if(saved!=null&&checkpoint.position==0L&&checkpoint.wantsPlay){
            checkpoint.position=saved.optLong("position").coerceAtLeast(0);checkpoint.wantsPlay=saved.optBoolean("play",true)
            checkpoint.speed=saved.optDouble("speed",1.0).toFloat().coerceIn(.5f,2f)
        }
        checkpointReady=true
    }
    fun saveCheckpoint(){
        val value=org.json.JSONObject().put("position",checkpoint.position).put("play",checkpoint.wantsPlay).put("speed",checkpoint.speed).toString()
        val edit=checkpointPrefs.edit().putString(checkpointKey,value)
        // Keep at most 128 progress records; no unbounded preferences growth.
        if(checkpointPrefs.all.size>128)checkpointPrefs.all.keys.filter{it!=checkpointKey}.take(checkpointPrefs.all.size-128).forEach{edit.remove(it)}
        edit.apply()
    }
    val holder = remember(fileId) { mutableStateOf<ExoPlayer?>(null) }
    val player = holder.value
    val audioPlayback = com.infinity.drive.presentation.platform.LocalAudioPlayback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val backgroundAllowed by rememberUpdatedState(allowBackgroundPlayback)
    var retryInitialization by remember(fileId) { mutableIntStateOf(0) }
    val touchExploration = app.tgdrive.ui.rememberTouchExploration()
    var initializationFailed by remember(content, isActivePage, retryInitialization) { mutableStateOf(false) }

    DisposableEffect(fileId,content, isActivePage, retryInitialization,checkpointReady) {
        var owned: ExoPlayer? = null
        if (isActivePage && checkpointReady) {
            try {
                // Pause the application-owned music queue before video claims audio focus.
                audioPlayback.pause()
                val renderers = DefaultRenderersFactory(context.applicationContext)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                val created = VideoSessionPool.acquire(fileId,content) {
                    ExoPlayer.Builder(context.applicationContext, renderers).build().apply {
                    setAudioAttributes(androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                    setHandleAudioBecomingNoisy(true)
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setPreferredTextLanguage(null)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    when (content) {
                        is PreviewContent.LocalMedia -> setMediaItem(MediaItem.Builder().setUri(android.net.Uri.fromFile(java.io.File(content.path))).setMediaId(fileId).setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build())
                        is PreviewContent.StreamedMedia -> {
                            val factory = if (content.parts.isNotEmpty()) {
                                dataSourceFactory.createParted(content.parts, content.encrypted,content.nominalBytesPerSecond)
                            } else dataSourceFactory.create(content.remoteFileId)
                            setMediaSource(ProgressiveMediaSource.Factory(factory)
                                .createMediaSource(MediaItem.Builder().setUri("telegram://stream/$fileId").setMediaId(fileId).setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build()))
                        }
                        else -> Unit
                    }
                    checkpoint.restore(this)
                    prepare()
                    if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !backgroundAllowed) {
                        checkpoint.suspend(this)
                    }
                }
                }
                owned=created
                if(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))created.playWhenReady=checkpoint.wantsPlay
                holder.value = created
                runCatching{context.applicationContext.startService(android.content.Intent(context,com.infinity.drive.presentation.audio.TeleDriveAudioService::class.java))}
            } catch (_: Exception) {
                owned?.let{VideoSessionPool.release(it)}
                owned = null
                initializationFailed = true
                notifyControls(true)
            }
        }
        onDispose {
            owned?.let {
                checkpoint.capture(it)
                saveCheckpoint()
                if(activity?.isChangingConfigurations!=true&&lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))it.pause()
                // Do not leave offscreen decoders and Telegram range loaders alive.
                holder.value = null
                VideoSessionPool.release(it)
            }
        }
    }

    LaunchedEffect(player) {
        if (player != null) while (isActive) {
            checkpoint.capture(player)
            saveCheckpoint()
            delay(2000)
        }
    }
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (player != null) when (event) {
                Lifecycle.Event.ON_STOP -> {
                    VideoSessionPool.background(player,backgroundAllowed)
                    if(!backgroundAllowed||!VideoSessionPool.serviceAvailable)checkpoint.suspend(player)
                }
                Lifecycle.Event.ON_START -> if (activePage) checkpoint.resume(player)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val audioOnly = when (content) {
        is PreviewContent.LocalMedia -> content.isAudio
        is PreviewContent.StreamedMedia -> content.isAudio
        else -> false
    }

    var interactionTick by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var overlayOpen by remember { mutableStateOf(false) }
    val visibleControls by rememberUpdatedState(controlsVisible)
    var resizeMode by rememberSaveable { mutableStateOf(PlayerResizeMode.FIT) }
    var playing by remember(player) { mutableStateOf(player?.isPlaying == true) }
    var buffering by remember(player) { mutableStateOf(player?.playbackState == Player.STATE_BUFFERING) }
    var firstFrame by remember(player) { mutableStateOf(false) }
    var decoderFailed by remember(player) { mutableStateOf(false) }
    var playbackFailed by remember(player) { mutableStateOf(player?.playerError != null) }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED && activePage) notifyControls(true)
            }

            override fun onRenderedFirstFrame() {
                firstFrame = true
            }

            override fun onPlayerError(error: PlaybackException) {
                if (activePage) notifyControls(true)
                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                        decoderFailed = true

                    else -> playbackFailed = true
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(controlsVisible, playing, interactionTick, activePage, audioOnly,scrubbing,overlayOpen,interactionBlocked,touchExploration) {
        if (!controlsVisible || !playing || !activePage || audioOnly || scrubbing || overlayOpen || interactionBlocked || touchExploration) return@LaunchedEffect
        delay(CONTROLLER_TIMEOUT_MS.milliseconds)
        notifyControls(false)
    }

    Box(modifier = modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        if (audioOnly && player != null) {
            AudioStage(
                player = player,
                fallbackTitle = title,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (!audioOnly && player != null) {
            AndroidView(
                factory = { viewContext ->
                    (android.view.LayoutInflater.from(viewContext).inflate(app.tgdrive.R.layout.teledrive_video_surface,null,false) as PlayerView).apply {
                        useController = false
                        this.player = player
                        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        setBackgroundColor(Color.TRANSPARENT)
                        setShutterBackgroundColor(Color.TRANSPARENT)
                    }
                },
                update = { view ->
                    view.player = player
                    view.keepScreenOn = isActivePage && (playing || buffering)
                    view.resizeMode = when (resizeMode) {
                        PlayerResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        PlayerResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        PlayerResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease={it.player=null}
            )
        }
        if (!audioOnly && !firstFrame) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(androidx.compose.ui.graphics.Color.Black)
            ) {
                AsyncImage(
                    model = ThumbnailModel(fileId,contentRevision),
                    contentDescription = null,
                    contentScale = if(resizeMode==PlayerResizeMode.ZOOM)ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (buffering && !controlsVisible && !playbackFailed && !decoderFailed) {
            androidx.compose.material3.CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(32.dp), color = androidx.compose.ui.graphics.Color.White,
                strokeWidth = 2.dp
            )
        }
        if (initializationFailed || decoderFailed || playbackFailed) {
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = DriveIcons.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(
                        if (decoderFailed) {
                            Res.string.player_codec_unsupported
                        } else {
                            Res.string.player_playback_failed
                        }
                    ),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = androidx.compose.ui.graphics.Color.White
                )
                androidx.compose.material3.TextButton(onClick = {
                    val needsRebuild=decoderFailed
                    decoderFailed=false
                    if (player == null || needsRebuild) retryInitialization++ else {
                        playbackFailed = false
                        player.seekTo(player.currentPosition.coerceAtLeast(0))
                        player.prepare()
                        player.play()
                    }
                }) { Text("Retry", color = androidx.compose.ui.graphics.Color.White) }
                val repository=org.koin.compose.koinInject<com.infinity.drive.domain.repository.FileRepository>()
                val transfers=org.koin.compose.koinInject<com.infinity.drive.domain.repository.TransferRepository>()
                val actions=androidx.compose.runtime.rememberCoroutineScope()
                var fallbackError by remember(fileId){mutableStateOf<String?>(null)}
                androidx.compose.material3.TextButton(onClick={actions.launch {
                    try {
                        val file=repository.getFile(fileId)?:error("File unavailable")
                        val path=file.localPath
                        if(path!=null){
                            val uri=androidx.core.content.FileProvider.getUriForFile(context,context.packageName+".fileprovider",java.io.File(path))
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri,file.mimeType).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }else{
                            check(transfers.enqueueDownload(fileId).isSuccess){"Couldn't queue the download"}
                            fallbackError="Download queued. Follow its progress in Activity."
                        }
                    }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                    catch(_:Exception){fallbackError="Couldn't open another player. Download the file and try again."}
                }}){Text("Download / Open in another player",color=androidx.compose.ui.graphics.Color.White)}
                fallbackError?.let{Text(it,color=androidx.compose.ui.graphics.Color.White)}
            }
        }
        if (!initializationFailed && !decoderFailed && !playbackFailed) Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClickLabel = if (controlsVisible) "Hide video controls" else "Show video controls"
                ) { notifyControls(!visibleControls) }
        )
        if (player != null && !decoderFailed && !playbackFailed) {
            PlayerControls(
                player = player,
                visible = controlsVisible,
                audioOnly = audioOnly,
                resizeMode = resizeMode,
                onCycleResizeMode = { resizeMode = resizeMode.next() },
                onInteraction = { interactionTick++ },
                onScrubbingChanged={scrubbing=it;onGestureInProgress(it)},
                onOverlayChanged={overlayOpen=it}
            )
        }
    }
}

/**
 * Stage for audio files, which have no video surface to look at. Cover art
 * embedded in the file is used when the container carries it, with the track
 * tags falling back to the file name.
 */
@Composable
private fun AudioStage(player: Player, fallbackTitle: String, modifier: Modifier = Modifier) {
    var metadata by remember(player) { mutableStateOf(player.mediaMetadata) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                metadata = mediaMetadata
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val artwork = remember(metadata) {
        metadata.artworkData?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            modifier = Modifier.size(ARTWORK_SIZE)
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = DriveIcons.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AUDIO_ICON_SIZE)
                    )
                }
            }
        }
        Text(
            text = metadata.title?.toString() ?: fallbackTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 24.dp)
        )
        metadata.artist?.toString()?.let { artist ->
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        metadata.albumTitle?.toString()?.let { album ->
            Text(
                text = album,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private const val CONTROLLER_TIMEOUT_MS = 3_000L

private val AUDIO_ICON_SIZE = 96.dp

private val ARTWORK_SIZE = 260.dp
