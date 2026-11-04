package com.infinity.drive.presentation.audio

import app.tgdrive.ui.ElasticLazyColumn as LazyColumn
import app.tgdrive.ui.DriveIcons
import androidx.compose.animation.*
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.presentation.platform.LocalAudioPlayback

private fun cueText(cue: LyricCue, positionMs: Long, active: Boolean, color: Color): AnnotatedString =
    if (!active || cue.words.isEmpty()) AnnotatedString(cue.text) else buildAnnotatedString {
        cue.words.forEach { word ->
            pushStyle(SpanStyle(color = if (word.timeMs <= positionMs) color else color.copy(alpha = .45f)))
            append(word.text)
            pop()
        }
    }

/** A virtualized, fixed-height viewport scrolls the actual lines instead of replacing three texts. */
@Composable
internal fun LyricsSnippet(lyrics:AudioLyrics,positionMs:Long,onOpen:()->Unit,modifier:Modifier=Modifier,trackKey:String?=null){
    Box(modifier.then(if(lyrics.plainText.isNotBlank())Modifier.semantics{contentDescription="Open lyrics"}else Modifier).clickable(enabled=lyrics.plainText.isNotBlank(),
        interactionSource=remember{androidx.compose.foundation.interaction.MutableInteractionSource()},indication=null,
        onClickLabel="Open lyrics",onClick=onOpen),contentAlignment=Alignment.Center){
        when{
            lyrics.plainText.isBlank()->Text("No embedded lyrics available",color=Color.White.copy(alpha=.5f),style=MaterialTheme.typography.bodySmall)
            lyrics.cues.isEmpty()->Text(lyrics.plainText,maxLines=3,overflow=TextOverflow.Ellipsis,
                color=Color.White.copy(alpha=.8f),style=MaterialTheme.typography.bodyLarge,
                textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            else->key(trackKey,lyrics){
                val active=lyrics.activeIndex(positionMs)
                val list=rememberLazyListState(initialFirstVisibleItemIndex=active.coerceAtLeast(0))
                BoxWithConstraints(Modifier.fillMaxSize()){
                    val lineHeight=with(androidx.compose.ui.platform.LocalDensity.current){MaterialTheme.typography.bodyLarge.lineHeight.toDp()}
                    val row=maxOf(maxHeight/3,lineHeight).coerceAtMost(maxHeight)
                    LaunchedEffect(active){list.animateScrollToItem(active.coerceAtLeast(0))}
                    androidx.compose.foundation.lazy.LazyColumn(state=list,userScrollEnabled=false,
                        modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=(maxHeight-row)/2)){
                        itemsIndexed(lyrics.cues,key={index,_->index}){index,cue->
                            val emphasis by androidx.compose.animation.core.animateFloatAsState(if(index==active)1f else .38f,tween(260),label="lyricFocus")
                            Box(Modifier.fillMaxWidth().height(row),contentAlignment=Alignment.Center){
                                Text(cueText(cue,positionMs,index==active,Color.White),
                                    Modifier.blur(((1-emphasis)/.62f).coerceIn(0f,1f).dp),maxLines=1,overflow=TextOverflow.Ellipsis,
                                    color=Color.White.copy(alpha=emphasis),style=MaterialTheme.typography.bodyLarge,
                                    fontWeight=FontWeight.Medium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SynchronizedLyrics(lyrics: AudioLyrics, onDismiss: () -> Unit) {
    val player = LocalAudioPlayback.current
    val state by player.state.collectAsStateWithLifecycle()
    val position=if(lyrics.cues.isNotEmpty())interpolatedAudioPosition(state)else state.positionMs
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxSize().ambientArtwork(state).systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min = audioHeaderHeight()).padding(end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onDismiss) { Icon(DriveIcons.ArrowBack, "Back to player") }
                    state.file?.let { AudioNowPlayingTitle(it, Modifier.weight(1f)) }
                }
                LyricsTimeline(lyrics, position, player::seek, Modifier.weight(1f).fillMaxWidth())
                Column(Modifier.padding(horizontal = 24.dp)) {
                    AudioSeekBar(state)
                    AudioTransport(state)
                }
            }
        }
    }
}

/** Dragging pauses follow mode. Layout metrics stay constant when the current line changes. */
@Composable
internal fun LyricsTimeline(lyrics: AudioLyrics, positionMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    val dragging by list.interactionSource.collectIsDraggedAsState()
    var follow by remember(lyrics) { mutableStateOf(true) }
    val active = lyrics.activeIndex(positionMs)
    LaunchedEffect(dragging) { if (dragging) follow = false }
    LaunchedEffect(lyrics, active, follow) {
        if (follow && lyrics.cues.isNotEmpty()) list.animateScrollToItem(active.coerceAtLeast(0))
    }
    val cues = remember(lyrics) { lyrics.cues.ifEmpty { lyrics.plainText.lines().filter(String::isNotBlank).map { LyricCue(0, it) } } }
    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(
                start = 28.dp, end = 28.dp, top = maxHeight * .32f, bottom = maxHeight * .5f),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                itemsIndexed(cues, key = { index, _ -> index }) { index, cue ->
                    val selected = lyrics.cues.isNotEmpty() && index == active
                    val color by animateColorAsState(Color.White.copy(alpha = if (selected || lyrics.cues.isEmpty()) 1f else .55f), tween(200), label = "lyricEmphasis")
                    Text(cueText(cue, positionMs, selected, color), color = color, fontSize = 25.sp, lineHeight = 34.sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().clickable(
                            enabled = lyrics.cues.isNotEmpty(), onClickLabel = "Play from this lyric") { onSeek(cue.timeMs); follow = true })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            if (!follow && lyrics.cues.isNotEmpty()) TextButton({ follow = true }) { Text("Follow playback", color = Color.White) }
            else Text(if (lyrics.cues.isEmpty()) "Lyrics have no timing" else "Tap a line to seek",
                color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Interpolation is local to visible lyrics. Pauses, seeks and new tracks re-anchor immediately. */
@Composable
internal fun interpolatedAudioPosition(state:com.infinity.drive.presentation.platform.AudioPlaybackState):Long {
    val position by produceState(state.positionMs,state.file?.id,state.positionMs,state.playing,state.loading){
        value=state.positionMs
        if(state.playing&&!state.loading){
            val start=androidx.compose.animation.core.withInfiniteAnimationFrameNanos{it}
            while(true)androidx.compose.animation.core.withInfiniteAnimationFrameNanos{frame->
                val elapsed=((frame-start)/1_000_000).coerceIn(0,350)
                value=(state.positionMs+elapsed).coerceAtMost(state.durationMs.takeIf{it>0}?:Long.MAX_VALUE)
            }
        }
    }
    return position
}
