package com.infinity.drive.presentation.audio

import app.tgdrive.ui.DriveIcons
import androidx.compose.animation.*
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@Composable
internal fun LyricsSnippet(lyrics: AudioLyrics, positionMs: Long, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val active = lyrics.activeIndex(positionMs)
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(modifier.height((108 * fontScale).dp).clickable(enabled = lyrics.plainText.isNotBlank(),
        onClickLabel = "Open lyrics", onClick = onOpen), contentAlignment = Alignment.CenterStart) {
        if (lyrics.plainText.isBlank()) Text("No embedded lyrics available", color = Color.White.copy(alpha = .65f),
            style = MaterialTheme.typography.bodyMedium)
        else if (lyrics.cues.isEmpty()) Text(lyrics.plainText, maxLines = 3, overflow = TextOverflow.Ellipsis,
            color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyLarge)
        else AnimatedContent(active, transitionSpec = {
            (fadeIn(tween(200)) togetherWith fadeOut(tween(200))).using(SizeTransform(clip = true) { _, _ -> snap() })
        }, label = "lyricLine") { index ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val first = (index - 1).coerceAtLeast(0)
                for (line in first..(first + 2).coerceAtMost(lyrics.cues.lastIndex)) {
                    val selected = line == index
                    val color = Color.White.copy(alpha = if (selected) 1f else .7f)
                    Text(cueText(lyrics.cues[line], positionMs, selected, color), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = color,
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
internal fun SynchronizedLyrics(lyrics: AudioLyrics, onDismiss: () -> Unit) {
    val player = LocalAudioPlayback.current
    val state by player.state.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxSize().ambientArtwork(state).systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min = audioHeaderHeight()).padding(end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onDismiss) { Icon(DriveIcons.ArrowBack, "Back to player") }
                    state.file?.let { AudioNowPlayingTitle(it, Modifier.weight(1f)) }
                }
                LyricsTimeline(lyrics, state.positionMs, player::seek, Modifier.weight(1f).fillMaxWidth())
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
    LaunchedEffect(active, follow) {
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
