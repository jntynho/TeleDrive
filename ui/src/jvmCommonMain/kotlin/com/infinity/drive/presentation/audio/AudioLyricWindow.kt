package com.infinity.drive.presentation.audio

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.presentation.platform.LocalAudioPlayback
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.abs

@Composable
internal fun audioLyricLineHeight(): Dp =
    with(LocalDensity.current) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() } + 4.dp

@Composable
internal fun AudioLyricWindow(fileId: String, rawLyrics: String?, lineHeight: Dp) {
    val lyrics = remember(rawLyrics) { AudioLyricsParser.parse(rawLyrics) }
    Box(
        Modifier.fillMaxWidth().height(lineHeight * 3).clipToBounds().testTag("audio-lyrics-slot"),
        contentAlignment = Alignment.Center
    ) {
        if (lyrics.cues.any { it.text.isNotBlank() }) {
            key(fileId, lyrics) { TimedLyricLines(fileId, lyrics, lineHeight) }
        }
    }
}

@Composable
private fun TimedLyricLines(fileId: String, lyrics: AudioLyrics, lineHeight: Dp) {
    val playback = LocalAudioPlayback.current
    val initial = remember(playback, fileId, lyrics) {
        playback.state.value.takeIf { it.file?.id == fileId }?.let { lyrics.activeIndex(it.positionMs) } ?: -1
    }
    val activeIndex by remember(playback, fileId, lyrics) {
        playback.state.map { state ->
            if (state.file?.id == fileId) lyrics.activeIndex(state.positionMs) else -1
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initial)
    val transition = updateTransition(activeIndex, label = "lyricCue")
    val centerIndex by transition.animateFloat(
        transitionSpec = { if (abs(initialState - targetState) == 1) tween(280) else snap() },
        label = "lyricScroll"
    ) { it.toFloat() }
    val density = LocalDensity.current
    val linePixels = with(density) { lineHeight.toPx() }
    val blurRadius = with(density) { 1.1.dp.toPx() }
    val softBlur = remember(blurRadius) { BlurEffect(blurRadius, blurRadius, TileMode.Decal) }
    val blurSupported = softBlur.isSupported()
    val fallbackShadow = remember(blurRadius) { Shadow(Color.White, blurRadius = blurRadius) }
    val style = MaterialTheme.typography.bodyMedium.copy(
        textDirection = TextDirection.Content,
        fontWeight = FontWeight.SemiBold
    )

    // Include the entering and leaving lines while the three visible positions slide.
    for (index in (activeIndex - 2).coerceAtLeast(0)..(activeIndex + 2).coerceAtMost(lyrics.cues.lastIndex)) {
        val cue = lyrics.cues[index]
        if (cue.text.isBlank()) continue
        key(index) {
            val semantics = when (index - activeIndex) {
                -1 -> Modifier.testTag("audio-previous-lyric").clearAndSetSemantics {}
                0 -> Modifier.testTag("audio-current-lyric")
                1 -> Modifier.testTag("audio-next-lyric").clearAndSetSemantics {}
                else -> Modifier.clearAndSetSemantics {}
            }
            val softenWithShadow = !blurSupported && index != activeIndex
            Box(
                Modifier.fillMaxWidth().height(lineHeight).graphicsLayer {
                    val offset = index - centerIndex
                    val distance = abs(offset)
                    translationY = offset * linePixels
                    alpha = 1f - .48f * distance.coerceAtMost(1f)
                    alpha *= (2f - distance.coerceIn(1f, 2f))
                    renderEffect = if (blurSupported && distance >= .5f) softBlur else null
                }.then(semantics),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    cue.text,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    color = Color.White.copy(alpha = if (softenWithShadow) .35f else 1f),
                    style = if (softenWithShadow) style.copy(shadow = fallbackShadow) else style,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
