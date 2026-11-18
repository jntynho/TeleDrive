package com.infinity.drive.presentation.preview

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import app.tgdrive.R
import com.infinity.drive.presentation.components.DriveBottomSheet
import java.util.Locale

internal data class VideoTrack(
    val group: TrackGroup,
    val index: Int,
    val language: String?,
    val title: String?
)

@OptIn(UnstableApi::class)
internal fun supportedVideoTracks(tracks: Tracks, type: Int): List<VideoTrack> = buildList {
    for (group in tracks.groups) if (group.type == type) {
        for (index in 0 until group.length) if (group.isTrackSupported(index)) {
            val format = group.getTrackFormat(index)
            add(VideoTrack(group.mediaTrackGroup, index, format.language, format.label))
        }
    }
}

@OptIn(UnstableApi::class)
internal fun selectVideoTrack(
    previous: TrackSelectionParameters,
    type: Int,
    track: VideoTrack? = null,
    disabled: Boolean = false
): TrackSelectionParameters {
    require(track == null || track.group.type == type && !disabled)
    val builder = previous.buildUpon()
        .clearOverridesOfType(type)
        .setTrackTypeDisabled(type, disabled)
    if (track != null) builder.setOverrideForType(TrackSelectionOverride(track.group, track.index))
    return builder.build()
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
internal fun VideoTrackPicker(
    player: Player,
    audioTracks: List<VideoTrack>,
    textTracks: List<VideoTrack>,
    parameters: TrackSelectionParameters,
    onDismiss: () -> Unit
) {
    DriveBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()).padding(bottom = 16.dp)
        ) {
            Text(
                stringResource(R.string.video_track_picker),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge
            )
            if (audioTracks.isNotEmpty()) {
                TrackOptions(
                    title = stringResource(R.string.video_audio_tracks),
                    available = audioTracks,
                    type = C.TRACK_TYPE_AUDIO,
                    parameters = parameters,
                    onSelect = { track, disabled ->
                        player.trackSelectionParameters = selectVideoTrack(
                            player.trackSelectionParameters, C.TRACK_TYPE_AUDIO, track, disabled
                        )
                    }
                )
            }
            if (textTracks.isNotEmpty()) {
                TrackOptions(
                    title = stringResource(R.string.video_subtitle_tracks),
                    available = textTracks,
                    type = C.TRACK_TYPE_TEXT,
                    parameters = parameters,
                    onSelect = { track, disabled ->
                        player.trackSelectionParameters = selectVideoTrack(
                            player.trackSelectionParameters, C.TRACK_TYPE_TEXT, track, disabled
                        )
                    }
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TrackOptions(
    title: String,
    available: List<VideoTrack>,
    type: Int,
    parameters: TrackSelectionParameters,
    onSelect: (VideoTrack?, Boolean) -> Unit
) {
    val disabled = type in parameters.disabledTrackTypes
    val override = parameters.overrides.values.firstOrNull { it.type == type }
    Column(Modifier.selectableGroup()) {
        Text(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
        if (type == C.TRACK_TYPE_TEXT) {
            TrackOption(stringResource(R.string.video_tracks_off), selected = disabled) { onSelect(null, true) }
        }
        TrackOption(
            stringResource(R.string.video_tracks_automatic),
            selected = !disabled && override == null
        ) { onSelect(null, false) }
        available.forEachIndexed { index, track ->
            val language = remember(track.language) {
                track.language?.takeUnless { it.isBlank() || it == "und" }
                    ?.let { Locale.forLanguageTag(it).displayLanguage.takeIf(String::isNotBlank) ?: it }
            }
            TrackOption(
                title = track.title?.takeIf(String::isNotBlank) ?: language
                    ?: stringResource(R.string.video_track_number, index + 1),
                selected = !disabled && override?.let {
                    it.mediaTrackGroup == track.group && track.index in it.trackIndices
                } == true
            ) { onSelect(track, false) }
        }
    }
}

@Composable
private fun TrackOption(title: String, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
    )
}
